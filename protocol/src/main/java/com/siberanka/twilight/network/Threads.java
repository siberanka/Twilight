/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.network;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Thread counts that follow the server. The network workers scale with the server's own network threads
 * ({@code settings.netty-threads} in spigot.yml on a backend) and otherwise with the processor count; they stay
 * between 1 and 4, since line-of-sight tests are short and a few threads serve hundreds of players.
 */
public final class Threads {
    private static final Pattern NETTY = Pattern.compile("(?m)^\\s+netty-threads:\\s*(\\d{1,3})\\s*$");

    private Threads() {}

    /**
     * Worker threads for line-of-sight tests.
     *
     * @param configured {@code bedrock-network.threads}, or {@link NetworkSettings#AUTO}
     * @param nettyThreads the server's network threads, or 0 when unknown
     */
    public static int network(int configured, int nettyThreads) {
        if (configured != NetworkSettings.AUTO) return Math.max(1, Math.min(16, configured));
        int base = nettyThreads > 0 ? (nettyThreads + 1) / 2 : (Runtime.getRuntime().availableProcessors() + 3) / 4;
        return Math.max(1, Math.min(4, base));
    }

    /** {@code settings.netty-threads} of a server's spigot.yml, or 0 when it has none. */
    public static int nettyThreads(Path spigotYml) {
        try {
            if (spigotYml == null || !Files.isRegularFile(spigotYml) || Files.size(spigotYml) > 1_048_576) return 0;
            Matcher matcher = NETTY.matcher(Files.readString(spigotYml, StandardCharsets.UTF_8));
            if (!matcher.find()) return 0;
            int threads = Integer.parseInt(matcher.group(1));
            return threads > 0 && threads <= 256 ? threads : 0;
        } catch (IOException | RuntimeException unreadable) {
            return 0;
        }
    }
}
