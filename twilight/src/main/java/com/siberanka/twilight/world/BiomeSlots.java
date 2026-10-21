/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.world;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Shows custom Java biomes with their exact colours on Bedrock.
 *
 * <p>A Bedrock client refuses biome definitions it does not know, but it takes the look of a
 * vanilla biome from the resource pack. Several Bedrock biomes exist only for old worlds: no
 * Java biome maps to them, so the pack can give each one the grass, foliage, water, fog and
 * sky of a custom biome, and Geyser sends that biome's id. When a server has more custom looks
 * than such slots, the slots go to the looks of the current season first, then to the looks the
 * vanilla biomes approximate worst; every other custom biome takes the closest vanilla biome or slot.
 */
public final class BiomeSlots {
    /** Pack path of the slot table read by the biome bridge; Bedrock ignores the file. */
    public static final String PATH = "twilight/biome-slots.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    /** A look this close to a vanilla biome or a chosen slot (a few RGB steps) needs no slot of its own. */
    private static final double CLOSE_ENOUGH = 8;

    /**
     * Bedrock biomes no Java biome maps to (legacy hills, edges and mutated variants), land
     * biomes first. Ids are Bedrock's; names are the client biome identifiers.
     */
    static final Map<Integer, String> FREE = free();

    /** A redefined Bedrock biome: its id and name, the look it shows and the Java biomes with exactly that look. */
    public record Slot(int id, String name, BiomeLook look, List<String> biomes) {}

    private final List<Slot> slots;

    public BiomeSlots(List<Slot> slots) {
        this.slots = List.copyOf(slots);
    }

    public List<Slot> slots() { return slots; }

    public boolean isEmpty() { return slots.isEmpty(); }

    /**
     * Gives the custom looks Bedrock slots: all of them when they fit. Otherwise the slots go to the looks
     * the vanilla biomes (and the slots chosen so far) approximate worst, so the most visible mismatches are
     * removed first; looks of {@code current} biomes (the season a seasons plugin shows now) go first.
     */
    public static BiomeSlots assign(Map<String, BiomeLook> custom, Collection<BiomeMatcher.Appearance> vanilla) {
        return assign(custom, vanilla, java.util.Set.of());
    }

    public static BiomeSlots assign(Map<String, BiomeLook> custom, Collection<BiomeMatcher.Appearance> vanilla,
                                    java.util.Set<String> current) {
        Map<BiomeLook, List<String>> distinct = new LinkedHashMap<>();
        new TreeMap<>(custom).forEach((key, look) -> distinct.computeIfAbsent(look, ignored -> new ArrayList<>()).add(key));
        List<BiomeLook> looks = new ArrayList<>(distinct.keySet());
        List<BiomeLook> chosen;
        if (looks.size() <= FREE.size()) {
            chosen = looks;
        } else {
            double[] error = new double[looks.size()];
            boolean[] preferred = new boolean[looks.size()];
            for (int index = 0; index < looks.size(); index++) {
                BiomeMatcher.Appearance appearance = BiomeMatcher.appearance(looks.get(index));
                error[index] = vanilla.stream().mapToDouble(other -> BiomeMatcher.distance(appearance, other)).min().orElse(1_000);
                preferred[index] = distinct.get(looks.get(index)).stream().anyMatch(current::contains);
            }
            chosen = new ArrayList<>();
            while (chosen.size() < FREE.size()) {
                int worst = -1;
                for (int index = 0; index < looks.size(); index++) {
                    if (error[index] <= CLOSE_ENOUGH) continue;
                    if (worst < 0 || preferred[index] && !preferred[worst]
                            || preferred[index] == preferred[worst] && error[index] > error[worst]) worst = index;
                }
                if (worst < 0) break;
                chosen.add(looks.get(worst));
                BiomeMatcher.Appearance appearance = BiomeMatcher.appearance(looks.get(worst));
                for (int index = 0; index < looks.size(); index++) {
                    error[index] = Math.min(error[index], BiomeMatcher.distance(BiomeMatcher.appearance(looks.get(index)), appearance));
                }
            }
            chosen.sort(Comparator.comparing(look -> distinct.get(look).getFirst()));
        }
        List<Slot> slots = new ArrayList<>();
        var free = FREE.entrySet().iterator();
        for (BiomeLook look : chosen) {
            var slot = free.next();
            slots.add(new Slot(slot.getKey(), slot.getValue(), look, List.copyOf(distinct.get(look))));
        }
        return new BiomeSlots(slots);
    }

    /** Client biome and fog definitions of every slot, by pack path. */
    public Map<String, byte[]> packFiles() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (Slot slot : slots) {
            BiomeLook look = slot.look();
            String fog = "twilight:fog_" + slot.name();
            JsonObject components = new JsonObject();
            components.add("minecraft:fog_appearance", object("fog_identifier", fog));
            components.add("minecraft:sky_color", object("sky_color", BiomeLook.hex(look.sky())));
            components.add("minecraft:water_appearance", object("surface_color", BiomeLook.hex(look.water())));
            components.add("minecraft:grass_appearance", object("color", BiomeLook.hex(look.grass())));
            components.add("minecraft:foliage_appearance", object("color", BiomeLook.hex(look.foliage())));
            JsonObject biome = new JsonObject();
            biome.add("description", object("identifier", "minecraft:" + slot.name()));
            biome.add("components", components);
            JsonObject root = new JsonObject();
            root.addProperty("format_version", "1.21.120");
            root.add("minecraft:client_biome", biome);
            files.put("biomes/" + slot.name() + ".client_biome.json", GSON.toJson(root).getBytes(StandardCharsets.UTF_8));

            JsonObject distance = new JsonObject();
            distance.add("air", fog(0.92, 1.0, look.fog(), "render"));
            distance.add("water", fog(0.0, 60.0, look.waterFog(), "fixed"));
            JsonObject settings = new JsonObject();
            settings.add("description", object("identifier", fog));
            settings.add("distance", distance);
            JsonObject fogRoot = new JsonObject();
            fogRoot.addProperty("format_version", "1.16.100");
            fogRoot.add("minecraft:fog_settings", settings);
            files.put("fogs/twilight_" + slot.name() + ".json", GSON.toJson(fogRoot).getBytes(StandardCharsets.UTF_8));
        }
        files.put(PATH, GSON.toJson(toJson()).getBytes(StandardCharsets.UTF_8));
        return files;
    }

    private static JsonObject fog(double start, double end, int colour, String type) {
        JsonObject fog = new JsonObject();
        fog.addProperty("fog_start", start);
        fog.addProperty("fog_end", end);
        fog.addProperty("fog_color", BiomeLook.hex(colour));
        fog.addProperty("render_distance_type", type);
        return fog;
    }

    private static JsonObject object(String key, String value) {
        JsonObject object = new JsonObject();
        object.addProperty(key, value);
        return object;
    }

    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        JsonArray array = new JsonArray();
        for (Slot slot : slots) {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", slot.id());
            entry.addProperty("name", slot.name());
            entry.add("look", slot.look().toJson());
            JsonArray biomes = new JsonArray();
            slot.biomes().forEach(biomes::add);
            entry.add("biomes", biomes);
            array.add(entry);
        }
        root.add("slots", array);
        return root;
    }

    public static BiomeSlots fromJson(JsonObject root) {
        if (root.get("version").getAsInt() != 1) throw new IllegalArgumentException("Unsupported biome slot table");
        List<Slot> slots = new ArrayList<>();
        for (var element : root.getAsJsonArray("slots")) {
            JsonObject entry = element.getAsJsonObject();
            int id = entry.get("id").getAsInt();
            String name = entry.get("name").getAsString();
            if (!name.equals(FREE.get(id))) throw new IllegalArgumentException("Biome slot " + id + " is not a free Bedrock biome");
            List<String> biomes = new ArrayList<>();
            entry.getAsJsonArray("biomes").forEach(biome -> biomes.add(biome.getAsString()));
            slots.add(new Slot(id, name, BiomeLook.fromJson(entry.getAsJsonArray("look")), biomes));
        }
        return new BiomeSlots(slots);
    }

    private static Map<Integer, String> free() {
        Map<Integer, String> free = new LinkedHashMap<>();
        free.put(19, "taiga_hills");
        free.put(17, "desert_hills");
        free.put(18, "forest_hills");
        free.put(22, "jungle_hills");
        free.put(28, "birch_forest_hills");
        free.put(31, "cold_taiga_hills");
        free.put(33, "mega_taiga_hills");
        free.put(49, "bamboo_jungle_hills");
        free.put(20, "extreme_hills_edge");
        free.put(13, "ice_mountains");
        free.put(39, "mesa_plateau");
        free.put(130, "desert_mutated");
        free.put(133, "taiga_mutated");
        free.put(149, "jungle_mutated");
        free.put(151, "jungle_edge_mutated");
        free.put(156, "birch_forest_hills_mutated");
        free.put(158, "cold_taiga_mutated");
        free.put(161, "redwood_taiga_hills_mutated");
        free.put(162, "extreme_hills_plus_trees_mutated");
        free.put(164, "savanna_plateau_mutated");
        free.put(166, "mesa_plateau_stone_mutated");
        free.put(167, "mesa_plateau_mutated");
        free.put(134, "swampland_mutated");
        free.put(157, "roofed_forest_mutated");
        free.put(15, "mushroom_island_shore");
        return java.util.Collections.unmodifiableMap(free);
    }
}
