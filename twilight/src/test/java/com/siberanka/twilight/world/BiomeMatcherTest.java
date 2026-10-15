package com.siberanka.twilight.world;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Custom datapack and plugin biomes are matched to the vanilla biome a player would confuse them with. */
public class BiomeMatcherTest {
    private static final int[] GRASS = climate(0xBFB755, 0x91BD59, 0x80B497);
    private static final int[] FOLIAGE = climate(0xAEA42A, 0x77AB2F, 0x60A17B);
    static final BiomeMatcher MATCHER = new BiomeMatcher(Map.of(
            "minecraft:plains", new BiomeMatcher.Appearance(0x91BD59, 0x77AB2F, 0x3F76E4, 0xC0D8FF, 1),
            "minecraft:snowy_plains", new BiomeMatcher.Appearance(0x80B497, 0x60A17B, 0x3F76E4, 0xC0D8FF, 2),
            "minecraft:swamp", new BiomeMatcher.Appearance(0x6A7039, 0x6A7039, 0x617B64, 0xC0D8FF, 1),
            "minecraft:desert", new BiomeMatcher.Appearance(0xBFB755, 0xAEA42A, 0x3F76E4, 0xC0D8FF, 0),
            "minecraft:nether_wastes", new BiomeMatcher.Appearance(0xBFB755, 0xAEA42A, 0x3F76E4, 0x330808, 0),
            "minecraft:soul_sand_valley", new BiomeMatcher.Appearance(0xBFB755, 0xAEA42A, 0x3F76E4, 0x1B4745, 0)),
            GRASS, FOLIAGE);
    private static final Set<String> ALL = MATCHER.vanilla().keySet();

    @Test
    void terralithStyleBiomesFindTheirLook() {
        // 26.x network data: fog in attributes, explicit colours in effects.
        Map<String, Object> alpine = Map.of("temperature", -0.3f, "downfall", 0.6f, "has_precipitation", (byte) 1,
                "effects", Map.of("water_color", 0x3D57D6),
                "attributes", Map.of("minecraft:visual/fog_color", 0xC0D8FF));
        assertEquals("minecraft:snowy_plains", MATCHER.closest(MATCHER.appearance(alpine), ALL));

        Map<String, Object> bayou = Map.of("temperature", 0.8f, "downfall", 0.9f, "has_precipitation", true,
                "effects", Map.of("water_color", 0x617B64, "grass_color_modifier", "swamp"));
        assertEquals("minecraft:swamp", MATCHER.closest(MATCHER.appearance(bayou), ALL));
    }

    @Test
    void netherBiomesAreToldApartByTheirFog() {
        Map<String, Object> ashen = Map.of("temperature", 2.0f, "downfall", 0f, "has_precipitation", false,
                "attributes", Map.of("minecraft:visual/fog_color", "#2a0b0b"));
        assertEquals("minecraft:nether_wastes", MATCHER.closest(MATCHER.appearance(ashen), ALL));
        Map<String, Object> soul = Map.of("temperature", 2.0f, "downfall", 0f, "has_precipitation", false,
                "effects", Map.of("fog_color", 0x1A5050));
        assertEquals("minecraft:soul_sand_valley", MATCHER.closest(MATCHER.appearance(soul), ALL));
        Map<String, Object> dunes = Map.of("temperature", 2.0f, "downfall", 0f, "has_precipitation", false);
        assertEquals("minecraft:desert", MATCHER.closest(MATCHER.appearance(dunes), ALL));
    }

    @Test
    void seasonalColoursFollowTheirColourWhileTemperatureFollowsTheMap() {
        // RealisticSeasons-style winter variant of plains: fixed snowy grass colour, rain kept.
        Map<String, Object> winter = Map.of("temperature", 0.8f, "downfall", 0.4f, "has_precipitation", 1,
                "effects", Map.of("grass_color", 0x80B497, "foliage_color", 0x60A17B));
        assertEquals("minecraft:plains", MATCHER.closest(MATCHER.appearance(winter), Set.of("minecraft:plains", "minecraft:swamp")));
        assertEquals(0x80B497, MATCHER.appearance(winter).grass());
        // Without explicit colours the colour map decides: hot and wet samples its top-left corner.
        assertEquals(GRASS[0], MATCHER.appearance(Map.of("temperature", 1f, "downfall", 1f)).grass());
        assertNull(MATCHER.closest(MATCHER.appearance(winter), Set.of()));
    }

    @Test
    void tableRoundTrips() {
        BiomeMatcher copy = BiomeMatcher.fromJson(MATCHER.toJson());
        assertEquals(MATCHER.vanilla(), copy.vanilla());
        assertEquals(BiomeMatcher.sample(GRASS, 0.3f, 0.7f),
                copy.appearance(Map.of("temperature", 0.3f, "downfall", 0.7f)).grass());
    }


    /** Java-like colour map regions: hot and dry, temperate, cold (17 x 17 grid, x = 1 - temperature). */
    public static int[] climate(int hotDry, int temperate, int cold) {
        int[] grid = new int[17 * 17];
        for (int y = 0; y < 17; y++) for (int x = 0; x < 17; x++) {
            grid[y * 17 + x] = x >= 12 ? cold : x <= 2 && y >= 12 ? hotDry : temperate;
        }
        return grid;
    }
}
