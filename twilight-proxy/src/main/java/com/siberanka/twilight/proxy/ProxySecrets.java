/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.proxy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The secret twilight-proxy shares with Twilight on the backends: the configured one, else the
 * secret the proxy already shares with its servers (Velocity's forwarding secret for modern or
 * BungeeGuard forwarding, or BungeeGuard's token on BungeeCord). No secret, no automatic packs.
 */
final class ProxySecrets {
    private static final Pattern TOML = Pattern.compile("^\\s*([A-Za-z0-9_-]+)\\s*=\\s*\"([^\"]*)\"\\s*(#.*)?$");
    private static final long MAX_FILE = 64 * 1024;

    private ProxySecrets() {}

    static List<String> discover(Path proxyRoot, boolean velocity, String configured) {
        List<String> secrets = new ArrayList<>();
        if (!configured.isBlank()) secrets.add(configured);
        if (velocity) {
            Map<String, String> toml = toml(proxyRoot.resolve("velocity.toml"));
            String mode = toml.getOrDefault("player-info-forwarding-mode", "");
            if (mode.equalsIgnoreCase("modern") || mode.equalsIgnoreCase("bungeeguard")) {
                String file = toml.getOrDefault("forwarding-secret-file", "forwarding.secret");
                Path secretFile = proxyRoot.resolve(file).normalize();
                if (secretFile.startsWith(proxyRoot.normalize())) add(secrets, read(secretFile));
                add(secrets, toml.get("forwarding-secret"));
            }
        } else {
            Path token = proxyRoot.resolve("plugins/BungeeGuard/token.yml");
            String text = read(token);
            if (text != null) {
                try {
                    Object value = SimpleYaml.parse(text).get("token");
                    if (value instanceof String string) add(secrets, string);
                } catch (IllegalArgumentException unreadable) {
                    // Not our file format to fix; the configured secret still applies.
                }
            }
        }
        return secrets;
    }

    private static Map<String, String> toml(Path file) {
        Map<String, String> values = new java.util.HashMap<>();
        String text = read(file);
        if (text == null) return values;
        for (String line : text.split("\r?\n")) {
            if (line.strip().startsWith("[")) break; // only the top-level table
            Matcher matcher = TOML.matcher(line);
            if (matcher.matches()) values.put(matcher.group(1), matcher.group(2));
        }
        return values;
    }

    private static String read(Path file) {
        try {
            if (!Files.isRegularFile(file) || Files.size(file) > MAX_FILE) return null;
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            return null;
        }
    }

    private static void add(List<String> secrets, String secret) {
        if (secret != null && !secret.isBlank() && !secrets.contains(secret.strip())) secrets.add(secret.strip());
    }
}
