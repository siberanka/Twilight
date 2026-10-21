/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Chooses the vanilla biome whose appearance is closest to a custom biome.
 *
 * <p>Bedrock only knows its own biomes; Geyser shows every biome it cannot map as the
 * dimension's fallback (ocean in the overworld). Custom biomes from datapacks (Terralith,
 * Incendium) and plugins (RealisticSeasons' seasonal biomes) therefore lost their colours.
 * The match compares what a player sees: grass, foliage and water colour, fog and the
 * kind of precipitation. Colours a biome does not set come from Java's grass and foliage
 * colour maps, sampled by temperature and downfall like Java does.
 */
public final class BiomeMatcher {
    /** Pack path of the vanilla appearance table generated with the pack. */
    public static final String PATH = "twilight/biomes.json";
    private static final int GRID = 17;

    /** Appearance of a vanilla biome, colours as 0xRRGGBB, precipitation 0 none, 1 rain, 2 snow. */
    public record Appearance(int grass, int foliage, int water, int fog, int precipitation) {}

    private final Map<String, Appearance> vanilla;
    private final int[] grassMap;
    private final int[] foliageMap;

    public BiomeMatcher(Map<String, Appearance> vanilla, int[] grassMap, int[] foliageMap) {
        if (grassMap.length != GRID * GRID || foliageMap.length != GRID * GRID) throw new IllegalArgumentException("colour map grid");
        this.vanilla = Map.copyOf(vanilla);
        this.grassMap = grassMap.clone();
        this.foliageMap = foliageMap.clone();
    }

    public Map<String, Appearance> vanilla() { return vanilla; }

    /**
     * Appearance of a biome from its Java definition (datapack JSON or network NBT, as nested maps
     * of numbers, strings and maps). Fog comes from the 26.x attribute or the older effect field.
     */
    public Appearance appearance(Map<String, ?> biome) {
        return appearance(look(biome));
    }

    /** The colours and climate a player sees of a biome definition (see {@link #appearance(Map)}). */
    public BiomeLook look(Map<String, ?> biome) {
        float temperature = number(biome.get("temperature"), 0.5f);
        float downfall = number(biome.get("downfall"), 0.5f);
        boolean precipitation = bool(biome.get("has_precipitation"), true);
        boolean frozen = "frozen".equals(String.valueOf(biome.get("temperature_modifier")));
        Map<?, ?> effects = biome.get("effects") instanceof Map<?, ?> map ? map : Map.of();
        Map<?, ?> attributes = biome.get("attributes") instanceof Map<?, ?> map ? map : Map.of();
        int grass = color(effects.get("grass_color"), sample(grassMap, temperature, downfall));
        Object modifier = effects.get("grass_color_modifier");
        if ("dark_forest".equals(String.valueOf(modifier))) grass = ((grass & 0xFEFEFE) + 0x28340A) >> 1;
        if ("swamp".equals(String.valueOf(modifier))) grass = 0x6A7039;
        int foliage = color(effects.get("foliage_color"), sample(foliageMap, temperature, downfall));
        int water = color(effects.get("water_color"), 0x3F76E4);
        int waterFog = color(attributes.get("minecraft:visual/water_fog_color"), color(effects.get("water_fog_color"), 0x050533));
        int fog = color(attributes.get("minecraft:visual/fog_color"), color(effects.get("fog_color"), 0xC0D8FF));
        int sky = color(attributes.get("minecraft:visual/sky_color"), color(effects.get("sky_color"), sky(temperature)));
        int kind = !precipitation ? 0 : frozen || temperature < 0.15f ? 2 : 1;
        return new BiomeLook(grass, foliage, water, waterFog, fog, sky, temperature, downfall, kind);
    }

    public static Appearance appearance(BiomeLook look) {
        return new Appearance(look.grass(), look.foliage(), look.water(), look.fog(), look.precipitation());
    }

    /** Java's sky colour for a biome that sets none, from its temperature. */
    static int sky(float temperature) {
        float t = Math.clamp(temperature / 3f, -1f, 1f);
        float hue = 0.62222224f - t * 0.05f, saturation = 0.5f + t * 0.1f;
        // Java's HSV conversion truncates each channel (Mth.hsvToRgb).
        int sector = (int) (hue * 6f) % 6;
        float f = hue * 6f - sector, p = 1f - saturation, q = 1f - f * saturation, r = 1f - (1f - f) * saturation;
        float[] rgb = switch (sector) {
            case 0 -> new float[]{1f, r, p};
            case 1 -> new float[]{q, 1f, p};
            case 2 -> new float[]{p, 1f, r};
            case 3 -> new float[]{p, q, 1f};
            case 4 -> new float[]{r, p, 1f};
            default -> new float[]{1f, p, q};
        };
        return Math.clamp((int) (rgb[0] * 255f), 0, 255) << 16 | Math.clamp((int) (rgb[1] * 255f), 0, 255) << 8
                | Math.clamp((int) (rgb[2] * 255f), 0, 255);
    }

    /** The closest vanilla biome among {@code candidates}, or null when none is available. */
    public String closest(Appearance custom, Set<String> candidates) {
        String best = null;
        double bestScore = Double.MAX_VALUE;
        for (Map.Entry<String, Appearance> entry : vanilla.entrySet()) {
            if (!candidates.contains(entry.getKey())) continue;
            double score = distance(custom, entry.getValue());
            if (score < bestScore || score == bestScore && entry.getKey().compareTo(best) < 0) {
                best = entry.getKey();
                bestScore = score;
            }
        }
        return best;
    }

    public static double distance(Appearance a, Appearance b) {
        return 3 * rgb(a.grass(), b.grass()) + 2 * rgb(a.foliage(), b.foliage()) + 2 * rgb(a.water(), b.water())
                + 1.5 * rgb(a.fog(), b.fog()) + (a.precipitation() == b.precipitation() ? 0 : 120);
    }

    private static double rgb(int a, int b) {
        int r = (a >> 16 & 255) - (b >> 16 & 255), g = (a >> 8 & 255) - (b >> 8 & 255), bl = (a & 255) - (b & 255);
        return Math.sqrt(r * r + g * g + bl * bl);
    }

    /** Java's colour map lookup on a 17 x 17 grid of the 256 x 256 map. */
    static int sample(int[] grid, float temperature, float downfall) {
        double t = Math.clamp(temperature, 0, 1), d = Math.clamp(downfall, 0, 1) * t;
        int x = (int) Math.round((1 - t) * (GRID - 1)), y = (int) Math.round((1 - d) * (GRID - 1));
        return grid[y * GRID + x];
    }

    /** Samples a 256 x 256 ARGB colour map down to the stored grid. */
    public static int[] grid(int[] argb256) {
        int[] grid = new int[GRID * GRID];
        for (int y = 0; y < GRID; y++) for (int x = 0; x < GRID; x++) {
            grid[y * GRID + x] = argb256[Math.min(255, y * 16) * 256 + Math.min(255, x * 16)] & 0xFFFFFF;
        }
        return grid;
    }

    private static float number(Object value, float fallback) {
        return value instanceof Number number ? number.floatValue() : fallback;
    }

    private static boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean flag) return flag;
        if (value instanceof Number number) return number.intValue() != 0;
        return fallback;
    }

    /** A Java colour value (number or "#rrggbb"), or {@code fallback}. */
    public static int color(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue() & 0xFFFFFF;
        if (value instanceof String text && text.startsWith("#") && text.length() >= 7) {
            try { return Integer.parseInt(text.substring(text.length() - 6), 16); }
            catch (NumberFormatException ignored) { return fallback; }
        }
        return fallback;
    }

    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.add("grass_map", array(grassMap));
        root.add("foliage_map", array(foliageMap));
        JsonObject biomes = new JsonObject();
        vanilla.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            Appearance a = entry.getValue();
            biomes.add(entry.getKey(), array(new int[]{a.grass(), a.foliage(), a.water(), a.fog(), a.precipitation()}));
        });
        root.add("biomes", biomes);
        return root;
    }

    public static BiomeMatcher fromJson(JsonObject root) {
        if (root.get("version").getAsInt() != 1) throw new IllegalArgumentException("Unsupported biome table");
        Map<String, Appearance> biomes = new LinkedHashMap<>();
        for (var entry : root.getAsJsonObject("biomes").entrySet()) {
            int[] v = ints(entry.getValue().getAsJsonArray());
            biomes.put(entry.getKey(), new Appearance(v[0], v[1], v[2], v[3], v[4]));
        }
        return new BiomeMatcher(biomes, ints(root.getAsJsonArray("grass_map")), ints(root.getAsJsonArray("foliage_map")));
    }

    private static JsonArray array(int[] values) {
        JsonArray array = new JsonArray();
        for (int value : values) array.add(value);
        return array;
    }

    private static int[] ints(JsonArray array) {
        int[] values = new int[array.size()];
        for (int index = 0; index < values.length; index++) values[index] = array.get(index).getAsInt();
        return values;
    }
}
