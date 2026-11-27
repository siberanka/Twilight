/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.conflicts;

import java.util.Locale;
import java.util.function.Function;

/**
 * The {@code integrations.conflicts} section of the backend and proxy configuration.
 *
 * @param enabled         {@code mode}: auto (default) detects and resolves conflicts; off only keeps the diagnostics
 * @param modelBones      who shows ModelEngine and BetterModel models (their bones) on Bedrock
 * @param displayEntities who registers Geyser's item and block display entities
 * @param foreignMappings what to do with other tools' Geyser mapping files that map what Twilight maps
 * @param maxNotices      overlap notices logged per check; the rest is summed up in one line
 */
public record ConflictSettings(boolean enabled, Owner modelBones, Owner displayEntities, Foreign foreignMappings,
                               int maxNotices) {
    /** What happens to a foreign mapping file that overlaps Twilight's mappings. */
    public enum Foreign { OFF, REPORT, RETIRE }

    public static final ConflictSettings DEFAULTS = new ConflictSettings(true, Owner.AUTO, Owner.AUTO, Foreign.REPORT, 10);

    /** Reads {@code integrations.conflicts.*} through {@code key} (a key relative to the section); null values keep defaults. */
    public static ConflictSettings parse(Function<String, Object> key) {
        String mode = text(key.apply("mode"), "auto");
        if (!mode.equals("auto") && !mode.equals("off")) throw new IllegalArgumentException("integrations.conflicts.mode must be auto or off");
        String foreign = text(key.apply("foreign-mappings"), "report");
        Foreign policy = switch (foreign) {
            case "report" -> Foreign.REPORT;
            case "retire" -> Foreign.RETIRE;
            case "off" -> Foreign.OFF;
            default -> throw new IllegalArgumentException("integrations.conflicts.foreign-mappings must be report, retire or off");
        };
        int notices;
        try {
            notices = Integer.parseInt(text(key.apply("max-notices"), "10"));
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("integrations.conflicts.max-notices must be a number from 1 to 100");
        }
        if (notices < 1 || notices > 100) throw new IllegalArgumentException("integrations.conflicts.max-notices must be a number from 1 to 100");
        return new ConflictSettings(mode.equals("auto"), Owner.parse(key.apply("model-bones"), "integrations.conflicts.model-bones"),
                Owner.parse(key.apply("display-entities"), "integrations.conflicts.display-entities"), policy, notices);
    }

    private static String text(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value).strip().toLowerCase(Locale.ROOT);
    }
}
