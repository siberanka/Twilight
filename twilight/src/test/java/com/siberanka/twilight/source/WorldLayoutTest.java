package com.siberanka.twilight.source;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorldLayoutTest {
    @TempDir Path root;

    @Test
    void honorsConfiguredLevelNameAndRuntimeWorlds() throws Exception {
        Files.writeString(root.resolve("server.properties"), "level-name=server.pro\n");
        Path primary = Files.createDirectories(root.resolve("server.pro"));
        Path nether = Files.createDirectories(root.resolve("server.pro_nether"));
        Path custom = Files.createDirectories(root.resolve("events"));

        assertEquals(Set.of(primary, nether, custom), WorldLayout.discover(root, Set.of(custom)));
    }
}
