/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.proxy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Bedrock pack of every backend server, from its configured source: Twilight on that server
 * (cached in {@code cache/}), a file in {@code packs/}, or a download link (cached, refreshed with
 * the server's ETag). Every pack is checked ({@link PackFiles#inspect}) before it is used; a failed
 * download or transfer keeps the previous pack.
 *
 * <p>Geyser reads a pack file again for every chunk it sends, so it is given an immutable copy of each
 * version ({@code cache/versions/<sha256>.mcpack}): a pack rebuilt or replaced while a large download runs
 * cannot change under it. A replaced version is kept until every reconnect that may still use it is over.
 */
final class PackStore {
    private final Platform platform;
    private final Path packs;
    private final Path cache;
    private final Path versions;
    private final Map<String, PackFiles.Pack> current = new ConcurrentHashMap<>();
    /** Version files no longer current -> when they were replaced. */
    private final Map<Path, Long> retired = new ConcurrentHashMap<>();
    private volatile java.util.function.Consumer<PackFiles.Pack> listener = pack -> {};
    private boolean started;
    private final Set<String> downloading = ConcurrentHashMap.newKeySet();
    private final Set<String> known = ConcurrentHashMap.newKeySet();
    private volatile ProxyConfig config;
    private volatile HttpClient http;

    PackStore(Platform platform) {
        this.platform = platform;
        this.packs = platform.dataDirectory().resolve("packs");
        this.cache = platform.dataDirectory().resolve("cache");
        this.versions = cache.resolve("versions");
    }

    /** Called (off the network threads) with every pack that becomes current. */
    void onChange(java.util.function.Consumer<PackFiles.Pack> listener) {
        this.listener = listener;
    }

    /** Applies a configuration: loads files and caches now, downloads links in the background. */
    void reload(ProxyConfig config, Iterable<String> servers) throws IOException {
        this.config = config;
        this.http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(Math.min(30, config.downloadTimeoutSeconds()))).build();
        Files.createDirectories(packs);
        Files.createDirectories(cache);
        Files.createDirectories(versions);
        Map<String, PackFiles.Pack> before = Map.copyOf(current);
        current.clear();
        known.clear();
        for (String server : servers) known.add(server.toLowerCase(Locale.ROOT));
        known.addAll(config.servers().keySet());
        for (String server : known) load(server);
        before.values().forEach(this::retireIfUnused);
        if (!started) {
            started = true;
            // Nothing can use an old version before the first session: keep only the current ones.
            try (var files = Files.newDirectoryStream(versions)) {
                for (Path file : files) if (!inUse(file)) Files.deleteIfExists(file);
            }
        }
    }

    /** The immutable copy of the pack version {@code hex}, if this store still has it. */
    Optional<Path> versionPath(String hex) {
        if (hex == null || !hex.matches("[0-9a-f]{64}")) return Optional.empty();
        Path file = versions.resolve(hex + ".mcpack");
        return Files.isRegularFile(file) ? Optional.of(file) : Optional.empty();
    }

    /** Every server's current pack file. */
    java.util.Collection<Path> currentPacks() {
        return current.values().stream().map(PackFiles.Pack::path).distinct().toList();
    }

    Optional<PackFiles.Pack> pack(String server) {
        return Optional.ofNullable(current.get(server.toLowerCase(Locale.ROOT)));
    }

    boolean auto(String server) {
        return config.source(server) instanceof ProxyConfig.PackSource.Auto;
    }

    /** Re-downloads every linked pack whose content changed (ETag), in the background. */
    void refreshLinks() {
        ProxyConfig snapshot = config;
        for (var entry : snapshot.servers().entrySet()) {
            if (entry.getValue() instanceof ProxyConfig.PackSource.Url url) download(entry.getKey(), url.uri());
        }
        if (snapshot.defaultSource() instanceof ProxyConfig.PackSource.Url url) download("*default", url.uri());
    }

    /** A pack Twilight on {@code server} sent; replaces the cached one when it checks out. */
    void acceptAuto(String server, Path received, long seconds) throws IOException {
        PackFiles.Pack checked = PackFiles.inspect(received, config.maxPackBytes());
        Path target = cache.resolve(PackFiles.safeName(server) + ".mcpack");
        PackFiles.moveInto(received, target);
        use(server.toLowerCase(Locale.ROOT), new PackFiles.Pack(target, checked.sha256(), checked.size()));
        platform.info("Received the Bedrock pack of " + server + " from Twilight (" + checked.size() / 1024 + " KiB, "
                + checked.hex().substring(0, 12) + ", " + seconds + " s).");
    }

    /**
     * The export of Twilight on {@code server}, read from its folder on this machine. Replaces the cached pack when
     * it differs; returns true when it did.
     */
    boolean acceptLocal(String server, Path export) throws IOException {
        String key = server.toLowerCase(Locale.ROOT);
        if (Files.isSymbolicLink(export)) throw new IOException("the export is a symbolic link");
        PackFiles.Pack checked = PackFiles.inspect(export, config.maxPackBytes());
        PackFiles.Pack have = current.get(key);
        if (have != null && MessageDigest.isEqual(have.sha256(), checked.sha256())) return false;
        Path temporary = temporaryFile(server);
        try {
            Files.copy(export, temporary, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            if (!MessageDigest.isEqual(PackFiles.sha256(temporary), checked.sha256())) {
                throw new IOException("the export changed while it was read");
            }
            Path target = cache.resolve(PackFiles.safeName(server) + ".mcpack");
            PackFiles.moveInto(temporary, target);
            use(key, new PackFiles.Pack(target, checked.sha256(), checked.size()));
        } finally {
            Files.deleteIfExists(temporary);
        }
        return true;
    }

    /** Makes {@code pack} the current one of {@code server}, through an immutable version copy. */
    private void use(String server, PackFiles.Pack pack) throws IOException {
        PackFiles.Pack version = version(pack);
        PackFiles.Pack previous = current.put(server, version);
        if (previous != null && !previous.path().equals(version.path())) retireIfUnused(previous);
        if (previous == null || !previous.path().equals(version.path())) {
            java.util.function.Consumer<PackFiles.Pack> notify = listener;
            platform.async(() -> notify.accept(version));
        }
    }

    private PackFiles.Pack version(PackFiles.Pack pack) throws IOException {
        Path target = versions.resolve(pack.hex() + ".mcpack");
        retired.remove(target);
        if (!Files.isRegularFile(target) || Files.size(target) != pack.size()) {
            Files.createDirectories(versions);
            Path temporary = Files.createTempFile(versions, "copy-", ".part");
            try {
                Files.copy(pack.path(), temporary, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                if (!java.security.MessageDigest.isEqual(PackFiles.sha256(temporary), pack.sha256())) {
                    throw new IOException("the pack changed while it was copied");
                }
                PackFiles.moveInto(temporary, target);
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
        return new PackFiles.Pack(target, pack.sha256(), pack.size());
    }

    private boolean inUse(Path file) {
        return current.values().stream().anyMatch(pack -> pack.path().equals(file));
    }

    private void retireIfUnused(PackFiles.Pack pack) {
        if (pack.path().startsWith(versions) && !inUse(pack.path())) retired.putIfAbsent(pack.path(), System.currentTimeMillis());
    }

    /** Deletes versions replaced longer than {@code keepMillis} ago (no reconnect can still be loading them). */
    void sweepVersions(long keepMillis) {
        long now = System.currentTimeMillis();
        retired.entrySet().removeIf(entry -> {
            if (inUse(entry.getKey())) return true;
            if (now - entry.getValue() < keepMillis) return false;
            try {
                Files.deleteIfExists(entry.getKey());
            } catch (IOException locked) {
                return false;
            }
            return true;
        });
    }

    Path temporaryFile(String server) throws IOException {
        Files.createDirectories(cache);
        return Files.createTempFile(cache, PackFiles.safeName(server) + "-", ".part");
    }

    private void load(String server) {
        ProxyConfig.PackSource source = config.source(server);
        try {
            if (source instanceof ProxyConfig.PackSource.Auto) {
                Path cached = cache.resolve(PackFiles.safeName(server) + ".mcpack");
                if (Files.isRegularFile(cached)) use(server, PackFiles.inspect(cached, config.maxPackBytes()));
            } else if (source instanceof ProxyConfig.PackSource.File file) {
                Path path = packs.resolve(file.name()).normalize();
                if (!path.startsWith(packs)) throw new IOException("pack file outside packs/");
                use(server, PackFiles.inspect(path, config.maxPackBytes()));
            } else if (source instanceof ProxyConfig.PackSource.Url url) {
                Path cached = linkCache(url.uri());
                if (Files.isRegularFile(cached)) use(server, PackFiles.inspect(cached, config.maxPackBytes()));
                download(server, url.uri());
            }
        } catch (IOException failure) {
            platform.warn("The Bedrock pack of " + server + " is unavailable: " + failure.getMessage(), null);
        }
    }

    private Path linkCache(URI uri) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(uri.toString().getBytes(StandardCharsets.UTF_8));
            return cache.resolve("link-" + HexFormat.of().formatHex(hash, 0, 12) + ".mcpack");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private void download(String server, URI uri) {
        String key = uri.toString();
        if (!downloading.add(key)) return;
        ProxyConfig snapshot = config;
        HttpClient client = http;
        platform.async(() -> {
            Path target = linkCache(uri);
            Path etagFile = target.resolveSibling(target.getFileName() + ".etag");
            Path partial = null;
            try {
                HttpRequest.Builder request = HttpRequest.newBuilder(uri).GET()
                        .timeout(Duration.ofSeconds(snapshot.downloadTimeoutSeconds()))
                        .header("User-Agent", "twilight-proxy");
                if (Files.isRegularFile(target) && Files.isRegularFile(etagFile) && Files.size(etagFile) < 512) {
                    request.header("If-None-Match", Files.readString(etagFile).strip());
                }
                HttpResponse<InputStream> response = client.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
                try (InputStream body = response.body()) {
                    if (response.statusCode() == 304) return;
                    if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode());
                    String scheme = response.uri().getScheme();
                    if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) throw new IOException("redirected off http(s)");
                    long declared = response.headers().firstValueAsLong("Content-Length").orElse(-1);
                    if (declared > snapshot.maxPackBytes()) throw new IOException("the pack is larger than max-pack-size-mb");
                    partial = temporaryFile(server);
                    long started = System.currentTimeMillis();
                    long[] copied = {0};
                    long[] lastProgress = {started};
                    // Idle limit: download-timeout-seconds without data; overall: that plus the size at 64 KiB/s.
                    long idleMillis = snapshot.downloadTimeoutSeconds() * 1000L;
                    long overallMillis = idleMillis + Math.max(declared, 0) * 1000 / (64 * 1024);
                    String[] stopped = {null};
                    Thread watchdog = Thread.ofVirtual().start(() -> {
                        try {
                            while (true) {
                                Thread.sleep(1000);
                                long now = System.currentTimeMillis();
                                if (now - lastProgress[0] > idleMillis) stopped[0] = "no data for " + idleMillis / 1000 + " s";
                                else if (declared > 0 && now - started > overallMillis) stopped[0] = "slower than 64 KiB/s";
                                if (stopped[0] != null) {
                                    body.close();
                                    return;
                                }
                            }
                        } catch (InterruptedException | IOException finished) {
                            // done
                        }
                    });
                    byte[] buffer = new byte[65_536];
                    try (OutputStream out = Files.newOutputStream(partial)) {
                        for (int read; (read = body.read(buffer)) >= 0;) {
                            copied[0] += read;
                            lastProgress[0] = System.currentTimeMillis();
                            if (copied[0] > snapshot.maxPackBytes()) throw new IOException("the pack is larger than max-pack-size-mb");
                            out.write(buffer, 0, read);
                        }
                    } catch (IOException failure) {
                        throw stopped[0] != null ? new IOException("download stopped: " + stopped[0], failure) : failure;
                    } finally {
                        watchdog.interrupt();
                    }
                    if (stopped[0] != null) throw new IOException("download stopped: " + stopped[0]);
                    PackFiles.Pack checked = PackFiles.inspect(partial, snapshot.maxPackBytes());
                    PackFiles.moveInto(partial, target);
                    partial = null;
                    String etag = response.headers().firstValue("ETag").orElse("");
                    if (!etag.isEmpty() && etag.length() < 256) Files.writeString(etagFile, etag);
                    else Files.deleteIfExists(etagFile);
                    for (String name : known) {
                        if (snapshot.source(name) instanceof ProxyConfig.PackSource.Url other && other.uri().equals(uri)) {
                            use(name, new PackFiles.Pack(target, checked.sha256(), checked.size()));
                        }
                    }
                    platform.info("Downloaded the Bedrock pack " + uri.getHost() + uri.getPath() + " (" + checked.size() / 1024 + " KiB).");
                }
            } catch (IOException | RuntimeException failure) {
                platform.warn("Could not download the Bedrock pack " + uri.getHost() + uri.getPath() + ": " + failure.getMessage(), null);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                if (partial != null) try { Files.deleteIfExists(partial); } catch (IOException ignored) { }
                downloading.remove(key);
            }
        });
    }
}
