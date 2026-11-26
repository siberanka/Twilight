package com.siberanka.twilight.integration.world;

import com.siberanka.twilight.world.BiomeMatcher;
import net.kyori.adventure.key.Key;
import org.cloudburstmc.nbt.NbtMap;
import org.geysermc.mcprotocollib.protocol.data.game.RegistryEntry;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundRegistryDataPacket;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

    // --- Geyser on a proxy: every session maps the custom biomes with the tables of its own pack ---------------

    @TempDir java.nio.file.Path temp;

    private java.nio.file.Path pack(String name, BiomeMatcher matcher, com.siberanka.twilight.world.BiomeSlots slots) throws Exception {
        java.nio.file.Path file = temp.resolve(name);
        try (var zip = new java.util.zip.ZipOutputStream(java.nio.file.Files.newOutputStream(file))) {
            if (matcher != null) {
                zip.putNextEntry(new java.util.zip.ZipEntry(BiomeMatcher.PATH));
                zip.write(matcher.toJson().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            if (slots != null) {
                zip.putNextEntry(new java.util.zip.ZipEntry(com.siberanka.twilight.world.BiomeSlots.PATH));
                zip.write(slots.toJson().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return file;
    }

    private static com.siberanka.twilight.world.BiomeSlots slotFor(int id, String name, String biome) {
        var look = new com.siberanka.twilight.world.BiomeLook(0xF29AC0, 0xE36FA4, 0x5DB7EF, 0x2A5C8A, 0xFFD8EC, 0x9CC8FF, 0.7f, 0.8f, 1);
        return new com.siberanka.twilight.world.BiomeSlots(List.of(
                new com.siberanka.twilight.world.BiomeSlots.Slot(id, name, look, List.of(biome))));
    }

    @Test
    void aPackWithBothTablesGivesTheSessionItsTables() throws Exception {
        var file = pack("a.zip", MATCHER, slotFor(19, "taiga_hills", "demo:blossom_vale"));
        var tables = GeyserBiomeBridge.readTables(file, slots -> slots);
        assertNotNull(tables);
        assertEquals(MATCHER.vanilla().keySet(), tables.matcher().vanilla().keySet());
        assertEquals(List.of("demo:blossom_vale"), tables.slots().slots().getFirst().biomes());
        assertEquals(file, tables.pack());
    }

    @Test
    void packsWithoutBiomeDataLeaveTheSessionAlone() throws Exception {
        // An older backend (no slot table), matching switched off (vanilla table only), no table at all, no file.
        assertNull(GeyserBiomeBridge.readTables(pack("old.zip", MATCHER, null), slots -> slots));
        assertNull(GeyserBiomeBridge.readTables(pack("slots-only.zip", null, slotFor(19, "taiga_hills", "demo:x")), slots -> slots));
        assertNull(GeyserBiomeBridge.readTables(pack("empty.zip", null, null), slots -> slots));
        assertNull(GeyserBiomeBridge.readTables(temp.resolve("missing.zip"), slots -> slots));
    }

    @Test
    void aMatchingBackendWithoutSlotsStillMatchesTheClosestVanillaBiome() throws Exception {
        var tables = GeyserBiomeBridge.readTables(pack("none.zip", MATCHER, new com.siberanka.twilight.world.BiomeSlots(List.of())), slots -> slots);
        assertNotNull(tables, "an empty slot table is the backend's 'matching is on' mark");
        assertTrue(tables.slots().isEmpty());
    }

    @Test
    void severalServersKeepTheirOwnMappingsForTheSameBiomeKey() throws Exception {
        Map<String, Integer> identifiers = new HashMap<>(Map.of("minecraft:plains", 1, "minecraft:snowy_plains", 12,
                "minecraft:nether_wastes", 8, "minecraft:ocean", 0));
        var packet = new ClientboundRegistryDataPacket(Key.key("minecraft", "worldgen/biome"), List.of(
                new RegistryEntry(Key.key("demo", "blossom_vale"), NbtMap.builder().putFloat("temperature", 0.7f).build())));
        var one = GeyserBiomeBridge.readTables(pack("one.zip", MATCHER, slotFor(19, "taiga_hills", "demo:blossom_vale")), slots -> slots);
        var two = GeyserBiomeBridge.readTables(pack("two.zip", MATCHER, slotFor(17, "desert_hills", "demo:blossom_vale")), slots -> slots);

        Map<String, String> first = GeyserBiomeBridge.register(packet, one.matcher(), one.slots(), identifiers);
        assertEquals(19, identifiers.get("demo:blossom_vale"));
        GeyserBiomeBridge.forget(identifiers, first.keySet());
        assertFalse(identifiers.containsKey("demo:blossom_vale"), "the session's mapping does not stay behind");

        Map<String, String> second = GeyserBiomeBridge.register(packet, two.matcher(), two.slots(), identifiers);
        assertEquals(Map.of("demo:blossom_vale", "desert_hills"), second);
        assertEquals(17, identifiers.get("demo:blossom_vale"));
        GeyserBiomeBridge.forget(identifiers, second.keySet());
        assertEquals(4, identifiers.size(), "Geyser's own mappings are never touched");
    }

    @Test
    void anUnreadablePackIsAnError() throws Exception {
        java.nio.file.Path broken = temp.resolve("broken.zip");
        java.nio.file.Files.writeString(broken, "not a zip");
        assertThrows(java.io.IOException.class, () -> GeyserBiomeBridge.readTables(broken, slots -> slots));
    }

    @Test
    void concurrentSessionsNeverSeeEachOthersMappingsOfTheSameKey() throws Exception {
        Map<String, Integer> identifiers = new HashMap<>(Map.of("minecraft:plains", 1));
        Object lock = new Object();
        var insideA = new java.util.concurrent.CountDownLatch(1);
        var releaseA = new java.util.concurrent.CountDownLatch(1);
        var seenA = new java.util.concurrent.atomic.AtomicInteger(-1);
        var seenB = new java.util.concurrent.atomic.AtomicInteger(-1);
        var done = new java.util.concurrent.CountDownLatch(2);
        Thread a = new Thread(() -> {
            GeyserBiomeBridge.applied(lock, identifiers, Map.of("demo:blossom_vale", 19), () -> {
                insideA.countDown();
                try { releaseA.await(); } catch (InterruptedException ignored) { }
                seenA.set(identifiers.get("demo:blossom_vale"));
            });
            done.countDown();
        });
        Thread b = new Thread(() -> {
            GeyserBiomeBridge.applied(lock, identifiers, Map.of("demo:blossom_vale", 17),
                    () -> seenB.set(identifiers.get("demo:blossom_vale")));
            done.countDown();
        });
        a.start();
        assertTrue(insideA.await(5, java.util.concurrent.TimeUnit.SECONDS));
        b.start();
        Thread.sleep(200); // B waits for A's lock instead of overwriting or removing A's mapping
        assertEquals(-1, seenB.get());
        releaseA.countDown();
        assertTrue(done.await(5, java.util.concurrent.TimeUnit.SECONDS));
        assertEquals(19, seenA.get());
        assertEquals(17, seenB.get());
        assertEquals(Map.of("minecraft:plains", 1), identifiers);
    }

    @Test
    void mappingsAreRemovedEvenWhenGeysersTranslatorFails() {
        Map<String, Integer> identifiers = new HashMap<>(Map.of("minecraft:plains", 1));
        assertThrows(IllegalStateException.class, () -> GeyserBiomeBridge.applied(new Object(), identifiers,
                Map.of("demo:x", 19, "minecraft:plains", 5), () -> { throw new IllegalStateException("broken"); }));
        assertEquals(Map.of("minecraft:plains", 1), identifiers, "Geyser's own mappings are neither replaced nor removed");
    }

    @Test
    void aSessionWithAnotherServersPackOnlyUsesASlotOfTheSameKeyWhenTheLooksAgree() throws Exception {
        var blossom = new com.siberanka.twilight.world.BiomeLook(0xF29AC0, 0xE36FA4, 0x5DB7EF, 0x2A5C8A, 0xFFD8EC,
                0x9CC8FF, 0.7f, 0.8f, 1);
        var slots = new com.siberanka.twilight.world.BiomeSlots(List.of(
                new com.siberanka.twilight.world.BiomeSlots.Slot(19, "taiga_hills", blossom, List.of("demo:blossom_vale"))));
        var pink = NbtMap.builder().putFloat("temperature", 0.7f).putFloat("downfall", 0.8f)
                .putCompound("effects", NbtMap.builder().putInt("grass_color", 0xF29AC0).putInt("foliage_color", 0xE36FA4)
                        .putInt("water_color", 0x5DB7EF).build())
                .putCompound("attributes", NbtMap.builder().putString("minecraft:visual/fog_color", "#ffd8ec").build()).build();
        // The same key on the other server, with a very different (blue, snowy) look.
        var blue = NbtMap.builder().putFloat("temperature", -0.5f).putFloat("downfall", 0.5f).putByte("has_precipitation", (byte) 1)
                .putCompound("effects", NbtMap.builder().putInt("grass_color", 0x0050FF).putInt("foliage_color", 0x00FFF0)
                        .putInt("water_color", 0xFFFFFF).build())
                .putCompound("attributes", NbtMap.builder().putString("minecraft:visual/fog_color", "#0030ff").build()).build();
        Map<String, Integer> base = Map.of("minecraft:plains", 1, "minecraft:snowy_plains", 12, "minecraft:nether_wastes", 8);

        Map<String, Integer> same = new HashMap<>(base);
        var samePacket = new ClientboundRegistryDataPacket(Key.key("minecraft", "worldgen/biome"),
                List.of(new RegistryEntry(Key.key("demo", "blossom_vale"), pink)));
        assertEquals(Map.of("demo:blossom_vale", "taiga_hills"), GeyserBiomeBridge.register(samePacket, MATCHER, slots, same, true));
        assertEquals(19, same.get("demo:blossom_vale"));

        var otherPacket = new ClientboundRegistryDataPacket(Key.key("minecraft", "worldgen/biome"),
                List.of(new RegistryEntry(Key.key("demo", "blossom_vale"), blue)));
        Map<String, Integer> other = new HashMap<>(base);
        Map<String, String> added = GeyserBiomeBridge.register(otherPacket, MATCHER, slots, other, true);
        assertNotEquals("taiga_hills", added.get("demo:blossom_vale"), "another server's slot look is not shown for this look");
        assertNotEquals(19, other.get("demo:blossom_vale"));
        // The backend's own Geyser trusts the slot of its own pack, as before.
        Map<String, Integer> backend = new HashMap<>(base);
        assertEquals(Map.of("demo:blossom_vale", "taiga_hills"), GeyserBiomeBridge.register(otherPacket, MATCHER, slots, backend));
    }
}
