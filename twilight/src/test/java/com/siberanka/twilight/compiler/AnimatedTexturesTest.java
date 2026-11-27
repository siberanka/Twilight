package com.siberanka.twilight.compiler;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.siberanka.twilight.config.TwilightConfig;
import com.siberanka.twilight.source.ContentSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

/** Java {@code .mcmeta} animations on Bedrock: parsing, the plan of states and schedule, and each export target. */
class AnimatedTexturesTest {
    @TempDir Path root;

    // ------------------------------------------------------------------------------------------ parsing

    @Test
    void readsDefaultFramesFrameOrderPerFrameTimesAndInterpolation() {
        BufferedImage sheet = sheet(16, 4);
        List<String> notes = new ArrayList<>();

        TextureAnimation.Parsed plain = TextureAnimation.parse("demo:a", sheet, "{\"animation\":{\"frametime\":3}}", notes);
        assertEquals(16, plain.first().getHeight());
        assertArrayEquals(new int[]{0, 1, 2, 3}, plain.track().image());
        assertArrayEquals(new int[]{3, 3, 3, 3}, plain.track().ticks());
        assertFalse(plain.track().interpolate());

        TextureAnimation.Parsed ordered = TextureAnimation.parse("demo:a", sheet,
                "{\"animation\":{\"interpolate\":true,\"frametime\":2,\"frames\":[3,{\"index\":1,\"time\":5},0]}}", notes);
        assertArrayEquals(new int[]{3, 1, 0}, ordered.track().image());
        assertArrayEquals(new int[]{2, 5, 2}, ordered.track().ticks());
        assertTrue(ordered.track().interpolate());
        assertEquals(color(3), ordered.first().getRGB(0, 0), "the first listed frame is the first picture");
        assertTrue(notes.isEmpty());
    }

    @Test
    void followsJavasRulesForBrokenFramesAndSizes() {
        BufferedImage sheet = sheet(16, 4);
        List<String> notes = new ArrayList<>();

        TextureAnimation.Parsed skipped = TextureAnimation.parse("demo:a", sheet,
                "{\"animation\":{\"frames\":[0,9,{\"index\":1,\"time\":0},2]}}", notes);
        assertArrayEquals(new int[]{0, 2}, skipped.track().image(), "an index outside the sheet and a time below one are skipped");
        assertEquals(1, notes.size());

        notes.clear();
        assertNull(TextureAnimation.parse("demo:a", sheet, "{\"animation\":{\"frames\":[2]}}", notes).track(),
                "one frame left is no animation");
        assertNull(TextureAnimation.parse("demo:a", sheet, "{\"texture\":{}}", notes).track());
        assertNull(TextureAnimation.parse("demo:a", sheet, null, notes).track());

        notes.clear();
        TextureAnimation.Parsed wrong = TextureAnimation.parse("demo:a", sheet, "{\"animation\":{\"width\":10}}", notes);
        assertNull(wrong.track());
        assertEquals(TextureSet.missingTexture().getRGB(0, 0), wrong.first().getRGB(0, 0), "Java draws its missing texture");
        assertTrue(notes.getFirst().contains("does not divide"));

        notes.clear();
        TextureAnimation.Parsed invalid = TextureAnimation.parse("demo:a", sheet, "{\"animation\":{\"frametime\":0}}", notes);
        assertNull(invalid.track());
        assertEquals(64, invalid.first().getHeight(), "unreadable metadata leaves the whole image");
        assertTrue(notes.getFirst().contains("not valid"));
    }

    @Test
    void cutsNonSquareFramesFromWidthAndHeight() {
        BufferedImage image = new BufferedImage(32, 16, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 32; x++) for (int y = 0; y < 16; y++) image.setRGB(x, y, color(x / 16));
        TextureAnimation.Parsed parsed = TextureAnimation.parse("demo:a", image, "{\"animation\":{\"width\":16,\"height\":8}}",
                new ArrayList<>());
        assertEquals(4, parsed.track().images().size(), "a 32x16 sheet of 16x8 frames holds four frames");
        assertEquals(16, parsed.first().getWidth());
        assertEquals(8, parsed.first().getHeight());
    }

    // ------------------------------------------------------------------------------------------ planning

    @Test
    void flipbookUsesTheGcdOfTheDurationsAndKeepsFrameOrder() {
        TextureAnimation.Plan plan = TextureAnimation.plan(animated(16, 3,
                "{\"animation\":{\"frames\":[{\"index\":0,\"time\":2},{\"index\":2,\"time\":4},{\"index\":1,\"time\":2}]}}"),
                true, TextureAnimation.MAX_BLOCK_STATES);
        assertEquals(2, plan.unitTicks());
        assertArrayEquals(new int[]{0, 1, 1, 2}, plan.schedule());
        assertFalse(plan.blend());
        assertEquals(3, plan.states().size());
        assertEquals(color(0), plan.states().get(0).getRGB(0, 0));
        assertEquals(color(2), plan.states().get(1).getRGB(0, 0));
        assertEquals(color(1), plan.states().get(2).getRGB(0, 0));
    }

    @Test
    void flipbookBlendsOnlyAUniformlyTimedInterpolatedTrack() {
        TextureAnimation.Plan uniform = TextureAnimation.plan(animated(16, 3, "{\"animation\":{\"frametime\":4,\"interpolate\":true}}"),
                true, 64);
        assertTrue(uniform.blend());
        assertEquals(4, uniform.unitTicks());
        assertArrayEquals(new int[]{0, 1, 2}, uniform.schedule());

        TextureAnimation.Plan uneven = TextureAnimation.plan(animated(16, 2,
                "{\"animation\":{\"interpolate\":true,\"frames\":[{\"index\":0,\"time\":2},{\"index\":1,\"time\":6}]}}"), true, 64);
        assertFalse(uneven.blend(), "Bedrock blends whole frames over equal durations only");
        assertEquals(2, uneven.unitTicks());
        assertTrue(uneven.notes().getFirst().contains("nearest frame"));
    }

    @Test
    void entityTexturesGetInterpolatedFramesAsExtraStates() {
        TextureAnimation.Plan plan = TextureAnimation.plan(animated(16, 2, "{\"animation\":{\"frametime\":4,\"interpolate\":true}}"),
                false, TextureAnimation.MAX_ENTITY_STATES);
        assertFalse(plan.blend());
        assertEquals(1, plan.unitTicks());
        assertEquals(8, plan.schedule().length, "one entry per tick of the cycle");
        assertEquals(5, plan.states().size(), "the way back repeats the blends of the way there: five distinct pictures");
        int first = plan.states().get(0).getRGB(0, 0), half = plan.states().get(plan.schedule()[2]).getRGB(0, 0);
        assertEquals(color(0), first);
        assertEquals((color(0) >> 16 & 255) + ((color(1) >> 16 & 255) - (color(0) >> 16 & 255)) / 2, half >> 16 & 255, 1);
    }

    @Test
    void combinesTracksOfDifferentLengthsOverTheirCommonCycle() {
        BufferedImage base = new BufferedImage(32, 16, BufferedImage.TYPE_INT_ARGB);
        TextureAnimation.Track a = track("a", 2, 2), b = track("b", 3, 2);
        TextureAnimation.Plan plan = TextureAnimation.plan(new TextureAnimation.Animated(base,
                List.of(new TextureAnimation.Layer(a, 0, 0), new TextureAnimation.Layer(b, 16, 0))), false, 32);
        assertEquals(2, plan.unitTicks());
        assertEquals(6, plan.schedule().length, "cycles of 4 and 6 ticks meet after 12 ticks");
        assertEquals(6, plan.states().size());
        assertTrue(plan.notes().isEmpty());
    }

    @Test
    void freezesTheSlowestTrackWhenTheCycleOrTheStateCountIsTooLarge() {
        BufferedImage base = new BufferedImage(32, 16, BufferedImage.TYPE_INT_ARGB);
        TextureAnimation.Track fast = track("fast", 2, 1), huge = track("huge", 2, 30_011);
        TextureAnimation.Plan plan = TextureAnimation.plan(new TextureAnimation.Animated(base,
                List.of(new TextureAnimation.Layer(fast, 0, 0), new TextureAnimation.Layer(huge, 16, 0))), false, 32);
        assertEquals(2, plan.states().size(), "the fast track still plays");
        assertTrue(plan.notes().getFirst().startsWith("huge"));

        TextureAnimation.Plan none = TextureAnimation.plan(new TextureAnimation.Animated(base,
                List.of(new TextureAnimation.Layer(huge, 16, 0))), false, 32);
        assertNull(none, "nothing playable is no animation, never an error");
    }

    @Test
    void largeStatesAreLimitedByThePixelBudget() {
        BufferedImage base = new BufferedImage(2048, 2048, BufferedImage.TYPE_INT_ARGB);
        TextureAnimation.Track big = new TextureAnimation.Track("big", List.of(base, base, base, base, base),
                new int[]{0, 1, 2, 3, 4}, new int[]{1, 1, 1, 1, 1}, false);
        assertNull(TextureAnimation.plan(new TextureAnimation.Animated(base,
                List.of(new TextureAnimation.Layer(big, 0, 0))), false, 32), "5 states of 4 million pixels exceed the budget");
    }

    // ------------------------------------------------------------------------------------------ blocks

    @Test
    void customBlocksPlayAsFlipbooksWithSquareStrips() throws Exception {
        Path source = root.resolve("blocks");
        writeSheet(source.resolve("assets/demo/textures/block/ruby.png"), 16, 4);
        write(source, "assets/demo/textures/block/ruby.png.mcmeta",
                "{\"animation\":{\"frametime\":2,\"frames\":[0,1,2,3,2,1]}}");
        writeSheet(source.resolve("assets/demo/textures/block/soft.png"), 16, 3);
        write(source, "assets/demo/textures/block/soft.png.mcmeta", "{\"animation\":{\"frametime\":6,\"interpolate\":true}}");
        writeSheet(source.resolve("assets/demo/textures/block/vine.png"), 16, 2);
        write(source, "assets/demo/textures/block/vine.png.mcmeta", "{\"animation\":{\"frametime\":5}}");
        write(source, "assets/minecraft/blockstates/note_block.json", """
                {"variants":{
                  "instrument=basedrum,note=3,powered=false":{"model":"demo:block/ruby"},
                  "instrument=basedrum,note=4,powered=false":{"model":"demo:block/soft"},
                  "instrument=snare,note=1,powered=false":{"model":"demo:block/vine"}}}
                """);
        write(source, "assets/demo/models/block/ruby.json", cubeBlock("demo:block/ruby"));
        write(source, "assets/demo/models/block/soft.json", cubeBlock("demo:block/soft"));
        write(source, "assets/demo/models/block/vine.json", """
                {"textures":{"cross":"demo:block/vine"},"elements":[
                  {"from":[0.8,0,8],"to":[15.2,16,8],"rotation":{"origin":[8,8,8],"axis":"y","angle":45},
                   "faces":{"north":{"texture":"#cross"},"south":{"texture":"#cross"}}}]}
                """);
        BuildResultHolder built = build(source, "data-blocks");
        try (ZipFile zip = new ZipFile(built.zip())) {
            JsonArray flipbooks = JsonParser.parseString(text(zip, "textures/flipbook_textures.json")).getAsJsonArray();
            JsonObject terrain = JsonParser.parseString(text(zip, "textures/terrain_texture.json")).getAsJsonObject()
                    .getAsJsonObject("texture_data");
            Map<String, JsonObject> byTile = new LinkedHashMap<>();
            for (JsonElement entry : flipbooks) byTile.put(entry.getAsJsonObject().get("atlas_tile").getAsString(), entry.getAsJsonObject());
            assertEquals(6 + 6 + 1, byTile.size(), "six faces of each cube and the atlas of the cross");

            JsonObject blocks = JsonParser.parseString(text(zip, "twilight/geyser_blocks.json")).getAsJsonObject();
            JsonObject ruby = null, soft = null, vine = null;
            for (JsonElement block : blocks.getAsJsonArray("blocks")) {
                String state = block.getAsJsonObject().get("state").getAsString();
                if (state.contains("note=3")) ruby = block.getAsJsonObject();
                else if (state.contains("note=4")) soft = block.getAsJsonObject();
                else vine = block.getAsJsonObject();
            }
            String rubyKey = ruby.getAsJsonObject("materials").getAsJsonObject("up").get("texture").getAsString();
            JsonObject rubyFlipbook = byTile.get(rubyKey);
            assertEquals(2, rubyFlipbook.get("ticks_per_frame").getAsInt());
            assertEquals("[0,1,2,3,2,1]", rubyFlipbook.get("frames").toString());
            assertFalse(rubyFlipbook.get("blend_frames").getAsBoolean(), "an authored sequence without interpolate does not blend");
            String rubyTexture = terrain.getAsJsonObject(rubyKey).get("textures").getAsString();
            assertEquals(rubyTexture, rubyFlipbook.get("flipbook_texture").getAsString());
            BufferedImage strip = ImageIO.read(new ByteArrayInputStream(zip.getInputStream(zip.getEntry(rubyTexture + ".png")).readAllBytes()));
            assertEquals(16, strip.getWidth());
            assertEquals(64, strip.getHeight(), "four frames stacked");
            assertEquals(color(3), strip.getRGB(3, 3 * 16 + 3));

            String softKey = soft.getAsJsonObject("materials").getAsJsonObject("up").get("texture").getAsString();
            assertTrue(byTile.get(softKey).get("blend_frames").getAsBoolean(), "interpolate with equal times uses Bedrock's blend");
            assertEquals(6, byTile.get(softKey).get("ticks_per_frame").getAsInt());

            String vineKey = vine.getAsJsonObject("materials").getAsJsonObject("*").get("texture").getAsString();
            String vineTexture = terrain.getAsJsonObject(vineKey).get("textures").getAsString();
            BufferedImage atlas = ImageIO.read(new ByteArrayInputStream(zip.getInputStream(zip.getEntry(vineTexture + ".png")).readAllBytes()));
            assertEquals(atlas.getWidth() * 2, atlas.getHeight(), "two square frames of the squared atlas");
            JsonObject geometry = JsonParser.parseString(text(zip, "models/blocks/twilight_blocks.geo.json")).getAsJsonObject()
                    .getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonObject("description");
            assertEquals(geometry.get("texture_width").getAsInt(), geometry.get("texture_height").getAsInt(),
                    "the block geometry addresses one square frame");
            assertEquals(atlas.getWidth(), geometry.get("texture_width").getAsInt());
            assertEquals("[0,1]", byTile.get(vineKey).get("frames").toString());
            assertEquals(5, byTile.get(vineKey).get("ticks_per_frame").getAsInt());
        }
    }

    @Test
    void aPartialNonSquareFaceOfAnAnimatedTextureStaysStillWithANotice() throws Exception {
        Path source = root.resolve("strip-blocks");
        writeSheet(source.resolve("assets/demo/textures/block/band.png"), 16, 2);
        write(source, "assets/demo/textures/block/band.png.mcmeta", "{\"animation\":{\"frametime\":2}}");
        write(source, "assets/minecraft/blockstates/note_block.json",
                "{\"variants\":{\"instrument=basedrum,note=3,powered=false\":{\"model\":\"demo:block/band\"}}}");
        write(source, "assets/demo/models/block/band.json", """
                {"textures":{"all":"demo:block/band"},"elements":[{"from":[0,0,0],"to":[16,16,16],"faces":{
                  "north":{"texture":"#all","uv":[0,0,16,8]},"south":{"texture":"#all"},"east":{"texture":"#all"},
                  "west":{"texture":"#all"},"up":{"texture":"#all"},"down":{"texture":"#all"}}}]}
                """);
        BuildResultHolder built = build(source, "data-band");
        try (ZipFile zip = new ZipFile(built.zip())) {
            JsonArray flipbooks = JsonParser.parseString(text(zip, "textures/flipbook_textures.json")).getAsJsonArray();
            assertEquals(5, flipbooks.size(), "five square faces play, the half-height face does not");
            assertTrue(built.notices().stream().anyMatch(note -> note.contains("not square")), built.notices().toString());
        }
    }

    // ------------------------------------------------------------------------------------------ held items

    @Test
    void animatedThreeDimensionalItemsGetTimedTextureFramesAndARenderController() throws Exception {
        Path source = root.resolve("items3d");
        writeSheet(source.resolve("assets/demo/textures/item/glow.png"), 16, 3);
        write(source, "assets/demo/textures/item/glow.png.mcmeta", "{\"animation\":{\"frametime\":5}}");
        writeSheet(source.resolve("assets/demo/textures/item/still.png"), 16, 1);
        write(source, "assets/minecraft/models/item/paper.json", """
                {"parent":"minecraft:item/generated","overrides":[
                  {"predicate":{"custom_model_data":1},"model":"demo:item/glow"},
                  {"predicate":{"custom_model_data":2},"model":"demo:item/still"}]}
                """);
        write(source, "assets/demo/models/item/glow.json", cubeBlock("demo:item/glow"));
        write(source, "assets/demo/models/item/still.json", cubeBlock("demo:item/still"));
        BuildResultHolder built = build(source, "data-items");
        try (ZipFile zip = new ZipFile(built.zip())) {
            List<String> controllers = names(zip, "render_controllers/").stream().filter(name -> name.contains(".animated.")).toList();
            assertEquals(1, controllers.size(), "only the animated item gets a render controller of its own");
            JsonObject controller = JsonParser.parseString(text(zip, controllers.getFirst())).getAsJsonObject()
                    .getAsJsonObject("render_controllers").entrySet().iterator().next().getValue().getAsJsonObject();
            assertEquals("[\"Texture.f0\",\"Texture.f1\",\"Texture.f2\"]",
                    controller.getAsJsonObject("arrays").getAsJsonObject("textures").get("Array.twilight_frames").toString());
            String expression = controller.getAsJsonArray("textures").get(0).getAsString();
            assertEquals("Array.twilight_frames[math.mod(math.floor(q.life_time * 20 / 5), 3)]", expression);
            assertEquals("texture.enchanted", controller.getAsJsonArray("textures").get(1).getAsString());

            String attachableName = names(zip, "attachables/").stream().filter(name -> text(zip, name).contains("_animated")).findFirst()
                    .orElseThrow();
            JsonObject description = JsonParser.parseString(text(zip, attachableName)).getAsJsonObject()
                    .getAsJsonObject("minecraft:attachable").getAsJsonObject("description");
            assertEquals(1, description.getAsJsonArray("render_controllers").size());
            assertTrue(description.getAsJsonObject("textures").has("f2"));
            String frame = description.getAsJsonObject("textures").get("f2").getAsString();
            BufferedImage third = ImageIO.read(new ByteArrayInputStream(zip.getInputStream(zip.getEntry(frame + ".png")).readAllBytes()));
            assertTrue(third.getRGB(0, 0) != 0);
            String defaults = description.getAsJsonObject("textures").get("default").getAsString();
            BufferedImage first = ImageIO.read(new ByteArrayInputStream(zip.getInputStream(zip.getEntry(defaults + ".png")).readAllBytes()));
            assertEquals(color(0), first.getRGB(2, 2), "the plain texture stays the first frame");
            assertEquals(1, names(zip, "attachables/").stream().filter(name -> text(zip, name).contains("controller.render.item_default")).count(),
                    "the still item keeps the vanilla render controller");
        }
    }

    @Test
    void animatedFlatItemsAreDrawnByAnAttachableWhileTheirIconStaysTheFirstFrame() throws Exception {
        Path source = root.resolve("itemsflat");
        writeSheet(source.resolve("assets/demo/textures/item/spark.png"), 16, 4);
        write(source, "assets/demo/textures/item/spark.png.mcmeta", "{\"animation\":{\"frametime\":3,\"frames\":[3,2,1,0]}}");
        write(source, "assets/minecraft/models/item/paper.json", """
                {"parent":"minecraft:item/generated","overrides":[
                  {"predicate":{"custom_model_data":1},"model":"demo:item/spark"}]}
                """);
        write(source, "assets/demo/models/item/spark.json",
                "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"demo:item/spark\"}}");
        BuildResultHolder built = build(source, "data-flat");
        try (ZipFile zip = new ZipFile(built.zip())) {
            List<String> attachables = names(zip, "attachables/");
            assertEquals(1, attachables.size(), "an animated flat item needs an attachable: the item atlas cannot animate");
            JsonObject description = JsonParser.parseString(text(zip, attachables.getFirst())).getAsJsonObject()
                    .getAsJsonObject("minecraft:attachable").getAsJsonObject("description");
            assertEquals(4, description.getAsJsonObject("textures").keySet().stream().filter(key -> key.matches("f\\d")).count());
            String icon = names(zip, "textures/twilight/").stream().filter(name -> name.endsWith("_icon.png")).findFirst().orElseThrow();
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(zip.getInputStream(zip.getEntry(icon)).readAllBytes()));
            assertEquals(16, image.getWidth());
            assertEquals(color(3), image.getRGB(4, 4), "the inventory icon is the first listed frame");
        }
    }

    @Test
    void animatedExportIsByteIdenticalBetweenBuilds() throws Exception {
        Path source = root.resolve("determinism");
        writeSheet(source.resolve("assets/demo/textures/item/glow.png"), 16, 3);
        write(source, "assets/demo/textures/item/glow.png.mcmeta", "{\"animation\":{\"frametime\":2,\"interpolate\":true}}");
        write(source, "assets/minecraft/models/item/paper.json", """
                {"parent":"minecraft:item/generated","overrides":[{"predicate":{"custom_model_data":1},"model":"demo:item/glow"}]}
                """);
        write(source, "assets/demo/models/item/glow.json", cubeBlock("demo:item/glow"));
        write(source, "assets/minecraft/blockstates/note_block.json",
                "{\"variants\":{\"instrument=basedrum,note=3,powered=false\":{\"model\":\"demo:block/glow\"}}}");
        write(source, "assets/demo/models/block/glow.json", cubeBlock("demo:item/glow"));
        byte[] first = Files.readAllBytes(build(source, "determinism-a").zip().toPath());
        byte[] second = Files.readAllBytes(build(source, "determinism-a").zip().toPath());
        assertArrayEquals(first, second);
    }

    // ------------------------------------------------------------------------------------------ displays

    @Test
    void displayVariantsWithAnimatedTexturesGetOwnSheetsAndATimedTextureIndex() throws Exception {
        TextureAnimation.Track track = track("glow", 3, 4);
        BufferedImage base = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        TextureAnimation.Animated animated = new TextureAnimation.Animated(track.images().get(0).getWidth() == 16
                ? track.images().get(0) : base, List.of(new TextureAnimation.Layer(track, 0, 0)));
        Map<String, byte[]> files = new LinkedHashMap<>();
        DisplayEntityResources resources = new DisplayEntityResources();
        resources.add("twilight:still", item(), png(0xffff0000), new JsonObject());
        resources.add("twilight:moving", item(), TextureSet.png(animated.base()), List.of(new JsonObject()), false, animated);
        resources.add("twilight:moving_too", item(), TextureSet.png(animated.base()), List.of(new JsonObject()), false, animated);
        resources.finish(files);
        assertTrue(resources.notes().isEmpty());

        JsonObject index = JsonParser.parseString(new String(files.get(DisplayEntityResources.INDEX), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject variants = index.getAsJsonObject("variants");
        assertEquals(0, variants.getAsJsonArray("twilight:still").get(0).getAsInt());
        int moving = variants.getAsJsonArray("twilight:moving").get(0).getAsInt();
        assertEquals(DisplayEntityResources.PARTS, moving, "the animated texture starts a sheet of its own");
        assertEquals(DisplayEntityResources.PARTS + 1, variants.getAsJsonArray("twilight:moving_too").get(0).getAsInt(),
                "variants sharing the animated texture share its sheet");
        assertFalse(files.keySet().stream().anyMatch(name -> name.startsWith("textures/twilight/display_sheet0_")),
                "static sheets stay single textures");

        JsonObject entity = JsonParser.parseString(new String(files.get("entity/twilight_display.entity.json"), StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonObject("minecraft:client_entity").getAsJsonObject("description");
        assertEquals("textures/twilight/display_sheet1", entity.getAsJsonObject("textures").get("s1f0").getAsString(),
                "the state equal to the picture of the sheet itself reuses its file");
        String second = entity.getAsJsonObject("textures").get("s1f1").getAsString();
        assertTrue(second.startsWith("textures/twilight/anim/"), second);
        assertTrue(files.containsKey(second + ".png"), "states are files named by their content");
        JsonObject controller = JsonParser.parseString(new String(files.get("render_controllers/twilight_display.json"),
                StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("render_controllers").getAsJsonObject("controller.render.twilight.display");
        JsonArray textures = controller.getAsJsonObject("arrays").getAsJsonObject("textures").getAsJsonArray("Array.textures");
        assertEquals(2 + 3, textures.size(), "two sheets and three timed slots");
        assertEquals("Texture.s1f0", textures.get(2).getAsString());
        String picked = controller.getAsJsonArray("textures").get(0).getAsString();
        assertTrue(picked.contains("v.sheet==1?2+math.mod(math.floor(q.life_time*20/4),3)"), picked);
        String pre = new String(files.get("entity/twilight_display.entity.json"), StandardCharsets.UTF_8);
        assertTrue(pre.contains("v.sheet=math.floor(v.var/64);"));
    }

    @Test
    void displayPacksWithoutAnimationAreUnchanged() throws Exception {
        Map<String, byte[]> files = new LinkedHashMap<>();
        DisplayEntityResources resources = new DisplayEntityResources();
        resources.add("twilight:still", item(), png(0xffff0000), new JsonObject());
        resources.finish(files);
        String controller = new String(files.get("render_controllers/twilight_display.json"), StandardCharsets.UTF_8);
        assertTrue(controller.contains("Array.textures[math.floor(math.floor(q.property('twilight:appearance')/9)/64)]"), controller);
        assertFalse(new String(files.get("entity/twilight_display.entity.json"), StandardCharsets.UTF_8).contains("v.sheet"));
    }

    // ------------------------------------------------------------------------------------------ modes and size

    @Test
    void compactMergesEqualStatesAndShrinksTheScheduleByTheCommonRunLength() {
        BufferedImage a = track("a", 1, 1).images().get(0), b = track("b", 2, 1).images().get(1);
        BufferedImage aCopy = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) aCopy.setRGB(x, y, a.getRGB(0, 0));
        TextureAnimation.Plan plan = TextureAnimation.compact(new TextureAnimation.Plan(1, new int[]{0, 0, 1, 1, 2, 2},
                List.of(a, b, aCopy), false, List.of()));
        assertEquals(2, plan.states().size(), "the identical copy is one state");
        assertEquals(2, plan.unitTicks(), "runs of two ticks become one unit of two ticks");
        assertArrayEquals(new int[]{0, 1, 1}, plan.schedule(), "the cycle starts at a run boundary and states are numbered by first use");

        assertNull(TextureAnimation.compact(new TextureAnimation.Plan(1, new int[]{0, 1}, List.of(a, aCopy), false, List.of())),
                "states that are all alike are no animation");
    }

    @Test
    void simpleModeSamplesFramesEvenlyAndDropsInterpolationStates() {
        TextureAnimation.MODE.set(TextureAnimation.Mode.SIMPLE);
        try {
            TextureAnimation.Plan plan = TextureAnimation.plan(animated(16, 16, "{\"animation\":{\"frametime\":2,\"interpolate\":true}}"),
                    false, TextureAnimation.MAX_ENTITY_STATES);
            assertEquals(TextureAnimation.SIMPLE_STATES, plan.states().size(), "16 frames are sampled down to 8, none interpolated");
            assertEquals(4, plan.unitTicks(), "each sampled state lasts two source frames");
            assertEquals(color(0), plan.states().get(0).getRGB(0, 0));
            assertEquals(color(2), plan.states().get(1).getRGB(0, 0), "every second frame");
            TextureAnimation.Plan flipbook = TextureAnimation.plan(animated(16, 16, "{\"animation\":{\"frametime\":2}}"), true,
                    TextureAnimation.MAX_BLOCK_STATES);
            assertEquals(16, flipbook.states().size(), "block flipbooks keep every frame in the simple mode");
        } finally { TextureAnimation.MODE.remove(); }
    }

    @Test
    void offModePlansNothingAndLoadsFirstFramesOnly() throws Exception {
        TextureAnimation.MODE.set(TextureAnimation.Mode.OFF);
        try {
            assertNull(TextureAnimation.plan(animated(16, 3, "{\"animation\":{\"frametime\":2}}"), true, 64));
            Path source = root.resolve("off-source");
            writeSheet(source.resolve("assets/demo/textures/item/glow.png"), 16, 3);
            write(source, "assets/demo/textures/item/glow.png.mcmeta", "{\"animation\":{\"frametime\":5}}");
            try (var index = com.siberanka.twilight.source.ResourceIndex.build(
                    List.of(new ContentSource("test", ContentSource.Kind.RESOURCE_PACK, source, 1)), config())) {
                TextureSet atlas = TextureSet.atlas(index, List.of("demo:item/glow"));
                assertNull(atlas.animation());
                assertEquals(16, atlas.height());
                assertEquals(color(0), TextureSet.layeredIcon(index, List.of("demo:item/glow")).getRGB(1, 1));
            }
        } finally { TextureAnimation.MODE.remove(); }
    }

    @Test
    void packsAreSmallestWhenOffAndSimpleAndShareIdenticalStateTextures() throws Exception {
        Path source = root.resolve("sizes");
        writeSheet(source.resolve("assets/demo/textures/item/glow.png"), 16, 8);
        write(source, "assets/demo/textures/item/glow.png.mcmeta", "{\"animation\":{\"frametime\":2,\"interpolate\":true}}");
        write(source, "assets/minecraft/models/item/paper.json", """
                {"parent":"minecraft:item/generated","overrides":[
                  {"predicate":{"custom_model_data":1},"model":"demo:item/glow"},
                  {"predicate":{"custom_model_data":2},"model":"demo:item/glow2"}]}
                """);
        write(source, "assets/demo/models/item/glow.json", cubeBlock("demo:item/glow"));
        write(source, "assets/demo/models/item/glow2.json", cubeBlock("demo:item/glow"));
        long full = states(buildMode(source, "full", "sizes-full"));
        long simple = states(buildMode(source, "simple", "sizes-simple"));
        long off = states(buildMode(source, "off", "sizes-off"));
        assertEquals(0, off, "off exports no state textures");
        assertTrue(simple > 0 && simple <= 8, "simple keeps at most 8 states, shared by both items: " + simple);
        assertTrue(full > simple, "full renders the interpolated states too: " + full + " vs " + simple);
        assertEquals(full, states(buildMode(source, "full", "sizes-full-again")), "deterministic");
    }

    private java.io.File buildMode(Path source, String mode, String data) throws Exception {
        BuildResult result = new BedrockPackCompiler(root.resolve(data), config().withAnimatedTextures(mode))
                .build(List.of(new ContentSource("test", ContentSource.Kind.RESOURCE_PACK, source, 1)), List.of());
        assertTrue(result.problems().isEmpty(), result.problems().toString());
        return result.outputDirectory().resolve("pack.zip").toFile();
    }

    /** Distinct state texture files of a pack (named by content, so identical states of two items count once). */
    private static long states(java.io.File zip) throws Exception {
        try (ZipFile file = new ZipFile(zip)) {
            return file.stream().map(ZipEntry::getName).filter(name -> name.startsWith("textures/twilight/anim/")).count();
        }
    }

    @Test
    void indexedPngsKeepEveryPixel() throws Exception {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) image.setRGB(x, y, color((x * 7 + y * 13) % 5));
        image.setRGB(3, 3, 0x80ff0000);
        byte[] compact = TextureSet.pngCompact(image);
        BufferedImage back = ImageIO.read(new ByteArrayInputStream(compact));
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            assertEquals(image.getRGB(x, y), back.getRGB(x, y));
        }
        assertTrue(compact.length < TextureSet.png(image).length, "indexed is smaller");
        BufferedImage many = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 32; x++) for (int y = 0; y < 32; y++) many.setRGB(x, y, 0xff000000 | x * 1000 + y * 7);
        assertEquals(many.getRGB(5, 5), ImageIO.read(new ByteArrayInputStream(TextureSet.pngCompact(many))).getRGB(5, 5),
                "more than 256 colours fall back to a plain PNG");
    }

    @Test
    void theModeIsAConfigurationOptionWithValidation() throws Exception {
        var yaml = new org.bukkit.configuration.file.YamlConfiguration();
        assertEquals("full", TwilightConfig.read(yaml, root).animatedTextures(), "absent = full");
        yaml.set("generation.animated-textures", "SIMPLE");
        assertEquals("simple", TwilightConfig.read(yaml, root).animatedTextures());
        yaml.set("generation.animated-textures", false);
        assertEquals("off", TwilightConfig.read(yaml, root).animatedTextures());
        yaml.set("generation.animated-textures", "bogus");
        assertThrows(IllegalArgumentException.class, () -> TwilightConfig.read(yaml, root));
        assertThrows(Exception.class, () -> new com.siberanka.twilight.config.BackendConfigCheck(root)
                .validate("generation:\n  animated-textures: bogus\n"));
        new com.siberanka.twilight.config.BackendConfigCheck(root).validate("generation:\n  animated-textures: simple\n");
    }

    // ------------------------------------------------------------------------------------------ helpers

    private record BuildResultHolder(java.io.File zipFile, List<String> notices) {
        java.io.File zip() { return zipFile; }
    }

    private BuildResultHolder build(Path source, String data) throws Exception {
        BuildResult result = new BedrockPackCompiler(root.resolve(data), config())
                .build(List.of(new ContentSource("test", ContentSource.Kind.RESOURCE_PACK, source, 1)), List.of());
        List<String> notices = new ArrayList<>();
        for (JsonElement notice : JsonParser.parseString(Files.readString(result.outputDirectory().resolve("build-report.json")))
                .getAsJsonObject().getAsJsonArray("notices")) notices.add(notice.getAsString());
        assertTrue(result.problems().isEmpty(), result.problems().toString());
        return new BuildResultHolder(result.outputDirectory().resolve("pack.zip").toFile(), notices);
    }

    private static List<String> names(ZipFile zip, String prefix) {
        return zip.stream().map(ZipEntry::getName).filter(name -> name.startsWith(prefix)).sorted().toList();
    }

    private static String text(ZipFile zip, String name) {
        try { return new String(zip.getInputStream(zip.getEntry(name)).readAllBytes(), StandardCharsets.UTF_8); }
        catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
    }

    private static String cubeBlock(String texture) {
        return """
                {"textures":{"all":"%s"},"elements":[{"from":[0,0,0],"to":[16,16,16],"faces":{
                  "north":{"texture":"#all"},"south":{"texture":"#all"},"east":{"texture":"#all"},
                  "west":{"texture":"#all"},"up":{"texture":"#all"},"down":{"texture":"#all"}}}]}
                """.formatted(texture);
    }

    private static TextureAnimation.Animated animated(int size, int frames, String metadata) {
        TextureAnimation.Parsed parsed = TextureAnimation.parse("demo:a", sheet(size, frames), metadata, new ArrayList<>());
        return new TextureAnimation.Animated(parsed.first(), List.of(new TextureAnimation.Layer(parsed.track(), 0, 0)));
    }

    private static TextureAnimation.Track track(String name, int frames, int ticks) {
        List<BufferedImage> images = new ArrayList<>();
        int[] order = new int[frames], times = new int[frames];
        for (int i = 0; i < frames; i++) {
            BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) image.setRGB(x, y, color(i));
            images.add(image);
            order[i] = i;
            times[i] = ticks;
        }
        return new TextureAnimation.Track(name, images, order, times, false);
    }

    /** A sheet of {@code frames} square frames, frame {@code i} in {@link #color}{@code (i)}. */
    private static BufferedImage sheet(int size, int frames) {
        BufferedImage image = new BufferedImage(size, size * frames, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < size * frames; y++) for (int x = 0; x < size; x++) image.setRGB(x, y, color(y / size));
        return image;
    }

    private static int color(int index) {
        return 0xff000000 | (40 + index * 50) << 16 | (200 - index * 30) << 8 | 20;
    }

    private static void writeSheet(Path path, int size, int frames) throws Exception {
        Files.createDirectories(path.getParent());
        ImageIO.write(sheet(size, frames), "PNG", path.toFile());
    }

    private static void write(Path root, String relative, String text) throws Exception {
        Path path = root.resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, text, StandardCharsets.UTF_8);
    }

    private static byte[] png(int argb) throws java.io.IOException {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) image.setRGB(x, y, argb);
        return TextureSet.png(image);
    }

    private static JsonObject item() {
        return JsonParser.parseString("""
                {"format_version":"1.21.0","minecraft:geometry":[{"description":{"identifier":"geometry.test",
                "texture_width":16,"texture_height":16},
                "bones":[{"name":"bone","binding":"hand","pivot":[0,8,0],"cubes":[
                {"origin":[-8,2,-8],"size":[16,12,16],"pivot":[0,8,0],"uv":{"north":{"uv":[0,0],"uv_size":[16,16]}}}]}]}]}
                """).getAsJsonObject();
    }

    private TwilightConfig config() {
        return new TwilightConfig(false, true, false, true, 40, 100, 10_000_000, 10_000,
                true, true, List.of(), "auto", false, false, 3);
    }

}
