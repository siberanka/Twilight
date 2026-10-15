package com.siberanka.twilight.compiler;

import com.google.gson.*;
import com.siberanka.twilight.integration.display.DisplayPose;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class DisplayEntityResourcesTest {
    @Test void contextTransformsMirrorExplicitAndFallbackLeftContextsWithoutMirroringScale() {
        JsonObject display = JsonParser.parseString("""
                {"thirdperson_righthand":{"translation":[2,3,4],"rotation":[10,20,30],"scale":[1,2,3]},
                 "firstperson_righthand":{"translation":[8,8,8]},
                 "firstperson_lefthand":{"translation":[5,6,7],"rotation":[40,50,60],"scale":[4,5,6]},
                 "fixed":{"rotation":[0,25,21],"translation":[0,200,0],"scale":[9,1,1]}}
                """).getAsJsonObject();
        double[] values = DisplayEntityResources.contextValues(display);
        assertArrayEquals(new double[]{-2, 3, -4, 10, 20, 30, 1, 2, 3}, context(values, 1), "left falls back to right, mirrored");
        assertArrayEquals(new double[]{2, 3, -4, 10, -20, -30, 1, 2, 3}, context(values, 2));
        assertArrayEquals(new double[]{-5, 6, -7, 40, 50, 60, 4, 4, 4}, context(values, 3), "Java limits scale to 4");
        assertArrayEquals(new double[]{0, 80, 0, 0, -25, -21, 4, 1, 1}, context(values, 8), "Java's translation and scale limits");
        assertArrayEquals(new double[]{0, 0, 0, 0, 0, 0, 1, 1, 1}, context(values, 0), "NONE never transforms");
    }

    @Test void variantsShareSheetsTexturesAndAnIndexWithTheirTransforms() throws Exception {
        byte[] red = png(16, 16, 0xffff0000), blue = png(32, 8, 0xff0000ff);
        JsonObject fixed = JsonParser.parseString("{\"fixed\":{\"rotation\":[0,90,0]}}").getAsJsonObject();
        Map<String, byte[]> files = new LinkedHashMap<>();
        DisplayEntityResources resources = new DisplayEntityResources();
        JsonObject original = item();
        String before = original.toString();
        resources.add("twilight:first", original, red, new JsonObject());
        resources.add("twilight:second", item(), red, fixed);
        resources.add("twilight:third", item(), blue, new JsonObject());
        resources.finish(files);
        assertEquals(before, original.toString(), "the item's own geometry is not changed");

        JsonObject index = json(files, DisplayEntityResources.INDEX);
        assertEquals(2, index.get("format").getAsInt());
        JsonObject variants = index.getAsJsonObject("variants");
        assertEquals(0, variants.getAsJsonArray("twilight:first").get(0).getAsInt());
        assertEquals(-1, variants.getAsJsonArray("twilight:first").get(1).getAsInt(), "untransformed items send nothing");
        assertEquals(1, variants.getAsJsonArray("twilight:second").get(0).getAsInt());
        int transform = variants.getAsJsonArray("twilight:second").get(1).getAsInt();
        JsonArray values = index.getAsJsonArray("transforms").get(transform).getAsJsonArray();
        assertEquals(81, values.size());
        assertEquals(-90, values.get(8 * 9 + 4).getAsDouble(), "fixed context, rotation Y");
        assertEquals(2, variants.getAsJsonArray("twilight:third").get(0).getAsInt());

        assertTrue(files.containsKey("textures/twilight/display_sheet0.png"));
        assertFalse(files.containsKey("models/entity/display.sheet1.geo.json"), "three parts fit one sheet");
        JsonObject geometry = json(files, "models/entity/display.sheet0.geo.json").getAsJsonArray("minecraft:geometry")
                .get(0).getAsJsonObject();
        assertEquals(48, geometry.getAsJsonObject("description").get("texture_width").getAsInt());
        assertEquals(16, geometry.getAsJsonObject("description").get("texture_height").getAsInt(),
                "the shared red texture is placed once, the lower blue one beside it on the same shelf");
        JsonArray bones = geometry.getAsJsonArray("bones");
        Set<String> names = new HashSet<>();
        for (JsonElement value : bones) {
            JsonObject bone = value.getAsJsonObject();
            if (bone.has("parent")) assertTrue(names.contains(bone.get("parent").getAsString()), bone.toString());
            assertTrue(names.add(bone.get("name").getAsString()));
            assertFalse(bone.has("binding"));
        }
        assertEquals("item_flip", find(bones, "ctx_t").get("parent").getAsString(), "Java applies the item transform after the frame");
        assertEquals("rx", find(bones, "item_flip").get("parent").getAsString(), "Java adds the item frame after both display rotations");
        JsonObject cube = find(bones, "twp0_0").getAsJsonArray("cubes").get(0).getAsJsonObject();
        assertEquals(-6, cube.getAsJsonArray("origin").get(1).getAsInt(), "centered like Java's item renderer");
        assertEquals(0, cube.getAsJsonArray("pivot").get(1).getAsInt());
        JsonObject third = find(bones, "twp2_0").getAsJsonArray("cubes").get(0).getAsJsonObject();
        assertEquals(16, third.getAsJsonObject("uv").getAsJsonObject("north").getAsJsonArray("uv").get(0).getAsInt(),
                "UVs move with the texture's place in the sheet");
        assertEquals(0, cube.getAsJsonObject("uv").getAsJsonObject("north").getAsJsonArray("uv").get(0).getAsInt());

        String entity = new String(files.get("entity/twilight_display.entity.json"), StandardCharsets.UTF_8);
        Set<String> properties = new TreeSet<>();
        for (String file : List.of(entity, new String(files.get("animations/twilight_display.json"), StandardCharsets.UTF_8),
                new String(files.get("render_controllers/twilight_display.json"), StandardCharsets.UTF_8))) {
            var matcher = java.util.regex.Pattern.compile("q\\.property\\('([^']+)'\\)").matcher(file);
            while (matcher.find()) properties.add(matcher.group(1));
        }
        // One target pose, the context transform, timing, revision and the packed item/context: within Bedrock's 32.
        assertEquals(27, properties.size(), properties.toString());
        assertTrue(entity.contains("v.rev=-1"), "pre_animation must not read an uninitialized revision variable");
        JsonObject pose = json(files, "animations/twilight_display.json").getAsJsonObject("animations")
                .getAsJsonObject("animation.twilight.display_pose").getAsJsonObject("bones");
        assertEquals(180, pose.getAsJsonObject("item_flip").getAsJsonArray("rotation").get(1).getAsInt());
        assertEquals(0, pose.getAsJsonObject("yaw").getAsJsonArray("rotation").get(1).getAsInt(),
                "The actor already applies body yaw; the mesh must not apply it a second time");
        assertFalse(json(files, "entity/twilight_display.entity.json").toString().contains("display.variant"),
                "no per-item animations for items whose meshes share one transform");
    }

    @Test void sheetsHoldAtMostTheirPartCount() throws Exception {
        Map<String, byte[]> files = new LinkedHashMap<>();
        DisplayEntityResources resources = new DisplayEntityResources();
        byte[] texture = png(16, 16, 0xff00ff00);
        for (int i = 0; i <= DisplayEntityResources.PARTS; i++) resources.add("twilight:item" + i, item(), texture, new JsonObject());
        resources.finish(files);
        assertEquals(DisplayEntityResources.PARTS, json(files, DisplayEntityResources.INDEX).getAsJsonObject("variants")
                .getAsJsonArray("twilight:item" + DisplayEntityResources.PARTS).get(0).getAsInt());
        assertTrue(files.containsKey("models/entity/display.sheet1.geo.json"));
        JsonArray models = json(files, "render_controllers/twilight_display.json").getAsJsonObject("render_controllers")
                .getAsJsonObject("controller.render.twilight.display").getAsJsonObject("arrays")
                .getAsJsonObject("geometries").getAsJsonArray("Array.models");
        assertEquals(2, models.size());
    }

    @Test void compositeMeshesWithDifferentTransformsKeepStaticTransforms() throws Exception {
        JsonObject geometry = JsonParser.parseString("""
                {"minecraft:geometry":[{"description":{},"bones":[
                {"name":"bone_0","cubes":[]},{"name":"bone_1","cubes":[]}]}]}
                """).getAsJsonObject();
        JsonObject first = JsonParser.parseString("{\"fixed\":{\"rotation\":[0,25,21]}}").getAsJsonObject();
        JsonObject second = JsonParser.parseString("{\"fixed\":{\"rotation\":[0,-35,12]}}").getAsJsonObject();
        Map<String, byte[]> files = new HashMap<>();
        DisplayEntityResources resources = new DisplayEntityResources();
        resources.add("twilight:test", geometry, png(16, 16, 0xffffffff), List.of(first, second));
        resources.finish(files);
        assertEquals(-1, json(files, DisplayEntityResources.INDEX).getAsJsonObject("variants").getAsJsonArray("twilight:test")
                .get(1).getAsInt(), "the shared context transform stays identity");
        JsonObject bones = json(files, "animations/display.variant0.json").getAsJsonObject("animations")
                .getAsJsonObject("animation.twilight.display.variant0").getAsJsonObject("bones");
        MolangMath math = new MolangMath();
        assertEquals(-25, math.eval(bones.getAsJsonObject("twp0_0y").getAsJsonArray("rotation").get(1).getAsString()
                .replace("q.property('twilight:appearance')", "8")), 1e-9);
        assertEquals(35, math.eval(bones.getAsJsonObject("twp0_1y").getAsJsonArray("rotation").get(1).getAsString()
                .replace("q.property('twilight:appearance')", "8")), 1e-9);
        assertTrue(new String(files.get("entity/twilight_display.entity.json"), StandardCharsets.UTF_8).contains("v.var==0"));
    }

    /** The client half follows Java: a new pose starts from the pose shown at that moment. */
    @Test void clientInterpolatesLikeJavaFromThePoseItShows() {
        String script = String.join("", DisplayEntityResources.preAnimation());
        MolangMath math = new MolangMath();
        String initialize = initializeScript();
        DisplayPose p0 = pose(0, 0, 0, 0), p1 = pose(1, 2, 0, 90), p2 = pose(-1, 0, 3, -45);
        Map<String, Double> properties = new HashMap<>();
        math.variables.put("q.life_time", 0.0);
        math.run(initialize, Map.of());
        set(properties, p0, 0, 0, 1);
        frame(math, script, properties, 0.0);
        assertPose(p0, math);

        set(properties, p1, 1, 0, 3);
        frame(math, script, properties, 1.0);
        frame(math, script, properties, 1.5);
        assertPose(p0.interpolate(p1, 0.5), math);

        // A new target halfway: Java starts it from the pose it shows.
        DisplayPose shown = p0.interpolate(p1, 0.5);
        set(properties, p2, 1, 0, 5);
        frame(math, script, properties, 1.5);
        frame(math, script, properties, 2.0);
        assertPose(shown.interpolate(p2, 0.5), math);

        // Only the start moves (even revision): the same segment plays again after the delay.
        properties.put("twilight:delay", 0.5);
        properties.put("twilight:revision", 6.0);
        frame(math, script, properties, 3.0);
        frame(math, script, properties, 3.25);
        assertPose(shown, math);
        frame(math, script, properties, 4.0);
        assertPose(shown.interpolate(p2, 0.5), math);

        // A zero duration jumps.
        set(properties, p1, 0, 0, 7);
        frame(math, script, properties, 5.0);
        assertPose(p1, math);
    }

    private static String initializeScript() {
        Map<String, byte[]> files = new HashMap<>();
        DisplayEntityResources resources = new DisplayEntityResources();
        try {
            resources.add("twilight:x", item(), png(16, 16, 0xffffffff), new JsonObject());
            resources.finish(files);
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
        return json(files, "entity/twilight_display.entity.json").getAsJsonObject("minecraft:client_entity")
                .getAsJsonObject("description").getAsJsonObject("scripts").getAsJsonArray("initialize").get(0).getAsString();
    }

    private static DisplayPose pose(double x, double y, double z, double yawDegrees) {
        double half = Math.toRadians(yawDegrees) / 2;
        return new DisplayPose(new float[]{(float) x, (float) y, (float) z, 1, 1, 1,
                0, (float) Math.sin(half), 0, (float) Math.cos(half), 0, 0, 0, 1});
    }

    private static void set(Map<String, Double> properties, DisplayPose pose, double seconds, double delay, int revision) {
        for (int i = 0; i < DisplayPose.SIZE; i++) properties.put("twilight:b" + i, (double) pose.value(i));
        properties.put("twilight:seconds", seconds);
        properties.put("twilight:delay", delay);
        properties.put("twilight:revision", (double) revision);
        properties.put("twilight:appearance", 0.0);
    }

    private static void frame(MolangMath math, String script, Map<String, Double> properties, double time) {
        math.variables.put("q.life_time", time);
        math.run(script, properties);
    }

    private static void assertPose(DisplayPose expected, MolangMath math) {
        for (int i = 0; i < 3; i++) assertEquals(expected.value(i), math.variables.get("v.t" + i), 1e-4, "translation " + i);
        double[] shown = {math.variables.get("v.lx"), math.variables.get("v.ly"), math.variables.get("v.lz"), math.variables.get("v.lw")};
        double dot = 0;
        for (int i = 0; i < 4; i++) dot += shown[i] * expected.value(6 + i);
        assertEquals(1, Math.abs(dot), 1e-5, "left rotation");
    }

    private static double[] context(double[] values, int context) {
        return Arrays.copyOfRange(values, context * 9, context * 9 + 9);
    }

    private static JsonObject item() {
        return JsonParser.parseString("""
                {"format_version":"1.21.0","minecraft:geometry":[{"description":{"identifier":"geometry.test",
                "texture_width":16,"texture_height":16},
                "bones":[{"name":"bone","binding":"hand","pivot":[0,8,0],"cubes":[
                {"origin":[-8,2,-8],"size":[16,12,16],"pivot":[0,8,0],"uv":{"north":{"uv":[0,0],"uv_size":[16,16]}}}]}]}]}
                """).getAsJsonObject();
    }

    private static byte[] png(int width, int height, int argb) throws java.io.IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < width; x++) for (int y = 0; y < height; y++) image.setRGB(x, y, argb);
        return TextureSet.png(image);
    }

    private static JsonObject find(JsonArray bones, String name) {
        for (JsonElement bone : bones) if (bone.getAsJsonObject().get("name").getAsString().equals(name)) return bone.getAsJsonObject();
        throw new AssertionError("no bone " + name);
    }

    private static JsonObject json(Map<String, byte[]> files, String path) {
        return JsonParser.parseString(new String(files.get(path), StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
