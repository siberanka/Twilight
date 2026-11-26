package com.siberanka.twilight.geyser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class SessionElytraWingsTest {
    private static Path pack(Path file, String listing) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            zip.putNextEntry(new ZipEntry(listing == null ? "manifest.json" : ElytraWings.PATH));
            zip.write((listing == null ? "{}" : listing).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return file;
    }

    @Test void everySessionUsesTheWingsOfItsOwnPack(@TempDir Path dir) throws Exception {
        Path first = pack(dir.resolve("a.zip"), ElytraWings.json(List.of(List.of("twilight:flame"), List.of("twilight:ice"))));
        Path second = pack(dir.resolve("b.zip"), ElytraWings.json(List.of(List.of("twilight:ice"))));
        Map<String, Path> packs = new HashMap<>(Map.of("one", first, "two", second));
        SessionElytraWings wings = new SessionElytraWings(xuid -> Optional.ofNullable(packs.get(xuid)), message -> fail(message));
        assertEquals(1, wings.of("one").indexOf("twilight:flame"));
        assertEquals(2, wings.of("one").indexOf("twilight:ice"));
        assertEquals(0, wings.of("two").indexOf("twilight:flame"));
        assertEquals(1, wings.of("two").indexOf("twilight:ice"));
    }

    @Test void switchingPacksChangesTheIndicesOfTheSession(@TempDir Path dir) throws Exception {
        Path first = pack(dir.resolve("a.zip"), ElytraWings.json(List.of(List.of("twilight:flame"))));
        Path second = pack(dir.resolve("b.zip"), ElytraWings.json(List.of(List.of("twilight:ice"), List.of("twilight:flame"))));
        Map<String, Path> packs = new HashMap<>(Map.of("player", first));
        SessionElytraWings wings = new SessionElytraWings(xuid -> Optional.ofNullable(packs.get(xuid)), message -> fail(message));
        assertEquals(1, wings.of("player").indexOf("twilight:flame"));
        packs.put("player", second); // the player re-joined through a transfer and loaded the other backend's pack
        assertEquals(2, wings.of("player").indexOf("twilight:flame"));
    }

    @Test void sessionsWithoutWingsStayVanilla(@TempDir Path dir) throws Exception {
        Path plain = pack(dir.resolve("plain.zip"), null);
        Map<String, Path> packs = Map.of("old", plain);
        SessionElytraWings wings = new SessionElytraWings(xuid -> Optional.ofNullable(packs.get(xuid)), message -> fail(message));
        assertTrue(wings.of("old").isEmpty());
        assertTrue(wings.of("without-pack").isEmpty());
        assertTrue(wings.of(null).isEmpty());
        assertTrue(wings.of("").isEmpty());
    }

    @Test void unreadablePacksWarnOnceAndStayVanilla(@TempDir Path dir) throws Exception {
        Path broken = pack(dir.resolve("broken.zip"), "{\"format\":9}");
        List<String> warnings = new ArrayList<>();
        SessionElytraWings wings = new SessionElytraWings(xuid -> Optional.of(broken), warnings::add);
        assertTrue(wings.of("a").isEmpty());
        assertTrue(wings.of("b").isEmpty());
        assertEquals(1, warnings.size());
    }

    @Test void aRewrittenPackFileIsReadAgain(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("a.zip");
        pack(file, ElytraWings.json(List.of(List.of("twilight:flame"))));
        SessionElytraWings wings = new SessionElytraWings(xuid -> Optional.of(file), message -> fail(message));
        assertEquals(1, wings.of("x").indexOf("twilight:flame"));
        pack(file, ElytraWings.json(List.of(List.of("twilight:ice"), List.of("twilight:flame", "twilight:more"))));
        Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 5000));
        assertEquals(2, wings.of("x").indexOf("twilight:flame"));
    }
}
