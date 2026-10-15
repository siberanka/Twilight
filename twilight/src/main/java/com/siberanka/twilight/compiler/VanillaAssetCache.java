package com.siberanka.twilight.compiler;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.KeyStore;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/** Lazily caches hash-verified Mojang client assets used only for explicit vanilla references. */
final class VanillaAssetCache implements AutoCloseable {
    private static final URI VERSION_MANIFEST = URI.create("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
    private static final Set<String> ALLOWED_HOSTS = Set.of(
            "piston-meta.mojang.com", "piston-data.mojang.com", "launcher.mojang.com",
            "resources.download.minecraft.net"
    );
    private static final long MAXIMUM_CLIENT_BYTES = 200L * 1024 * 1024;
    private static final long MAXIMUM_TEXTURE_BYTES = 16L * 1024 * 1024;
    private static final long MAXIMUM_MODEL_BYTES = 1024L * 1024;

    private final Path versionDirectory;
    private final String version;
    private final boolean allowDownload;
    private final HttpClient client;
    private ZipFile archive;
    private IOException initializationFailure;
    private boolean attempted;
    private JsonObject assetObjects;

    VanillaAssetCache(Path dataDirectory, String version, boolean allowDownload) {
        this.version = requireSafeVersion(version);
        this.allowDownload = allowDownload;
        this.versionDirectory = dataDirectory.toAbsolutePath().normalize()
                .resolve("cache/vanilla").resolve(this.version).normalize();
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
                .sslContext(platformSslContext())
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    Optional<byte[]> readTexture(String logicalPath) throws IOException {
        String normalized = logicalPath.replace('\\', '/');
        if (!normalized.startsWith("assets/minecraft/textures/") || normalized.contains("..")) {
            throw new IOException("Vanilla fallback path is outside Minecraft textures: " + logicalPath);
        }
        return readEntry(normalized, MAXIMUM_TEXTURE_BYTES);
    }

    com.siberanka.twilight.source.ResourceIndex.PackFormat packFormat(com.siberanka.twilight.source.ContentSource.Kind kind) throws IOException {
        byte[] bytes = readEntry("version.json", MAXIMUM_MODEL_BYTES)
                .orElseThrow(() -> new IOException("Minecraft client lacks version.json"));
        try {
            JsonObject pack = JsonParser.parseString(new String(bytes, java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("pack_version");
            String type = kind == com.siberanka.twilight.source.ContentSource.Kind.DATAPACK ? "data" : "resource";
            int major = pack.has(type + "_major") ? pack.get(type + "_major").getAsInt() : pack.get(type).getAsInt();
            int minor = pack.has(type + "_minor") ? pack.get(type + "_minor").getAsInt() : 0;
            return new com.siberanka.twilight.source.ResourceIndex.PackFormat(major, minor);
        } catch (RuntimeException invalid) {
            throw new IOException("Invalid Minecraft resource-pack format", invalid);
        }
    }

    Optional<byte[]> readFontDefinition(String logicalPath) throws IOException {
        String normalized = logicalPath.replace('\\', '/');
        if (!normalized.startsWith("assets/minecraft/font/") || !normalized.endsWith(".json") ||
                normalized.contains("..")) {
            throw new IOException("Vanilla font path is outside Minecraft fonts: " + logicalPath);
        }
        return readEntry(normalized, MAXIMUM_MODEL_BYTES);
    }

    /** Vanilla data-pack files (for example {@code data/minecraft/worldgen/biome/}) below a prefix. */
    java.util.List<String> dataEntries(String prefix) throws IOException {
        if (!prefix.startsWith("data/minecraft/") || prefix.contains("..")) throw new IOException("Not vanilla data: " + prefix);
        ensureOpen();
        return archive.stream().map(ZipEntry::getName).filter(name -> name.startsWith(prefix) && !name.endsWith("/"))
                .sorted().toList();
    }

    Optional<byte[]> readData(String logicalPath) throws IOException {
        if (!logicalPath.startsWith("data/minecraft/") || logicalPath.contains("..")) throw new IOException("Not vanilla data: " + logicalPath);
        return readEntry(logicalPath, MAXIMUM_MODEL_BYTES);
    }

    Optional<byte[]> readModel(String logicalPath) throws IOException {
        String normalized = logicalPath.replace('\\', '/');
        if (!normalized.startsWith("assets/minecraft/models/") || !normalized.endsWith(".json") ||
                normalized.contains("..")) {
            throw new IOException("Vanilla model path is outside Minecraft models: " + logicalPath);
        }
        return readEntry(normalized, MAXIMUM_MODEL_BYTES);
    }

    Optional<byte[]> readSoundRegistry() throws IOException {
        Files.createDirectories(versionDirectory);
        Path asset = versionDirectory.resolve("sounds.json");
        Path checksum = versionDirectory.resolve("sounds.sha1");
        if (Files.isRegularFile(asset) && Files.isRegularFile(checksum)) {
            String expected = Files.readString(checksum).trim().toLowerCase();
            byte[] bytes = Files.readAllBytes(asset);
            if (bytes.length <= MAXIMUM_TEXTURE_BYTES && expected.matches("[0-9a-f]{40}") &&
                    expected.equals(sha1(bytes))) return Optional.of(bytes);
            Files.deleteIfExists(asset);
            Files.deleteIfExists(checksum);
        }
        if (!allowDownload) throw new IOException("Vanilla asset download is disabled and no verified sound registry exists for " + version);
        Download download = resolveSoundRegistry();
        if (download.size() < 1 || download.size() > MAXIMUM_TEXTURE_BYTES) {
            throw new IOException("Mojang sound registry size is outside the allowed range: " + download.size());
        }
        byte[] bytes = getBytes(download.uri(), (int) MAXIMUM_TEXTURE_BYTES);
        if (bytes.length != download.size()) throw new IOException("Mojang sound registry size mismatch");
        if (!download.sha1().equals(sha1(bytes))) throw new IOException("Mojang sound registry SHA-1 mismatch");
        Path temporary = versionDirectory.resolve("sounds-" + UUID.randomUUID() + ".tmp");
        try {
            Files.write(temporary, bytes);
            moveReplace(temporary, asset);
            Files.writeString(checksum, download.sha1() + System.lineSeparator());
        } finally {
            Files.deleteIfExists(temporary);
        }
        return Optional.of(bytes);
    }

    Optional<byte[]> readSound(String logicalPath) throws IOException {
        String normalized = logicalPath.replace('\\', '/');
        if (!normalized.startsWith("assets/minecraft/sounds/") || !normalized.endsWith(".ogg") ||
                normalized.contains("..")) {
            throw new IOException("Vanilla fallback path is outside Minecraft sounds: " + logicalPath);
        }
        String assetKey = normalized.substring("assets/".length());
        Download download = resolveAsset(assetKey);
        if (download == null) return Optional.empty();
        if (download.size() < 1 || download.size() > MAXIMUM_TEXTURE_BYTES) {
            throw new IOException("Mojang sound size is outside the allowed range: " + download.size());
        }
        Path object = versionDirectory.resolve("objects").resolve(download.sha1().substring(0, 2))
                .resolve(download.sha1()).normalize();
        if (!object.startsWith(versionDirectory.resolve("objects").normalize())) {
            throw new IOException("Unsafe Mojang sound cache path");
        }
        if (Files.isRegularFile(object) && Files.size(object) == download.size() &&
                download.sha1().equals(sha1(object))) return Optional.of(Files.readAllBytes(object));
        if (!allowDownload) throw new IOException("Vanilla asset download is disabled and no verified sound exists for " + assetKey);
        byte[] bytes = getBytes(download.uri(), (int) MAXIMUM_TEXTURE_BYTES);
        if (bytes.length != download.size()) throw new IOException("Mojang sound size mismatch: " + assetKey);
        if (!download.sha1().equals(sha1(bytes))) throw new IOException("Mojang sound SHA-1 mismatch: " + assetKey);
        Files.createDirectories(object.getParent());
        Path temporary = object.resolveSibling(download.sha1() + '-' + UUID.randomUUID() + ".tmp");
        try {
            Files.write(temporary, bytes);
            moveReplace(temporary, object);
        } finally {
            Files.deleteIfExists(temporary);
        }
        return Optional.of(bytes);
    }

    private Optional<byte[]> readEntry(String normalized, long maximumBytes) throws IOException {
        ensureOpen();
        ZipEntry entry = archive.getEntry(normalized);
        if (entry == null) return Optional.empty();
        if (entry.isDirectory() || entry.getSize() < 0 || entry.getSize() > maximumBytes) {
            throw new IOException("Vanilla asset entry has an unsafe size: " + normalized);
        }
        try (InputStream input = archive.getInputStream(entry)) {
            byte[] bytes = input.readNBytes((int) entry.getSize() + 1);
            if (bytes.length != entry.getSize()) throw new IOException("Vanilla asset entry size mismatch: " + normalized);
            return Optional.of(bytes);
        }
    }

    private void ensureOpen() throws IOException {
        if (archive != null) return;
        if (attempted) throw initializationFailure;
        attempted = true;
        try {
            Files.createDirectories(versionDirectory);
            Path jar = versionDirectory.resolve("client.jar");
            Path checksum = versionDirectory.resolve("client.sha1");
            if (Files.isRegularFile(jar) && Files.isRegularFile(checksum)) {
                String expected = Files.readString(checksum).trim().toLowerCase();
                if (expected.matches("[0-9a-f]{40}") && expected.equals(sha1(jar))) {
                    archive = new ZipFile(jar.toFile());
                    return;
                }
                Files.deleteIfExists(jar);
                Files.deleteIfExists(checksum);
            }
            if (!allowDownload) throw new IOException("Vanilla asset download is disabled and no verified cache exists for " + version);
            Download download = resolveDownload();
            if (download.size() < 1 || download.size() > MAXIMUM_CLIENT_BYTES) {
                throw new IOException("Mojang client size is outside the allowed range: " + download.size());
            }
            Path temporary = versionDirectory.resolve("client-" + UUID.randomUUID() + ".tmp");
            try {
                HttpResponse<Path> response = send(HttpRequest.newBuilder(download.uri())
                        .timeout(Duration.ofMinutes(2)).GET().build(), HttpResponse.BodyHandlers.ofFile(temporary));
                if (response.statusCode() != 200) throw new IOException("Mojang client download returned HTTP " + response.statusCode());
                if (Files.size(temporary) != download.size()) throw new IOException("Mojang client size mismatch");
                if (!download.sha1().equals(sha1(temporary))) throw new IOException("Mojang client SHA-1 mismatch");
                moveReplace(temporary, jar);
                Files.writeString(checksum, download.sha1() + System.lineSeparator());
            } finally {
                Files.deleteIfExists(temporary);
            }
            archive = new ZipFile(jar.toFile());
        } catch (IOException failure) {
            initializationFailure = failure;
            throw failure;
        }
    }

    private Download resolveDownload() throws IOException {
        JsonObject metadata = versionMetadata();
        JsonObject clientJson = metadata.getAsJsonObject("downloads").getAsJsonObject("client");
        return download(clientJson);
    }

    private Download resolveSoundRegistry() throws IOException {
        Download download = resolveAsset("minecraft/sounds.json");
        if (download == null) throw new IOException("Mojang asset index has no minecraft/sounds.json");
        return download;
    }

    private Download resolveAsset(String assetKey) throws IOException {
        JsonObject object = assetObjects().getAsJsonObject(assetKey);
        if (object == null) return null;
        String hash = object.get("hash").getAsString().toLowerCase();
        long size = object.get("size").getAsLong();
        if (!hash.matches("[0-9a-f]{40}")) throw new IOException("Invalid Mojang asset hash for " + assetKey);
        return new Download(allowed("https://resources.download.minecraft.net/" + hash.substring(0, 2) + '/' + hash),
                hash, size);
    }

    private JsonObject assetObjects() throws IOException {
        if (assetObjects != null) return assetObjects;
        Files.createDirectories(versionDirectory);
        Path index = versionDirectory.resolve("asset-index.json");
        Path checksum = versionDirectory.resolve("asset-index.sha1");
        byte[] bytes = null;
        if (Files.isRegularFile(index) && Files.isRegularFile(checksum)) {
            String expected = Files.readString(checksum).trim().toLowerCase();
            if (expected.matches("[0-9a-f]{40}") && expected.equals(sha1(index)) &&
                    Files.size(index) <= 32L * 1024 * 1024) bytes = Files.readAllBytes(index);
            else {
                Files.deleteIfExists(index);
                Files.deleteIfExists(checksum);
            }
        }
        if (bytes == null) {
            if (!allowDownload) throw new IOException("Vanilla asset download is disabled and no verified asset index exists for " + version);
            JsonObject metadata = versionMetadata();
            Download descriptor = download(metadata.getAsJsonObject("assetIndex"));
            if (descriptor.size() < 1 || descriptor.size() > 32L * 1024 * 1024) {
                throw new IOException("Mojang asset index size is outside the allowed range: " + descriptor.size());
            }
            bytes = getBytes(descriptor.uri(), 32 * 1024 * 1024);
            if (bytes.length != descriptor.size()) throw new IOException("Mojang asset index size mismatch");
            if (!descriptor.sha1().equals(sha1(bytes))) throw new IOException("Mojang asset index SHA-1 mismatch");
            Path temporary = versionDirectory.resolve("asset-index-" + UUID.randomUUID() + ".tmp");
            try {
                Files.write(temporary, bytes);
                moveReplace(temporary, index);
                Files.writeString(checksum, descriptor.sha1() + System.lineSeparator());
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
        JsonObject parsed = json(bytes, "asset index");
        assetObjects = parsed.getAsJsonObject("objects");
        if (assetObjects == null) throw new IOException("Mojang asset index has no objects map");
        return assetObjects;
    }

    private JsonObject versionMetadata() throws IOException {
        JsonObject manifest = json(getBytes(VERSION_MANIFEST, 4 * 1024 * 1024), "version manifest");
        JsonObject selected = null;
        for (var element : manifest.getAsJsonArray("versions")) {
            JsonObject candidate = element.getAsJsonObject();
            if (version.equals(candidate.get("id").getAsString())) { selected = candidate; break; }
        }
        if (selected == null) throw new IOException("Minecraft version is absent from Mojang manifest: " + version);
        URI metadataUri = allowed(selected.get("url").getAsString());
        byte[] metadataBytes = getBytes(metadataUri, 4 * 1024 * 1024);
        String metadataSha1 = selected.get("sha1").getAsString().toLowerCase();
        if (!metadataSha1.equals(sha1(metadataBytes))) throw new IOException("Mojang version metadata SHA-1 mismatch");
        return json(metadataBytes, "version metadata");
    }

    private static Download download(JsonObject descriptor) throws IOException {
        try {
            String sha1 = descriptor.get("sha1").getAsString().toLowerCase();
            if (!sha1.matches("[0-9a-f]{40}")) throw new IOException("Invalid Mojang download SHA-1");
            return new Download(allowed(descriptor.get("url").getAsString()), sha1,
                    descriptor.get("size").getAsLong());
        } catch (NullPointerException | IllegalStateException invalid) {
            throw new IOException("Invalid Mojang download descriptor", invalid);
        }
    }

    private byte[] getBytes(URI uri, int maximum) throws IOException {
        HttpResponse<byte[]> response = send(HttpRequest.newBuilder(allowed(uri.toString()))
                .timeout(Duration.ofSeconds(45)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) throw new IOException(uri + " returned HTTP " + response.statusCode());
        if (response.body().length > maximum) throw new IOException(uri + " exceeded metadata size limit");
        return response.body();
    }

    private <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) throws IOException {
        try { return client.send(request, handler); }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Mojang asset request was interrupted", interrupted);
        }
    }

    private static URI allowed(String value) throws IOException {
        URI uri;
        try { uri = URI.create(value); }
        catch (RuntimeException invalid) { throw new IOException("Invalid Mojang asset URI", invalid); }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !ALLOWED_HOSTS.contains(uri.getHost())) {
            throw new IOException("Untrusted Mojang asset URI: " + uri);
        }
        return uri;
    }

    private static JsonObject json(byte[] bytes, String label) throws IOException {
        try { return JsonParser.parseString(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject(); }
        catch (RuntimeException invalid) { throw new IOException("Invalid Mojang " + label, invalid); }
    }

    private static String requireSafeVersion(String value) {
        if (value == null || !value.matches("[0-9A-Za-z._-]{1,64}")) throw new IllegalArgumentException("Unsafe Minecraft version");
        return value;
    }

    private static String sha1(Path file) throws IOException {
        MessageDigest digest = sha1Digest();
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[65_536];
            for (int read; (read = input.read(buffer)) >= 0;) if (read > 0) digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String sha1(byte[] bytes) {
        return HexFormat.of().formatHex(sha1Digest().digest(bytes));
    }

    private static MessageDigest sha1Digest() {
        try { return MessageDigest.getInstance("SHA-1"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static SSLContext platformSslContext() {
        try {
            if (System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("windows")) {
                KeyStore roots = KeyStore.getInstance("Windows-ROOT");
                roots.load(null, null);
                TrustManagerFactory managers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                managers.init(roots);
                SSLContext context = SSLContext.getInstance("TLS");
                context.init(null, managers.getTrustManagers(), null);
                return context;
            }
            return SSLContext.getDefault();
        } catch (GeneralSecurityException | IOException failure) {
            throw new IllegalStateException("Could not initialize platform TLS trust", failure);
        }
    }

    private static void moveReplace(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override public void close() throws IOException {
        if (archive != null) archive.close();
    }

    private record Download(URI uri, String sha1, long size) {}
}
