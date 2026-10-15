/* Copyright (c) 2026 siberanka. Twilight License (PolyForm Noncommercial 1.0.0 + terms), see LICENSE. */
package com.siberanka.twilight.integration.display;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * What this server shows in Java displays and as dropped items: furniture, decorations, hologram items and blocks.
 * A Bedrock client builds the model of every display variant while it loads the pack (about a third of a
 * millisecond per cube on a desktop, several times that on phones), so a pack carries variants only for what appears
 * here, besides model bones, which only displays show.
 *
 * <p>Item and block displays and dropped items are read when their chunks load and shortly after they spawn; the
 * keys are kept in {@code displayed-items.txt}. A key is the base item with its custom model data, the item model the
 * stack names, the item itself when it has neither ({@code item=}, displays only: Bedrock draws dropped items of
 * their own model itself) or the block state of a block display ({@code block=}). New keys are reported once, so that
 * the next build includes them.
 */
public final class DisplayedItems implements Listener, AutoCloseable {
    /** Bounds the file and the work per build against content that spawns endless distinct items. */
    static final int MAX_KEYS = 20_000;

    private final Plugin plugin;
    private final Path file;
    private final Set<String> keys = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean saving = new AtomicBoolean();
    private final AtomicBoolean full = new AtomicBoolean();
    private volatile Consumer<List<String>> onNew = added -> { };

    private DisplayedItems(Plugin plugin, Path file) {
        this.plugin = plugin;
        this.file = file;
    }

    /** Reads the saved keys and starts watching displays. */
    public static DisplayedItems start(Plugin plugin, Path file) {
        DisplayedItems items = new DisplayedItems(plugin, file);
        items.load();
        Bukkit.getPluginManager().registerEvents(items, plugin);
        items.registerSpawns();
        return items;
    }

    /** Called (on a server thread) with the keys seen for the first time. */
    public void onNew(Consumer<List<String>> listener) {
        onNew = listener;
    }

    /** The keys seen so far. */
    public Set<String> keys() {
        return Set.copyOf(keys);
    }

    /**
     * Whether a key can change a build: model bones (ModelEngine's and BetterModel's item models) always get their
     * display models, so seeing one changes nothing.
     */
    public static boolean matters(String key) {
        return !key.startsWith("model=modelengine:") && !key.startsWith("model=bettermodel:");
    }

    /** Whether a key names an item's own model or a block state (built whatever item-display-variants says). */
    public static boolean own(String key) {
        return key.startsWith("item=") || key.startsWith("block=") || key.startsWith("head=");
    }

    /** Displays already in loaded chunks (not on Folia, where each region reads its own as chunks load). */
    public void scanLoaded() {
        if (folia()) return;
        List<String> found = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntitiesByClasses(ItemDisplay.class, BlockDisplay.class, Item.class)) {
                found.addAll(keys(entity));
            }
        }
        record(found);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        List<String> found = new ArrayList<>();
        for (Entity entity : event.getEntities()) found.addAll(keys(entity));
        record(found);
    }

    /** The keys of an item display's item, a block display's block or a dropped item's custom item. */
    static List<String> keys(Entity entity) {
        try {
            if (entity instanceof ItemDisplay display) {
                ItemStack stack = display.getItemStack();
                List<String> keys = keys(stack);
                if (keys.isEmpty() && stack != null && stack.getType() == org.bukkit.Material.PLAYER_HEAD && !itemModel(stack)) {
                    List<String> skins = new ArrayList<>();
                    com.siberanka.twilight.integration.heads.PlayerHeads.head(stack, skins);
                    for (String skin : skins) if (!skin.startsWith("name:")) keys.add(DisplayKeys.head(skin));
                    if (!keys.isEmpty()) return keys;
                }
                if (keys.isEmpty() && stack != null && !stack.getType().isAir() && !itemModel(stack)) {
                    String own = DisplayKeys.item(stack.getType().getKey().toString().toLowerCase(Locale.ROOT));
                    if (DisplayKeys.valid(own)) keys.add(own);
                }
                return keys;
            }
            if (entity instanceof BlockDisplay display) {
                String key = DisplayKeys.block(display.getBlock().getAsString());
                return key != null && DisplayKeys.valid(key) && !key.equals("block=minecraft:air") ? List.of(key) : List.of();
            }
            if (entity instanceof Item item) return keys(item.getItemStack());
        } catch (RuntimeException | LinkageError unreadable) {
            // An entity a plugin left half set up: nothing to learn from it.
        }
        return List.of();
    }

    private static boolean itemModel(ItemStack stack) {
        try {
            return stack.hasItemMeta() && stack.getItemMeta().hasItemModel();
        } catch (LinkageError | RuntimeException older) {
            return false;
        }
    }

    /**
     * Paper reports every entity that enters a world; plugins often set the item right after spawning, so it is
     * read a moment later on the entity's own scheduler (its region on Folia).
     */
    private void registerSpawns() {
        Class<? extends org.bukkit.event.Event> type;
        java.lang.reflect.Method getEntity;
        try {
            type = Class.forName("com.destroystokyo.paper.event.entity.EntityAddToWorldEvent").asSubclass(org.bukkit.event.Event.class);
            getEntity = type.getMethod("getEntity");
        } catch (ReflectiveOperationException | LinkageError spigot) {
            return;
        }
        // Fires for every entity entering a world: only a type check and one cached method call per event.
        Bukkit.getPluginManager().registerEvent(type, this, EventPriority.MONITOR, (listener, event) -> {
            if (!type.isInstance(event)) return;
            try {
                Object entity = getEntity.invoke(event);
                if (entity instanceof ItemDisplay || entity instanceof BlockDisplay || entity instanceof Item) later((Entity) entity);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Nothing to read.
            }
        }, plugin, true);
    }

    private void later(Entity entity) {
        // Dropped vanilla items are by far the most common entity here and teach nothing.
        if (entity instanceof Item item) {
            ItemStack stack = item.getItemStack();
            if (stack == null || !stack.hasItemMeta()) return;
        }
        Runnable read = () -> {
            if (entity.isValid()) record(keys(entity));
        };
        try {
            entity.getScheduler().runDelayed(plugin, task -> read.run(), null, 5L);
        } catch (LinkageError | RuntimeException spigot) {
            Bukkit.getScheduler().runTaskLater(plugin, read, 5L);
        }
    }

    /** The keys of a stack: its custom model data (number and string) and its item model. */
    static List<String> keys(ItemStack stack) {
        List<String> result = new ArrayList<>(3);
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) return result;
        ItemMeta meta = stack.getItemMeta();
        String base = stack.getType().getKey().toString().toLowerCase(Locale.ROOT);
        try {
            if (meta.hasItemModel() && meta.getItemModel() != null) result.add("model=" + meta.getItemModel());
        } catch (LinkageError | RuntimeException older) {
            // Before 1.21.4 there is no item model component.
        }
        try {
            var component = meta.getCustomModelDataComponent();
            if (component != null) {
                if (!component.getFloats().isEmpty()) result.add(base + "|cmd=" + (int) Math.floor(component.getFloats().getFirst()));
                if (!component.getStrings().isEmpty()) result.add(base + "|str=" + component.getStrings().getFirst());
            }
        } catch (LinkageError | RuntimeException older) {
            if (meta.hasCustomModelData()) result.add(base + "|cmd=" + meta.getCustomModelData());
        }
        result.removeIf(key -> !DisplayKeys.valid(key));
        return result;
    }

    /** Items seen in displays elsewhere: by Geyser, or by twilight-proxy for this server's players. */
    public void report(java.util.Collection<String> found) {
        List<String> valid = new ArrayList<>();
        for (String key : found) if (DisplayKeys.valid(key)) valid.add(key);
        record(valid);
    }

    private void record(List<String> found) {
        if (found.isEmpty()) return;
        List<String> added = new ArrayList<>();
        for (String key : found) {
            if (keys.contains(key)) continue;
            if (keys.size() >= MAX_KEYS) {
                if (full.compareAndSet(false, true)) {
                    plugin.getLogger().warning("More than " + MAX_KEYS + " distinct items in item displays; further ones"
                            + " are not given display models (geyser.item-display-variants: all includes every item).");
                }
                break;
            }
            if (keys.add(key)) added.add(key);
        }
        if (added.isEmpty()) return;
        save();
        onNew.accept(added);
    }

    private void load() {
        if (!Files.isRegularFile(file)) return;
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String key = line.strip();
                if (DisplayKeys.valid(key) && keys.size() < MAX_KEYS) keys.add(key);
            }
        } catch (IOException | RuntimeException unreadable) {
            plugin.getLogger().warning("Could not read " + file.getFileName() + "; displays are learned again.");
        }
    }

    /** Writes the keys off the server thread; at most one write runs, and a later change writes again. */
    private void save() {
        if (!saving.compareAndSet(false, true)) return;
        CompletableFuture.runAsync(() -> {
            try {
                // Batches the keys of a chunk load or a burst of spawns into one write.
                Thread.sleep(2_000);
                Files.createDirectories(file.toAbsolutePath().getParent());
                Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
                Set<String> written;
                do {
                    written = new TreeSet<>(keys);
                    Files.write(temporary, written, StandardCharsets.UTF_8);
                    try {
                        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                    } catch (AtomicMoveNotSupportedException unsupported) {
                        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
                    }
                } while (written.size() != keys.size());
            } catch (IOException failure) {
                plugin.getLogger().log(Level.WARNING, "Could not save " + file.getFileName(), failure);
            } catch (InterruptedException stopping) {
                Thread.currentThread().interrupt();
            } finally {
                saving.set(false);
            }
        });
    }

    private static boolean folia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException absent) {
            return false;
        }
    }

    @Override public void close() {
        HandlerList.unregisterAll(this);
    }
}
