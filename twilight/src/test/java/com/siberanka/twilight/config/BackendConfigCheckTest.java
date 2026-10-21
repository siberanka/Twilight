/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.config;

import com.siberanka.twilight.settings.ConfigMaintenance;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Twilight's config.yml with Bukkit's YAML and every check the plugin applies. */
class BackendConfigCheckTest {
    @TempDir Path root;
    final List<String> info = new ArrayList<>(), warn = new ArrayList<>();
    LocalDateTime now = LocalDateTime.of(2026, 10, 17, 9, 0);

    static String resource(String path) throws Exception {
        try (InputStream input = BackendConfigCheckTest.class.getResourceAsStream(path)) {
            assertNotNull(input, path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }

    ConfigMaintenance.Report maintain(Path file) throws Exception {
        now = now.plusSeconds(1);
        return ConfigMaintenance.run(file, resource("/config.yml"), new BackendConfigCheck(root),
                BackendConfigCheck.FREE_FORM, info::add, warn::add, now);
    }

    @Test void theDefaultsPassEveryCheck() throws Exception {
        new BackendConfigCheck(root).validate(resource("/config.yml"));
        Path file = root.resolve("config.yml");
        Files.writeString(file, resource("/config.yml"));
        assertFalse(maintain(file).written());
        assertTrue(warn.isEmpty(), warn.toString());
    }

    @Test void invalidValuesAreSetBackOneByOne() throws Exception {
        String defaults = resource("/config.yml");
        String broken = defaults.replace("  backups-to-keep: 3", "  backups-to-keep: 50")
                .replace("  item-display-variants: seen", "  item-display-variants: some")
                .replace("  max-glyph-cell: 512", "  max-glyph-cell: 300")
                .replace("  startup-delay-ticks: 40", "  startup-delay-ticks: soon")
                .replace("  view-angle: auto", "  view-angle: 10")
                .replace("  port: 8163", "  port: 0")
                .replace("  player-head-skins-of-players: 1024", "  player-head-skins-of-players: 99999")
                // Fine values the administrator chose stay.
                .replace("  strict: true", "  strict: false")
                .replace("  link-minutes: 10", "  link-minutes: 30");
        Path file = root.resolve("config.yml");
        Files.writeString(file, broken);
        var report = maintain(file);
        assertTrue(report.written());
        assertEquals(7, report.repaired().size(), report.repaired().toString());
        String now = Files.readString(file);
        assertEquals(defaults.replace("  strict: true", "  strict: false").replace("  link-minutes: 10", "  link-minutes: 30"), now);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(now);
        TwilightConfig config = TwilightConfig.read(yaml, root);
        assertEquals(3, config.backupsToKeep());
        assertFalse(config.strict());
        assertFalse(maintain(file).written());
    }

    @Test void aConfigurationOfTheFirstBetaIsBroughtUpToDate() throws Exception {
        String old = resource("/settings/config-1.0.0-beta.1.yml").replace("  backups-to-keep: 3", "  backups-to-keep: 5");
        Path file = root.resolve("config.yml");
        Files.writeString(file, old);
        var report = maintain(file);
        assertTrue(report.written());
        assertTrue(report.repaired().isEmpty(), report.repaired().toString());
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(Files.readString(file));
        assertEquals(5, yaml.getInt("geyser.backups-to-keep"));
        assertEquals("auto", yaml.getString("bedrock-network.view-angle"));
        assertTrue(yaml.getBoolean("geyser.player-heads"));
        assertFalse(maintain(file).written());
    }

    @Test void unreadableYamlIsRestoredAndTheBrokenFileKept() throws Exception {
        String defaults = resource("/config.yml");
        String mine = defaults.replace("  backups-to-keep: 3", "  backups-to-keep: 6");
        Path file = root.resolve("config.yml");
        Files.writeString(file, mine);
        maintain(file);
        String broken = mine.replace("generation:\n", "generation:\n strict: [\n");
        Files.writeString(file, broken);
        var report = maintain(file);
        assertTrue(report.restored());
        assertEquals(mine, Files.readString(file));
        assertEquals(broken, Files.readString(report.backup()));
    }

    @Test void providersAreTheAdministratorsOwn() throws Exception {
        String defaults = resource("/config.yml");
        String mine = defaults.replace("    itemsadder: auto\n", "").replace("    nexo: auto\n", "    nexo: off\n    myplugin: generated\n");
        Path file = root.resolve("config.yml");
        Files.writeString(file, mine);
        var report = maintain(file);
        assertFalse(report.written(), "a provider left out is not added back");
        assertTrue(report.unknown().isEmpty(), report.unknown().toString());
    }
}
