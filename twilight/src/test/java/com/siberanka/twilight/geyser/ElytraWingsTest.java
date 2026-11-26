package com.siberanka.twilight.geyser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ElytraWingsTest {
    @Test void indicesFollowTheOrderOfTheWingsTextures() throws Exception {
        String json = ElytraWings.json(List.of(List.of("twilight:a", "twilight:b"), List.of("twilight:c")));
        ElytraWings wings = ElytraWings.parse(json);
        assertEquals(1, wings.indexOf("twilight:a"));
        assertEquals(1, wings.indexOf("twilight:b"));
        assertEquals(2, wings.indexOf("twilight:c"));
        assertEquals(0, wings.indexOf("minecraft:elytra"));
        assertEquals(0, wings.indexOf(null));
        assertFalse(wings.isEmpty());
        assertTrue(ElytraWings.EMPTY.isEmpty());
    }

    @Test void rejectsInputItDoesNotTrust() {
        assertThrows(IOException.class, () -> ElytraWings.parse("{\"format\":2,\"wings\":[]}"));
        assertThrows(IOException.class, () -> ElytraWings.parse("not json"));
        assertThrows(IOException.class, () -> ElytraWings.parse(ElytraWings.json(List.of(List.of("minecraft:elytra")))));
        List<List<String>> tooMany = new ArrayList<>();
        for (int i = 0; i <= ElytraWings.MAX; i++) tooMany.add(List.of("twilight:w" + i));
        assertThrows(IOException.class, () -> ElytraWings.parse(ElytraWings.json(tooMany)));
    }

    @Test void readsThePackFileAndToleratesPacksWithoutIt(@TempDir Path dir) throws Exception {
        assertTrue(ElytraWings.read(dir.resolve("missing.zip")).isEmpty());
        Path plain = dir.resolve("plain.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(plain))) {
            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write("{}".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        assertTrue(ElytraWings.read(plain).isEmpty());
        Path pack = dir.resolve("pack.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(pack))) {
            zip.putNextEntry(new ZipEntry(ElytraWings.PATH));
            zip.write(ElytraWings.json(List.of(List.of("twilight:flame"))).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        assertEquals(1, ElytraWings.read(pack).indexOf("twilight:flame"));
    }
}
