/* Copyright (c) 2026 siberanka. Twilight License (PolyForm Noncommercial 1.0.0 + terms), see LICENSE. */
package com.siberanka.twilight.integration.heads;

import com.siberanka.twilight.geyser.CustomSkulls;
import com.siberanka.twilight.geyser.HeadTextures;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * The player heads this server shows: in menus (any menu plugin's inventory, read a moment after it opens), on
 * armour stands, mobs, item frames, displays and the ground, in the configuration files of its plugins (textures,
 * skin URLs, user names, HeadDatabase ids) and the ones twilight-proxy reports. Their skins go into the pack, so that
 * Geyser on this server and on the proxy registers them as custom skull blocks: Bedrock then draws each head with its
 * skin instead of Steve's.
 *
 * <p>Skins are kept as hashes in {@code player-heads.txt} ({@code name:<user>} for heads named only by a user).
 * Geyser registers skulls when it starts, so new heads need a restart, like new items.
 */
public final class PlayerHeads implements Listener, AutoCloseable {
    private final Plugin plugin;
    private final Path file;
    private final Set<String> heads = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean saving = new AtomicBoolean();
    private final AtomicBoolean full = new AtomicBoolean();
    private volatile Consumer<List<String>> onNew = added -> { };

    private PlayerHeads(Plugin plugin, Path file) {
        this.plugin = plugin;
        this.file = file;
    }

    public static PlayerHeads start(Plugin plugin, Path file) {
        PlayerHeads heads = new PlayerHeads(plugin, file);
        heads.load();
        Bukkit.getPluginManager().registerEvents(heads, plugin);
        return heads;
    }

    /** Called with the heads seen for the first time (skin hashes, or {@code name:<user>}). */
    public void onNew(Consumer<List<String>> listener) {
        onNew = listener;
    }

    /** The skin hashes seen so far. */
    public Set<String> skins() {
        Set<String> result = new TreeSet<>();
        for (String head : heads) if (!head.startsWith("name:")) result.add(head);
        return result;
    }

    /** The user names of heads that named only a player. */
    public Set<String> names() {
        Set<String> result = new TreeSet<>();
        for (String head : heads) if (head.startsWith("name:")) result.add(head.substring(5));
        return result;
    }

    /** Heads found elsewhere: a configuration scan, a display, twilight-proxy. */
    public void report(Collection<String> found) {
        record(new ArrayList<>(found));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        HumanEntity viewer = event.getPlayer();
        Inventory top = event.getInventory();
        // Menu plugins often fill their items right after opening and refresh them later.
        Runnable read = () -> {
            List<String> found = new ArrayList<>();
            try {
                for (ItemStack stack : top.getContents()) head(stack, found);
            } catch (RuntimeException closed) {
                return;
            }
            record(found);
        };
        later(viewer, read, 2L);
        later(viewer, read, 40L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        List<String> found = new ArrayList<>();
        for (Entity entity : event.getEntities()) entity(entity, found);
        record(found);
    }

    /** The skins of the heads an entity wears, holds or shows. */
    static void entity(Entity entity, List<String> found) {
        try {
            if (entity instanceof ItemDisplay display) head(display.getItemStack(), found);
            else if (entity instanceof Item item) head(item.getItemStack(), found);
            else if (entity instanceof ItemFrame frame) head(frame.getItem(), found);
            else if (entity instanceof LivingEntity living && !(living instanceof Player)) {
                EntityEquipment equipment = living.getEquipment();
                if (equipment != null) {
                    head(equipment.getHelmet(), found);
                    head(equipment.getItemInMainHand(), found);
                    head(equipment.getItemInOffHand(), found);
                }
            }
        } catch (RuntimeException | LinkageError unreadable) {
            // An entity a plugin left half set up: nothing to learn from it.
        }
    }

    /** The skin of a player head: from its profile's textures, else from the online player it names. */
    public static void head(ItemStack stack, List<String> found) {
        if (stack == null || stack.getType() != Material.PLAYER_HEAD || !stack.hasItemMeta()) return;
        if (!(stack.getItemMeta() instanceof SkullMeta skull)) return;
        try {
            var profile = skull.getOwnerProfile();
            if (profile == null) return;
            var skin = profile.getTextures().getSkin();
            String hash = skin == null ? null : HeadTextures.fromUrl(skin.toString());
            if (hash == null && profile.getName() != null) {
                Player online = Bukkit.getPlayerExact(profile.getName());
                var own = online == null ? null : online.getPlayerProfile().getTextures().getSkin();
                hash = own == null ? null : HeadTextures.fromUrl(own.toString());
                if (hash == null && HeadTextures.name(profile.getName())) found.add("name:" + profile.getName());
            }
            if (hash != null) found.add(hash);
        } catch (RuntimeException | LinkageError older) {
            // A server without profile textures in its API (before 1.18): such heads stay Steve's on Bedrock.
        }
    }

    private void later(HumanEntity viewer, Runnable task, long ticks) {
        try {
            viewer.getScheduler().runDelayed(plugin, ignored -> task.run(), null, ticks);
        } catch (LinkageError | RuntimeException spigot) {
            Bukkit.getScheduler().runTaskLater(plugin, task, ticks);
        }
    }

    private void record(List<String> found) {
        if (found.isEmpty()) return;
        List<String> added = new ArrayList<>();
        for (String head : found) {
            if (!(HeadTextures.valid(head) || head.startsWith("name:") && HeadTextures.name(head.substring(5)))) continue;
            if (heads.contains(head)) continue;
            if (heads.size() >= CustomSkulls.MAX) {
                if (full.compareAndSet(false, true)) {
                    plugin.getLogger().warning("More than " + CustomSkulls.MAX + " distinct player heads; further ones keep"
                            + " Steve's skin on Bedrock.");
                }
                break;
            }
            if (heads.add(head)) added.add(head);
        }
        if (added.isEmpty()) return;
        save();
        onNew.accept(added);
    }

    private void load() {
        if (!Files.isRegularFile(file)) return;
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String head = line.strip();
                if ((HeadTextures.valid(head) || head.startsWith("name:") && HeadTextures.name(head.substring(5)))
                        && heads.size() < CustomSkulls.MAX) heads.add(head);
            }
        } catch (IOException | RuntimeException unreadable) {
            plugin.getLogger().warning("Could not read " + file.getFileName() + "; player heads are learned again.");
        }
    }

    /** Writes the heads off the server thread; at most one write runs, and a later change writes again. */
    private void save() {
        if (!saving.compareAndSet(false, true)) return;
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(2_000);
                Files.createDirectories(file.toAbsolutePath().getParent());
                Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
                Set<String> written;
                do {
                    written = new TreeSet<>(heads);
                    Files.write(temporary, written, StandardCharsets.UTF_8);
                    try {
                        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                    } catch (AtomicMoveNotSupportedException unsupported) {
                        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
                    }
                } while (!written.equals(new TreeSet<>(heads)));
            } catch (IOException | RuntimeException failure) {
                plugin.getLogger().warning("Could not save " + file.getFileName() + ": " + failure.getMessage());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                saving.set(false);
            }
        });
    }

    @Override public void close() {
        HandlerList.unregisterAll(this);
    }
}
