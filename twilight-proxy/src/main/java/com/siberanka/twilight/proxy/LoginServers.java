/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.proxy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the login servers that login plugins on this proxy use (LeaderOS Auth, AuthMeVelocity, AuthMeBungee,
 * LibreLogin, JPremium and similar), so {@code login-servers} works without being filled in by hand. Only the
 * configuration files of plugins whose folder names a login plugin are read, only a few well-known keys are
 * looked at, and only names of servers this proxy has are taken; nothing is written.
 */
final class LoginServers {
    private static final Pattern FOLDER = Pattern.compile("(?i).*(auth|login|premium|limbo).*");
    private static final Set<String> FILES = Set.of("config.yml", "config.yaml", "config.toml", "config.conf");
    private static final Set<String> KEYS = Set.of("auth-server", "auth-servers", "authservers", "auth_servers",
            "authserver", "limbo", "limbo-servers", "limboservers", "limboservernames", "login-servers", "auth-lobby",
            "authlobby", "auth-lobbies");
    private static final Pattern KEY_LINE = Pattern.compile("^\\s*\"?([A-Za-z_-]+)\"?\\s*[:=](.*)$");
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final long MAX_BYTES = 1_048_576;
    private static final int MAX_FOLDERS = 256;

    private LoginServers() {}

    /** Login server (lower case) -> the file it was found in, relative to the proxy folder. */
    static Map<String, String> detect(Path proxyRoot, Collection<String> servers) {
        return detect(proxyRoot, servers, null, ignored -> { });
    }

    /**
     * As {@link #detect(Path, Collection)}, reading only the folders of {@code installed} plugins (case-insensitive;
     * null reads every login plugin folder). A folder left behind by a removed plugin is reported to
     * {@code skipped} instead of being read.
     */
    static Map<String, String> detect(Path proxyRoot, Collection<String> servers, Set<String> installed,
                                      java.util.function.Consumer<String> skipped) {
        Set<String> loaded = installed == null ? null : installed.stream().map(name -> name.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        Map<String, String> known = new TreeMap<>();
        for (String server : servers) known.put(server.toLowerCase(Locale.ROOT), server);
        Map<String, String> found = new TreeMap<>();
        Path plugins = proxyRoot.resolve("plugins");
        if (!Files.isDirectory(plugins)) return found;
        List<Path> folders;
        try (var list = Files.list(plugins)) {
            folders = list.filter(Files::isDirectory).filter(folder -> FOLDER.matcher(folder.getFileName().toString()).matches())
                    .sorted().limit(MAX_FOLDERS).toList();
        } catch (IOException unreadable) {
            return found;
        }
        for (Path folder : folders) {
            if (loaded != null && !loaded.contains(folder.getFileName().toString().toLowerCase(Locale.ROOT))) {
                if (FILES.stream().anyMatch(name -> Files.isRegularFile(folder.resolve(name)))) {
                    skipped.accept(proxyRoot.relativize(folder).toString().replace('\\', '/'));
                }
                continue;
            }
            for (String name : FILES) {
                Path file = folder.resolve(name);
                try {
                    if (!Files.isRegularFile(file) || Files.isSymbolicLink(file) || Files.size(file) > MAX_BYTES) continue;
                    String relative = proxyRoot.relativize(file).toString().replace('\\', '/');
                    for (String server : servers(Files.readString(file, StandardCharsets.UTF_8))) {
                        String proxyName = known.get(server.toLowerCase(Locale.ROOT));
                        if (proxyName != null) found.putIfAbsent(proxyName.toLowerCase(Locale.ROOT), relative);
                    }
                } catch (IOException | RuntimeException unreadable) {
                    // Not readable as text: not a configuration this looks at.
                }
            }
        }
        return found;
    }

    /** The values of the login-server keys in one configuration file (YAML, TOML or HOCON). */
    static List<String> servers(String text) {
        List<String> values = new java.util.ArrayList<>();
        String[] lines = text.split("\r?\n");
        for (int index = 0; index < lines.length; index++) {
            Matcher line = KEY_LINE.matcher(stripComment(lines[index]));
            if (!line.matches() || !KEYS.contains(line.group(1).toLowerCase(Locale.ROOT))) continue;
            String value = line.group(2).strip();
            if (value.equals("[")) {
                // A list over several lines (TOML, HOCON).
                for (int next = index + 1; next < lines.length; next++) {
                    String item = stripComment(lines[next]).strip();
                    if (item.startsWith("]")) break;
                    names(item, values);
                }
                continue;
            }
            if (!value.isEmpty()) {
                names(value, values);
                continue;
            }
            // A YAML block list on the following lines.
            for (int next = index + 1; next < lines.length; next++) {
                String item = stripComment(lines[next]).strip();
                if (item.isEmpty()) continue;
                if (!item.startsWith("- ")) break;
                names(item.substring(2), values);
            }
        }
        return values;
    }

    private static void names(String value, List<String> into) {
        for (String part : value.replaceAll("[\\[\\]\"',]", " ").trim().split("\\s+")) {
            if (NAME.matcher(part).matches()) into.add(part);
        }
    }

    private static String stripComment(String line) {
        int hash = line.indexOf('#');
        return hash < 0 ? line : line.substring(0, hash);
    }
}
