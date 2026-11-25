package com.siberanka.twilight.compiler;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.siberanka.twilight.config.TwilightConfig;
import com.siberanka.twilight.source.ContentSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ModelFaceRepairTest {
    @TempDir Path root;

    /** Two cubes whose faces carry the reference under a key named like the texture, as some exporters write it. */
    private static String malformed(String texture, String variable, String key) {
        String faces = """
                {"north":{"uv":[0,0,8,8],"%1$s":"#%2$s"},"south":{"uv":[0,0,8,8],"%1$s":"#%2$s"},
                 "east":{"uv":[0,0,8,8],"%1$s":"#%2$s"},"west":{"uv":[0,0,8,8],"%1$s":"#%2$s"},
                 "up":{"uv":[0,0,8,8],"%1$s":"#%2$s"},"down":{"uv":[0,0,8,8],"%1$s":"#%2$s"}}
                """.formatted(key, variable);
        return """
                {"textures":{"%2$s":"%1$s","particle":"%1$s"},
                 "elements":[{"from":[0,0,0],"to":[8,4,8],"faces":%3$s},
                             {"from":[2,4,2],"to":[6,12,6],"faces":%3$s}]}
                """.formatted(texture, variable, faces);
    }

    private static String standard(String texture, String variable) {
        return malformed(texture, variable, "texture");
    }

    private Path pack(String name, String model) throws Exception {
        Path source = root.resolve(name);
        write(source, "assets/minecraft/models/item/paper.json", """
                {"parent":"minecraft:item/generated","overrides":[
                  {"predicate":{"custom_model_data":1},"model":"demo:item/stone"},
                  {"predicate":{"custom_model_data":2},"model":"demo:item/stone"},
                  {"predicate":{"custom_model_data":3},"model":"demo:item/flat"}]}
                """);
        write(source, "assets/demo/models/item/stone.json", model);
        write(source, "assets/demo/models/item/flat.json", """
                {"parent":"minecraft:item/generated","textures":{"layer0":"demo:item/stone"}}
                """);
        Path texture = source.resolve("assets/demo/textures/item/stone.png");
        Files.createDirectories(texture.getParent());
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) image.setRGB(x, y, new Color(40 + x * 20, 90, 200 - y * 10).getRGB());
        ImageIO.write(image, "PNG", texture.toFile());
        return source;
    }

    private static BuildResult build(Path data, Path source) throws Exception {
        // A client jar that only states the resource-pack format, which the pack overlays are selected by.
        Path cache = data.resolve("cache/vanilla/26.2");
        Files.createDirectories(cache);
        Path client = cache.resolve("client.jar");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(client))) {
            zip.putNextEntry(new ZipEntry("version.json"));
            zip.write("{\"pack_version\":{\"resource_major\":75,\"resource_minor\":0}}".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        Files.writeString(cache.resolve("client.sha1"), java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(client))));
        return new BedrockPackCompiler(data, config(), "26.2").build(
                List.of(new ContentSource("test", ContentSource.Kind.RESOURCE_PACK, source, 1)), List.of());
    }

    private static Map<String, String> content(BuildResult result) throws Exception {
        Map<String, String> entries = new TreeMap<>();
        try (ZipFile zip = new ZipFile(result.outputDirectory().resolve("pack.zip").toFile())) {
            zip.stream().filter(entry -> entry.getName().startsWith("models/") || entry.getName().startsWith("textures/")
                    || entry.getName().startsWith("render_controllers/") || entry.getName().startsWith("attachables/"))
                    .forEach(entry -> {
                        try {
                            entries.put(entry.getName(), java.util.HexFormat.of().formatHex(
                                    java.security.MessageDigest.getInstance("SHA-256").digest(zip.getInputStream(entry).readAllBytes())));
                        } catch (Exception failure) { throw new RuntimeException(failure); }
                    });
        }
        return entries;
    }

    @Test
    void repairedModelConvertsLikeTheStandardOneAndIsNotedOncePerModel() throws Exception {
        BuildResult fixed = build(root.resolve("data-a"), pack("a", malformed("demo:item/stone", "0", "demo:item/stone")));
        BuildResult control = build(root.resolve("data-b"), pack("b", standard("demo:item/stone", "0")));

        assertTrue(fixed.problems().isEmpty(), fixed.problems().toString());
        assertEquals(3, fixed.converted());
        assertTrue(control.modelRepairs().isEmpty());
        Map<String, String> repaired = content(fixed);
        assertTrue(repaired.keySet().stream().anyMatch(name -> name.startsWith("models/entity/geometry.")));
        assertEquals(content(control), repaired, "geometry, UVs and textures must match the standard model");
        assertEquals(1, fixed.modelRepairs().size(), "one line per model, not per face or per item using it");
        assertEquals("demo:item/stone: 12 face(s) used non-standard key \"demo:item/stone\" instead of \"texture\"; "
                + "Java clients may show this model as missing - fix the source model", fixed.modelRepairs().getFirst());
    }

    @Test
    void repairFollowsTheReferenceEvenWhenTheKeyNoLongerMatchesAnyTexture() throws Exception {
        // ItemsAdder rewrites the texture variables to its own names but keeps the broken face key.
        BuildResult fixed = build(root.resolve("data-a"), pack("a", malformed("demo:item/stone", "0", "gone:old/name")));
        BuildResult control = build(root.resolve("data-b"), pack("b", standard("demo:item/stone", "0")));
        assertTrue(fixed.problems().isEmpty(), fixed.problems().toString());
        assertEquals(content(control), content(fixed));
        assertEquals(1, fixed.modelRepairs().size());
    }

    @Test
    void repairedByKeyNameWhenTheValueIsNotAReference() throws Exception {
        JsonArray elements = JsonParser.parseString("""
                [{"faces":{"north":{"uv":[0,0,1,1],"demo:item/stone":true},
                           "south":{"uv":[0,0,1,1],"demo:item/stone":true}}}]""").getAsJsonArray();
        ModelFaceRepair.Result result = ModelFaceRepair.repair(elements, Map.of("a", "demo:item/stone", "particle", "demo:item/stone"),
                Map.of("a", "demo:item/stone", "particle", "demo:item/stone"));
        assertEquals(2, result.repaired());
        assertEquals("#a", elements.get(0).getAsJsonObject().getAsJsonObject("faces").getAsJsonObject("north")
                .get("texture").getAsString());
        assertFalse(elements.get(0).getAsJsonObject().getAsJsonObject("faces").getAsJsonObject("north").has("demo:item/stone"));
    }

    @Test
    void ambiguousFacesStayUnrepairedAndStrictModeNamesThem() throws Exception {
        String faces = """
                {"north":{"uv":[0,0,8,8],"foo":"x","bar":"y"},"south":{"uv":[0,0,8,8],"foo":"x"}}""";
        String model = """
                {"textures":{"0":"demo:item/stone","1":"demo:item/stone"},"elements":[{"from":[0,0,0],"to":[8,4,8],"faces":%s}]}
                """.formatted(faces);
        ConversionException failure = assertThrows(ConversionException.class, () -> build(root.resolve("data"), pack("a", model)));
        assertTrue(failure.problems().getFirst().contains(
                "3D model has no face textures (element 0 face north: keys [uv, foo, bar]; element 0 face south: keys [uv, foo])"),
                failure.problems().toString());
        assertFalse(failure.problems().getFirst().contains("model-repaired"));
    }

    @Test
    void tiedReferencesAreAmbiguous() {
        JsonArray elements = JsonParser.parseString("""
                [{"faces":{"north":{"uv":[0,0,1,1],"p":"#a","q":"#b"}}}]""").getAsJsonArray();
        ModelFaceRepair.Result result = ModelFaceRepair.repair(elements, Map.of("a", "x:a", "b", "x:b"), Map.of("a", "x:a", "b", "x:b"));
        assertEquals(0, result.repaired());
        assertEquals("element 0 face north: keys [uv, p, q]", ModelFaceRepair.unrepaired(elements, 3));
    }

    @Test
    void wellFormedModelsAreLeftExactlyAsTheyWere() {
        String json = """
                [{"faces":{"north":{"uv":[0,0,1,1],"texture":"#a","cullface":"north","tintindex":0},
                           "up":{"texture":"minecraft:block/stone"}}}]""";
        JsonArray elements = JsonParser.parseString(json).getAsJsonArray();
        ModelFaceRepair.Result result = ModelFaceRepair.repair(elements, Map.of("a", "x:a"), Map.of("a", "x:a"));
        assertEquals(0, result.repaired());
        assertEquals(JsonParser.parseString(json), elements);
        assertEquals("", ModelFaceRepair.unrepaired(elements, 3));
        assertEquals(Set.of(), result.keys());
    }

    @Test
    void tombstoneDebugPacketConvertsInStrictModeLikeItsControl() throws Exception {
        Path base = Path.of("test-material/debug-packets/tombstone3/x");
        if (!Files.isDirectory(base)) base = Path.of("../test-material/debug-packets/tombstone3/x");
        assumeTrue(Files.isDirectory(base.resolve("fail")) && Files.isDirectory(base.resolve("ctrl")),
                "test-material is git-ignored and absent here");
        BuildResult fail = build(root.resolve("data-fail"), base.resolve("fail"));
        BuildResult control = build(root.resolve("data-ctrl"), base.resolve("ctrl"));
        assertTrue(fail.problems().isEmpty(), fail.problems().toString());
        assertTrue(control.problems().isEmpty());
        assertEquals(control.converted(), fail.converted());
        assertEquals(content(control), content(fail));
        assertEquals(1, fail.modelRepairs().size());
        assertTrue(fail.modelRepairs().getFirst().startsWith("myitems:new_deco/tombstone3: 54 face(s)"), fail.modelRepairs().toString());
    }

    private static void write(Path root, String relative, String text) throws Exception {
        Path path = root.resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, text, StandardCharsets.UTF_8);
    }

    private static TwilightConfig config() {
        return new TwilightConfig(false, true, false, true, 40, 100, 10_000_000, 10_000,
                true, true, List.of(), "auto", false, false, 3);
    }
}
