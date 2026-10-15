package com.siberanka.twilight.source;

import com.siberanka.twilight.config.TwilightConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class SourceDiscovery {
    private static final Set<String> PROVIDERS = Set.of(
            "itemsadder", "craftengine", "nexo", "oraxen", "bettermodel", "modelengine", "realisticseasons",
            "customnameplates", "betterhud"
    );
    private static final Set<String> PACK_NAMES = Set.of(
            "generated.zip", "resource_pack.zip", "resourcepack.zip", "pack.zip", "build.zip", "resource pack.zip"
    );
    private static final Set<String> PROVIDER_DATA_NAMES = Set.of(
            "cache", ".cache", "data", ".data", "contents", "content", "resources"
    );
    /**
     * Never content: ItemsAdder's and Nexo's copies of the vanilla client assets (they would shadow
     * the server's packs with vanilla definitions) and temporary build folders that outlive builds.
     */
    private static final Set<String> IGNORED_DIRECTORIES = Set.of("vanilla_assets", ".assetcache", "tmp", "temp", ".tmp");

    private final Path serverRoot;
    private final TwilightConfig config;

    public SourceDiscovery(Path serverRoot, TwilightConfig config) {
        this.serverRoot = serverRoot.toAbsolutePath().normalize();
        this.config = config;
    }

    public List<ContentSource> discover(Set<Path> worlds) throws IOException {
        List<ContentSource> found = new ArrayList<>();
        Set<Path> seen = new HashSet<>();
        if (config.autoDiscoverSources()) {
            Path plugins = serverRoot.resolve("plugins");
            if (Files.isDirectory(plugins)) {
                try (var children = Files.list(plugins)) {
                    for (Path pluginDirectory : children.filter(Files::isDirectory).toList()) {
                        String provider = pluginDirectory.getFileName().toString().toLowerCase(Locale.ROOT);
                        if (!PROVIDERS.contains(provider)) continue;
                        String mode = config.sourceMode(provider);
                        if (mode.equals("off")) continue;
                        discoverProvider(provider, mode, pluginDirectory, found, seen);
                        if (provider.equals("itemsadder") && !mode.equals("contents")) {
                            discoverRenamedItemsAdderOutput(pluginDirectory, found, seen);
                        }
                        if (!mode.equals("generated")) discoverMergedPacks(provider, pluginDirectory, plugins, found, seen);
                    }
                }
            }
            if (config.datapackSources()) for (Path world : worlds) discoverDatapacks(world, found, seen);
        }
        for (Path additional : config.additionalSources()) {
            addIfPack("configured", ContentSource.Kind.RESOURCE_PACK, additional, 900, found, seen);
        }
        demoteUndeliveredPacks(found);
        found.sort(Comparator.comparingInt(ContentSource::priority).thenComparing(source -> source.path().toString()));
        return List.copyOf(found);
    }

    /**
     * @param mode {@code auto}: the generated pack Java players receive outranks the working folders, which fill
     *             gaps; {@code generated}: only the generated pack; {@code contents}: only the working folders.
     *             Item and model metadata are read in every mode.
     */
    private void discoverProvider(String provider, String mode, Path directory, List<ContentSource> found,
                                  Set<Path> seen) throws IOException {
        try (var paths = Files.walk(directory, 6)) {
            for (Path path : paths.toList()) {
                if (Files.isSymbolicLink(path) || ignored(directory, path)) continue;
                String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                boolean knownArchive = Files.isRegularFile(path) && PACK_NAMES.contains(name)
                        // ItemsAdder's working pack folder may keep a stale self-hosted pack.zip from
                        // older versions; its current output lives in output/.
                        && !(provider.equals("itemsadder") && insidePack(path, directory));
                boolean packDirectory = Files.isDirectory(path) &&
                        (Files.isRegularFile(path.resolve("pack.mcmeta")) || Files.isDirectory(path.resolve("assets")))
                        && !insidePack(path, directory);
                boolean modelSource = Files.isDirectory(path) && path.getParent() != null &&
                        path.getParent().equals(directory) && (name.equals("blueprints") || name.equals("models"));
                if (knownArchive && mode.equals("contents") || packDirectory && mode.equals("generated")) continue;
                if (knownArchive || packDirectory) {
                    ContentSource.Kind kind = provider.equals("realisticseasons")
                            ? ContentSource.Kind.SEASONAL_PACK : ContentSource.Kind.RESOURCE_PACK;
                    add(provider, kind, path, sourcePriority(provider, directory, path, knownArchive), found, seen);
                } else if (modelSource && (provider.equals("modelengine") || provider.equals("bettermodel"))) {
                    add(provider, ContentSource.Kind.MODEL_SOURCE, path, providerPriority(provider) + 90, found, seen);
                } else if (path.getParent() != null && path.getParent().equals(directory) &&
                        Files.isDirectory(path) && PROVIDER_DATA_NAMES.contains(name)) {
                    add(provider, ContentSource.Kind.PROVIDER_DATA, path,
                            providerPriority(provider) + metadataPriority(name), found, seen);
                }
            }
        }
    }

    /**
     * ItemsAdder can be configured to write its pack under another name than generated.zip
     * (e.g. generated_1.21.x.zip or the hosted file name). Without generated.zip, the most
     * recently written archive in output/ is the pack ItemsAdder last produced.
     */
    private void discoverRenamedItemsAdderOutput(Path directory, List<ContentSource> found, Set<Path> seen) throws IOException {
        Path output = directory.resolve("output");
        if (!Files.isDirectory(output) || Files.isSymbolicLink(output) || Files.isRegularFile(output.resolve("generated.zip"))) return;
        Path newest = null;
        java.nio.file.attribute.FileTime newestTime = null;
        try (var children = Files.list(output)) {
            for (Path candidate : children.filter(Files::isRegularFile).filter(path -> !Files.isSymbolicLink(path))
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip"))
                    .sorted().toList()) {
                var modified = Files.getLastModifiedTime(candidate);
                if (newestTime == null || modified.compareTo(newestTime) > 0) {
                    newest = candidate;
                    newestTime = modified;
                }
            }
        }
        if (newest != null) add("itemsadder", ContentSource.Kind.RESOURCE_PACK, newest, providerPriority("itemsadder") + 90, found, seen);
    }

    /**
     * Java players only receive packs from providers that run and send them. A provider folder
     * whose plugin is not installed is leftover data, and a provider whose settings disable sending
     * (when another running provider sends a generated pack) is not what players see. Such sources
     * keep filling gaps, but at the lowest priority, so the delivered pack wins where they disagree.
     */
    private void demoteUndeliveredPacks(List<ContentSource> found) {
        Path plugins = serverRoot.resolve("plugins");
        Set<String> providers = new HashSet<>();
        for (ContentSource source : found) {
            if (source.kind() == ContentSource.Kind.RESOURCE_PACK && PROVIDERS.contains(source.provider())) providers.add(source.provider());
        }
        Set<String> notInstalled = new HashSet<>(), notSending = new HashSet<>();
        for (String provider : providers) {
            if (!ProviderDelivery.installed(plugins, provider)) notInstalled.add(provider);
            else if (!ProviderDelivery.delivers(plugins, provider)) notSending.add(provider);
        }
        boolean deliveredArchive = found.stream().anyMatch(source -> source.kind() == ContentSource.Kind.RESOURCE_PACK
                && PROVIDERS.contains(source.provider()) && !notInstalled.contains(source.provider())
                && !notSending.contains(source.provider()) && Files.isRegularFile(source.path())
                && source.priority() >= providerPriority(source.provider()) + 90);
        Set<String> demoted = new HashSet<>(notInstalled);
        if (deliveredArchive) demoted.addAll(notSending);
        if (demoted.isEmpty()) return;
        found.replaceAll(source -> source.kind() == ContentSource.Kind.RESOURCE_PACK && demoted.contains(source.provider())
                ? new ContentSource(source.provider(), source.kind(), source.path(), source.priority() - 500) : source);
    }

    private static boolean ignored(Path providerRoot, Path path) {
        for (Path part : providerRoot.relativize(path)) {
            if (IGNORED_DIRECTORIES.contains(part.toString().toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private static boolean insidePack(Path path, Path providerRoot) {
        for (Path ancestor = path.getParent(); ancestor != null && ancestor.startsWith(providerRoot); ancestor = ancestor.getParent()) {
            if (Files.isRegularFile(ancestor.resolve("pack.mcmeta"))) return true;
        }
        return false;
    }

    private void discoverDatapacks(Path world, List<ContentSource> found, Set<Path> seen) throws IOException {
        Path datapacks = world.resolve("datapacks");
        if (!Files.isDirectory(datapacks) || Files.isSymbolicLink(datapacks)) return;
        try (var children = Files.list(datapacks)) {
            for (Path path : children.toList()) {
                if (Files.isSymbolicLink(path)) continue;
                if ((Files.isDirectory(path) && Files.isRegularFile(path.resolve("pack.mcmeta"))) ||
                        (Files.isRegularFile(path) && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip"))) {
                    add("datapack:" + world.getFileName(), ContentSource.Kind.DATAPACK, path, 700, found, seen);
                }
            }
        }
    }

    /** The keys under which pack-building providers list other plugins' packs they merge into their own. */
    static final Map<String, List<String>> MERGE_KEYS = Map.of(
            "itemsadder", List.of("merge_other_plugins_resourcepacks_folders"),
            "craftengine", List.of("merge-external-folders", "merge-external-zip-files"));

    /**
     * The other plugins' packs a provider merges into the pack it builds (ItemsAdder's
     * {@code merge_other_plugins_resourcepacks_folders}, CraftEngine's {@code merge-external-folders} and
     * {@code merge-external-zip-files}), paths relative to {@code plugins/}. When the provider's generated pack exists
     * it already holds them, merged the way Java players receive them (fonts, sounds and atlases combined into one
     * pack), so they are read only while the provider has not generated its pack yet.
     */
    private void discoverMergedPacks(String provider, Path directory, Path plugins, List<ContentSource> found,
                                     Set<Path> seen) {
        List<String> keys = MERGE_KEYS.get(provider);
        Path config = directory.resolve("config.yml");
        if (keys == null || !Files.isRegularFile(config)) return;
        boolean generated = found.stream().anyMatch(source -> source.provider().equals(provider)
                && source.kind() == ContentSource.Kind.RESOURCE_PACK && Files.isRegularFile(source.path()));
        if (generated) return;
        List<String> lines;
        try {
            lines = Files.readAllLines(config);
        } catch (IOException | RuntimeException unreadable) {
            return;
        }
        Path root;
        try {
            root = plugins.toRealPath();
        } catch (IOException unreadable) {
            return;
        }
        for (String entry : yamlList(lines, keys)) {
            try {
                Path path = plugins.resolve(entry).normalize();
                if (!path.startsWith(plugins) || !Files.exists(path) || Files.isSymbolicLink(path)) continue;
                if (!path.toRealPath().startsWith(root)) continue;
                boolean archive = Files.isRegularFile(path) && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip");
                boolean pack = Files.isDirectory(path)
                        && (Files.isRegularFile(path.resolve("pack.mcmeta")) || Files.isDirectory(path.resolve("assets")));
                if (archive || pack) add(provider, ContentSource.Kind.RESOURCE_PACK, path, providerPriority(provider) + 70, found, seen);
            } catch (IOException | RuntimeException invalid) {
                // An entry that is not a usable path is skipped, as the provider skips it.
            }
        }
    }

    /** The items of the first YAML block list under any of the keys, at any depth. */
    static List<String> yamlList(List<String> lines, List<String> keys) {
        List<String> values = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            String trimmed = line.strip();
            int colon = trimmed.indexOf(':');
            if (colon <= 0 || !keys.contains(trimmed.substring(0, colon).strip())) continue;
            int indent = line.length() - line.stripLeading().length();
            String rest = trimmed.substring(colon + 1).strip();
            if (rest.startsWith("[") && rest.endsWith("]")) {
                for (String part : rest.substring(1, rest.length() - 1).split(",")) addListValue(values, part);
                continue;
            }
            for (int next = index + 1; next < lines.size(); next++) {
                String item = lines.get(next);
                if (item.isBlank() || item.stripLeading().startsWith("#")) continue;
                int itemIndent = item.length() - item.stripLeading().length();
                if (!item.stripLeading().startsWith("- ") || itemIndent < indent) break;
                addListValue(values, item.stripLeading().substring(2));
            }
        }
        return values;
    }

    private static void addListValue(List<String> values, String raw) {
        String value = raw.strip();
        int comment = value.indexOf(" #");
        if (comment >= 0) value = value.substring(0, comment).strip();
        if (value.length() >= 2 && (value.startsWith("'") && value.endsWith("'") || value.startsWith("\"") && value.endsWith("\""))) {
            value = value.substring(1, value.length() - 1);
        }
        if (!value.isEmpty() && !value.contains("..")) values.add(value.replace('\\', '/'));
    }

    private void addIfPack(String provider, ContentSource.Kind kind, Path path, int priority,
                           List<ContentSource> found, Set<Path> seen) throws IOException {
        if (!Files.exists(path) || Files.isSymbolicLink(path)) throw new IOException("Configured source is missing or symbolic: " + path);
        add(provider, kind, path, priority, found, seen);
    }

    private void add(String provider, ContentSource.Kind kind, Path path, int priority,
                     List<ContentSource> found, Set<Path> seen) throws IOException {
        Path real = path.toRealPath();
        if (seen.add(real)) found.add(new ContentSource(provider, kind, real, priority));
    }

    private static int providerPriority(String provider) {
        return switch (provider) {
            case "modelengine", "bettermodel" -> 500;
            case "realisticseasons" -> 800;
            default -> 600;
        };
    }

    /**
     * A provider's generated archive is exactly what Java players download, so it outranks the
     * provider's working folders (authored contents, data and caches can be stale or partial);
     * those folders still supply files the archive lacks.
     */
    private static int sourcePriority(String provider, Path providerRoot, Path source, boolean archive) {
        int base = providerPriority(provider);
        if (archive) return base + 90;
        Path relative = providerRoot.relativize(source);
        for (Path part : relative) {
            String name = part.toString().toLowerCase(Locale.ROOT);
            if (name.equals("contents") || name.equals("content") || name.equals("resources")) return base + 80;
            if (name.equals("data") || name.equals(".data")) return base + 60;
            if (name.equals("cache") || name.equals(".cache") || name.equals("storage")) return base + 40;
        }
        return base + 20;
    }

    private static int metadataPriority(String name) {
        return switch (name) {
            case "contents", "content", "resources" -> 79;
            case "data", ".data" -> 59;
            default -> 39;
        };
    }
}
