package com.siberanka.twilight.proxy;

import com.siberanka.twilight.conflicts.ConflictSettings;
import com.siberanka.twilight.conflicts.Owner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ProxyConflictsTest {
    @TempDir Path plugins;

    @Test
    void theSectionIsOptionalAndValidated() {
        assertEquals(ConflictSettings.DEFAULTS, ProxyConfig.parse("packs:\n  default: auto\n").conflicts());
        ProxyConfig custom = ProxyConfig.parse("packs:\n  default: auto\nintegrations:\n  conflicts:\n    model-bones: other\n"
                + "    display-entities: twilight\n    foreign-mappings: retire\n    max-notices: 4\n");
        assertEquals(Owner.OTHER, custom.conflicts().modelBones());
        assertEquals(Owner.TWILIGHT, custom.conflicts().displayEntities());
        assertEquals(ConflictSettings.Foreign.RETIRE, custom.conflicts().foreignMappings());
        assertEquals(4, custom.conflicts().maxNotices());
        assertThrows(IllegalArgumentException.class, () -> ProxyConfig.parse("packs:\n  default: auto\nintegrations:\n  conflicts:\n    model-bones: both\n"));
    }

    @Test
    void theShippedDefaultsParseToTheDefaults() throws IOException {
        try (var in = ProxyConfig.class.getResourceAsStream("/proxy-config.yml")) {
            assertEquals(ConflictSettings.DEFAULTS, ProxyConfig.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8)).conflicts());
        }
    }

    @Test
    void detectsGeyserModelEngineInTheProxysGeyserFolder() throws IOException {
        Path own = plugins.resolve("twilight-proxy");
        Files.createDirectories(own);
        Path extensions = plugins.resolve("Geyser-Velocity/extensions");
        Files.createDirectories(extensions);
        Files.createDirectories(plugins.resolve("GeyserUtils"));
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(extensions.resolve("GeyserModelEngineExtension.jar")))) {
            zip.putNextEntry(new ZipEntry("extension.yml"));
            zip.write("name: GeyserModelEngineExtension\nid: geysermodelengineextension\n".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        var assessment = ProxyConflicts.assess(own, null, ConflictSettings.DEFAULTS);
        assertTrue(assessment.showModelBones(), "Twilight keeps drawing by default");
        assertFalse(ProxyConflicts.assess(own, null, new ConflictSettings(true, Owner.OTHER, Owner.AUTO, ConflictSettings.Foreign.REPORT, 10)).showModelBones());
        assertTrue(assessment.registerDisplays());
        assertTrue(ProxyConflicts.environment(own, null, "1.0.2").proxy());
    }

    @Test
    void aProxyWithoutGeyserOrExtensionsChangesNothing() throws IOException {
        Path own = plugins.resolve("twilight-proxy");
        Files.createDirectories(own);
        var assessment = ProxyConflicts.assess(own, null, ConflictSettings.DEFAULTS);
        assertTrue(assessment.showModelBones() && assessment.registerDisplays());
        assertTrue(ProxyConflicts.geyserFolder(own).isEmpty());
    }

    @Test
    void foreignMappingFilesAreReportedOrRetired() throws IOException {
        Path geyser = plugins.resolve("Geyser-Velocity");
        Files.createDirectories(geyser.resolve("custom_mappings"));
        Files.writeString(geyser.resolve("custom_mappings/twilight-proxy_item_mappings.json"),
                "{\"items\":{\"minecraft:paper\":[{\"custom_model_data\":10000}]}}");
        Files.writeString(geyser.resolve("custom_mappings/j2b.json"), "{\"items\":{\"minecraft:paper\":[{\"custom_model_data\":10000}]}}");
        java.util.List<String> logged = new java.util.ArrayList<>();
        assertEquals(0, ProxyConflicts.foreign(geyser, ConflictSettings.DEFAULTS, plugins.resolve("twilight-proxy/retired"),
                (message, failure) -> logged.add(message)));
        assertEquals(1, logged.size());
        assertTrue(logged.get(0).startsWith("Conflict: "));
        assertTrue(Files.exists(geyser.resolve("custom_mappings/j2b.json")), "report mode changes nothing");
        var retire = new ConflictSettings(true, Owner.AUTO, Owner.AUTO, ConflictSettings.Foreign.RETIRE, 10);
        assertEquals(1, ProxyConflicts.foreign(geyser, retire, plugins.resolve("twilight-proxy/retired"), (message, failure) -> logged.add(message)));
        assertFalse(Files.exists(geyser.resolve("custom_mappings/j2b.json")));
    }
}
