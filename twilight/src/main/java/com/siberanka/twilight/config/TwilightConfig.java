/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record TwilightConfig(
        boolean vanillaOverride,
        boolean strict,
        boolean autoBuildOnStartup,
        boolean syncProviderChanges,
        long startupDelayTicks,
        long providerCommandDelayTicks,
        long maximumSourceBytes,
        int maximumArchiveEntries,
        boolean downloadVanillaAssets,
        boolean autoDiscoverSources,
        List<Path> additionalSources,
        String geyserDirectory,
        boolean deployAfterBuild,
        boolean reloadAfterDeploy,
        int backupsToKeep,
        boolean javaContainerLayout,
        boolean javaTextLayout,
        boolean javaTextSurfaces,
        boolean bedrockBiomeMatching,
        boolean javaGlyphTint,
        boolean javaTranslations,
        boolean javaTextLayers,
        String nametagBackground,
        Map<String, String> providerSources,
        boolean datapackSources,
        boolean sendPackToBedrock
) {
    /** How a provider's content is read: as the provider sends it, only its generated pack, only its folders, or not. */
    public static final Set<String> SOURCE_MODES = Set.of("auto", "generated", "contents", "off");
    /** Providers whose packs and data Twilight discovers on its own. */
    public static final List<String> PROVIDERS = List.of("itemsadder", "nexo", "craftengine", "oraxen", "bettermodel",
            "modelengine", "customnameplates", "betterhud", "realisticseasons");
    /** Bedrock's name-tag background box: kept, hidden, or hidden when a nameplate plugin draws its own. */
    public static final java.util.Set<String> NAMETAG_BACKGROUNDS = java.util.Set.of("auto", "bedrock", "hidden");

    public TwilightConfig {
        if (!NAMETAG_BACKGROUNDS.contains(nametagBackground)) {
            throw new IllegalArgumentException("ui.nametag-background must be auto, bedrock or hidden");
        }
        providerSources = Map.copyOf(providerSources);
        providerSources.forEach((provider, mode) -> {
            if (!SOURCE_MODES.contains(mode)) {
                throw new IllegalArgumentException("sources.providers." + provider + " must be auto, generated, contents or off");
            }
        });
    }

    /** The source mode of a provider; providers without a setting are read automatically. */
    public String sourceMode(String provider) {
        return providerSources.getOrDefault(provider, "auto");
    }

    public TwilightConfig(boolean vanillaOverride, boolean strict, boolean autoBuildOnStartup,
                          boolean syncProviderChanges, long startupDelayTicks, long providerCommandDelayTicks,
                          long maximumSourceBytes, int maximumArchiveEntries, boolean downloadVanillaAssets,
                          boolean autoDiscoverSources, List<Path> additionalSources, String geyserDirectory,
                          boolean deployAfterBuild, boolean reloadAfterDeploy, int backupsToKeep) {
        this(vanillaOverride, strict, autoBuildOnStartup, syncProviderChanges, startupDelayTicks,
                providerCommandDelayTicks, maximumSourceBytes, maximumArchiveEntries, downloadVanillaAssets,
                autoDiscoverSources, additionalSources, geyserDirectory, deployAfterBuild, reloadAfterDeploy,
                backupsToKeep, true, true, true, true, true, true, true, "auto", Map.of(), true, true);
    }

    public TwilightConfig(boolean vanillaOverride, boolean strict, boolean autoBuildOnStartup,
                          boolean syncProviderChanges, long startupDelayTicks, long providerCommandDelayTicks,
                          long maximumSourceBytes, int maximumArchiveEntries, boolean downloadVanillaAssets,
                          boolean autoDiscoverSources, List<Path> additionalSources, String geyserDirectory,
                          boolean deployAfterBuild, boolean reloadAfterDeploy, int backupsToKeep,
                          boolean javaContainerLayout) {
        this(vanillaOverride, strict, autoBuildOnStartup, syncProviderChanges, startupDelayTicks,
                providerCommandDelayTicks, maximumSourceBytes, maximumArchiveEntries, downloadVanillaAssets,
                autoDiscoverSources, additionalSources, geyserDirectory, deployAfterBuild, reloadAfterDeploy,
                backupsToKeep, javaContainerLayout, javaContainerLayout, javaContainerLayout, true, true, true, true, "auto", Map.of(), true, true);
    }

    public TwilightConfig(boolean vanillaOverride, boolean strict, boolean autoBuildOnStartup,
                          boolean syncProviderChanges, long startupDelayTicks, long providerCommandDelayTicks,
                          long maximumSourceBytes, int maximumArchiveEntries, boolean downloadVanillaAssets,
                          boolean autoDiscoverSources, List<Path> additionalSources, String geyserDirectory,
                          boolean deployAfterBuild, boolean reloadAfterDeploy, int backupsToKeep,
                          boolean javaContainerLayout, boolean javaTextLayout) {
        this(vanillaOverride, strict, autoBuildOnStartup, syncProviderChanges, startupDelayTicks,
                providerCommandDelayTicks, maximumSourceBytes, maximumArchiveEntries, downloadVanillaAssets,
                autoDiscoverSources, additionalSources, geyserDirectory, deployAfterBuild, reloadAfterDeploy,
                backupsToKeep, javaContainerLayout, javaTextLayout, javaTextLayout, true, true, true, true, "auto", Map.of(), true, true);
    }

    public TwilightConfig(boolean vanillaOverride, boolean strict, boolean autoBuildOnStartup,
                          boolean syncProviderChanges, long startupDelayTicks, long providerCommandDelayTicks,
                          long maximumSourceBytes, int maximumArchiveEntries, boolean downloadVanillaAssets,
                          boolean autoDiscoverSources, List<Path> additionalSources, String geyserDirectory,
                          boolean deployAfterBuild, boolean reloadAfterDeploy, int backupsToKeep,
                          boolean javaContainerLayout, boolean javaTextLayout, boolean javaTextSurfaces) {
        this(vanillaOverride, strict, autoBuildOnStartup, syncProviderChanges, startupDelayTicks,
                providerCommandDelayTicks, maximumSourceBytes, maximumArchiveEntries, downloadVanillaAssets,
                autoDiscoverSources, additionalSources, geyserDirectory, deployAfterBuild, reloadAfterDeploy,
                backupsToKeep, javaContainerLayout, javaTextLayout, javaTextSurfaces, true, true, true, true, "auto", Map.of(), true, true);
    }

    /** The same configuration with strict publication switched on or off. */
    public TwilightConfig withStrict(boolean value) {
        return new TwilightConfig(vanillaOverride, value, autoBuildOnStartup, syncProviderChanges, startupDelayTicks,
                providerCommandDelayTicks, maximumSourceBytes, maximumArchiveEntries, downloadVanillaAssets,
                autoDiscoverSources, additionalSources, geyserDirectory, deployAfterBuild, reloadAfterDeploy, backupsToKeep,
                javaContainerLayout, javaTextLayout, javaTextSurfaces, bedrockBiomeMatching, javaGlyphTint,
                javaTranslations, javaTextLayers, nametagBackground, providerSources, datapackSources, sendPackToBedrock);
    }

    public static TwilightConfig read(FileConfiguration source, Path serverRoot) {
        long maximumBytes = source.getLong("generation.maximum-source-bytes", 1_073_741_824L);
        int maximumEntries = source.getInt("generation.maximum-archive-entries", 100_000);
        int backups = source.getInt("geyser.backups-to-keep", 3);
        long startupDelay = boundedTicks(source.getLong("generation.startup-delay-ticks", 40L), "startup-delay-ticks");
        long providerDelay = boundedTicks(source.getLong("generation.provider-command-delay-ticks", 100L), "provider-command-delay-ticks");
        if (maximumBytes < 1_048_576L) throw new IllegalArgumentException("maximum-source-bytes must be at least 1 MiB");
        if (maximumEntries < 100) throw new IllegalArgumentException("maximum-archive-entries must be at least 100");
        if (backups < 1 || backups > 20) throw new IllegalArgumentException("backups-to-keep must be between 1 and 20");

        List<Path> additional = source.getStringList("sources.additional").stream()
                .map(serverRoot::resolve).map(Path::normalize).toList();
        return new TwilightConfig(
                source.getBoolean("vanilla-override", false),
                source.getBoolean("generation.strict", true),
                source.getBoolean("generation.auto-build-on-startup", true),
                source.getBoolean("generation.sync-provider-changes", true),
                startupDelay,
                providerDelay,
                maximumBytes,
                maximumEntries,
                source.getBoolean("generation.download-vanilla-assets", true),
                source.getBoolean("sources.auto-discover", true),
                additional,
                source.getString("geyser.directory", "auto"),
                source.getBoolean("geyser.deploy-after-build", true),
                source.getBoolean("geyser.reload-after-deploy", false),
                backups,
                source.getBoolean("ui.java-container-layout", true),
                source.getBoolean("ui.java-text-layout", true),
                source.getBoolean("ui.java-text-surfaces", true),
                source.getBoolean("world.bedrock-biome-matching", true),
                source.getBoolean("ui.java-glyph-tint", true),
                source.getBoolean("ui.java-translations", true),
                source.getBoolean("ui.java-text-layers", true),
                source.getString("ui.nametag-background", "auto").toLowerCase(java.util.Locale.ROOT),
                providerSources(source),
                source.getBoolean("sources.datapacks", true),
                source.getBoolean("geyser.send-pack-to-bedrock", true)
        );
    }

    /** {@code sources.providers}: true (auto), false (off) or a mode per provider. */
    private static Map<String, String> providerSources(FileConfiguration source) {
        Map<String, String> modes = new java.util.LinkedHashMap<>();
        var section = source.getConfigurationSection("sources.providers");
        if (section == null) return modes;
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            String mode = value instanceof Boolean flag ? (flag ? "auto" : "off")
                    : String.valueOf(value).trim().toLowerCase(java.util.Locale.ROOT);
            modes.put(key.toLowerCase(java.util.Locale.ROOT).replace("-", ""), mode);
        }
        return modes;
    }

    private static long boundedTicks(long value, String name) {
        if (value < 1L || value > 72_000L) throw new IllegalArgumentException(name + " must be between 1 and 72000 ticks");
        return value;
    }
}
