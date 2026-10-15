package com.siberanka.twilight.source;

import com.siberanka.twilight.config.TwilightConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ContentInspector {
    private final TwilightConfig config;

    public ContentInspector(TwilightConfig config) {
        this.config = config;
    }

    public ContentReport inspect(List<ContentSource> sources) throws IOException {
        Counters counters = new Counters();
        List<String> paths = new ArrayList<>();
        Map<String, Integer> providers = new HashMap<>();
        for (ContentSource source : sources) {
            paths.add(source.path().toString());
            providers.merge(source.provider(), 1, Integer::sum);
            if (source.kind() == ContentSource.Kind.PROVIDER_DATA) inspectProviderData(source.path(), counters);
            else if (Files.isDirectory(source.path())) inspectDirectory(source.path(), counters);
            else inspectArchive(source.path(), counters);
        }
        return new ContentReport(Instant.now(), sources.size(), counters.bytes, counters.entries, counters.textures,
                counters.itemDefinitions, counters.legacyItemModels, counters.models, counters.blockStates,
                counters.fonts, counters.sounds, counters.bbmodels, counters.biomes, List.copyOf(paths), Map.copyOf(providers));
    }

    private void inspectArchive(Path path, Counters counters) throws IOException {
        long archiveBytes = Files.size(path);
        if (archiveBytes > config.maximumSourceBytes()) throw new IOException("Source exceeds size limit: " + path);
        counters.bytes += archiveBytes;
        try (ZipFile zip = new ZipFile(path.toFile())) {
            if (zip.size() > config.maximumArchiveEntries()) throw new IOException("Archive entry limit exceeded: " + path);
            long expandedBytes = 0;
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                String safe = safeEntry(entry.getName(), path);
                if (entry.getSize() < 0) throw new IOException("Archive entry has unknown expanded size: " + safe);
                expandedBytes = Math.addExact(expandedBytes, entry.getSize());
                if (expandedBytes > config.maximumSourceBytes()) throw new IOException("Expanded archive exceeds size limit: " + path);
                counters.entries++;
                classify(safe, counters);
            }
        }
    }

    private void inspectDirectory(Path root, Counters counters) throws IOException {
        inspectDirectory(root, counters, false);
    }

    private void inspectProviderData(Path root, Counters counters) throws IOException {
        inspectDirectory(root, counters, true);
    }

    private void inspectDirectory(Path root, Counters counters, boolean metadataOnly) throws IOException {
        int sourceEntries = 0;
        long sourceBytes = 0;
        try (var paths = Files.walk(root, 20)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                if (metadataOnly && !isProviderMetadata(path)) continue;
                if (Files.isSymbolicLink(path)) throw new IOException("Symbolic source entry rejected: " + path);
                Path real = path.toRealPath();
                if (!real.startsWith(root.toRealPath())) throw new IOException("Source entry escapes root: " + path);
                long size = Files.size(path);
                if (size > config.maximumSourceBytes()) throw new IOException("Source entry exceeds size limit: " + path);
                sourceBytes = Math.addExact(sourceBytes, size);
                if (sourceBytes > config.maximumSourceBytes()) throw new IOException("Source directory exceeds size limit: " + root);
                counters.bytes += size;
                counters.entries++;
                if (++sourceEntries > config.maximumArchiveEntries()) throw new IOException("Directory entry limit exceeded: " + root);
                classify(root.relativize(path).toString().replace('\\', '/'), counters);
            }
        }
    }

    static boolean isProviderMetadata(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!(name.endsWith(".json") || name.endsWith(".yml") || name.endsWith(".yaml"))) return false;
        for (Path part : path) if (part.toString().equalsIgnoreCase("assets")) return false;
        return true;
    }

    static String safeEntry(String entry, Path archive) throws IOException {
        String normalized = entry.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.matches("^[A-Za-z]:.*")) throw new IOException("Absolute archive path: " + archive);
        Path path = Path.of(normalized).normalize();
        if (path.startsWith("..")) throw new IOException("Archive traversal entry: " + entry);
        return path.toString().replace('\\', '/');
    }

    private static void classify(String input, Counters c) {
        String path = input.toLowerCase(Locale.ROOT);
        if (path.endsWith(".png")) c.textures++;
        if (path.matches("(^|.*/)assets/[^/]+/items/.+\\.json$")) c.itemDefinitions++;
        if (path.matches("(^|.*/)assets/minecraft/models/item/.+\\.json$")) c.legacyItemModels++;
        if (path.matches("(^|.*/)assets/[^/]+/models/.+\\.json$")) c.models++;
        if (path.matches("(^|.*/)assets/[^/]+/blockstates/.+\\.json$")) c.blockStates++;
        if (path.matches("(^|.*/)assets/[^/]+/font/.+\\.json$")) c.fonts++;
        if (path.endsWith("sounds.json") || path.matches("(^|.*/)assets/[^/]+/sounds/.+\\.(ogg|wav)$")) c.sounds++;
        if (path.endsWith(".bbmodel")) c.bbmodels++;
        if (path.matches("(^|.*/)data/[^/]+/worldgen/biome/.+\\.json$")) c.biomes++;
    }

    private static final class Counters {
        long bytes;
        int entries, textures, itemDefinitions, legacyItemModels, models, blockStates, fonts, sounds, bbmodels, biomes;
    }
}
