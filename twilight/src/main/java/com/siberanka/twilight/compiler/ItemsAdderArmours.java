/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.compiler;

import com.siberanka.twilight.source.ResourceIndex;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.regex.Pattern;

/**
 * ItemsAdder's custom armour. ItemsAdder draws it on Java as dyed leather: its pack replaces the leather layers with
 * a sheet of every armour and a core shader picks the armour by the leather colour, the colour of an
 * {@code armors_rendering} entry. Bedrock has no such shader, so the colour of a worn item is looked up here and
 * the entry's own layer textures are drawn instead.
 */
final class ItemsAdderArmours {
    private static final String PREFIX = "provider-data/itemsadder/";
    private static final Pattern PATH = Pattern.compile("[a-z0-9_.-]+(:[a-z0-9_./-]+)?|[a-z0-9_./-]+");
    /** Configuration files larger than this are not ItemsAdder armour definitions anyone writes by hand. */
    private static final int MAX_FILE = 1 << 20;
    private static final Map<ResourceIndex, Map<Integer, Rendering>> CACHE = new WeakHashMap<>();

    /**
     * @param layer1 the texture worn on the head, body, arms and feet ({@code layer_1})
     * @param layer2 the texture worn on the legs ({@code layer_2})
     * @param tint   the colour multiplied into the layers ({@code use_color}), or -1
     */
    record Rendering(String layer1, String layer2, int tint) {
        String layer(String equipmentLayer) {
            return equipmentLayer.equals("humanoid_leggings") ? layer2 : layer1;
        }
    }

    private ItemsAdderArmours() {}

    /** The armour ItemsAdder draws for a leather colour, if any of its contents defines one. */
    static Optional<Rendering> forColour(ResourceIndex resources, int rgb) {
        Map<Integer, Rendering> renderings;
        synchronized (CACHE) {
            renderings = CACHE.computeIfAbsent(resources, ItemsAdderArmours::read);
        }
        return Optional.ofNullable(renderings.get(rgb & 0xFFFFFF));
    }

    private static Map<Integer, Rendering> read(ResourceIndex resources) {
        Map<Integer, Rendering> result = new HashMap<>();
        for (String path : resources.pathsStartingWith(PREFIX)) {
            if (!(path.endsWith(".yml") || path.endsWith(".yaml"))) continue;
            try {
                ResourceIndex.Asset asset = resources.find(path).orElse(null);
                if (asset == null || asset.size() > MAX_FILE) continue;
                String text = new String(asset.readBytes(), StandardCharsets.UTF_8);
                if (!text.contains("armors_rendering")) continue;
                if (!(yaml().load(text) instanceof Map<?, ?> root)) continue;
                String namespace = root.get("info") instanceof Map<?, ?> info && info.get("namespace") instanceof String value
                        ? value : null;
                if (!(root.get("armors_rendering") instanceof Map<?, ?> renderings)) continue;
                for (Object value : renderings.values()) {
                    if (!(value instanceof Map<?, ?> entry)) continue;
                    Integer colour = colour(entry.get("color"));
                    String layer1 = texture(entry.get("layer_1"), namespace), layer2 = texture(entry.get("layer_2"), namespace);
                    if (colour == null || layer1 == null || layer2 == null) continue;
                    int tint = Boolean.TRUE.equals(entry.get("use_color")) ? 0xFF000000 | colour : -1;
                    result.putIfAbsent(colour, new Rendering(layer1, layer2, tint));
                }
            } catch (IOException | RuntimeException malformed) {
                // ItemsAdder refuses a malformed file too; its armour keeps Bedrock's default look.
            }
        }
        return result;
    }

    private static Yaml yaml() {
        LoaderOptions options = new LoaderOptions();
        options.setMaxAliasesForCollections(20);
        options.setNestingDepthLimit(40);
        options.setCodePointLimit(MAX_FILE);
        options.setAllowDuplicateKeys(true);
        // Plain maps, lists and scalars only: no tags, no object construction.
        return new Yaml(new SafeConstructor(options));
    }

    static Integer colour(Object value) {
        if (value instanceof Number number) return number.intValue() & 0xFFFFFF;
        if (!(value instanceof String text)) return null;
        String hex = text.trim();
        if (hex.startsWith("#")) hex = hex.substring(1);
        if (!hex.matches("[0-9a-fA-F]{6}")) return null;
        return Integer.parseInt(hex, 16);
    }

    private static String texture(Object value, String namespace) {
        if (!(value instanceof String text)) return null;
        String path = text.trim().toLowerCase(java.util.Locale.ROOT);
        if (path.endsWith(".png")) path = path.substring(0, path.length() - 4);
        if (path.isEmpty() || path.contains("..") || !PATH.matcher(path).matches()) return null;
        if (path.contains(":")) return path;
        return namespace == null || !namespace.matches("[a-z0-9_.-]+") ? null : namespace + ':' + path;
    }
}
