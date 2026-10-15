/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.compiler;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.siberanka.twilight.source.ResourceIndex;
import com.siberanka.twilight.world.BiomeLook;
import com.siberanka.twilight.world.BiomeMatcher;

import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Looks of the custom (non-minecraft) biomes of the server's datapacks and registry. */
final class CustomBiomes {
    private static final Pattern BIOME = Pattern.compile("data/([a-z0-9_.-]+)/worldgen/biome/([a-z0-9_./-]+)\\.json");
    private static final Gson GSON = new Gson();

    private CustomBiomes() {}

    @SuppressWarnings("unchecked")
    static Map<String, BiomeLook> read(ResourceIndex resources, Map<String, Map<String, ?>> serverBiomes, BiomeMatcher matcher)
            throws IOException {
        Map<String, BiomeLook> looks = new TreeMap<>();
        for (String path : resources.pathsStartingWith("data/")) {
            Matcher match = BIOME.matcher(path);
            if (!match.matches() || "minecraft".equals(match.group(1))) continue;
            var asset = resources.find(path).orElse(null);
            if (asset == null) continue;
            try {
                Map<String, Object> biome = GSON.fromJson(JsonParser.parseString(asset.readUtf8()), Map.class);
                if (biome != null) looks.put(match.group(1) + ':' + match.group(2), matcher.look(biome));
            } catch (JsonParseException | IllegalStateException | ClassCastException malformed) {
                // Java refuses such a datapack biome too; it never reaches a client.
            }
        }
        // The registry is what clients receive: it wins over datapack files and adds plugin biomes.
        serverBiomes.forEach((key, biome) -> {
            if (!key.startsWith("minecraft:")) looks.put(key, matcher.look(biome));
        });
        return looks;
    }
}
