/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.proxy;

import com.siberanka.twilight.conflicts.ConflictSettings;
import com.siberanka.twilight.conflicts.Conflicts;
import com.siberanka.twilight.conflicts.Environment;
import com.siberanka.twilight.conflicts.GeyserFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/** Reads the proxy's surroundings for {@link Conflicts}: plugin folders next to twilight-proxy and Geyser's extensions. */
final class ProxyConflicts {
    private ProxyConflicts() {}

    /** Geyser's data folder next to twilight-proxy's own ({@code plugins/Geyser-Velocity} or {@code plugins/Geyser-BungeeCord}). */
    static Optional<Path> geyserFolder(Path dataDirectory) {
        Path plugins = dataDirectory.toAbsolutePath().getParent();
        if (plugins == null) return Optional.empty();
        for (String name : new String[]{"Geyser-Velocity", "Geyser-BungeeCord", "Geyser-Spigot"}) {
            Path folder = plugins.resolve(name);
            if (Files.isDirectory(folder)) return Optional.of(folder);
        }
        return Optional.empty();
    }

    /** The environment as far as it can be read; {@code platform} may be null before the proxy platform is known. */
    static Environment environment(Path dataDirectory, Platform platform, String version) {
        Set<String> plugins = new HashSet<>();
        Path parent = dataDirectory.toAbsolutePath().getParent();
        if (parent != null && Files.isDirectory(parent)) {
            try (var folders = Files.list(parent)) {
                folders.filter(Files::isDirectory).limit(512).forEach(folder -> plugins.add(folder.getFileName().toString()));
            } catch (IOException unreadable) {
                // Only a hint.
            }
        }
        if (platform != null) platform.pluginFolders().ifPresent(plugins::addAll);
        Optional<Path> geyser = geyserFolder(dataDirectory);
        String label = platform == null ? "proxy" : (platform.velocity() ? "Velocity" : "BungeeCord") + " (twilight-proxy " + version + ")";
        String geyserVersion = platform == null ? "" : platform.geyserPlugin().orElse("");
        return new Environment(true, label, plugins, GeyserFolder.extensions(geyser.orElse(null)),
                geyser.isPresent() || !geyserVersion.isEmpty(), geyserVersion, false, 0, false);
    }

    static Conflicts.Assessment assess(Path dataDirectory, Platform platform, ConflictSettings settings) {
        return Conflicts.assess(environment(dataDirectory, platform, platform == null ? "" : platform.version()), settings);
    }

    /**
     * Looks in a Geyser folder for other tools' mapping files and packs that overlap Twilight's, logging each once
     * ({@code integrations.conflicts.foreign-mappings}); with {@code retire} the files are moved out to {@code retired/}.
     *
     * @return how many files were moved
     */
    static int foreign(Path geyserFolder, ConflictSettings settings, Path retiredRoot,
                       java.util.function.BiConsumer<String, Throwable> warn) {
        try {
            var result = com.siberanka.twilight.conflicts.MappingOverlaps.scan(geyserFolder, settings);
            if (settings.foreignMappings() != ConflictSettings.Foreign.RETIRE) {
                for (var finding : result.findings()) warn.accept("Conflict: " + finding.text(), null);
                return 0;
            }
            var moved = com.siberanka.twilight.conflicts.MappingOverlaps.retire(geyserFolder, result.overlaps(), retiredRoot);
            for (String file : moved) warn.accept("Moved " + file + ": another tool's mapping file that overlapped Twilight's mappings.", null);
            return moved.size();
        } catch (IOException | RuntimeException failure) {
            warn.accept("Could not check Geyser's folders for other tools' mappings: " + failure.getMessage(), failure);
            return 0;
        }
    }
}
