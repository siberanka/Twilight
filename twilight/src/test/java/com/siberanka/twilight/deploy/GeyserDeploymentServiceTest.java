package com.siberanka.twilight.deploy;

import com.siberanka.twilight.config.TwilightConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeyserDeploymentServiceTest {
    @TempDir Path root;

    @Test
    void deploysOnlyOwnedFilesRetainsThreeSnapshotsAndRollsBack() throws Exception {
        Path geyser = Files.createDirectories(root.resolve("plugins/Geyser-Spigot"));
        Files.createDirectories(geyser.resolve("packs"));
        Files.writeString(geyser.resolve("packs/unrelated.zip"), "keep");
        Path data = Files.createDirectories(root.resolve("plugins/Twilight"));
        Path output = Files.createDirectories(data.resolve("build/current/custom_mappings"));
        GeyserDeploymentService service = new GeyserDeploymentService(root, data, config());

        for (int version = 1; version <= 5; version++) {
            writePack(data.resolve("build/current/pack.zip"), "pack-" + version);
            Files.writeString(output.resolve("geyser_item_mappings.json"), "mapping-" + version);
            DeploymentResult result = service.deploy(data.resolve("build/current"));
            assertTrue(result.success());
            Thread.sleep(2);
        }

        assertEquals("pack-5", readTexture(geyser.resolve("packs/twilight.zip")));
        assertEquals("mapping-5", Files.readString(geyser.resolve("custom_mappings/twilight_item_mappings.json")));
        assertEquals("keep", Files.readString(geyser.resolve("packs/unrelated.zip")));
        assertEquals(3, service.snapshots().size());

        service.rollback(1);
        assertEquals("pack-4", readTexture(geyser.resolve("packs/twilight.zip")));
        assertEquals("mapping-4", Files.readString(geyser.resolve("custom_mappings/twilight_item_mappings.json")));
        assertFalse(Files.exists(root.resolve("outside")));
    }

    @Test
    void keepsRestartRequiredUntilRuntimeMappingsMatchAgain() throws Exception {
        Path geyser = Files.createDirectories(root.resolve("plugins/Geyser-Spigot/custom_mappings"));
        Files.createDirectories(geyser.getParent().resolve("packs"));
        Files.writeString(geyser.resolve("twilight_item_mappings.json"), "initial");
        Path data = Files.createDirectories(root.resolve("plugins/Twilight"));
        Path output = Files.createDirectories(data.resolve("build/current/custom_mappings"));
        Path mapping = output.resolve("geyser_item_mappings.json");
        writePack(data.resolve("build/current/pack.zip"), "textures-1");
        Files.writeString(mapping, "initial");
        GeyserDeploymentService service = new GeyserDeploymentService(root, data, config());
        assertFalse(service.deploy(output.getParent()).restartRequired());

        Files.writeString(mapping, "new-items");
        assertTrue(service.deploy(output.getParent()).restartRequired());
        service.reconfigure(config());
        writePack(data.resolve("build/current/pack.zip"), "textures-2");
        assertTrue(service.deploy(output.getParent()).restartRequired(), "reload/pack edits cannot activate registry changes");
        assertTrue(service.rollback(1).restartRequired(), "previous deploy still had unactivated mappings");
        assertFalse(service.rollback(2).restartRequired(), "restoring the runtime mappings clears the requirement");
    }

    @Test
    void newRuntimeCanReloadTexturesButMappingRemovalNeedsRestart() throws Exception {
        Path geyser = Files.createDirectories(root.resolve("plugins/Geyser-Spigot"));
        Files.createDirectories(geyser.resolve("packs"));
        Path data = Files.createDirectories(root.resolve("plugins/Twilight"));
        Path output = Files.createDirectories(data.resolve("build/current/custom_mappings"));
        Path mapping = output.resolve("geyser_item_mappings.json");
        Files.writeString(mapping, "items");
        writePack(data.resolve("build/current/pack.zip"), "pack");
        GeyserDeploymentService service = new GeyserDeploymentService(root, data, config());
        assertTrue(service.deploy(output.getParent()).restartRequired());
        // A new service represents a fresh server process, after Geyser loaded the files.
        service = new GeyserDeploymentService(root, data, config());
        assertFalse(service.deploy(output.getParent()).restartRequired());
        Files.delete(mapping);
        assertTrue(service.deploy(output.getParent()).restartRequired());
    }

    @Test
    void displayIndexChangesRequireRestartEvenWithoutItemMappingChanges() throws Exception {
        Path geyser = Files.createDirectories(root.resolve("plugins/Geyser-Spigot"));
        Files.createDirectories(geyser.resolve("packs"));
        Path data = Files.createDirectories(root.resolve("plugins/Twilight"));
        Path output = Files.createDirectories(data.resolve("build/current"));
        Path pack = output.resolve("pack.zip");
        writePack(pack, "first", "{\"twilight:a\":0,\"twilight:b\":1}");
        GeyserDeploymentService service = new GeyserDeploymentService(root, data, config());
        assertTrue(service.deploy(output).restartRequired());
        service = new GeyserDeploymentService(root, data, config());
        writePack(pack, "new texture", "{\"twilight:a\":0,\"twilight:b\":1}");
        assertFalse(service.deploy(output).restartRequired());
        writePack(pack, "new texture", "{\"twilight:b\":0,\"twilight:a\":1}");
        assertTrue(service.deploy(output).restartRequired(), "same items with reordered variants must not reload");
        service.reconfigure(config());
        assertTrue(service.deploy(output).restartRequired());
        writePack(pack, "removed displays");
        assertTrue(service.deploy(output).restartRequired());
    }

    @Test
    void liveDisplayVariantsNeedNoRestartButItemMappingsStillDo() throws Exception {
        Path geyser = Files.createDirectories(root.resolve("plugins/Geyser-Spigot"));
        Files.createDirectories(geyser.resolve("packs"));
        Path data = Files.createDirectories(root.resolve("plugins/Twilight"));
        Path output = Files.createDirectories(data.resolve("build/current/custom_mappings"));
        Path pack = output.getParent().resolve("pack.zip");
        writePack(pack, "first", "{\"twilight:a\":0}");
        Files.writeString(output.resolve("geyser_item_mappings.json"), "items");
        new GeyserDeploymentService(root, data, config()).deploy(output.getParent());
        // A fresh server process: Geyser loaded these files.
        GeyserDeploymentService service = new GeyserDeploymentService(root, data, config());
        service.liveDisplayVariants(true);
        writePack(pack, "first", "{\"twilight:a\":0,\"twilight:b\":1}");
        assertFalse(service.deploy(output.getParent()).restartRequired(), "sessions take the variants of the pack they load");
        Files.writeString(output.resolve("geyser_item_mappings.json"), "more items");
        assertTrue(service.deploy(output.getParent()).restartRequired(), "Geyser registers items only when it starts");
    }

    private static void writePack(Path path, String texture) throws Exception {
        writePack(path, texture, null);
    }

    @Test
    void rejectsBrokenPackBeforeReplacingDeployedFiles() throws Exception {
        Path geyser = Files.createDirectories(root.resolve("plugins/Geyser-Spigot"));
        Files.createDirectories(geyser.resolve("packs"));
        Path data = Files.createDirectories(root.resolve("plugins/Twilight"));
        Path output = Files.createDirectories(data.resolve("build/current"));
        writePack(output.resolve("pack.zip"), "original");
        GeyserDeploymentService service = new GeyserDeploymentService(root, data, config());
        service.deploy(output);
        Files.writeString(output.resolve("pack.zip"), "truncated invalid archive");
        org.junit.jupiter.api.Assertions.assertThrows(java.io.IOException.class, () -> service.deploy(output));
        assertEquals("original", readTexture(geyser.resolve("packs/twilight.zip")));
    }

    private static void writePack(Path path, String texture, String index) throws Exception {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
            zip.putNextEntry(new ZipEntry("test-texture.txt"));
            zip.write(texture.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            if (index != null) {
                zip.putNextEntry(new ZipEntry("twilight/display-index-v2.json"));
                zip.write(index.getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
    }

    private static String readTexture(Path path) throws Exception {
        try (ZipFile zip = new ZipFile(path.toFile())) {
            return new String(zip.getInputStream(zip.getEntry("test-texture.txt")).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private TwilightConfig config() {
        return new TwilightConfig(false, true, false, true, 40, 100, 10_000_000, 1000,
                true, true, List.of(), "auto", false, false, 3);
    }
}
