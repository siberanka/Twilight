package com.siberanka.twilight.compiler;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Regression: the cross-shaped arrow shaft must follow the fletching, NOT move forward. */
class Crossbow3DProjectileAlignmentRepairTest {
    private static final String PREFIX = "models/entity/geometry.";
    private static final String BASE = "test_crossbow";

    @Test
    void repairsBothLoadingFramesAndArrowAndRocketWithoutTouchingString() {
        Map<String, byte[]> pack = pack(true);
        byte[] untouchedIdle = pack.get(PREFIX + BASE + "_state_0.geo.json").clone();
        byte[] unrelatedBow = pack.get("models/entity/geometry.bow.geo.json").clone();
        byte[] animations = pack.get("animations/crossbow.animation.json").clone();
        assertEquals(4, Crossbow3DProjectileAlignmentRepair.apply(pack));
        assertEquals(0, Crossbow3DProjectileAlignmentRepair.apply(pack));
        assertArrayEquals(untouchedIdle, pack.get(PREFIX + BASE + "_state_0.geo.json"));
        assertArrayEquals(unrelatedBow, pack.get("models/entity/geometry.bow.geo.json"));
        assertArrayEquals(animations, pack.get("animations/crossbow.animation.json"));
        assertMovement(pack, PREFIX + BASE + "_state_1.geo.json", 4.25, 8.0, 10.735);
        assertMovement(pack, PREFIX + BASE + "_state_2.geo.json", 2.50, 6.25, 8.985);
        assertMovement(pack, PREFIX + BASE + "_state_3.geo.json", 1.25, 5.0, 7.735);
        assertMovement(pack, PREFIX + "arrow.geo.json", 1.25, 5.0, 7.735);
        assertMovement(pack, PREFIX + "rocket.geo.json", 1.25, 5.0, 7.735);
    }

    @Test
    void correctlyAlignedSourceModelsRemainUntouched() {
        Map<String, byte[]> pack = pack(false);
        Map<String, byte[]> snapshot = new LinkedHashMap<>(pack);
        assertEquals(0, Crossbow3DProjectileAlignmentRepair.apply(pack));
        for (Map.Entry<String, byte[]> entry : snapshot.entrySet()) {
            assertArrayEquals(entry.getValue(), pack.get(entry.getKey()), entry.getKey());
        }
    }

    @Test
    void removesTheObsoleteArrowAsStringMarker() {
        Map<String, byte[]> pack = pack(true);
        String marker = "twilight/3d-crossbow-string-direction-repair.json";
        pack.put(marker, new byte[]{1, 2, 3});
        assertEquals(5, Crossbow3DProjectileAlignmentRepair.apply(pack));
        assertTrue(!pack.containsKey(marker));
    }

    private static void assertMovement(Map<String, byte[]> pack, String file,
                                       double origin, double pivot, double feather) {
        List<Object> cubes = cubes(pack.get(file));
        for (int index : new int[]{1, 2}) {
            Map<String, Object> shaft = object(cubes.get(index));
            assertNear(origin, point(shaft, "origin", 2), file + " shaft origin " + index);
            assertNear(pivot, point(shaft, "pivot", 2), file + " shaft pivot " + index);
        }
        assertNear(feather, point(object(cubes.get(3)), "origin", 2), file + " fletching");
        assertNear(.75359, point(object(cubes.get(4)), "origin", 2),
                file + " real string must not be moved");
    }

    private static void assertNear(double expected, double actual, String message) {
        assertTrue(Math.abs(expected - actual) < .00001, message + ": " + expected + " != " + actual);
    }

    private static Map<String, byte[]> pack(boolean withWrongDirection) {
        Map<String, byte[]> result = new LinkedHashMap<>();
        result.put("twilight/geyser_item_mappings.json", bytes(Map.of("items", Map.of(
                "minecraft:crossbow", List.of(
                        Map.of("type", "legacy", "custom_model_data", 10001,
                                "bedrock_identifier", "twilight:" + BASE),
                        chargedMapping("arrow", "arrow"), chargedMapping("rocket", "rocket"))))));
        result.put("animations/crossbow.animation.json", bytes(Map.of("animations", Map.of())));
        result.put("models/entity/geometry.bow.geo.json", new byte[]{1, 2, 3});
        result.put(PREFIX + BASE + "_state_0.geo.json", geometry(4.25, 8.0, 10.735));
        result.put(PREFIX + BASE + "_state_1.geo.json", geometry(4.25, 8.0, 10.735));
        result.put(PREFIX + BASE + "_state_2.geo.json",
                geometry(withWrongDirection ? 6.0 : 2.5, withWrongDirection ? 9.75 : 6.25, 8.985));
        result.put(PREFIX + BASE + "_state_3.geo.json",
                geometry(withWrongDirection ? 7.25 : 1.25, withWrongDirection ? 11.0 : 5.0, 7.735));
        for (String charged : List.of("arrow", "rocket")) {
            result.put(PREFIX + charged + ".geo.json",
                    geometry(withWrongDirection ? 7.25 : 1.25, withWrongDirection ? 11.0 : 5.0, 7.735));
        }
        return result;
    }

    private static Map<String, Object> chargedMapping(String id, String chargeType) {
        return Map.of("type", "legacy", "custom_model_data", 10001,
                "bedrock_identifier", "twilight:" + id,
                "predicate", Map.of("property", "charge_type", "value", chargeType));
    }

    private static byte[] geometry(double shaftZ, double pivotZ, double featherZ) {
        List<Object> cubes = new ArrayList<>();
        cubes.add(cube(new double[]{0, 1, 0}, new double[]{1, 1, 1},
                new double[]{0, 1, 0}, Map.of()));
        cubes.add(cube(new double[]{-.015, 8.14, shaftZ}, new double[]{.03, 1.75, 7.5},
                new double[]{0, 9.015, pivotZ}, Map.of("east", Map.of(), "west", Map.of())));
        cubes.add(cube(new double[]{-.875, 9.0, shaftZ}, new double[]{1.75, .03, 7.5},
                new double[]{0, 9.015, pivotZ}, Map.of("up", Map.of(), "down", Map.of())));
        cubes.add(cube(new double[]{-3.5, 5.525, featherZ}, new double[]{7, 7, .03},
                new double[]{0, 9.025, featherZ + .015},
                Map.of("north", Map.of(), "south", Map.of())));
        cubes.add(cube(new double[]{-2.55, 5.25, .75359}, new double[]{2, .03, 5},
                new double[]{-1.55, 5.265, 3.25359}, Map.of("up", Map.of(), "down", Map.of())));
        cubes.add(cube(new double[]{.55, 5.25, .75359}, new double[]{2, .03, 5},
                new double[]{1.55, 5.265, 3.25359}, Map.of("up", Map.of(), "down", Map.of())));
        return bytes(Map.of("minecraft:geometry", List.of(Map.of("bones", List.of(
                Map.of("name", "bone_basis", "cubes", cubes))))));
    }

    private static Map<String, Object> cube(double[] origin, double[] size, double[] pivot,
                                            Map<String, Object> uv) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("origin", coords(origin));
        data.put("size", coords(size));
        data.put("pivot", coords(pivot));
        data.put("rotation", List.of(0.0, 0.0, -45.0));
        data.put("uv", uv);
        return data;
    }

    private static List<Double> coords(double[] xyz) {
        return List.of(xyz[0], xyz[1], xyz[2]);
    }

    private static List<Object> cubes(byte[] bytes) {
        List<Object> geometries = list(object(BedrockAnimationBridge.Json.read(bytes)).get("minecraft:geometry"));
        List<Object> bones = list(object(geometries.get(0)).get("bones"));
        return list(object(bones.get(0)).get("cubes"));
    }

    private static double point(Map<String, Object> cube, String field, int axis) {
        return ((Number) list(cube.get(field)).get(axis)).doubleValue();
    }

    private static byte[] bytes(Object data) { return BedrockAnimationBridge.Json.bytes(data); }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object obj) { return (Map<String, Object>) obj; }
    @SuppressWarnings("unchecked")
    private static List<Object> list(Object obj) { return (List<Object>) obj; }
}
