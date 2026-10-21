/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.proxy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** twilight-proxy's config.yml with the real parser and checks. */
class ProxyConfigMaintenanceTest {
    @TempDir Path root;
    final List<String> info = new ArrayList<>(), warn = new ArrayList<>();

    static String resource(String path) throws Exception {
        try (InputStream input = ProxyConfigMaintenanceTest.class.getResourceAsStream(path)) {
            assertNotNull(input, path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    ProxyConfig load(Path file) throws Exception {
        return ProxyConfig.loadMaintained(file, info::add, warn::add);
    }

    @Test void anOldConfigurationGetsTheNewSettingsAndKeepsItsServers() throws Exception {
        String old = resource("/settings/proxy-config-1.0.0-beta.1.yml")
                .replace("    lobby: auto", "    survival: survival.zip\n    hub: none")
                .replace("transfer-port: 0", "transfer-port: 19133");
        Path file = root.resolve("config.yml");
        Files.writeString(file, old);
        ProxyConfig config = load(file);
        String now = Files.readString(file);
        assertTrue(now.contains("bedrock-network:"), now);
        assertTrue(now.contains("  player-heads: true"), "a setting added to an existing section");
        assertTrue(now.contains("    survival: survival.zip\n") || now.contains("    survival: survival.zip\r\n"));
        assertFalse(now.contains("lobby: auto"), "the example server is never added to the administrator's list");
        assertEquals(19133, config.transferPort());
        assertTrue(config.network().enabled());
        assertTrue(info.stream().anyMatch(line -> line.contains("added")), info.toString());
        assertTrue(Files.list(root.resolve("config-backups")).anyMatch(path -> path.getFileName().toString().startsWith("config-")));
        // Loading again changes nothing.
        String once = Files.readString(file);
        load(file);
        assertEquals(once, Files.readString(file));
    }

    @Test void invalidValuesAreSetBackAndTheProxyStarts() throws Exception {
        String defaults = resource("/proxy-config.yml").replace("\r\n", "\n");
        String broken = defaults.replace("transfer-port: 0", "transfer-port: 99999")
                .replace("max-pack-size-mb: 256", "max-pack-size-mb: lots")
                .replace("  restart: notify", "  restart: sometimes")
                .replace("  view-angle: auto", "  view-angle: 10");
        Path file = root.resolve("config.yml");
        Files.writeString(file, broken);
        assertThrows(IllegalArgumentException.class, () -> ProxyConfig.load(file), "the file as written is refused");
        ProxyConfig config = load(file);
        assertEquals(0, config.transferPort());
        assertEquals(256L * 1024 * 1024, config.maxPackBytes());
        assertEquals(com.siberanka.twilight.network.NetworkSettings.AUTO, config.network().viewAngle());
        assertEquals(4, warn.size(), warn.toString());
        assertEquals(defaults, Files.readString(file));
    }

    @Test void brokenYamlIsRestoredFromTheLastVersionThatWorked() throws Exception {
        String defaults = resource("/proxy-config.yml").replace("\r\n", "\n");
        String mine = defaults.replace("transfer-port: 0", "transfer-port: 19140");
        Path file = root.resolve("config.yml");
        Files.writeString(file, mine);
        load(file);
        Files.writeString(file, mine.replace("secret: \"\"", "secret: \"unterminated"));
        ProxyConfig config = load(file);
        assertEquals(19140, config.transferPort(), "the administrator's last working values are back");
        assertTrue(warn.stream().anyMatch(line -> line.contains("could not be read")), warn.toString());
    }

    @Test void theDefaultFileIsLeftExactlyAsItIs() throws Exception {
        String defaults = resource("/proxy-config.yml");
        Path file = root.resolve("config.yml");
        Files.writeString(file, defaults);
        load(file);
        assertEquals(defaults, Files.readString(file));
        assertTrue(warn.isEmpty(), warn.toString());
        assertTrue(info.isEmpty(), info.toString());
    }
}
