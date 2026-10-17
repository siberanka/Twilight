/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.compiler;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Inverted elements (from past to) keep every texture corner where Java draws it once their corners are ordered. */
class InvertedElementsTest {
    private static final String[] FACES = {"down", "up", "north", "south", "west", "east"};

    @Test void anOrderedElementIsLeftAlone() {
        JsonObject element = JsonParser.parseString("""
                {"from":[0,0,0],"to":[16,8,16],"faces":{"up":{"uv":[0,0,16,16],"texture":"#0"}}}
                """).getAsJsonObject();
        assertSame(element, InvertedElements.normalized(element));
    }

    @Test void theUpFaceOfAFullyInvertedBoxIsDrawnAtTheBottom() {
        JsonObject element = JsonParser.parseString("""
                {"from":[16,16,16],"to":[0,0,0],"faces":{"up":{"uv":[0,0,16,16],"texture":"#0"}}}
                """).getAsJsonObject();
        JsonObject normalized = InvertedElements.normalized(element);
        assertEquals(JsonParser.parseString("[0.0,0.0,0.0]"), normalized.get("from"));
        assertEquals(JsonParser.parseString("[16.0,16.0,16.0]"), normalized.get("to"));
        JsonObject faces = normalized.getAsJsonObject("faces");
        assertFalse(faces.has("up"));
        assertEquals(JsonParser.parseString("[16.0,0.0,0.0,16.0]"), faces.getAsJsonObject("down").get("uv"));
        assertFalse(faces.getAsJsonObject("down").has("rotation"));
        assertEquals("#0", faces.getAsJsonObject("down").get("texture").getAsString());
    }

    /** Every vertex of every face keeps Java's texture corner, for random boxes, UVs and face rotations. */
    @Test void everyCornerKeepsJavasTexturePosition() {
        Random random = new Random(26);
        for (int round = 0; round < 500; round++) {
            double[] from = new double[3], to = new double[3];
            for (int axis = 0; axis < 3; axis++) {
                from[axis] = random.nextInt(17);
                do to[axis] = random.nextInt(17); while (to[axis] == from[axis]);
            }
            JsonObject faces = new JsonObject();
            for (String face : FACES) {
                JsonObject value = new JsonObject();
                if (random.nextBoolean()) value.add("uv", BedrockPackCompiler.numberArray(random.nextInt(17),
                        random.nextInt(17), random.nextInt(17), random.nextInt(17)));
                if (random.nextBoolean()) value.addProperty("rotation", 90 * random.nextInt(4));
                value.addProperty("texture", "#" + face);
                faces.add(face, value);
            }
            JsonObject element = new JsonObject();
            element.add("from", BedrockPackCompiler.numberArray(from));
            element.add("to", BedrockPackCompiler.numberArray(to));
            element.add("faces", faces);
            Map<String, String> java = javaCorners(element);
            Map<String, String> ordered = javaCorners(InvertedElements.normalized(element));
            assertEquals(java, ordered, "element " + element);
        }
    }

    /** A fully inverted box shows its inside: its up face lies at the bottom and looks up, into the box. */
    @Test void insideFacesBecomeOneSidedPlanes() {
        JsonObject element = JsonParser.parseString("""
                {"from":[16,16,16],"to":[0,0,0],"rotation":{"angle":22.5,"axis":"y","origin":[8,8,8]},
                 "faces":{"up":{"uv":[0,0,16,16],"texture":"#0"},"north":{"uv":[0,0,8,8],"texture":"#0"}}}
                """).getAsJsonObject();
        assertTrue(InvertedElements.inverted(element));
        var faces = InvertedElements.faces(element);
        assertEquals(2, faces.size());
        JsonObject up = faces.get(0);
        assertEquals(JsonParser.parseString("[0.0,0.0,0.0]"), up.get("from"));
        assertEquals(JsonParser.parseString("[16.0,0.0,16.0]"), up.get("to"));
        assertEquals(JsonParser.parseString("[16.0,16.0,0.0,0.0]"), up.getAsJsonObject("faces").getAsJsonObject("up").get("uv"));
        assertEquals(element.get("rotation"), up.get("rotation"));
        JsonObject north = faces.get(1);
        assertTrue(north.getAsJsonObject("faces").has("north"), "Java's north face lies at the south side, looking north");
        assertEquals(16.0, north.getAsJsonArray("from").get(2).getAsDouble());
    }

    /** Every plane keeps the texture corners of the face it comes from. */
    @Test void planesKeepJavasCorners() {
        Random random = new Random(7);
        for (int round = 0; round < 300; round++) {
            double[] from = new double[3], to = new double[3];
            for (int axis = 0; axis < 3; axis++) {
                from[axis] = random.nextInt(17);
                do to[axis] = random.nextInt(17); while (to[axis] == from[axis]);
            }
            if (from[0] < to[0] && from[1] < to[1] && from[2] < to[2]) { double swap = from[1]; from[1] = to[1]; to[1] = swap; }
            JsonObject faces = new JsonObject();
            for (String face : FACES) {
                JsonObject value = new JsonObject();
                value.add("uv", BedrockPackCompiler.numberArray(random.nextInt(17), random.nextInt(17), random.nextInt(17), random.nextInt(17)));
                if (random.nextBoolean()) value.addProperty("rotation", 90 * random.nextInt(4));
                value.addProperty("texture", "#" + face);
                faces.add(face, value);
            }
            JsonObject element = new JsonObject();
            element.add("from", BedrockPackCompiler.numberArray(from));
            element.add("to", BedrockPackCompiler.numberArray(to));
            element.add("faces", faces);
            Map<String, String> java = javaCorners(element);
            var planes = InvertedElements.faces(element);
            assertEquals(6, planes.size());
            for (JsonObject plane : planes) {
                Map<String, String> corners = javaCorners(plane);
                assertEquals(4, corners.size());
                for (var corner : corners.entrySet()) assertEquals(java.get(corner.getKey()), corner.getValue(), "element " + element);
            }
        }
    }

    /** Java's baking: each texture's vertex positions with the texture corner it gets (FaceInfo, CuboidFace). */
    private static Map<String, String> javaCorners(JsonObject element) {
        double[] from = vector(element.getAsJsonArray("from")), to = vector(element.getAsJsonArray("to"));
        Map<String, String> result = new HashMap<>();
        for (String name : FACES) {
            JsonObject face = element.getAsJsonObject("faces").getAsJsonObject(name);
            if (face == null) continue;
            double[] uv = face.has("uv") ? vector(face.getAsJsonArray("uv")) : BedrockPackCompiler.defaultFaceUv(name, from, to);
            int turn = face.has("rotation") ? face.get("rotation").getAsInt() / 90 : 0;
            int[][] picks = switch (name) {
                case "down" -> new int[][]{{0, 0, 1}, {0, 0, 0}, {1, 0, 0}, {1, 0, 1}};
                case "up" -> new int[][]{{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}};
                case "north" -> new int[][]{{1, 1, 0}, {1, 0, 0}, {0, 0, 0}, {0, 1, 0}};
                case "south" -> new int[][]{{0, 1, 1}, {0, 0, 1}, {1, 0, 1}, {1, 1, 1}};
                case "west" -> new int[][]{{0, 1, 0}, {0, 0, 0}, {0, 0, 1}, {0, 1, 1}};
                default -> new int[][]{{1, 1, 1}, {1, 0, 1}, {1, 0, 0}, {1, 1, 0}};
            };
            for (int vertex = 0; vertex < 4; vertex++) {
                int shifted = (vertex + turn) % 4;
                double u = shifted == 0 || shifted == 1 ? uv[0] : uv[2], v = shifted == 0 || shifted == 3 ? uv[1] : uv[3];
                StringBuilder position = new StringBuilder(face.get("texture").getAsString());
                for (int axis = 0; axis < 3; axis++) position.append(' ').append(picks[vertex][axis] == 0 ? from[axis] : to[axis]);
                result.put(position.toString(), u + "," + v);
            }
        }
        return result;
    }

    private static double[] vector(JsonArray array) {
        double[] result = new double[array.size()];
        for (int i = 0; i < result.length; i++) result[i] = array.get(i).getAsDouble();
        return result;
    }
}
