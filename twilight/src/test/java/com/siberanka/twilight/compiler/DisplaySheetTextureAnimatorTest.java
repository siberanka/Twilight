package com.siberanka.twilight.compiler;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.siberanka.twilight.config.TwilightConfig;
import com.siberanka.twilight.source.ContentSource;
import com.siberanka.twilight.source.CustomItemDescriptor;

import javax.imageio.ImageIO;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.zip.ZipFile;

import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DisplaySheetTextureAnimatorTest {
    @TempDir Path root;
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

    @Test
    void geometryCoordinatesWinEvenWhenFirstFramePixelsDiffer() {
        // A blank Java frame can be repacked or recoloured by an unrelated renderer pass.
        // Only geometry, not byte-for-byte bitmap equality, identifies the displayed UVs.
        BufferedImage originalAtlas = colour(16, 16, 0x00000000);
        BufferedImage sheet = colour(64, 32, 0xff333333);
        JsonObject source = geometry("original", 0, 0);
        JsonObject placed = geometry("twp2_0", 27, 7);
        assertArrayEquals(new int[]{27, 7},
                DisplaySheetTextureAnimator.geometryOffset(source, placed, 2, originalAtlas, sheet));
    }

    @Test
    void compilingRealDisplaySheetsAnimatesTransparentDisplayOnlyAndOrdinaryModels() throws Exception {
        // Synthetic resource pack built from scratch; no third-party furniture assets are included.
        Path source = root.resolve("pack-source");
        write(source, "assets/example/models/item/transparent_display.json", cube("example:item/transparent_display"));
        write(source, "assets/example/models/item/ordinary_display.json", cube("example:item/ordinary_display"));
        animatedPng(source, "transparent_display", 2, true);
        animatedPng(source, "ordinary_display", 3, false);

        var items = List.of(
                new CustomItemDescriptor("ModelEngine", "minecraft:paper",
                        Optional.of("example:item/transparent_display"), OptionalInt.empty(), "Synthetic display bone"),
                new CustomItemDescriptor("test", "minecraft:stone",
                        Optional.of("example:item/ordinary_display"), OptionalInt.of(7431), "Synthetic furniture"));
        TwilightConfig config = new TwilightConfig(false, true, false, true, 40, 100, 10_000_000, 10_000,
                true, true, List.of(), "auto", false, false, 3);
        BuildResult built = new BedrockPackCompiler(root.resolve("compiled"), config)
                .build(List.of(new ContentSource("test", ContentSource.Kind.RESOURCE_PACK, source, 1)), items);
        try (ZipFile zip = new ZipFile(built.outputDirectory().resolve("pack.zip").toFile())) {
            var entry = zip.getEntry("twilight/display-animations-v3.json");
            assertNotNull(entry, "The v2 animation stage must leave a diagnostic report in every compiled pack");
            JsonObject report = JsonParser.parseString(new String(
                    zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals(2, report.get("repaired").getAsInt(), report.toString());
            assertTrue(zip.stream().anyMatch(e -> e.getName().startsWith("textures/twilight/display_animated/")
                    && e.getName().endsWith(".png")));
            JsonObject entity = JsonParser.parseString(new String(zip.getInputStream(
                    zip.getEntry("entity/twilight_display.entity.json")).readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject();
            assertTrue(entity.toString().contains("twilight_sheet_frame"));
            JsonObject controller = JsonParser.parseString(new String(zip.getInputStream(
                    zip.getEntry("render_controllers/twilight_display.json")).readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject();
            assertTrue(controller.toString().contains("twilight_sheet_frame"));
        }
    }

    private static String cube(String sprite) {
        return """
            {"textures":{"all":"%s"},"elements":[
                {"from":[2,2,2],"to":[14,14,14],"faces":{
                  "north":{"uv":[0,0,16,16],"texture":"#all"},
                  "south":{"uv":[16,0,0,16],"texture":"#all"},
                  "east":{"texture":"#all"},"west":{"texture":"#all"}}}]}
            """.formatted(sprite);
    }

    private static void write(Path root, String name, String content) throws Exception {
        Path target = root.resolve(name);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content, StandardCharsets.UTF_8);
    }

    private static void animatedPng(Path root, String id, int ticks, boolean firstTransparent) throws Exception {
        BufferedImage strip = new BufferedImage(16, 48, BufferedImage.TYPE_INT_ARGB);
        for (int frame = 0; frame < 3; frame++) {
            int color = firstTransparent && frame == 0 ? 0x00000000 :
                    (0xff000000 | ((frame + 2) * 0x223344));
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++)
                strip.setRGB(x, frame * 16 + y, color);
        }
        Path png = root.resolve("assets/example/textures/item/" + id + ".png");
        Files.createDirectories(png.getParent());
        assertTrue(ImageIO.write(strip, "png", png.toFile()));
        Files.writeString(png.resolveSibling(png.getFileName() + ".mcmeta"),
                "{\"animation\":{\"frametime\":" + ticks + "}}", StandardCharsets.UTF_8);
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
