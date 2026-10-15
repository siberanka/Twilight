/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.proxy;

import java.net.InetAddress;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

/** The Bedrock sessions of the proxy's Geyser, as the shared core uses them ({@link GeyserBridge}). */
interface Sessions {
    /** The XUID of a proxy player's Bedrock session. */
    Optional<String> xuid(UUID player, String name, InetAddress address);

    /** The address Geyser sees that session connect from. */
    Optional<InetAddress> address(UUID player, String name, InetAddress address);

    /** Sends the Bedrock client back to Geyser; returns the {@code host:port} it was sent to. */
    Optional<String> transfer(UUID player, String name, InetAddress from, String address, int port);

    /** Reads and hashes a pack the way Geyser will, ahead of the first session that needs it. */
    void prepare(Path pack);

    /** Geyser's configuration folder ({@code custom_mappings/} lives there), if known. */
    Optional<Path> geyserFolder();

    /** Geyser's pack folder ({@code packs/}), if known. */
    default Optional<Path> packFolder() {
        return Optional.empty();
    }

    /** True once Geyser has registered its custom items (it does so once, when it starts). */
    boolean itemsRegistered();

    /** Removes every listener registered with Geyser. */
    void close();

    /** The outcome of attaching to Geyser. */
    record Attach(Sessions sessions, String problem, boolean retry, Throwable cause) {
        static Attach attached(Sessions sessions) {
            return new Attach(sessions, "", false, null);
        }

        /** Geyser is there but not started yet: try again shortly. */
        static Attach notReady(String problem) {
            return new Attach(null, problem, true, null);
        }

        /** Geyser is absent or cannot be used; trying again does not help. */
        static Attach failed(String problem) {
            return new Attach(null, problem, false, null);
        }

        /** As {@link #failed(String)}, with the error logged in full. */
        static Attach failed(String problem, Throwable cause) {
            return new Attach(null, problem, false, cause);
        }
    }

    /** Attaches the core to Geyser. */
    @FunctionalInterface
    interface Attacher {
        Attach attach(ProxyCore core);
    }

    /** A short description of a failure for the log: its type and message. */
    static String describe(Throwable failure) {
        String message = failure.getMessage();
        return failure.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": "
                + (message.length() > 200 ? message.substring(0, 200) + "..." : message));
    }
}
