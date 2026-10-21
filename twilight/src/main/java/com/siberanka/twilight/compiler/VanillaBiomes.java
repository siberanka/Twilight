/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.compiler;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.siberanka.twilight.world.BiomeMatcher;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Builds the vanilla biome appearance table from the verified Minecraft client. */
final class VanillaBiomes {
    private static final String BIOMES = "data/minecraft/worldgen/biome/";
    private static final Gson GSON = new Gson();

    private VanillaBiomes() {}

    @SuppressWarnings("unchecked")
    static BiomeMatcher read(VanillaAssetCache vanilla) throws IOException {
        int[] grass = BiomeMatcher.grid(colorMap(vanilla, "assets/minecraft/textures/colormap/grass.png"));
        int[] foliage = BiomeMatcher.grid(colorMap(vanilla, "assets/minecraft/textures/colormap/foliage.png"));
        BiomeMatcher sampler = new BiomeMatcher(Map.of(), grass, foliage);
        Map<String, BiomeMatcher.Appearance> biomes = new LinkedHashMap<>();
        for (String path : vanilla.dataEntries(BIOMES)) {
            if (!path.endsWith(".json")) continue;
            byte[] bytes = vanilla.readData(path).orElseThrow();
            Map<String, Object> biome = GSON.fromJson(JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)), Map.class);
            biomes.put("minecraft:" + path.substring(BIOMES.length(), path.length() - 5), sampler.appearance(biome));
        }
        if (biomes.isEmpty()) throw new IOException("The Minecraft client has no biome definitions");
        return new BiomeMatcher(biomes, grass, foliage);
    }

    private static int[] colorMap(VanillaAssetCache vanilla, String path) throws IOException {
        BufferedImage image = PngImages.read(vanilla.readTexture(path).orElseThrow(() -> new IOException("Missing " + path)));
        if (image == null || image.getWidth() != 256 || image.getHeight() != 256) throw new IOException("Unexpected colour map " + path);
        return image.getRGB(0, 0, 256, 256, null, 0, 256);
    }
}
