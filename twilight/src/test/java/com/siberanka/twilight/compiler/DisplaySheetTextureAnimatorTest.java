package com.siberanka.twilight.compiler;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DisplaySheetTextureAnimatorTest {
    @Test
    void azureChestUsesRealMcmetaEvenThoughFirstThreeFramesAreTransparent() throws Exception {
        BufferedImage strip;
        JsonObject meta;
        try (InputStream png = getClass().getResourceAsStream("/azureset-animation/animations_2.png");
             InputStreamReader json = new InputStreamReader(
                     getClass().getResourceAsStream("/azureset-animation/animations_2.png.mcmeta"))) {
            assertNotNull(png);
            strip = PngImages.read(png.readAllBytes());
            meta = JsonParser.parseReader(json).getAsJsonObject().getAsJsonObject("animation");
        }
        var sprites = DisplaySheetTextureAnimator.parseFrames("azureset:animations/animations_2", strip, meta);
        assertNotNull(sprites);
        assertEquals(30, sprites.frames().size());
        assertEquals(32, sprites.frames().getFirst().getWidth());
        assertEquals(0, sprites.frames().get(0).getRGB(3, 3) >>> 24);
        assertEquals(0, sprites.frames().get(4).getRGB(3, 3) >>> 24);
        boolean frameSixVisible = false;
        for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++)
            frameSixVisible |= (sprites.frames().get(6).getRGB(x, y) >>> 24) != 0;
        assertTrue(frameSixVisible, "Java animation starts at tick 6; no bitmap match of its first frame is possible");

        BufferedImage sheet = new BufferedImage(96, 32, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 32; y++) for (int x = 0; x < 96; x++) sheet.setRGB(x, y, 0xff151515);
        // Only the face at sheet x=48..64 samples the middle 16 pixels of the animated Java sprite.
        var window = new DisplaySheetTextureAnimator.Rect(48, 0, 64, 32);
        var target = new DisplaySheetTextureAnimator.Rect(40, 0, 72, 32);
        var clip = new DisplaySheetTextureAnimator.Clip(sprites, target, List.of(window));
        var plan = new DisplaySheetTextureAnimator.Plan("twilight:azure_chest", 3, sheet, List.of(clip), 30);
        BufferedImage before = DisplaySheetTextureAnimator.draw(plan, 0);
        BufferedImage after = DisplaySheetTextureAnimator.draw(plan, 12);
        assertEquals(0, before.getRGB(49, 0) >>> 24); // transparent animation frame
        assertEquals(0xff151515, before.getRGB(41, 0)); // no modification outside visible UV
        assertEquals(0xff151515, after.getRGB(41, 0));
        assertEquals(0xff151515, after.getRGB(10, 10));
        boolean changed = false;
        for (int y = 0; y < 32; y++) for (int x = 48; x < 64; x++)
            changed |= before.getRGB(x, y) != after.getRGB(x, y);
        assertTrue(changed, "The visible Azure Chest UV region must be updated after its transparent frames");
    }

    @Test
    void azureChestJavaModelReallyUsesTheAnimatedSpriteOnANorthFace() throws Exception {
        JsonObject model;
        try (InputStreamReader reader = new InputStreamReader(
                getClass().getResourceAsStream("/azureset-animation/chest.json"))) {
            model = JsonParser.parseReader(reader).getAsJsonObject();
        }
        assertEquals("azureset:animations/animations_2", model.getAsJsonObject("textures").get("1").getAsString());
        int linkedFaces = 0;
        for (var element : model.getAsJsonArray("elements")) {
            JsonObject faces = element.getAsJsonObject().getAsJsonObject("faces");
            if (faces == null) continue;
            for (var face : faces.entrySet()) {
                if (!face.getValue().getAsJsonObject().has("texture")
                        || !"#1".equals(face.getValue().getAsJsonObject().get("texture").getAsString())) continue;
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
