package com.siberanka.twilight.compiler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Keeps the visible 3D crossbow projectile together throughout drawback and
 * in the independent arrow/rocket charged models.
 *
 * <p>The projectile has two crossed shaft planes plus a flat fletching sprite.
 * The sprite is the movement reference. In particular, the two shaft planes are
 * NOT bowstring geometry: reversing their Z motion sends the bolt forward while
 * its fletching moves backward. The actual bowstring and weapon poses are never
 * changed here.</p>
 *
 * <p>Only known, unambiguous three-part projectiles are touched. Arbitrary
 * models, 2D crossbows, other weapons and animation poses are left untouched.</p>
 */
public final class Crossbow3DProjectileAlignmentRepair {
    private static final String MAPPINGS = "twilight/geyser_item_mappings.json";
    private static final String OBSOLETE_MARKER = "twilight/3d-crossbow-string-direction-repair.json";
    private static final double EPS = 0.0001;

    private Crossbow3DProjectileAlignmentRepair() {}

    public static int apply(Map<String, byte[]> packFiles) {
        if (packFiles == null || packFiles.isEmpty()) return 0;
        byte[] mappingBytes = packFiles.get(MAPPINGS);
        if (mappingBytes == null) return 0;
        int changed = 0;
        try {
            Map<String, Object> items = object(object(BedrockAnimationBridge.Json.read(mappingBytes)).get("items"));
            List<Object> mappings = list(items.get("minecraft:crossbow"));
            for (Object raw : mappings) {
                Map<String, Object> mapping = object(raw);
                String identifier = string(mapping.get("bedrock_identifier"));
                if (identifier == null || !identifier.startsWith("twilight:")) continue;
                String suffix = identifier.substring(identifier.indexOf(':') + 1);
                String base = "models/entity/geometry." + suffix + "_state_";
                try {
                    Geometry[] states = new Geometry[4];
                    for (int state = 1; state <= 3; state++) {
                        byte[] bytes = packFiles.get(base + state + ".geo.json");
                        if (bytes == null) break;
                        states[state] = Geometry.read(bytes);
                    }
                    if (states[1] == null || states[2] == null || states[3] == null) continue;
                    Projectile reference = Projectile.identify(states[1]);
                    if (reference == null) continue;
                    // The arrow's feather actually travels rearward in this model.
                    // If it does not, do not guess a direction for an unknown asset.
                    Projectile halfway = reference.match(states[2]);
                    Projectile fullyDrawn = reference.match(states[3]);
                    if (halfway == null || fullyDrawn == null) continue;
                    double halfDelta = halfway.featherZ() - reference.featherZ();
                    double fullDelta = fullyDrawn.featherZ() - reference.featherZ();
                    if (!(halfDelta < -EPS && fullDelta < halfDelta - EPS)) continue;
                    for (int state = 2; state <= 3; state++) {
                        Projectile current = state == 2 ? halfway : fullyDrawn;
                        if (align(reference, current)) {
                            packFiles.put(base + state + ".geo.json", states[state].bytes());
                            changed++;
                        }
                    }
                    for (Object rawCharged : mappings) {
                        Map<String, Object> chargedMapping = object(rawCharged);
                        if (!Objects.equals(mapping.get("type"), chargedMapping.get("type")) ||
                                !Objects.equals(mapping.get("custom_model_data"), chargedMapping.get("custom_model_data")) ||
                                !Objects.equals(mapping.get("item_model"), chargedMapping.get("item_model"))) continue;
                        Map<String, Object> predicate = object(chargedMapping.get("predicate"));
                        if (!"charge_type".equals(predicate.get("property")) ||
                                !("arrow".equals(predicate.get("value")) ||
                                        "rocket".equals(predicate.get("value")))) continue;
                        String chargedId = string(chargedMapping.get("bedrock_identifier"));
                        if (chargedId == null || !chargedId.startsWith("twilight:")) continue;
                        String chargedSuffix = chargedId.substring(chargedId.indexOf(':') + 1);
                        String path = "models/entity/geometry." + chargedSuffix + ".geo.json";
                        byte[] bytes = packFiles.get(path);
                        if (bytes == null) continue;
                        Geometry charged = Geometry.read(bytes);
                        Projectile armedProjectile = reference.match(charged);
                        if (armedProjectile == null ||
                                Math.abs(armedProjectile.featherZ() - fullyDrawn.featherZ()) > EPS) continue;
                        if (align(reference, armedProjectile)) {
                            packFiles.put(path, charged.bytes());
                            changed++;
                        }
                    }
                } catch (RuntimeException invalidModel) {
                    // An unfamiliar custom model must not interrupt pack compilation.
                }
            }
        } catch (RuntimeException invalidMappings) {
            return 0;
        }
        // The old marker falsely described the two projectile planes as string.
        // It must not remain in a pack that no longer uses that repair.
        if (packFiles.remove(OBSOLETE_MARKER) != null) changed++;
        return changed;
    }

    private static boolean align(Projectile reference, Projectile current) {
        double delta = current.featherZ() - reference.featherZ();
        boolean changed = false;
        for (int i = 0; i < 2; i++) {
            Map<String, Object> original = reference.shafts.get(i);
            Map<String, Object> actual = current.shafts.get(i);
            changed |= setZ(actual, "origin", vector(original, "origin", 2) + delta);
            changed |= setZ(actual, "pivot", vector(original, "pivot", 2) + delta);
        }
        return changed;
    }

    private static boolean setZ(Map<String, Object> cube, String field, double z) {
        List<Object> original = list(cube.get(field));
        if (!vec3(original) || !Double.isFinite(z)) return false;
        if (Math.abs(number(original.get(2)) - z) < EPS) return false;
        // Avoid a 1e-15 floating-point tail introduced by subtracting two
        // serialized decimal coordinates (e.g. 1.2500000000000009).
        double rounded = Math.rint(z * 1_000_000.0) / 1_000_000.0;
        if (Math.abs(rounded - z) < 1e-9) z = rounded;
        List<Object> coordinates = new ArrayList<>(original);
        coordinates.set(2, z);
        cube.put(field, coordinates);
        return true;
    }

    private record Geometry(Map<String, Object> document, List<Map<String, Object>> cubes) {
        static Geometry read(byte[] bytes) {
            Map<String, Object> document = object(BedrockAnimationBridge.Json.read(bytes));
            List<Object> models = list(document.get("minecraft:geometry"));
            if (models.size() != 1) return null;
            for (Object raw : list(object(models.get(0)).get("bones"))) {
                Map<String, Object> bone = object(raw);
                if ("bone_basis".equals(bone.get("name"))) {
                    List<Map<String, Object>> cubes = new ArrayList<>();
                    for (Object cube : list(bone.get("cubes"))) cubes.add(object(cube));
                    return new Geometry(document, cubes);
                }
            }
            return null;
        }
        byte[] bytes() { return BedrockAnimationBridge.Json.bytes(document); }
    }

    private record Projectile(List<Map<String, Object>> shafts, Map<String, Object> feather) {
        static Projectile identify(Geometry state) {
            if (state == null) return null;
            // Two perpendicular faces forming the bolt's shaft, centered on the
            // projectile sprite. The bowstring segments sit off-center and are
            // deliberately excluded.
            for (int featherIndex = 0; featherIndex < state.cubes.size(); featherIndex++) {
                Map<String, Object> feather = state.cubes.get(featherIndex);
                if (!fletching(feather)) continue;
                int vertical = -1, horizontal = -1;
                double centerX = center(feather, 0);
                for (int i = 0; i < state.cubes.size(); i++) {
                    Map<String, Object> shaft = state.cubes.get(i);
                    if (!shaftPlane(shaft) || Math.abs(center(shaft, 0) - centerX) > .25) continue;
                    List<Object> size = list(shaft.get("size"));
                    if (number(size.get(0)) <= .06 && number(size.get(1)) >= 1) {
                        if (vertical != -1) return null;
                        vertical = i;
                    } else if (number(size.get(1)) <= .06 && number(size.get(0)) >= 1) {
                        if (horizontal != -1) return null;
                        horizontal = i;
                    }
                }
                if (vertical >= 0 && horizontal >= 0 &&
                        Math.abs(vector(state.cubes.get(vertical), "origin", 2) -
                                vector(state.cubes.get(horizontal), "origin", 2)) < EPS) {
                    return new Projectile(List.of(state.cubes.get(vertical), state.cubes.get(horizontal)), feather);
                }
            }
            return null;
        }

        Projectile match(Geometry target) {
            if (target == null) return null;
            List<Map<String, Object>> matches = new ArrayList<>();
            for (Map<String, Object> source : shafts) {
                Map<String, Object> match = null;
                for (Map<String, Object> candidate : target.cubes) {
                    if (!shaftPlane(candidate) || !sameAppearance(source, candidate)) continue;
                    if (match != null) return null;
                    match = candidate;
                }
                if (match == null || matches.contains(match)) return null;
                matches.add(match);
            }
            Map<String, Object> matchedFeather = null;
            for (Map<String, Object> candidate : target.cubes) {
                if (!fletching(candidate) || !sameAppearance(feather, candidate)) continue;
                if (matchedFeather != null) return null;
                matchedFeather = candidate;
            }
            return matchedFeather == null ? null : new Projectile(matches, matchedFeather);
        }
        double featherZ() { return vector(feather, "origin", 2); }
    }

    private static boolean sameAppearance(Map<String, Object> a, Map<String, Object> b) {
        return sameVector(a, b, "size") && sameVector(a, b, "rotation") &&
                sameXY(a, b, "origin") && sameXY(a, b, "pivot") &&
                object(a.get("uv")).keySet().equals(object(b.get("uv")).keySet());
    }

    private static boolean sameXY(Map<String, Object> a, Map<String, Object> b, String field) {
        return Math.abs(vector(a, field, 0) - vector(b, field, 0)) < EPS &&
                Math.abs(vector(a, field, 1) - vector(b, field, 1)) < EPS;
    }
    private static boolean sameVector(Map<String, Object> a, Map<String, Object> b, String field) {
        List<Object> x = list(a.get(field)), y = list(b.get(field));
        if (x.size() != y.size()) return false;
        for (int i = 0; i < x.size(); i++) {
            if (Math.abs(number(x.get(i)) - number(y.get(i))) > EPS) return false;
        }
        return true;
    }
    private static boolean fletching(Map<String, Object> cube) {
        List<Object> dimensions = list(cube.get("size"));
        Map<String, Object> uv = object(cube.get("uv"));
        return vec3(dimensions) && vec3(list(cube.get("origin"))) &&
                number(dimensions.get(0)) >= 4 && number(dimensions.get(1)) >= 4 &&
                number(dimensions.get(2)) > 0 && number(dimensions.get(2)) < .08 &&
                uv.containsKey("north") && uv.containsKey("south");
    }
    private static boolean shaftPlane(Map<String, Object> cube) {
        List<Object> dimensions = list(cube.get("size"));
        Map<String, Object> uv = object(cube.get("uv"));
        if (!vec3(dimensions) || !vec3(list(cube.get("origin"))) ||
                !vec3(list(cube.get("pivot"))) || number(dimensions.get(2)) < 5) return false;
        boolean vertical = number(dimensions.get(0)) > 0 && number(dimensions.get(0)) <= .06 &&
                number(dimensions.get(1)) >= 1 && uv.containsKey("east") && uv.containsKey("west");
        boolean horizontal = number(dimensions.get(1)) > 0 && number(dimensions.get(1)) <= .06 &&
                number(dimensions.get(0)) >= 1 && uv.containsKey("up") && uv.containsKey("down");
        return vertical || horizontal;
    }
    private static double center(Map<String, Object> cube, int axis) {
        return vector(cube, "origin", axis) + vector(cube, "size", axis) / 2.0;
    }
    private static double vector(Map<String, Object> cube, String key, int axis) {
        List<Object> values = list(cube.get(key));
        return values.size() == 3 ? number(values.get(axis)) : Double.NaN;
    }
    private static boolean vec3(List<Object> v) {
        return v.size() == 3 && v.stream().allMatch(Number.class::isInstance);
    }
    private static double number(Object value) {
        return value instanceof Number n ? n.doubleValue() : Double.NaN;
    }
    private static String string(Object value) { return value instanceof String s ? s : null; }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
    }
    @SuppressWarnings("unchecked")
    private static List<Object> list(Object value) {
        return value instanceof List<?> ? (List<Object>) value : List.of();
    }
}
