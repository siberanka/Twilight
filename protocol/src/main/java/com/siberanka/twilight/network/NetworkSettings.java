/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.network;

import java.util.Locale;
import java.util.function.Function;

/**
 * Settings of the {@code bedrock-network} section, shared by Twilight and twilight-proxy. Every number may be
 * {@code auto} ({@link #AUTO}): it then follows the player's connection (see {@link ConnectionQuality}).
 *
 * @param viewAngle       degrees of the cone around the player's look that counts as in view
 * @param behindDistance  blocks within which entities outside that cone still get every update
 * @param wallDistance    blocks from which an entity behind walls is held back
 * @param wallThickness   solid blocks every line of sight to the entity must cross before it counts as hidden
 * @param farDistance     blocks from which visible entities get fewer movement updates
 * @param hiddenUpdateMs  how often a hidden entity is brought up to date anyway
 * @param packetsPerSecond movement and data updates of entities per second and player (others never count)
 * @param threads         threads that test lines of sight for all players
 */
public record NetworkSettings(boolean enabled, int threads, boolean culling, boolean cullPlayers, int viewAngle,
                              int behindDistance, boolean wallCulling, int wallDistance, int wallThickness,
                              int farDistance, int hiddenUpdateMs, int packetsPerSecond, boolean adaptiveCompression) {
    /** A value that follows the connection. */
    public static final int AUTO = -1;

    public static final NetworkSettings DEFAULTS = new NetworkSettings(true, AUTO, true, true, AUTO, AUTO, true, AUTO,
            AUTO, AUTO, AUTO, AUTO, true);
    public static final NetworkSettings DISABLED = new NetworkSettings(false, AUTO, true, true, AUTO, AUTO, true, AUTO,
            AUTO, AUTO, AUTO, AUTO, true);

    public NetworkSettings {
        check("threads", threads, 1, 16);
        check("view-angle", viewAngle, 90, 360);
        check("behind-distance", behindDistance, 0, 128);
        check("wall-distance", wallDistance, 4, 256);
        check("wall-thickness", wallThickness, 1, 16);
        check("far-distance", farDistance, 8, 512);
        check("hidden-update-ms", hiddenUpdateMs, 100, 10_000);
        check("packets-per-second", packetsPerSecond, 100, 100_000);
    }

    private static void check(String key, int value, int min, int max) {
        if (value != AUTO && (value < min || value > max)) {
            throw new IllegalArgumentException("bedrock-network." + key + " must be auto or " + min + "-" + max);
        }
    }

    /**
     * Reads the section through {@code value}, which returns a key's value (String, Number or Boolean) or null
     * when it is missing; missing keys keep their defaults.
     */
    public static NetworkSettings parse(Function<String, Object> value) {
        NetworkSettings d = DEFAULTS;
        return new NetworkSettings(bool(value, "enabled", d.enabled), number(value, "threads"),
                bool(value, "culling", d.culling), bool(value, "cull-players", d.cullPlayers),
                number(value, "view-angle"), number(value, "behind-distance"), bool(value, "wall-culling", d.wallCulling),
                number(value, "wall-distance"), number(value, "wall-thickness"), number(value, "far-distance"),
                number(value, "hidden-update-ms"), number(value, "packets-per-second"),
                bool(value, "adaptive-compression", d.adaptiveCompression));
    }

    private static boolean bool(Function<String, Object> value, String key, boolean fallback) {
        Object raw = value.apply(key);
        if (raw == null) return fallback;
        if (raw instanceof Boolean flag) return flag;
        String text = String.valueOf(raw).strip().toLowerCase(Locale.ROOT);
        return switch (text) {
            case "true", "yes", "on" -> true;
            case "false", "no", "off" -> false;
            default -> throw new IllegalArgumentException("bedrock-network." + key + " must be true or false");
        };
    }

    private static int number(Function<String, Object> value, String key) {
        Object raw = value.apply(key);
        if (raw == null) return AUTO;
        if (raw instanceof Number number) {
            double exact = number.doubleValue();
            if (exact != Math.rint(exact) || exact < 0 || exact > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("bedrock-network." + key + " must be auto or a whole number");
            }
            return (int) exact;
        }
        String text = String.valueOf(raw).strip().toLowerCase(Locale.ROOT);
        if (text.isEmpty() || text.equals("auto")) return AUTO;
        try {
            int parsed = Integer.parseInt(text);
            if (parsed < 0) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("bedrock-network." + key + " must be auto or a whole number");
        }
    }
}
