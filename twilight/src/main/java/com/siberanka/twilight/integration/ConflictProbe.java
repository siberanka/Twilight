/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.integration;

import com.siberanka.twilight.conflicts.Environment;
import com.siberanka.twilight.conflicts.GeyserFolder;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;

/** Reads the backend's surroundings into an {@link Environment} for conflict detection. */
public final class ConflictProbe {
    private ConflictProbe() {}

    /**
     * @param geyserFolder      this server's Geyser folder, or null when Geyser is not on this server
     * @param proxyVersionCode  the version code twilight-proxy announced, 0 when unknown
     * @param proxyShowsDisplays whether twilight-proxy reports showing item displays
     */
    public static Environment environment(Server server, Path geyserFolder, int proxyVersionCode, boolean proxyShowsDisplays) {
        Plugin geyser = server.getPluginManager().getPlugin("Geyser-Spigot");
        String version = geyser == null ? "" : "Geyser-Spigot " + geyser.getPluginMeta().getVersion();
        var plugins = Arrays.stream(server.getPluginManager().getPlugins()).map(Plugin::getName).collect(Collectors.toSet());
        return new Environment(false, server.getName() + " " + server.getMinecraftVersion() + " (" + server.getVersion() + ")",
                plugins, GeyserFolder.extensions(geyserFolder), geyser != null, version, folia(), proxyVersionCode, proxyShowsDisplays);
    }

    private static boolean folia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException absent) {
            return false;
        }
    }
}
