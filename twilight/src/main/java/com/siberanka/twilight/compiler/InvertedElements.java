/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.compiler;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Map;

/**
 * Elements whose {@code from} is past their {@code to} on an axis. Java bakes every face from the two corners as
 * written, so such a box is turned inside out: its {@code up} face lies at the bottom, its {@code east} face on the
 * west side, and textures run the other way. Modellers use it for the inside of spheres and glass (CraftEngine's
 * globe). Bedrock boxes need a positive size; drawn with a negative one, faces took the wrong texture corners.
 *
 * <p>An inverted element becomes the same box with ordered corners; each face moves to the side of the box where
 * Java draws it and gets the UV rectangle and rotation that put every texture corner where Java puts it. Bedrock's
 * entity and block materials draw both sides of a face, so the face still shows from the side Java shows it.
 */
final class InvertedElements {
    private static final String[] FACES = {"down", "up", "north", "south", "west", "east"};
    /** Java's FaceInfo: per face, the four vertices as (x, y, z) picks of 0 = from, 1 = to. */
    private static final Map<String, int[][]> VERTICES = Map.of(
            "down", new int[][]{{0, 0, 1}, {0, 0, 0}, {1, 0, 0}, {1, 0, 1}},
            "up", new int[][]{{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}},
            "north", new int[][]{{1, 1, 0}, {1, 0, 0}, {0, 0, 0}, {0, 1, 0}},
            "south", new int[][]{{0, 1, 1}, {0, 0, 1}, {1, 0, 1}, {1, 1, 1}},
            "west", new int[][]{{0, 1, 0}, {0, 0, 0}, {0, 0, 1}, {0, 1, 1}},
            "east", new int[][]{{1, 1, 1}, {1, 0, 1}, {1, 0, 0}, {1, 1, 0}});
    private static final double EPSILON = 1e-6;

    private InvertedElements() {}

    /** The element with ordered corners, or the element itself when no axis is inverted. */
    static JsonObject normalized(JsonObject element) {
        double[] from = vector(element.get("from"), 0), to = vector(element.get("to"), 16);
        if (from == null || to == null) return element;
        boolean[] inverted = {from[0] > to[0], from[1] > to[1], from[2] > to[2]};
        if (!inverted[0] && !inverted[1] && !inverted[2]) return element;
        double[] min = {Math.min(from[0], to[0]), Math.min(from[1], to[1]), Math.min(from[2], to[2])};
        double[] max = {Math.max(from[0], to[0]), Math.max(from[1], to[1]), Math.max(from[2], to[2])};
        JsonObject result = element.deepCopy();
        result.add("from", BedrockPackCompiler.numberArray(min));
        result.add("to", BedrockPackCompiler.numberArray(max));
        if (!(element.get("faces") instanceof JsonObject faces)) return result;
        JsonObject moved = new JsonObject();
        for (String name : FACES) {
            if (!(faces.get(name) instanceof JsonObject face)) continue;
            int axis = axis(name);
            String target = inverted[axis] ? opposite(name) : name;
            JsonObject remapped = remap(name, face, from, to, target, min, max);
            // Two faces never land on the same side: inverting an axis swaps both of its faces.
            moved.add(target, remapped == null ? face.deepCopy() : remapped);
        }
        result.add("faces", moved);
        return result;
    }

    /** Whether any axis of the element runs backwards. */
    static boolean inverted(JsonObject element) {
        double[] from = vector(element.get("from"), 0), to = vector(element.get("to"), 16);
        return from != null && to != null && (from[0] > to[0] || from[1] > to[1] || from[2] > to[2]);
    }

    /**
     * The faces of an inverted element as one flat element each, for materials that draw one side of a face as
     * Java does. Java shows a face only from the side its corners wind towards ({@code FaceBakery} finds that
     * direction from the corners), which for an inverted element is the inside of the box: the inside of a sphere
     * shows, its outside does not. Each face becomes a zero-thickness element on its plane that keeps only the face
     * looking that way, with Java's texture corners. Faces Java cannot draw (no area) are left out.
     */
    static java.util.List<JsonObject> faces(JsonObject element) {
        double[] from = vector(element.get("from"), 0), to = vector(element.get("to"), 16);
        java.util.List<JsonObject> result = new java.util.ArrayList<>();
        if (from == null || to == null || !(element.get("faces") instanceof JsonObject faces)) return result;
        for (String name : FACES) {
            if (!(faces.get(name) instanceof JsonObject face)) continue;
            double[][] corners = positions(name, from, to);
            String facing = facing(corners);
            if (facing == null) continue;
            int axis = axis(facing);
            double[] min = new double[3], max = new double[3];
            for (int i = 0; i < 3; i++) {
                min[i] = Double.MAX_VALUE;
                max[i] = -Double.MAX_VALUE;
                for (double[] corner : corners) {
                    min[i] = Math.min(min[i], corner[i]);
                    max[i] = Math.max(max[i], corner[i]);
                }
            }
            min[axis] = max[axis] = corners[0][axis];
            JsonObject remapped = remap(name, face, from, to, facing, min, max);
            if (remapped == null) continue;
            JsonObject flat = new JsonObject();
            flat.add("from", BedrockPackCompiler.numberArray(min));
            flat.add("to", BedrockPackCompiler.numberArray(max));
            if (element.has("rotation")) flat.add("rotation", element.get("rotation").deepCopy());
            JsonObject only = new JsonObject();
            only.add(facing, remapped);
            flat.add("faces", only);
            result.add(flat);
        }
        return result;
    }

    /** The direction a quad faces from its corners' winding (Java's {@code FaceBakery.calculateFacing}). */
    private static String facing(double[][] corners) {
        double[] a = subtract(corners[1], corners[0]), b = subtract(corners[2], corners[0]);
        double[] normal = {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
        int axis = 0;
        for (int i = 1; i < 3; i++) if (Math.abs(normal[i]) > Math.abs(normal[axis])) axis = i;
        if (Math.abs(normal[axis]) < EPSILON) return null;
        boolean positive = normal[axis] > 0;
        return switch (axis) {
            case 0 -> positive ? "east" : "west";
            case 1 -> positive ? "up" : "down";
            default -> positive ? "south" : "north";
        };
    }

    private static double[] subtract(double[] a, double[] b) {
        return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    /** The face drawn on {@code target} of the ordered box with Java's texture corners, or null if degenerate. */
    private static JsonObject remap(String name, JsonObject face, double[] from, double[] to, String target,
                                    double[] min, double[] max) {
        double[] uv;
        try {
            uv = face.has("uv") ? vector4(face.get("uv")) : BedrockPackCompiler.defaultFaceUv(name, from, to);
        } catch (RuntimeException malformed) {
            return null;
        }
        if (uv == null) return null;
        int turn = face.has("rotation") ? Math.floorMod(face.get("rotation").getAsInt(), 360) / 90 : 0;
        double[][] javaPositions = positions(name, from, to);
        double[][] javaUvs = new double[4][];
        for (int vertex = 0; vertex < 4; vertex++) javaUvs[vertex] = corner(uv, (vertex + turn) % 4);
        // The texture corner Java gives each vertex of the face, found again on the ordered box's face.
        double[][] targetPositions = positions(target, min, max);
        double[][] wanted = new double[4][];
        for (int vertex = 0; vertex < 4; vertex++) {
            double[] found = null;
            for (int source = 0; source < 4; source++) {
                if (!same(targetPositions[vertex], javaPositions[source])) continue;
                if (found != null && !same(found, javaUvs[source])) return null; // a flat face: corners coincide
                found = javaUvs[source];
            }
            if (found == null) return null;
            wanted[vertex] = found;
        }
        for (int offset = 0; offset < 4; offset++) {
            int rotation = (turn + offset) % 4;
            double[] rectangle = rectangle(wanted, rotation);
            if (rectangle == null) continue;
            JsonObject result = face.deepCopy();
            result.add("uv", BedrockPackCompiler.numberArray(rectangle));
            if (rotation == 0) result.remove("rotation");
            else result.addProperty("rotation", rotation * 90);
            return result;
        }
        return null;
    }

    /** The UV rectangle that gives each vertex its wanted corner under a rotation, or null if none does. */
    private static double[] rectangle(double[][] wanted, int rotation) {
        double[] rectangle = new double[4];
        boolean[] set = new boolean[4];
        for (int vertex = 0; vertex < 4; vertex++) {
            int shifted = (vertex + rotation) % 4;
            int u = shifted == 0 || shifted == 1 ? 0 : 2, v = shifted == 0 || shifted == 3 ? 1 : 3;
            if (!assign(rectangle, set, u, wanted[vertex][0]) || !assign(rectangle, set, v, wanted[vertex][1])) return null;
        }
        return rectangle;
    }

    private static boolean assign(double[] rectangle, boolean[] set, int index, double value) {
        if (set[index]) return Math.abs(rectangle[index] - value) < EPSILON;
        rectangle[index] = value;
        set[index] = true;
        return true;
    }

    /** Java's texture corner for a shifted vertex index: 0 = (u0, v0), 1 = (u0, v1), 2 = (u1, v1), 3 = (u1, v0). */
    private static double[] corner(double[] uv, int shifted) {
        return new double[]{shifted == 0 || shifted == 1 ? uv[0] : uv[2], shifted == 0 || shifted == 3 ? uv[1] : uv[3]};
    }

    private static double[][] positions(String face, double[] from, double[] to) {
        int[][] picks = VERTICES.get(face);
        double[][] result = new double[4][3];
        for (int vertex = 0; vertex < 4; vertex++) {
            for (int axis = 0; axis < 3; axis++) result[vertex][axis] = picks[vertex][axis] == 0 ? from[axis] : to[axis];
        }
        return result;
    }

    private static boolean same(double[] a, double[] b) {
        for (int i = 0; i < a.length; i++) if (Math.abs(a[i] - b[i]) > EPSILON) return false;
        return true;
    }

    private static int axis(String face) {
        return switch (face) {
            case "west", "east" -> 0;
            case "down", "up" -> 1;
            default -> 2;
        };
    }

    private static String opposite(String face) {
        return switch (face) {
            case "down" -> "up";
            case "up" -> "down";
            case "north" -> "south";
            case "south" -> "north";
            case "west" -> "east";
            default -> "west";
        };
    }

    private static double[] vector(JsonElement value, double fallback) {
        if (value == null) return new double[]{fallback, fallback, fallback};
        if (!value.isJsonArray() || value.getAsJsonArray().size() < 3) return null;
        double[] result = new double[3];
        for (int i = 0; i < 3; i++) {
            JsonElement number = value.getAsJsonArray().get(i);
            if (!number.isJsonPrimitive() || !number.getAsJsonPrimitive().isNumber()) return null;
            result[i] = number.getAsDouble();
            if (!Double.isFinite(result[i])) return null;
        }
        return result;
    }

    private static double[] vector4(JsonElement value) {
        if (!value.isJsonArray() || value.getAsJsonArray().size() < 4) return null;
        double[] result = new double[4];
        for (int i = 0; i < 4; i++) {
            result[i] = value.getAsJsonArray().get(i).getAsDouble();
            if (!Double.isFinite(result[i])) return null;
        }
        return result;
    }
}
