/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.proxy;

import java.nio.file.Path;
import java.util.Collection;

/** What the proxy platform (Velocity or BungeeCord) provides to the shared core. */
public interface Platform {
    Path dataDirectory();

    /** The proxy's working directory (velocity.toml, plugins/). */
    Path proxyRoot();

    boolean velocity();

    Collection<String> serverNames();

    /** The server players join first when nothing else decides. */
    String defaultServer();

    void info(String message);

    void warn(String message, Throwable failure);

    /** Runs a task off the network threads. */
    void async(Runnable task);

    void repeat(Runnable task, long periodSeconds);

    /** Runs a task once after {@code millis}. */
    void later(Runnable task, long millis);

    /** The address a player is connected to the proxy from, if known. */
    java.util.Optional<java.net.InetAddress> address(java.util.UUID player);

    /** The backend server a player is connected to, if any. */
    java.util.Optional<String> currentServer(java.util.UUID player);

    /** The address of every backend server in the proxy's server list (name -> address). */
    default java.util.Map<String, java.net.InetSocketAddress> serverAddresses() {
        return java.util.Map.of();
    }

    /**
     * Sends a plugin message on Twilight's channel to backend {@code server} through a player connected there;
     * false when nobody is connected to it (or the server is unknown).
     */
    default boolean sendToServer(String server, byte[] message) {
        return false;
    }

    /** Players online on the proxy, or -1 when unknown. */
    default int onlinePlayers() {
        return -1;
    }

    /** Stops the proxy (for a host that starts it again). */
    default void stopProxy(String reason) {
    }

    /** The installed Geyser plugin ("Geyser-BungeeCord 2.11.3"), if the proxy lists one. */
    default java.util.Optional<String> geyserPlugin() {
        return java.util.Optional.empty();
    }

    /**
     * The data folder names of the plugins this proxy loaded (Velocity: plugin ids, BungeeCord: plugin names),
     * or empty when the platform cannot tell. Folders of removed plugins are not among them.
     */
    default java.util.Optional<java.util.Set<String>> pluginFolders() {
        return java.util.Optional.empty();
    }

    /** twilight-proxy's version from its plugin description. */
    String version();

    /**
     * Sends {@code text} followed by the clickable link {@code url} (none when null) to every online player with
     * {@link ProxyCore#UPDATE_PERMISSION} or {@link ProxyCore#ADMIN_PERMISSION}.
     */
    void tellAdmins(String text, String url);
}
