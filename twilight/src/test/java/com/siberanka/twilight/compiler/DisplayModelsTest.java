/* Copyright (c) 2026 siberanka. Twilight License (PolyForm Noncommercial 1.0.0 + terms), see LICENSE. */
package com.siberanka.twilight.compiler;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisplayModelsTest {
    private static JsonArray element(String json) {
        JsonArray elements = new JsonArray();
        elements.add(JsonParser.parseString(json));
        return elements;
    }

    private static double[] numbers(JsonArray array) {
        double[] values = new double[array.size()];
        for (int i = 0; i < values.length; i++) values[i] = array.get(i).getAsDouble();
        return values;
    }

    /** y=90 turns north to east (a furnace facing east), and each face keeps the texture area it showed. */
    @Test
    void blockStateRotationMovesFacesWithTheirTextures() {
        JsonArray baked = DisplayModels.bake(element("""
                {"from":[0,0,0],"to":[16,8,4],"faces":{
                  "north":{"uv":[0,0,8,4],"texture":"#front"},
                  "up":{"uv":[0,0,16,4],"texture":"#top"}}}
                """), 0, 90, false);
        JsonObject element = baked.get(0).getAsJsonObject();
        assertArrayEquals(new double[]{12, 0, 0}, numbers(element.getAsJsonArray("from")));
        assertArrayEquals(new double[]{16, 8, 16}, numbers(element.getAsJsonArray("to")));
        JsonObject faces = element.getAsJsonObject("faces");
        assertTrue(faces.has("east") && faces.has("up"), faces.toString());
        assertFalse(faces.has("north"));
        assertArrayEquals(new double[]{0, 0, 8, 4}, numbers(faces.getAsJsonObject("east").getAsJsonArray("uv")),
                "a side face moved to another side keeps its texture upright");
        JsonObject up = faces.getAsJsonObject("up");
        assertEquals(90, up.get("rotation").getAsInt(), "the top face turns with the block: " + up);
    }

    /** uvlock keeps the texture aligned with the world: faces get the UVs of their new place. */
    @Test
    void uvlockKeepsTexturesAlignedWithTheWorld() {
        JsonArray baked = DisplayModels.bake(element("""
                {"from":[0,0,0],"to":[16,8,4],"faces":{"up":{"texture":"#top"}}}
                """), 0, 90, true);
        JsonObject up = baked.get(0).getAsJsonObject().getAsJsonObject("faces").getAsJsonObject("up");
        assertArrayEquals(new double[]{12, 0, 16, 16}, numbers(up.getAsJsonArray("uv")));
        assertFalse(up.has("rotation"));
    }

    /** x=90 tips the top to the north; an element rotation follows the turned axis. */
    @Test
    void xRotationTurnsTopsAndElementRotations() {
        JsonArray baked = DisplayModels.bake(element("""
                {"from":[4,0,4],"to":[12,16,12],"rotation":{"origin":[8,8,8],"axis":"y","angle":22.5},
                 "faces":{"up":{"uv":[4,4,12,12],"texture":"#end"}}}
                """), 90, 0, false);
        JsonObject element = baked.get(0).getAsJsonObject();
        assertTrue(element.getAsJsonObject("faces").has("north"), element.toString());
        JsonObject rotation = element.getAsJsonObject("rotation");
        assertEquals("z", rotation.get("axis").getAsString());
        assertEquals(22.5, Math.abs(rotation.get("angle").getAsDouble()));
    }

    @Test
    void extrusionCoversOpaquePixelsWithRectangles() {
        boolean[][] opaque = {
                {true, true, false},
                {true, true, true},
                {false, false, true}};
        List<int[]> rectangles = DisplayModels.rectangles(opaque);
        int covered = 0;
        boolean[][] seen = new boolean[3][3];
        for (int[] r : rectangles) for (int y = r[1]; y < r[3]; y++) for (int x = r[0]; x < r[2]; x++) {
            assertTrue(opaque[y][x]);
            assertFalse(seen[y][x], "rectangles do not overlap");
            seen[y][x] = true;
            covered++;
        }
        assertEquals(6, covered);
        assertTrue(rectangles.size() <= 3, "neighbouring pixels share rectangles");
    }

    /** Variants match on the properties they name; multipart adds every case whose condition holds (or, not). */
    @Test
    void blockStatesSelectVariantsAndMultipartCases() {
        JsonObject variants = JsonParser.parseString("""
                {"variants":{"facing=north,lit=false":{"model":"a"},"facing=east":[{"model":"b","y":90},{"model":"c"}]}}
                """).getAsJsonObject();
        assertEquals(List.of(new DisplayModels.Applied("minecraft:b", 0, 90, false)),
                DisplayModels.applied(variants, Map.of("facing", "east", "lit", "true")));
        JsonObject multipart = JsonParser.parseString("""
                {"multipart":[{"apply":{"model":"post"}},
                  {"when":{"north":"true"},"apply":{"model":"side","uvlock":true}},
                  {"when":{"OR":[{"east":"true"},{"west":"low|tall"}]},"apply":{"model":"wide"}},
                  {"when":{"up":"!true"},"apply":{"model":"cap"}}]}
                """).getAsJsonObject();
        List<DisplayModels.Applied> applied = DisplayModels.applied(multipart, Map.of("north", "true", "east", "false",
                "west", "tall", "up", "false"));
        assertEquals(List.of("minecraft:post", "minecraft:side", "minecraft:wide", "minecraft:cap"),
                applied.stream().map(DisplayModels.Applied::model).toList());
        assertTrue(applied.get(1).uvlock());
    }
}
