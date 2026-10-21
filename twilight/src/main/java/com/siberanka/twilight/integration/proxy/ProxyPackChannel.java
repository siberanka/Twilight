/* Copyright (c) 2026 siberanka. Twilight License (PolyForm Noncommercial 1.0.0 + terms), see LICENSE. */
package com.siberanka.twilight.integration.proxy;

import com.siberanka.twilight.protocol.PackChannel;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Offers this server's exported Bedrock pack to twilight-proxy over plugin messages.
 *
 * <p>Only active when a secret shared with the proxy exists: {@code proxy.secret} in Twilight's
 * configuration, Paper's Velocity forwarding secret or BungeeGuard's tokens. Requests must carry a
 * valid HMAC, a fresh timestamp and an unused nonce; one transfer runs at a time, at a bounded rate,
 * and stops when the player leaves or the pack changes. Without a secret nothing is announced and
 * every message on the channel is ignored.
 */
public final class ProxyPackChannel implements PluginMessageListener, Listener, AutoCloseable {
    private static final int CHUNKS_PER_TICK = 2;
    private static final long FULL_TRANSFER_COOLDOWN_MILLIS = 30_000;
    private static final int NONCE_MEMORY = 1024;

    private final Plugin plugin;
    /** Runs a task on the server (global region on Folia) after the given ticks. */
    private final java.util.function.ObjLongConsumer<Runnable> later;
    private final Path pack;
    private final List<PackChannel.Key> keys;
    private final Map<String, Boolean> usedNonces = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, false) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > NONCE_MEMORY;
        }
    });
    private volatile Fingerprint fingerprint;
    private Transfer active;
    private long lastCompleted;
    private volatile java.util.function.Consumer<PackChannel.Capabilities> capabilities = ignored -> { };
    private volatile java.util.function.Consumer<List<String>> displayed = ignored -> { };

    private ProxyPackChannel(Plugin plugin, java.util.function.ObjLongConsumer<Runnable> later, Path pack,
                             List<PackChannel.Key> keys) {
        this.plugin = plugin;
        this.later = later;
        this.pack = pack;
        this.keys = List.copyOf(keys);
    }

    /** Receives the items the proxy saw in item displays without a model in this server's pack (verified). */
    public void onDisplayed(java.util.function.Consumer<List<String>> listener) {
        displayed = listener;
    }

    /** Receives the proxy's verified statements of what its Bedrock runtime shows. */
    public void onCapabilities(java.util.function.Consumer<PackChannel.Capabilities> listener) {
        capabilities = listener;
    }

    /** Null (and a log line) when no secret is shared with a proxy. */
    public static ProxyPackChannel start(Plugin plugin, java.util.function.ObjLongConsumer<Runnable> later, Path exportedPack,
                                         String configuredSecret) {
        List<PackChannel.Key> keys = new ArrayList<>();
        for (String secret : secrets(plugin, configuredSecret)) {
            try { keys.add(PackChannel.Key.derive(secret)); }
            catch (IllegalArgumentException tooShort) { plugin.getLogger().warning("Ignoring a proxy secret shorter than 16 characters."); }
        }
        if (keys.isEmpty()) {
            plugin.getLogger().info("twilight-proxy pack sharing is off: no proxy secret (proxy.secret, Velocity forwarding or BungeeGuard).");
            return null;
        }
        ProxyPackChannel channel = new ProxyPackChannel(plugin, later, exportedPack, keys);
        var messenger = Bukkit.getMessenger();
        messenger.registerOutgoingPluginChannel(plugin, PackChannel.CHANNEL);
        messenger.registerIncomingPluginChannel(plugin, PackChannel.CHANNEL, channel);
        Bukkit.getPluginManager().registerEvents(channel, plugin);
        plugin.getLogger().info("twilight-proxy pack sharing is on (" + keys.size() + " shared secret(s)).");
        return channel;
    }

    /** Explicit secret first, then the secrets the proxy setup already shares with this server. */
    static List<String> secrets(Plugin plugin, String configured) {
        List<String> secrets = new ArrayList<>();
        if (configured != null && !configured.isBlank()) secrets.add(configured.strip());
        File root = Bukkit.getWorldContainer().getAbsoluteFile();
        File paperGlobal = new File(root, "config/paper-global.yml");
        if (paperGlobal.isFile()) {
            var yaml = YamlConfiguration.loadConfiguration(paperGlobal);
            if (yaml.getBoolean("proxies.velocity.enabled")) add(secrets, yaml.getString("proxies.velocity.secret"));
        }
        File legacyPaper = new File(root, "paper.yml");
        if (legacyPaper.isFile()) {
            var yaml = YamlConfiguration.loadConfiguration(legacyPaper);
            if (yaml.getBoolean("settings.velocity-support.enabled")) add(secrets, yaml.getString("settings.velocity-support.secret"));
        }
        File bungeeGuard = new File(plugin.getDataFolder().getParentFile(), "BungeeGuard/config.yml");
        if (bungeeGuard.isFile()) {
            for (String token : YamlConfiguration.loadConfiguration(bungeeGuard).getStringList("allowed-tokens")) add(secrets, token);
        }
        return secrets;
    }

    private static void add(List<String> secrets, String secret) {
        if (secret != null && !secret.isBlank() && !secrets.contains(secret.strip())) secrets.add(secret.strip());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // After the proxy has registered the channel for this connection.
        later.accept(() -> announce(player), 40L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        synchronized (this) {
            if (active != null && active.player.equals(event.getPlayer().getUniqueId())) active = null;
        }
    }

    /** Announces the current pack to the proxy through any online player (after a new build). */
    public void announceToAnyPlayer() {
        later.accept(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                announce(player);
                return;
            }
        }, 1L);
    }

    private void announce(Player player) {
        if (!player.isOnline()) return;
        Fingerprint current = fingerprint();
        if (current == null) return;
        player.sendPluginMessage(plugin, PackChannel.CHANNEL,
                PackChannel.announce(keys.getFirst(), System.currentTimeMillis(), current.sha256, current.size));
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!PackChannel.CHANNEL.equals(channel) || message == null || message.length > PackChannel.MAX_MESSAGE) return;
        Object decoded = PackChannel.read(message, keys, System.currentTimeMillis());
        if (decoded instanceof PackChannel.Capabilities features) {
            capabilities.accept(features);
            return;
        }
        if (decoded instanceof PackChannel.Displayed seen) {
            displayed.accept(seen.keys());
            return;
        }
        if (!(decoded instanceof PackChannel.Request request)) return;
        if (usedNonces.put(HexFormat.of().formatHex(request.nonce()), Boolean.TRUE) != null) return;
        Fingerprint current = fingerprint();
        if (current == null || !MessageDigest.isEqual(current.sha256, request.sha256())) return;
        synchronized (this) {
            long now = System.currentTimeMillis();
            if (active != null || now - lastCompleted < FULL_TRANSFER_COOLDOWN_MILLIS) return;
            active = new Transfer(player.getUniqueId(), request.nonce(), current);
        }
        pump();
    }

    /** Sends the next chunks of the active transfer, then again next tick until done. */
    private void pump() {
        Transfer transfer;
        synchronized (this) { transfer = active; }
        if (transfer == null) return;
        Player player = Bukkit.getPlayer(transfer.player);
        Fingerprint current = fingerprint();
        if (player == null || !player.isOnline() || current == null || !MessageDigest.isEqual(current.sha256, transfer.pack.sha256)) {
            finish(transfer, false);
            return;
        }
        int total = (int) ((transfer.pack.size + PackChannel.CHUNK_BYTES - 1) / PackChannel.CHUNK_BYTES);
        byte[] buffer = new byte[PackChannel.CHUNK_BYTES];
        try (FileChannel file = FileChannel.open(pack, StandardOpenOption.READ)) {
            for (int sent = 0; sent < CHUNKS_PER_TICK && transfer.next < total; sent++, transfer.next++) {
                ByteBuffer target = ByteBuffer.wrap(buffer);
                long position = (long) transfer.next * PackChannel.CHUNK_BYTES;
                while (target.hasRemaining() && position + target.position() < transfer.pack.size) {
                    if (file.read(target, position + target.position()) < 0) break;
                }
                player.sendPluginMessage(plugin, PackChannel.CHANNEL,
                        PackChannel.chunk(keys.getFirst(), transfer.nonce, transfer.next, total, buffer, target.position()));
            }
        } catch (IOException failure) {
            plugin.getLogger().log(Level.WARNING, "Could not read the exported pack for twilight-proxy", failure);
            finish(transfer, false);
            return;
        }
        if (transfer.next >= total) finish(transfer, true);
        else later.accept(this::pump, 1L);
    }

    private synchronized void finish(Transfer transfer, boolean complete) {
        if (active == transfer) active = null;
        if (complete) lastCompleted = System.currentTimeMillis();
    }

    /** SHA-256 and size of the exported pack, recomputed only when the file changes. */
    private Fingerprint fingerprint() {
        try {
            if (!Files.isRegularFile(pack)) return null;
            long size = Files.size(pack), modified = Files.getLastModifiedTime(pack).toMillis();
            Fingerprint cached = fingerprint;
            if (cached != null && cached.size == size && cached.modified == modified) return cached;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(pack)) {
                byte[] buffer = new byte[65_536];
                for (int read; (read = input.read(buffer)) >= 0;) digest.update(buffer, 0, read);
            }
            Fingerprint next = new Fingerprint(digest.digest(), size, modified);
            fingerprint = next;
            return next;
        } catch (IOException | NoSuchAlgorithmException failure) {
            return null;
        }
    }

    @Override public void close() {
        var messenger = Bukkit.getMessenger();
        messenger.unregisterIncomingPluginChannel(plugin, PackChannel.CHANNEL, this);
        messenger.unregisterOutgoingPluginChannel(plugin, PackChannel.CHANNEL);
        HandlerList.unregisterAll(this);
        synchronized (this) { active = null; }
    }

    private record Fingerprint(byte[] sha256, long size, long modified) {}

    private static final class Transfer {
        final UUID player;
        final byte[] nonce;
        final Fingerprint pack;
        int next;

        Transfer(UUID player, byte[] nonce, Fingerprint pack) {
            this.player = player;
            this.nonce = nonce;
            this.pack = pack;
        }
    }
}
