/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.proxy;

import com.siberanka.twilight.integration.display.DisplayKeys;
import com.siberanka.twilight.protocol.PackChannel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;

/**
 * Items Bedrock players saw in Java item displays that their server's pack has no model for. Twilight on that
 * server learns them and builds their models (see Twilight's {@code geyser.item-display-variants: seen}): displays
 * that plugins send only as packets (model bones, client-side furniture, holograms) never exist on the server, so
 * this is the only way it learns about them.
 *
 * <p>Each key is reported once per server per run; at most {@link #MAX_PER_SERVER} keys per server and
 * {@link #MAX_SERVERS} servers are remembered, so a client or plugin cannot grow it without bound.
 */
final class DisplaySightings {
    static final int MAX_PER_SERVER = 20_000;
    static final int MAX_SERVERS = 256;

    private final Map<String, Set<String>> reported = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> pending = new ConcurrentHashMap<>();

    /** Called on Geyser's session threads: only cheap set operations. */
    void seen(String server, Collection<String> keys) {
        String name = server.toLowerCase(Locale.ROOT);
        if (!reported.containsKey(name) && reported.size() >= MAX_SERVERS) return;
        Set<String> known = reported.computeIfAbsent(name, ignored -> ConcurrentHashMap.newKeySet());
        for (String key : keys) {
            if (!DisplayKeys.valid(key) || known.size() >= MAX_PER_SERVER || !known.add(key)) continue;
            pending.computeIfAbsent(name, ignored -> ConcurrentHashMap.newKeySet()).add(key);
        }
    }

    /**
     * Sends what is pending, signed, through {@code send(server, message)} (false: nobody connected there yet,
     * kept for the next attempt).
     */
    void flush(List<PackChannel.Key> keys, BiPredicate<String, byte[]> send) {
        if (keys.isEmpty()) return;
        for (Map.Entry<String, Set<String>> entry : pending.entrySet()) {
            List<String> batch = new ArrayList<>(PackChannel.MAX_DISPLAYED);
            for (String key : entry.getValue()) {
                batch.add(key);
                if (batch.size() == PackChannel.MAX_DISPLAYED) break;
            }
            if (batch.isEmpty()) continue;
            byte[] message = PackChannel.displayed(keys.getFirst(), System.currentTimeMillis(), batch);
            if (send.test(entry.getKey(), message)) entry.getValue().removeAll(batch);
        }
    }

    int pending() {
        return pending.values().stream().mapToInt(Set::size).sum();
    }
}
