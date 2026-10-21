/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.proxy.api;

import java.nio.file.Path;
import java.util.Optional;

/**
 * twilight-proxy for other proxy plugins: the Bedrock pack each backend server uses. Obtain it with
 * {@link #get()} after twilight-proxy is enabled. Packs are checked archives on local disk; read
 * them, do not modify or delete them.
 */
public interface TwilightProxyApi {
    /** The Bedrock pack (.mcpack) of {@code server}, if one is available. */
    Optional<Path> pack(String server);

    /** Its SHA-256 as lowercase hex, if a pack is available. */
    Optional<String> packSha256(String server);

    /** Reloads the configuration and packs; returns false when the configuration is invalid. */
    boolean reload();

    static TwilightProxyApi get() {
        TwilightProxyApi api = Holder.api;
        if (api == null) throw new IllegalStateException("twilight-proxy is not enabled");
        return api;
    }

    /** Set by twilight-proxy itself. */
    final class Holder {
        private static volatile TwilightProxyApi api;

        private Holder() {}

        public static void set(TwilightProxyApi value) {
            api = value;
        }
    }
}
