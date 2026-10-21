/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.geyser;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.geysermc.geyser.api.block.custom.CustomBlockData;
import org.geysermc.geyser.api.block.custom.component.BoxComponent;
import org.geysermc.geyser.api.block.custom.component.CustomBlockComponents;
import org.geysermc.geyser.api.block.custom.component.GeometryComponent;
import org.geysermc.geyser.api.block.custom.component.MaterialInstance;
import org.geysermc.geyser.api.block.custom.component.TransformationComponent;
import org.geysermc.geyser.api.event.EventRegistrar;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomBlocksEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomItemsEvent;
import org.geysermc.geyser.api.item.custom.v2.CustomItemBedrockOptions;
import org.geysermc.geyser.api.item.custom.v2.CustomItemDefinition;
import org.geysermc.geyser.api.item.custom.v2.component.ItemDataComponent;
import org.geysermc.geyser.api.item.custom.v2.component.ItemDataComponentMap;
import org.geysermc.geyser.api.item.custom.v2.component.geyser.GeyserBlockPlacer;
import org.geysermc.geyser.api.item.custom.v2.component.geyser.GeyserItemDataComponents;
import org.geysermc.geyser.api.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.zip.ZipFile;

/**
 * Registers the custom blocks of Twilight packs with Geyser and gives the custom items that place them the
 * block as Bedrock icon and dropped model, as Java draws those items in 3D.
 *
 * <p>Each pack lists its blocks in {@link #PATH}: one Bedrock block per Java block state (a content plugin's
 * note block, mushroom block or tripwire state), with its geometry, textures and Java's collision. Geyser reads
 * blocks and items once, when it starts; packs from several servers (a proxy) are merged, the first pack wins
 * where two describe the same block differently.
 */
public final class CustomBlocks implements AutoCloseable {
    public static final String PATH = "twilight/geyser_blocks.json";
    private static final long MAX_BYTES = 16L * 1024 * 1024;

    /** The blocks and block items of a set of packs. */
    public record Merged(List<JsonObject> blocks, Map<String, String> items, int conflicts, String hash) {
        static final Merged EMPTY = new Merged(List.of(), Map.of(), 0, "");
    }

    private final EventRegistrar registrar;
    private final Supplier<Collection<Path>> packs;
    private final Consumer<String> info;
    private final BiConsumer<String, Throwable> warn;
    private volatile Merged registered;
    /** Bedrock block name -> identifier Geyser gave it. */
    private final Map<String, String> identifiers = new LinkedHashMap<>();

    private CustomBlocks(Object owner, Supplier<Collection<Path>> packs, Consumer<String> info,
                         BiConsumer<String, Throwable> warn) {
        this.registrar = EventRegistrar.of(owner);
        this.packs = packs;
        this.info = info;
        this.warn = warn;
    }

    /**
     * Subscribes to Geyser's custom block and item definition; call before Geyser starts (a backend's plugin load
     * phase, a proxy's attach).
     */
    public static CustomBlocks start(Object owner, Supplier<Collection<Path>> packs, Consumer<String> info,
                                     BiConsumer<String, Throwable> warn) {
        CustomBlocks blocks = new CustomBlocks(owner, packs, info, warn);
        // After Geyser's own integrated pack, which overrides every mushroom block state with its six-face model;
        // the last override of a state wins, so content plugins' mushroom blocks must come later.
        GeyserEvents.subscribeLast(blocks.registrar, GeyserDefineCustomBlocksEvent.class, blocks::defineBlocks);
        GeyserEvents.subscribe(blocks.registrar, GeyserDefineCustomItemsEvent.class, blocks::defineItems);
        return blocks;
    }

    /** What Geyser registered when it started, or null before. */
    public Merged registered() {
        return registered;
    }

    /** The blocks the packs describe now (for telling whether Geyser needs a restart). */
    public Merged current() {
        return read(packs.get());
    }

    private void defineBlocks(GeyserDefineCustomBlocksEvent event) {
        Merged merged = read(packs.get());
        int count = 0, failed = 0;
        synchronized (identifiers) {
            identifiers.clear();
            for (JsonObject block : merged.blocks()) {
                try {
                    CustomBlockData data = data(block);
                    event.register(data);
                    event.registerOverride(block.get("state").getAsString(), data.defaultBlockState());
                    identifiers.put(data.name(), data.identifier());
                    count++;
                } catch (RuntimeException | LinkageError failure) {
                    if (failed++ == 0) warn.accept("Could not register the custom block " + block.get("name") + " with Geyser", failure);
                }
            }
        }
        registered = merged;
        if (count > 0 || failed > 0) {
            info.accept("Registered " + count + " custom block(s) for Bedrock players"
                    + (failed > 0 ? "; " + failed + " could not be registered" : "")
                    + (merged.conflicts() > 0 ? "; " + merged.conflicts() + " block(s) differ between servers, the first server's look is used" : "")
                    + ".");
        }
    }

    private void defineItems(GeyserDefineCustomItemsEvent event) {
        Merged merged = registered;
        if (merged == null || merged.items().isEmpty()) return;
        Map<String, String> blocks;
        synchronized (identifiers) {
            blocks = Map.copyOf(identifiers);
        }
        try {
            int placed = 0;
            for (Collection<CustomItemDefinition> definitions : liveDefinitions(event).values()) {
                if (!(definitions instanceof List<CustomItemDefinition> list)) continue;
                for (int index = 0; index < list.size(); index++) {
                    CustomItemDefinition definition = list.get(index);
                    String block = merged.items().get(definition.bedrockIdentifier().toString());
                    String identifier = block == null ? null : blocks.get(block);
                    if (identifier == null) continue;
                    list.set(index, withBlock(definition, Identifier.of(identifier)));
                    placed++;
                }
            }
            if (placed > 0) info.accept(placed + " custom item(s) show their block in 3D, as on Java.");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            warn.accept("Custom block items keep their flat icon: this Geyser build's item registry could not be read", failure);
        }
    }

    /**
     * The definitions Geyser read from its mapping files, which it registers after this event. Geyser's event
     * only shows them read-only, so the block placer is added to the registered definition in place.
     */
    @SuppressWarnings("unchecked")
    private static Map<?, Collection<CustomItemDefinition>> liveDefinitions(GeyserDefineCustomItemsEvent event)
            throws ReflectiveOperationException {
        for (Class<?> type = event.getClass(); type != null; type = type.getSuperclass()) {
            for (var field : type.getDeclaredFields()) {
                if (!field.getName().equals("customItems")) continue;
                field.setAccessible(true);
                Object multimap = field.get(event);
                // The multimap's class is not public (Guava's ArrayListMultimap); its public method is.
                var asMap = multimap.getClass().getMethod("asMap");
                asMap.setAccessible(true);
                return (Map<?, Collection<CustomItemDefinition>>) asMap.invoke(multimap);
            }
        }
        throw new NoSuchFieldException("customItems");
    }

    /** The same definition with a block placer that shows the block as icon and dropped model. */
    static CustomItemDefinition withBlock(CustomItemDefinition definition, Identifier block) {
        CustomItemDefinition.Builder builder = CustomItemDefinition.builder(definition.bedrockIdentifier(), definition.model());
        if (definition.displayName() != null) builder.displayName(definition.displayName());
        builder.priority(definition.priority());
        if (definition.predicateStrategy() != null) builder.predicateStrategy(definition.predicateStrategy());
        definition.predicates().forEach(builder::predicate);
        CustomItemBedrockOptions options = definition.bedrockOptions();
        CustomItemBedrockOptions.Builder copy = CustomItemBedrockOptions.builder()
                .allowOffhand(options.allowOffhand())
                .displayHandheld(options.displayHandheld())
                .protectionValue(options.protectionValue());
        if (options.icon() != null) copy.icon(options.icon());
        if (options.creativeCategory() != null) copy.creativeCategory(options.creativeCategory());
        if (options.creativeGroup() != null) copy.creativeGroup(options.creativeGroup());
        if (options.tags() != null) copy.tags(options.tags());
        builder.bedrockOptions(copy);
        ItemDataComponentMap components = definition.components();
        for (ItemDataComponent<?> component : components.keySet()) copyComponent(builder, component, components);
        for (Identifier removed : definition.removedComponents()) builder.removeComponent(removed);
        GeyserBlockPlacer placer = GeyserBlockPlacer.of(block, true);
        // Geyser's API keeps its own components to non-vanilla items; Geyser itself derives this one for vanilla
        // block items, and its builder can add it the same way.
        if (builder instanceof org.geysermc.geyser.item.custom.GeyserCustomItemDefinition.Builder geyser) {
            geyser.geyserComponent(GeyserItemDataComponents.BLOCK_PLACER, placer);
        } else {
            builder.component(GeyserItemDataComponents.BLOCK_PLACER, placer);
        }
        return builder.build();
    }

    private static <T> void copyComponent(CustomItemDefinition.Builder builder, ItemDataComponent<T> component,
                                          ItemDataComponentMap components) {
        builder.component(component, components.get(component));
    }

    /**
     * Bedrock rotates a block's transformation the other way round than Java's blockstate {@code x} and
     * {@code y}, which turn clockwise looking along the axis.
     */
    static int bedrockRotation(int java) {
        return Math.floorMod(360 - java, 360);
    }

    static CustomBlockData data(JsonObject block) {
        CustomBlockComponents.Builder components = CustomBlockComponents.builder();
        String geometry = block.get("geometry").getAsString();
        components.geometry(GeometryComponent.builder().identifier(geometry).build());
        boolean opaqueCube = geometry.equals("minecraft:geometry.full_block");
        for (Map.Entry<String, JsonElement> material : block.getAsJsonObject("materials").entrySet()) {
            JsonObject value = material.getValue().getAsJsonObject();
            String renderMethod = value.get("render_method").getAsString();
            opaqueCube &= renderMethod.equals("opaque");
            MaterialInstance.Builder instance = MaterialInstance.builder()
                    .texture(value.get("texture").getAsString())
                    .renderMethod(renderMethod)
                    .faceDimming(!value.has("face_dimming") || value.get("face_dimming").getAsBoolean());
            if (value.has("ambient_occlusion")) {
                instance.ambientOcclusionExponent(value.get("ambient_occlusion").getAsBoolean() ? 1.0f : 0.0f);
            }
            components.materialInstance(material.getKey(), instance.build());
        }
        components.selectionBox(box(block.getAsJsonArray("selection")));
        List<BoxComponent> collision = new ArrayList<>();
        for (JsonElement box : block.getAsJsonArray("collision")) collision.add(box(box.getAsJsonArray()));
        if (collision.isEmpty()) collision.add(BoxComponent.emptyBox());
        components.collisionBoxes(collision);
        // Java decides how long breaking takes; Geyser drives it, as for its own custom blocks.
        components.destructibleByMining(Float.MAX_VALUE);
        if (!opaqueCube) components.lightDampening(0);
        if (block.has("light_emission")) components.lightEmission(Math.max(0, Math.min(15, block.get("light_emission").getAsInt())));
        if (block.has("rotation")) {
            JsonArray rotation = block.getAsJsonArray("rotation");
            components.transformation(new TransformationComponent(bedrockRotation(rotation.get(0).getAsInt()),
                    bedrockRotation(rotation.get(1).getAsInt()), 0));
        }
        return CustomBlockData.builder()
                .name(block.get("name").getAsString())
                .includedInCreativeInventory(false)
                .components(components.build())
                .build();
    }

    private static BoxComponent box(JsonArray value) {
        return new BoxComponent(value.get(0).getAsFloat(), value.get(1).getAsFloat(), value.get(2).getAsFloat(),
                value.get(3).getAsFloat(), value.get(4).getAsFloat(), value.get(5).getAsFloat());
    }

    /** Merges the block lists of packs; packs without one, or with an unreadable one, are skipped. */
    public static Merged read(Collection<Path> packs) {
        Map<String, JsonObject> blocks = new LinkedHashMap<>();
        Map<String, String> items = new LinkedHashMap<>();
        int conflicts = 0;
        for (Path pack : packs) {
            JsonObject root = readPack(pack);
            if (root == null) continue;
            if (root.get("blocks") instanceof JsonArray list) {
                for (JsonElement element : list) {
                    if (!(element instanceof JsonObject block) || !valid(block)) continue;
                    JsonObject previous = blocks.putIfAbsent(block.get("name").getAsString(), block);
                    if (previous != null && !sameBlock(previous, block)) conflicts++;
                }
            }
            if (root.get("items") instanceof JsonObject links) {
                for (Map.Entry<String, JsonElement> link : links.entrySet()) {
                    if (link.getValue().isJsonPrimitive()) items.putIfAbsent(link.getKey(), link.getValue().getAsString());
                }
            }
        }
        if (blocks.isEmpty()) return Merged.EMPTY;
        StringBuilder canonical = new StringBuilder();
        blocks.values().forEach(block -> canonical.append(block).append('\n'));
        items.forEach((item, block) -> canonical.append(item).append('=').append(block).append('\n'));
        return new Merged(List.copyOf(blocks.values()), Map.copyOf(items), conflicts, sha256(canonical.toString()));
    }

    /** Two packs' descriptions of a block differ in more than their (per-server) textures. */
    private static boolean sameBlock(JsonObject first, JsonObject second) {
        return first.get("geometry").equals(second.get("geometry")) && first.get("state").equals(second.get("state"))
                && first.getAsJsonObject("materials").keySet().equals(second.getAsJsonObject("materials").keySet())
                && java.util.Objects.equals(first.get("rotation"), second.get("rotation"));
    }

    static boolean valid(JsonObject block) {
        try {
            String name = block.get("name").getAsString();
            String state = block.get("state").getAsString();
            return name.matches("[a-z][a-z0-9_]{0,63}") && state.matches("minecraft:[a-z_]+\\[[a-z0-9_=,]+]")
                    && block.get("geometry").getAsString().matches("[a-z0-9_.:]{1,96}")
                    && block.get("materials").isJsonObject() && !block.getAsJsonObject("materials").isEmpty()
                    && block.getAsJsonArray("selection").size() == 6 && block.get("collision").isJsonArray();
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    private static JsonObject readPack(Path pack) {
        if (pack == null || !Files.isRegularFile(pack)) return null;
        try (ZipFile zip = new ZipFile(pack.toFile())) {
            var entry = zip.getEntry(PATH);
            if (entry == null || entry.getSize() > MAX_BYTES) return null;
            try (InputStream input = zip.getInputStream(entry)) {
                byte[] bytes = input.readNBytes((int) MAX_BYTES + 1);
                if (bytes.length > MAX_BYTES) return null;
                JsonObject root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
                return root.has("format") && root.get("format").getAsInt() == 1 ? root : null;
            }
        } catch (IOException | RuntimeException unreadable) {
            return null;
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    @Override
    public void close() {
        GeyserEvents.unregisterAll(registrar);
    }
}
