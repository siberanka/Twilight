package com.siberanka.twilight.source;

import com.siberanka.twilight.config.TwilightConfig;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ResourceIndex implements AutoCloseable {
    private final Map<String, Asset> assets;
    private final Map<String, List<Asset>> alternatives;
    private final List<ZipFile> archives;
    private AtlasSprites atlasSprites;

    private ResourceIndex(Map<String, Asset> assets, Map<String, List<Asset>> alternatives, List<ZipFile> archives) {
        this.assets = Map.copyOf(assets);
        this.alternatives = Map.copyOf(alternatives);
        this.archives = List.copyOf(archives);
    }

    public static ResourceIndex build(List<ContentSource> sources, TwilightConfig config) throws IOException {
        return build(sources, config, ignored -> { throw new IOException("Minecraft pack format is required to select pack overlays"); });
    }

    public static ResourceIndex build(List<ContentSource> sources, TwilightConfig config, PackFormatSupplier packFormat) throws IOException {
        Map<String, Asset> assets = new LinkedHashMap<>();
        Map<String, List<Asset>> alternatives = new LinkedHashMap<>();
        List<ZipFile> archives = new ArrayList<>();
        try {
            for (ContentSource source : sources) {
                if (source.kind() == ContentSource.Kind.MODEL_SOURCE) indexModelDirectory(source, assets, alternatives, config);
                else if (source.kind() == ContentSource.Kind.PROVIDER_DATA) indexProviderData(source, assets, alternatives, config);
                else if (Files.isDirectory(source.path())) indexDirectory(source, assets, alternatives, config, packFormat);
                else {
                    ZipFile archive = new ZipFile(source.path().toFile());
                    archives.add(archive);
                    indexArchive(source, archive, assets, alternatives, config, packFormat);
                }
            }
            alternatives.replaceAll((ignored, stack) -> List.copyOf(stack.reversed()));
            return new ResourceIndex(assets, alternatives, archives);
        } catch (IOException | RuntimeException failure) {
            for (ZipFile archive : archives) try { archive.close(); } catch (IOException suppressed) { failure.addSuppressed(suppressed); }
            if (failure instanceof IOException io) throw io;
            throw (RuntimeException) failure;
        }
    }

    public Optional<Asset> find(String path) {
        return Optional.ofNullable(assets.get(normalize(path)));
    }

    /** Effective asset first, followed by lower-priority layers for validated fallback. */
    public List<Asset> findAll(String path) {
        return alternatives.getOrDefault(normalize(path), List.of());
    }

    /** Sprite renames declared by the packs' texture atlases (read once). */
    public synchronized AtlasSprites atlasSprites() {
        if (atlasSprites == null) atlasSprites = AtlasSprites.read(this);
        return atlasSprites;
    }

    public Collection<String> paths() {
        return assets.keySet();
    }

    public List<String> pathsStartingWith(String prefix) {
        String normalized = normalize(prefix);
        return assets.keySet().stream().filter(path -> path.startsWith(normalized)).sorted().toList();
    }

    private static void indexArchive(ContentSource source, ZipFile zip, Map<String, Asset> target,
                                     Map<String, List<Asset>> alternatives, TwilightConfig config, PackFormatSupplier packFormat) throws IOException {
            Map<String, Asset> collected = new LinkedHashMap<>();
            if (zip.size() > config.maximumArchiveEntries()) throw new IOException("Archive entry limit exceeded: " + source.path());
            long expandedBytes = 0;
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                String original = ContentInspector.safeEntry(entry.getName(), source.path());
                String logical = logicalPath(original);
                if (logical == null && !original.endsWith("pack.mcmeta")) continue;
                if (entry.getSize() < 0) throw new IOException("Archive entry has unknown expanded size: " + original);
                expandedBytes = Math.addExact(expandedBytes, entry.getSize());
                if (expandedBytes > config.maximumSourceBytes()) throw new IOException("Expanded archive exceeds size limit: " + source.path());
                collected.put(original, new Asset(source, logical, entry.getSize(), () -> {
                    ZipEntry selected = zip.getEntry(entry.getName());
                    if (selected == null) throw new IOException("Archive entry disappeared: " + entry.getName());
                    try (InputStream input = zip.getInputStream(selected)) { return readLimited(input, config.maximumSourceBytes(), original); }
                }));
            }
            selectLayers(collected, target, alternatives, packFormat, source.kind());
    }

    private static void indexDirectory(ContentSource source, Map<String, Asset> target,
                                       Map<String, List<Asset>> alternatives, TwilightConfig config, PackFormatSupplier packFormat) throws IOException {
        Map<String, Asset> collected = new LinkedHashMap<>();
        Path root = source.path().toRealPath();
        long totalBytes = 0;
        int entries = 0;
        try (var stream = Files.walk(root, 20)) {
            for (Path file : stream.filter(Files::isRegularFile).toList()) {
                if (Files.isSymbolicLink(file)) throw new IOException("Symbolic pack entry rejected: " + file);
                String original = root.relativize(file).toString().replace('\\', '/');
                String logical = logicalPath(original);
                if (logical == null && !original.endsWith("pack.mcmeta")) continue;
                long size = Files.size(file);
                if (size > config.maximumSourceBytes()) throw new IOException("Oversized source entry: " + file);
                totalBytes = Math.addExact(totalBytes, size);
                if (totalBytes > config.maximumSourceBytes()) throw new IOException("Source directory exceeds size limit: " + root);
                if (++entries > config.maximumArchiveEntries()) throw new IOException("Source directory entry limit exceeded: " + root);
                collected.put(original, new Asset(source, logical, size, () -> Files.readAllBytes(file)));
            }
        }
        selectLayers(collected, target, alternatives, packFormat, source.kind());
    }

    private static void selectLayers(Map<String, Asset> entries, Map<String, Asset> target,
                                     Map<String, List<Asset>> alternatives, PackFormatSupplier format, ContentSource.Kind kind) throws IOException {
        PackOverlays overlays = PackOverlays.read(entries, format, kind);
        for (String original : entries.keySet().stream().filter(path -> overlays.order(path) >= 0)
                .sorted(Comparator.comparingInt(overlays::order).thenComparing(String::compareTo)).toList()) {
            Asset asset = entries.get(original);
            if (asset.path() != null) add(target, alternatives, asset.path(), asset);
        }
    }

    @FunctionalInterface
    public interface PackFormatSupplier { PackFormat get(ContentSource.Kind kind) throws IOException; }

    public record PackFormat(int major, int minor) implements Comparable<PackFormat> {
        public PackFormat {
            if (major < 0 || minor < 0) throw new IllegalArgumentException("Negative pack format");
        }
        @Override public int compareTo(PackFormat other) {
            int comparison = Integer.compare(major, other.major);
            return comparison == 0 ? Integer.compare(minor, other.minor) : comparison;
        }
    }

    private static void indexModelDirectory(ContentSource source, Map<String, Asset> target,
                                            Map<String, List<Asset>> alternatives, TwilightConfig config) throws IOException {
        Path root = source.path().toRealPath();
        try (var stream = Files.walk(root, 20)) {
            for (Path file : stream.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".bbmodel")).toList()) {
                String relative = root.relativize(file).toString().replace('\\', '/');
                long size = Files.size(file);
                if (size > config.maximumSourceBytes()) throw new IOException("Oversized model source: " + file);
                String logical = "blueprints/" + source.provider() + "/" + relative;
                add(target, alternatives, logical, new Asset(source, logical, size, () -> Files.readAllBytes(file)));
            }
        }
    }

    private static void indexProviderData(ContentSource source, Map<String, Asset> target,
                                          Map<String, List<Asset>> alternatives, TwilightConfig config) throws IOException {
        Path root = source.path().toRealPath();
        long totalBytes = 0;
        int entries = 0;
        try (var stream = Files.walk(root, 20)) {
            for (Path file : stream.filter(Files::isRegularFile).toList()) {
                if (Files.isSymbolicLink(file)) throw new IOException("Symbolic provider metadata rejected: " + file);
                if (!ContentInspector.isProviderMetadata(file)) continue;
                long size = Files.size(file);
                totalBytes = Math.addExact(totalBytes, size);
                if (totalBytes > config.maximumSourceBytes()) throw new IOException("Provider metadata exceeds size limit: " + root);
                if (++entries > config.maximumArchiveEntries()) throw new IOException("Provider metadata entry limit exceeded: " + root);
                String relative = root.relativize(file).toString().replace('\\', '/');
                String logical = "provider-data/" + source.provider() + '/' + root.getFileName().toString().toLowerCase(Locale.ROOT) + '/' + relative;
                add(target, alternatives, logical, new Asset(source, logical, size, () -> Files.readAllBytes(file)));
            }
        }
    }

    private static String logicalPath(String path) {
        String normalized = normalize(path);
        int assets = normalized.indexOf("assets/");
        int data = normalized.indexOf("data/");
        if (assets >= 0 && (data < 0 || assets < data)) return normalized.substring(assets);
        if (data >= 0) return normalized.substring(data);
        if (normalized.endsWith(".bbmodel")) return "blueprints/embedded/" + normalized;
        return null;
    }

    private static void add(Map<String, Asset> target, Map<String, List<Asset>> alternatives,
                            String logical, Asset asset) {
        target.put(logical, asset);
        alternatives.computeIfAbsent(logical, ignored -> new ArrayList<>()).add(asset);
    }

    private static String normalize(String path) {
        String normalized = path.replace('\\', '/');
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        return normalized;
    }

    private static byte[] readLimited(InputStream input, long maximum, String name) throws IOException {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[65_536];
        long total = 0;
        for (int read; (read = input.read(buffer)) >= 0;) {
            if (read == 0) continue;
            total += read;
            if (total > maximum) throw new IOException("Expanded archive entry exceeds size limit: " + name);
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    @FunctionalInterface
    private interface Reader { byte[] read() throws IOException; }

    public record Asset(ContentSource source, String path, long size, Reader reader) {
        public byte[] readBytes() throws IOException { return reader.read(); }
        public String readUtf8() throws IOException { return new String(readBytes(), java.nio.charset.StandardCharsets.UTF_8); }
    }

    @Override
    public void close() throws IOException {
        IOException failure = null;
        for (ZipFile archive : archives) {
            try { archive.close(); }
            catch (IOException closeFailure) {
                if (failure == null) failure = closeFailure;
                else failure.addSuppressed(closeFailure);
            }
        }
        if (failure != null) throw failure;
    }
}
