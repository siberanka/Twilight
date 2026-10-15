/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.integration.pack;

import com.siberanka.twilight.host.HostSettings;
import com.siberanka.twilight.host.PackHost;
import com.siberanka.twilight.host.SessionHosting;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.event.EventRegistrar;
import org.geysermc.geyser.api.event.bedrock.SessionLoadResourcePacksEvent;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Bedrock players on this server download their packs from Twilight's {@link PackHost} instead of receiving
 * them in Geyser's chunks. After every other listener has added its packs, each pack Geyser would send from a
 * file (Twilight's, Geyser's own and any other in its packs folder) is announced with a link minted for the
 * session; a pack that cannot be hosted stays as Geyser offers it.
 */
public final class GeyserPackHosting implements AutoCloseable {
    private final PackHost host;
    private final EventRegistrar registrar;
    private final Logger logger;
    private final Set<String> warned = ConcurrentHashMap.newKeySet();

    private GeyserPackHosting(PackHost host, Object owner, Logger logger) {
        this.host = host;
        this.registrar = EventRegistrar.of(owner);
        this.logger = logger;
    }

    public static GeyserPackHosting start(Object owner, HostSettings settings, Path cache, Logger logger) throws IOException {
        PackHost host = PackHost.start(settings, cache, logger::info);
        GeyserPackHosting hosting = new GeyserPackHosting(host, owner, logger);
        try {
            com.siberanka.twilight.geyser.GeyserEvents.subscribeLast(hosting.registrar, SessionLoadResourcePacksEvent.class, hosting::load);
        } catch (RuntimeException | LinkageError failure) {
            host.close();
            throw new IOException("Geyser's pack event is not available: " + failure, failure);
        }
        return hosting;
    }

    private void load(SessionLoadResourcePacksEvent event) {
        SessionHosting.hostAll(host, event, (pack, reason) -> {
            if (warned.add(pack + reason)) logger.warning("Bedrock pack host: " + pack + " is sent by Geyser: " + reason);
        });
    }

    @Override
    public void close() {
        try {
            GeyserApi.api().eventBus().unregisterAll(registrar);
        } catch (RuntimeException | LinkageError ignored) {
            // Geyser already gone
        }
        host.close();
    }
}
