/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.conflicts;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What a Twilight instance can see of its surroundings: the plugins of its server or proxy, the extensions in the
 * Geyser folder it works with, and a few facts about the platform. Names are compared in lower case.
 *
 * @param proxy           true for twilight-proxy
 * @param platform        a label for reports ("Paper 1.21.4 (Leaf)", "Velocity 3.4")
 * @param plugins         installed plugins
 * @param extensions      ids and names of the Geyser extensions in the Geyser folder (empty when unknown)
 * @param geyserHere      whether Geyser runs in this process
 * @param geyserVersion   Geyser's version label, or empty
 * @param folia           whether this is a regionised (Folia) server
 * @param peerVersionCode the version code the other Twilight half announced (backend: of the proxy), 0 when unknown
 * @param proxyShowsDisplays backend only: the proxy in front shows item displays too
 */
public record Environment(boolean proxy, String platform, Set<String> plugins, Set<String> extensions, boolean geyserHere,
                          String geyserVersion, boolean folia, int peerVersionCode, boolean proxyShowsDisplays) {
    public Environment {
        plugins = lower(plugins);
        extensions = lower(extensions);
    }

    private static Set<String> lower(Collection<String> names) {
        return names.stream().map(name -> name.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
    }

    public boolean hasPlugin(String name) { return plugins.contains(name.toLowerCase(Locale.ROOT)); }

    public boolean hasExtension(String id) { return extensions.contains(id.toLowerCase(Locale.ROOT)); }

    /** Whether any plugin or extension has a name containing {@code part}. */
    public boolean mentions(String part) {
        String needle = part.toLowerCase(Locale.ROOT);
        return plugins.stream().anyMatch(name -> name.contains(needle)) || extensions.stream().anyMatch(name -> name.contains(needle));
    }

    /** {@code 1.0.2-pre.3} as 10002 (major * 10000 + minor * 100 + patch); 0 when it is not a version. */
    public static int versionCode(String version) {
        if (version == null) return 0;
        var matcher = java.util.regex.Pattern.compile("^([0-9]{1,3})[.]([0-9]{1,2})(?:[.]([0-9]{1,2}))?").matcher(version.strip());
        if (!matcher.find()) return 0;
        int patch = matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3));
        return Integer.parseInt(matcher.group(1)) * 10_000 + Integer.parseInt(matcher.group(2)) * 100 + patch;
    }

    /** {@code 10002} as {@code 1.0.2}. */
    public static String describe(int code) {
        return code / 10_000 + "." + code / 100 % 100 + "." + code % 100;
    }
}
