package com.siberanka.twilight.compiler;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DisplaySheetTextureAnimatorTest {
    /** A generated sprite fixture: nothing is copied from or requires a third-party resource pack. */
    @Test
    void transparentLeadingFramesStillAnimateWithinVisibleUvWindow() {
        // 15 generated 32x32 frames, two ticks each. Initial frames are all transparent.
        BufferedImage strip = new BufferedImage(32, 32 * 15, BufferedImage.TYPE_INT_ARGB);
        for (int frame = 6; frame < 15; frame++) {
            int pixel = 0xff000000 | (frame * 0x010101);
            for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++)
                strip.setRGB(x, frame * 32 + y, pixel);
        }
        JsonObject meta = JsonParser.parseString("{\"frametime\":2}").getAsJsonObject();
        var sprites = DisplaySheetTextureAnimator.parseFrames("test:animated_fixture", strip, meta);
        assertNotNull(sprites);
        assertEquals(30, sprites.frames().size());
        assertEquals(32, sprites.frames().getFirst().getWidth());
        assertEquals(0, sprites.frames().get(0).getRGB(3, 3) >>> 24);
        assertEquals(0, sprites.frames().get(8).getRGB(3, 3) >>> 24);
        assertEquals(0xff060606, sprites.frames().get(12).getRGB(3, 3));

        BufferedImage sheet = colour(96, 32, 0xff151515);
        var window = new DisplaySheetTextureAnimator.Rect(48, 0, 64, 32);
        var target = new DisplaySheetTextureAnimator.Rect(40, 0, 72, 32);
        var clip = new DisplaySheetTextureAnimator.Clip(sprites, target, List.of(window));
        var plan = new DisplaySheetTextureAnimator.Plan("test:animated_furniture", 3, sheet, List.of(clip), 30);
        BufferedImage before = DisplaySheetTextureAnimator.draw(plan, 0);
        BufferedImage after = DisplaySheetTextureAnimator.draw(plan, 12);
        assertEquals(0, before.getRGB(49, 0) >>> 24);
        assertEquals(0xff151515, before.getRGB(41, 0));
        assertEquals(0xff151515, after.getRGB(41, 0));
        assertEquals(0xff151515, after.getRGB(10, 10));
        assertEquals(0xff060606, after.getRGB(49, 0));
        assertEquals(0xff060606, after.getRGB(63, 31));
    }

    @Test
    void modelSelectsCorrectAnimatedSpriteFromFaceTextureReference() {
        // Entire synthetic model, including sprite name and UVs, constructed in this test.
        JsonObject model = JsonParser.parseString("""
                {
                    "textures": {"0": "test:static", "1": "test:animated_fixture"},
                    "elements": [{
                        "faces": {
                            "north": {"texture": "#1", "uv": [1, 1, 15, 15]},
                            "south": {"texture": "#0", "uv": [0, 0, 16, 16]}
                        }
                    }]
                }
                """).getAsJsonObject();
        assertEquals("test:animated_fixture", model.getAsJsonObject("textures").get("1").getAsString());
        int linkedFaces = 0;
        for (var element : model.getAsJsonArray("elements")) {
            JsonObject faces = element.getAsJsonObject().getAsJsonObject("faces");
            for (var face : faces.entrySet()) {
                if (!"#1".equals(face.getValue().getAsJsonObject().get("texture").getAsString())) continue;
                assertEquals("north", face.getKey());
                JsonArray uv = face.getValue().getAsJsonObject().getAsJsonArray("uv");
                assertEquals(1.0, uv.get(0).getAsDouble());
                assertEquals(1.0, uv.get(1).getAsDouble());
                linkedFaces++;
            }
        }
        assertEquals(1, linkedFaces);
    }

    @Test
    void twoIndependentAnimationsOnOneSheetNeverOverwriteEachOther() {
        BufferedImage base = new BufferedImage(32, 8, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 8; y++) for (int x = 0; x < 32; x++) base.setRGB(x, y, 0xff555555);
        var a = new DisplaySheetTextureAnimator.AnimatedSprite("a", List.of(colour(8,8,0xffaabbcc), colour(8,8,0xff123456)));
        var b = new DisplaySheetTextureAnimator.AnimatedSprite("b", List.of(colour(8,8,0xff111111), colour(8,8,0xff222222), colour(8,8,0xff333333)));
        var first = new DisplaySheetTextureAnimator.Plan("first", 0, base, List.of(
                new DisplaySheetTextureAnimator.Clip(a, new DisplaySheetTextureAnimator.Rect(0,0,8,8), List.of(new DisplaySheetTextureAnimator.Rect(0,0,8,8)))), 2);
        var second = new DisplaySheetTextureAnimator.Plan("second", 1, base, List.of(
                new DisplaySheetTextureAnimator.Clip(b, new DisplaySheetTextureAnimator.Rect(16,0,24,8), List.of(new DisplaySheetTextureAnimator.Rect(16,0,24,8)))), 3);
        assertEquals(0xff123456, DisplaySheetTextureAnimator.draw(first, 1).getRGB(2, 2));
        assertEquals(0xff555555, DisplaySheetTextureAnimator.draw(first, 1).getRGB(17, 2));
        assertEquals(0xff333333, DisplaySheetTextureAnimator.draw(second, 2).getRGB(17, 2));
        assertEquals(0xff555555, DisplaySheetTextureAnimator.draw(second, 2).getRGB(2, 2));
    }

    @Test
    void modelGeometryProvesAtlasPositionWithoutBitmapSearching() {
        BufferedImage source = colour(16, 16, 0x00000000);
        BufferedImage sheet = colour(48, 32, 0xff111111);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) sheet.setRGB(x + 19, y + 9, 0);
        JsonObject original = geometry("original", 0, 0);
        JsonObject moved = geometry("twp0_0", 19, 9);
        assertArrayEquals(new int[]{19, 9}, DisplaySheetTextureAnimator.geometryOffset(original, moved, 0, source, sheet));
    }

    private static JsonObject geometry(String name, int u, int v) {
        JsonObject face = new JsonObject();
        JsonArray pos = new JsonArray(); pos.add(u); pos.add(v);
        JsonArray size = new JsonArray(); size.add(16); size.add(16);
        face.add("uv", pos); face.add("uv_size", size);
        JsonObject faces = new JsonObject(); faces.add("north", face);
        JsonObject cube = new JsonObject(); cube.add("uv", faces);
        JsonArray cubes = new JsonArray(); cubes.add(cube);
        JsonObject bone = new JsonObject(); bone.addProperty("name", name); bone.add("cubes", cubes);
        JsonArray bones = new JsonArray(); bones.add(bone);
        JsonObject body = new JsonObject(); body.add("bones", bones);
        JsonArray geometries = new JsonArray(); geometries.add(body);
        JsonObject root = new JsonObject(); root.add("minecraft:geometry", geometries);
        return root;
    }

    private static BufferedImage colour(int width, int height, int argb) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) image.setRGB(x, y, argb);
        return image;
    }
}
