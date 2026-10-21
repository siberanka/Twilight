/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.geyser;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Twilight files in a Geyser folder that the current setup does not own: item mappings and packs left by an
 * older Twilight, copied by hand or written by a custom sync tool. Geyser loads every {@code .json} under
 * {@code custom_mappings} (recursively) and every pack in {@code packs}, so such leftovers register the same
 * Java items a second time or send a second Twilight pack, and Bedrock players see broken icons.
 *
 * <p>They are moved, never deleted, to a dated folder ({@code retired/<time>/...}) outside Geyser, so an
 * administrator can restore them.
 */
public final class StaleFiles {
    private static final int MAX_DEPTH = 4;
    private static final long MAX_MANIFEST_BYTES = 64 * 1024;
    private static final DateTimeFormatter FOLDER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /** A file that was moved: its path relative to Geyser's folder, and where it went. */
    public record Retired(String file, Path to) {}

    private StaleFiles() {}

    /**
     * Moves Twilight mapping files ({@code twilight*.json} under {@code custom_mappings}) and Twilight packs (named
     * {@code twilight*}, or whose manifest names the pack "Twilight") out of Geyser, except {@code keep} (paths
     * relative to Geyser's folder, such as {@code custom_mappings/twilight-proxy_item_mappings.json}).
     */
    public static List<Retired> retire(Path geyserFolder, Path packFolder, Set<String> keep, Path retiredRoot) throws IOException {
        List<Path> stale = find(geyserFolder, packFolder, keep);
        if (stale.isEmpty()) return List.of();
        Path target = retiredRoot.resolve(LocalDateTime.now().format(FOLDER));
        List<Retired> moved = new ArrayList<>();
        for (Path file : stale) {
            String relative = relative(geyserFolder, packFolder, file);
            Path destination = unused(target.resolve(relative.replace('/', java.io.File.separatorChar)));
            Files.createDirectories(destination.getParent());
            try {
                Files.move(file, destination, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException otherDisk) {
                Files.move(file, destination);
            }
            moved.add(new Retired(relative, destination));
        }
        return moved;
    }

    /** The stale Twilight files, without moving them. */
    public static List<Path> find(Path geyserFolder, Path packFolder, Set<String> keep) throws IOException {
        List<Path> stale = new ArrayList<>();
        Path mappings = geyserFolder.resolve("custom_mappings");
        if (Files.isDirectory(mappings) && !Files.isSymbolicLink(mappings)) {
            try (var files = Files.walk(mappings, MAX_DEPTH)) {
                for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                    String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (!name.startsWith("twilight") || !name.endsWith(".json")) continue;
                    if (keep.contains(relative(geyserFolder, packFolder, file))) continue;
                    stale.add(file);
                }
            }
        }
        if (packFolder != null && Files.isDirectory(packFolder) && !Files.isSymbolicLink(packFolder)) {
            try (var files = Files.list(packFolder)) {
                for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                    String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (!name.endsWith(".zip") && !name.endsWith(".mcpack")) continue;
                    if (keep.contains(relative(geyserFolder, packFolder, file))) continue;
                    if (name.startsWith("twilight") || twilightPack(file)) stale.add(file);
                }
            }
        }
        return stale;
    }

    /** A pack whose manifest names it "Twilight" (every pack Twilight builds). */
    static boolean twilightPack(Path file) {
        try (ZipFile zip = new ZipFile(file.toFile())) {
            ZipEntry manifest = zip.getEntry("manifest.json");
            if (manifest == null || manifest.getSize() > MAX_MANIFEST_BYTES) return false;
            try (InputStream in = zip.getInputStream(manifest)) {
                byte[] bytes = in.readNBytes((int) MAX_MANIFEST_BYTES + 1);
                if (bytes.length > MAX_MANIFEST_BYTES) return false;
                JsonElement root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
                JsonElement name = root.getAsJsonObject().getAsJsonObject("header").get("name");
                return name != null && "Twilight".equals(name.getAsString());
            }
        } catch (IOException | RuntimeException notAPack) {
            return false;
        }
    }

    /** {@code custom_mappings/...} or {@code packs/<name>}, with forward slashes. */
    private static String relative(Path geyserFolder, Path packFolder, Path file) {
        if (packFolder != null && file.startsWith(packFolder) && !file.startsWith(geyserFolder.resolve("custom_mappings"))) {
            return "packs/" + packFolder.relativize(file).toString().replace('\\', '/');
        }
        return geyserFolder.relativize(file).toString().replace('\\', '/');
    }

    private static Path unused(Path path) {
        if (!Files.exists(path)) return path;
        for (int index = 2; ; index++) {
            Path candidate = path.resolveSibling(path.getFileName() + "." + index);
            if (!Files.exists(candidate)) return candidate;
        }
    }
}
