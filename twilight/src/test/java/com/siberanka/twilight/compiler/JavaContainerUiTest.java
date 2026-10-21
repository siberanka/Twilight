package com.siberanka.twilight.compiler;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.siberanka.twilight.config.TwilightConfig;
import com.siberanka.twilight.source.ContentSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

class JavaContainerUiTest {
    // Java AbstractContainerScreen: title (8, 6), first chest slot frame (7, 17).
    private static final int[] JAVA_TITLE = {8, 6};
    private static final int[] JAVA_SLOT_FRAME = {7, 17};

    @TempDir Path root;

    @Test
    void placesTheFirstTextRowWhereJavaDrawsTheTitleForBothChestSizes() {
        for (int[] grid : new int[][]{JavaContainerUi.SMALL_CHEST_GRID, JavaContainerUi.LARGE_CHEST_GRID}) {
            JsonArray offset = JavaContainerUi.titleOffset(grid);
            int textX = offset.get(0).getAsInt();
            int textTop = offset.get(1).getAsInt() + JavaContainerUi.BEDROCK_LABEL_TEXT_TOP;
            assertEquals(JAVA_TITLE[0] - JAVA_SLOT_FRAME[0], textX - grid[0]);
            assertEquals(JAVA_TITLE[1] - JAVA_SLOT_FRAME[1], textTop - grid[1]);
        }
        JsonObject screen = JavaContainerUi.chestScreen();
        assertEquals("[8,-3]", screen.getAsJsonObject("small_chest_panel_top_half")
                .get("$twilight_java_title_offset").toString());
        assertEquals("[8,-2]", screen.getAsJsonObject("large_chest_panel_top_half")
                .get("$twilight_java_title_offset").toString());
    }

    @Test
    void keepsJavaPlayerInventoryDistanceFromEveryChestGrid() {
        // Java: chest frames every 18 units, player inventory frame 13 units after the last row.
        // Bedrock (measured, vanilla offsets): 11 units for 1-6 rows; the bottom half is bottom-anchored.
        JsonObject screen = JavaContainerUi.chestScreen();
        assertEquals("[0,10]", screen.getAsJsonObject("small_chest_panel_top_half").get("offset").toString());
        assertEquals("[0,9]", screen.getAsJsonObject("large_chest_panel_top_half").get("offset").toString());
        for (int[][] panel : new int[][][]{{JavaContainerUi.SMALL_CHEST_PANEL, {0, 10}},
                {JavaContainerUi.LARGE_CHEST_PANEL, {0, 9}}}) {
            int raised = panel[0][1] - JavaContainerUi.panelOffset(panel[0]).get(1).getAsInt();
            assertEquals(JavaContainerUi.JAVA_INVENTORY_GAP, JavaContainerUi.BEDROCK_INVENTORY_GAP + raised);
        }
    }

    @Test
    void givesOnlyChestScreensJavaHotbarSpacing() {
        JsonObject common = JavaContainerUi.commonScreen();
        assertEquals("common", common.get("namespace").getAsString());
        JsonObject hotbar = common.getAsJsonObject("hotbar_grid_template");
        assertEquals("[0,-5]", hotbar.get("$twilight_hotbar_offset|default").toString(), "other screens stay vanilla");
        assertEquals("$twilight_hotbar_offset", hotbar.get("offset").getAsString());
        assertEquals(2, hotbar.size(), "grid size, template and bindings stay vanilla");
        JsonObject chest = JavaContainerUi.chestScreen();
        for (String panel : List.of("small_chest_panel", "large_chest_panel")) {
            assertEquals("[0,-4]", chest.getAsJsonObject(panel).get("$twilight_hotbar_offset").toString());
            assertFalse(chest.getAsJsonObject(panel).has("controls"), "controls stay vanilla");
        }
    }

    @Test
    void drawsTheChestInventoryLabelWhereAndAboveWhereJavaDoes() {
        JsonObject common = JavaContainerUi.commonScreen();
        JsonArray modifications = common.getAsJsonObject("inventory_panel_bottom_half_with_label")
                .getAsJsonArray("modifications");
        assertEquals(2, modifications.size());
        JsonObject remove = modifications.get(0).getAsJsonObject();
        assertEquals("remove", remove.get("operation").getAsString());
        assertEquals("inventory_label", remove.get("control_name").getAsString());
        JsonObject insert = modifications.get(1).getAsJsonObject();
        assertEquals("insert_back", insert.get("operation").getAsString());
        JsonObject label = insert.getAsJsonArray("value").get(0).getAsJsonObject()
                .getAsJsonObject("inventory_label@common.section_heading_label");
        // Defaults reproduce vanilla for furnaces, hoppers and every other user of the shared bottom half.
        assertEquals("[7,3]", label.get("$twilight_inventory_label_offset|default").toString());
        assertEquals(2, label.get("$twilight_inventory_label_layer|default").getAsInt());
        assertEquals("container.inventory", label.get("text").getAsString());
        assertEquals("top_left", label.get("anchor_from").getAsString());

        JsonObject chest = JavaContainerUi.chestScreen();
        for (String panel : List.of("small_chest_panel", "large_chest_panel")) {
            JsonObject screen = chest.getAsJsonObject(panel);
            // Java: one unit right of and ten above the player inventory frame; text starts 1 below origin.
            assertEquals("[8,2]", screen.get("$twilight_inventory_label_offset").toString());
            int layer = screen.get("$twilight_inventory_label_layer").getAsInt();
            assertTrue(layer > JavaContainerUi.TITLE_LAYER, "Java draws the inventory label after the title");
            assertTrue(layer < 8, "items stay above the label");
        }
    }

    @Test
    void movesTheLabelOriginForTheTitleLayoutOnly() {
        JsonObject laidOut = JavaContainerUi.chestScreen(true);
        assertEquals("[-248,-3]", laidOut.getAsJsonObject("small_chest_panel_top_half")
                .get("$twilight_java_title_offset").toString());
        assertEquals("[-248,-2]", laidOut.getAsJsonObject("large_chest_panel_top_half")
                .get("$twilight_java_title_offset").toString());
        assertEquals(JavaContainerUi.chestScreen(), JavaContainerUi.chestScreen(false));
    }

    @Test
    void neverWrapsAndLayersTheTitleBetweenSlotFramesAndItems() {
        JsonObject screen = JavaContainerUi.chestScreen();
        assertEquals("chest", screen.get("namespace").getAsString());
        JsonObject label = screen.getAsJsonObject("chest_label");
        assertEquals("[\"default\",\"default\"]", label.get("size").toString());
        assertEquals("$twilight_java_title_offset", label.get("offset").getAsString());
        assertEquals("[8,-2]", label.get("$twilight_java_title_offset|default").toString());
        int layer = label.get("layer").getAsInt();
        assertTrue(layer > 3, "slot frames must stay below the title image");
        assertTrue(layer < 5, "selection, hover and items must stay above the title image");
        for (var channel : label.getAsJsonArray("color")) assertEquals(0x40, Math.round(channel.getAsDouble() * 255));
        // A partial definition must not replace vanilla behavior such as text binding or localization.
        for (String preserved : List.of("text", "type", "localize", "enable_profanity_filter", "anchor_from", "controls")) {
            assertFalse(label.has(preserved), preserved);
            assertFalse(screen.getAsJsonObject("small_chest_panel_top_half").has(preserved), preserved);
        }
    }

    @Test
    void compilerEmitsTheAdapterOnlyWhenEnabled() throws Exception {
        Path source = root.resolve("source");
        Path font = source.resolve("assets/minecraft/font/default.json");
        Files.createDirectories(font.getParent());
        Files.writeString(font, """
                {"providers":[{"type":"bitmap","file":"demo:font/menu.png","height":83,"ascent":14,"chars":["\\uEC03"]}]}
                """);
        Path texture = source.resolve("assets/demo/textures/font/menu.png");
        Files.createDirectories(texture.getParent());
        BufferedImage image = new BufferedImage(176, 83, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xFF402010);
        ImageIO.write(image, "PNG", texture.toFile());
        ContentSource pack = new ContentSource("test", ContentSource.Kind.RESOURCE_PACK, source, 1);

        Path enabled = new BedrockPackCompiler(root.resolve("enabled"), config(true)).build(List.of(pack), List.of()).outputDirectory();
        try (ZipFile zip = new ZipFile(enabled.resolve("pack.zip").toFile())) {
            var entry = zip.getEntry(JavaContainerUi.PATH);
            assertNotNull(entry);
            JsonObject parsed = JsonParser.parseString(new String(zip.getInputStream(entry).readAllBytes(),
                    StandardCharsets.UTF_8)).getAsJsonObject();
            // A custom font enables the title layout, which moves the label origin left and adds layer labels;
            // pocket (phone) screens use the same Java layout by default.
            assertEquals(JavaContainerUi.chestScreen(true, true, true), parsed);
            JsonObject table = JsonParser.parseString(new String(zip.getInputStream(zip.getEntry("twilight/text-layout.json"))
                    .readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("java", table.get("pocket_layout").getAsString(), "the runtime lays pocket titles out too");
            assertNotNull(zip.getEntry(JavaHudUi.PATH), "layered action bar and boss bar labels");
            assertNotNull(zip.getEntry("twilight/text-layout.json"));
            assertNotNull(zip.getEntry(JavaContainerUi.COMMON_PATH));
            assertTrue(zip.stream().anyMatch(e -> e.getName().startsWith("font/glyph_")), "the menu image is on a glyph page of large glyphs");
        }
        Path bedrockPocket = new BedrockPackCompiler(root.resolve("pocket"), config(true)).withPocketContainerLayout(false)
                .build(List.of(pack), List.of()).outputDirectory();
        try (ZipFile zip = new ZipFile(bedrockPocket.resolve("pack.zip").toFile())) {
            JsonObject parsed = JsonParser.parseString(new String(zip.getInputStream(zip.getEntry(JavaContainerUi.PATH))
                    .readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals(JavaContainerUi.chestScreen(true, true), parsed);
            assertFalse(parsed.has("small_chest_screen"), "Bedrock's pocket screens stay");
            JsonObject table = JsonParser.parseString(new String(zip.getInputStream(zip.getEntry("twilight/text-layout.json"))
                    .readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            assertFalse(table.has("pocket_layout"));
        }
        Path disabled = new BedrockPackCompiler(root.resolve("disabled"), config(false)).build(List.of(pack), List.of()).outputDirectory();
        try (ZipFile zip = new ZipFile(disabled.resolve("pack.zip").toFile())) {
            assertNull(zip.getEntry(JavaContainerUi.PATH));
            assertNull(zip.getEntry(JavaContainerUi.COMMON_PATH));
            assertNotNull(zip.getEntry("font/glyph_EC.png"));
        }
    }

    /**
     * Bedrock's pocket profile (phones) shows chests in two columns with the title in a header bar, so a Java menu
     * image lands behind the slot panel. The pocket variables pick the desktop panel instead, keeping every
     * desktop value, so phones get the same Java layout.
     */
    @Test
    void pocketScreensUseTheDesktopJavaLayout() {
        JsonObject screen = JavaContainerUi.chestScreen(true, true, true);
        for (String[] entry : JavaContainerUi.SCREENS) {
            JsonArray variables = screen.getAsJsonObject(entry[0]).getAsJsonArray("variables");
            assertEquals(2, variables.size(), entry[0]);
            for (var variable : variables) {
                JsonObject profile = variable.getAsJsonObject();
                assertEquals(entry[1], profile.get("$screen_content").getAsString(), entry[0]);
                assertEquals("common.screen_background", profile.get("$screen_bg_content").getAsString());
                assertFalse(profile.get("$use_custom_pocket_toast").getAsBoolean());
            }
            assertEquals("$desktop_screen", variables.get(0).getAsJsonObject().get("requires").getAsString());
            assertEquals("$pocket_screen", variables.get(1).getAsJsonObject().get("requires").getAsString());
        }
        JsonObject desktopOnly = JavaContainerUi.chestScreen(true, true, false);
        for (String[] entry : JavaContainerUi.SCREENS) assertFalse(desktopOnly.has(entry[0]));
        assertEquals(desktopOnly.getAsJsonObject("chest_label"), screen.getAsJsonObject("chest_label"));
    }

    /**
     * With the text layout a plain title shows in its own label at Java's position (the first bytes of the title)
     * and a laid-out one in the labels left of Java's origin (the rest), so a plain title never depends on spacers.
     */
    @Test
    void plainTitlesHaveALabelAtJavasPosition() {
        JsonObject screen = JavaContainerUi.chestScreen(true, true, true);
        JsonObject plain = screen.getAsJsonObject("twilight_plain_title");
        String start = plain.getAsJsonArray("bindings").get(1).getAsJsonObject().get("source_property_name").getAsString();
        assertTrue(start.contains("('%.192s' * $twilight_text)"), start);
        assertEquals("[8,-3]", screen.getAsJsonObject("small_chest_panel_top_half").get("$twilight_plain_title_offset").toString());
        String laidOut = screen.getAsJsonObject("chest_label").getAsJsonArray("bindings").get(1).getAsJsonObject()
                .get("source_property_name").getAsString();
        assertTrue(laidOut.contains("($twilight_text - ('%.192s' * $twilight_text))"), laidOut);
        assertTrue(screen.getAsJsonObject("large_chest_panel_top_half").getAsJsonArray("modifications").size() >= 2);
        assertNull(JavaContainerUi.chestScreen(false).get("twilight_plain_title"));
        // The filler between the plain block and the laid-out text never doubles a reset.
        String filler = com.siberanka.twilight.text.LayerEncoding.filler(189);
        assertEquals(189, com.siberanka.twilight.text.LayerEncoding.bytes(filler));
        assertTrue(filler.startsWith("§o") && filler.endsWith("§o") && !filler.contains("§r§r"));
        var table = new com.siberanka.twilight.text.TextLayoutTable(java.util.Map.of(), 0xF900, 32).withPlainTitles(true);
        assertTrue(com.siberanka.twilight.text.TextLayoutTable.fromJson(table.toJson()).plainTitles());
        assertFalse(table.withContainerOrigin(0).plainTitles(), "Bedrock's own title label has no plain block");
    }

    @Test
    void layoutTableCarriesThePocketLayout() {
        var table = new com.siberanka.twilight.text.TextLayoutTable(java.util.Map.of(), 0xF900, 32);
        assertFalse(table.pocketJavaLayout());
        var pocket = table.withPocketJavaLayout(true);
        var read = com.siberanka.twilight.text.TextLayoutTable.fromJson(pocket.toJson());
        assertTrue(read.pocketJavaLayout());
        assertTrue(read.withContainerOrigin(com.siberanka.twilight.text.TextLayoutTable.ORIGIN).pocketJavaLayout(), "copies keep it");
        assertTrue(read.withLayers(java.util.Set.of()).withHiddenBossBars(java.util.Set.of()).pocketJavaLayout());
        assertFalse(com.siberanka.twilight.text.TextLayoutTable.fromJson(table.toJson()).pocketJavaLayout());
    }

    private static TwilightConfig config(boolean titles) {
        return new TwilightConfig(false, true, false, false, 40, 100, 10_000_000, 1000,
                false, false, List.of(), "auto", false, false, 3, titles);
    }
}
