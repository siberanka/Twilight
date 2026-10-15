package com.siberanka.twilight.deploy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** The exported pack and mappings serve a Geyser on a proxy or another plugin that sends the pack. */
class PackExportTest {
    @TempDir Path root;

    @Test
    void exportsThePackAndCurrentMappingsOnly() throws Exception {
        Path output = root.resolve("build/current");
        Files.createDirectories(output.resolve("custom_mappings"));
        Files.writeString(output.resolve("pack.zip"), "pack-1");
        Files.writeString(output.resolve("custom_mappings/geyser_item_mappings.json"), "{}");
        Path data = root.resolve("data");
        Files.createDirectories(data.resolve("export/custom_mappings"));
        Files.writeString(data.resolve("export/custom_mappings/twilight_old.json"), "stale");

        PackExport.write(output, data);

        assertEquals("pack-1", Files.readString(data.resolve(PackExport.PACK)));
        assertEquals("{}", Files.readString(data.resolve("export/custom_mappings/twilight_item_mappings.json")));
        assertFalse(Files.exists(data.resolve("export/custom_mappings/twilight_old.json")));
        try (var files = Files.list(data.resolve("export"))) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
    }
}
