package com.siberanka.twilight.source;

import com.siberanka.twilight.config.TwilightConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContentInspectorTest {
    @TempDir Path root;

    @Test
    void classifiesItemsModelsGlyphsMobsAndBiomes() throws Exception {
        Path pack = root.resolve("pack.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(pack))) {
            add(zip, "assets/demo/items/sword.json");
            add(zip, "assets/minecraft/models/item/diamond_sword.json");
            add(zip, "assets/demo/models/item/sword.json");
            add(zip, "assets/demo/font/default.json");
            add(zip, "assets/demo/textures/item/sword.png");
            add(zip, "models/boss.bbmodel");
            add(zip, "data/demo/worldgen/biome/autumn.json");
        }
        ContentReport report = new ContentInspector(config()).inspect(List.of(
                new ContentSource("test", ContentSource.Kind.RESOURCE_PACK, pack, 1)));
        assertEquals(1, report.itemDefinitions());
        assertEquals(1, report.legacyItemModels());
        assertEquals(2, report.modelJson());
        assertEquals(1, report.fontDefinitions());
        assertEquals(1, report.bbmodels());
        assertEquals(1, report.biomeDefinitions());
    }

    @Test
    void rejectsArchiveTraversal() throws Exception {
        Path pack = root.resolve("bad.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(pack))) {
            add(zip, "../outside.json");
        }
        assertThrows(IOException.class, () -> new ContentInspector(config()).inspect(List.of(
                new ContentSource("test", ContentSource.Kind.RESOURCE_PACK, pack, 1))));
    }

    private static void add(ZipOutputStream zip, String name) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private TwilightConfig config() {
        return new TwilightConfig(false, true, false, true, 40, 100, 10_000_000, 1000,
                true, true, List.of(), "auto", false, false, 3);
    }
}
