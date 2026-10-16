/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.proxy;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** twilight-proxy's settings, validated once when loaded. */
record ProxyConfig(PackSource defaultSource, Map<String, PackSource> servers, boolean transferOnSwitch,
                   String transferAddress, int transferPort, String initialServer, String secret,
                   long maxPackBytes, int downloadTimeoutSeconds, int urlRefreshMinutes,
                   int transferTimeoutSeconds, java.util.Set<String> loginServers,
                   com.siberanka.twilight.host.HostSettings host, boolean updateCheck, boolean updateNotify,
                   LocalSettings local, boolean retireStaleFiles, boolean restartWhenEmpty,
                   boolean textLayout, boolean translations, int loadingProtectionSeconds, boolean customBlocks,
                   boolean itemDisplays, boolean playerHeads, com.siberanka.twilight.network.NetworkSettings network) {
    /** {@code local-backends}: read the exports of backends on this machine from their folders. */
    record LocalSettings(boolean enabled, java.util.List<Path> search, Map<String, Path> servers) {
        static final LocalSettings OFF = new LocalSettings(false, java.util.List.of(), Map.of());
    }

    private static final Pattern FILE_NAME = Pattern.compile("[A-Za-z0-9._-]{1,128}");
    private static final Pattern SERVER_NAME = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    /** With {@code transfer-timeout-seconds: auto}: time for the reconnect and login, plus the pack at this rate. */
    private static final long AUTO_BASE_MILLIS = 180_000;
    private static final long AUTO_BYTES_PER_SECOND = 128 * 1024;
    private static final long AUTO_MAX_MILLIS = 3_600_000;

    /** Where a server's Bedrock pack comes from. */
    sealed interface PackSource {
        /** The pack Twilight on that backend shares over plugin messages. */
        record Auto() implements PackSource {}

        /** No pack: Bedrock players there have none of the per-server packs. */
        record None() implements PackSource {}

        /** A file in twilight-proxy's {@code packs} folder. */
        record File(String name) implements PackSource {}

        /** A direct download link to a .mcpack or .zip. */
        record Url(URI uri) implements PackSource {}
    }

    static ProxyConfig load(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            Files.createDirectories(file.getParent());
            try (InputStream defaults = ProxyConfig.class.getResourceAsStream("/proxy-config.yml")) {
                if (defaults == null) throw new IOException("The default configuration is missing from the JAR");
                Files.copy(defaults, file);
            }
        }
        if (Files.size(file) > 1_048_576) throw new IOException("config.yml is larger than 1 MiB");
        return parse(Files.readString(file, StandardCharsets.UTF_8));
    }

    static ProxyConfig parse(String text) {
        Map<String, Object> root = SimpleYaml.parse(text);
        Map<String, Object> packs = map(root.get("packs"), "packs");
        PackSource fallback = source(string(packs.getOrDefault("default", "auto"), "packs.default"), "packs.default");
        Map<String, PackSource> servers = new LinkedHashMap<>();
        for (var entry : map(packs.getOrDefault("server", Map.of()), "packs.server").entrySet()) {
            String server = entry.getKey();
            if (!SERVER_NAME.matcher(server).matches()) throw new IllegalArgumentException("Invalid server name '" + server + "'");
            String path = "packs.server." + server;
            servers.put(server.toLowerCase(Locale.ROOT), source(string(entry.getValue(), path), path));
        }
        int port = integer(root.getOrDefault("transfer-port", "0"), "transfer-port", 0, 65_535);
        long maxMb = integer(root.getOrDefault("max-pack-size-mb", "256"), "max-pack-size-mb", 1, 2048);
        return new ProxyConfig(fallback, Map.copyOf(servers),
                bool(root.getOrDefault("transfer-on-switch", "true"), "transfer-on-switch"),
                string(root.getOrDefault("transfer-address", ""), "transfer-address").strip(), port,
                string(root.getOrDefault("initial-server", ""), "initial-server").strip().toLowerCase(Locale.ROOT),
                string(root.getOrDefault("secret", ""), "secret").strip(),
                maxMb * 1024 * 1024,
                integer(root.getOrDefault("download-timeout-seconds", "60"), "download-timeout-seconds", 5, 600),
                integer(root.getOrDefault("url-refresh-minutes", "60"), "url-refresh-minutes", 0, 10_080),
                transferTimeout(root.getOrDefault("transfer-timeout-seconds", "auto")),
                serverList(root.getOrDefault("login-servers", java.util.List.of()), "login-servers"),
                hostSettings(root.get("pack-host")),
                updateSetting(root.get("update-check"), "enabled"),
                updateSetting(root.get("update-check"), "notify-players"),
                localSettings(root.get("local-backends")),
                itemSetting(root.get("item-mappings"), "retire-stale-files"),
                restartSetting(root.get("item-mappings")),
                bedrockSwitch(root.get("bedrock"), "text-layout"),
                bedrockSwitch(root.get("bedrock"), "translations"),
                root.get("bedrock") == null ? 300 : integer(map(root.get("bedrock"), "bedrock")
                        .getOrDefault("loading-protection-seconds", "300"), "bedrock.loading-protection-seconds", 0, 1800),
                bedrockSwitch(root.get("bedrock"), "custom-blocks"),
                bedrockSwitch(root.get("bedrock"), "item-displays"),
                bedrockSwitch(root.get("bedrock"), "player-heads"),
                networkSettings(root.get("bedrock-network")));
    }

    /** The {@code bedrock-network} section; missing keeps every default. */
    private static com.siberanka.twilight.network.NetworkSettings networkSettings(Object section) {
        if (section == null) return com.siberanka.twilight.network.NetworkSettings.DEFAULTS;
        Map<String, Object> values = map(section, "bedrock-network");
        return com.siberanka.twilight.network.NetworkSettings.parse(values::get);
    }

    /** A switch in the {@code bedrock} section; on when missing. */
    private static boolean bedrockSwitch(Object section, String key) {
        if (section == null) return true;
        return bool(map(section, "bedrock").getOrDefault(key, "true"), "bedrock." + key);
    }

    /** The {@code local-backends} section; missing means {@code mode: auto} without extra folders. */
    private static LocalSettings localSettings(Object section) {
        if (section == null) return new LocalSettings(true, java.util.List.of(), Map.of());
        Map<String, Object> values = map(section, "local-backends");
        String mode = string(values.getOrDefault("mode", "auto"), "local-backends.mode").strip().toLowerCase(Locale.ROOT);
        if (!mode.equals("auto") && !mode.equals("off")) throw new IllegalArgumentException("local-backends.mode must be auto or off");
        java.util.List<Path> search = new java.util.ArrayList<>();
        Object folders = values.getOrDefault("search", java.util.List.of());
        if (!(folders instanceof java.util.List<?> list)) throw new IllegalArgumentException("local-backends.search must be a list");
        for (Object folder : list) search.add(folderPath(String.valueOf(folder), "local-backends.search"));
        Map<String, Path> servers = new LinkedHashMap<>();
        Object explicit = values.get("server");
        if (explicit != null && !(explicit instanceof String text && text.isBlank())) {
            for (var entry : map(explicit, "local-backends.server").entrySet()) {
                if (!SERVER_NAME.matcher(entry.getKey()).matches()) throw new IllegalArgumentException("local-backends.server: invalid server name '" + entry.getKey() + "'");
                servers.put(entry.getKey().toLowerCase(Locale.ROOT), folderPath(string(entry.getValue(), "local-backends.server." + entry.getKey()), "local-backends.server"));
            }
        }
        return new LocalSettings(mode.equals("auto"), java.util.List.copyOf(search), Map.copyOf(servers));
    }

    private static Path folderPath(String value, String path) {
        String text = value.strip();
        if (text.isEmpty() || text.indexOf('\0') >= 0) throw new IllegalArgumentException(path + ": empty folder");
        try {
            return Path.of(text).normalize();
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException(path + ": invalid folder '" + text + "'");
        }
    }

    /** A switch in the {@code item-mappings} section; on when missing. */
    private static boolean itemSetting(Object section, String key) {
        if (section == null) return true;
        return bool(map(section, "item-mappings").getOrDefault(key, "true"), "item-mappings." + key);
    }

    /** {@code item-mappings.restart}: notify (default) or when-empty. */
    private static boolean restartSetting(Object section) {
        if (section == null) return false;
        String value = string(map(section, "item-mappings").getOrDefault("restart", "notify"), "item-mappings.restart")
                .strip().toLowerCase(Locale.ROOT);
        if (value.equals("notify")) return false;
        if (value.equals("when-empty")) return true;
        throw new IllegalArgumentException("item-mappings.restart must be notify or when-empty");
    }

    /** A switch in the {@code update-check} section; on when the section or the key is missing. */
    private static boolean updateSetting(Object section, String key) {
        if (section == null) return true;
        Map<String, Object> values = map(section, "update-check");
        return bool(values.getOrDefault(key, "true"), "update-check." + key);
    }

    /**
     * How long a reconnect may take, from the transfer until the player reaches the server: a fixed
     * {@code transfer-timeout-seconds}, or (auto) three minutes plus the pack at 128 KiB/s, at most an hour.
     */
    long transferDeadlineMillis(long packBytes) {
        if (transferTimeoutSeconds > 0) return transferTimeoutSeconds * 1000L;
        return Math.min(AUTO_MAX_MILLIS, AUTO_BASE_MILLIS + Math.max(0, packBytes) * 1000 / AUTO_BYTES_PER_SECOND);
    }

    boolean loginServer(String server) {
        return loginServers.contains(server.toLowerCase(Locale.ROOT));
    }

    /** 0 for auto. */
    private static int transferTimeout(Object value) {
        String text = string(value, "transfer-timeout-seconds").strip();
        if (text.equalsIgnoreCase("auto") || text.isEmpty()) return 0;
        return integer(text, "transfer-timeout-seconds", 60, 7200);
    }

    private static java.util.Set<String> serverList(Object value, String path) {
        if (!(value instanceof java.util.List<?> names)) throw new IllegalArgumentException(path + " must be a list such as [auth, limbo]");
        java.util.Set<String> servers = new java.util.LinkedHashSet<>();
        for (Object name : names) {
            String server = String.valueOf(name).strip();
            if (!SERVER_NAME.matcher(server).matches()) throw new IllegalArgumentException(path + ": invalid server name '" + server + "'");
            servers.add(server.toLowerCase(Locale.ROOT));
        }
        return java.util.Set.copyOf(servers);
    }

    /** The {@code pack-host} section; missing means disabled. */
    private static com.siberanka.twilight.host.HostSettings hostSettings(Object section) {
        if (section == null) return com.siberanka.twilight.host.HostSettings.DISABLED;
        Map<String, Object> values = map(section, "pack-host");
        return com.siberanka.twilight.host.HostSettings.parse(values::get);
    }

    PackSource source(String server) {
        return servers.getOrDefault(server.toLowerCase(Locale.ROOT), defaultSource);
    }

    static PackSource source(String value, String path) {
        String trimmed = value.strip();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.equals("auto") || lower.equals("true")) return new PackSource.Auto();
        if (lower.equals("none") || lower.equals("false") || lower.isEmpty()) return new PackSource.None();
        if (lower.startsWith("https://") || lower.startsWith("http://")) {
            try {
                URI uri = new URI(trimmed);
                if (uri.getHost() == null || uri.getUserInfo() != null) throw new IllegalArgumentException("host");
                return new PackSource.Url(uri);
            } catch (Exception invalid) {
                throw new IllegalArgumentException(path + ": invalid download link");
            }
        }
        if (!FILE_NAME.matcher(trimmed).matches() || trimmed.startsWith(".")) {
            throw new IllegalArgumentException(path + ": expected auto, none, a file name in packs/ or an http(s) link");
        }
        return new PackSource.File(trimmed);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value, String path) {
        if (value instanceof Map<?, ?> map) return (Map<String, Object>) map;
        throw new IllegalArgumentException(path + " must be a section");
    }

    private static String string(Object value, String path) {
        if (value instanceof String text) return text;
        throw new IllegalArgumentException(path + " must be a value, not a section");
    }

    private static boolean bool(Object value, String path) {
        String text = string(value, path).strip().toLowerCase(Locale.ROOT);
        if (text.equals("true")) return true;
        if (text.equals("false")) return false;
        throw new IllegalArgumentException(path + " must be true or false");
    }

    private static int integer(Object value, String path, int min, int max) {
        try {
            int number = Integer.parseInt(string(value, path).strip());
            if (number < min || number > max) throw new NumberFormatException();
            return number;
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException(path + " must be a whole number from " + min + " to " + max);
        }
    }
}
