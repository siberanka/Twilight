/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.config;

import com.siberanka.twilight.host.HostSettings;
import com.siberanka.twilight.network.NetworkSettings;
import com.siberanka.twilight.settings.ConfigMaintenance;
import org.bukkit.configuration.file.YamlConfiguration;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Every check Twilight applies to its {@code config.yml}, as one validator: the values {@link TwilightConfig}
 * reads, the {@code bedrock-network} and {@code pack-host} sections, and the options read elsewhere. Used by
 * {@link ConfigMaintenance} to tell which values are invalid.
 */
public final class BackendConfigCheck implements ConfigMaintenance.Validator {
    /** Sections whose keys are the administrator's own: never added, checked or reported. */
    public static final Set<String> FREE_FORM = Set.of("sources.providers");

    private static final Map<String, Set<String>> OPTIONS = Map.of(
            "geyser.item-display-models", Set.of("auto", "on", "off", "true", "false"),
            "geyser.item-display-variants", Set.of("seen", "all"),
            "geyser.restart-for-item-changes", Set.of("notify", "when-empty"),
            "ui.pocket-container-layout", Set.of("java", "bedrock"));
    private static final Map<String, int[]> RANGES = Map.of(
            "geyser.loading-protection-seconds", new int[]{0, 1800},
            "geyser.player-head-skins-of-players", new int[]{0, 4096});
    private static final Set<Integer> GLYPH_CELLS = Set.of(512, 256, 128, 64);

    private final Path serverRoot;

    public BackendConfigCheck(Path serverRoot) {
        this.serverRoot = serverRoot;
    }

    @Override public void syntax(String text) throws Exception {
        new YamlConfiguration().loadFromString(text);
    }

    @Override public void validate(String text) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        TwilightConfig.read(yaml, serverRoot);
        NetworkSettings.parse(key -> yaml.get("bedrock-network." + key));
        HostSettings.parse(key -> yaml.get("pack-host." + key));
        OPTIONS.forEach((path, allowed) -> {
            Object value = yaml.get(path);
            if (value == null) return;
            String option = String.valueOf(value).strip().toLowerCase(Locale.ROOT);
            if (!allowed.contains(option)) throw new IllegalArgumentException(path + " must be one of " + allowed);
        });
        RANGES.forEach((path, range) -> {
            Object value = yaml.get(path);
            if (value == null) return;
            if (!(value instanceof Number number) || number.doubleValue() != Math.rint(number.doubleValue())
                    || number.longValue() < range[0] || number.longValue() > range[1]) {
                throw new IllegalArgumentException(path + " must be a whole number from " + range[0] + " to " + range[1]);
            }
        });
        Object cell = yaml.get("ui.max-glyph-cell");
        if (cell != null && !(cell instanceof Number number && GLYPH_CELLS.contains(number.intValue())
                && number.doubleValue() == number.intValue())) {
            throw new IllegalArgumentException("ui.max-glyph-cell must be 512, 256, 128 or 64");
        }
    }
}
