/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.world;

import com.google.gson.JsonArray;

/**
 * Everything a player sees of a biome: grass, foliage, water, underwater fog, fog and sky colours
 * (0xRRGGBB), climate (temperature, downfall) and precipitation (0 none, 1 rain, 2 snow).
 */
public record BiomeLook(int grass, int foliage, int water, int waterFog, int fog, int sky, float temperature,
                        float downfall, int precipitation) {

    public JsonArray toJson() {
        JsonArray array = new JsonArray();
        array.add(grass);
        array.add(foliage);
        array.add(water);
        array.add(waterFog);
        array.add(fog);
        array.add(sky);
        array.add(temperature);
        array.add(downfall);
        array.add(precipitation);
        return array;
    }

    public static BiomeLook fromJson(JsonArray array) {
        return new BiomeLook(array.get(0).getAsInt(), array.get(1).getAsInt(), array.get(2).getAsInt(),
                array.get(3).getAsInt(), array.get(4).getAsInt(), array.get(5).getAsInt(), array.get(6).getAsFloat(),
                array.get(7).getAsFloat(), array.get(8).getAsInt());
    }

    /** Colours as Bedrock resource files write them. */
    static String hex(int rgb) {
        return String.format("#%06x", rgb & 0xFFFFFF);
    }
}
