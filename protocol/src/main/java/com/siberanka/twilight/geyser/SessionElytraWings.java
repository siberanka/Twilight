/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.geyser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Consumer;

/**
 * The wings of the pack each Bedrock session loaded, for a Geyser on a proxy where every backend serves its own
 * pack (and its own wings table, so the property index of a custom elytra differs per backend). The pack file is
 * given by the session's XUID; its wings are read once per file version. Sessions without a Twilight pack, packs
 * without a wings listing (an older backend, or {@code geyser.elytra-per-player} off there) and unreadable packs
 * give {@link ElytraWings#EMPTY}, which leaves the player's elytra exactly as Geyser sends it.
 */
public final class SessionElytraWings {
    private record Known(long size, long modified, ElytraWings wings) {}

    private final Function<String, Optional<Path>> packOf;
    private final Consumer<String> warn;
    private final Map<Path, Known> known = new ConcurrentHashMap<>();
    private volatile boolean warned;

    public SessionElytraWings(Function<String, Optional<Path>> packOf, Consumer<String> warn) {
        this.packOf = packOf;
        this.warn = warn;
    }

    public ElytraWings of(String xuid) {
        if (xuid == null || xuid.isEmpty()) return ElytraWings.EMPTY;
        Optional<Path> file = packOf.apply(xuid);
        if (file == null || file.isEmpty()) return ElytraWings.EMPTY;
        try {
            Path pack = file.get();
            long size = Files.size(pack), modified = Files.getLastModifiedTime(pack).toMillis();
            Known cached = known.get(pack);
            if (cached != null && cached.size() == size && cached.modified() == modified) return cached.wings();
            ElytraWings wings = ElytraWings.read(pack);
            if (known.size() > 64) known.clear();
            known.put(pack, new Known(size, modified, wings));
            return wings;
        } catch (IOException | RuntimeException failure) {
            if (!warned) {
                warned = true;
                warn.accept("Could not read the elytra wings of a session's pack; its elytras stay vanilla (" + failure.getMessage() + ").");
            }
            return ElytraWings.EMPTY;
        }
    }
}
