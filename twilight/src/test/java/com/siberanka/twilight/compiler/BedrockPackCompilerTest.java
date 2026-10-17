package com.siberanka.twilight.compiler;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.siberanka.twilight.config.TwilightConfig;
import com.siberanka.twilight.source.ContentSource;
import com.siberanka.twilight.source.CustomItemDescriptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.InputStreamReader;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockPackCompilerTest {
    @TempDir Path root;

    @Test
    void convertsLayeredLegacyAndVolumetricModernVariantsDeterministically() throws Exception {
        Path source = root.resolve("source");
        write(source, "assets/minecraft/models/item/paper.json", """
                {"parent":"minecraft:item/generated","overrides":[
                  {"predicate":{"custom_model_data":7},"model":"demo:item/layered"}
                ]}
                """);
        write(source, "assets/demo/models/item/layered.json", """
                {"parent":"minecraft:item/generated","textures":{"layer0":"demo:item/base","layer1":"demo:item/top"}}
                """);
        png(source.resolve("assets/demo/textures/item/base.png"), Color.RED);
        png(source.resolve("assets/demo/textures/item/top.png"), new Color(0, 0, 255, 128));

        write(source, "assets/demo/items/hammer.json", """
                {"model":{"type":"minecraft:condition","property":"minecraft:damaged",
                  "on_true":{"type":"minecraft:model","model":"demo:item/hammer_damaged"},
                  "on_false":{"type":"minecraft:composite","models":[
                    {"type":"minecraft:model","model":"demo:item/hammer"},
                    {"type":"minecraft:model","model":"demo:item/handle"}]}}}
                """);
        write(source, "assets/demo/models/item/hammer.json", cube("demo:item/hammer"));
        write(source, "assets/demo/models/item/hammer_damaged.json", cube("demo:item/hammer_damaged"));
        write(source, "assets/demo/models/item/handle.json", cube("demo:item/handle"));
        png(source.resolve("assets/demo/textures/item/hammer.png"), Color.ORANGE);
        png(source.resolve("assets/demo/textures/item/hammer_damaged.png"), Color.GRAY);
        png(source.resolve("assets/demo/textures/item/handle.png"), Color.DARK_GRAY);

        ContentSource pack = new ContentSource("test", ContentSource.Kind.RESOURCE_PACK, source, 1);
        CustomItemDescriptor live = new CustomItemDescriptor("test", "minecraft:iron_pickaxe",
                Optional.of("demo:hammer"), OptionalInt.empty(), "Hammer");
        Path data = root.resolve("data");
        BedrockPackCompiler compiler = new BedrockPackCompiler(data, config());
        BuildResult first = compiler.build(List.of(pack), List.of(live));
        BuildResult second = compiler.build(List.of(pack), List.of(live));

        assertEquals(3, first.converted());
        assertEquals(2, first.threeDimensional());
        assertEquals(first.packSha256(), second.packSha256());
        try (ZipFile zip = new ZipFile(second.outputDirectory().resolve("pack.zip").toFile())) {
            assertNotNull(zip.getEntry("textures/item_texture.json"));
            JsonObject manifest = read(zip, "manifest.json");
            JsonArray headerVersion = manifest.getAsJsonObject("header").getAsJsonArray("version");
            JsonArray moduleVersion = manifest.getAsJsonArray("modules").get(0).getAsJsonObject()
                    .getAsJsonArray("version");
            assertEquals(headerVersion, moduleVersion);
            assertTrue(headerVersion.get(2).getAsInt() >= 1);
            assertTrue(headerVersion.get(2).getAsInt() <= 65_535,
                    "pack version must remain in the Bedrock client-safe component range");
            long geometries = zip.stream().filter(entry -> entry.getName().startsWith("models/entity/geometry.")).count();
            assertEquals(3, geometries);
            assertTrue(zip.stream().filter(entry -> entry.getName().startsWith("models/entity/geometry."))
                    .map(entry -> {
                        try { return read(zip, entry.getName()); } catch (Exception failure) { throw new RuntimeException(failure); }
                    }).anyMatch(value -> value.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject()
                            .getAsJsonArray("bones").get(0).getAsJsonObject().getAsJsonArray("cubes").size() == 2),
                    "static composite parts must be merged into one Bedrock geometry");
            String geometryName = zip.stream().filter(entry -> entry.getName().startsWith("models/entity/geometry."))
                    .findFirst().orElseThrow().getName();
            JsonObject geometry = read(zip, geometryName);
            JsonArray size = geometry.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject()
                    .getAsJsonArray("bones").get(0).getAsJsonObject().getAsJsonArray("cubes").get(0).getAsJsonObject()
                    .getAsJsonArray("size");
            assertTrue(size.get(2).getAsDouble() > 0, "3D depth must remain volumetric");
        }

        JsonObject mappings = JsonParser.parseString(Files.readString(second.outputDirectory()
                .resolve("custom_mappings/geyser_item_mappings.json"))).getAsJsonObject();
        JsonObject items = mappings.getAsJsonObject("items");
        assertTrue(items.has("minecraft:paper"));
        assertTrue(items.has("minecraft:iron_pickaxe"));
        assertEquals(1, items.getAsJsonArray("minecraft:paper").size());
        assertEquals(2, items.getAsJsonArray("minecraft:iron_pickaxe").size());
        assertFalse(items.has("minecraft:stick"), "unmodified vanilla items must never be emitted");
        for (var definition : items.getAsJsonArray("minecraft:iron_pickaxe")) {
            assertEquals("demo:hammer", definition.getAsJsonObject().get("model").getAsString());
            assertTrue(definition.getAsJsonObject().has("predicate"));
        }
    }

    /**
     * Backends of a proxy network share one Geyser registry. The same Java selector must therefore become the
     * same Bedrock item on every backend, whatever model each backend's pack draws for it.
     */
    @Test
    void sameSelectorIsTheSameBedrockItemOnEveryBackend() throws Exception {
        Path flat = root.resolve("backend-a");
        write(flat, "assets/minecraft/models/item/paper.json", """
                {"parent":"minecraft:item/generated","overrides":[
                  {"predicate":{"custom_model_data":7},"model":"alpha:item/coin"}]}
                """);
        write(flat, "assets/alpha/models/item/coin.json", """
                {"parent":"minecraft:item/generated","textures":{"layer0":"alpha:item/coin"}}
                """);
        png(flat.resolve("assets/alpha/textures/item/coin.png"), Color.YELLOW);
        write(flat, "assets/demo/items/hammer.json", """
                {"model":{"type":"minecraft:model","model":"alpha:item/hammer_flat"}}
                """);
        write(flat, "assets/alpha/models/item/hammer_flat.json", """
                {"parent":"minecraft:item/generated","textures":{"layer0":"alpha:item/coin"}}
                """);

        Path volumetric = root.resolve("backend-b");
        write(volumetric, "assets/minecraft/models/item/paper.json", """
                {"parent":"minecraft:item/generated","overrides":[
                  {"predicate":{"custom_model_data":7},"model":"beta:item/gem"}]}
                """);
        write(volumetric, "assets/beta/models/item/gem.json", cube("beta:item/gem"));
        png(volumetric.resolve("assets/beta/textures/item/gem.png"), Color.CYAN);
        write(volumetric, "assets/demo/items/hammer.json", """
                {"model":{"type":"minecraft:model","model":"beta:item/hammer"}}
                """);
        write(volumetric, "assets/beta/models/item/hammer.json", cube("beta:item/gem"));

        CustomItemDescriptor hammer = new CustomItemDescriptor("test", "minecraft:iron_pickaxe",
                Optional.of("demo:hammer"), OptionalInt.empty(), "Hammer");
        BuildResult a = new BedrockPackCompiler(root.resolve("data-a"), config())
                .build(List.of(new ContentSource("a", ContentSource.Kind.RESOURCE_PACK, flat, 1)), List.of(hammer));
        BuildResult b = new BedrockPackCompiler(root.resolve("data-b"), config())
                .build(List.of(new ContentSource("b", ContentSource.Kind.RESOURCE_PACK, volumetric, 1)), List.of(hammer));
        assertEquals(2, a.converted());
        assertEquals(2, b.converted());
        assertEquals(0, a.threeDimensional());
        assertEquals(2, b.threeDimensional());

        JsonObject itemsA = mappings(a).getAsJsonObject("items");
        JsonObject itemsB = mappings(b).getAsJsonObject("items");
        String coinA = itemsA.getAsJsonArray("minecraft:paper").get(0).getAsJsonObject().get("bedrock_identifier").getAsString();
        String coinB = itemsB.getAsJsonArray("minecraft:paper").get(0).getAsJsonObject().get("bedrock_identifier").getAsString();
        assertEquals(coinA, coinB, "custom model data 7 on paper must be one Bedrock item on every backend");
        assertTrue(coinA.startsWith("twilight:minecraft_paper_7_"), coinA);
        String hammerA = itemsA.getAsJsonArray("minecraft:iron_pickaxe").get(0).getAsJsonObject().get("bedrock_identifier").getAsString();
        String hammerB = itemsB.getAsJsonArray("minecraft:iron_pickaxe").get(0).getAsJsonObject().get("bedrock_identifier").getAsString();
        assertEquals(hammerA, hammerB, "an item model must be one Bedrock item on every backend");
        assertFalse(coinA.equals(hammerA));

        // The pack carries the same mappings for twilight-proxy; each pack draws the item its own way.
        for (BuildResult result : List.of(a, b)) {
            try (ZipFile zip = new ZipFile(result.outputDirectory().resolve("pack.zip").toFile())) {
                assertEquals(mappings(result), read(zip, BedrockPackCompiler.PACK_MAPPINGS));
            }
        }
        try (ZipFile zip = new ZipFile(b.outputDirectory().resolve("pack.zip").toFile())) {
            String safe = coinB.substring(coinB.indexOf(':') + 1);
            assertNotNull(zip.getEntry("attachables/" + safe + ".json"), "backend b draws the coin as a 3D model");
        }
    }

    /**
     * A pack for a proxy's Geyser leaves out the item-display entity (a copy of every 3D item that the client
     * builds while loading); every JSON file is written without indentation.
     */
    @Test
    void leavesOutItemDisplayModelsWhenNoDisplayBridgeUsesThemAndMinifiesJson() throws Exception {
        Path source = root.resolve("display-source");
        write(source, "assets/minecraft/models/item/paper.json", """
                {"parent":"minecraft:item/generated","overrides":[
                  {"predicate":{"custom_model_data":5},"model":"demo:item/gem"}]}
                """);
        write(source, "assets/demo/models/item/gem.json", cube("demo:item/gem"));
        png(source.resolve("assets/demo/textures/item/gem.png"), Color.CYAN);
        ContentSource pack = new ContentSource("test", ContentSource.Kind.RESOURCE_PACK, source, 1);
        BuildResult withDisplays = new BedrockPackCompiler(root.resolve("data-displays"), config()).build(List.of(pack), List.of());
        BuildResult proxy = new BedrockPackCompiler(root.resolve("data-proxy"), config()).withItemDisplays(false)
                .build(List.of(pack), List.of());
        assertEquals(1, proxy.converted());
        assertEquals(1, proxy.threeDimensional());
        try (ZipFile zip = new ZipFile(withDisplays.outputDirectory().resolve("pack.zip").toFile())) {
            assertNotNull(zip.getEntry("entity/twilight_display.entity.json"));
        }
        try (ZipFile zip = new ZipFile(proxy.outputDirectory().resolve("pack.zip").toFile())) {
            assertNull(zip.getEntry("entity/twilight_display.entity.json"));
            assertNull(zip.getEntry("twilight/display-index-v2.json"));
            assertTrue(zip.stream().noneMatch(entry -> entry.getName().contains("display.")), "no display geometry or animation");
            assertEquals(1, zip.stream().filter(entry -> entry.getName().startsWith("attachables/")).count(), "held items keep their model");
            for (ZipEntry entry : zip.stream().filter(entry -> entry.getName().endsWith(".json")).toList()) {
                String text = new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
                if (!entry.getName().equals("manifest.json")) assertFalse(text.contains("\n  "), entry.getName() + " is indented");
                JsonParser.parseString(text);
            }
        }
    }

    /**
     * Displays show every item and block: flat custom items as Java's extrusion, items without custom data with their
     * own model (tinted like Java), block states of block displays rotated as their block state file says; flat ones
     * are marked so that dropped items stay Bedrock's own.
     */
    @Test
    void displaysShowFlatItemsOwnModelsAndBlockStates() throws Exception {
        Path source = root.resolve("own-source");
        write(source, "assets/minecraft/models/item/paper.json", """
                {"parent":"minecraft:item/generated","textures":{"layer0":"minecraft:item/paper"},"overrides":[
                  {"predicate":{"custom_model_data":5},"model":"demo:item/note"}]}
                """);
        write(source, "assets/demo/models/item/note.json", """
                {"parent":"minecraft:item/generated","textures":{"layer0":"demo:item/note"}}
                """);
        png(source.resolve("assets/demo/textures/item/note.png"), Color.ORANGE);
        write(source, "assets/demo/items/wand.json", """
                {"model":{"type":"minecraft:condition","property":"minecraft:using_item",
                  "on_true":{"type":"minecraft:model","model":"demo:item/missing"},
                  "on_false":{"type":"minecraft:model","model":"demo:item/wand","tints":[{"type":"minecraft:constant","value":-65536}]}}}
                """);
        write(source, "assets/demo/models/item/wand.json", """
                {"parent":"minecraft:item/generated","textures":{"layer0":"demo:item/wand"}}
                """);
        png(source.resolve("assets/demo/textures/item/wand.png"), Color.WHITE);
        write(source, "assets/demo/blockstates/lamp.json", """
                {"variants":{"facing=north":{"model":"demo:block/lamp"},"facing=east":{"model":"demo:block/lamp","y":90}}}
                """);
        write(source, "assets/demo/models/block/lamp.json", cube("demo:block/lamp"));
        png(source.resolve("assets/demo/textures/block/lamp.png"), Color.YELLOW);
        ContentSource pack = new ContentSource("test", ContentSource.Kind.RESOURCE_PACK, source, 1);
        BuildResult result = new BedrockPackCompiler(root.resolve("data-own"), config())
                .withDisplayedItems(java.util.Set.of("minecraft:paper|cmd=5"))
                .withDisplayedOwnModels(java.util.Set.of("item=demo:wand", "block=demo:lamp[facing=east]", "block=demo:none"))
                .build(List.of(pack), List.of());
        try (ZipFile zip = new ZipFile(result.outputDirectory().resolve("pack.zip").toFile())) {
            JsonObject index = read(zip, "twilight/display-index-v2.json");
            assertEquals(1, index.getAsJsonObject("variants").size(), index.toString());
            assertTrue(index.getAsJsonObject("vanilla").has("demo:wand"));
            assertTrue(index.getAsJsonObject("blocks").has("block=demo:lamp[facing=east]"));
            JsonArray flat = index.getAsJsonArray("flat");
            assertEquals(2, flat.size(), "the paper note and the wand are flat; the lamp is not");
            int lamp = index.getAsJsonObject("blocks").getAsJsonArray("block=demo:lamp[facing=east]").get(0).getAsInt();
            assertFalse(flat.contains(new com.google.gson.JsonPrimitive(lamp)));
            JsonArray transforms = index.getAsJsonArray("transforms");
            int transform = index.getAsJsonObject("blocks").getAsJsonArray("block=demo:lamp[facing=east]").get(1).getAsInt();
            JsonArray none = transforms.get(transform).getAsJsonArray();
            assertEquals(-8, none.get(0).getAsInt(), "block displays draw from the block's corner, undoing the item half turn");
            assertEquals(8, none.get(2).getAsInt());
            BufferedImage sheet = ImageIO.read(zip.getInputStream(zip.getEntry("textures/twilight/display_sheet0.png")));
            boolean red = false;
            for (int x = 0; x < sheet.getWidth(); x++) red |= sheet.getRGB(x, 0) == 0xFFFF0000;
            assertTrue(red, "the wand's constant tint is baked into its texture");
        }
        JsonObject report = JsonParser.parseString(Files.readString(result.outputDirectory().resolve("build-report.json")))
                .getAsJsonObject();
        assertTrue(report.getAsJsonArray("notices").toString().contains("block=demo:none"), report.toString());
    }

    /**
     * The player heads a server shows go into the pack for Geyser's custom skulls; a head seen in a display gets a
     * display model drawn with its skin (here from the skin cache, without downloads).
     */
    @Test
    void packsPlayerHeadsAndDrawsDisplayedHeadsWithTheirSkin() throws Exception {
        String skin = "4b0b1f7b8a6fa27e5f8fdb4c1d1e44f6a2c1e0d9c3b2a19f8e7d6c5b4a392817";
        Path cache = root.resolve("skins");
        Files.createDirectories(cache);
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(10, 10, 0xFF112233);
        ImageIO.write(image, "PNG", cache.resolve(skin + ".png").toFile());
        Path source = root.resolve("head-source");
        Files.createDirectories(source);
        ContentSource pack = new ContentSource("test", ContentSource.Kind.RESOURCE_PACK, source, 1);
        BuildResult result = new BedrockPackCompiler(root.resolve("data-heads"), config())
                .withDisplayedOwnModels(java.util.Set.of("head=" + skin, "head=" + "0".repeat(64)))
                .withPlayerHeads(java.util.Set.of(skin), java.util.Set.of("Notch"), cache, false)
                .withPlayerSkins(new java.util.LinkedHashSet<>(List.of("bbbbbbbbbbbbbbbb", "aaaaaaaaaaaaaaaa")))
                .build(List.of(pack), List.of());
        try (ZipFile zip = new ZipFile(result.outputDirectory().resolve("pack.zip").toFile())) {
            JsonObject heads = read(zip, com.siberanka.twilight.geyser.CustomSkulls.PATH);
            assertEquals(skin, heads.getAsJsonArray("skins").get(0).getAsString());
            assertEquals("Notch", heads.getAsJsonArray("names").get(0).getAsString());
            JsonObject players = read(zip, com.siberanka.twilight.geyser.CustomSkulls.PLAYERS_PATH);
            assertEquals("bbbbbbbbbbbbbbbb", players.getAsJsonArray("skins").get(0).getAsString(), "most recent first");
            JsonObject index = read(zip, "twilight/display-index-v2.json");
            assertTrue(index.getAsJsonObject("heads").has(skin), index.toString());
            assertEquals(1, index.getAsJsonObject("heads").size(), "a skin that cannot be read gets no model");
        }
    }

    /**
     * Content plugins draw custom blocks through vanilla block states (ItemsAdder's and CraftEngine's note blocks).
     * Every state with a custom model becomes a Bedrock block named after the Java state; full cubes use the unit
     * cube with a texture per face, other shapes a block geometry; vanilla looks and invalid states are left alone,
     * and the item drawn like the block is linked to it (3D icon and drop).
     */
    @Test
    void convertsCustomBlockStatesAndLinksTheirItems() throws Exception {
        Path source = root.resolve("block-source");
        write(source, "assets/minecraft/blockstates/note_block.json", """
                {"variants":{
                  "instrument=harp":{"model":"block/note_block"},
                  "instrument=basedrum,note=3,powered=false":{"model":"demo:block/ruby_ore"},
                  "instrument=basedrum,note=4,powered=false":{"model":"demo:block/ruby_ore","y":90},
                  "instrument=snare,note=1,powered=false":{"model":"demo:block/plant"},
                  "instrument=bogus,note=1,powered=false":{"model":"demo:block/ruby_ore"}}}
                """);
        String cube = """
                {"textures":{"all":"demo:block/ruby_ore"},"elements":[{"from":[0,0,0],"to":[16,16,16],"faces":{
                  "north":{"texture":"#all","cullface":"north"},"south":{"texture":"#all"},"east":{"texture":"#all"},
                  "west":{"texture":"#all"},"up":{"texture":"#all"},"down":{"texture":"#all"}}}]}
                """;
        write(source, "assets/demo/models/block/ruby_ore.json", cube);
        // The item uses its own model file with the same shape and art, as ItemsAdder's do.
        write(source, "assets/demo/models/item/ruby_ore.json", cube.replace(",\"cullface\":\"north\"", ""));
        write(source, "assets/demo/models/block/plant.json", """
                {"textures":{"cross":"demo:block/plant"},"elements":[
                  {"from":[0.8,0,8],"to":[15.2,16,8],"rotation":{"origin":[8,8,8],"axis":"y","angle":45},
                   "faces":{"north":{"texture":"#cross"},"south":{"texture":"#cross"}}}]}
                """);
        write(source, "assets/minecraft/models/item/paper.json", """
                {"parent":"minecraft:item/generated","overrides":[
                  {"predicate":{"custom_model_data":5},"model":"demo:item/ruby_ore"}]}
                """);
        png(source.resolve("assets/demo/textures/block/ruby_ore.png"), Color.RED);
        png(source.resolve("assets/demo/textures/block/plant.png"), new Color(0, 255, 0, 0));
        ContentSource pack = new ContentSource("test", ContentSource.Kind.RESOURCE_PACK, source, 1);
        BuildResult result = new BedrockPackCompiler(root.resolve("data-blocks"), config()).build(List.of(pack), List.of());
        try (ZipFile zip = new ZipFile(result.outputDirectory().resolve("pack.zip").toFile())) {
            JsonObject file = JsonParser.parseString(new String(zip.getInputStream(zip.getEntry("twilight/geyser_blocks.json"))
                    .readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            java.util.Map<String, JsonObject> blocks = new java.util.LinkedHashMap<>();
            for (var block : file.getAsJsonArray("blocks")) blocks.put(block.getAsJsonObject().get("state").getAsString(), block.getAsJsonObject());
            assertEquals(java.util.Set.of(
                    "minecraft:note_block[instrument=basedrum,note=3,powered=false]",
                    "minecraft:note_block[instrument=basedrum,note=4,powered=false]",
                    "minecraft:note_block[instrument=snare,note=1,powered=false]"), blocks.keySet(),
                    "vanilla looks and states the block does not have stay vanilla");
            JsonObject ruby = blocks.get("minecraft:note_block[instrument=basedrum,note=3,powered=false]");
            assertEquals("minecraft:geometry.full_block", ruby.get("geometry").getAsString());
            assertEquals(6, ruby.getAsJsonObject("materials").size());
            assertEquals("opaque", ruby.getAsJsonObject("materials").getAsJsonObject("up").get("render_method").getAsString());
            assertEquals("[-8,0,-8,16,16,16]", ruby.get("selection").toString().replace(".0", ""));
            assertFalse(ruby.has("rotation"));
            assertEquals("[0,90]", blocks.get("minecraft:note_block[instrument=basedrum,note=4,powered=false]").get("rotation").toString());
            JsonObject plant = blocks.get("minecraft:note_block[instrument=snare,note=1,powered=false]");
            assertTrue(plant.get("geometry").getAsString().startsWith("geometry.twilight_b_"));
            assertEquals("alpha_test", plant.getAsJsonObject("materials").getAsJsonObject("*").get("render_method").getAsString());
            String geometries = new String(zip.getInputStream(zip.getEntry("models/blocks/twilight_blocks.geo.json")).readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(geometries.contains(plant.get("geometry").getAsString()));
            JsonObject terrain = JsonParser.parseString(new String(zip.getInputStream(zip.getEntry("textures/terrain_texture.json"))
                    .readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("texture_data");
            String upKey = ruby.getAsJsonObject("materials").getAsJsonObject("up").get("texture").getAsString();
            assertNotNull(zip.getEntry(terrain.getAsJsonObject(upKey).get("textures").getAsString() + ".png"));
            // Block names depend only on the Java state, so every backend names a state alike.
            assertEquals(CustomBlockCompiler.name("note_block", "minecraft:note_block[instrument=basedrum,note=3,powered=false]"),
                    ruby.get("name").getAsString());
            String paper = mappings(result).getAsJsonObject("items").getAsJsonArray("minecraft:paper").get(0).getAsJsonObject()
                    .get("bedrock_identifier").getAsString();
            assertEquals(ruby.get("name").getAsString(), file.getAsJsonObject("items").get(paper).getAsString());
        }
        BuildResult without = new BedrockPackCompiler(root.resolve("data-noblocks"), config()).withCustomBlocks(false)
                .build(List.of(pack), List.of());
        try (ZipFile zip = new ZipFile(without.outputDirectory().resolve("pack.zip").toFile())) {
            assertNull(zip.getEntry("twilight/geyser_blocks.json"));
        }
    }

    @Test
    void expandsPartialBlockStatesInJavaOrder() {
        var domain = CustomBlockCompiler.BLOCKS.get("note_block");
        assertEquals(List.of("instrument=basedrum,note=3,powered=false"),
                CustomBlockCompiler.expand(domain, "powered=false,note=3,instrument=basedrum"));
        assertEquals(23 * 2, CustomBlockCompiler.expand(domain, "note=3").size());
        assertEquals(23 * 25 * 2, CustomBlockCompiler.expand(domain, "").size());
        assertNull(CustomBlockCompiler.expand(domain, "instrument=bogus"));
        assertNull(CustomBlockCompiler.expand(domain, "color=red"));
    }

    private static JsonObject mappings(BuildResult result) throws Exception {
        return JsonParser.parseString(Files.readString(result.outputDirectory()
                .resolve("custom_mappings/geyser_item_mappings.json"))).getAsJsonObject();
    }

    @Test
    void resolvesSpritesThatTextureAtlasesRename() throws Exception {
        // ItemsAdder's generated packs reference atlas sprites such as "ia:2015" that a "single"
        // source renames from the real texture; "directory" sources may rename whole folders.
        // Java resolves both, so must the conversion.
        Path source = root.resolve("atlas-source");
        write(source, "assets/minecraft/models/item/paper.json", """
                {"parent":"minecraft:item/generated","overrides":[
                  {"predicate":{"custom_model_data":1},"model":"demo:item/lettuce"},
                  {"predicate":{"custom_model_data":2},"model":"demo:item/gem"}]}
                """);
        write(source, "assets/demo/models/item/lettuce.json", """
                {"parent":"minecraft:item/generated","textures":{"layer0":"ia:2015"}}
                """);
        // Object-form texture entries (1.21.6+) name their sprite in "sprite".
        write(source, "assets/demo/models/item/gem.json", """
                {"parent":"minecraft:item/generated","textures":{"layer0":{"sprite":"demo:shiny/gem","force_translucent":true}}}
                """);
        write(source, "assets/minecraft/atlases/items.json", """
                {"sources":[{"type":"single","resource":"demo:item/food/lettuce","sprite":"ia:2015"},
                            {"type":"directory","source":"item/gems","prefix":"shiny/"}]}
                """);
        png(source.resolve("assets/demo/textures/item/food/lettuce.png"), Color.GREEN);
        png(source.resolve("assets/demo/textures/item/gems/gem.png"), Color.MAGENTA);

        ContentSource pack = new ContentSource("test", ContentSource.Kind.RESOURCE_PACK, source, 1);
        BuildResult result = new BedrockPackCompiler(root.resolve("atlas-data"), config())
                .build(List.of(pack), List.of());
        assertTrue(result.problems().isEmpty(), result.problems().toString());
        assertEquals(2, result.converted());
    }

    @Test
    void facesWithUndefinedTexturesUseJavasMissingTexture() throws Exception {
        // Real packs (GearForge bows, cosmetics icons) leave "#missing" undefined; Java draws its
        // magenta-black texture on those faces and the rest of the model normally.
        Path source = root.resolve("missing-texture-source");
        write(source, "assets/minecraft/models/item/paper.json", """
                {"parent":"minecraft:item/generated","overrides":[{"predicate":{"custom_model_data":3},"model":"demo:item/half"}]}
                """);
        write(source, "assets/demo/models/item/half.json", """
                {"textures":{"all":"demo:item/half"},
                 "elements":[{"from":[4,4,4],"to":[12,12,12],"faces":{
                   "north":{"texture":"#all"},"south":{"texture":"#missing"},"east":{"texture":"#all"},
                   "west":{"texture":"#missing"},"up":{"texture":"#all"},"down":{"texture":"#all"}}}]}
                """);
        png(source.resolve("assets/demo/textures/item/half.png"), Color.CYAN);
        BuildResult result = new BedrockPackCompiler(root.resolve("missing-texture-data"), config())
                .build(List.of(new ContentSource("test", ContentSource.Kind.RESOURCE_PACK, source, 1)), List.of());
        assertTrue(result.problems().isEmpty(), result.problems().toString());
        assertEquals(1, result.converted());
        java.awt.image.BufferedImage missing = TextureSet.missingTexture();
        assertEquals(0xFF000000, missing.getRGB(0, 0));
        assertEquals(0xFFF800F8, missing.getRGB(8, 0));
        JsonObject report = JsonParser.parseString(Files.readString(result.outputDirectory().resolve("build-report.json")))
                .getAsJsonObject();
        assertTrue(report.getAsJsonArray("notices").toString().contains("missing texture"), report.toString());
    }

    @Test
    void customSelectorsMayShowPlainVanillaModels() throws Exception {
        // Menu packs map custom model data to vanilla models (a barrier as "closed" button); only the
        // selector is custom. The vanilla model comes from the verified client, as on Java.
        Path source = root.resolve("vanilla-target-source");
        write(source, "assets/minecraft/models/item/paper.json", """
                {"parent":"minecraft:item/generated","overrides":[{"predicate":{"custom_model_data":9},"model":"minecraft:item/barrier"}]}
                """);
        Path data = root.resolve("vanilla-target-data");
        seedClientCache(data, Map.of(
                "assets/minecraft/models/item/barrier.json",
                "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"minecraft:item/barrier\"}}"
                        .getBytes(StandardCharsets.UTF_8),
                "assets/minecraft/models/item/generated.json", "{}".getBytes(StandardCharsets.UTF_8),
                "assets/minecraft/textures/item/barrier.png", pngBytes(Color.RED)));
        BuildResult result = new BedrockPackCompiler(data, config(), "26.2").build(List.of(
                new ContentSource("test", ContentSource.Kind.RESOURCE_PACK, source, 1)), List.of());
        assertTrue(result.problems().isEmpty(), result.problems().toString());
        assertEquals(1, result.converted());
    }

    @Test
    void leavesSpecialRendererItemsToBedrocksNativeRendering() throws Exception {
        // Player-skin heads (emote rigs, head plugins) have neither elements nor layers: Java's special
        // renderer draws the owner's skin, as Geyser does natively. They are noted, not failed.
        Path source = root.resolve("special-source");
        write(source, "assets/minecraft/models/item/player_head.json", """
                {"parent":"minecraft:builtin/entity","overrides":[{"predicate":{"custom_model_data":4},"model":"demo:entity/player/head"}]}
                """);
        write(source, "assets/demo/models/entity/player/head.json", """
                {"parent":"minecraft:builtin/entity","display":{"head":{"scale":[1,1,1]}}}
                """);
        BuildResult result = new BedrockPackCompiler(root.resolve("special-data"), config())
                .build(List.of(new ContentSource("test", ContentSource.Kind.RESOURCE_PACK, source, 1)), List.of());
        assertTrue(result.problems().isEmpty(), result.problems().toString());
        assertEquals(0, result.converted());
        JsonObject report = JsonParser.parseString(Files.readString(result.outputDirectory().resolve("build-report.json")))
                .getAsJsonObject();
        assertTrue(report.getAsJsonArray("notices").toString().contains("special renderer"), report.toString());
    }

    @Test
    void writesTheVanillaBiomeAppearanceTableFromTheClient() throws Exception {
        Path source = root.resolve("biome-source");
        write(source, "assets/demo/models/item/x.json", "{}");
        Path data = root.resolve("biome-data");
        java.awt.image.BufferedImage map = new java.awt.image.BufferedImage(256, 256, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 256; y++) for (int x = 0; x < 256; x++) map.setRGB(x, y, 0xFF000000 | x << 16 | y << 8);
        java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
        ImageIO.write(map, "PNG", png);
        seedClientCache(data, Map.of(
                "data/minecraft/worldgen/biome/plains.json", """
                        {"temperature":0.8,"downfall":0.4,"has_precipitation":true,"effects":{"water_color":4159204}}
                        """.getBytes(StandardCharsets.UTF_8),
                "data/minecraft/worldgen/biome/swamp.json", """
                        {"temperature":0.8,"downfall":0.9,"has_precipitation":true,
                         "effects":{"water_color":6388580,"grass_color_modifier":"swamp","foliage_color":6975545}}
                        """.getBytes(StandardCharsets.UTF_8),
                "assets/minecraft/textures/colormap/grass.png", png.toByteArray(),
                "assets/minecraft/textures/colormap/foliage.png", png.toByteArray()));
        write(source, "data/demo/worldgen/biome/blossom_vale.json", """
                {"temperature":0.7,"downfall":0.8,"has_precipitation":true,
                 "effects":{"grass_color":"#f29ac0","foliage_color":"#e36fa4","water_color":"#5db7ef"},
                 "attributes":{"minecraft:visual/sky_color":"#9cc8ff","minecraft:visual/fog_color":"#ffd8ec"}}
                """);
        // RealisticSeasons registers its seasonal biomes at runtime: they come from the server registry.
        Map<String, Map<String, ?>> registry = Map.of("realisticseasons:plains_winter", Map.of("temperature", 0.05,
                "downfall", 0.4, "effects", Map.of("grass_color", 0xB8D0E1, "foliage_color", 0xB8D0E1)));
        BuildResult result = new BedrockPackCompiler(data, config(), "26.2").withServerBiomes(registry)
                .withNameplatePlugin(true).build(List.of(
                new ContentSource("test", ContentSource.Kind.RESOURCE_PACK, source, 1)), List.of());
        try (ZipFile zip = new ZipFile(result.outputDirectory().resolve("pack.zip").toFile())) {
            var table = com.siberanka.twilight.world.BiomeMatcher.fromJson(read(zip, com.siberanka.twilight.world.BiomeMatcher.PATH));
            assertEquals(java.util.Set.of("minecraft:plains", "minecraft:swamp"), table.vanilla().keySet());
            assertEquals(0x6A7039, table.vanilla().get("minecraft:swamp").grass());
            assertEquals(0x3F76E4, table.vanilla().get("minecraft:plains").water());
            var slots = com.siberanka.twilight.world.BiomeSlots.fromJson(read(zip, com.siberanka.twilight.world.BiomeSlots.PATH));
            assertEquals(List.of(List.of("demo:blossom_vale"), List.of("realisticseasons:plains_winter")),
                    slots.slots().stream().map(com.siberanka.twilight.world.BiomeSlots.Slot::biomes).toList());
            assertEquals(2, slots.slots().get(1).look().precipitation(), "a cold seasonal biome snows");
            JsonObject grass = read(zip, "biomes/taiga_hills.client_biome.json").getAsJsonObject("minecraft:client_biome")
                    .getAsJsonObject("components").getAsJsonObject("minecraft:grass_appearance");
            assertEquals("#f29ac0", grass.get("color").getAsString());
            assertNotNull(zip.getEntry("fogs/twilight_desert_hills.json"));
            JsonObject material = read(zip, "materials/ui3D.material").getAsJsonObject("materials").getAsJsonObject("name_tag");
            assertEquals("One", material.get("blendDst").getAsString(), "CustomNameplates draws its own tag backgrounds");
        }
    }

    @Test
    void mergesResourcePackTranslationsLikeJava() throws Exception {
        Path lower = root.resolve("lang-lower"), upper = root.resolve("lang-upper");
        write(lower, "assets/dnt/lang/en_us.json", """
                {"item.dnt.relic":"Relic","container.inventory":"Inventory"}
                """);
        write(upper, "assets/minecraft/lang/en_us.json", """
                {"container.inventory":" ","container.enderchest":"\u00a7f\ue2ab"}
                """);
        write(upper, "assets/dnt/lang/tr_tr.json", """
                {"item.dnt.relic":"Kalinti"}
                """);
        write(upper, "assets/broken/lang/de_de.json", "{not json");
        BuildResult result = new BedrockPackCompiler(root.resolve("lang-data"), config()).build(List.of(
                new ContentSource("lower", ContentSource.Kind.RESOURCE_PACK, lower, 1),
                new ContentSource("upper", ContentSource.Kind.RESOURCE_PACK, upper, 2)), List.of());
        assertTrue(result.problems().isEmpty(), result.problems().toString());
        try (ZipFile zip = new ZipFile(result.outputDirectory().resolve("pack.zip").toFile())) {
            JsonObject english = read(zip, "twilight/lang/en_us.json");
            assertEquals(" ", english.get("container.inventory").getAsString(), "the higher pack wins");
            assertEquals("Relic", english.get("item.dnt.relic").getAsString());
            assertEquals("Kalinti", read(zip, "twilight/lang/tr_tr.json").get("item.dnt.relic").getAsString());
            String bedrock = new String(zip.getInputStream(zip.getEntry("texts/en_US.lang")).readAllBytes(), StandardCharsets.UTF_8);
            assertEquals("container.inventory=\u00a7r\t#\n", bedrock, "a blank label stays blank on Bedrock");
            assertNull(zip.getEntry("texts/tr_TR.lang"), "no Bedrock UI key overridden in Turkish");
        }
        JsonObject report = JsonParser.parseString(Files.readString(result.outputDirectory().resolve("build-report.json")))
                .getAsJsonObject();
        assertTrue(report.getAsJsonArray("notices").toString().contains("de_de"), report.toString());
    }

    @Test
    void preservesLargeModelJavaHandTransformWithoutImplicitFitting() throws Exception {
        Path source = root.resolve("wide-model-source");
        write(source, "assets/demo/items/wide.json", """
                {"model":{"type":"minecraft:model","model":"demo:item/wide"}}
                """);
        write(source, "assets/demo/models/item/wide.json", """
                {"textures":{"all":"demo:item/wide"},
                 "display":{"firstperson_righthand":{"rotation":[-163,75,170],
                   "translation":[0.5,4,-2],"scale":[0.5,0.5,0.5]}},
                 "elements":[{"from":[-32,-16,-24],"to":[48,40,32],"faces":{
                   "north":{"texture":"#all"},"south":{"texture":"#all"},
                   "east":{"texture":"#all"},"west":{"texture":"#all"},
                   "up":{"texture":"#all"},"down":{"texture":"#all"}}}]}
                """);
        png(source.resolve("assets/demo/textures/item/wide.png"), Color.MAGENTA);
        CustomItemDescriptor live = new CustomItemDescriptor("test", "minecraft:crossbow",
                Optional.of("demo:wide"), OptionalInt.empty(), "Wide model");

        BuildResult result = new BedrockPackCompiler(root.resolve("wide-model-data"), config()).build(
                List.of(new ContentSource("test", ContentSource.Kind.RESOURCE_PACK, source, 1)), List.of(live));

        try (ZipFile zip = new ZipFile(result.outputDirectory().resolve("pack.zip").toFile())) {
            String animationName = zip.stream()
                    .map(ZipEntry::getName)
                    .filter(name -> name.startsWith("animations/") && name.endsWith(".animation.json"))
                    .findFirst().orElseThrow();
            JsonObject definitions = read(zip, animationName).getAsJsonObject("animations");
            JsonObject firstPerson = definitions.entrySet().stream()
                    .filter(entry -> entry.getKey().endsWith(".first_person_right"))
                    .findFirst().orElseThrow().getValue().getAsJsonObject();
            JsonArray scale = firstPerson.getAsJsonObject("bones").getAsJsonObject("bone")
                    .getAsJsonArray("scale");
            JsonArray position = firstPerson.getAsJsonObject("bones").getAsJsonObject("bone")
                    .getAsJsonArray("position");
            assertEquals(0.5, scale.get(0).getAsDouble(), 1.0e-9);
            assertEquals(0.5, scale.get(1).getAsDouble(), 1.0e-9);
            assertEquals(0.5, scale.get(2).getAsDouble(), 1.0e-9);
            // The authored translation as is (no fitting), only carried into the hand frame.
            double[] offset = firstPersonViewInverse(new double[]{0.5 + 1.5, 4 - 1.5, -2 + 3});
            assertEquals(offset[0], position.get(0).getAsDouble(), 1.0e-9);
            assertEquals(12.5 + offset[1], position.get(1).getAsDouble(), 1.0e-9);
            assertEquals(-offset[2], position.get(2).getAsDouble(), 1.0e-9);
        }
    }

    @Test
    void derivesHandheldPoseFromTheResolvedJavaModelInsteadOfTheBaseItem() throws Exception {
        Path source = root.resolve("model-parent-pose-source");
        write(source, "assets/demo/models/item/axe_up.json", """
                {"parent":"minecraft:item/handheld","textures":{"all":"demo:item/axe_up"},
                 "display":{
                   "firstperson_righthand":{"rotation":[17,-90,0],"translation":[2.6,7,-0.008],"scale":[1.6,1.6,1.6]},
                   "thirdperson_righthand":{"rotation":[0,-90,0],"translation":[0,7,1],"scale":[2.4,2.4,2.4]}},
                 "elements":[{"from":[2,0,7],"to":[14,16,9],"faces":{
                   "north":{"texture":"#all"},"south":{"texture":"#all"},
                   "east":{"texture":"#all"},"west":{"texture":"#all"},
                   "up":{"texture":"#all"},"down":{"texture":"#all"}}}]}
                """);
        write(source, "assets/demo/models/item/generated_box.json", """
                {"parent":"minecraft:item/generated","textures":{"all":"demo:item/generated_box"},
                 "elements":[{"from":[2,0,7],"to":[14,16,9],"faces":{
                   "north":{"texture":"#all"},"south":{"texture":"#all"},
                   "east":{"texture":"#all"},"west":{"texture":"#all"},
                   "up":{"texture":"#all"},"down":{"texture":"#all"}}}]}
                """);
        png(source.resolve("assets/demo/textures/item/axe_up.png"), Color.ORANGE);
        png(source.resolve("assets/demo/textures/item/generated_box.png"), Color.GREEN);
        List<CustomItemDescriptor> live = List.of(
                new CustomItemDescriptor("test", "minecraft:paper", Optional.of("demo:item/axe_up"),
                        OptionalInt.empty(), "Upright axe"),
                new CustomItemDescriptor("test", "minecraft:iron_axe", Optional.of("demo:item/generated_box"),
                        OptionalInt.empty(), "Generated box"));

        BuildResult result = new BedrockPackCompiler(root.resolve("model-parent-pose-data"), config()).build(
                List.of(new ContentSource("test", ContentSource.Kind.RESOURCE_PACK, source, 1)), live);
        JsonObject items = JsonParser.parseString(Files.readString(result.outputDirectory()
                .resolve("custom_mappings/geyser_item_mappings.json"))).getAsJsonObject().getAsJsonObject("items");
        JsonObject paper = items.getAsJsonArray("minecraft:paper").get(0).getAsJsonObject();
        JsonObject ironAxe = items.getAsJsonArray("minecraft:iron_axe").get(0).getAsJsonObject();
        assertTrue(paper.getAsJsonObject("bedrock_options").get("display_handheld").getAsBoolean());
        assertFalse(ironAxe.getAsJsonObject("bedrock_options").has("display_handheld"));

        String safe = paper.get("bedrock_identifier").getAsString().substring("twilight:".length());
        try (ZipFile zip = new ZipFile(result.outputDirectory().resolve("pack.zip").toFile())) {
            JsonObject animations = read(zip, "animations/" + safe + ".animation.json")
                    .getAsJsonObject("animations");
            JsonObject first = animations.getAsJsonObject("animation.twilight." + safe + ".first_person_right")
                    .getAsJsonObject("bones").getAsJsonObject("bone");
            JsonObject third = animations.getAsJsonObject("animation.twilight." + safe + ".third_person_right")
                    .getAsJsonObject("bones").getAsJsonObject("bone");
            // Java's translation is in its view frame; the hand bone sees that frame turned (firstPersonView).
            double[] offset = firstPersonViewInverse(new double[]{2.6 + 1.5, 7 - 1.5, -0.008 + 3});
            JsonArray position = first.getAsJsonArray("position");
            assertEquals(offset[0], position.get(0).getAsDouble(), 1.0e-9);
            assertEquals(12.5 + offset[1], position.get(1).getAsDouble(), 1.0e-9);
            assertEquals(-offset[2], position.get(2).getAsDouble(), 1.0e-9);
            assertVector(first.getAsJsonArray("scale"), 1.6, 1.6, 1.6);
            assertVector(third.getAsJsonArray("position"), 0.0, 13.5, -7.0);
            assertVector(third.getAsJsonArray("scale"), 2.4, 2.4, 2.4);
        }
    }

    @Test
    void thirdPersonYawDoesNotTurnTheModelHeightAxisSideways() throws Exception {
        Path source = root.resolve("third-person-axis-source");
        List<CustomItemDescriptor> items = new java.util.ArrayList<>();
        // Yaw turns the faces around the handle, never the handle itself.
        // Exercise both quarter turns and intermediate angles, including the
        // inherited handheld yaw that previously made an upright tool horizontal.
        for (int yaw : new int[]{-180, -90, -45, 0, 45, 90, 180}) {
            String model = "demo:item/yaw_" + (yaw + 180);
            write(source, "assets/demo/models/item/yaw_" + (yaw + 180) + ".json", """
                    {"textures":{"all":"demo:item/axis"},
                     "display":{"thirdperson_righthand":{"rotation":[0,%d,0]}},
                     "elements":[{"from":[7,0,7],"to":[9,16,9],"faces":{
                       "north":{"texture":"#all"},"south":{"texture":"#all"}}}]}
                    """.formatted(yaw));
            items.add(new CustomItemDescriptor("test", "minecraft:paper", Optional.of(model),
                    OptionalInt.empty(), "Axis test"));
        }
        png(source.resolve("assets/demo/textures/item/axis.png"), Color.ORANGE);
        BuildResult result = new BedrockPackCompiler(root.resolve("third-person-axis-data"), config()).build(
                List.of(new ContentSource("test", ContentSource.Kind.RESOURCE_PACK, source, 1)), items);
        assertEquals(items.size(), result.converted());
        try (ZipFile zip = new ZipFile(result.outputDirectory().resolve("pack.zip").toFile())) {
            for (ZipEntry entry : zip.stream().filter(e -> e.getName().startsWith("animations/")).toList()) {
                JsonObject definitions = read(zip, entry.getName()).getAsJsonObject("animations");
                for (var definition : definitions.entrySet()) {
                    if (!definition.getKey().endsWith(".third_person_right")) continue;
                    JsonArray rotation = definition.getValue().getAsJsonObject().getAsJsonObject("bones")
                            .getAsJsonObject("bone").getAsJsonArray("rotation");
                    double x = Math.toRadians(rotation.get(0).getAsDouble());
                    double y = Math.toRadians(rotation.get(1).getAsDouble());
                    double z = Math.toRadians(rotation.get(2).getAsDouble());
                    // Transform the unit-height vector by the exported Euler
                    // rotation. Hand-space height points along Z at neutral roll.
                    double heightX = Math.cos(z) * Math.sin(y) * Math.sin(x) - Math.sin(z) * Math.cos(x);
                    double heightY = Math.sin(z) * Math.sin(y) * Math.sin(x) + Math.cos(z) * Math.cos(x);
                    double heightZ = Math.cos(y) * Math.sin(x);
                    assertEquals(0, heightX, 1.0e-9, definition.getKey());
                    assertEquals(0, heightY, 1.0e-9, definition.getKey());
                    assertEquals(1, heightZ, 1.0e-9, definition.getKey());
                }
            }
        }
    }

    @Test
    void thirdPersonOrientationMatchesJavaForAsymmetricAndCompoundPoses() throws Exception {
        int[][] poses = {{0, 0, 0}, {0, -90, 55}, {25, -50, 35}, {20, 90, 30}};
        for (int i = 0; i < poses.length; i++) {
            int[] pose = poses[i];
            JsonObject animations = compilePoseAnimations("java_basis_" + i, """
                    {"thirdperson_righthand":{"rotation":[%d,%d,%d]}}
                    """.formatted(pose[0], pose[1], pose[2]));
            JsonArray exported = poseBone(animations, "third_person_right").getAsJsonArray("rotation");
            for (double[] axis : new double[][]{{1, 0, 0}, {0, 1, 0}, {0, 0, 1}}) {
                // Java display applies intrinsic XYZ, after the held-item -90 X frame.
                double[] expected = rotateAxis(axis, 2, pose[2]);
                expected = rotateAxis(expected, 1, pose[1]);
                expected = rotateAxis(expected, 0, pose[0] - 90);
                // Our geometry reflects Z. Bedrock's rendered coordinates reflect
                // X, so the source model enters the bone with X/Z both reversed.
                double[] actual = {-axis[0], axis[1], -axis[2]};
                actual = rotateAxis(actual, 0, -exported.get(0).getAsDouble());
                actual = rotateAxis(actual, 1, -exported.get(1).getAsDouble());
                actual = rotateAxis(actual, 2, exported.get(2).getAsDouble());
                assertArrayEquals(expected, actual, 1.0e-9, "pose " + i);
            }
        }
    }

    @Test
    void mirrorsExplicitLeftHandTransformsLikeTheJavaRenderer() throws Exception {
        JsonObject animations = compilePoseAnimations("explicit_left", """
                {"firstperson_righthand":{"rotation":[17,-32,-21],"translation":[-2,3,4]},
                 "firstperson_lefthand":{"rotation":[17,32,21],"translation":[2,3,4]},
                 "thirdperson_righthand":{"rotation":[17,-32,-21],"translation":[-2,3,4]},
                 "thirdperson_lefthand":{"rotation":[17,32,21],"translation":[2,3,4]}}
                """);
        // Java applies the left-hand X-translation/YZ-rotation sign change
        // even when a pack explicitly supplies a left-hand display context.
        for (String perspective : List.of("first_person", "third_person")) {
            assertEquals(poseBone(animations, perspective + "_right"),
                    poseBone(animations, perspective + "_left"), perspective);
        }
    }

    @Test
    void firstPersonOrientationMatchesJavaIncludingEulerSingularities() throws Exception {
        int[][] poses = {{0, 0, 0}, {30, 0, 0}, {0, 30, 0}, {0, 0, 30}, {0, -90, 25}, {-90, 0, 0}, {90, 0, 0},
                {25, -50, 35}};
        for (int i = 0; i < poses.length; i++) {
            int[] pose = poses[i];
            JsonObject animations = compilePoseAnimations("first_basis_" + i, """
                    {"firstperson_righthand":{"rotation":[%d,%d,%d]}}
                    """.formatted(pose[0], pose[1], pose[2]));
            JsonArray exported = poseBone(animations, "first_person_right").getAsJsonArray("rotation");
            for (double[] axis : new double[][]{{1, 0, 0}, {0, 1, 0}, {0, 0, 1}}) {
                // Java first person: the display rotation (intrinsic XYZ) straight in the view frame.
                double[] expected = rotateAxis(axis, 2, pose[2]);
                expected = rotateAxis(expected, 1, pose[1]);
                expected = rotateAxis(expected, 0, pose[0]);
                // Bedrock: the bone turns the model with X mirrored (measured), then the hand shows the bone
                // turned by the first person view.
                double[] actual = {-axis[0], axis[1], axis[2]};
                actual = rotateAxis(actual, 0, exported.get(0).getAsDouble());
                actual = rotateAxis(actual, 1, exported.get(1).getAsDouble());
                actual = rotateAxis(actual, 2, exported.get(2).getAsDouble());
                actual[0] = -actual[0];
                actual = firstPersonView(actual);
                assertArrayEquals(expected, actual, 1.0e-9, "pose " + i);
            }
        }
    }

    /** Bedrock's first person hand frame in Java's view frame, as measured (BedrockPackCompiler.FIRST_PERSON_VIEW). */
    private static double[] firstPersonView(double[] v) {
        double[] result = rotateAxis(v, 0, 90);
        result = rotateAxis(result, 2, -135);
        result = rotateAxis(result, 1, -25);
        result = rotateAxis(result, 0, 20);
        return rotateAxis(result, 2, 15);
    }

    private static double[] firstPersonViewInverse(double[] v) {
        double[] result = rotateAxis(v, 2, -15);
        result = rotateAxis(result, 0, -20);
        result = rotateAxis(result, 1, 25);
        result = rotateAxis(result, 2, 135);
        return rotateAxis(result, 0, -90);
    }

    @Test
    void givesChangedFlatWeaponsJavaTransformsAndRuntimeStates() throws Exception {
        Path source = root.resolve("native-flat-source");
        write(source, "assets/minecraft/models/item/bow.json", """
                {"overrides":[
                  {"predicate":{"custom_model_data":100},"model":"demo:item/astral_bow"},
                  {"predicate":{"custom_model_data":100,"pulling":1,"pull":0.0},"model":"demo:item/astral_bow_0"},
                  {"predicate":{"custom_model_data":100,"pulling":1,"pull":0.65},"model":"demo:item/astral_bow_1"},
                  {"predicate":{"custom_model_data":100,"pulling":1,"pull":0.9},"model":"demo:item/astral_bow_2"}]}
                """);
        for (String name : List.of("astral_bow", "astral_bow_0", "astral_bow_1", "astral_bow_2")) {
            write(source, "assets/demo/models/item/" + name + ".json",
                    "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"demo:item/" + name + "\"}}");
            png(source.resolve("assets/demo/textures/item/" + name + ".png"), Color.CYAN);
        }
        write(source, "assets/minecraft/models/item/fishing_rod.json", """
                {"overrides":[
                  {"predicate":{"custom_model_data":101},"model":"demo:item/astral_rod"},
                  {"predicate":{"custom_model_data":101,"cast":1},"model":"demo:item/astral_rod_cast"}]}
                """);
        for (String name : List.of("astral_rod", "astral_rod_cast")) {
            write(source, "assets/demo/models/item/" + name + ".json",
                    "{\"parent\":\"minecraft:item/handheld_rod\",\"textures\":{\"layer0\":\"demo:item/" + name + "\"}}");
            png(source.resolve("assets/demo/textures/item/" + name + ".png"), Color.BLUE);
        }

        BuildResult result = new BedrockPackCompiler(root.resolve("native-flat-data"), config()).build(List.of(
                new ContentSource("test", ContentSource.Kind.RESOURCE_PACK, source, 1)), List.of());

        assertEquals(3, result.converted());
        assertEquals(0, result.threeDimensional());
        try (ZipFile zip = new ZipFile(result.outputDirectory().resolve("pack.zip").toFile())) {
            assertEquals(6, zip.stream().map(ZipEntry::getName)
                    .filter(name -> name.startsWith("models/entity/geometry.")).count());
            assertEquals(3, zip.stream().map(ZipEntry::getName)
                    .filter(name -> name.startsWith("attachables/")).count());
            String bowAttachable = zip.stream().map(ZipEntry::getName).filter(name -> name.startsWith("attachables/"))
                    .filter(name -> {
                        try { return read(zip, name).toString().contains("twilight_model_index"); }
                        catch (Exception failure) { throw new RuntimeException(failure); }
                    }).findFirst().orElseThrow();
            JsonObject description = read(zip, bowAttachable).getAsJsonObject("minecraft:attachable")
                    .getAsJsonObject("description");
            assertTrue(description.getAsJsonObject("geometry").get("state_3").getAsString()
                    .startsWith("geometry.twilight."));
            assertTrue(description.getAsJsonArray("render_controllers").get(0).getAsString()
                    .startsWith("controller.render.twilight."));
        }
        JsonObject mappings = JsonParser.parseString(Files.readString(result.outputDirectory()
                .resolve("custom_mappings/geyser_item_mappings.json"))).getAsJsonObject().getAsJsonObject("items");
        JsonArray bow = mappings.getAsJsonArray("minecraft:bow");
        assertEquals(1, bow.size());
        assertFalse(bow.get(0).getAsJsonObject().getAsJsonObject("bedrock_options").has("display_handheld"));
        JsonArray rods = mappings.getAsJsonArray("minecraft:fishing_rod");
        assertEquals(2, rods.size());
        assertTrue(rods.asList().stream().map(JsonElement::getAsJsonObject)
                .allMatch(mapping -> mapping.getAsJsonObject("bedrock_options")
                        .get("display_handheld").getAsBoolean()));
        assertTrue(rods.asList().stream().map(JsonElement::getAsJsonObject)
                .anyMatch(mapping -> mapping.has("predicate") && mapping.get("predicate").toString().contains("fishing_rod_cast")));
    }

    @Test
    void keepsOnlyExactSilhouetteBowRecoloursOnVanillaBedrockGeometry() throws Exception {
        Path source = root.resolve("verified-native-bow-source");
        String bowDisplay = """
                "display":{
                  "thirdperson_righthand":{"rotation":[-80,260,-40],"translation":[-1,-2,2.5],"scale":[0.9,0.9,0.9]},
                  "thirdperson_lefthand":{"rotation":[-80,-280,40],"translation":[-1,-2,2.5],"scale":[0.9,0.9,0.9]},
                  "firstperson_righthand":{"rotation":[0,-90,25],"translation":[1.13,3.2,1.13],"scale":[0.68,0.68,0.68]},
                  "firstperson_lefthand":{"rotation":[0,90,-25],"translation":[1.13,3.2,1.13],"scale":[0.68,0.68,0.68]}}
                """;
        write(source, "assets/minecraft/models/item/bow.json", """
                {"parent":"minecraft:item/generated","textures":{"layer0":"minecraft:item/bow"},%s,
                 "overrides":[
                  {"predicate":{"custom_model_data":400},"model":"demo:item/recolour"},
                  {"predicate":{"custom_model_data":400,"pulling":1,"pull":0.0},"model":"demo:item/recolour_0"},
                  {"predicate":{"custom_model_data":400,"pulling":1,"pull":0.65},"model":"demo:item/recolour_1"},
                  {"predicate":{"custom_model_data":400,"pulling":1,"pull":0.9},"model":"demo:item/recolour_2"}]}
                """.formatted(bowDisplay));
        for (String name : List.of("recolour", "recolour_0", "recolour_1", "recolour_2")) {
            write(source, "assets/demo/models/item/" + name + ".json",
                    "{\"parent\":\"minecraft:item/bow\",\"textures\":{\"layer0\":\"demo:item/" + name + "\"}}");
            png(source.resolve("assets/demo/textures/item/" + name + ".png"), Color.MAGENTA);
        }
        write(source, "assets/minecraft/models/item/fishing_rod.json", """
                {"overrides":[
                  {"predicate":{"custom_model_data":401},"model":"demo:item/recolour_rod"},
                  {"predicate":{"custom_model_data":401,"cast":1},"model":"demo:item/recolour_rod_cast"}]}
                """);
        for (String name : List.of("recolour_rod", "recolour_rod_cast")) {
            write(source, "assets/demo/models/item/" + name + ".json",
                    "{\"parent\":\"minecraft:item/handheld_rod\",\"textures\":{\"layer0\":\"demo:item/" + name + "\"}}");
            png(source.resolve("assets/demo/textures/item/" + name + ".png"), Color.BLUE);
        }
        String crossbowDisplay = """
                "display":{
                  "thirdperson_righthand":{"rotation":[-90,0,-60],"translation":[2,0.1,-3],"scale":[0.9,0.9,0.9]},
                  "thirdperson_lefthand":{"rotation":[-90,0,30],"translation":[2,0.1,-3],"scale":[0.9,0.9,0.9]},
                  "firstperson_righthand":{"rotation":[-90,0,-55],"translation":[1.13,3.2,1.13],"scale":[0.68,0.68,0.68]},
                  "firstperson_lefthand":{"rotation":[-90,0,35],"translation":[1.13,3.2,1.13],"scale":[0.68,0.68,0.68]}}
                """;
        write(source, "assets/minecraft/models/item/crossbow.json", """
                {"parent":"minecraft:item/generated","textures":{"layer0":"minecraft:item/crossbow_standby"},%s,
                 "overrides":[
                  {"predicate":{"custom_model_data":402},"model":"demo:item/crossbow"},
                  {"predicate":{"custom_model_data":402,"pulling":1,"pull":0.0},"model":"demo:item/crossbow_pulling_0"},
                  {"predicate":{"custom_model_data":402,"pulling":1,"pull":0.58},"model":"demo:item/crossbow_pulling_1"},
                  {"predicate":{"custom_model_data":402,"pulling":1,"pull":1.0},"model":"demo:item/crossbow_pulling_2"},
                  {"predicate":{"custom_model_data":402,"charged":1},"model":"demo:item/crossbow_arrow"},
                  {"predicate":{"custom_model_data":402,"charged":1,"firework":1},"model":"demo:item/crossbow_firework"}]}
                """.formatted(crossbowDisplay));
        for (String name : List.of("crossbow", "crossbow_pulling_0", "crossbow_pulling_1",
                "crossbow_pulling_2", "crossbow_arrow", "crossbow_firework")) {
            write(source, "assets/demo/models/item/" + name + ".json",
                    "{\"parent\":\"minecraft:item/crossbow\",\"textures\":{\"layer0\":\"demo:item/" + name + "\"}}");
            png(source.resolve("assets/demo/textures/item/" + name + ".png"), Color.GREEN);
        }

        Map<String, byte[]> vanilla = new LinkedHashMap<>();
        vanilla.put("assets/minecraft/models/item/bow.json", ("{\"parent\":\"minecraft:item/generated\"," +
                "\"textures\":{\"layer0\":\"minecraft:item/bow\"}," + bowDisplay + '}').getBytes(StandardCharsets.UTF_8));
        String[] vanillaNames = {"bow", "bow_pulling_0", "bow_pulling_1", "bow_pulling_2"};
        for (int index = 0; index < vanillaNames.length; index++) {
            String name = vanillaNames[index];
            if (index > 0) vanilla.put("assets/minecraft/models/item/" + name + ".json",
                    ("{\"parent\":\"minecraft:item/bow\",\"textures\":{\"layer0\":\"minecraft:item/" + name + "\"}}").getBytes(StandardCharsets.UTF_8));
            vanilla.put("assets/minecraft/textures/item/" + name + ".png", pngBytes(Color.WHITE));
        }
        for (String name : List.of("fishing_rod", "fishing_rod_cast")) {
            vanilla.put("assets/minecraft/models/item/" + name + ".json",
                    ("{\"parent\":\"minecraft:item/handheld_rod\",\"textures\":{\"layer0\":\"minecraft:item/" + name + "\"}}").getBytes(StandardCharsets.UTF_8));
            vanilla.put("assets/minecraft/textures/item/" + name + ".png", pngBytes(Color.WHITE));
        }
        vanilla.put("assets/minecraft/models/item/crossbow.json", ("{\"parent\":\"minecraft:item/generated\"," +
                "\"textures\":{\"layer0\":\"minecraft:item/crossbow_standby\"}," + crossbowDisplay + '}').getBytes(StandardCharsets.UTF_8));
        for (String name : List.of("crossbow_pulling_0", "crossbow_pulling_1", "crossbow_pulling_2",
                "crossbow_arrow", "crossbow_firework")) {
            vanilla.put("assets/minecraft/models/item/" + name + ".json",
                    ("{\"parent\":\"minecraft:item/crossbow\",\"textures\":{\"layer0\":\"minecraft:item/" + name + "\"}}").getBytes(StandardCharsets.UTF_8));
        }
        for (String name : List.of("crossbow_standby", "crossbow_pulling_0", "crossbow_pulling_1",
                "crossbow_pulling_2", "crossbow_arrow", "crossbow_firework")) {
            vanilla.put("assets/minecraft/textures/item/" + name + ".png", pngBytes(Color.WHITE));
        }
        Path data = root.resolve("verified-native-bow-data");
        seedClientCache(data, vanilla);

        BuildResult recolour = new BedrockPackCompiler(data, config(), "26.2").build(List.of(
                new ContentSource("test", ContentSource.Kind.RESOURCE_PACK, source, 1)), List.of());
        assertEquals(6, recolour.converted());
        try (ZipFile zip = new ZipFile(recolour.outputDirectory().resolve("pack.zip").toFile())) {
            assertEquals(0, zip.stream().filter(entry -> entry.getName().startsWith("models/entity/geometry.")).count());
            assertEquals(4, zip.stream().filter(entry -> entry.getName().startsWith("attachables/")).count());
            String attachable = zip.stream().map(ZipEntry::getName)
                    .filter(name -> name.startsWith("attachables/"))
                    .filter(name -> {
                        try { return read(zip, name).toString().contains("geometry.bow_pulling_2"); }
                        catch (Exception failure) { throw new RuntimeException(failure); }
                    }).findFirst().orElseThrow();
            assertTrue(read(zip, attachable).toString().contains("geometry.bow_pulling_2"));
            assertEquals(3, zip.stream().map(ZipEntry::getName)
                    .filter(name -> name.startsWith("attachables/"))
                    .filter(name -> {
                        try { return read(zip, name).toString().contains("geometry.crossbow_arrow"); }
                        catch (Exception failure) { throw new RuntimeException(failure); }
                    }).count());
        }

        Path animationMetadata = source.resolve("assets/demo/textures/item/recolour_0.png.mcmeta");
        write(source, "assets/demo/textures/item/recolour_0.png.mcmeta", "{\"animation\":{\"frametime\":2}}");
        Path animatedData = root.resolve("animated-native-bow-data");
        seedClientCache(animatedData, vanilla);
        BuildResult animated = new BedrockPackCompiler(animatedData, config(), "26.2").build(List.of(
                new ContentSource("test", ContentSource.Kind.RESOURCE_PACK, source, 1)), List.of());
        try (ZipFile zip = new ZipFile(animated.outputDirectory().resolve("pack.zip").toFile())) {
            assertEquals(4, zip.stream().filter(entry -> entry.getName().startsWith("models/entity/geometry.")).count());
        }
        Files.delete(animationMetadata);

        BufferedImage changed = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) changed.setRGB(x, y, Color.MAGENTA.getRGB());
        changed.setRGB(0, 0, 0);
        ImageIO.write(changed, "PNG", source.resolve("assets/demo/textures/item/recolour_1.png").toFile());
        Path changedData = root.resolve("changed-native-bow-data");
        seedClientCache(changedData, vanilla);
        BuildResult changedShape = new BedrockPackCompiler(changedData, config(), "26.2").build(List.of(
                new ContentSource("test", ContentSource.Kind.RESOURCE_PACK, source, 1)), List.of());
        try (ZipFile zip = new ZipFile(changedShape.outputDirectory().resolve("pack.zip").toFile())) {
            assertEquals(4, zip.stream().filter(entry -> entry.getName().startsWith("models/entity/geometry.")).count());
        }
    }

    @Test
    void keepsVolumetricBowPullStagesAsRuntimeJavaGeometryAndTransforms() throws Exception {
        Path source = root.resolve("dynamic-volumetric-source");
        write(source, "assets/minecraft/models/item/bow.json", """
                {"overrides":[
                  {"predicate":{"custom_model_data":200},"model":"demo:item/extraordinarily_long_volumetric_weapon_bow"},
                  {"predicate":{"custom_model_data":200,"pulling":1,"pull":0.0},"model":"demo:item/extraordinarily_long_volumetric_weapon_bow_0"},
                  {"predicate":{"custom_model_data":200,"pulling":1,"pull":0.65},"model":"demo:item/extraordinarily_long_volumetric_weapon_bow_1"},
                  {"predicate":{"custom_model_data":200,"pulling":1,"pull":0.9},"model":"demo:item/extraordinarily_long_volumetric_weapon_bow_2"}]}
                """);
        for (String name : List.of("extraordinarily_long_volumetric_weapon_bow",
                "extraordinarily_long_volumetric_weapon_bow_0",
                "extraordinarily_long_volumetric_weapon_bow_1",
                "extraordinarily_long_volumetric_weapon_bow_2")) {
            write(source, "assets/demo/models/item/" + name + ".json", cube("demo:item/" + name));
            png(source.resolve("assets/demo/textures/item/" + name + ".png"), Color.ORANGE);
        }

        BuildResult result = new BedrockPackCompiler(root.resolve("dynamic-volumetric-data"), config()).build(List.of(
                new ContentSource("test", ContentSource.Kind.RESOURCE_PACK, source, 1)), List.of());

        assertEquals(1, result.converted());
        assertEquals(1, result.threeDimensional());
        try (ZipFile zip = new ZipFile(result.outputDirectory().resolve("pack.zip").toFile())) {
            assertEquals(4, zip.stream().map(ZipEntry::getName)
                    .filter(name -> name.startsWith("models/entity/geometry.") && name.endsWith(".geo.json")).count());
            assertEquals(4, zip.stream().map(ZipEntry::getName)
                    .filter(name -> name.startsWith("animations/") && name.endsWith(".animation.json")).count());
            assertTrue(zip.stream().map(ZipEntry::getName).allMatch(name -> name.length() < 80),
                    "Bedrock pack paths must stay below Geyser's cross-platform portability limit");
            String controllerPath = zip.stream().map(ZipEntry::getName)
                    .filter(name -> name.startsWith("render_controllers/")).findFirst().orElseThrow();
            JsonObject controller = read(zip, controllerPath).getAsJsonObject("render_controllers")
                    .entrySet().iterator().next().getValue().getAsJsonObject();
            assertEquals("Array.twilight_geometries[v.twilight_model_index]",
                    controller.get("geometry").getAsString());
            String attachablePath = zip.stream().map(ZipEntry::getName)
                    .filter(name -> name.startsWith("attachables/")).findFirst().orElseThrow();
            JsonObject scripts = read(zip, attachablePath).getAsJsonObject("minecraft:attachable")
                    .getAsJsonObject("description").getAsJsonObject("scripts");
            assertTrue(scripts.getAsJsonArray("pre_animation").get(0).getAsString().contains("twilight_model_index"));
            assertTrue(scripts.getAsJsonArray("animate").size() >= 20);
        }
    }

    @Test
    void mapsLegacyCrossbowArrowAndRocketChargeTypesWithoutFlatteningThemTogether() throws Exception {
        Path source = root.resolve("crossbow-charge-source");
        write(source, "assets/minecraft/models/item/crossbow.json", """
                {"overrides":[
                  {"predicate":{"custom_model_data":300},"model":"demo:item/crossbow"},
                  {"predicate":{"custom_model_data":300,"pulling":1,"pull":0.0},"model":"demo:item/crossbow_0"},
                  {"predicate":{"custom_model_data":300,"pulling":1,"pull":0.58},"model":"demo:item/crossbow_1"},
                  {"predicate":{"custom_model_data":300,"pulling":1,"pull":1.0},"model":"demo:item/crossbow_2"},
                  {"predicate":{"custom_model_data":300,"charged":1},"model":"demo:item/crossbow_arrow"},
                  {"predicate":{"custom_model_data":300,"charged":1,"firework":1},"model":"demo:item/crossbow_rocket"}]}
                """);
        for (String name : List.of("crossbow", "crossbow_0", "crossbow_1", "crossbow_2", "crossbow_arrow", "crossbow_rocket")) {
            write(source, "assets/demo/models/item/" + name + ".json",
                    "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"demo:item/" + name + "\"}}");
            png(source.resolve("assets/demo/textures/item/" + name + ".png"), Color.GRAY);
        }

        BuildResult result = new BedrockPackCompiler(root.resolve("crossbow-charge-data"), config()).build(List.of(
                new ContentSource("test", ContentSource.Kind.RESOURCE_PACK, source, 1)), List.of());
        JsonArray mappings = JsonParser.parseString(Files.readString(result.outputDirectory()
                .resolve("custom_mappings/geyser_item_mappings.json"))).getAsJsonObject()
                .getAsJsonObject("items").getAsJsonArray("minecraft:crossbow");

        assertEquals(3, mappings.size());
        assertTrue(mappings.toString().contains("\"value\":\"arrow\""));
        assertTrue(mappings.toString().contains("\"value\":\"rocket\""));
        assertTrue(mappings.asList().stream().map(JsonElement::getAsJsonObject)
                .noneMatch(mapping -> mapping.getAsJsonObject("bedrock_options").has("display_handheld")));
    }

    @Test
    void strictFailurePreservesLastKnownGoodBuild() throws Exception {
        Path source = root.resolve("strict-source");
        write(source, "assets/minecraft/models/item/stick.json", """
                {"parent":"minecraft:item/handheld","overrides":[
                  {"predicate":{"custom_model_data":1},"model":"demo:item/good"}
                ]}
                """);
        write(source, "assets/demo/models/item/good.json", """
                {"parent":"minecraft:item/generated","textures":{"layer0":"demo:item/good"}}
                """);
        png(source.resolve("assets/demo/textures/item/good.png"), Color.GREEN);
        ContentSource pack = new ContentSource("test", ContentSource.Kind.RESOURCE_PACK, source, 1);
        Path data = root.resolve("strict-data");
        BedrockPackCompiler compiler = new BedrockPackCompiler(data, config());
        compiler.build(List.of(pack), List.of());
        Path current = data.resolve("build/current/pack.zip");
        byte[] knownGood = Files.readAllBytes(current);

        write(source, "assets/minecraft/models/item/stick.json", """
                {"parent":"minecraft:item/handheld","overrides":[
                  {"predicate":{"custom_model_data":1},"model":"demo:item/good"},
                  {"predicate":{"custom_model_data":2},"model":"demo:item/broken"}
                ]}
                """);
        // Unreadable content fails strict publication (a model that exists nowhere is Java's missing model instead).
        write(source, "assets/demo/models/item/broken.json", "{not json");
        ConversionException failure = assertThrows(ConversionException.class,
                () -> compiler.build(List.of(pack), List.of()));
        assertEquals(1, failure.problems().size());
        assertArrayEquals(knownGood, Files.readAllBytes(current));
    }

    @Test
    void convertsDefaultBitmapGlyphsIntoBedrockUnicodePages() throws Exception {
        Path source = root.resolve("font-source");
        write(source, "assets/minecraft/font/default.json", """
                {"providers":[{"type":"bitmap","file":"demo:font/icons.png",
                  "height":9,"ascent":8,"chars":[""]}]}
                """);
        Path texture = source.resolve("assets/demo/textures/font/icons.png");
        Files.createDirectories(texture.getParent());
        BufferedImage icons = new BufferedImage(18, 9, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 9; y++) for (int x = 0; x < 18; x++) {
            icons.setRGB(x, y, (x < 9 ? Color.MAGENTA : Color.CYAN).getRGB());
        }
        ImageIO.write(icons, "PNG", texture.toFile());

        ContentSource pack = new ContentSource("test", ContentSource.Kind.RESOURCE_PACK, source, 1);
        // Without the title layout no spacer or wide-variant pages accompany the glyph page.
        BuildResult result = new BedrockPackCompiler(root.resolve("font-data"), withoutTextLayout())
                .build(List.of(pack), List.of());

        assertEquals(2, result.glyphs());
        assertEquals(1, result.fontPages());
        try (ZipFile zip = new ZipFile(result.outputDirectory().resolve("pack.zip").toFile())) {
            var entry = zip.getEntry("font/glyph_E0.png");
            assertNotNull(entry);
            BufferedImage page = ImageIO.read(zip.getInputStream(entry));
            assertEquals(256, page.getWidth());
            assertEquals(256, page.getHeight());
            assertEquals(0, page.getRGB(0, 0));
            assertEquals(Color.MAGENTA.getRGB(), page.getRGB(0, 3));
            assertEquals(Color.CYAN.getRGB(), page.getRGB(16, 3));
        }
    }

    @Test
    void rejectsShrinkingGuiIntoAnEmojiAndPreservesNormalGlyphsInDiagnosticMode() throws Exception {
        Path source = root.resolve("mixed-font-height-source");
        String emoji = Character.toString(0xE000);
        String gui = Character.toString(0xE00F);
        write(source, "assets/minecraft/font/default.json", """
                {"providers":[
                  {"type":"bitmap","file":"demo:font/emoji.png","height":9,"ascent":8,"chars":["%s"]},
                  {"type":"bitmap","file":"demo:font/gui.png","height":1024,"ascent":1023,"chars":["%s"]}
                ]}
                """.formatted(emoji, gui));
        Path emojiTexture = source.resolve("assets/demo/textures/font/emoji.png");
        Files.createDirectories(emojiTexture.getParent());
        BufferedImage emojiImage = new BufferedImage(9, 9, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 9; y++) for (int x = 0; x < 9; x++) {
            emojiImage.setRGB(x, y, Color.MAGENTA.getRGB());
        }
        ImageIO.write(emojiImage, "PNG", emojiTexture.toFile());
        BufferedImage guiImage = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 256; y++) for (int x = 0; x < 256; x++) {
            guiImage.setRGB(x, y, Color.CYAN.getRGB());
        }
        ImageIO.write(guiImage, "PNG", source.resolve("assets/demo/textures/font/gui.png").toFile());

        // With the text layout the oversized GUI image only loses its picture; strict publication still passes.
        BuildResult strict = new BedrockPackCompiler(root.resolve("mixed-font-strict"), config()).build(
                List.of(new ContentSource("test", ContentSource.Kind.RESOURCE_PACK, source, 1)), List.of());
        assertTrue(strict.problems().isEmpty(), strict.problems().toString());
        TwilightConfig diagnostic = new TwilightConfig(false, false, false, false, 40, 100, 10_000_000, 10_000,
                false, false, List.of(), "auto", false, false, 3);
        BuildResult result = new BedrockPackCompiler(root.resolve("mixed-font-height-data"), diagnostic).build(
                List.of(new ContentSource("test", ContentSource.Kind.RESOURCE_PACK, source, 1)), List.of());
        assertEquals(1, result.glyphs());
        assertTrue(Files.readString(result.outputDirectory().resolve("build-report.json"))
                .contains("larger than Bedrock's glyph cell"));

        try (ZipFile zip = new ZipFile(result.outputDirectory().resolve("pack.zip").toFile())) {
            BufferedImage page = ImageIO.read(zip.getInputStream(zip.getEntry("font/glyph_E0.png")));
            assertEquals(256, page.getWidth());
            assertEquals(0, page.getRGB(0, 2));
            assertEquals(Color.MAGENTA.getRGB(), page.getRGB(0, 3));
            assertEquals(Color.MAGENTA.getRGB(), page.getRGB(0, 11));
            assertEquals(0, page.getRGB(0, 12));
            assertEquals(0, page.getRGB(240, 0));
            assertEquals(0, page.getRGB(255, 15));
        }
    }

    @Test
    void doesNotSilentlyDiscardCustomMenuSpacingWithoutTheTextLayout() throws Exception {
        Path source = root.resolve("custom-space-source");
        write(source, "assets/minecraft/font/default.json", """
                {"providers":[{"type":"space","advances":{"\\ue000":-8}}]}
                """);
        ConversionException failure = assertThrows(ConversionException.class,
                () -> new BedrockPackCompiler(root.resolve("custom-space-data"), withoutTextLayout()).build(
                        List.of(new ContentSource("test", ContentSource.Kind.RESOURCE_PACK, source, 1)), List.of()));
        assertTrue(failure.problems().stream().anyMatch(p -> p.contains("custom font advances")));
    }

    @Test
    void fallsBackToAValidLowerPackLayerWhenGeneratedFontTextureIsUnreadable() throws Exception {
        Path lower = root.resolve("font-lower");
        Path upper = root.resolve("font-upper");
        png(lower.resolve("assets/demo/textures/font/icon.png"), Color.GREEN);
        write(upper, "assets/minecraft/font/default.json", """
                {"providers":[{"type":"bitmap","file":"demo:font/icon.png",
                  "height":8,"ascent":7,"chars":[""]}]}
                """);
        Path unreadable = upper.resolve("assets/demo/textures/font/icon.png");
        Files.createDirectories(unreadable.getParent());
        Files.write(unreadable, new byte[]{1, 2, 3, 4});

        BuildResult result = new BedrockPackCompiler(root.resolve("font-fallback-data"), config()).build(List.of(
                new ContentSource("lower", ContentSource.Kind.RESOURCE_PACK, lower, 1),
                new ContentSource("upper", ContentSource.Kind.RESOURCE_PACK, upper, 2)
        ), List.of());

        assertEquals(1, result.glyphs());
        assertTrue(result.problems().isEmpty());
    }

    @Test
    void resolvesExplicitVanillaFontReferencesFromVerifiedLocalClientCache() throws Exception {
        Path source = root.resolve("vanilla-font-source");
        write(source, "assets/minecraft/font/default.json", """
                {"providers":[{"type":"bitmap","file":"minecraft:item/yellow_dye.png",
                  "height":8,"ascent":7,"chars":[""]}]}
                """);
        Path data = root.resolve("vanilla-font-data");
        Path cache = data.resolve("cache/vanilla/26.2");
        Files.createDirectories(cache);
        Path client = cache.resolve("client.jar");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(client))) {
            zip.putNextEntry(new ZipEntry("assets/minecraft/textures/item/yellow_dye.png"));
            zip.write(pngBytes(Color.YELLOW));
            zip.closeEntry();
        }
        String checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1")
                .digest(Files.readAllBytes(client)));
        Files.writeString(cache.resolve("client.sha1"), checksum);

        BuildResult result = new BedrockPackCompiler(data, config(), "26.2").build(List.of(
                new ContentSource("test", ContentSource.Kind.RESOURCE_PACK, source, 1)
        ), List.of());

        assertEquals(1, result.glyphs());
        assertEquals(1, result.vanillaFallbackTextures());
        assertTrue(result.problems().isEmpty());
    }

    @Test
    void compilesGloballySafePrivateUseGlyphsFromNamedFonts() throws Exception {
        Path source = root.resolve("named-font-source");
        write(source, "assets/demo/font/menu.json", """
                {"providers":[{"type":"bitmap","file":"demo:font/menu.png",
                  "height":8,"ascent":7,"chars":["\uE120"]}]}
                """);
        png(source.resolve("assets/demo/textures/font/menu.png"), Color.BLUE);

        BuildResult result = new BedrockPackCompiler(root.resolve("named-font-data"), config()).build(List.of(
                new ContentSource("test", ContentSource.Kind.RESOURCE_PACK, source, 1)
        ), List.of());

        assertEquals(1, result.namedFonts());
        assertEquals(1, result.namedGlyphs());
        assertEquals(1, result.glyphs());
        assertTrue(result.problems().isEmpty());
        try (ZipFile zip = new ZipFile(result.outputDirectory().resolve("pack.zip").toFile())) {
            assertNotNull(zip.getEntry("font/glyph_E1.png"));
        }
    }

    @Test
    void rejectsNamedFontCodepointCollisionsWithoutTheTextLayout() throws Exception {
        Path source = root.resolve("named-font-conflict");
        write(source, "assets/demo/font/a.json", """
                {"providers":[{"type":"bitmap","file":"demo:font/a.png",
                  "height":8,"ascent":7,"chars":["\uE121"]}]}
                """);
        write(source, "assets/demo/font/b.json", """
                {"providers":[{"type":"bitmap","file":"demo:font/b.png",
                  "height":8,"ascent":7,"chars":["\uE121"]}]}
                """);
        png(source.resolve("assets/demo/textures/font/a.png"), Color.RED);
        png(source.resolve("assets/demo/textures/font/b.png"), Color.GREEN);

        ConversionException failure = assertThrows(ConversionException.class,
                () -> new BedrockPackCompiler(root.resolve("named-font-conflict-data"), withoutTextLayout()).build(List.of(
                        new ContentSource("test", ContentSource.Kind.RESOURCE_PACK, source, 1)
                ), List.of()));

        assertTrue(failure.problems().stream().anyMatch(problem ->
                problem.contains("conflict on Bedrock's global codepoint map")));
    }

    @Test
    void keepsIdenticalGlyphFilenamesInDifferentNamespacesDistinct() throws Exception {
        Path source = root.resolve("font-namespace-collision");
        write(source, "assets/minecraft/font/default.json", """
                {"providers":[
                  {"type":"bitmap","file":"first:gui/menu.png","height":16,"ascent":11,"chars":["\uE000"]},
                  {"type":"bitmap","file":"second:gui/menu.png","height":16,"ascent":11,"chars":["\uE001"]}
                ]}
                """);
        png(source.resolve("assets/first/textures/gui/menu.png"), Color.RED);
        png(source.resolve("assets/second/textures/gui/menu.png"), Color.GREEN);
        BuildResult result = new BedrockPackCompiler(root.resolve("font-namespace-output"), config())
                .build(List.of(new ContentSource("fixture", ContentSource.Kind.RESOURCE_PACK, source, 1)), List.of());
        assertEquals(2, result.glyphs());
        try (ZipFile zip = new ZipFile(result.outputDirectory().resolve("pack.zip").toFile())) {
            BufferedImage page = ImageIO.read(zip.getInputStream(zip.getEntry("font/glyph_E0.png")));
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                assertEquals(Color.RED.getRGB(), page.getRGB(x, y));
                assertEquals(Color.GREEN.getRGB(), page.getRGB(16 + x, y));
            }
        }
    }

    @Test
    void preservesMultiRowGlyphSheetsWithoutRegisteringBlankCells() throws Exception {
        Path source = root.resolve("font-grid");
        write(source, "assets/minecraft/font/default.json", """
                {"providers":[{"type":"bitmap","file":"grid:sheet.png","height":16,"ascent":11,
                  "chars":["\uE010\\u0000","\uE020\uE021"]}]}
                """);
        BufferedImage sheet = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        int[] colors = {Color.RED.getRGB(), Color.YELLOW.getRGB(), Color.GREEN.getRGB(), Color.BLUE.getRGB()};
        for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) {
            sheet.setRGB(x, y, colors[(y / 16) * 2 + x / 16]);
        }
        Path texture = source.resolve("assets/grid/textures/sheet.png");
        Files.createDirectories(texture.getParent());
        ImageIO.write(sheet, "PNG", texture.toFile());
        BuildResult result = new BedrockPackCompiler(root.resolve("font-grid-output"), config())
                .build(List.of(new ContentSource("fixture", ContentSource.Kind.RESOURCE_PACK, source, 1)), List.of());
        assertEquals(3, result.glyphs());
        try (ZipFile zip = new ZipFile(result.outputDirectory().resolve("pack.zip").toFile())) {
            assertTrue(zip.getEntry("font/glyph_00.png") == null);
            BufferedImage page = ImageIO.read(zip.getInputStream(zip.getEntry("font/glyph_E0.png")));
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                assertEquals(colors[0], page.getRGB(x, 16 + y));
                assertEquals(colors[2], page.getRGB(x, 32 + y));
                assertEquals(colors[3], page.getRGB(16 + x, 32 + y));
                assertEquals(0, page.getRGB(16 + x, 16 + y));
            }
        }
    }

    @Test
    void protectsVanillaFontCellsWithoutOverrideOrTextLayout() throws Exception {
        Path source = root.resolve("vanilla-font-override");
        write(source, "assets/minecraft/font/default.json", """
                {"providers":[{"type":"bitmap","file":"demo:font/letters.png",
                  "height":8,"ascent":7,"chars":["A"]}]}
                """);
        png(source.resolve("assets/demo/textures/font/letters.png"), Color.RED);

        ConversionException failure = assertThrows(ConversionException.class,
                () -> new BedrockPackCompiler(root.resolve("vanilla-font-override-data"), withoutTextLayout()).build(List.of(
                        new ContentSource("test", ContentSource.Kind.RESOURCE_PACK, source, 1)
                ), List.of()));

        assertTrue(failure.problems().stream().anyMatch(problem ->
                problem.contains("default font glyphs require vanilla-override")));
    }

    @Test
    void convertsLayeredAndReferencedJavaSoundsToBedrockDefinitions() throws Exception {
        Path lower = root.resolve("sound-lower");
        Path upper = root.resolve("sound-upper");
        write(lower, "assets/demo/sounds.json", """
                {"bell":{"sounds":["bell/one"]}}
                """);
        write(upper, "assets/demo/sounds.json", """
                {"bell":{"sounds":[{"name":"demo:bell/two","volume":0.5,"pitch":1.2,
                                      "weight":3,"stream":true,"attenuation_distance":24}]},
                 "combo":{"sounds":[{"name":"demo:bell","type":"event","volume":0.5}]}}
                """);
        ogg(lower.resolve("assets/minecraft/sounds/bell/one.ogg"), 1);
        ogg(upper.resolve("assets/demo/sounds/bell/two.ogg"), 2);

        BuildResult result = new BedrockPackCompiler(root.resolve("sound-data"), config()).build(List.of(
                new ContentSource("lower", ContentSource.Kind.RESOURCE_PACK, lower, 1),
                new ContentSource("upper", ContentSource.Kind.RESOURCE_PACK, upper, 2)
        ), List.of());

        assertEquals(2, result.soundDefinitions());
        assertEquals(2, result.soundFiles());
        assertTrue(result.problems().isEmpty());
        try (ZipFile zip = new ZipFile(result.outputDirectory().resolve("pack.zip").toFile())) {
            assertNotNull(zip.getEntry("sounds/minecraft/bell/one.ogg"));
            assertNotNull(zip.getEntry("sounds/demo/bell/two.ogg"));
            JsonObject definitions = read(zip, "sounds/sound_definitions.json").getAsJsonObject("sound_definitions");
            assertEquals(2, definitions.getAsJsonObject("demo:bell").getAsJsonArray("sounds").size());
            JsonObject combo = definitions.getAsJsonObject("demo:combo").getAsJsonArray("sounds")
                    .get(0).getAsJsonObject();
            assertEquals(0.5, combo.get("volume").getAsDouble());
        }
    }

    @Test
    void reportsSoundFilesMissingForJavaTooWithoutBlockingStrictBuilds() throws Exception {
        // A definition whose file exists in no pack is silent for Java players as well (real case: a
        // Survival mob pack); the conversion is faithful, so the report notes it and the build proceeds.
        Path source = root.resolve("sound-missing");
        write(source, "assets/demo/sounds.json", """
                {"missing":{"sounds":["demo:not/present"]}}
                """);

        BuildResult result = new BedrockPackCompiler(root.resolve("sound-missing-data"), config()).build(List.of(
                new ContentSource("test", ContentSource.Kind.RESOURCE_PACK, source, 1)
        ), List.of());
        assertTrue(result.problems().isEmpty(), result.problems().toString());
        JsonObject report = JsonParser.parseString(Files.readString(result.outputDirectory().resolve("build-report.json")))
                .getAsJsonObject();
        assertTrue(report.getAsJsonArray("notices").toString().contains("demo:not/present"), report.toString());
    }

    @Test
    void skipsVerifiedVanillaSoundsButAllowsNewMinecraftNamespaceEvents() throws Exception {
        Path source = root.resolve("sound-vanilla-registry");
        String sharedDefinition = "{\"sounds\":[\"minecraft:existing\"]}";
        write(source, "assets/minecraft/sounds.json", """
                {"vanilla.same":%s,"custom.new":{"sounds":["minecraft:custom/new"]}}
                """.formatted(sharedDefinition));

        Path data = root.resolve("sound-vanilla-registry-data");
        Path cache = data.resolve("cache/vanilla/26.2");
        Files.createDirectories(cache);
        byte[] registry = ("{\"vanilla.same\":" + sharedDefinition + '}').getBytes(StandardCharsets.UTF_8);
        Path sounds = cache.resolve("sounds.json");
        Files.write(sounds, registry);
        String checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1")
                .digest(registry));
        Files.writeString(cache.resolve("sounds.sha1"), checksum);
        byte[] soundBytes = new byte[]{'O', 'g', 'g', 'S', 0, 2, 0, 3};
        String soundHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(soundBytes));
        byte[] indexBytes = ("{\"objects\":{\"minecraft/sounds/custom/new.ogg\":{\"hash\":\"" + soundHash +
                "\",\"size\":" + soundBytes.length + "}}}").getBytes(StandardCharsets.UTF_8);
        Files.write(cache.resolve("asset-index.json"), indexBytes);
        Files.writeString(cache.resolve("asset-index.sha1"), HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-1").digest(indexBytes)));
        Path cachedSound = cache.resolve("objects").resolve(soundHash.substring(0, 2)).resolve(soundHash);
        Files.createDirectories(cachedSound.getParent());
        Files.write(cachedSound, soundBytes);

        BuildResult result = new BedrockPackCompiler(data, config(), "26.2").build(List.of(
                new ContentSource("test", ContentSource.Kind.RESOURCE_PACK, source, 1)
        ), List.of());

        assertEquals(1, result.soundDefinitions());
        assertEquals(1, result.soundFiles());
        assertEquals(1, result.vanillaFallbackSounds());
        assertTrue(result.problems().isEmpty());
        try (ZipFile zip = new ZipFile(result.outputDirectory().resolve("pack.zip").toFile())) {
            JsonObject definitions = read(zip, "sounds/sound_definitions.json").getAsJsonObject("sound_definitions");
            assertTrue(definitions.has("minecraft:custom.new"));
            assertFalse(definitions.has("minecraft:vanilla.same"));
        }
    }

    /** Worn custom armour shows its equipment asset on Bedrock instead of nothing; a held one keeps its icon. */
    @Test
    void longEquipmentNamesKeepPackPathsShort() throws Exception {
        Path source = root.resolve("long-armour");
        String asset = "elder_grove_dryadia:keeper_of_the_grove_nature_gearforge_armorset";
        String name = asset.substring(asset.indexOf(':') + 1);
        write(source, "assets/elder_grove_dryadia/models/item/helmet.json",
                "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"elder_grove_dryadia:item/helmet\"}}");
        png(source.resolve("assets/elder_grove_dryadia/textures/item/helmet.png"), Color.ORANGE);
        write(source, "assets/elder_grove_dryadia/equipment/" + name + ".json",
                "{\"layers\":{\"humanoid\":[{\"texture\":\"" + asset + "\"}]}}");
        png(source.resolve("assets/elder_grove_dryadia/textures/entity/equipment/humanoid/" + name + ".png"), Color.RED);
        BuildResult result = new BedrockPackCompiler(root.resolve("long-armour-data"), config()).build(
                List.of(new ContentSource("test", ContentSource.Kind.RESOURCE_PACK, source, 1)),
                List.of(new CustomItemDescriptor("test", "minecraft:chainmail_helmet",
                        Optional.of("elder_grove_dryadia:item/helmet"), OptionalInt.empty(), "Helmet",
                        Optional.of(new CustomItemDescriptor.Equipment("head", Optional.of(asset))))));
        assertTrue(result.problems().isEmpty(), result.problems().toString());
        try (ZipFile zip = new ZipFile(result.outputDirectory().resolve("pack.zip").toFile())) {
            assertTrue(zip.stream().anyMatch(e -> e.getName().startsWith("textures/twilight/equipment/")));
            for (ZipEntry entry : zip.stream().toList()) {
                assertTrue(entry.getName().length() < 80, entry.getName());
            }
        }
    }

    @Test
    void wornArmourGetsItsEquipmentAsset() throws Exception {
        Path source = root.resolve("armour");
        for (String piece : List.of("helmet", "leggings", "ghost")) {
            write(source, "assets/demo/items/topaz_" + piece + ".json",
                    "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"demo:item/topaz_" + piece + "\"}}");
            write(source, "assets/demo/models/item/topaz_" + piece + ".json",
                    "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"demo:item/topaz_" + piece + "\"}}");
            png(source.resolve("assets/demo/textures/item/topaz_" + piece + ".png"), Color.ORANGE);
        }
        write(source, "assets/demo/equipment/topaz.json", """
                {"layers":{"humanoid":[{"texture":"demo:topaz"},
                  {"texture":"demo:topaz_overlay","dyeable":{"color_when_undyed":65280}},
                  {"texture":"demo:topaz_dye_only","dyeable":{}}],
                 "humanoid_leggings":[{"texture":"demo:topaz"}]}}
                """);
        png(source.resolve("assets/demo/textures/entity/equipment/humanoid/topaz.png"), Color.RED);
        png(source.resolve("assets/demo/textures/entity/equipment/humanoid/topaz_overlay.png"), new Color(255, 255, 255, 128));
        png(source.resolve("assets/demo/textures/entity/equipment/humanoid/topaz_dye_only.png"), Color.BLUE);
        png(source.resolve("assets/demo/textures/entity/equipment/humanoid_leggings/topaz.png"), Color.YELLOW);
        ContentSource pack = new ContentSource("test", ContentSource.Kind.RESOURCE_PACK, source, 1);
        List<CustomItemDescriptor> live = List.of(
                new CustomItemDescriptor("test", "minecraft:chainmail_helmet", Optional.of("demo:topaz_helmet"), OptionalInt.empty(),
                        "Helmet", Optional.of(new CustomItemDescriptor.Equipment("head", Optional.of("demo:topaz")))),
                new CustomItemDescriptor("test", "minecraft:paper", Optional.of("demo:topaz_leggings"), OptionalInt.empty(),
                        "Leggings", Optional.of(new CustomItemDescriptor.Equipment("legs", Optional.of("demo:topaz")))),
                new CustomItemDescriptor("test", "minecraft:paper", Optional.of("demo:topaz_ghost"), OptionalInt.empty(),
                        "Ghost", Optional.of(new CustomItemDescriptor.Equipment("chest", Optional.of("demo:missing")))));
        BuildResult result = new BedrockPackCompiler(root.resolve("data"), config()).build(List.of(pack), live);
        assertTrue(result.problems().isEmpty(), result.problems().toString());
        String notices = JsonParser.parseString(Files.readString(result.outputDirectory().resolve("build-report.json")))
                .getAsJsonObject().get("notices").toString();
        assertTrue(notices.contains("demo:missing"), notices);
        JsonObject items = JsonParser.parseString(Files.readString(result.outputDirectory()
                .resolve("custom_mappings/geyser_item_mappings.json"))).getAsJsonObject().getAsJsonObject("items");
        JsonObject helmet = items.getAsJsonArray("minecraft:chainmail_helmet").get(0).getAsJsonObject();
        assertEquals("head", helmet.getAsJsonObject("components").getAsJsonObject("minecraft:equippable").get("slot").getAsString());
        String helmetId = helmet.get("bedrock_identifier").getAsString();
        String leggingsId = null;
        for (JsonElement mapping : items.getAsJsonArray("minecraft:paper")) {
            if (mapping.getAsJsonObject().get("model").getAsString().equals("demo:topaz_leggings")) {
                leggingsId = mapping.getAsJsonObject().get("bedrock_identifier").getAsString();
                assertEquals("legs", mapping.getAsJsonObject().getAsJsonObject("components")
                        .getAsJsonObject("minecraft:equippable").get("slot").getAsString(), "a provider may make paper wearable");
            }
        }
        assertNotNull(leggingsId);
        try (ZipFile zip = new ZipFile(result.outputDirectory().resolve("pack.zip").toFile())) {
            JsonObject worn = read(zip, "attachables/" + helmetId.substring(helmetId.indexOf(':') + 1) + ".worn.json")
                    .getAsJsonObject("minecraft:attachable").getAsJsonObject("description");
            assertEquals(helmetId + ".worn", worn.get("identifier").getAsString());
            assertEquals("geometry.humanoid.armor.helmet", worn.getAsJsonObject("geometry").get("default").getAsString());
            assertTrue(worn.getAsJsonObject("item").get(helmetId).getAsString().contains("slot.armor.head"),
                    "only worn in its own slot; held, the item keeps its icon");
            String texture = worn.getAsJsonObject("textures").get("default").getAsString();
            BufferedImage image = ImageIO.read(zip.getInputStream(zip.getEntry(texture + ".png")));
            // Red base, the overlay tinted with its undyed green at half alpha; the dye-only layer is left out.
            Color drawn = new Color(image.getRGB(3, 3), true);
            assertTrue(Math.abs(drawn.getRed() - 127) <= 3 && Math.abs(drawn.getGreen() - 128) <= 3 && drawn.getBlue() == 0
                    && drawn.getAlpha() == 255, drawn.toString());
            JsonObject legs = read(zip, "attachables/" + leggingsId.substring(leggingsId.indexOf(':') + 1) + ".worn.json")
                    .getAsJsonObject("minecraft:attachable").getAsJsonObject("description");
            assertEquals("geometry.humanoid.armor.leggings", legs.getAsJsonObject("geometry").get("default").getAsString());
            BufferedImage leggings = ImageIO.read(zip.getInputStream(zip.getEntry(
                    legs.getAsJsonObject("textures").get("default").getAsString() + ".png")));
            assertEquals(Color.YELLOW.getRGB(), leggings.getRGB(0, 0), "leggings draw the humanoid_leggings layer");
            assertEquals(2, zip.stream().filter(entry -> entry.getName().endsWith(".worn.json")).count());
        }
    }

    /** ItemsAdder's armour is dyed leather a Java shader draws by colour; Bedrock gets the colour's own layers. */
    @Test
    void itemsAdderArmourIsFoundByItsLeatherColour() throws Exception {
        Path pack = root.resolve("ia-pack"), contents = root.resolve("ia-contents");
        write(pack, "assets/minecraft/models/item/leather_helmet.json", """
                {"parent":"minecraft:item/generated","textures":{"layer0":"minecraft:item/leather_helmet"},"overrides":[
                  {"predicate":{"custom_model_data":33001},"model":"frost:item/helmet"},
                  {"predicate":{"custom_model_data":33002},"model":"frost:item/plain"}]}
                """);
        write(pack, "assets/frost/models/item/helmet.json", "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"frost:item/helmet\"}}");
        write(pack, "assets/frost/models/item/plain.json", "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"frost:item/helmet\"}}");
        png(pack.resolve("assets/frost/textures/item/helmet.png"), Color.CYAN);
        png(pack.resolve("assets/minecraft/textures/item/leather_helmet.png"), Color.GRAY);
        write(pack, "assets/minecraft/equipment/leather.json", """
                {"layers":{"humanoid":[{"texture":"minecraft:leather","dyeable":{"color_when_undyed":10511680}}]}}
                """);
        // The shader sheet ItemsAdder puts in place of the leather layer: no armour layout.
        Files.createDirectories(pack.resolve("assets/minecraft/textures/entity/equipment/humanoid"));
        ImageIO.write(new BufferedImage(2048, 64, BufferedImage.TYPE_INT_ARGB), "PNG",
                pack.resolve("assets/minecraft/textures/entity/equipment/humanoid/leather.png").toFile());
        png(pack.resolve("assets/frost/textures/armor/frost_layer_1.png"), Color.BLUE);
        png(pack.resolve("assets/frost/textures/armor/frost_layer_2.png"), Color.GREEN);
        write(contents, "frost/configs/armor.yml", """
                info:
                  namespace: frost
                armors_rendering:
                  frostarmor:
                    color: '#1cb9e9'
                    layer_1: armor/frost_layer_1
                    layer_2: armor/frost_layer_2
                    use_color: false
                """);
        // Tags never construct objects: a file trying it is ignored, as ItemsAdder ignores a file it cannot read.
        write(contents, "evil/configs/armor.yml", """
                armors_rendering:
                  evil: !!javax.script.ScriptEngineManager [!!java.net.URLClassLoader [[!!java.net.URL ["http://127.0.0.1/"]]]]
                """);
        List<CustomItemDescriptor> live = List.of(
                new CustomItemDescriptor("ItemsAdder", "minecraft:leather_helmet", Optional.empty(), OptionalInt.of(33001), "Frost",
                        Optional.of(new CustomItemDescriptor.Equipment("head", Optional.of("minecraft:leather"), OptionalInt.of(0x1cb9e9)))),
                new CustomItemDescriptor("ItemsAdder", "minecraft:leather_helmet", Optional.empty(), OptionalInt.of(33002), "Plain",
                        Optional.of(new CustomItemDescriptor.Equipment("head", Optional.of("minecraft:leather"), OptionalInt.of(0x123456)))));
        BuildResult result = new BedrockPackCompiler(root.resolve("data"), config()).build(List.of(
                new ContentSource("itemsadder", ContentSource.Kind.RESOURCE_PACK, pack, 1),
                new ContentSource("itemsadder", ContentSource.Kind.PROVIDER_DATA, contents, 2)), live);
        JsonObject items = JsonParser.parseString(Files.readString(result.outputDirectory()
                .resolve("custom_mappings/geyser_item_mappings.json"))).getAsJsonObject().getAsJsonObject("items");
        Map<Integer, String> identifiers = new LinkedHashMap<>();
        for (JsonElement mapping : items.getAsJsonArray("minecraft:leather_helmet")) {
            identifiers.put(mapping.getAsJsonObject().get("custom_model_data").getAsInt(),
                    mapping.getAsJsonObject().get("bedrock_identifier").getAsString());
        }
        try (ZipFile zip = new ZipFile(result.outputDirectory().resolve("pack.zip").toFile())) {
            for (var entry : identifiers.entrySet()) {
                String id = entry.getValue(), name = "attachables/" + id.substring(id.indexOf(':') + 1) + ".worn.json";
                if (entry.getKey() != 33001) {
                    // Without the vanilla client there is no leather layer to fall back to: never the shader sheet.
                    assertNull(zip.getEntry(name));
                    continue;
                }
                JsonObject worn = read(zip, name).getAsJsonObject("minecraft:attachable").getAsJsonObject("description");
                BufferedImage image = ImageIO.read(zip.getInputStream(zip.getEntry(
                        worn.getAsJsonObject("textures").get("default").getAsString() + ".png")));
                assertEquals(Color.BLUE.getRGB(), image.getRGB(0, 0), "the colour's own armour");
            }
        }
    }

    private static String cube(String texture) {
        return """
                {"textures":{"all":"%s"},"display":{"firstperson_righthand":{"rotation":[0,45,0]}},
                 "elements":[{"from":[2,1,3],"to":[14,15,13],"faces":{
                   "north":{"texture":"#all","uv":[0,0,16,16]},"south":{"texture":"#all"},
                   "east":{"texture":"#all"},"west":{"texture":"#all"},
                   "up":{"texture":"#all"},"down":{"texture":"#all"}}}]}
                """.formatted(texture);
    }

    private static void seedClientCache(Path data, Map<String, byte[]> entries) throws Exception {
        Path cache = data.resolve("cache/vanilla/26.2");
        Files.createDirectories(cache);
        Path client = cache.resolve("client.jar");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(client))) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        Files.writeString(cache.resolve("client.sha1"), HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(client))));
    }

    private static void write(Path root, String relative, String text) throws Exception {
        Path path = root.resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, text, StandardCharsets.UTF_8);
    }

    private static void png(Path path, Color color) throws Exception {
        Files.createDirectories(path.getParent());
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) image.setRGB(x, y, color.getRGB());
        ImageIO.write(image, "PNG", path.toFile());
    }

    private static byte[] pngBytes(Color color) throws Exception {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) image.setRGB(x, y, color.getRGB());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", output);
        return output.toByteArray();
    }

    private static void ogg(Path path, int marker) throws Exception {
        Files.createDirectories(path.getParent());
        Files.write(path, new byte[]{'O', 'g', 'g', 'S', 0, 2, 0, (byte) marker});
    }

    private static JsonObject read(ZipFile zip, String name) throws Exception {
        try (var reader = new InputStreamReader(zip.getInputStream(zip.getEntry(name)), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static double[] rotateAxis(double[] point, int axis, double degrees) {
        double[] result = point.clone();
        int first = (axis + 1) % 3, second = (axis + 2) % 3;
        double cosine = Math.cos(Math.toRadians(degrees)), sine = Math.sin(Math.toRadians(degrees));
        result[first] = cosine * point[first] - sine * point[second];
        result[second] = sine * point[first] + cosine * point[second];
        return result;
    }

    private JsonObject compilePoseAnimations(String name, String display) throws Exception {
        Path source = root.resolve(name + "-source");
        JsonObject model = JsonParser.parseString(cube("demo:item/pose")).getAsJsonObject();
        model.add("display", JsonParser.parseString(display));
        write(source, "assets/demo/models/item/pose.json", model.toString());
        png(source.resolve("assets/demo/textures/item/pose.png"), Color.ORANGE);
        BuildResult result = new BedrockPackCompiler(root.resolve(name + "-data"), config()).build(
                List.of(new ContentSource("test", ContentSource.Kind.RESOURCE_PACK, source, 1)),
                List.of(new CustomItemDescriptor("test", "minecraft:paper", Optional.of("demo:item/pose"),
                        OptionalInt.empty(), "Pose test")));
        assertEquals(1, result.converted());
        try (ZipFile zip = new ZipFile(result.outputDirectory().resolve("pack.zip").toFile())) {
            String path = zip.stream().map(ZipEntry::getName)
                    .filter(n -> n.startsWith("animations/")).findFirst().orElseThrow();
            return read(zip, path).getAsJsonObject("animations");
        }
    }

    private static JsonObject poseBone(JsonObject animations, String context) {
        return animations.entrySet().stream().filter(e -> e.getKey().endsWith("." + context))
                .findFirst().orElseThrow().getValue().getAsJsonObject()
                .getAsJsonObject("bones").getAsJsonObject("bone");
    }

    private static void assertVector(JsonArray actual, double x, double y, double z) {
        assertEquals(x, actual.get(0).getAsDouble(), 1.0e-9);
        assertEquals(y, actual.get(1).getAsDouble(), 1.0e-9);
        assertEquals(z, actual.get(2).getAsDouble(), 1.0e-9);
    }

    @Test
    void preservesElementRotationsAndFaceUvInTheReflectedModelFrame() throws Exception {
        Path source = root.resolve("element-rotation-source");
        JsonObject model = JsonParser.parseString(cube("demo:item/rotated")).getAsJsonObject();
        JsonArray elements = new JsonArray();
        for (String axis : List.of("x", "y", "z")) {
            elements.add(JsonParser.parseString("""
                    {"from":[2,3,4],"to":[10,12,14],
                     "rotation":{"origin":[7,6,5],"axis":"%s","angle":45},
                     "faces":{"north":{"texture":"#all","rotation":90}}}
                    """.formatted(axis)));
        }
        model.add("elements", elements);
        write(source, "assets/demo/models/item/rotated.json", model.toString());
        png(source.resolve("assets/demo/textures/item/rotated.png"), Color.ORANGE);
        BuildResult result = new BedrockPackCompiler(root.resolve("element-rotation-data"), config()).build(
                List.of(new ContentSource("test", ContentSource.Kind.RESOURCE_PACK, source, 1)),
                List.of(new CustomItemDescriptor("test", "minecraft:paper", Optional.of("demo:item/rotated"),
                        OptionalInt.empty(), "Rotated cube")));
        try (ZipFile zip = new ZipFile(result.outputDirectory().resolve("pack.zip").toFile())) {
            String path = zip.stream().map(ZipEntry::getName).filter(n -> n.startsWith("models/entity/geometry.")).findFirst().orElseThrow();
            JsonArray cubes = read(zip, path).getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject()
                    .getAsJsonArray("bones").get(0).getAsJsonObject().getAsJsonArray("cubes");
            for (int axis = 0; axis < 3; axis++) {
                JsonObject cube = cubes.get(axis).getAsJsonObject();
                assertVector(cube.getAsJsonArray("pivot"), -1, 6, 3);
                // Java corner relative to its authored pivot, then independent
                // Bedrock X/Y angle signs and the model's X/Z reflection.
                double[] corner = {-5, -3, -1};
                double[] actual = {-corner[0], corner[1], -corner[2]};
                JsonArray rotation = cube.getAsJsonArray("rotation");
                actual = rotateAxis(actual, 0, -rotation.get(0).getAsDouble());
                actual = rotateAxis(actual, 1, -rotation.get(1).getAsDouble());
                actual = rotateAxis(actual, 2, rotation.get(2).getAsDouble());
                actual = new double[]{-actual[0], actual[1], -actual[2]};
                assertArrayEquals(rotateAxis(corner, axis, 45), actual, 1.0e-9);
                JsonObject uv = cube.getAsJsonObject("uv").getAsJsonObject("south");
                assertEquals(90, uv.get("uv_rotation").getAsInt());
                assertEquals(6, uv.getAsJsonArray("uv").get(0).getAsInt());
                assertEquals(4, uv.getAsJsonArray("uv").get(1).getAsInt());
                assertEquals(8, uv.getAsJsonArray("uv_size").get(0).getAsInt());
                assertEquals(9, uv.getAsJsonArray("uv_size").get(1).getAsInt());
            }
        }
    }

    private TwilightConfig withoutTextLayout() {
        return new TwilightConfig(false, true, false, true, 40, 100, 10_000_000, 10_000,
                true, true, List.of(), "auto", false, false, 3, true, false);
    }

    private TwilightConfig config() {
        return new TwilightConfig(false, true, false, true, 40, 100, 10_000_000, 10_000,
                true, true, List.of(), "auto", false, false, 3);
    }
}
