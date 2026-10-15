package com.siberanka.twilight.geyser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class StaleFilesTest {
    @TempDir Path root;

    @Test
    void movesTwilightLeftoversOutOfGeyserAndKeepsEverythingElse() throws Exception {
        Path geyser = root.resolve("plugins/Geyser-BungeeCord");
        Path mappings = geyser.resolve("custom_mappings");
        Path packs = geyser.resolve("packs");
        write(mappings.resolve("twilight-proxy_item_mappings.json"), "{\"ours\":true}");
        write(mappings.resolve("twilight_network_item_mappings.json"), "{\"old\":true}");
        // Geyser reads custom_mappings recursively, so a copy in a sub-folder counts as well.
        write(mappings.resolve("backup/Twilight_item_mappings.json"), "{\"copy\":true}");
        write(mappings.resolve("geyser_block_mappings.json"), "{}");
        write(mappings.resolve("twilight_notes.txt"), "not a mapping file");
        zip(packs.resolve("twilight_network.mcpack"), "Other name");
        zip(packs.resolve("network-pack.zip"), "Twilight");
        zip(packs.resolve("server-art.zip"), "Server art");
        write(packs.resolve("readme.txt"), "x");

        Set<String> keep = Set.of("custom_mappings/twilight-proxy_item_mappings.json");
        assertEquals(4, StaleFiles.find(geyser, packs, keep).size());
        List<StaleFiles.Retired> moved = StaleFiles.retire(geyser, packs, keep, root.resolve("plugins/twilight-proxy/retired"));
        assertEquals(List.of("custom_mappings/backup/Twilight_item_mappings.json", "custom_mappings/twilight_network_item_mappings.json",
                "packs/network-pack.zip", "packs/twilight_network.mcpack"), moved.stream().map(StaleFiles.Retired::file).toList());
        for (StaleFiles.Retired file : moved) {
            assertTrue(Files.isRegularFile(file.to()), file.to().toString());
            assertTrue(file.to().startsWith(root.resolve("plugins/twilight-proxy/retired")));
        }
        assertEquals("{\"old\":true}", Files.readString(moved.get(1).to()), "moved unchanged, never deleted");
        assertTrue(Files.isRegularFile(mappings.resolve("twilight-proxy_item_mappings.json")));
        assertTrue(Files.isRegularFile(mappings.resolve("geyser_block_mappings.json")));
        assertTrue(Files.isRegularFile(mappings.resolve("twilight_notes.txt")));
        assertTrue(Files.isRegularFile(packs.resolve("server-art.zip")));
        assertFalse(Files.exists(mappings.resolve("twilight_network_item_mappings.json")));
        assertEquals(List.of(), StaleFiles.find(geyser, packs, keep), "nothing left to move");

        // A second leftover with the same name later does not overwrite the first retired copy.
        write(mappings.resolve("twilight_network_item_mappings.json"), "{\"again\":true}");
        StaleFiles.retire(geyser, packs, keep, root.resolve("plugins/twilight-proxy/retired"));
        assertEquals("{\"old\":true}", Files.readString(moved.get(1).to()));
    }

    @Test
    void keepsTheFilesTwilightDeploysOnASingleServer() throws Exception {
        Path geyser = root.resolve("plugins/Geyser-Spigot");
        write(geyser.resolve("custom_mappings/twilight_item_mappings.json"), "{}");
        zip(geyser.resolve("packs/twilight.zip"), "Twilight");
        zip(geyser.resolve("packs/twilight-old.zip"), "Twilight");
        Set<String> keep = Set.of("packs/twilight.zip", "custom_mappings/twilight_item_mappings.json");
        assertEquals(List.of(geyser.resolve("packs/twilight-old.zip")), StaleFiles.find(geyser, geyser.resolve("packs"), keep));
        assertEquals(List.of(), StaleFiles.find(root.resolve("missing"), null, keep));
    }

    private static void write(Path file, String text) throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }

    private static void zip(Path file, String name) throws Exception {
        Files.createDirectories(file.getParent());
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(file))) {
            out.putNextEntry(new ZipEntry("manifest.json"));
            out.write(("{\"format_version\":2,\"header\":{\"name\":\"" + name + "\",\"uuid\":\"00000000-0000-0000-0000-000000000001\"}}")
                    .getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
    }
}
