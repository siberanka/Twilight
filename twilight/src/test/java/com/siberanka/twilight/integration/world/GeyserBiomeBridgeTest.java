package com.siberanka.twilight.integration.world;

import com.siberanka.twilight.world.BiomeMatcher;
import net.kyori.adventure.key.Key;
import org.cloudburstmc.nbt.NbtMap;
import org.geysermc.mcprotocollib.protocol.data.game.RegistryEntry;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundRegistryDataPacket;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Registry packets as a Java server sends them: vanilla entries without data, custom ones with data. */
class GeyserBiomeBridgeTest {
    private static final BiomeMatcher MATCHER = new BiomeMatcher(Map.of(
            "minecraft:plains", new BiomeMatcher.Appearance(0x91BD59, 0x77AB2F, 0x3F76E4, 0xC0D8FF, 1),
            "minecraft:snowy_plains", new BiomeMatcher.Appearance(0x80B497, 0x60A17B, 0x3F76E4, 0xC0D8FF, 2),
            "minecraft:nether_wastes", new BiomeMatcher.Appearance(0xBFB755, 0xAEA42A, 0x3F76E4, 0x330808, 0)),
            com.siberanka.twilight.world.BiomeMatcherTest.climate(0xBFB755, 0x91BD59, 0x80B497),
            com.siberanka.twilight.world.BiomeMatcherTest.climate(0xAEA42A, 0x77AB2F, 0x60A17B));

    @Test
    void mapsUnknownBiomesOnlyAndKeepsGeysersMappings() throws Exception {
        Map<String, Integer> identifiers = new HashMap<>(Map.of("minecraft:plains", 1, "minecraft:snowy_plains", 12,
                "minecraft:nether_wastes", 8, "minecraft:ocean", 0));
        var packet = new ClientboundRegistryDataPacket(Key.key("minecraft", "worldgen/biome"), List.of(
                new RegistryEntry(Key.key("minecraft", "plains"), null),
                new RegistryEntry(Key.key("terralith", "glacial_chasm"), NbtMap.builder()
                        .putFloat("temperature", -0.7f).putFloat("downfall", 0.5f).putByte("has_precipitation", (byte) 1)
                        .putCompound("effects", NbtMap.builder().putInt("water_color", 0x3D57D6).build()).build()),
                new RegistryEntry(Key.key("incendium", "ash_barrens"), NbtMap.builder()
                        .putFloat("temperature", 2f).putFloat("downfall", 0f).putByte("has_precipitation", (byte) 0)
                        .putCompound("attributes", NbtMap.builder().putInt("minecraft:visual/fog_color", 0x2A0B0B).build())
                        .build())));
        Map<String, String> added = GeyserBiomeBridge.register(packet, MATCHER, identifiers);
        assertEquals(Map.of("terralith:glacial_chasm", "minecraft:snowy_plains",
                "incendium:ash_barrens", "minecraft:nether_wastes"), added);
        assertEquals(12, identifiers.get("terralith:glacial_chasm"));
        assertEquals(8, identifiers.get("incendium:ash_barrens"));
        assertEquals(1, identifiers.get("minecraft:plains"));
        // A second session sees the same keys already mapped.
        assertTrue(GeyserBiomeBridge.register(packet, MATCHER, identifiers).isEmpty());
    }

    @Test
    void customBiomesTakeTheirSlotOrTheClosestSlotLook() throws Exception {
        var blossom = new com.siberanka.twilight.world.BiomeLook(0xF29AC0, 0xE36FA4, 0x5DB7EF, 0x2A5C8A, 0xFFD8EC,
                0x9CC8FF, 0.7f, 0.8f, 1);
        var slots = new com.siberanka.twilight.world.BiomeSlots(List.of(
                new com.siberanka.twilight.world.BiomeSlots.Slot(19, "taiga_hills", blossom, List.of("demo:blossom_vale"))));
        Map<String, Integer> identifiers = new HashMap<>(Map.of("minecraft:plains", 1, "minecraft:snowy_plains", 12,
                "minecraft:nether_wastes", 8));
        var pink = NbtMap.builder().putFloat("temperature", 0.7f).putFloat("downfall", 0.8f)
                .putCompound("effects", NbtMap.builder().putInt("grass_color", 0xF09CC2).putInt("foliage_color", 0xE070A0)
                        .putInt("water_color", 0x5DB7EF).build())
                .putCompound("attributes", NbtMap.builder().putString("minecraft:visual/fog_color", "#ffd8ec").build()).build();
        var packet = new ClientboundRegistryDataPacket(Key.key("minecraft", "worldgen/biome"), List.of(
                new RegistryEntry(Key.key("demo", "blossom_vale"), pink),
                // Added after the pack was built: a near copy of the slot's look.
                new RegistryEntry(Key.key("demo", "blossom_edge"), pink),
                new RegistryEntry(Key.key("terralith", "glacial_chasm"), NbtMap.builder()
                        .putFloat("temperature", -0.7f).putFloat("downfall", 0.5f).build())));
        Map<String, String> added = GeyserBiomeBridge.register(packet, MATCHER, slots, identifiers);
        assertEquals(Map.of("demo:blossom_vale", "taiga_hills", "demo:blossom_edge", "taiga_hills",
                "terralith:glacial_chasm", "minecraft:snowy_plains"), added);
        assertEquals(19, identifiers.get("demo:blossom_vale"));
        assertEquals(19, identifiers.get("demo:blossom_edge"));
    }

    @Test
    void ignoresOtherRegistries() throws Exception {
        Map<String, Integer> identifiers = new HashMap<>(Map.of("minecraft:plains", 1));
        var packet = new ClientboundRegistryDataPacket(Key.key("minecraft", "dimension_type"), List.of(
                new RegistryEntry(Key.key("demo", "custom"), NbtMap.builder().putFloat("temperature", 1f).build())));
        assertTrue(GeyserBiomeBridge.register(packet, MATCHER, identifiers).isEmpty());
        assertEquals(1, identifiers.size());
    }
}
