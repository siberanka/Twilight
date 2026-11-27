package com.siberanka.twilight.compiler;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.siberanka.twilight.config.TwilightConfig;
import com.siberanka.twilight.source.ContentSource;
import com.siberanka.twilight.source.CustomItemDescriptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BetterModel 3.3.0 (and ModelEngine) writes one item definition for every bone: {@code range_dispatch} on the custom
 * model data, the bones being the same base item with that item model and a number.
 */
class CmdDispatchedBonesTest {
    @TempDir Path root;

    private static final String DEFINITION = """
            {"model":{"type":"range_dispatch","property":"custom_model_data","fallback":{"type":"empty"},"entries":[
              {"threshold":1,"model":{"type":"model","model":"bettermodel:c/a","tints":[{"type":"custom_model_data","default":16777215}]}},
              {"threshold":2,"model":{"type":"composite","models":[
                {"type":"model","model":"bettermodel:c/b","tints":[{"type":"custom_model_data","default":16777215}]},
                {"type":"model","model":"bettermodel:c/c","tints":[{"type":"custom_model_data","default":16777215}]}]}},
              {"threshold":3,"model":{"type":"model","model":"bettermodel:c/d"}}]}}
            """;

    private static String cube(int offset) {
        return """
                {"textures":{"all":"bettermodel:item/tex"},
                 "elements":[{"from":[%d,1,3],"to":[%d,15,13],"faces":{
                   "north":{"texture":"#all","uv":[0,0,16,16]},"south":{"texture":"#all"},
                   "east":{"texture":"#all"},"west":{"texture":"#all"},
                   "up":{"texture":"#all"},"down":{"texture":"#all"}}}]}
                """.formatted(offset, offset + 4);
    }

    private ContentSource pack() throws Exception {
        Path source = root.resolve("bm33");
        put(source, "assets/bettermodel/items/bm_models.json", DEFINITION);
        for (String name : new String[]{"a", "b", "c", "d"}) {
            put(source, "assets/bettermodel/models/c/" + name + ".json", cube(name.charAt(0) - 'a' + 2));
        }
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) image.setRGB(x, y, 0xFF3366CC);
        Path texture = source.resolve("assets/bettermodel/textures/item/tex.png");
        Files.createDirectories(texture.getParent());
        ImageIO.write(image, "PNG", texture.toFile());
        return new ContentSource("bettermodel", ContentSource.Kind.RESOURCE_PACK, source, 1);
    }

    private static void put(Path source, String relative, String text) throws Exception {
        Path file = source.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    private static List<CustomItemDescriptor> bones(String provider) {
        return List.of(
                new CustomItemDescriptor(provider, "minecraft:leather_horse_armor", Optional.of("bettermodel:bm_models"), OptionalInt.of(1), "a"),
                new CustomItemDescriptor(provider, "minecraft:leather_horse_armor", Optional.of("bettermodel:bm_models"), OptionalInt.of(2), "b"),
                new CustomItemDescriptor(provider, "minecraft:leather_horse_armor", Optional.of("bettermodel:bm_models"), OptionalInt.of(3), "c"));
    }

    private TwilightConfig config() {
        return new TwilightConfig(false, true, false, true, 40, 100, 10_000_000, 10_000,
                true, true, List.of(), "auto", false, false, 3);
    }

    private static JsonObject index(BuildResult result) throws Exception {
        try (ZipFile zip = new ZipFile(result.outputDirectory().resolve("pack.zip").toFile())) {
            var entry = zip.getEntry("twilight/display-index-v2.json");
            return entry == null ? null : JsonParser.parseString(new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static JsonObject report(BuildResult result) throws Exception {
        return JsonParser.parseString(Files.readString(result.outputDirectory().resolve("build-report.json"))).getAsJsonObject();
    }

    @Test void cmdDispatchedBonesOfBetterModelBecomeDisplayOnlyModels() throws Exception {
        BuildResult result = new BedrockPackCompiler(root.resolve("data"), config())
                .withDisplayedItems(Set.of("model=bettermodel:bm_models", "minecraft:leather_horse_armor|cmd=1"))
                .build(List.of(pack()), bones("BetterModel"));
        JsonObject index = index(result);
        JsonObject models = index.getAsJsonObject("models");
        assertEquals(Set.of("bettermodel:bm_models|cmd=1", "bettermodel:bm_models|cmd=2", "bettermodel:bm_models|cmd=3"),
                models.keySet(), index.toString());
        assertEquals(0, index.getAsJsonObject("variants").size(), "bones have no Bedrock item: " + index);
        assertEquals(3, report(result).get("display_only_models").getAsInt());
    }

    @Test void modelEngineBonesGetTheSameTreatment() throws Exception {
        BuildResult result = new BedrockPackCompiler(root.resolve("data-me"), config())
                .build(List.of(pack()), bones("ModelEngine"));
        assertEquals(3, index(result).getAsJsonObject("models").size());
    }

    @Test void otherSourcesKeepTheirBedrockItems() throws Exception {
        BuildResult result = new BedrockPackCompiler(root.resolve("data-other"), config())
                .build(List.of(pack()), bones("Nexo"));
        JsonObject index = index(result);
        assertTrue(index.getAsJsonObject("models").size() == 0, index.toString());
        assertEquals(3, index.getAsJsonObject("variants").size());
    }

    /** Geyser matches every threshold a value reaches and takes the highest priority: Java's highest threshold must win. */
    @Test void theHighestReachedThresholdGetsTheHighestPriority() throws Exception {
        BuildResult result = new BedrockPackCompiler(root.resolve("data-priority"), config())
                .build(List.of(pack()), bones("Nexo"));
        JsonObject mappings = JsonParser.parseString(Files.readString(
                result.outputDirectory().resolve("custom_mappings/geyser_item_mappings.json"))).getAsJsonObject();
        var definitions = mappings.getAsJsonObject("items").getAsJsonArray("minecraft:leather_horse_armor");
        java.util.Map<Integer, Integer> priorityByThreshold = new java.util.TreeMap<>();
        for (var definition : definitions) {
            JsonObject value = definition.getAsJsonObject();
            priorityByThreshold.put((int) value.getAsJsonObject("predicate").get("threshold").getAsDouble(),
                    value.get("priority").getAsInt());
        }
        assertEquals(3, priorityByThreshold.size(), priorityByThreshold.toString());
        assertTrue(priorityByThreshold.get(1) < priorityByThreshold.get(2) && priorityByThreshold.get(2) < priorityByThreshold.get(3),
                priorityByThreshold.toString());
    }

    @Test void modelBoneAcceptsOnlyCustomModelDataDispatch() {
        var cmd = new com.google.gson.JsonArray();
        cmd.add(JsonParser.parseString("{\"type\":\"range_dispatch\",\"property\":\"custom_model_data\",\"threshold\":5.0}"));
        assertTrue(BedrockPackCompiler.modelBone(new ItemCandidate("minecraft:leather_horse_armor", "bettermodel:bm_models",
                List.of("bettermodel:c/a"), OptionalInt.empty(), "BetterModel", cmd, 3, List.of())));
        var other = new com.google.gson.JsonArray();
        other.add(JsonParser.parseString("{\"type\":\"condition\",\"property\":\"damaged\",\"expected\":true}"));
        assertFalse(BedrockPackCompiler.modelBone(new ItemCandidate("minecraft:leather_horse_armor", "bettermodel:bm_models",
                List.of("bettermodel:c/a"), OptionalInt.empty(), "BetterModel", other, 0, List.of())));
    }
}
