package com.siberanka.twilight.world;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Custom biome looks redefine free Bedrock biomes; the pack files carry the exact Java colours. */
class BiomeSlotsTest {
    private static final BiomeLook BLOSSOM = new BiomeLook(0xF29AC0, 0xE36FA4, 0x5DB7EF, 0x2A5C8A, 0xFFD8EC, 0x9CC8FF,
            0.7f, 0.8f, 1);
    private static final BiomeLook WINTER = new BiomeLook(0xB8D0E1, 0xB8D0E1, 0x216FC4, 0x216FC4, 0xA6D1FF, 0xD2DEFF,
            0.05f, 0.4f, 2);

    @Test
    void everyLookGetsASlotWhenTheyFit() {
        BiomeSlots slots = BiomeSlots.assign(Map.of("demo:blossom_vale", BLOSSOM, "realisticseasons:forest_winter", WINTER,
                "realisticseasons:plains_winter", WINTER), BiomeMatcherTest.MATCHER.vanilla().values());
        assertEquals(2, slots.slots().size());
        BiomeSlots.Slot first = slots.slots().getFirst();
        assertEquals(19, first.id());
        assertEquals("taiga_hills", first.name());
        assertEquals(List.of("demo:blossom_vale"), first.biomes());
        // Biomes with an identical look share one slot.
        assertEquals(List.of("realisticseasons:forest_winter", "realisticseasons:plains_winter"), slots.slots().get(1).biomes());
    }

    @Test
    void packFilesCarryTheExactColoursAndClimate() {
        BiomeSlots slots = BiomeSlots.assign(Map.of("demo:blossom_vale", BLOSSOM), BiomeMatcherTest.MATCHER.vanilla().values());
        Map<String, byte[]> files = slots.packFiles();
        JsonObject biome = json(files.get("biomes/taiga_hills.client_biome.json")).getAsJsonObject("minecraft:client_biome");
        assertEquals("minecraft:taiga_hills", biome.getAsJsonObject("description").get("identifier").getAsString());
        JsonObject components = biome.getAsJsonObject("components");
        assertEquals("#f29ac0", components.getAsJsonObject("minecraft:grass_appearance").get("color").getAsString());
        assertEquals("#e36fa4", components.getAsJsonObject("minecraft:foliage_appearance").get("color").getAsString());
        assertEquals("#5db7ef", components.getAsJsonObject("minecraft:water_appearance").get("surface_color").getAsString());
        assertEquals("#9cc8ff", components.getAsJsonObject("minecraft:sky_color").get("sky_color").getAsString());
        assertEquals("twilight:fog_taiga_hills",
                components.getAsJsonObject("minecraft:fog_appearance").get("fog_identifier").getAsString());
        JsonObject fog = json(files.get("fogs/twilight_taiga_hills.json")).getAsJsonObject("minecraft:fog_settings");
        assertEquals("#ffd8ec", fog.getAsJsonObject("distance").getAsJsonObject("air").get("fog_color").getAsString());
        assertEquals("#2a5c8a", fog.getAsJsonObject("distance").getAsJsonObject("water").get("fog_color").getAsString());
        BiomeSlots read = BiomeSlots.fromJson(json(files.get(BiomeSlots.PATH)));
        assertEquals(slots.slots(), read.slots());
    }

    @Test
    void moreLooksThanSlotsKeepTheWorstApproximated() {
        Map<String, BiomeLook> custom = new LinkedHashMap<>();
        // A close copy of vanilla plains, which the vanilla table already shows well...
        custom.put("demo:almost_plains", new BiomeLook(0x91BD5A, 0x77AB2F, 0x3F76E4, 0x050533, 0xC0D8FF, 0x78A7FF, 0.8f, 0.4f, 1));
        // ...and more distinct looks than free slots.
        for (int index = 0; index < BiomeSlots.FREE.size(); index++) {
            int colour = 0x100000 * (index % 15 + 1) + 0x0F00 * (index / 15) + 0x80;
            custom.put("demo:look_" + index, new BiomeLook(colour, colour, 0x3F76E4, 0x050533, 0xC0D8FF, 0x78A7FF, 0.8f, 0.4f, 1));
        }
        BiomeSlots slots = BiomeSlots.assign(custom, BiomeMatcherTest.MATCHER.vanilla().values());
        assertEquals(BiomeSlots.FREE.size(), slots.slots().size());
        assertTrue(slots.slots().stream().noneMatch(slot -> slot.biomes().contains("demo:almost_plains")));
        assertEquals(BiomeSlots.FREE.size(), slots.slots().stream().map(BiomeSlots.Slot::id).distinct().count());
    }

    @Test
    void theCurrentSeasonsLooksGetSlotsFirst() {
        Map<String, BiomeLook> custom = new LinkedHashMap<>();
        for (int index = 0; index < BiomeSlots.FREE.size() + 5; index++) {
            int colour = 0x100000 * (index % 15 + 1) + 0x0F00 * (index / 15) + 0x80;
            custom.put("realisticseasons:look_" + index, new BiomeLook(colour, colour, 0x3F76E4, 0x050533, 0xC0D8FF,
                    0x78A7FF, 0.8f, 0.4f, 1));
        }
        // Winter's pale look is close to vanilla snow, but it is what players see now.
        custom.put("realisticseasons:winter", new BiomeLook(0x84B097, 0x60A17B, 0x3F76E4, 0x050533, 0xC0D8FF, 0x78A7FF,
                0.0f, 0.5f, 2));
        BiomeSlots slots = BiomeSlots.assign(custom, BiomeMatcherTest.MATCHER.vanilla().values(),
                java.util.Set.of("realisticseasons:winter"));
        assertTrue(slots.slots().stream().anyMatch(slot -> slot.biomes().contains("realisticseasons:winter")));
        assertEquals(BiomeSlots.FREE.size(), slots.slots().size());
    }

    @Test
    void rejectsSlotsThatAreNotFreeBedrockBiomes() {
        JsonObject table = new BiomeSlots(List.of(new BiomeSlots.Slot(1, "plains", BLOSSOM, List.of("demo:x")))).toJson();
        assertThrows(IllegalArgumentException.class, () -> BiomeSlots.fromJson(table));
    }

    @Test
    void readsSkyAndUnderwaterFogFromJavaDefinitions() {
        Map<String, Object> swampy = Map.of("temperature", 0.8f, "downfall", 0.9f,
                "effects", Map.of("water_color", "#617b64"),
                "attributes", Map.of("minecraft:visual/sky_color", "#78a7ff", "minecraft:visual/water_fog_color", "#232317"));
        BiomeLook look = BiomeMatcherTest.MATCHER.look(swampy);
        assertEquals(0x78A7FF, look.sky());
        assertEquals(0x232317, look.waterFog());
        assertEquals(0x617B64, look.water());
        // Older definitions without a sky colour get Java's temperature formula (plains: 0x78A7FF).
        assertEquals(0x78A7FF, BiomeMatcher.sky(0.8f));
    }

    private static JsonObject json(byte[] bytes) {
        return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
