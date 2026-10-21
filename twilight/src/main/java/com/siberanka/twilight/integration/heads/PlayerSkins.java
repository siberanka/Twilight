/* Copyright (c) 2026 siberanka. Twilight License (PolyForm Noncommercial 1.0.0 + terms), see LICENSE. */
package com.siberanka.twilight.integration.heads;

import com.siberanka.twilight.geyser.HeadTextures;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The skins of the players who join. Menus very often show a player's own head or another player's
 * ({@code head-%player_name%}, top lists, friends, auction sellers): such heads exist in no configuration, and their
 * skin is the player's. With the skins of recent players registered too, those heads show their skins on Bedrock.
 *
 * <p>Only the skin hash and the time it was last seen are kept ({@code player-skins.txt}); the most recent players
 * come first. A new player never starts a build or asks for a restart: the skins go into the next build and Geyser
 * registers them at its next start.
 */
public final class PlayerSkins implements Listener, AutoCloseable {
    /** Skins kept at most (the file stays small; the pack takes the most recent of them). */
    static final int MAX_KEPT = 20_000;

    private final Plugin plugin;
    private final Path file;
    private final Map<String, Long> seen = new ConcurrentHashMap<>();
    private final AtomicBoolean saving = new AtomicBoolean();

    private PlayerSkins(Plugin plugin, Path file) {
        this.plugin = plugin;
        this.file = file;
    }

    public static PlayerSkins start(Plugin plugin, Path file) {
        PlayerSkins skins = new PlayerSkins(plugin, file);
        skins.load();
        Bukkit.getPluginManager().registerEvents(skins, plugin);
        for (Player online : Bukkit.getOnlinePlayers()) skins.record(online);
        return skins;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        record(player);
        // Bedrock players' skins (Floodgate) arrive a few seconds after they join.
        try {
            player.getScheduler().runDelayed(plugin, ignored -> record(player), null, 200L);
        } catch (LinkageError | RuntimeException spigot) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> record(player), 200L);
        }
    }

    private void record(Player player) {
        try {
            if (!player.isOnline()) return;
            var skin = player.getPlayerProfile().getTextures().getSkin();
            String hash = skin == null ? null : HeadTextures.fromUrl(skin.toString());
            if (hash == null) return;
            Long previous = seen.put(hash, System.currentTimeMillis() / 1000);
            if (previous == null && seen.size() > MAX_KEPT) forgetOldest();
            save();
        } catch (RuntimeException | LinkageError unreadable) {
            // A profile without textures (offline mode): nothing to keep.
        }
    }

    /** The skins of the most recent players, newest first. */
    public Set<String> recent(int limit) {
        if (limit <= 0) return Set.of();
        List<Map.Entry<String, Long>> entries = new ArrayList<>(seen.entrySet());
        entries.sort(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()).thenComparing(Map.Entry.comparingByKey()));
        Set<String> result = new LinkedHashSet<>();
        for (var entry : entries) {
            if (result.size() >= limit) break;
            result.add(entry.getKey());
        }
        return result;
    }

    private void forgetOldest() {
        seen.entrySet().stream().min(Map.Entry.comparingByValue()).ifPresent(oldest -> seen.remove(oldest.getKey()));
    }

    private void load() {
        if (!Files.isRegularFile(file)) return;
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String[] parts = line.strip().split(" ");
                if (parts.length != 2 || !HeadTextures.valid(parts[0]) || seen.size() >= MAX_KEPT) continue;
                try {
                    seen.put(parts[0], Long.parseLong(parts[1]));
                } catch (NumberFormatException ignored) {
                    // A damaged line.
                }
            }
        } catch (IOException | RuntimeException unreadable) {
            plugin.getLogger().warning("Could not read " + file.getFileName() + "; player skins are learned again.");
        }
    }

    /** Writes the skins off the server thread, batching the joins of a minute into one write. */
    private void save() {
        if (!saving.compareAndSet(false, true)) return;
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(60_000);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                saving.set(false);
            }
            write();
        });
    }

    private synchronized void write() {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            List<String> lines = new ArrayList<>();
            seen.forEach((hash, time) -> lines.add(hash + ' ' + time));
            lines.sort(null);
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            Files.write(temporary, lines, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException failure) {
            plugin.getLogger().warning("Could not save " + file.getFileName() + ": " + failure.getMessage());
        }
    }

    /** Stops listening and writes what a pending save would have written. */
    @Override public void close() {
        HandlerList.unregisterAll(this);
        if (!seen.isEmpty()) write();
    }
}
