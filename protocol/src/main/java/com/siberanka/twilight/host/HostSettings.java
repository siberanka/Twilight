/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.host;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Settings of the {@code pack-host} section, shared by Twilight and twilight-proxy.
 *
 * @param publicAddress {@code auto} (the host name each player joined with), a host name or IP address, or an
 *                      {@code http(s)://host[:port]} base when a reverse proxy or TLS terminator sits in front
 * @param publicPort    port in links when {@code public-address} has none; 0 uses {@code port}
 */
public record HostSettings(boolean enabled, String bindAddress, int port, String publicAddress, int publicPort,
                           boolean requirePlayerAddress, int linkMinutes, int downloadsPerLink, int maxConnections,
                           int maxConnectionsPerAddress, List<InetAddress> trustedProxies) {
    private static final Pattern HOST = Pattern.compile("[A-Za-z0-9.-]{1,253}|\\[[0-9A-Fa-f:.]{2,45}]|[0-9A-Fa-f:.]{2,45}");
    private static final Pattern IPV4 = Pattern.compile("(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})");
    private static final Pattern IPV6 = Pattern.compile("[0-9A-Fa-f:.]{2,45}");

    public static final HostSettings DISABLED = new HostSettings(false, "", 8163, "auto", 0, true, 10, 3, 64, 4, List.of());

    public HostSettings {
        if (port < 1 || port > 65_535) throw new IllegalArgumentException("pack-host.port must be 1-65535");
        if (publicPort < 0 || publicPort > 65_535) throw new IllegalArgumentException("pack-host.public-port must be 0-65535");
        if (linkMinutes < 1 || linkMinutes > 120) throw new IllegalArgumentException("pack-host.link-minutes must be 1-120");
        if (downloadsPerLink < 1 || downloadsPerLink > 20) throw new IllegalArgumentException("pack-host.downloads-per-link must be 1-20");
        if (maxConnections < 1 || maxConnections > 4096) throw new IllegalArgumentException("pack-host.max-connections must be 1-4096");
        if (maxConnectionsPerAddress < 1 || maxConnectionsPerAddress > 64) {
            throw new IllegalArgumentException("pack-host.max-connections-per-address must be 1-64");
        }
        bindAddress = bindAddress == null ? "" : bindAddress.strip();
        if (!bindAddress.isEmpty() && literal(bindAddress) == null) {
            throw new IllegalArgumentException("pack-host.bind-address must be empty or an IP address");
        }
        publicAddress = publicAddress == null || publicAddress.isBlank() ? "auto" : publicAddress.strip();
        if (!publicAddress.equalsIgnoreCase("auto")) base(publicAddress); // validates
        trustedProxies = List.copyOf(trustedProxies);
    }

    /**
     * Reads the section through {@code value}, which returns a key's String, Number, Boolean or List value
     * (or null when it is missing); missing keys keep their defaults.
     */
    public static HostSettings parse(Function<String, Object> value) {
        HostSettings d = DISABLED;
        List<InetAddress> proxies = new ArrayList<>();
        Object list = value.apply("trusted-proxies");
        if (list instanceof String inline && inline.strip().startsWith("[") && inline.strip().endsWith("]")) {
            String body = inline.strip().substring(1, inline.strip().length() - 1).strip();
            list = body.isEmpty() ? List.of() : java.util.Arrays.stream(body.split(","))
                    .map(entry -> entry.strip().replace("\"", "").replace("'", "")).toList();
        }
        if (list instanceof List<?> entries) {
            for (Object entry : entries) {
                InetAddress address = literal(String.valueOf(entry).strip());
                if (address == null) throw new IllegalArgumentException("pack-host.trusted-proxies: '" + entry + "' is not an IP address");
                proxies.add(PackHost.normalise(address));
            }
        } else if (list != null && !String.valueOf(list).isBlank() && !String.valueOf(list).strip().equals("[]")) {
            throw new IllegalArgumentException("pack-host.trusted-proxies must be a list of IP addresses");
        }
        return new HostSettings(bool(value, "enabled", d.enabled), text(value, "bind-address", d.bindAddress),
                integer(value, "port", d.port), text(value, "public-address", d.publicAddress),
                integer(value, "public-port", d.publicPort), bool(value, "require-player-address", d.requirePlayerAddress),
                integer(value, "link-minutes", d.linkMinutes), integer(value, "downloads-per-link", d.downloadsPerLink),
                integer(value, "max-connections", d.maxConnections),
                integer(value, "max-connections-per-address", d.maxConnectionsPerAddress), proxies);
    }

    /** The link base ({@code scheme://host[:port]}) for a player who joined with {@code joinAddress}. */
    public Optional<String> baseUrl(String joinAddress, int boundPort) {
        int linkPort = publicPort > 0 ? publicPort : boundPort;
        if (!publicAddress.equalsIgnoreCase("auto")) return Optional.of(base(publicAddress, linkPort));
        if (joinAddress == null) return Optional.empty();
        String host = joinAddress.strip();
        // Bedrock reports the address as typed; a trailing dot or port is not part of the host.
        if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        if (host.isEmpty() || !HOST.matcher(host).matches()) return Optional.empty();
        if (host.contains(":") && !host.startsWith("[")) host = "[" + host + "]";
        return Optional.of("http://" + host.toLowerCase(Locale.ROOT) + ":" + linkPort);
    }

    private static String base(String address) {
        return base(address, 1);
    }

    private static String base(String address, int linkPort) {
        String text = address.strip();
        if (text.startsWith("http://") || text.startsWith("https://")) {
            try {
                URI uri = new URI(text);
                if (uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                        || uri.getPath() != null && !uri.getPath().isEmpty() && !uri.getPath().equals("/")) {
                    throw new IllegalArgumentException();
                }
                return uri.getScheme() + "://" + uri.getRawAuthority();
            } catch (Exception invalid) {
                throw new IllegalArgumentException("pack-host.public-address: expected auto, a host name or http(s)://host[:port]");
            }
        }
        if (!HOST.matcher(text).matches()) {
            throw new IllegalArgumentException("pack-host.public-address: expected auto, a host name or http(s)://host[:port]");
        }
        if (text.contains(":") && !text.startsWith("[")) text = "[" + text + "]";
        return "http://" + text.toLowerCase(Locale.ROOT) + ":" + linkPort;
    }

    boolean trusted(InetAddress remote) {
        return trustedProxies.contains(remote);
    }

    /** An IP address literal, never a DNS lookup; null when {@code text} is not one. */
    static InetAddress literal(String text) {
        try {
            if (IPV4.matcher(text).matches()) {
                for (String part : text.split("\\.")) if (Integer.parseInt(part) > 255) return null;
                return InetAddress.getByName(text);
            }
            String bare = text.startsWith("[") && text.endsWith("]") ? text.substring(1, text.length() - 1) : text;
            if (bare.contains(":") && IPV6.matcher(bare).matches()) return InetAddress.getByName(bare);
        } catch (UnknownHostException | NumberFormatException invalid) {
            return null;
        }
        return null;
    }

    private static boolean bool(Function<String, Object> value, String key, boolean fallback) {
        Object raw = value.apply(key);
        if (raw == null) return fallback;
        String text = String.valueOf(raw).strip().toLowerCase(Locale.ROOT);
        if (text.equals("true")) return true;
        if (text.equals("false")) return false;
        throw new IllegalArgumentException("pack-host." + key + " must be true or false");
    }

    private static int integer(Function<String, Object> value, String key, int fallback) {
        Object raw = value.apply(key);
        if (raw == null) return fallback;
        try {
            return Integer.parseInt(String.valueOf(raw).strip());
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("pack-host." + key + " must be a whole number");
        }
    }

    private static String text(Function<String, Object> value, String key, String fallback) {
        Object raw = value.apply(key);
        if (raw == null) return fallback;
        if (raw instanceof List<?> || raw instanceof java.util.Map<?, ?>) throw new IllegalArgumentException("pack-host." + key + " must be a value");
        return String.valueOf(raw);
    }
}
