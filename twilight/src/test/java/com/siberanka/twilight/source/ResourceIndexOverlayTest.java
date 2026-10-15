package com.siberanka.twilight.source;

import com.siberanka.twilight.config.TwilightConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class ResourceIndexOverlayTest {
    @TempDir Path root;
    private static final String ASSET = "assets/demo/models/test.json";
    private static final String METADATA = """
            {"overlays":{"entries":[
              {"directory":"old","formats":[15,36]},
              {"directory":"z_first","formats":{"min_inclusive":63,"max_inclusive":100}},
              {"directory":"a_last","min_format":[88,0],"max_format":[88,2]},
              {"directory":"future","min_format":[88,3],"max_format":100}]}}
            """;

    @Test void directoryAndReversedZipRespectDeclarationOrderAndExcludeInactiveAssets() throws Exception {
        Map<String, String> entries = entries();
        Path directory = root.resolve("directory");
        for (var entry : entries.entrySet()) {
            Path file = directory.resolve(entry.getKey());
            Files.createDirectories(file.getParent());
            Files.writeString(file, entry.getValue());
        }
        Path archive = root.resolve("wrapped.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (String name : new ArrayList<>(entries.keySet()).reversed()) {
                zip.putNextEntry(new ZipEntry("wrapper/" + name));
                zip.write(entries.get(name).getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        for (Path source : List.of(directory, archive)) {
            try (ResourceIndex index = ResourceIndex.build(sources(source), config(), ignored -> new ResourceIndex.PackFormat(88,1))) {
                assertEquals("last", index.find(ASSET).orElseThrow().readUtf8());
                assertEquals(List.of("last", "first", "base"), index.findAll(ASSET).stream().map(asset -> {
                    try { return asset.readUtf8(); } catch (Exception failure) { throw new RuntimeException(failure); }
                }).toList());
                assertTrue(index.find("assets/demo/models/old-only.json").isEmpty());
                assertTrue(index.find("assets/demo/models/future-only.json").isEmpty());
                assertTrue(index.find("assets/demo/models/undeclared.json").isEmpty());
            }
        }
    }

    @Test void selectsModernMinorVersionBoundariesAndLegacyRanges() throws Exception {
        Path directory = root.resolve("pack");
        for (var entry : entries().entrySet()) {
            Path file = directory.resolve(entry.getKey());
            Files.createDirectories(file.getParent()); Files.writeString(file, entry.getValue());
        }
        for (var sample : Map.of(new ResourceIndex.PackFormat(88,2), "last",
                new ResourceIndex.PackFormat(88,3), "future", new ResourceIndex.PackFormat(36,0), "old").entrySet()) {
            try (ResourceIndex index = ResourceIndex.build(sources(directory), config(), ignored -> sample.getKey())) {
                assertEquals(sample.getValue(), index.find(ASSET).orElseThrow().readUtf8());
            }
        }
        assertThrows(java.io.IOException.class, () -> ResourceIndex.build(sources(directory), config()));
    }

    @Test void sourceDiscoveryDoesNotReimportOverlayAsIndependentPack() throws Exception {
        Path provider = root.resolve("plugins/ModelEngine/resource pack");
        Files.createDirectories(provider.resolve("old/assets/demo"));
        Files.writeString(provider.resolve("pack.mcmeta"), METADATA);
        TwilightConfig discovery = new TwilightConfig(false,true,false,false,40,100,10_000_000,1000,
                false,true,List.of(),"auto",false,false,3);
        List<ContentSource> found = new SourceDiscovery(root, discovery).discover(Set.of());
        assertEquals(1, found.size());
        assertEquals(provider.toRealPath(), found.getFirst().path());
    }

    @Test void rejectsTraversalInOverlayDirectory() throws Exception {
        for (String name : List.of("../escape", ".", "..", "", "nested/path", "C:escape")) {
            Files.writeString(root.resolve("pack.mcmeta"),
                    "{\"overlays\":{\"entries\":[{\"directory\":\"" + name + "\",\"formats\":88}]}}");
            assertThrows(java.io.IOException.class, () -> ResourceIndex.build(sources(root), config(),
                    ignored -> new ResourceIndex.PackFormat(88,0)), name);
        }
    }

    @Test void versionedAndUppercaseOverlayNamesWorkInDirectoriesAndArchives() throws Exception {
        Map<String,String> files = new LinkedHashMap<>();
        files.put("pack.mcmeta", """
                {"overlays":{"entries":[
                  {"directory":"1.21.5","formats":[55,100]},
                  {"directory":"New_26.2-RC","formats":[88,100]}]}}
                """);
        files.put(ASSET, "base");
        files.put("1.21.5/" + ASSET, "versioned");
        files.put("New_26.2-RC/" + ASSET, "uppercase");
        Path directory = root.resolve("versioned");
        Path archive = root.resolve("versioned.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (var file : files.entrySet()) {
                Path target = directory.resolve(file.getKey());
                Files.createDirectories(target.getParent());
                Files.writeString(target, file.getValue());
                zip.putNextEntry(new ZipEntry(file.getKey()));
                zip.write(file.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        for (Path source : List.of(directory, archive)) {
            for (var sample : Map.of(54, "base", 55, "versioned", 88, "uppercase").entrySet()) {
                try (ResourceIndex index = ResourceIndex.build(sources(source), config(),
                        ignored -> new ResourceIndex.PackFormat(sample.getKey(),0))) {
                    assertEquals(sample.getValue(), index.find(ASSET).orElseThrow().readUtf8());
                }
            }
        }
    }

    private static Map<String,String> entries() {
        Map<String,String> entries = new LinkedHashMap<>();
        entries.put("pack.mcmeta", METADATA);
        entries.put(ASSET,"base");
        entries.put("old/"+ASSET,"old");
        entries.put("z_first/"+ASSET,"first");
        entries.put("a_last/"+ASSET,"last");
        entries.put("future/"+ASSET,"future");
        entries.put("old/assets/demo/models/old-only.json","old-only");
        entries.put("future/assets/demo/models/future-only.json","future-only");
        entries.put("unused/assets/demo/models/undeclared.json","unreferenced");
        return entries;
    }

    private static List<ContentSource> sources(Path path) {
        return List.of(new ContentSource("test",ContentSource.Kind.RESOURCE_PACK,path,1));
    }
    private static TwilightConfig config() {
        return new TwilightConfig(false,true,false,false,40,100,10_000_000,1000,
                false,false,List.of(),"auto",false,false,3);
    }
}
