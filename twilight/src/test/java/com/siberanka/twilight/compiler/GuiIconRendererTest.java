package com.siberanka.twilight.compiler;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.siberanka.twilight.config.TwilightConfig;
import com.siberanka.twilight.source.ContentSource;
import com.siberanka.twilight.source.CustomItemDescriptor;
import com.siberanka.twilight.source.ResourceIndex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

class GuiIconRendererTest {
    @TempDir Path root;

    @Test
    void compositeKeepsChildGuiTransformsAndSharesDepthAcrossChildren() throws Exception {
        textures();
        write("assets/demo/models/red_part.json", "{\"gui_light\":\"front\",\"display\":{\"gui\":{\"translation\":[-4,0,2]}},\"elements\":["
                + plane(6,6,10,10,8,"red") + "]}");
        write("assets/demo/models/blue_part.json", "{\"gui_light\":\"front\",\"display\":{\"gui\":{\"translation\":[4,0,0]}},\"elements\":["
                + plane(6,6,10,10,8,"blue") + "]}");
        try (ResourceIndex index = index()) {
            var resolver = new JavaModelResolver(index);
            var model = resolver.resolveAll(List.of("demo:red_part", "demo:blue_part"));
            var atlas = TextureSet.atlas(index, List.of("demo:red", "demo:blue"));
            var icon = GuiIconRenderer.render(model, atlas, model.elements());
            assertEquals(0xFFFF0000, icon.getRGB(16,32));
            assertEquals(0xFF0000FF, icon.getRGB(48,32));
            assertEquals(0, icon.getRGB(32,32));
            // Move the rear blue child onto red; neither child order may hide red.
            model.parts().get(1).display().getAsJsonObject("gui").add("translation",
                    JsonParser.parseString("[-4,0,0]"));
            var reversed = new ResolvedJavaModel(model.identifier(), model.elements(), model.textures(),
                    model.display(), false, true, List.of(model.parts().get(1), model.parts().get(0)));
            assertEquals(0xFFFF0000, GuiIconRenderer.render(model, atlas, model.elements()).getRGB(16,32));
            assertArrayEquals(pixels(GuiIconRenderer.render(model, atlas, model.elements())),
                    pixels(GuiIconRenderer.render(reversed, atlas, reversed.elements())));
        }
    }

    @Test
    void compilerRetainsMeshAndGeneratedChildrenWithIndependentPoses() throws Exception {
        textures();
        write("assets/demo/models/mesh.json", "{\"display\":{\"fixed\":{\"rotation\":[0,25,21]}},\"elements\":["
                + plane(6,6,10,10,8,"red") + "]}");
        write("assets/demo/models/sprite.json", """
                {"parent":"minecraft:item/generated","textures":{"layer0":"demo:blue"},
                 "display":{"fixed":{"rotation":[0,-35,12]},"firstperson_righthand":{"translation":[2,3,4]}}}
                """);
        write("assets/demo/items/composite.json", """
                {"model":{"type":"minecraft:composite","models":[{"type":"minecraft:model","model":"demo:mesh"},
                {"type":"minecraft:composite","models":[{"type":"minecraft:model","model":"demo:sprite"}]}]}}
                """);
        var result = new BedrockPackCompiler(root.resolve("output"), config()).build(
                List.of(new ContentSource("test", ContentSource.Kind.RESOURCE_PACK, root, 1)),
                List.of(new CustomItemDescriptor("test", "minecraft:paper", Optional.of("demo:composite"), OptionalInt.empty(), "")));
        assertEquals(1, result.converted(), result.problems().toString());
        try (ZipFile zip = new ZipFile(result.outputDirectory().resolve("pack.zip").toFile())) {
            var entry = zip.stream().filter(e -> e.getName().startsWith("models/entity/display.")).findFirst().orElseThrow();
            var geometry = JsonParser.parseString(new String(zip.getInputStream(entry).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
            var meshes = java.util.stream.StreamSupport.stream(geometry.getAsJsonArray("bones").spliterator(), false)
                    .map(com.google.gson.JsonElement::getAsJsonObject).filter(b -> b.has("cubes")).toList();
            assertEquals(2, meshes.size());
            assertEquals("twp0_0s", meshes.get(0).get("parent").getAsString());
            assertEquals("twp0_1s", meshes.get(1).get("parent").getAsString());
            assertFalse(meshes.get(1).getAsJsonArray("cubes").isEmpty(), "Generated children must not disappear beside mesh children");
            var animation = zip.stream().filter(e -> e.getName().endsWith(".animation.json")).findFirst().orElseThrow();
            var definitions = JsonParser.parseString(new String(zip.getInputStream(animation).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("animations");
            var first = definitions.entrySet().stream().filter(e -> e.getKey().endsWith("first_person_right"))
                    .findFirst().orElseThrow().getValue().getAsJsonObject().getAsJsonObject("bones");
            assertEquals(2, first.size());
            assertNotEquals(first.get("bone_0"), first.get("bone_1"));
        }
    }

    @Test
    void compilerPublishesTheRenderedIconAndReferencesItFromTheItemAtlas() throws Exception {
        textures();
        write("assets/demo/models/marker.json", "{\"gui_light\":\"front\",\"elements\":[" + plane(4,2,12,14,8,"red") + "]}");
        write("assets/demo/items/marker.json", "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"demo:marker\"}}");
        var result = new BedrockPackCompiler(root.resolve("output"), config()).build(
                List.of(new ContentSource("test",ContentSource.Kind.RESOURCE_PACK,root,1)),
                List.of(new CustomItemDescriptor("test","minecraft:paper",Optional.of("demo:marker"),OptionalInt.empty(),"")));
        assertEquals(1, result.converted());
        try (ZipFile zip = new ZipFile(result.outputDirectory().resolve("pack.zip").toFile())) {
            JsonObject itemAtlas = JsonParser.parseString(new String(zip.getInputStream(zip.getEntry("textures/item_texture.json")).readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            String path = itemAtlas.getAsJsonObject("texture_data").entrySet().iterator().next().getValue()
                    .getAsJsonObject().get("textures").getAsString();
            assertTrue(path.endsWith("_icon"));
            BufferedImage icon = ImageIO.read(zip.getInputStream(zip.getEntry(path + ".png")));
            assertEquals(64, icon.getWidth());
            assertArrayEquals(new int[]{16,8,47,55}, bounds(icon));
        }
    }

    @Test
    void inventoryUsesGeometrySilhouetteInsteadOfTextureAtlas() throws Exception {
        textures();
        BufferedImage icon = render(plane(4, 2, 12, 14, 8, "red"), "{}");
        assertEquals(0, icon.getRGB(0, 0));
        assertEquals(32 * 48, visible(icon));
        assertEquals(0xFF0000, icon.getRGB(32, 32) & 0xFFFFFF);
    }

    @Test
    void appliesGuiScaleThenRotationThenTranslationWithoutAutoFitting() throws Exception {
        textures();
        BufferedImage icon = render(plane(4, 6, 12, 10, 8, "red"),
                "{\"scale\":[0.5,1,1],\"rotation\":[0,0,90],\"translation\":[4,2,0]}");
        // A centered 4x4 square moves four model units right and two units up.
        assertArrayEquals(new int[]{40, 16, 55, 31}, bounds(icon));
    }

    @Test
    void inheritsGuiPoseAndLightingAndSelectsTheExposedFace() throws Exception {
        textures();
        write("assets/demo/models/parent.json", """
                {"gui_light":"front","display":{"gui":{"rotation":[0,-90,0],"scale":[0.5,0.5,0.5]}},
                 "textures":{"r":"demo:red","b":"demo:blue"},"elements":[
                 {"from":[0,0,0],"to":[16,16,16],"faces":{"south":{"texture":"#r"},"east":{"texture":"#b"}}}]}
                """);
        write("assets/demo/models/child.json", "{\"parent\":\"demo:parent\"}");
        try (ResourceIndex index = index()) {
            ResolvedJavaModel model = new JavaModelResolver(index).resolve("demo:child");
            assertTrue(model.frontLight());
            BufferedImage icon = GuiIconRenderer.render(model, TextureSet.atlas(index, List.of("demo:red", "demo:blue")), model.elements());
            assertEquals(0x0000FF, icon.getRGB(32,32) & 0xFFFFFF);
            assertArrayEquals(new int[]{16,16,47,47}, bounds(icon));
        }
    }

    @Test
    void preservesUvQuarterTurnsAndMirroredRanges() throws Exception {
        textures();
        String plane = plane(0,0,16,16,8,"quadrants");
        BufferedImage turned = render(plane.replace("\"uv\":[0,0,16,16]", "\"uv\":[0,0,16,16],\"rotation\":90"), "{}");
        assertEquals(0x0000FF, turned.getRGB(8,8) & 0xFFFFFF);
        assertEquals(0xFF0000, turned.getRGB(56,8) & 0xFFFFFF);
        BufferedImage mirrored = render(plane.replace("[0,0,16,16]", "[16,0,0,16]"), "{}");
        assertEquals(0x00FF00, mirrored.getRGB(8,8) & 0xFFFFFF);
        assertEquals(0xFF0000, mirrored.getRGB(56,8) & 0xFFFFFF);
    }

    @Test
    void depthAndTransparentHolesAreIndependentOfElementOrder() throws Exception {
        textures();
        String back = plane(0,0,16,16,4,"blue"), front = plane(0,0,16,16,12,"hole");
        BufferedImage first = render(back + ',' + front, "{}"), reversed = render(front + ',' + back, "{}");
        assertArrayEquals(pixels(first), pixels(reversed));
        assertEquals(0x0000FF, first.getRGB(8,8) & 0xFFFFFF);
        assertEquals(0xFF0000, first.getRGB(56,56) & 0xFFFFFF);
    }

    @Test
    void blendsTranslucentFacesOnceAndHidesThoseBehindOpaqueGeometry() throws Exception {
        textures();
        String back = plane(0,0,16,16,4,"blue"), front = plane(0,0,16,16,12,"glass");
        BufferedImage first = render(back + ',' + front, "{}"), reversed = render(front + ',' + back, "{}");
        assertArrayEquals(pixels(first), pixels(reversed));
        assertEquals(0xFF80007F, first.getRGB(32,32)); // Shared triangle diagonal must not blend twice.
        BufferedImage hidden = render(front + ',' + plane(0,0,16,16,15,"blue"), "{}");
        assertEquals(0xFF0000FF, hidden.getRGB(32,32));
    }

    @Test
    void rotatesElementsAroundTheirOwnOriginAndRescalesThem() throws Exception {
        textures();
        JsonObject element = JsonParser.parseString(plane(4,4,12,12,8,"red")).getAsJsonObject();
        element.add("rotation", JsonParser.parseString("{\"origin\":[8,8,8],\"axis\":\"y\",\"angle\":45,\"rescale\":true}"));
        BufferedImage scaled = render(element.toString(), "{}");
        assertArrayEquals(new int[]{16,16,47,47}, bounds(scaled));
        element.getAsJsonObject("rotation").addProperty("rescale", false);
        assertTrue(visible(render(element.toString(), "{}")) < visible(scaled));
    }

    @Test
    void rejectsNonFiniteCoordinatesInsteadOfPublishingAnEmptyIcon() throws Exception {
        textures();
        assertThrows(java.io.IOException.class, () -> render(plane(0,0,16,16,8,"red"), "{\"scale\":[1e999,1,1]}"));
    }

    private BufferedImage render(String elements, String gui) throws Exception {
        JsonArray cubes = JsonParser.parseString('[' + elements + ']').getAsJsonArray();
        JsonObject display = new JsonObject(); display.add("gui", JsonParser.parseString(gui));
        ResolvedJavaModel model = new ResolvedJavaModel("demo:test", cubes, Map.of(), display, false, true);
        try (ResourceIndex index = index()) {
            return GuiIconRenderer.render(model, TextureSet.atlas(index,
                    List.of("demo:red", "demo:blue", "demo:quadrants", "demo:hole", "demo:glass")), cubes);
        }
    }

    private static String plane(double x0, double y0, double x1, double y1, double z, String texture) {
        return "{\"from\":["+x0+','+y0+','+z+"],\"to\":["+x1+','+y1+','+z+
                "],\"faces\":{\"south\":{\"texture\":\"demo:"+texture+"\",\"uv\":[0,0,16,16]}}}";
    }

    private void textures() throws Exception {
        Path dir = Files.createDirectories(root.resolve("assets/demo/textures"));
        int[][] colors = {{0xFFFF0000,0xFFFF0000,0xFFFF0000,0xFFFF0000},
                {0xFF0000FF,0xFF0000FF,0xFF0000FF,0xFF0000FF},
                {0xFFFF0000,0xFF00FF00,0xFF0000FF,0xFFFFFF00},
                {0,0xFFFF0000,0xFFFF0000,0xFFFF0000},
                {0x80FF0000,0x80FF0000,0x80FF0000,0x80FF0000}};
        int i = 0;
        for (String name : List.of("red", "blue", "quadrants", "hole", "glass")) {
            BufferedImage image = new BufferedImage(2,2,BufferedImage.TYPE_INT_ARGB);
            image.setRGB(0,0,2,2,colors[i++],0,2);
            ImageIO.write(image,"PNG",dir.resolve(name+".png").toFile());
        }
    }

    private void write(String path, String content) throws Exception {
        Path file = root.resolve(path); Files.createDirectories(file.getParent()); Files.writeString(file, content);
    }

    private ResourceIndex index() throws Exception {
        return ResourceIndex.build(List.of(new ContentSource("test",ContentSource.Kind.RESOURCE_PACK,root,1)),config());
    }

    private TwilightConfig config() {
        return new TwilightConfig(false,true,false,false,40,100,10_000_000,1000,false,false,List.of(),"auto",false,false,3);
    }

    private static int[] pixels(BufferedImage image) { return image.getRGB(0,0,64,64,null,0,64); }
    private static int visible(BufferedImage image) { return (int) java.util.Arrays.stream(pixels(image)).filter(p -> p >>> 24 != 0).count(); }
    private static int[] bounds(BufferedImage image) {
        int minX=64,minY=64,maxX=-1,maxY=-1;
        for (int y=0;y<64;y++) for (int x=0;x<64;x++) if (image.getRGB(x,y) >>> 24 != 0) {
            minX=Math.min(minX,x);minY=Math.min(minY,y);maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);
        }
        return new int[]{minX,minY,maxX,maxY};
    }
}
