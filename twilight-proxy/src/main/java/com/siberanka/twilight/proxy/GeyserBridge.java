/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.proxy;

import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.connection.GeyserConnection;
import org.geysermc.geyser.api.event.EventRegistrar;
import org.geysermc.geyser.api.event.bedrock.SessionLoadResourcePacksEvent;
import org.geysermc.geyser.api.pack.PackCodec;
import org.geysermc.geyser.api.pack.ResourcePack;

import com.siberanka.twilight.geyser.GeyserEvents;
import com.siberanka.twilight.host.PackHost;
import com.siberanka.twilight.host.SessionHosting;

import java.net.InetAddress;
import java.util.Optional;
import java.util.UUID;

/**
 * The only class that touches Geyser's API, so twilight-proxy loads without Geyser. Packs are
 * registered per Bedrock session when it loads its resource packs (before it joins a server).
 */
final class GeyserBridge implements Sessions {
    private final EventRegistrar registrar;

    private GeyserBridge(Object owner) {
        this.registrar = EventRegistrar.of(owner);
    }

    /**
     * Registers the pack listeners with Geyser. Geyser's API exists once Geyser has loaded (on Velocity in its
     * own start-up event, which may run after twilight-proxy's): until then the result asks to try again. A
     * failure after the first listener was registered removes it again, so nothing is left half attached.
     */
    @SuppressWarnings("deprecation") // register(ResourcePack) is the form every Geyser 2.x accepts
    static Attach attach(Object owner, ProxyCore core) {
        GeyserApi api;
        try {
            api = GeyserApi.api();
        } catch (RuntimeException notLoaded) {
            return Attach.notReady("Geyser's API is not available yet (" + Sessions.describe(notLoaded) + ")");
        }
        if (api == null) return Attach.notReady("Geyser's API is not available yet");
        GeyserBridge bridge = new GeyserBridge(owner);
        try {
            // Through GeyserEvents: Floodgate's copy of Geyser's event library must never be linked against.
            GeyserEvents.subscribe(bridge.registrar, SessionLoadResourcePacksEvent.class, event -> {
                GeyserConnection connection = event.connection();
                core.packFor(connection.xuid()).ifPresent(pack -> event.register(bridge.loaded(pack)));
            });
            // After every listener has added its packs, the pack host (when it runs) turns them all into links.
            java.util.function.Consumer<SessionLoadResourcePacksEvent> hostAll = event -> {
                var host = core.host();
                if (host == null) return;
                com.siberanka.twilight.host.SessionHosting.hostAll(host, event, (pack, reason) -> {
                    if (bridge.warned.add(pack + reason)) core.warn("pack-host: " + pack + " is sent by Geyser: " + reason);
                });
            };
            if (!GeyserEvents.subscribeLast(bridge.registrar, SessionLoadResourcePacksEvent.class, hostAll)) {
                core.warn("This Geyser cannot order event listeners; pack-host links may miss packs that other plugins "
                        + "register after twilight-proxy.");
            }
            try {
                // Geyser fires this right before it reads custom_mappings: the merged file is written first.
                GeyserEvents.subscribe(bridge.registrar,
                        org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomItemsEvent.class, event -> {
                            core.beforeGeyserItems();
                            bridge.itemsRegistered = true;
                        });
            } catch (RuntimeException | LinkageError unknown) {
                bridge.itemsRegistered = true; // cannot tell: assume Geyser has started
            }
            try {
                bridge.folder = api.configDirectory();
                bridge.packFolder = api.packDirectory();
            } catch (RuntimeException | LinkageError unknown) {
                // Geyser's folders are unknown: no item mappings are written and nothing is retired.
            }
            return Attach.attached(bridge);
        } catch (RuntimeException | LinkageError failure) {
            bridge.close();
            return Attach.failed("Geyser's event API could not be used (" + Sessions.describe(failure) + ")", failure);
        }
    }

    private volatile java.nio.file.Path folder;
    private volatile java.nio.file.Path packFolder;

    @Override
    public Optional<java.nio.file.Path> packFolder() {
        return Optional.ofNullable(packFolder);
    }
    private volatile boolean itemsRegistered;

    @Override
    public boolean itemsRegistered() {
        return itemsRegistered;
    }

    @Override
    public Optional<java.nio.file.Path> geyserFolder() {
        return Optional.ofNullable(folder);
    }

    /** Geyser's pack record of each pack file, reused while the file is unchanged (hashed once, not per session). */
    private final java.util.Map<java.nio.file.Path, Loaded> packs = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Set<String> warned = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private record Loaded(long size, long modified, ResourcePack pack) {}

    private ResourcePack loaded(java.nio.file.Path file) {
        try {
            long size = java.nio.file.Files.size(file), modified = java.nio.file.Files.getLastModifiedTime(file).toMillis();
            Loaded known = packs.get(file);
            if (known != null && known.size() == size && known.modified() == modified) return known.pack();
            ResourcePack pack = ResourcePack.create(PackCodec.path(file));
            if (packs.size() > 256) packs.clear();
            packs.put(file, new Loaded(size, modified, pack));
            return pack;
        } catch (java.io.IOException unreadable) {
            return ResourcePack.create(PackCodec.path(file));
        }
    }

    /** Reads and hashes a pack file the way Geyser will, ahead of the first session that needs it. */
    @Override
    public void prepare(java.nio.file.Path file) {
        ResourcePack pack = loaded(file);
        pack.codec().sha256();
        pack.codec().size();
    }

    /**
     * The Bedrock session of a proxy player. While the first server connection is being chosen, some proxies
     * (BungeeCord) know the player before Geyser recorded its Java UUID; the session's Java name is then
     * matched as well, but only for a session not linked to a Java player yet and connected from the same
     * address, so a Java player who takes a Bedrock player's name on an offline-mode network never matches.
     */
    @Override
    public Optional<String> xuid(UUID player, String name, InetAddress address) {
        GeyserConnection connection = connection(player, name, address);
        return connection == null ? Optional.empty() : Optional.ofNullable(connection.xuid());
    }

    /** The address Geyser sees the session of {@code xuid} connect from. */
    @Override
    public Optional<InetAddress> address(UUID player, String name, InetAddress address) {
        GeyserConnection connection = connection(player, name, address);
        return connection == null ? Optional.empty() : Optional.ofNullable(SessionHosting.address(connection));
    }

    private static GeyserConnection connection(UUID player, String name, InetAddress address) {
        GeyserApi api = GeyserApi.api();
        GeyserConnection connection = api.connectionByUuid(player);
        if (connection != null) return connection;
        for (GeyserConnection online : api.onlineConnections()) {
            if (player.equals(online.javaUuid())) return online;
        }
        if (name == null || address == null) return null;
        InetAddress from = PackHost.normalise(address);
        for (GeyserConnection online : api.onlineConnections()) {
            if (online.javaUuid() == null && name.equalsIgnoreCase(online.javaUsername())
                    && from.equals(SessionHosting.address(online))) return online;
        }
        return null;
    }

    /**
     * Sends the Bedrock client back to Geyser (configured address or the one it joined with); returns the
     * {@code host:port} it was sent to, or empty when the transfer was not possible. The join address comes
     * from the client, so it is only used when it is a plain host name or IP address.
     */
    @Override
    public Optional<String> transfer(UUID player, String name, InetAddress from, String address, int port) {
        GeyserConnection connection = connection(player, name, from);
        if (connection == null) return Optional.empty();
        String host = address.isEmpty() ? connection.joinAddress() : address;
        int target = port > 0 ? port : connection.joinPort();
        if (!TransferHosts.valid(host, target)) return Optional.empty();
        if (!connection.transfer(host, target)) return Optional.empty();
        return Optional.of(host + ":" + target);
    }

    @Override
    public void close() {
        try { GeyserEvents.unregisterAll(registrar); } catch (RuntimeException | LinkageError ignored) { }
    }
}
