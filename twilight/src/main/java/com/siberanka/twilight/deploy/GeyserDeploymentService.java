/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.deploy;

import com.siberanka.twilight.config.TwilightConfig;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.ZipFile;

public final class GeyserDeploymentService {
    private static final DateTimeFormatter SNAPSHOT_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    private final Path serverRoot;
    private final Path dataDirectory;
    private TwilightConfig config;
    private final Map<Path, Map<String, String>> startupMappings = new LinkedHashMap<>();
    private final ReentrantLock lock = new ReentrantLock();

    public GeyserDeploymentService(Path serverRoot, Path dataDirectory, TwilightConfig config) {
        this.serverRoot = serverRoot.toAbsolutePath().normalize();
        this.dataDirectory = dataDirectory.toAbsolutePath().normalize();
        this.config = config;
        try { captureStartupMappings(resolveGeyserDirectory()); }
        catch (IOException ignored) { /* Geyser may not be installed yet. Capture before the first deploy. */ }
    }

    public void reconfigure(TwilightConfig config) {
        lock.lock();
        try { this.config = config; }
        finally { lock.unlock(); }
    }

    public DeploymentResult deploy(Path outputDirectory) throws IOException {
        lock.lock();
        try {
            Path geyser = resolveGeyserDirectory();
            captureStartupMappings(geyser);
            Map<String, Path> artifacts = collectArtifacts(outputDirectory.toAbsolutePath().normalize());
            if (artifacts.isEmpty() && !config.sendPackToBedrock()) {
                // Nothing for Geyser: no item mappings, and the pack is sent by another plugin.
                Properties previous = loadManifest();
                for (String old : previous.stringPropertyNames()) Files.deleteIfExists(resolveTarget(geyser, old));
                storeManifest(new Properties());
                return new DeploymentResult(true, geyser, null, List.of(),
                        "The pack is not sent to Bedrock players (geyser.send-pack-to-bedrock: false); nothing to deploy.", false);
            }
            if (artifacts.isEmpty()) throw new IOException("No validated Twilight artifacts were found in " + outputDirectory);

            Properties previous = loadManifest();
            Path snapshot = snapshot(geyser, previous);
            Path staging = geyser.resolve(".twilight-staging-" + UUID.randomUUID()).normalize();
            ensureWithin(geyser, staging);
            boolean publicationStarted = false;
            try {
                stageAndVerify(staging, artifacts);
                // Read registry-bearing ZIP data before changing the deployed files.
                mappingHashes(staging);
                publicationStarted = true;
                publish(geyser, staging, artifacts, previous);
                Properties next = manifestFor(artifacts);
                storeManifest(next);
                retainNewestSnapshots();
                Set<String> keep = new java.util.HashSet<>(CANONICAL);
                keep.addAll(artifacts.keySet());
                var retired = com.siberanka.twilight.geyser.StaleFiles.retire(geyser, geyser.resolve("packs"), keep,
                        dataDirectory.resolve("retired"));
                boolean restart = !retired.isEmpty() || !startupMappings.get(geyser).equals(mappingHashes(geyser));
                return new DeploymentResult(true, geyser, snapshot, List.copyOf(artifacts.keySet()),
                        "Deployed " + artifacts.size() + " Twilight files; retained " + config.backupsToKeep() + " Geyser snapshots."
                                + (retired.isEmpty() ? "" : " Moved " + retired.size() + " stale Twilight file(s) out of Geyser to "
                                + dataDirectory.relativize(retired.getFirst().to().getParent().getParent()) + ": "
                                + String.join(", ", retired.stream().map(com.siberanka.twilight.geyser.StaleFiles.Retired::file).toList()) + ".")
                                + (restart ? " Restart the server to activate changed Geyser item mappings or display variants; geyser reload is insufficient." : ""), restart);
            } catch (Exception failure) {
                if (publicationStarted) {
                    for (String relative : artifacts.keySet()) Files.deleteIfExists(resolveTarget(geyser, relative));
                    if (snapshot != null) restoreSnapshot(geyser, snapshot);
                }
                if (failure instanceof IOException io) throw io;
                throw new IOException("Geyser deployment failed and was rolled back", failure);
            } finally {
                deleteTree(staging, geyser);
            }
        } finally {
            lock.unlock();
        }
    }

    /** Files Twilight deploys to Geyser under fixed names; kept even before the first deployment. */
    private static final Set<String> CANONICAL = Set.of("packs/twilight.zip", "custom_mappings/twilight_item_mappings.json");

    /**
     * Moves Twilight files this server does not own out of its Geyser ({@link com.siberanka.twilight.geyser.StaleFiles}):
     * item mappings and packs from older versions, copies or sync tools. Run before Geyser loads its folder (Twilight's
     * load phase) and after every deployment.
     */
    public List<com.siberanka.twilight.geyser.StaleFiles.Retired> retireStaleFiles() throws IOException {
        lock.lock();
        try {
            Path geyser = resolveGeyserDirectory();
            Set<String> keep = new java.util.HashSet<>(CANONICAL);
            for (String owned : loadManifest().stringPropertyNames()) keep.add(owned.replace('\\', '/'));
            return com.siberanka.twilight.geyser.StaleFiles.retire(geyser, geyser.resolve("packs"), keep,
                    dataDirectory.resolve("retired"));
        } finally {
            lock.unlock();
        }
    }

    public DeploymentResult rollback(int index) throws IOException {
        lock.lock();
        try {
            List<Path> snapshots = snapshots();
            if (index < 1 || index > snapshots.size()) throw new IOException("Rollback index must be between 1 and " + snapshots.size());
            Path geyser = resolveGeyserDirectory();
            captureStartupMappings(geyser);
            Path selected = snapshots.get(index - 1);
            restoreSnapshot(geyser, selected);
            Properties restored = readProperties(selected.resolve("deployment.properties"));
            storeManifest(restored);
            boolean restart = !startupMappings.get(geyser).equals(mappingHashes(geyser));
            return new DeploymentResult(true, geyser, selected, restored.stringPropertyNames().stream().sorted().toList(),
                    "Restored Geyser snapshot " + selected.getFileName()
                            + (restart ? "; restart the server to activate changed Geyser item mappings or display variants." : ""), restart);
        } finally {
            lock.unlock();
        }
    }

    public List<Path> snapshots() throws IOException {
        Path root = backupRoot();
        if (!Files.isDirectory(root)) return List.of();
        try (var stream = Files.list(root)) {
            return stream.filter(Files::isDirectory).sorted(Comparator.comparing(Path::getFileName).reversed()).toList();
        }
    }

    private void captureStartupMappings(Path geyser) throws IOException {
        if (!startupMappings.containsKey(geyser)) startupMappings.put(geyser, mappingHashes(geyser));
    }

    private static Map<String, String> mappingHashes(Path geyser) throws IOException {
        Path directory = geyser.resolve("custom_mappings");
        Map<String, String> hashes = new LinkedHashMap<>();
        if (Files.isDirectory(directory)) try (var files = Files.list(directory)) {
            for (Path file : files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith("twilight_")).sorted().toList()) {
                hashes.put(file.getFileName().toString(), sha256(file));
            }
        }
        // The display translator binds variant ordinals at startup. Textures can
        // reload, but changing that table requires rebuilding the runtime registry.
        Path pack = geyser.resolve("packs/twilight.zip");
        if (Files.isRegularFile(pack)) try (ZipFile zip = new ZipFile(pack.toFile())) {
            // Display variants and custom blocks are registered when Geyser starts, like item mappings.
            for (String registered : java.util.List.of("twilight/display-index-v2.json", "twilight/geyser_blocks.json")) {
                var entry = zip.getEntry(registered);
                if (entry != null) try (InputStream input = zip.getInputStream(entry)) {
                    hashes.put("packs/twilight.zip!" + registered, sha256(input));
                }
            }
        }
        return Map.copyOf(hashes);
    }

    public Path resolveGeyserDirectory() throws IOException {
        String configured = config.geyserDirectory().trim();
        if (!configured.equalsIgnoreCase("auto")) {
            Path selected = serverRoot.resolve(configured).normalize().toAbsolutePath();
            ensureWithin(serverRoot, selected);
            if (!Files.isDirectory(selected)) throw new IOException("Configured Geyser directory does not exist: " + selected);
            return selected;
        }
        Path plugins = serverRoot.resolve("plugins");
        if (!Files.isDirectory(plugins)) throw new IOException("Server plugins directory does not exist: " + plugins);
        try (var children = Files.list(plugins)) {
            return children.filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).startsWith("geyser-"))
                    .filter(path -> Files.isDirectory(path.resolve("packs")) || Files.isRegularFile(path.resolve("config.yml")))
                    .sorted().findFirst().orElseThrow(() -> new IOException("No local Geyser plugin data directory was found."));
        }
    }

    private Map<String, Path> collectArtifacts(Path output) throws IOException {
        ensureWithin(dataDirectory, output);
        if (!Files.isDirectory(output) || Files.isSymbolicLink(output)) return Map.of();
        Map<String, Path> files = new LinkedHashMap<>();
        // Without sending, Geyser keeps the item mappings but not the pack; a previously deployed pack is removed.
        if (config.sendPackToBedrock()) addArtifact(files, "packs/twilight.zip", output.resolve("pack.zip"), output);
        collectNamed(files, output.resolve("custom_mappings"), "custom_mappings", true, output);
        collectNamed(files, output.resolve("lang"), "locales/overrides", false, output);
        return files;
    }

    private void collectNamed(Map<String, Path> files, Path sourceDirectory, String targetDirectory,
                              boolean prefixMapping, Path outputRoot) throws IOException {
        if (!Files.isDirectory(sourceDirectory)) return;
        try (var stream = Files.list(sourceDirectory)) {
            for (Path source : stream.filter(Files::isRegularFile).sorted().toList()) {
                String name = source.getFileName().toString();
                if (prefixMapping) {
                    name = "twilight_" + name.replaceFirst("^geyser_", "");
                }
                addArtifact(files, targetDirectory + "/" + name, source, outputRoot);
            }
        }
    }

    private void addArtifact(Map<String, Path> files, String target, Path source, Path outputRoot) throws IOException {
        if (!Files.isRegularFile(source)) return;
        if (Files.isSymbolicLink(source) || !source.toRealPath().startsWith(outputRoot.toRealPath())) {
            throw new IOException("Artifact escapes output directory: " + source);
        }
        if (files.putIfAbsent(target.replace('\\', '/'), source) != null) throw new IOException("Duplicate deployment target: " + target);
    }

    private Path snapshot(Path geyser, Properties manifest) throws IOException {
        List<String> existing = new ArrayList<>();
        for (String relative : manifest.stringPropertyNames()) {
            if (Files.isRegularFile(resolveTarget(geyser, relative))) existing.add(relative);
        }
        existing.sort(String::compareTo);
        if (existing.isEmpty()) return null;
        Path snapshot = backupRoot().resolve(SNAPSHOT_TIME.format(LocalDateTime.now()));
        Files.createDirectories(snapshot.resolve("files"));
        for (String relative : existing) {
            Path source = resolveTarget(geyser, relative);
            Path destination = snapshot.resolve("files").resolve(relative).normalize();
            ensureWithin(snapshot.resolve("files"), destination);
            Files.createDirectories(destination.getParent());
            Files.copy(source, destination, StandardCopyOption.COPY_ATTRIBUTES);
        }
        writeProperties(snapshot.resolve("deployment.properties"), manifest);
        return snapshot;
    }

    private void stageAndVerify(Path staging, Map<String, Path> artifacts) throws IOException {
        for (Map.Entry<String, Path> artifact : artifacts.entrySet()) {
            Path target = staging.resolve(artifact.getKey()).normalize();
            ensureWithin(staging, target);
            Files.createDirectories(target.getParent());
            Files.copy(artifact.getValue(), target);
            if (!sha256(artifact.getValue()).equals(sha256(target))) throw new IOException("Staged hash mismatch: " + artifact.getKey());
        }
    }

    private void publish(Path geyser, Path staging, Map<String, Path> artifacts, Properties previous) throws IOException {
        for (String relative : artifacts.keySet()) {
            Path source = staging.resolve(relative);
            Path target = resolveTarget(geyser, relative);
            Files.createDirectories(target.getParent());
            moveReplace(source, target);
        }
        for (String old : previous.stringPropertyNames()) {
            if (!artifacts.containsKey(old)) Files.deleteIfExists(resolveTarget(geyser, old));
        }
    }

    private void restoreSnapshot(Path geyser, Path snapshot) throws IOException {
        Properties current = loadManifest();
        Properties restored = readProperties(snapshot.resolve("deployment.properties"));
        for (String relative : current.stringPropertyNames()) Files.deleteIfExists(resolveTarget(geyser, relative));
        for (String relative : restored.stringPropertyNames()) {
            Path source = snapshot.resolve("files").resolve(relative).normalize();
            ensureWithin(snapshot.resolve("files"), source);
            if (!Files.isRegularFile(source)) throw new IOException("Snapshot file is missing: " + relative);
            Path target = resolveTarget(geyser, relative);
            Files.createDirectories(target.getParent());
            Path temporary = target.resolveSibling(target.getFileName() + ".twilight-restore");
            Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
            moveReplace(temporary, target);
        }
    }

    private Properties manifestFor(Map<String, Path> artifacts) throws IOException {
        Properties properties = new Properties();
        for (Map.Entry<String, Path> artifact : artifacts.entrySet()) properties.setProperty(artifact.getKey(), sha256(artifact.getValue()));
        return properties;
    }

    private Properties loadManifest() throws IOException {
        return readProperties(dataDirectory.resolve("deployment.properties"));
    }

    private void storeManifest(Properties manifest) throws IOException {
        Files.createDirectories(dataDirectory);
        Path target = dataDirectory.resolve("deployment.properties");
        Path temporary = dataDirectory.resolve("deployment.properties.tmp");
        writeProperties(temporary, manifest);
        moveReplace(temporary, target);
    }

    private static Properties readProperties(Path path) throws IOException {
        Properties properties = new Properties();
        if (Files.isRegularFile(path)) {
            try (InputStream input = Files.newInputStream(path)) { properties.load(input); }
        }
        return properties;
    }

    private static void writeProperties(Path path, Properties properties) throws IOException {
        Files.createDirectories(path.getParent());
        try (OutputStream output = Files.newOutputStream(path)) { properties.store(output, "Twilight-owned Geyser deployment"); }
    }

    private Path resolveTarget(Path geyser, String relative) throws IOException {
        Path target = geyser.resolve(relative).normalize();
        ensureWithin(geyser, target);
        return target;
    }

    private Path backupRoot() {
        return dataDirectory.resolve("backups").resolve("geyser");
    }

    private void retainNewestSnapshots() throws IOException {
        List<Path> snapshots = snapshots();
        for (int index = config.backupsToKeep(); index < snapshots.size(); index++) deleteTree(snapshots.get(index), backupRoot());
    }

    private static void moveReplace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String sha256(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            return sha256(input);
        }
    }

    private static String sha256(InputStream input) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[65_536];
            for (int read; (read = input.read(buffer)) >= 0;) if (read > 0) digest.update(buffer, 0, read);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void ensureWithin(Path root, Path target) throws IOException {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedTarget = target.toAbsolutePath().normalize();
        if (!normalizedTarget.startsWith(normalizedRoot)) throw new IOException("Path escapes managed root: " + target);
    }

    private static void deleteTree(Path target, Path managedRoot) throws IOException {
        if (!Files.exists(target)) return;
        ensureWithin(managedRoot, target);
        try (var stream = Files.walk(target)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) {
                if (Files.isSymbolicLink(path)) throw new IOException("Refusing to delete symbolic path: " + path);
                Files.deleteIfExists(path);
            }
        }
    }
}
