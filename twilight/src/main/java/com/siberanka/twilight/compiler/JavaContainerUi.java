/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.compiler;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.siberanka.twilight.text.LayerEncoding;
import com.siberanka.twilight.text.TextLayoutTable;

/**
 * Lays out Bedrock's desktop chest screens like Java's generic container screens.
 *
 * <p>Java draws the title unwrapped at (8, 6) with the container tint, after the
 * slot frames of its background and before slot items. Its first slot frame is at
 * (7, 17), so the title starts one unit right of and eleven units above it, and
 * the player inventory frame starts 121 units below a six-row chest frame
 * (18 per chest row plus 13). Bedrock's native label wraps at 90% of the panel,
 * hyphenates a wide glyph, draws its first text row one unit below the label top
 * and leaves only 11 units below the chest grid. Font-image menus designed for
 * Java therefore clip or miss their slots.
 *
 * <p>This partial definition is merged with the vanilla {@code chest} namespace.
 * It changes only the title label's size, position, layer and default colour,
 * the top-half panel offsets, the hotbar gap and the inventory label's position
 * and layer; text bindings, grids and other screens keep vanilla values through
 * variable defaults.
 *
 * <p>Bedrock's pocket UI profile (the default on phones and tablets) shows chests as
 * two scrolling columns with the title in a header bar above them: a Java menu image
 * drawn from the title lands in that bar and behind the slot panel. With
 * {@code pocketJava}, pocket screens use the same Java layout as desktop screens.
 */
final class JavaContainerUi {
    static final String PATH = "ui/chest_screen.json";
    static final String COMMON_PATH = "ui/ui_common.json";
    static final int JAVA_TITLE_RIGHT_OF_SLOT_FRAME = 1;
    static final int JAVA_TITLE_ABOVE_SLOT_FRAME = 11;
    /** Gap between the last chest-row frame and the player inventory frame on Java. */
    static final int JAVA_INVENTORY_GAP = 13;
    /** Measured Bedrock gap with vanilla offsets on 1-6 row chests (2 October / 3 October captures). */
    static final int BEDROCK_INVENTORY_GAP = 11;
    /** Java's hotbar frame is 4 units below the inventory; vanilla Bedrock's bottom-anchored hotbar is 3. */
    static final int[] VANILLA_HOTBAR_OFFSET = {0, -5};
    static final int JAVA_HOTBAR_GAP = 4;
    static final int BEDROCK_HOTBAR_GAP = 3;
    /**
     * Java draws the chest inventory label at (8, imageHeight - 94): one unit right
     * of and ten above the player inventory frame, after the title. Vanilla Bedrock
     * draws it at offset (7, 3) in the bottom half (text row 9 above the frame) on
     * layer 2, below a title image.
     */
    static final int[] VANILLA_INVENTORY_LABEL_OFFSET = {7, 3};
    static final int VANILLA_INVENTORY_LABEL_LAYER = 2;
    static final int[] JAVA_CHEST_INVENTORY_LABEL_OFFSET = {8, 2};
    /** Above the title (chest panel + 4), below item renderers (chest panel + 8). */
    static final int JAVA_INVENTORY_LABEL_LAYER = 6;
    /** Native label text starts one GUI unit below the label origin. */
    static final int BEDROCK_LABEL_TEXT_TOP = 1;
    /** Vanilla small/large top-half offsets and grid offsets inside them. */
    static final int[] SMALL_CHEST_PANEL = {0, 12};
    static final int[] LARGE_CHEST_PANEL = {0, 11};
    static final int[] SMALL_CHEST_GRID = {7, 9};
    static final int[] LARGE_CHEST_GRID = {7, 10};
    /**
     * Relative to the top half: slot frames are at layer 3 (item 1 + cell 1 +
     * image 1), selection at 5, hover at 6 and item renderers at 8.
     */
    static final int TITLE_LAYER = 4;
    /** Java's default container title colour, 0x404040. */
    static final double JAVA_TITLE_CHANNEL = 64 / 255.0;
    private static final String OFFSET_VARIABLE = "$twilight_java_title_offset";
    private static final String HOTBAR_VARIABLE = "$twilight_hotbar_offset";
    private static final String LABEL_OFFSET_VARIABLE = "$twilight_inventory_label_offset";
    private static final String LABEL_LAYER_VARIABLE = "$twilight_inventory_label_layer";

    private JavaContainerUi() {}

    static JsonObject chestScreen() {
        return chestScreen(false);
    }

    /**
     * @param textLayout the runtime title layout prefixes every title with spacers that
     *                   start Java's origin {@link TextLayoutTable#ORIGIN} units after the label
     */
    static JsonObject chestScreen(boolean textLayout) {
        return chestScreen(textLayout, false);
    }

    /**
     * @param layers also show layered titles (a negative space back over an image) in one label per
     *               layer, see {@link LayerLabels}
     */
    static JsonObject chestScreen(boolean textLayout, boolean layers) {
        return chestScreen(textLayout, layers, false);
    }

    /** Chest screens with their vanilla panel per UI profile: small, large and the screens that reuse them. */
    static final String[][] SCREENS = {
            {"small_chest_screen", "chest.small_chest_panel"},
            {"large_chest_screen", "chest.large_chest_panel"},
            {"ender_chest_screen", "chest.ender_chest_panel"},
            {"shulker_box_screen", "chest.shulker_box_panel"},
            {"barrel_screen", "chest.barrel_panel"}};

    /**
     * @param pocketJava pocket UI screens use the Java (desktop) chest layout instead of Bedrock's columns
     */
    static JsonObject chestScreen(boolean textLayout, boolean layers, boolean pocketJava) {
        int origin = textLayout ? TextLayoutTable.ORIGIN : 0;
        JsonObject root = new JsonObject();
        root.addProperty("namespace", "chest");

        // Layer labels space lines one unit apart; Bedrock centres a line's glyphs in that unit, which lifts
        // the first line by half the removed spacing (measured 4 October 2026): the offset puts it back.
        double lift = layers ? LayerLabels.FIRST_LINE_LIFT : 0;
        JsonObject label = new JsonObject();
        label.add(OFFSET_VARIABLE + "|default", titleOffset(LARGE_CHEST_GRID, origin, lift));
        label.addProperty("offset", OFFSET_VARIABLE);
        // Java never wraps or clips a container title.
        label.add("size", array("default", "default"));
        label.addProperty("layer", TITLE_LAYER);
        double channel = Math.round(JAVA_TITLE_CHANNEL * 1_000_000d) / 1_000_000d;
        JsonArray colour = new JsonArray();
        for (int index = 0; index < 3; index++) colour.add(channel);
        label.add("color", colour);
        LayerLabels.Source title = LayerLabels.Source.variable("$container_title", LayerLabels.CHEST_EMPTY);
        int block = LayerEncoding.BLOCK_BYTES.get(TextLayoutTable.CHEST_LAYERS);
        if (layers) LayerLabels.showBlock(label, title, 0, block);
        root.add("chest_label", label);

        JsonObject small = topHalf(SMALL_CHEST_PANEL, SMALL_CHEST_GRID, origin, lift);
        JsonObject large = topHalf(LARGE_CHEST_PANEL, LARGE_CHEST_GRID, origin, lift);
        if (layers) {
            // One panel at the title label's position, drawn on the title's layer; its labels follow in order.
            JsonObject template = new JsonObject();
            template.addProperty("anchor_from", "top_left");
            template.addProperty("anchor_to", "top_left");
            template.add("size", array("default", "default"));
            template.add("color", colour.deepCopy());
            JsonObject panel = new JsonObject();
            panel.addProperty("type", "panel");
            panel.addProperty("anchor_from", "top_left");
            panel.addProperty("anchor_to", "top_left");
            panel.addProperty("offset", OFFSET_VARIABLE);
            panel.addProperty("layer", TITLE_LAYER);
            panel.add("controls", LayerLabels.layerLabels("twilight_title", template, title, block));
            root.add("twilight_title_layers", panel);
            JsonObject reference = new JsonObject();
            reference.add("twilight_title_layers@chest.twilight_title_layers", new JsonObject());
            JsonArray controls = new JsonArray();
            controls.add(reference);
            // Inserted first: the lower layers are drawn before the vanilla label with the top layer.
            small.add("modifications", LayerLabels.insertFront(controls));
            large.add("modifications", LayerLabels.insertFront(controls.deepCopy()));
        }
        root.add("small_chest_panel_top_half", small);
        root.add("large_chest_panel_top_half", large);
        // Variables flow to every descendant; ender chest, shulker box and barrel panels inherit these.
        for (String panel : new String[]{"small_chest_panel", "large_chest_panel"}) {
            JsonObject screen = new JsonObject();
            screen.add(HOTBAR_VARIABLE, array(VANILLA_HOTBAR_OFFSET[0],
                    VANILLA_HOTBAR_OFFSET[1] + JAVA_HOTBAR_GAP - BEDROCK_HOTBAR_GAP));
            screen.add(LABEL_OFFSET_VARIABLE, array(JAVA_CHEST_INVENTORY_LABEL_OFFSET));
            screen.addProperty(LABEL_LAYER_VARIABLE, JAVA_INVENTORY_LABEL_LAYER);
            root.add(panel, screen);
        }
        if (pocketJava) {
            for (String[] screen : SCREENS) root.add(screen[0], desktopOnPocket(screen[1]));
        }
        return root;
    }

    /**
     * The screen's profile variables with the desktop panel for both profiles. Vanilla picks
     * {@code pocket_containers.*} for {@code $pocket_screen}; touch input works on the desktop panels
     * as it does with Bedrock's Classic UI profile.
     */
    static JsonObject desktopOnPocket(String panel) {
        JsonArray variables = new JsonArray();
        for (String profile : new String[]{"$desktop_screen", "$pocket_screen"}) {
            JsonObject entry = new JsonObject();
            entry.addProperty("requires", profile);
            entry.addProperty("$use_custom_pocket_toast", false);
            entry.addProperty("$screen_content", panel);
            entry.addProperty("$screen_bg_content", "common.screen_background");
            entry.addProperty("$screen_background_alpha", 0.4);
            variables.add(entry);
        }
        JsonObject screen = new JsonObject();
        screen.add("variables", variables);
        return screen;
    }

    /**
     * Turns the shared hotbar offset and inventory label placement into variables
     * whose defaults are vanilla's, so only chest screens that set them change.
     * The label is rebuilt through Bedrock's control modifications because it is
     * an array entry with literal values.
     */
    static JsonObject commonScreen() {
        JsonObject root = new JsonObject();
        root.addProperty("namespace", "common");
        JsonObject hotbar = new JsonObject();
        hotbar.add(HOTBAR_VARIABLE + "|default", array(VANILLA_HOTBAR_OFFSET));
        hotbar.addProperty("offset", HOTBAR_VARIABLE);
        root.add("hotbar_grid_template", hotbar);

        JsonObject label = new JsonObject();
        label.addProperty("anchor_from", "top_left");
        label.addProperty("anchor_to", "top_left");
        label.add(LABEL_OFFSET_VARIABLE + "|default", array(VANILLA_INVENTORY_LABEL_OFFSET));
        label.addProperty("offset", LABEL_OFFSET_VARIABLE);
        label.addProperty(LABEL_LAYER_VARIABLE + "|default", VANILLA_INVENTORY_LABEL_LAYER);
        label.addProperty("layer", LABEL_LAYER_VARIABLE);
        label.addProperty("text", "container.inventory");
        JsonObject control = new JsonObject();
        control.add("inventory_label@common.section_heading_label", label);
        JsonArray value = new JsonArray();
        value.add(control);

        JsonObject remove = new JsonObject();
        remove.addProperty("array_name", "controls");
        remove.addProperty("operation", "remove");
        remove.addProperty("control_name", "inventory_label");
        JsonObject insert = new JsonObject();
        insert.addProperty("array_name", "controls");
        insert.addProperty("operation", "insert_back");
        insert.add("value", value);
        JsonArray modifications = new JsonArray();
        modifications.add(remove);
        modifications.add(insert);
        JsonObject bottom = new JsonObject();
        bottom.add("modifications", modifications);
        root.add("inventory_panel_bottom_half_with_label", bottom);
        return root;
    }

    /** Label origin that places the first text row where Java draws its title. */
    static JsonArray titleOffset(int[] grid) {
        return titleOffset(grid, 0);
    }

    static JsonArray titleOffset(int[] grid, int origin) {
        return titleOffset(grid, origin, 0);
    }

    /** @param lift units Bedrock raises the first line of a label with layer line spacing */
    static JsonArray titleOffset(int[] grid, int origin, double lift) {
        JsonArray offset = array(grid[0] + JAVA_TITLE_RIGHT_OF_SLOT_FRAME - origin);
        int y = grid[1] - JAVA_TITLE_ABOVE_SLOT_FRAME - BEDROCK_LABEL_TEXT_TOP;
        if (lift == 0) offset.add(y);
        else offset.add(y + lift);
        return offset;
    }

    /**
     * Raises the bottom-anchored player inventory's distance from the chest grid
     * to Java's by moving the top-anchored half up; slots keep Java's spacing.
     */
    static JsonArray panelOffset(int[] vanilla) {
        return array(vanilla[0], vanilla[1] - (JAVA_INVENTORY_GAP - BEDROCK_INVENTORY_GAP));
    }

    private static JsonObject topHalf(int[] panel, int[] grid, int origin, double lift) {
        JsonObject half = new JsonObject();
        half.add("offset", panelOffset(panel));
        half.add(OFFSET_VARIABLE, titleOffset(grid, origin, lift));
        return half;
    }

    private static JsonArray array(String... values) {
        JsonArray array = new JsonArray();
        for (String value : values) array.add(value);
        return array;
    }

    private static JsonArray array(int... values) {
        JsonArray array = new JsonArray();
        for (int value : values) array.add(value);
        return array;
    }
}
