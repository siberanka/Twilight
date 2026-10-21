package com.siberanka.twilight.compiler;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Uses an authored synthetic bow and atlas; no third-party pack assets. */
class BowStringDrawDirectionTest {
    private static final String SAFE = "synthetic_bow";
    private static final String ID = "twilight:" + SAFE;
    private static final String DEPTH = "twilight/3d-bow-draw-depth-repair.json";
    private static final String DRAW = "twilight/3d-bow-arrow-string-repair.json";
    private static final String IDLE = "twilight/3d-bow-idle-string-uv-repair.json";
    private static final String TEXTURE = "textures/twilight/" + SAFE + ".png";
    private static final int STRING = 8;

    @Test
    void fullRepairChainKeepsIdleAndDrawStringAtTheNockWithoutTurningTheArrow() throws Exception {
        Map<String, byte[]> pack = fixture();
        Map<String, byte[]> original = new LinkedHashMap<>(pack);
        applyChain(pack);
        assertPhysicalDraw(pack);
        // Restoring the converted UVs must not alter a pose, texture, arrow UV,
        // cube rotation, bow body or the arrow's authored translation.
        for (int state = 0; state < 4; state++) {
            assertEquals(json(original, geometryPath(state)), json(pack, geometryPath(state)));
        }
        assertOtherAssetsUnchanged(original, pack);
        assertEquals(2, json(pack, DRAW).get("version").getAsInt());
        assertIdempotent(pack);
    }

    @Test
    void migratesV1StringInIdleAndAllDrawStatesWithoutReversingProjectileAgain() throws Exception {
        Map<String, byte[]> pack = fixture();
        Map<String, byte[]> original = new LinkedHashMap<>(pack);
        // This is the v1 output: the projectile is correct but every string
        // face was horizontally flipped, including idle by the separate pass.
        for (int state = 0; state < 4; state++) {
            JsonObject root = json(pack, geometryPath(state));
            JsonObject string = cubes(root).get(STRING).getAsJsonObject();
            for (String side : List.of("west", "east")) {
                JsonObject face = string.getAsJsonObject("uv").getAsJsonObject(side);
                double u = face.getAsJsonArray("uv").get(0).getAsDouble();
                double width = face.getAsJsonArray("uv_size").get(0).getAsDouble();
                face.add("uv", array(u + width, 0));
                face.add("uv_size", array(-width, 70));
            }
            put(pack, geometryPath(state), root);
        }
        for (String marker : List.of(DEPTH, DRAW, IDLE)) {
            put(pack, marker, parse("{\"version\":1,\"applied_identifiers\":[\"" + ID + "\"]}"));
        }
        // Establish the reported bug: the string moves toward the tip (+Z),
        // although the projectile itself draws backwards (-Z).
        assertTrue(stringZ(pack, 3, "west") > stringZ(pack, 1, "west"));
        assertNotEquals(arrowOrigin(pack, 1), stringZ(pack, 0, "west"));
        applyChain(pack);
        assertPhysicalDraw(pack);
        for (int state = 0; state < 4; state++) {
            assertEquals(json(original, geometryPath(state)), json(pack, geometryPath(state)));
        }
        assertOtherAssetsUnchanged(original, pack);
        assertEquals(2, json(pack, IDLE).get("version").getAsInt());
        assertIdempotent(pack);
    }

    @Test
    void ignoresAnUnrelatedBowWithoutTheKnownBasisAndDepthRepair() throws Exception {
        Map<String, byte[]> pack = fixture();
        pack.remove("animations/" + SAFE + "_state_0.animation.json");
        Map<String, byte[]> original = new LinkedHashMap<>(pack);
        applyChain(pack);
        assertEquals(original.keySet(), pack.keySet());
        original.forEach((path, bytes) -> assertArrayEquals(bytes, pack.get(path), path));
    }

    private static void assertPhysicalDraw(Map<String, byte[]> pack) throws Exception {
        double previous = stringZ(pack, 1, "west");
        assertEquals(previous, stringZ(pack, 0, "west"), 1e-9, "idle must join the first draw state");
        for (int state = 1; state < 4; state++) {
            double string = stringZ(pack, state, "west");
            assertEquals(string, stringZ(pack, state, "east"), 1e-9, "both faces must agree");
            assertEquals(arrowOrigin(pack, state), string, 1e-9, "string must meet the nock");
            assertEquals(14, arrowTipZ(pack, state) - string, 1e-9, "tip direction must stay +Z");
            if (state > 1) assertTrue(string < previous, "draw must move away from the tip");
            previous = string;
        }
    }

    private static double stringZ(Map<String, byte[]> pack, int state, String side) throws Exception {
        JsonObject cube = cubes(json(pack, geometryPath(state))).get(STRING).getAsJsonObject();
        JsonObject face = cube.getAsJsonObject("uv").getAsJsonObject(side);
        double u = face.getAsJsonArray("uv").get(0).getAsDouble();
        double width = face.getAsJsonArray("uv_size").get(0).getAsDouble();
        double z = cube.getAsJsonArray("origin").get(2).getAsDouble();
        double depth = cube.getAsJsonArray("size").get(2).getAsDouble();
        BufferedImage atlas = PngImages.read(pack.get(TEXTURE));
        double sum = 0;
        int pixels = 0;
        for (int x = (int) Math.min(u, u + width); x < Math.max(u, u + width); x++) {
            if ((atlas.getRGB(x, 35) >>> 24) == 0) continue;
            double t = (x + 0.5 - u) / width;
            // Bedrock west: UV U runs minZ -> maxZ; east runs maxZ -> minZ.
            sum += z + depth * (side.equals("west") ? t : 1 - t);
            pixels++;
        }
        assertEquals(2, pixels);
        return sum / pixels;
    }

    private static double arrowOrigin(Map<String, byte[]> pack, int state) {
        return cubes(json(pack, geometryPath(state))).get(STRING + 1).getAsJsonObject()
                .getAsJsonArray("origin").get(2).getAsDouble();
    }

    private static double arrowTipZ(Map<String, byte[]> pack, int state) {
        JsonObject arrow = cubes(json(pack, geometryPath(state))).get(STRING + 1).getAsJsonObject();
        JsonObject up = arrow.getAsJsonObject("uv").getAsJsonObject("up");
        double v = up.getAsJsonArray("uv").get(1).getAsDouble();
        double height = up.getAsJsonArray("uv_size").get(1).getAsDouble();
        // Top-face UVs begin at maxZ. The synthetic arrow points at texture V=56.
        return arrowOrigin(pack, state) + arrow.getAsJsonArray("size").get(2).getAsDouble()
                * (1 - (56 - v) / height);
    }

    private static void applyChain(Map<String, byte[]> pack) {
        Reverse3DBowDrawDepthRepair.apply(pack);
        BowArrowAndStringDrawRepair.apply(pack);
        BowIdleStringUvRepair.apply(pack);
    }

    private static void assertIdempotent(Map<String, byte[]> pack) {
        Map<String, byte[]> previous = new LinkedHashMap<>(pack);
        applyChain(pack);
        assertEquals(previous.keySet(), pack.keySet());
        previous.forEach((path, bytes) -> assertArrayEquals(bytes, pack.get(path), path));
    }

    private static void assertOtherAssetsUnchanged(Map<String, byte[]> before, Map<String, byte[]> after) {
        before.forEach((path, bytes) -> {
            if (!path.startsWith("models/entity/geometry." + SAFE)) {
                assertArrayEquals(bytes, after.get(path), path);
            }
        });
    }

    private static Map<String, byte[]> fixture() throws Exception {
        Map<String, byte[]> pack = new LinkedHashMap<>();
        put(pack, "twilight/geyser_item_mappings.json", parse("""
                {"items":{"minecraft:bow":[{"bedrock_identifier":"twilight:synthetic_bow"}]}}
                """));
        JsonObject geometry = new JsonObject(), animations = new JsonObject();
        BufferedImage atlas = new BufferedImage(288, 96, BufferedImage.TYPE_INT_ARGB);
        for (int state = 0; state < 4; state++) {
            geometry.addProperty("state_" + state, "geometry.twilight." + SAFE + "_state_" + state);
            String animationId = "animation.twilight." + SAFE + "_state_" + state + ".first_person_right";
            animations.addProperty("state_" + state + "_first_person_right", animationId);
            JsonObject animation = new JsonObject();
            animation.add(animationId, parse("{\"loop\":true,\"bones\":{\"bone_basis\":{\"rotation\":[0,180,0]}}}"));
            JsonObject animationRoot = new JsonObject(); animationRoot.add("animations", animation);
            put(pack, "animations/" + SAFE + "_state_" + state + ".animation.json", animationRoot);
            JsonArray cubes = new JsonArray();
            for (int i = 0; i < STRING; i++) cubes.add(parse("{\"origin\":[-1,0,0],\"size\":[2,2,2]}"));
            int offset = Math.max(0, state - 1) * 96;
            int stringPixel = new int[]{0, 0, 12, 22}[state];
            for (int y = 0; y < 70; y++) for (int x = 0; x < 2; x++) {
                atlas.setRGB(offset + stringPixel + x, y, 0xff40cfff);
            }
            JsonObject string = parse("""
                    {"origin":[-0.015,-0.75,-9.5],"size":[0.03,17.5,6],
                     "pivot":[-0.015,0,3],"rotation":[0,0,0]}
                    """);
            JsonObject uv = new JsonObject();
            uv.add("west", face(offset + 24, 0, -24, 70));
            uv.add("east", face(offset, 0, 24, 70));
            string.add("uv", uv); cubes.add(string);
            if (state > 0) for (int rotation : new int[]{-45, 45}) {
                double z = new double[]{0, -3.75, -6.75, -9.25}[state];
                JsonObject arrow = new JsonObject();
                arrow.add("origin", array(-1.125, 7.985, z));
                arrow.add("size", array(2.25, 0.03, 14));
                arrow.add("pivot", array(0, 8, z + 7));
                arrow.add("rotation", array(0, 0, rotation));
                JsonObject arrowUv = new JsonObject();
                arrowUv.add("up", face(offset + 96, 56, -9, -56));
                arrowUv.add("down", face(offset + 96, 0, -9, 56));
                arrow.add("uv", arrowUv); cubes.add(arrow);
            }
            JsonObject description = new JsonObject();
            description.addProperty("identifier", "geometry.twilight." + SAFE + "_state_" + state);
            description.addProperty("texture_width", 288); description.addProperty("texture_height", 96);
            JsonObject bone = new JsonObject(); bone.addProperty("name", "bone_basis"); bone.add("cubes", cubes);
            JsonArray bones = new JsonArray(); bones.add(bone);
            JsonObject model = new JsonObject(); model.add("description", description); model.add("bones", bones);
            JsonArray models = new JsonArray(); models.add(model);
            JsonObject root = new JsonObject(); root.add("minecraft:geometry", models);
            put(pack, geometryPath(state), root);
        }
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(atlas, "png", png);
        pack.put(TEXTURE, png.toByteArray());
        JsonObject description = new JsonObject(); description.addProperty("identifier", ID);
        description.add("geometry", geometry); description.add("animations", animations);
        JsonObject attach = new JsonObject(); attach.add("description", description);
        JsonObject root = new JsonObject(); root.add("minecraft:attachable", attach);
        put(pack, "attachables/" + SAFE + ".json", root);
        pack.put("models/entity/geometry.crossbow.geo.json", "crossbow untouched".getBytes(StandardCharsets.UTF_8));
        return pack;
    }

    private static JsonObject face(double u, double v, double w, double h) {
        JsonObject face = new JsonObject(); face.add("uv", array(u, v)); face.add("uv_size", array(w, h)); return face;
    }

    private static JsonArray array(double... values) {
        JsonArray array = new JsonArray(); for (double value : values) array.add(value); return array;
    }

    private static JsonArray cubes(JsonObject root) {
        return root.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject()
                .getAsJsonArray("bones").get(0).getAsJsonObject().getAsJsonArray("cubes");
    }

    private static String geometryPath(int state) {
        return "models/entity/geometry." + SAFE + "_state_" + state + ".geo.json";
    }

    private static JsonObject json(Map<String, byte[]> pack, String path) {
        return parse(new String(pack.get(path), StandardCharsets.UTF_8));
    }

    private static JsonObject parse(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
    private static void put(Map<String, byte[]> pack, String path, JsonObject json) {
        pack.put(path, json.toString().getBytes(StandardCharsets.UTF_8));
    }
}
