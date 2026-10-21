/* Copyright (c) 2026 siberanka. Twilight License (PolyForm Noncommercial 1.0.0 + terms), see LICENSE. */
package com.siberanka.twilight.integration.world;

import com.google.gson.JsonParser;
import com.siberanka.twilight.world.BiomeLook;
import com.siberanka.twilight.world.BiomeMatcher;
import com.siberanka.twilight.world.BiomeSlots;
import org.cloudburstmc.protocol.bedrock.data.biome.BiomeDefinitionData;
import org.cloudburstmc.protocol.bedrock.data.biome.BiomeDefinitions;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.event.EventRegistrar;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostInitializeEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostReloadEvent;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundRegistryDataPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundChunksBiomesPacket;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipFile;

/**
 * Shows custom Java biomes on Bedrock with their own look instead of Geyser's fallback.
 *
 * <p>The served pack redefines free Bedrock biomes with the exact colours of the server's
 * custom biomes ({@link BiomeSlots}); the bridge maps those biomes to their slot and gives the
 * slot the custom biome's climate (temperature, downfall, rain or snow). Any other biome Geyser
 * cannot map (one added after the pack was built, or beyond the free slots) gets the vanilla
 * biome or slot whose grass, foliage, water, fog and precipitation are closest (see
 * {@link BiomeMatcher}). The registry entries carry the biome's own data, so runtime biomes such
 * as RealisticSeasons' seasonal ones are matched with their current colours. Mappings are
 * global, never replace an existing Geyser mapping and never change the packets other plugins send.
 */
public final class GeyserBiomeBridge implements AutoCloseable {
    private static final String BIOME_REGISTRY = "minecraft:worldgen/biome";

    private final Path servedPack;
    private final Logger logger;
    private final EventRegistrar registrar;
    private final Wrapper wrapper = new Wrapper();
    private final BiomeUpdates updates = new BiomeUpdates();
    private final java.util.function.BiConsumer<java.util.UUID, List<long[]>> resend;
    private volatile PacketTranslator<? extends Packet> originalUpdates;
    private final AtomicBoolean failureLogged = new AtomicBoolean();
    private volatile BiomeMatcher matcher;
    private volatile BiomeSlots slots = new BiomeSlots(List.of());
    private volatile PacketTranslator<? extends Packet> original;

    private GeyserBiomeBridge(Object owner, Path servedPack, Logger logger,
                              java.util.function.BiConsumer<java.util.UUID, List<long[]>> resend) {
        this.resend = resend;
        this.servedPack = servedPack;
        this.logger = logger;
        this.registrar = EventRegistrar.of(owner);
    }

    /**
     * @param resend sends the given chunks (x, z) to the Java player again; Geyser does not translate
     *               biome-only chunk updates, so Bedrock players receive such chunks whole
     */
    public static GeyserBiomeBridge create(Object owner, Path servedPack, Logger logger,
                                           java.util.function.BiConsumer<java.util.UUID, List<long[]>> resend) {
        GeyserBiomeBridge bridge = new GeyserBiomeBridge(owner, servedPack, logger, resend);
        com.siberanka.twilight.geyser.GeyserEvents.subscribe(bridge.registrar, GeyserPostInitializeEvent.class, event -> bridge.attach());
        com.siberanka.twilight.geyser.GeyserEvents.subscribe(bridge.registrar, GeyserPostReloadEvent.class, event -> bridge.attach());
        if (Registries.JAVA_PACKET_TRANSLATORS.get().get(ClientboundRegistryDataPacket.class) != null) bridge.attach();
        return bridge;
    }

    private synchronized void attach() {
        try {
            matcher = readTable(servedPack);
        } catch (IOException | RuntimeException failure) {
            matcher = null;
            logger.log(Level.WARNING, "Could not read the vanilla biome table of the served Twilight pack", failure);
        }
        try {
            BiomeSlots read = readSlots(servedPack);
            slots = read == null ? new BiomeSlots(List.of()) : usable(read);
            if (!slots.isEmpty()) applyClimate(slots);
        } catch (IOException | RuntimeException | LinkageError failure) {
            slots = new BiomeSlots(List.of());
            logger.log(Level.WARNING, "Could not apply the custom biome slots of the served Twilight pack; "
                    + "custom biomes use their closest vanilla biome", failure);
        }
        var translators = Registries.JAVA_PACKET_TRANSLATORS.get();
        PacketTranslator<? extends Packet> existing = translators.get(ClientboundChunksBiomesPacket.class);
        if (!com.siberanka.twilight.geyser.TranslatorChain.installed(existing, updates)) {
            originalUpdates = existing;
            translators.put(ClientboundChunksBiomesPacket.class, updates);
        }
        PacketTranslator<? extends Packet> current = translators.get(ClientboundRegistryDataPacket.class);
        if (current == null) {
            logger.warning("Geyser has no registry-data translator; custom biomes keep Geyser's fallback.");
            return;
        }
        if (!com.siberanka.twilight.geyser.TranslatorChain.installed(current, wrapper)) {
            original = current;
            translators.put(ClientboundRegistryDataPacket.class, wrapper);
        }
        logger.info(matcher == null ? "Biome matching idle: the served pack has no vanilla biome table."
                : "Custom biomes are shown on Bedrock with " + slots.slots().size() + " exact biome look(s) and the closest of "
                + matcher.vanilla().size() + " vanilla appearances otherwise.");
    }

    static BiomeMatcher readTable(Path pack) throws IOException {
        if (!Files.isRegularFile(pack)) return null;
        try (ZipFile zip = new ZipFile(pack.toFile())) {
            var entry = zip.getEntry(BiomeMatcher.PATH);
            if (entry == null) return null;
            if (entry.getSize() > 4L * 1024 * 1024) throw new IOException("Biome table exceeds size limit");
            try (Reader reader = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
                return BiomeMatcher.fromJson(JsonParser.parseReader(reader).getAsJsonObject());
            }
        }
    }

    static BiomeSlots readSlots(Path pack) throws IOException {
        if (!Files.isRegularFile(pack)) return null;
        try (ZipFile zip = new ZipFile(pack.toFile())) {
            var entry = zip.getEntry(BiomeSlots.PATH);
            if (entry == null) return null;
            if (entry.getSize() > 4L * 1024 * 1024) throw new IOException("Biome slot table exceeds size limit");
            try (Reader reader = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
                return BiomeSlots.fromJson(JsonParser.parseReader(reader).getAsJsonObject());
            }
        }
    }

    /** Drops slots whose Bedrock biome this Geyser build maps a vanilla Java biome to. */
    private BiomeSlots usable(BiomeSlots read) throws IOException {
        Map<String, Integer> identifiers = identifiers();
        java.util.Set<Integer> taken = new java.util.HashSet<>();
        synchronized (identifiers) {
            identifiers.forEach((key, id) -> { if (key.startsWith("minecraft:")) taken.add(id); });
        }
        List<BiomeSlots.Slot> kept = new ArrayList<>();
        for (BiomeSlots.Slot slot : read.slots()) {
            if (taken.contains(slot.id())) logger.warning("Bedrock biome " + slot.name() + " is in use by Geyser; "
                    + slot.biomes() + " use the closest vanilla biome instead.");
            else kept.add(slot);
        }
        return new BiomeSlots(kept);
    }

    /** Gives every slot its custom biome's climate, which Bedrock uses for rain and snow. */
    static void applyClimate(BiomeSlots slots) {
        BiomeDefinitions current = Registries.BIOMES.get();
        Map<String, BiomeDefinitionData> definitions = new java.util.LinkedHashMap<>(current.getDefinitions());
        for (BiomeSlots.Slot slot : slots.slots()) {
            String key = definitions.containsKey(slot.name()) ? slot.name() : "minecraft:" + slot.name();
            BiomeDefinitionData old = definitions.get(key);
            if (old == null) continue;
            BiomeLook look = slot.look();
            // Neutral plains tags and no snowy foliage: the slot's own tags (taiga, mesa, frozen) make Bedrock
            // tint leaves itself and ignore the pack's foliage colour (measured 4 October 2026). Rain or snow
            // follows the temperature, as on Java.
            BiomeDefinitionData tags = definitions.getOrDefault("plains", definitions.get("minecraft:plains"));
            definitions.put(key, new BiomeDefinitionData(old.getId(), look.temperature(), look.downfall(),
                    0f, 0f, 0f, 0f, 0f, old.getDepth(), old.getScale(), new java.awt.Color(look.water()),
                    look.precipitation() != 0, tags == null ? old.getTags() : tags.getTags(), old.getChunkGenData()));
        }
        Registries.BIOMES.set(new BiomeDefinitions(definitions));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Integer> identifiers() throws IOException {
        try {
            // Geyser's map is a (relocated) fastutil Object2IntMap, which is also a java.util.Map.
            // Reflection avoids a compile-time cast to the unrelocated fastutil type.
            Object registry = Registries.BIOME_IDENTIFIERS;
            return (Map<String, Integer>) registry.getClass().getMethod("get").invoke(registry);
        } catch (ReflectiveOperationException failure) {
            throw new IOException("Geyser's biome identifiers are unavailable", failure);
        }
    }

    @Override public synchronized void close() {
        GeyserApi.api().eventBus().unregisterAll(registrar);
        if (original != null) Registries.JAVA_PACKET_TRANSLATORS.get().replace(ClientboundRegistryDataPacket.class, wrapper, original);
        var translators = Registries.JAVA_PACKET_TRANSLATORS.get();
        if (originalUpdates != null) translators.replace(ClientboundChunksBiomesPacket.class, updates, originalUpdates);
        else translators.remove(ClientboundChunksBiomesPacket.class, updates);
    }

    /** Registers mappings for the unmapped biomes of a registry packet; returns custom key -> vanilla key or slot. */
    static Map<String, String> register(Object packet, BiomeMatcher matcher, Map<String, Integer> identifiers)
            throws ReflectiveOperationException {
        return register(packet, matcher, new BiomeSlots(List.of()), identifiers);
    }

    @SuppressWarnings("unchecked")
    static Map<String, String> register(Object packet, BiomeMatcher matcher, BiomeSlots slots, Map<String, Integer> identifiers)
            throws ReflectiveOperationException {
        Map<String, String> added = new java.util.LinkedHashMap<>();
        if (!BIOME_REGISTRY.equals(keyString(packet.getClass().getMethod("getRegistry").invoke(packet)))) return added;
        List<String> candidates = new ArrayList<>();
        for (String key : matcher.vanilla().keySet()) if (identifiers.containsKey(key)) candidates.add(key);
        java.util.Set<String> usable = java.util.Set.copyOf(candidates);
        for (Object entry : (List<?>) packet.getClass().getMethod("getEntries").invoke(packet)) {
            String key = keyString(entry.getClass().getMethod("getId").invoke(entry));
            Object data = entry.getClass().getMethod("getData").invoke(entry);
            if (key == null || identifiers.containsKey(key)) continue;
            BiomeSlots.Slot exact = null;
            for (BiomeSlots.Slot slot : slots.slots()) if (slot.biomes().contains(key)) exact = slot;
            if (exact != null) {
                identifiers.put(key, exact.id());
                added.put(key, exact.name());
                continue;
            }
            if (!(data instanceof Map<?, ?> nbt)) continue;
            BiomeMatcher.Appearance appearance = matcher.appearance((Map<String, ?>) nbt);
            String closest = matcher.closest(appearance, usable);
            double score = closest == null ? Double.MAX_VALUE : BiomeMatcher.distance(appearance, matcher.vanilla().get(closest));
            BiomeSlots.Slot nearest = null;
            for (BiomeSlots.Slot slot : slots.slots()) {
                double distance = BiomeMatcher.distance(appearance, BiomeMatcher.appearance(slot.look()));
                if (distance < score) {
                    score = distance;
                    nearest = slot;
                }
            }
            if (nearest != null) {
                identifiers.put(key, nearest.id());
                added.put(key, nearest.name());
            } else if (closest != null) {
                identifiers.put(key, identifiers.get(closest));
                added.put(key, closest);
            }
        }
        return added;
    }

    private static String keyString(Object key) throws ReflectiveOperationException {
        if (key == null) return null;
        Method asString = key.getClass().getMethod("asString");
        asString.setAccessible(true);
        return (String) asString.invoke(key);
    }

    /** Biome-only chunk updates (fillbiome, seasons plugins): the player gets the whole chunks again. */
    private final class BiomeUpdates extends PacketTranslator<ClientboundChunksBiomesPacket> implements com.siberanka.twilight.geyser.TranslatorChain.Link {
        @Override public Object next() {
            return originalUpdates;
        }

        @SuppressWarnings("unchecked")
        @Override public void translate(GeyserSession session, ClientboundChunksBiomesPacket packet) {
            if (originalUpdates != null) ((PacketTranslator<ClientboundChunksBiomesPacket>) originalUpdates).translate(session, packet);
            try {
                List<long[]> chunks = new ArrayList<>();
                for (var data : packet.getChunkBiomeData()) chunks.add(new long[]{data.getX(), data.getZ()});
                java.util.UUID player = session.javaUuid();
                if (player != null && !chunks.isEmpty()) resend.accept(player, chunks);
            } catch (RuntimeException | LinkageError failure) {
                if (failureLogged.compareAndSet(false, true)) {
                    logger.log(Level.WARNING, "Biome updates could not be forwarded to a Bedrock player.", failure);
                }
            }
        }

        @Override public boolean shouldExecuteInEventLoop() {
            return originalUpdates == null || originalUpdates.shouldExecuteInEventLoop();
        }
    }

    private final class Wrapper extends PacketTranslator<ClientboundRegistryDataPacket> implements com.siberanka.twilight.geyser.TranslatorChain.Link {
        @Override public Object next() {
            return original;
        }

        @SuppressWarnings("unchecked")
        @Override public void translate(GeyserSession session, ClientboundRegistryDataPacket packet) {
            BiomeMatcher current = matcher;
            if (current != null) {
                try {
                    Map<String, Integer> identifiers = identifiers();
                    Map<String, String> added;
                    synchronized (identifiers) {
                        added = register(packet, current, slots, identifiers);
                    }
                    if (!added.isEmpty()) {
                        logger.info("Bedrock biome mapping: " + added.size() + " custom biome(s), e.g. "
                                + added.entrySet().iterator().next());
                    }
                } catch (IOException | ReflectiveOperationException | RuntimeException | LinkageError failure) {
                    if (failureLogged.compareAndSet(false, true)) {
                        logger.log(Level.WARNING, "Custom biome matching failed; such biomes keep Geyser's fallback.", failure);
                    }
                }
            }
            ((PacketTranslator<ClientboundRegistryDataPacket>) original).translate(session, packet);
        }

        @Override public boolean shouldExecuteInEventLoop() {
            return original.shouldExecuteInEventLoop();
        }
    }
}
