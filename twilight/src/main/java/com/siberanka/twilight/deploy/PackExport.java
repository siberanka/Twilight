/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.deploy;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;

/**
 * Every successful build is also written to {@code plugins/Twilight/export}: the Bedrock pack as
 * {@code Twilight.mcpack} and Geyser's item mappings. Servers whose Geyser runs on a proxy, or that
 * send the pack with another plugin ({@code geyser.send-pack-to-bedrock: false}), take them from there.
 * Files are replaced atomically, so a reader never sees a half-written pack.
 */
public final class PackExport {
    /** The exported pack, relative to Twilight's data folder. */
    public static final String PACK = "export/Twilight.mcpack";

    private PackExport() {}

    public static void write(Path buildOutput, Path dataDirectory) throws IOException {
        Path export = dataDirectory.resolve("export");
        Files.createDirectories(export);
        Path pack = buildOutput.resolve("pack.zip");
        if (!Files.isRegularFile(pack)) throw new IOException("The build has no pack to export: " + pack);
        copy(pack, dataDirectory.resolve(PACK));
        Path mappings = export.resolve("custom_mappings");
        Set<String> written = new HashSet<>();
        Path source = buildOutput.resolve("custom_mappings");
        if (Files.isDirectory(source)) {
            Files.createDirectories(mappings);
            try (var files = Files.list(source)) {
                for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                    String name = "twilight_" + file.getFileName().toString().replaceFirst("^geyser_", "");
                    copy(file, mappings.resolve(name));
                    written.add(name);
                }
            }
        }
        if (Files.isDirectory(mappings)) try (var files = Files.list(mappings)) {
            for (Path stale : files.filter(Files::isRegularFile).toList()) {
                if (!written.contains(stale.getFileName().toString())) Files.delete(stale);
            }
        }
    }

    private static void copy(Path source, Path target) throws IOException {
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
