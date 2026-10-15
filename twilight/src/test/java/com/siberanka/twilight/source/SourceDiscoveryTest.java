package com.siberanka.twilight.source;

import com.siberanka.twilight.config.TwilightConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceDiscoveryTest {
    @TempDir Path root;

    @Test
    void disablingAutomaticDiscoveryStillHonorsExplicitSources() throws Exception {
        Path automatic = Files.createDirectories(root.resolve("plugins/ItemsAdder/output"));
        Files.writeString(automatic.resolve("pack.mcmeta"), "{}");
        Path datapack = Files.createDirectories(root.resolve("world/datapacks/example"));
        Files.writeString(datapack.resolve("pack.mcmeta"), "{}");
        Path explicit = Files.createDirectories(root.resolve("explicit"));
        Files.writeString(explicit.resolve("pack.mcmeta"), "{}");

        TwilightConfig config = new TwilightConfig(false, true, false, true, 40, 100, 10_000_000, 1000,
                true, false, List.of(explicit), "auto", false, false, 3);
        List<ContentSource> sources = new SourceDiscovery(root, config)
                .discover(Set.of(root.resolve("world")));

        assertEquals(List.of(explicit.toRealPath()), sources.stream().map(ContentSource::path).toList());
    }

    @Test
    void generatedPackIsAuthoritativeAndWorkingFoldersFillGaps() throws Exception {
        // Java players download the generated pack; working folders may hold stale copies (real case:
        // 2022 item models in ItemsAdder's data/resource_pack next to a 2026 generated.zip).
        Path provider = Files.createDirectories(root.resolve("plugins/ItemsAdder"));
        Path generated = Files.createDirectories(provider.resolve("output"));
        Files.write(generated.resolve("generated.zip"), zipWith("assets/demo/models/item/shared.json", "{\"layer\":\"generated\"}"));
        Path cache = Files.createDirectories(provider.resolve("storage/cache/resource_pack/assets/demo"));
        Files.writeString(cache.resolve("cached.json"), "{}");
        Files.createDirectories(cache.resolve("models/item"));
        Files.writeString(cache.resolve("models/item/shared.json"), "{\"layer\":\"cache\"}");
        Path data = Files.createDirectories(provider.resolve("data/resource_pack/assets/demo"));
        Files.writeString(data.resolve("data.json"), "{}");
        Files.createDirectories(data.resolve("models/item"));
        Files.writeString(data.resolve("models/item/shared.json"), "{\"layer\":\"data\"}");
        Path contents = Files.createDirectories(provider.resolve("contents/example/resourcepack/assets/demo"));
        Files.writeString(contents.resolve("authored.json"), "{}");
        Files.createDirectories(contents.resolve("models/item"));
        Files.writeString(contents.resolve("models/item/shared.json"), "{\"layer\":\"contents\"}");
        Files.createDirectories(provider.resolve("contents/example/configs"));
        Files.writeString(provider.resolve("contents/example/configs/items.yml"), "info: {}\n");

        TwilightConfig config = new TwilightConfig(false, true, true, true, 40, 100, 10_000_000, 1000,
                true, true, List.of(), "auto", false, false, 3);
        List<ContentSource> sources = new SourceDiscovery(root, config).discover(Set.of());

        ContentSource generatedSource = source(sources, "generated.zip");
        ContentSource cachedSource = source(sources, "resource_pack", "storage");
        ContentSource dataSource = source(sources, "resource_pack", "data");
        ContentSource authoredSource = source(sources, "resourcepack", "contents");
        assertTrue(cachedSource.priority() < dataSource.priority());
        assertTrue(dataSource.priority() < authoredSource.priority());
        assertTrue(authoredSource.priority() < generatedSource.priority());
        assertTrue(sources.stream().anyMatch(source -> source.kind() == ContentSource.Kind.PROVIDER_DATA &&
                source.path().getFileName().toString().equalsIgnoreCase("contents")));
        try (ResourceIndex resources = ResourceIndex.build(sources, config)) {
            assertEquals("{\"layer\":\"generated\"}", resources.find("assets/demo/models/item/shared.json")
                    .orElseThrow().readUtf8());
            assertTrue(resources.find("provider-data/itemsadder/contents/example/configs/items.yml").isPresent());
            assertTrue(resources.find("assets/demo/authored.json").isPresent(), "working folders fill gaps");
        }
    }

    @Test
    void ignoresVanillaAssetCopiesTemporaryBuildsAndStaleNestedPacks() throws Exception {
        // Real ItemsAdder folders: storage/cache/vanilla_assets/<version> holds the vanilla client assets,
        // storage/cache/tmp a leftover build folder, data/resource_pack/pack.zip an old self-hosted pack.
        Path provider = Files.createDirectories(root.resolve("plugins/ItemsAdder"));
        Files.createDirectories(provider.resolve("output"));
        Files.write(provider.resolve("output/generated.zip"), emptyZip());
        for (String stale : List.of("storage/cache/vanilla_assets/26.2", "storage/cache/tmp/resource_pack")) {
            Path pack = Files.createDirectories(provider.resolve(stale).resolve("assets/minecraft/font"));
            Files.writeString(pack.resolve("default.json"), "{\"providers\":[]}");
            Files.writeString(provider.resolve(stale).resolve("pack.mcmeta"), "{}");
        }
        Path working = Files.createDirectories(provider.resolve("data/resource_pack/assets/demo"));
        Files.writeString(working.getParent().getParent().resolve("pack.mcmeta"), "{}");
        Files.write(working.getParent().getParent().resolve("pack.zip"), emptyZip());

        TwilightConfig config = new TwilightConfig(false, true, true, true, 40, 100, 10_000_000, 1000,
                true, true, List.of(), "auto", false, false, 3);
        List<ContentSource> sources = new SourceDiscovery(root, config).discover(Set.of());
        Path real = root.toRealPath();
        assertTrue(sources.stream().noneMatch(source -> real.relativize(source.path()).toString().contains("vanilla_assets")), sources.toString());
        assertTrue(sources.stream().noneMatch(source -> real.relativize(source.path()).toString().contains("tmp")), sources.toString());
        assertTrue(sources.stream().noneMatch(source -> source.path().getFileName().toString().equals("pack.zip")), sources.toString());
        assertTrue(sources.stream().anyMatch(source -> source.path().getFileName().toString().equals("generated.zip")));
        assertTrue(sources.stream().anyMatch(source -> source.path().endsWith(Path.of("data", "resource_pack"))));
    }

    @Test
    void providerModesSelectGeneratedPacksWorkingFoldersOrNothing() throws Exception {
        Path provider = Files.createDirectories(root.resolve("plugins/ItemsAdder"));
        Files.write(Files.createDirectories(provider.resolve("output")).resolve("generated.zip"), emptyZip());
        Path contents = Files.createDirectories(provider.resolve("contents/demo/resourcepack"));
        Files.writeString(contents.resolve("pack.mcmeta"), "{}");
        Path datapack = Files.createDirectories(root.resolve("world/datapacks/example"));
        Files.writeString(datapack.resolve("pack.mcmeta"), "{}");
        java.util.function.Function<java.util.Map<String, String>, List<String>> kinds = modes -> {
            TwilightConfig config = new TwilightConfig(false, true, true, true, 40, 100, 10_000_000, 1000, true, true,
                    List.of(), "auto", false, false, 3, true, true, true, true, true, true, true, "auto", modes,
                    !modes.containsKey("nodatapacks"), true);
            try {
                Path realRoot = root.toRealPath();
                return new SourceDiscovery(root, config).discover(Set.of(root.resolve("world"))).stream()
                        .filter(source -> source.kind() != ContentSource.Kind.PROVIDER_DATA)
                        .map(source -> realRoot.relativize(source.path()).toString().replace('\\', '/')).sorted().toList();
            } catch (java.io.IOException failure) {
                throw new java.io.UncheckedIOException(failure);
            }
        };
        assertEquals(List.of("plugins/ItemsAdder/contents/demo/resourcepack", "plugins/ItemsAdder/output/generated.zip",
                "world/datapacks/example"), kinds.apply(java.util.Map.of()));
        assertEquals(List.of("plugins/ItemsAdder/output/generated.zip", "world/datapacks/example"),
                kinds.apply(java.util.Map.of("itemsadder", "generated")));
        assertEquals(List.of("plugins/ItemsAdder/contents/demo/resourcepack", "world/datapacks/example"),
                kinds.apply(java.util.Map.of("itemsadder", "contents")));
        assertEquals(List.of("world/datapacks/example"), kinds.apply(java.util.Map.of("itemsadder", "off")));
        assertEquals(List.of("plugins/ItemsAdder/output/generated.zip"),
                kinds.apply(java.util.Map.of("itemsadder", "generated", "nodatapacks", "off")));
    }

    @Test
    void usesTheNewestRenamedItemsAdderPackWhenGeneratedZipIsAbsent() throws Exception {
        Path output = Files.createDirectories(root.resolve("plugins/ItemsAdder/output"));
        Files.write(output.resolve("server-pack-1.18.zip"), emptyZip());
        Files.write(output.resolve("generated_1.21.x.zip"), emptyZip());
        Files.setLastModifiedTime(output.resolve("server-pack-1.18.zip"), java.nio.file.attribute.FileTime.fromMillis(1_000_000L));
        Files.setLastModifiedTime(output.resolve("generated_1.21.x.zip"), java.nio.file.attribute.FileTime.fromMillis(2_000_000L));
        TwilightConfig config = new TwilightConfig(false, true, true, true, 40, 100, 10_000_000, 1000,
                true, true, List.of(), "auto", false, false, 3);
        List<ContentSource> sources = new SourceDiscovery(root, config).discover(Set.of());
        assertEquals(List.of("generated_1.21.x.zip"), sources.stream().map(source -> source.path().getFileName().toString())
                .filter(name -> name.endsWith(".zip")).toList());

        Files.write(output.resolve("generated.zip"), emptyZip());
        List<ContentSource> standard = new SourceDiscovery(root, config).discover(Set.of());
        assertEquals(List.of("generated.zip"), standard.stream().map(source -> source.path().getFileName().toString())
                .filter(name -> name.endsWith(".zip")).toList());
    }

    @Test
    void discoversNameplateAndHudPacksAndIgnoresNexoVanillaCache() throws Exception {
        // CustomNameplates keeps its pack in ResourcePack/ and resourcepack.zip when not merged into another
        // provider; Nexo caches vanilla client assets under pack/.assetCache/<version>.
        Path nameplates = Files.createDirectories(root.resolve("plugins/CustomNameplates/ResourcePack/assets/nameplates/font"));
        Files.writeString(nameplates.resolve("default.json"), "{\"providers\":[]}");
        Files.writeString(root.resolve("plugins/CustomNameplates/ResourcePack/pack.mcmeta"), "{}");
        Files.write(root.resolve("plugins/CustomNameplates/resourcepack.zip"), emptyZip());
        Path hud = Files.createDirectories(root.resolve("plugins/BetterHud/build/assets/betterhud"));
        Files.writeString(hud.getParent().getParent().resolve("pack.mcmeta"), "{}");
        Path nexoCache = Files.createDirectories(root.resolve("plugins/Nexo/pack/.assetCache/1.21.4/assets/minecraft"));
        Files.writeString(nexoCache.getParent().getParent().resolve("pack.mcmeta"), "{}");
        Files.write(root.resolve("plugins/Nexo/pack/pack.zip"), emptyZip());

        TwilightConfig config = new TwilightConfig(false, true, true, true, 40, 100, 10_000_000, 1000,
                true, true, List.of(), "auto", false, false, 3);
        List<ContentSource> sources = new SourceDiscovery(root, config).discover(Set.of());
        Path real = root.toRealPath();
        List<String> found = sources.stream().map(source -> source.provider() + ":" + real.relativize(source.path())
                .toString().replace('\\', '/')).toList();
        assertTrue(found.contains("customnameplates:plugins/CustomNameplates/ResourcePack"), found.toString());
        assertTrue(found.contains("customnameplates:plugins/CustomNameplates/resourcepack.zip"), found.toString());
        assertTrue(found.contains("betterhud:plugins/BetterHud/build"), found.toString());
        assertTrue(found.contains("nexo:plugins/Nexo/pack/pack.zip"), found.toString());
        assertTrue(found.stream().noneMatch(path -> path.contains(".assetCache")), found.toString());
    }

    @Test
    void undeliveredProviderPacksLoseToTheDeliveredPack() throws Exception {
        // Real Survival setup: ItemsAdder hosting disabled (no-host) while CraftEngine sends its pack,
        // which already holds the converted ItemsAdder content; 29 characters differ between the two.
        Files.createDirectories(root.resolve("plugins/ItemsAdder/output"));
        Files.write(root.resolve("plugins/ItemsAdder_4.0.18.jar"), new byte[0]);
        Files.write(root.resolve("plugins/craft-engine-paper-plugin-26.7.4.jar"), new byte[0]);
        Files.write(root.resolve("plugins/ItemsAdder/output/generated.zip"),
                zipWith("assets/minecraft/font/default.json", "{\"from\":\"itemsadder\"}"));
        Files.writeString(root.resolve("plugins/ItemsAdder/config.yml"), """
                resource-pack:
                  hosting:
                    no-host:
                      enabled: true # players get the pack elsewhere
                    self-host:
                      enabled: false
                """);
        Files.createDirectories(root.resolve("plugins/CraftEngine/generated"));
        Files.write(root.resolve("plugins/CraftEngine/generated/resource_pack.zip"),
                zipWith("assets/minecraft/font/default.json", "{\"from\":\"craftengine\"}"));
        Files.writeString(root.resolve("plugins/CraftEngine/config.yml"), """
                resource-pack:
                  delivery:
                    send-on-join: true
                """);
        TwilightConfig config = new TwilightConfig(false, true, true, true, 40, 100, 10_000_000, 1000,
                true, true, List.of(), "auto", false, false, 3);
        List<ContentSource> sources = new SourceDiscovery(root, config).discover(Set.of());
        try (ResourceIndex resources = ResourceIndex.build(sources, config)) {
            assertEquals("{\"from\":\"craftengine\"}", resources.find("assets/minecraft/font/default.json").orElseThrow().readUtf8());
        }

        // Without another delivering pack, ItemsAdder's own output stays authoritative.
        Files.delete(root.resolve("plugins/CraftEngine/generated/resource_pack.zip"));
        List<ContentSource> alone = new SourceDiscovery(root, config).discover(Set.of());
        assertTrue(alone.stream().allMatch(source -> source.priority() >= 600), alone.toString());
    }

    @Test
    void providerFoldersWithoutTheirPluginOnlyFillGaps() throws Exception {
        // Real Survival/SkyBlock: an ItemsAdder folder remains after switching to CraftEngine, without the JAR.
        Files.createDirectories(root.resolve("plugins/ItemsAdder/output"));
        Files.write(root.resolve("plugins/ItemsAdder/output/generated.zip"),
                zipWith("assets/minecraft/font/default.json", "{\"from\":\"itemsadder\"}"));
        Files.createDirectories(root.resolve("plugins/CraftEngine/generated"));
        Files.write(root.resolve("plugins/craft-engine-paper-plugin-26.7.4.jar"), new byte[0]);
        Files.write(root.resolve("plugins/CraftEngine/generated/resource_pack.zip"),
                zipWith("assets/minecraft/font/default.json", "{\"from\":\"craftengine\"}"));
        TwilightConfig config = new TwilightConfig(false, true, true, true, 40, 100, 10_000_000, 1000,
                true, true, List.of(), "auto", false, false, 3);
        List<ContentSource> sources = new SourceDiscovery(root, config).discover(Set.of());
        ContentSource leftover = sources.stream().filter(source -> source.provider().equals("itemsadder")).findFirst().orElseThrow();
        assertTrue(leftover.priority() < 600, sources.toString());
        try (ResourceIndex resources = ResourceIndex.build(sources, config)) {
            assertEquals("{\"from\":\"craftengine\"}", resources.find("assets/minecraft/font/default.json").orElseThrow().readUtf8());
        }
    }

    @Test
    void readsNestedYamlScalarsWithComments() {
        var values = ProviderDelivery.scalars("""
                Pack:
                  server:
                    type: "POLYMATH" # host
                  dispatch:
                    send_pre_join: false
                    send_on_join: false
                list:
                  - a: b
                url: 'https://example.invalid/#fragment'
                """);
        assertEquals("POLYMATH", values.get("Pack.server.type"));
        assertEquals("false", values.get("Pack.dispatch.send_on_join"));
        assertEquals("https://example.invalid/#fragment", values.get("url"));
    }

    /** Folders the provider merges into its pack are read while it has not generated one yet. */
    @Test
    void readsTheFoldersTheProviderMergesWhenItHasNoGeneratedPack() throws Exception {
        Path provider = Files.createDirectories(root.resolve("plugins/ItemsAdder"));
        Files.writeString(provider.resolve("config.yml"), """
                resource-pack:
                  zip:
                    merge_other_plugins_resourcepacks_folders:
                      - ModelEngine/resource pack # bones
                      - "MyNpcs/pack"
                      - ../outside
                      - Missing/pack
                """);
        Files.createDirectories(root.resolve("plugins/ModelEngine/resource pack/assets/modelengine"));
        Files.createDirectories(root.resolve("plugins/MyNpcs/pack"));
        Files.writeString(root.resolve("plugins/MyNpcs/pack/pack.mcmeta"), "{}");
        Files.createDirectories(root.resolve("outside/assets"));
        TwilightConfig config = new TwilightConfig(false, true, true, true, 40, 100, 10_000_000, 1000,
                true, true, List.of(), "auto", false, false, 3);

        List<ContentSource> sources = new SourceDiscovery(root, config).discover(Set.of());
        assertTrue(sources.stream().anyMatch(source -> source.path().endsWith("resource pack")), sources.toString());
        assertTrue(sources.stream().anyMatch(source -> source.path().endsWith(Path.of("MyNpcs", "pack"))), sources.toString());
        assertTrue(sources.stream().noneMatch(source -> source.path().endsWith("outside")), sources.toString());

        Files.createDirectories(provider.resolve("output"));
        Files.write(provider.resolve("output/generated.zip"), zipWith("pack.mcmeta", "{}"));
        sources = new SourceDiscovery(root, config).discover(Set.of());
        assertTrue(sources.stream().noneMatch(source -> source.path().endsWith(Path.of("MyNpcs", "pack"))),
                "the generated pack already holds the merged folders");
    }

    @Test
    void readsBlockAndFlowYamlLists() {
        List<String> keys = List.of("merge-external-folders", "merge-external-zip-files");
        assertEquals(List.of("a/pack", "b.zip", "c"), SourceDiscovery.yamlList(List.of(
                "resource-pack:",
                "  merge-external-folders:",
                "    - 'a/pack'",
                "    # comment",
                "    - ../escape",
                "  merge-external-zip-files: [b.zip, \"c\"]",
                "  other:",
                "    - ignored"), keys));
    }

    private static byte[] zipWith(String name, String content) throws Exception {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(bytes)) {
            zip.putNextEntry(new java.util.zip.ZipEntry(name));
            zip.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    private static ContentSource source(List<ContentSource> sources, String fileName, String... ancestor) {
        return sources.stream().filter(source -> source.path().getFileName().toString().equalsIgnoreCase(fileName))
                .filter(source -> ancestor.length == 0 || java.util.stream.StreamSupport.stream(source.path().spliterator(), false)
                        .anyMatch(part -> part.toString().equalsIgnoreCase(ancestor[0])))
                .findFirst().orElseThrow();
    }

    private static byte[] emptyZip() throws Exception {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(bytes)) { }
        return bytes.toByteArray();
    }
}
