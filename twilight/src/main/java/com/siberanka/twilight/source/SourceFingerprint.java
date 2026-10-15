package com.siberanka.twilight.source;

import com.siberanka.twilight.config.TwilightConfig;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/** Exact content fingerprint used to suppress duplicate provider-triggered builds. */
public final class SourceFingerprint {
    private SourceFingerprint() {}

    public static String compute(List<ContentSource> sources, List<CustomItemDescriptor> runtimeItems,
                                 TwilightConfig config) throws IOException {
        MessageDigest digest = sha256();
        List<ContentSource> orderedSources = sources.stream()
                .sorted(Comparator.comparingInt(ContentSource::priority)
                        .thenComparing(ContentSource::provider)
                        .thenComparing(source -> source.path().toString()))
                .toList();
        for (ContentSource source : orderedSources) {
            token(digest, source.provider());
            token(digest, source.kind().name());
            token(digest, Integer.toString(source.priority()));
            if (Files.isDirectory(source.path())) hashDirectory(digest, source.path(), config,
                    source.kind() == ContentSource.Kind.PROVIDER_DATA);
            else hashFile(digest, source.path(), config.maximumSourceBytes());
        }
        runtimeItems.stream().map(SourceFingerprint::itemToken).sorted().forEach(value -> token(digest, value));
        return HexFormat.of().withUpperCase().formatHex(digest.digest());
    }

    private static void hashDirectory(MessageDigest digest, Path directory, TwilightConfig config,
                                      boolean metadataOnly) throws IOException {
        Path root = directory.toRealPath();
        long total = 0;
        int count = 0;
        try (var paths = Files.walk(root, 20)) {
            for (Path file : paths.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> root.relativize(path).toString())).toList()) {
                if (metadataOnly && !ContentInspector.isProviderMetadata(file)) continue;
                if (Files.isSymbolicLink(file) || !file.toRealPath().startsWith(root)) {
                    throw new IOException("Symbolic or escaping source entry rejected: " + file);
                }
                long size = Files.size(file);
                total = Math.addExact(total, size);
                if (size > config.maximumSourceBytes() || total > config.maximumSourceBytes()) {
                    throw new IOException("Source fingerprint size limit exceeded: " + root);
                }
                if (++count > config.maximumArchiveEntries()) {
                    throw new IOException("Source fingerprint entry limit exceeded: " + root);
                }
                token(digest, root.relativize(file).toString().replace('\\', '/'));
                hashFile(digest, file, config.maximumSourceBytes());
            }
        }
    }

    private static void hashFile(MessageDigest digest, Path file, long maximumBytes) throws IOException {
        long size = Files.size(file);
        if (size > maximumBytes) throw new IOException("Source fingerprint size limit exceeded: " + file);
        token(digest, Long.toString(size));
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[65_536];
            long total = 0;
            for (int read; (read = input.read(buffer)) >= 0;) {
                if (read == 0) continue;
                total += read;
                if (total > maximumBytes) throw new IOException("Source changed or exceeded its fingerprint limit: " + file);
                digest.update(buffer, 0, read);
            }
        }
    }

    private static String itemToken(CustomItemDescriptor item) {
        return item.provider() + '\u0000' + item.baseItem() + '\u0000' + item.itemModel().orElse("") + '\u0000' +
                (item.customModelData().isPresent() ? item.customModelData().getAsInt() : "") + '\u0000' + item.displayName();
    }

    private static void token(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }

    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
