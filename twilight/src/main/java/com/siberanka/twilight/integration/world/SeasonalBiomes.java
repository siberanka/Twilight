/* Copyright (c) 2026 siberanka. Twilight License (PolyForm Noncommercial 1.0.0 + terms), see LICENSE. */
package com.siberanka.twilight.integration.world;

import com.siberanka.twilight.world.BiomeMatcher;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The custom biomes a seasons plugin shows right now, read through RealisticSeasons' public API only.
 *
 * <p>RealisticSeasons registers a biome for every season stage of every vanilla biome (about 190 on
 * a typical server) and sends the current one to clients. Bedrock has fewer free biome slots, so the
 * slots go to the current season's looks first ({@link com.siberanka.twilight.world.BiomeSlots}); the
 * season change event rebuilds the pack. The API gives each vanilla biome's look in a season; a
 * registry biome belongs to the season when its water, sky and (when the season sets one) grass
 * colour are that look's. Nothing of RealisticSeasons' own data or packets is changed.
 */
public final class SeasonalBiomes {
    private SeasonalBiomes() {}

    /** Keys of {@code biomes} (Java definitions) that belong to a season some world shows now; empty without RealisticSeasons. */
    public static Set<String> current(Object bukkitServer, Map<String, Map<String, ?>> biomes) throws ReflectiveOperationException {
        Object manager = bukkitServer.getClass().getMethod("getPluginManager").invoke(bukkitServer);
        Class<?> managerType = Class.forName("org.bukkit.plugin.PluginManager", true, bukkitServer.getClass().getClassLoader());
        Object plugin = managerType.getMethod("getPlugin", String.class).invoke(manager, "RealisticSeasons");
        if (plugin == null || !(Boolean) plugin.getClass().getMethod("isEnabled").invoke(plugin)) return Set.of();
        ClassLoader loader = plugin.getClass().getClassLoader();
        Class<?> api = Class.forName("me.casperge.realisticseasons.api.SeasonsAPI", true, loader);
        Class<?> season = Class.forName("me.casperge.realisticseasons.season.Season", true, loader);
        Class<?> world = Class.forName("org.bukkit.World", true, loader);
        Class<?> biome = Class.forName("org.bukkit.block.Biome", true, loader);
        Object instance = api.getMethod("getInstance").invoke(null);
        if (instance == null) return Set.of();
        Method getSeason = api.getMethod("getSeason", world);
        Method replacement = api.getMethod("getReplacementSeasonBiome", biome, season);

        Set<Object> seasons = new HashSet<>();
        for (Object each : (Iterable<?>) bukkitServer.getClass().getMethod("getWorlds").invoke(bukkitServer)) {
            Object current = getSeason.invoke(instance, each);
            if (current != null && !"DISABLED".equals(String.valueOf(current))) seasons.add(current);
        }
        Object registry = Class.forName("org.bukkit.Registry", true, loader).getField("BIOME").get(null);
        Set<String> signatures = new HashSet<>();
        for (Object vanilla : (Iterable<?>) registry) {
            for (Object current : seasons) {
                Object look = replacement.invoke(instance, vanilla, current);
                if (look == null) continue;
                Integer water = colour(look, "getWaterColoHex"), sky = colour(look, "getSkyColorHex");
                if (water == null || sky == null) continue;
                Integer grass = colour(look, "getGrassColorHex");
                signatures.add(signature(water, sky, grass));
            }
        }
        Set<String> keys = new TreeSet<>();
        biomes.forEach((key, definition) -> {
            Map<?, ?> effects = definition.get("effects") instanceof Map<?, ?> map ? map : Map.of();
            Map<?, ?> attributes = definition.get("attributes") instanceof Map<?, ?> map ? map : Map.of();
            int water = BiomeMatcher.color(effects.get("water_color"), -1);
            int sky = BiomeMatcher.color(attributes.get("minecraft:visual/sky_color"), BiomeMatcher.color(effects.get("sky_color"), -1));
            int grass = BiomeMatcher.color(effects.get("grass_color"), -1);
            if (water < 0 || sky < 0) return;
            if (signatures.contains(signature(water, sky, grass < 0 ? null : grass))
                    || signatures.contains(signature(water, sky, null))) keys.add(key);
        });
        return keys;
    }

    private static String signature(int water, int sky, Integer grass) {
        return water + "/" + sky + "/" + (grass == null ? "*" : grass);
    }

    private static Integer colour(Object look, String getter) throws ReflectiveOperationException {
        Object value = look.getClass().getMethod(getter).invoke(look);
        if (value == null) return null;
        String text = String.valueOf(value).trim().toLowerCase(Locale.ROOT).replace("#", "");
        if (!text.matches("[0-9a-f]{6}")) return null;
        return Integer.parseInt(text, 16);
    }
}
