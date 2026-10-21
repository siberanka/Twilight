package com.siberanka.twilight.compiler;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AnimatedAttachableUvRepairTest {
    private static final String SAFE = "generic_animated_furniture";
    private static final String RC = "render_controllers/" + SAFE + "_java_animation.json";
    private static final String ATTACH = "attachables/" + SAFE + ".json";
    private static final String GEOMETRY = "models/entity/geometry." + SAFE + ".geo.json";
    private static final String F0 = "textures/twilight/test_anim_0.png";
    private static final String F1 = "textures/twilight/test_anim_1.png";

    @Test
    void transfersAnimationToTransparentVisibleUvAndUsesWorldClock() throws Exception {
        Map<String, byte[]> pack = fixture(false);
        assertEquals(1, AnimatedAttachableUvRepair.apply(pack));
        JsonObject rc = json(pack, RC).getAsJsonObject("render_controllers").entrySet().iterator().next()
                .getValue().getAsJsonObject();
        assertEquals("Array.twilight_java_frames[math.mod(math.floor(q.time_stamp), 4)]",
                rc.getAsJsonArray("textures").get(0).getAsString());
        BufferedImage changed = PngImages.read(pack.get(F1));
        assertEquals(0xff38ddff, changed.getRGB(17, 1));
        assertEquals(0xff38ddff, changed.getRGB(8, 1));
        JsonObject report = json(pack, AnimatedAttachableUvRepair.REPORT);
        assertEquals(1, report.get("atlases_rebaked").getAsInt());
        assertEquals(1, report.get("clocks_fixed").getAsInt());
    }

    @Test
    void alreadyVisibleAnimationDoesNotGetMovedButClockStillRuns() throws Exception {
        Map<String, byte[]> pack = fixture(true);
        byte[] frame = pack.get(F1).clone();
        assertEquals(0, AnimatedAttachableUvRepair.apply(pack));
        assertArrayEquals(frame, pack.get(F1));
        assertEquals(1, json(pack, AnimatedAttachableUvRepair.REPORT).get("clocks_fixed").getAsInt());
    }

    @Test
    void doesNotChangeMissingOrNonAnimationAssets() {
        Map<String, byte[]> pack = new LinkedHashMap<>();
        pack.put(ATTACH, "{}".getBytes(StandardCharsets.UTF_8));
        assertEquals(0, AnimatedAttachableUvRepair.apply(pack));
        assertArrayEquals("{}".getBytes(StandardCharsets.UTF_8), pack.get(ATTACH));
    }

    private static Map<String, byte[]> fixture(boolean visible) throws Exception {
        Map<String, byte[]> pack = new LinkedHashMap<>();
        JsonObject description = new JsonObject();
        JsonObject textures = new JsonObject();
        textures.addProperty("java_anim_0", "textures/twilight/test_anim_0");
        textures.addProperty("java_anim_1", "textures/twilight/test_anim_1");
        description.add("textures", textures);
        JsonObject attach = new JsonObject();
        attach.add("description", description);
        JsonObject outer = new JsonObject();
        outer.add("minecraft:attachable", attach);
        put(pack, ATTACH, outer);

        JsonObject face = new JsonObject();
        JsonArray uv = new JsonArray();uv.add(16);uv.add(0);
        JsonArray size = new JsonArray();size.add(6);size.add(6);
        face.add("uv", uv);face.add("uv_size", size);
        JsonObject uvMap = new JsonObject();
        if (visible) {
            uv.set(0, new com.google.gson.JsonPrimitive(8));
            size.set(0, new com.google.gson.JsonPrimitive(4));
            size.set(1, new com.google.gson.JsonPrimitive(4));
        }
        uvMap.add("north", face);
        JsonObject cube = new JsonObject();cube.add("uv", uvMap);
        JsonArray cubes = new JsonArray();cubes.add(cube);
        JsonObject bone = new JsonObject();bone.add("cubes", cubes);
        JsonArray bones = new JsonArray();bones.add(bone);
        JsonObject geo = new JsonObject();geo.add("bones", bones);
        JsonArray geometries = new JsonArray();geometries.add(geo);
        JsonObject geometry = new JsonObject();geometry.add("minecraft:geometry", geometries);
        put(pack, GEOMETRY, geometry);

        JsonArray timeline = new JsonArray();
        timeline.add("Texture.java_anim_0");timeline.add("Texture.java_anim_0");
        timeline.add("Texture.java_anim_1");timeline.add("Texture.java_anim_1");
        JsonObject arr = new JsonObject();arr.add("Array.twilight_java_frames", timeline);
        JsonObject arrs = new JsonObject();arrs.add("textures", arr);
        JsonObject controller = new JsonObject();controller.add("arrays", arrs);
        JsonArray chosen = new JsonArray();
        chosen.add("Array.twilight_java_frames[math.mod(math.floor(q.anim_time * 20), 4)]");
        controller.add("textures", chosen);
        JsonObject controllers = new JsonObject();controllers.add("controller.render.twilight.test", controller);
        JsonObject document = new JsonObject();document.add("render_controllers", controllers);
        put(pack, RC, document);
        for (int i = 0; i < 2; i++) {
            BufferedImage image = new BufferedImage(24, 8, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 4; y++) for (int x = 8; x < 12; x++) {
                image.setRGB(x, y, i == 0 ? 0xff000000 : 0xff38ddff);
            }
            pack.put(i == 0 ? F0 : F1, TextureSet.png(image));
        }
        return pack;
    }

    private static void put(Map<String, byte[]> pack, String path, JsonObject object) {
        pack.put(path, object.toString().getBytes(StandardCharsets.UTF_8));
    }
    private static JsonObject json(Map<String, byte[]> pack, String path) {
        return JsonParser.parseString(new String(pack.get(path), StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
