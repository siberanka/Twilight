/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.settings;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Configuration maintenance on real and crafted files: what is added and repaired, what is never touched, and
 * that it can neither loop nor damage a file.
 */
class ConfigMaintenanceTest {
    @TempDir Path root;
    final List<String> info = new ArrayList<>(), warn = new ArrayList<>();
    LocalDateTime now = LocalDateTime.of(2026, 10, 17, 12, 0, 0);

    static final String DEFAULTS = """
            # Example plugin.

            server:
              # Port to listen on (1-65535).
              port: 8080
              # fast or safe.
              mode: fast
              enabled: true
              hosts: []

            # Added in a later version.
            extra:
              # A new switch.
              switch: false
              # A number.
              limit: 10

            names:
              alpha: one
            """;

    /** Rules like a plugin's: a port range and a fixed set of modes. */
    static final ConfigMaintenance.Validator RULES = new ConfigMaintenance.Validator() {
        @Override public void syntax(String text) {
            YamlOutline.parse(text);
        }

        @Override public void validate(String text) {
            YamlOutline outline = YamlOutline.parse(text);
            var port = outline.get("server.port");
            if (port != null) {
                int value = Integer.parseInt(YamlOutline.unquote(port.value()));
                if (value < 1 || value > 65_535) throw new IllegalArgumentException("server.port must be 1-65535");
            }
            var mode = outline.get("server.mode");
            if (mode != null && !Set.of("fast", "safe").contains(YamlOutline.unquote(mode.value()))) {
                throw new IllegalArgumentException("server.mode must be fast or safe");
            }
        }
    };

    static final ConfigMaintenance.Validator ANYTHING = new ConfigMaintenance.Validator() {
        @Override public void syntax(String text) {
            YamlOutline.parse(text);
        }

        @Override public void validate(String text) {
        }
    };

    ConfigMaintenance.Report run(Path file) {
        return run(file, DEFAULTS, RULES, Set.of("names"));
    }

    ConfigMaintenance.Report run(Path file, String defaults, ConfigMaintenance.Validator validator, Set<String> freeForm) {
        now = now.plusSeconds(1);
        return ConfigMaintenance.run(file, defaults, validator, freeForm, info::add, warn::add, now);
    }

    Path write(String text) throws IOException {
        Path file = root.resolve("config.yml");
        Files.writeString(file, text, StandardCharsets.UTF_8);
        return file;
    }

    List<Path> backups() throws IOException {
        Path folder = root.resolve(ConfigMaintenance.BACKUPS);
        if (!Files.isDirectory(folder)) return List.of();
        try (var files = Files.list(folder)) {
            return files.filter(path -> path.getFileName().toString().matches("config-\\d{8}-\\d{6}.*\\.yml")).sorted().toList();
        }
    }

    @Test void aCurrentFileIsNeverWritten() throws Exception {
        Path file = write(DEFAULTS);
        var modified = Files.getLastModifiedTime(file);
        var report = run(file);
        assertFalse(report.written());
        assertEquals(DEFAULTS, Files.readString(file));
        assertEquals(modified, Files.getLastModifiedTime(file));
        assertTrue(backups().isEmpty(), "no backup without a change");
    }

    @Test void addsNewSettingsWithTheirCommentsAndKeepsEverythingElse() throws Exception {
        String old = """
                # My server! Do not touch.
                server:
                    # my port
                    port: 25000   # chosen by me
                    mode: 'safe'
                    custom-thing: kept

                names:
                    beta: two
                """;
        Path file = write(old);
        var report = run(file);
        assertTrue(report.written());
        String now = Files.readString(file);
        assertTrue(now.startsWith("# My server! Do not touch.\nserver:\n    # my port\n    port: 25000   # chosen by me\n    mode: 'safe'\n"), now);
        // Missing keys of an existing section, indented like the file (four spaces), with their comments.
        assertTrue(now.contains("    mode: 'safe'\n    enabled: true\n    hosts: []\n    custom-thing: kept\n"), now);
        // A missing section, with its comments, where the defaults have it.
        assertTrue(now.contains("\n# Added in a later version.\nextra:\n  # A new switch.\n  switch: false\n  # A number.\n  limit: 10\n"), now);
        // The administrator's own section is not filled with examples.
        assertFalse(now.contains("alpha"));
        assertTrue(now.contains("names:\n    beta: two\n"));
        assertEquals(List.of("server.enabled", "server.hosts", "extra.*"), report.added());
        assertEquals(List.of("server.custom-thing"), report.unknown());
        assertEquals(1, backups().size());
        assertEquals(old, Files.readString(backups().getFirst()), "the backup is the old file, byte for byte");
        assertFalse(run(file).written(), "a second run changes nothing");
    }

    @Test void setsBackOnlyTheInvalidValues() throws Exception {
        Path file = write("""
                server:
                  # Port to listen on (1-65535).
                  port: 99999 # too high
                  mode: turbo
                  enabled: maybe
                  hosts: [a, b]

                extra:
                  switch: true
                  limit: ten

                names:
                  alpha: anything goes here
                """);
        var report = run(file);
        assertTrue(report.written());
        String now = Files.readString(file);
        assertTrue(now.contains("  port: 8080 # too high\n"), "the comment stays: " + now);
        assertTrue(now.contains("  mode: fast\n"));
        assertTrue(now.contains("  enabled: true\n"));
        assertTrue(now.contains("  hosts: [a, b]\n"), "valid values stay");
        assertTrue(now.contains("  switch: true\n"));
        assertTrue(now.contains("  limit: 10\n"));
        assertTrue(now.contains("  alpha: anything goes here\n"), "free-form values are never checked");
        assertEquals(4, report.repaired().size(), report.repaired().toString());
        assertTrue(report.repaired().stream().anyMatch(line -> line.startsWith("server.port: '99999'")));
        assertFalse(run(file).written());
    }

    @Test void valuesThatAreFineTogetherAreNeverTouched() throws Exception {
        // The file as a whole passes: nothing is tried one by one.
        ConfigMaintenance.Validator crossField = new ConfigMaintenance.Validator() {
            @Override public void syntax(String text) {
                YamlOutline.parse(text);
            }

            @Override public void validate(String text) {
                YamlOutline outline = YamlOutline.parse(text);
                // limit 99 is only allowed with switch true.
                if (YamlOutline.unquote(outline.get("extra.limit").value()).equals("99")
                        && !YamlOutline.unquote(outline.get("extra.switch").value()).equals("true")) {
                    throw new IllegalArgumentException("extra.limit 99 needs extra.switch");
                }
            }
        };
        String good = DEFAULTS.replace("switch: false", "switch: true").replace("limit: 10", "limit: 99");
        Path file = write(good);
        assertFalse(run(file, DEFAULTS, crossField, Set.of("names")).written());
        assertEquals(good, Files.readString(file));
    }

    @Test void brokenYamlIsRestoredFromTheLastFileThatWorked() throws Exception {
        String mine = DEFAULTS.replace("port: 8080", "port: 1234");
        Path file = write(mine);
        assertFalse(run(file).written());
        String broken = mine.replace("  mode: fast", "  mode: \"fast");
        Files.writeString(file, broken);
        var report = run(file);
        assertTrue(report.written());
        assertTrue(report.restored());
        assertEquals(mine, Files.readString(file), "the last working file, with its own values");
        assertTrue(report.backup().getFileName().toString().endsWith("-broken.yml"));
        assertEquals(broken, Files.readString(report.backup()));
        assertFalse(run(file).written());
        assertTrue(warn.stream().anyMatch(line -> line.contains("could not be read")));
    }

    @Test void brokenYamlWithoutAWorkingVersionGetsTheDefaults() throws Exception {
        Path file = write("server:\n\tport: 1\n");
        var report = run(file);
        assertTrue(report.restored());
        assertEquals(DEFAULTS, Files.readString(file));
        assertEquals("server:\n\tport: 1\n", Files.readString(report.backup()));
    }

    @Test void neverLoopsOnItsOwnOutput() throws Exception {
        Path file = write("server:\n  port: 70000\n");
        assertTrue(run(file).written());
        String once = Files.readString(file);
        for (int i = 0; i < 5; i++) assertFalse(run(file).written());
        assertEquals(once, Files.readString(file));
        assertEquals(1, backups().size());
    }

    @Test void filesItCannotHandleSafelyAreOnlyRead() throws Exception {
        // Not UTF-8 (an ANSI editor).
        Path file = root.resolve("config.yml");
        byte[] latin = "server:\n  mode: fäst\n".getBytes(StandardCharsets.ISO_8859_1);
        Files.write(file, latin);
        assertNotNull(run(file).skipped());
        assertArrayEquals(latin, Files.readAllBytes(file));
        // Larger than 1 MiB.
        String huge = DEFAULTS + "#" + "x".repeat(1_100_000) + "\n";
        Files.writeString(file, huge);
        assertNotNull(run(file).skipped());
        assertEquals(huge, Files.readString(file));
        // YAML the outline does not follow (an anchor) is left alone.
        String anchors = "server: &base\n  port: 1\n";
        Files.writeString(file, anchors);
        ConfigMaintenance.Validator platform = new ConfigMaintenance.Validator() {
            @Override public void syntax(String text) {}
            @Override public void validate(String text) {}
        };
        var report = run(file, DEFAULTS, platform, Set.of());
        assertFalse(report.written());
        assertEquals(anchors, Files.readString(file));
        // Defaults that fail their own checks change nothing.
        Files.writeString(file, "server:\n  port: 1\n");
        ConfigMaintenance.Validator never = new ConfigMaintenance.Validator() {
            @Override public void syntax(String text) {}
            @Override public void validate(String text) { throw new IllegalArgumentException("no"); }
        };
        assertNotNull(run(file, DEFAULTS, never, Set.of()).skipped());
        assertEquals("server:\n  port: 1\n", Files.readString(file));
        assertTrue(backups().isEmpty());
    }

    @Test void symbolicLinksAreOnlyRead() throws Exception {
        Path target = root.resolve("real.yml");
        Files.writeString(target, "server:\n  port: 1\n");
        Path link = root.resolve("config.yml");
        try {
            Files.createSymbolicLink(link, target);
        } catch (IOException | UnsupportedOperationException | SecurityException noLinks) {
            return; // Windows without the privilege
        }
        assertNotNull(run(link).skipped());
        assertEquals("server:\n  port: 1\n", Files.readString(target));
    }

    @Test void keepsLineEndingsAndByteOrderMark() throws Exception {
        String crlf = "﻿server:\r\n  port: 0\r\n";
        Path file = root.resolve("config.yml");
        Files.write(file, crlf.getBytes(StandardCharsets.UTF_8));
        assertTrue(run(file).written());
        String now = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        assertTrue(now.startsWith("﻿server:\r\n  port: 8080\r\n"), now);
        assertFalse(now.replace("\r\n", "").contains("\n"), "every line ends with CRLF");
    }

    @Test void keepsTheNewestBackupsOnly() throws Exception {
        Path file = write("server:\n  port: 1\n");
        for (int i = 0; i < 14; i++) {
            Files.writeString(file, "server:\n  port: " + (70_000 + i) + "\n");
            assertTrue(run(file).written());
        }
        assertEquals(ConfigMaintenance.KEEP_BACKUPS, backups().size());
        assertTrue(Files.isRegularFile(root.resolve(ConfigMaintenance.BACKUPS).resolve("config.last-good.yml")));
    }

    @Test void aSectionWrittenAsAValueAndAValueWrittenAsASection() throws Exception {
        Path file = write("""
                server: none
                extra:
                  switch:
                    deep: true
                """);
        var report = run(file);
        assertTrue(report.written());
        YamlOutline outline = YamlOutline.parse(Files.readString(file));
        assertEquals(YamlOutline.Kind.SECTION, outline.get("server").kind());
        assertEquals("8080", outline.get("server.port").value());
        assertEquals("false", outline.get("extra.switch").value());
        assertNull(outline.get("extra.switch.deep"));
        assertFalse(run(file).written());
    }

    @Test void blockListsAndBlockScalarsAreKept() throws Exception {
        String text = """
                server:
                  port: 8080
                  mode: fast
                  enabled: true
                  hosts:
                    - a.example
                    - b.example
                extra:
                  switch: false
                  limit: 10
                  note: |
                    first line
                    second line
                names:
                  alpha: one
                """;
        Path file = write(text);
        var report = run(file);
        assertFalse(report.written(), "a block list where a one-line list is expected is fine: " + report);
        assertEquals(List.of("extra.note"), report.unknown());
    }

    // ---- the real files ------------------------------------------------------------------------------------

    static String resource(String path) throws IOException {
        try (InputStream input = ConfigMaintenanceTest.class.getResourceAsStream(path)) {
            assertNotNull(input, path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }

    @Test void theRealDefaultsAreStable() throws Exception {
        String defaults = resource("/config.yml");
        YamlOutline outline = YamlOutline.parse(defaults);
        assertTrue(outline.entries.size() > 80);
        assertEquals(defaults.replace("\r\n", "\n"), ConfigMaintenance.plan(defaults, defaults, ANYTHING, Set.of("sources.providers")).text());
    }

    /** A config.yml from 1.0.0-beta.1 gets every newer setting and keeps every value it had. */
    @Test void upgradesARealOldConfiguration() throws Exception {
        String defaults = resource("/config.yml");
        String old = resource("/settings/config-1.0.0-beta.1.yml")
                .replace("backups-to-keep: 3", "backups-to-keep: 7")
                .replace("  java-container-layout: true", "  java-container-layout: false # mine");
        Path file = write(old);
        var report = run(file, defaults, ANYTHING, Set.of("sources.providers"));
        assertTrue(report.written());
        String now = Files.readString(file);
        YamlOutline upgraded = YamlOutline.parse(now), wanted = YamlOutline.parse(defaults);
        for (var entry : wanted.entries) assertNotNull(upgraded.get(entry.path()), "missing " + entry.path());
        assertEquals("7", upgraded.get("geyser.backups-to-keep").value());
        assertTrue(now.contains("  java-container-layout: false # mine"));
        assertTrue(report.added().contains("bedrock-network.*"), report.added().toString());
        assertTrue(report.added().contains("geyser.player-head-skins-of-players"), report.added().toString());
        assertTrue(report.unknown().isEmpty(), report.unknown().toString());
        // Everything the old file said is still in it, line for line, in the same order.
        List<String> oldLines = YamlOutline.split(old.replace("\r\n", "\n"));
        List<String> newLines = YamlOutline.split(now);
        int at = 0;
        for (String line : oldLines) {
            int found = newLines.subList(at, newLines.size()).indexOf(line);
            assertTrue(found >= 0, "lost line: " + line);
            at += found + 1;
        }
        assertFalse(run(file, defaults, ANYTHING, Set.of("sources.providers")).written());
    }
}
