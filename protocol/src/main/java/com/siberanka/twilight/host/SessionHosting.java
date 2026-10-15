/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.host;

import org.geysermc.geyser.api.connection.GeyserConnection;
import org.geysermc.geyser.api.event.bedrock.SessionLoadResourcePacksEvent;
import org.geysermc.geyser.api.pack.PathPackCodec;
import org.geysermc.geyser.api.pack.ResourcePack;
import org.geysermc.geyser.api.pack.UrlPackCodec;
import org.geysermc.geyser.api.pack.option.ResourcePackOption;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Turns the packs a Bedrock session is about to load into packs it downloads from the {@link PackHost}, with
 * links minted for this session. Anything unexpected keeps a pack as it was: Geyser then sends it itself.
 */
public final class SessionHosting {
    private SessionHosting() {}

    /**
     * Hosts every pack of the event that Geyser would send from a file (keeping its options), so the session
     * gets only links: Bedrock falls back to Geyser's transfer for all packs when links and files are mixed.
     * Subscribe it after every other listener that registers packs.
     *
     * @param warn receives the pack name and the reason when a pack cannot be hosted (the caller logs it once)
     */
    public static void hostAll(PackHost host, SessionLoadResourcePacksEvent event, BiConsumer<String, String> warn) {
        // A client whose last link did not work gets Geyser's transfer for a while (the host logged why).
        if (!host.usable(address(event.connection()))) return;
        for (ResourcePack pack : List.copyOf(event.resourcePacks())) {
            if (pack.codec() instanceof UrlPackCodec) continue;
            String name = name(pack);
            Optional<ResourcePack> hosted = offer(host, pack, event.connection(), reason -> warn.accept(name, reason));
            if (hosted.isEmpty()) continue;
            ResourcePackOption<?>[] options = event.options(pack.uuid()).toArray(ResourcePackOption<?>[]::new);
            event.unregister(pack.uuid());
            try {
                event.register(hosted.get(), options);
            } catch (RuntimeException | LinkageError failure) {
                event.register(pack, options); // as Geyser had it
                warn.accept(name, failure.getClass().getSimpleName() + ": " + failure.getMessage());
            }
        }
    }

    /**
     * @param warn receives one line when the pack cannot be hosted (the caller logs it once)
     * @return the hosted pack, or empty to keep {@code pack}
     */
    public static Optional<ResourcePack> offer(PackHost host, ResourcePack pack, GeyserConnection connection,
                                               Consumer<String> warn) {
        try {
            if (!(pack.codec() instanceof PathPackCodec file)) {
                warn.accept("it is not a pack file, so every pack of the session is sent by Geyser");
                return Optional.empty();
            }
            var snapshot = host.snapshot(file.path(), file.sha256());
            if (snapshot.isEmpty()) {
                warn.accept("the pack changed since Geyser loaded it; it is sent by Geyser until Geyser reloads");
                return Optional.empty();
            }
            InetAddress player = address(connection);
            Optional<String> url = host.link(snapshot.get(), player, connection.joinAddress());
            if (url.isEmpty()) {
                warn.accept(player == null && host.settings().requirePlayerAddress()
                        ? "the player's address is unknown (require-player-address)"
                        : "no host for links: the join address '" + connection.joinAddress() + "' is not usable (set public-address)");
            }
            return url.map(link -> HostedPackCodec.hosted(pack, link, () -> host.fallback(link)));
        } catch (Exception | LinkageError failure) {
            warn.accept(failure.getClass().getSimpleName() + ": " + failure.getMessage());
            return Optional.empty();
        }
    }

    private static String name(ResourcePack pack) {
        try {
            return pack.manifest().header().name();
        } catch (RuntimeException unnamed) {
            return pack.uuid().toString();
        }
    }

    /** The address Geyser sees the session connect from (the real client behind a PROXY-protocol front). */
    public static InetAddress address(GeyserConnection connection) {
        try {
            Object socket = connection.getClass().getMethod("getSocketAddress").invoke(connection);
            return socket instanceof InetSocketAddress address && address.getAddress() != null
                    ? PackHost.normalise(address.getAddress()) : null;
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return null;
        }
    }
}
