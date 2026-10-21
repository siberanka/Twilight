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

class AnimatedDisplaySheetUvRepairTest {
    @Test
    void distinctAnimatedItemsOnSameSheetKeepOwnFramesAndTiming() throws Exception {
        Map<String, byte[]> pack = fixture(12, 4);
        addVariant(pack, "clock", 0, 0, 4, 4, 2, false);
        addVariant(pack, "chest", 1, 4, 4, 4, 4, false);
        // Another item shares the same sheet, and has no animation metadata.
        JsonObject index = json(pack, "twilight/display-index-v2.json");
        index.getAsJsonObject("variants").add("twilight:static", array(2, -1));
        put(pack, "twilight/display-index-v2.json", index);
        assertEquals(6, AnimatedDisplaySheetUvRepair.apply(pack));

        JsonObject entity = json(pack, "entity/twilight_display.entity.json")
                .getAsJsonObject("minecraft:client_entity").getAsJsonObject("description");
        JsonObject textures = entity.getAsJsonObject("textures");
        assertTrue(textures.has("twilight_uv_v0_f0"));
        assertTrue(textures.has("twilight_uv_v1_f3"));
        assertFalse(textures.has("twilight_uv_v2_f0"));

        JsonArray scripts = entity.getAsJsonObject("scripts").getAsJsonArray("pre_animation");
        assertTrue(scripts.toString().contains("v.var==0"));
        assertTrue(scripts.toString().contains("v.var==1"));
        assertTrue(scripts.toString().contains("q.life_time*20"));
        assertTrue(scripts.toString().contains("v.twilight_tick"));
        assertTrue(scripts.toString().contains("),2)"));
        assertTrue(scripts.toString().contains("),4)"));

        JsonObject controllers = json(pack, "render_controllers/twilight_display.json")
                .getAsJsonObject("render_controllers");
        for (String mode : new String[]{"controller.render.twilight.display", "controller.render.twilight.display_lit"}) {
            JsonObject controller = controllers.getAsJsonObject(mode);
            assertEquals("Array.textures[v.twilight_uv_frame]", controller.getAsJsonArray("textures").get(0).getAsString());
            assertEquals(7, controller.getAsJsonObject("arrays").getAsJsonObject("textures")
                    .getAsJsonArray("Array.textures").size());
        }
        BufferedImage clockFrame1 = PngImages.read(pack.get("textures/twilight/display_animated/twilight_uv_v0_f1.png"));
        assertEquals(0xff44cc00, clockFrame1.getRGB(0, 0));
        assertEquals(0xff444444, clockFrame1.getRGB(4, 0));
        assertEquals(0xff444444, clockFrame1.getRGB(8, 0));
        BufferedImage chestFrame3 = PngImages.read(pack.get("textures/twilight/display_animated/twilight_uv_v1_f3.png"));
        assertEquals(0xff444444, chestFrame3.getRGB(0, 0));
        assertEquals(0xff88cc00, chestFrame3.getRGB(4, 0));
        assertEquals(0xff444444, chestFrame3.getRGB(8, 0));
        assertEquals(0, AnimatedDisplaySheetUvRepair.apply(pack));
    }

    @Test
    void animatedPixelsOutsideVisibleJavaUvAreRebakedToCorrespondingBedrockFace() throws Exception {
        Map<String, byte[]> pack = fixture(12, 4);
        addVariant(pack, "hidden", 0, 0, 8, 4, 2, true);
        assertEquals(2, AnimatedDisplaySheetUvRepair.apply(pack));
        BufferedImage output = PngImages.read(pack.get("textures/twilight/display_animated/twilight_uv_v0_f1.png"));
        assertEquals(0xff44cc00, output.getRGB(0, 0));
        assertEquals(0xff444444, output.getRGB(4, 0));
    }

    @Test
    void reversedBedrockUvCopiesTheCorrespondingJavaPixelsInTheRightOrder() throws Exception {
        Map<String, byte[]> pack = fixture(12, 4);
        addVariant(pack, "rotated", 0, 4, 4, 4, 2, false);
        JsonObject geometry = json(pack, "models/entity/display.sheet0.geo.json");
        JsonObject face = geometry.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject()
                .getAsJsonArray("bones").get(0).getAsJsonObject().getAsJsonArray("cubes").get(0)
                .getAsJsonObject().getAsJsonObject("uv").getAsJsonObject("north");
        face.add("uv", array(8, 0));
        face.add("uv_size", array(-4, 4));
        put(pack, "models/entity/display.sheet0.geo.json", geometry);
        for (int frame = 0; frame < 2; frame++) {
            String name = "textures/twilight/rotated_frame_" + frame + ".png";
            BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++)
                image.setRGB(x, y, 0xff000000 | ((x + 1) << 16) | (frame << 8));
            pack.put(name, TextureSet.png(image));
        }
        assertEquals(2, AnimatedDisplaySheetUvRepair.apply(pack));
        BufferedImage sheet = PngImages.read(pack.get("textures/twilight/display_animated/twilight_uv_v0_f1.png"));
        assertEquals(0xff040100, sheet.getRGB(4, 0));
        assertEquals(0xff010100, sheet.getRGB(7, 0));
        assertEquals(0xff444444, sheet.getRGB(0, 0));
    }

    @Test
    void absentV2IndexNeverTouchesOriginalPreV2Pack() throws Exception {
        Map<String, byte[]> pack = fixture(12, 4);
        pack.remove("twilight/display-index-v2.json");
        Map<String, byte[]> original = new LinkedHashMap<>(pack);
        assertEquals(0, AnimatedDisplaySheetUvRepair.apply(pack));
        assertEquals(original.keySet(), pack.keySet());
    }

    private static Map<String, byte[]> fixture(int width, int height) throws Exception {
        Map<String, byte[]> pack = new LinkedHashMap<>();
        JsonObject index = new JsonObject();
        index.addProperty("format", 2);
        index.addProperty("parts", 64);
        index.add("variants", new JsonObject());
        put(pack, "twilight/display-index-v2.json", index);

        JsonObject description = new JsonObject();
        description.addProperty("identifier", "twilight:item_display");
        JsonObject textures = new JsonObject();
        textures.addProperty("s0", "textures/twilight/display_sheet0");
        description.add("textures", textures);
        JsonObject scripts = new JsonObject();
        JsonArray pre = new JsonArray();
        pre.add("v.var=math.floor(q.property('twilight:appearance')/9);v.part=v.var-math.floor(v.var/64)*64;");
        scripts.add("pre_animation", pre);
        description.add("scripts", scripts);
        JsonObject entity = new JsonObject();
        JsonObject clientEntity = new JsonObject();
        clientEntity.add("description", description);
        entity.add("minecraft:client_entity", clientEntity);
        put(pack, "entity/twilight_display.entity.json", entity);

        JsonObject controllers = new JsonObject();
        controllers.add("controller.render.twilight.display", controller());
        controllers.add("controller.render.twilight.display_lit", controller());
        JsonObject rc = new JsonObject();
        rc.add("render_controllers", controllers);
        put(pack, "render_controllers/twilight_display.json", rc);

        BufferedImage sheet = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) sheet.setRGB(x, y, 0xff444444);
        pack.put("textures/twilight/display_sheet0.png", TextureSet.png(sheet));
        return pack;
    }

    private static JsonObject controller() {
        JsonObject controller = new JsonObject();
        JsonArray frames = new JsonArray();
        frames.add("Texture.s0");
        JsonObject arrays = new JsonObject();
        JsonObject textures = new JsonObject();
        textures.add("Array.textures", frames);
        arrays.add("textures", textures);
        controller.add("arrays", arrays);
        JsonArray selectors = new JsonArray();
        selectors.add("Array.textures[math.floor(math.floor(q.property('twilight:appearance')/9)/64)]");
        controller.add("textures", selectors);
        return controller;
    }

    private static void addVariant(Map<String, byte[]> pack, String name, int ordinal, int offset, int width,
                                   int height, int count, boolean animatedElsewhere) throws Exception {
        JsonObject index = json(pack, "twilight/display-index-v2.json");
        index.getAsJsonObject("variants").add("twilight:" + name, array(ordinal, -1));
        put(pack, "twilight/display-index-v2.json", index);
        int faceWidth = animatedElsewhere ? 4 : width;
        // The held model samples the first 'faceWidth' pixels; the sheet moves that UV to 'offset'.
        JsonObject source = geometry("geometry.twilight." + name, 0, faceWidth, width, height, name);
        put(pack, "models/entity/geometry." + name + ".geo.json", source);

        // Add variant bones into one shared geometry sheet.
        String sheetPath = "models/entity/display.sheet0.geo.json";
        JsonObject sheet = pack.containsKey(sheetPath) ? json(pack, sheetPath) : geometry("geometry.twilight.display.sheet0", 0, 0, 12, 4, "empty");
        JsonArray bones = sheet.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones");
        if (!pack.containsKey(sheetPath)) bones.remove(0);
        JsonObject bone = source.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject()
                .getAsJsonArray("bones").get(0).getAsJsonObject().deepCopy();
        bone.addProperty("name", "twp" + ordinal + "_0");
        bone.getAsJsonArray("cubes").get(0).getAsJsonObject().getAsJsonObject("uv")
                .getAsJsonObject("north").getAsJsonArray("uv").set(0, new com.google.gson.JsonPrimitive(offset));
        bones.add(bone);
        put(pack, sheetPath, sheet);

        JsonObject attachDescription = new JsonObject();
        attachDescription.addProperty("identifier", "twilight:" + name);
        JsonObject attachTextures = new JsonObject();
        JsonArray sequence = new JsonArray();
        for (int frame = 0; frame < count; frame++) {
            String tex = "textures/twilight/" + name + "_frame_" + frame;
            attachTextures.addProperty("java_anim_" + frame, tex);
            BufferedImage png = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                if (!animatedElsewhere || x >= faceWidth) png.setRGB(x, y, 0xff00cc00 | ((frame + 1) * 0x220000));
            }
            pack.put(tex + ".png", TextureSet.png(png));
            sequence.add("Texture.java_anim_" + frame);
        }
        attachDescription.add("textures", attachTextures);
        JsonObject attachable = new JsonObject();
        attachable.add("description", attachDescription);
        JsonObject attach = new JsonObject();
        attach.add("minecraft:attachable", attachable);
        put(pack, "attachables/" + name + ".json", attach);

        JsonObject rc = new JsonObject();
        JsonObject def = new JsonObject();
        JsonObject arrays = new JsonObject();
        JsonObject textures = new JsonObject();
        textures.add("Array.twilight_java_frames", sequence);
        arrays.add("textures", textures);
        def.add("arrays", arrays);
        JsonArray select = new JsonArray();
        select.add("Array.twilight_java_frames[math.mod(math.floor(q.anim_time * 20), " + count + ")]");
        def.add("textures", select);
        JsonObject controllers = new JsonObject();
        controllers.add("controller.render.twilight." + name + "_java_animation", def);
        rc.add("render_controllers", controllers);
        put(pack, "render_controllers/" + name + "_java_animation.json", rc);
    }

    private static JsonObject geometry(String id, int uvX, int faceWidth, int width, int height, String name) {
        JsonObject desc = new JsonObject();
        desc.addProperty("identifier", id);
        desc.addProperty("texture_width", width);
        desc.addProperty("texture_height", height);
        JsonObject face = new JsonObject();
        JsonArray uv = new JsonArray();
        uv.add(uvX); uv.add(0);
        JsonArray size = new JsonArray();
        size.add(faceWidth); size.add(height);
        face.add("uv", uv);
        face.add("uv_size", size);
        JsonObject faces = new JsonObject();
        faces.add("north", face);
        JsonObject cube = new JsonObject();
        cube.add("uv", faces);
        JsonArray cubes = new JsonArray();
        cubes.add(cube);
        JsonObject bone = new JsonObject();
        bone.addProperty("name", name);
        bone.add("cubes", cubes);
        JsonArray bones = new JsonArray();
        bones.add(bone);
        JsonObject body = new JsonObject();
        body.add("description", desc);
        body.add("bones", bones);
        JsonArray geometries = new JsonArray();
        geometries.add(body);
        JsonObject outer = new JsonObject();
        outer.add("minecraft:geometry", geometries);
        return outer;
    }

    private static JsonArray array(int a, int b) {
        JsonArray result = new JsonArray(); result.add(a); result.add(b); return result;
    }
    private static JsonObject json(Map<String, byte[]> pack, String key) {
        return JsonParser.parseString(new String(pack.get(key), StandardCharsets.UTF_8)).getAsJsonObject();
    }
    private static void put(Map<String, byte[]> pack, String key, JsonObject object) {
        pack.put(key, object.toString().getBytes(StandardCharsets.UTF_8));
    }
}
