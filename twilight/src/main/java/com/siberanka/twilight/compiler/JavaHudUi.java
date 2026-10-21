/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.compiler;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.siberanka.twilight.text.LayerEncoding;
import com.siberanka.twilight.text.TextLayoutTable;

/**
 * Bedrock HUD labels that show Java's layered action bar and boss bar text.
 *
 * <p>Plugins such as CustomNameplates draw text on a background image by moving the pen back
 * with negative spaces; one Bedrock label cannot. The action bar and every boss bar name get
 * extra labels at the vanilla label's position, one per further {@link LayerLabels layer}; the
 * vanilla label shows the first. Java draws no backdrop behind the action bar, so its Bedrock background is
 * cleared as well. The definitions are merged with the vanilla {@code hud} namespace; vanilla
 * labels are re-inserted with the same values plus a visibility binding.
 */
final class JavaHudUi {
    static final String PATH = "ui/hud_screen.json";
    /** Transparent texture for cleared backgrounds. */
    static final String CLEAR_TEXTURE = "textures/ui/twilight_clear";

    private JavaHudUi() {}

    static JsonObject hudScreen() {
        return hudScreen(java.util.Set.of(), java.util.Set.of());
    }

    /**
     * @param styled  boss bar colours drawn with the pack's sprites
     * @param sprites sprite names converted to {@link BossBars#BEDROCK_FOLDER} (colour and notch sprites)
     */
    static JsonObject hudScreen(java.util.Set<String> styled, java.util.Set<String> sprites) {
        JsonObject root = new JsonObject();
        root.addProperty("namespace", "hud");

        JsonObject actionbarLabel = new JsonObject();
        actionbarLabel.addProperty("type", "label");
        actionbarLabel.addProperty("anchor_from", "center");
        actionbarLabel.addProperty("anchor_to", "center");
        actionbarLabel.addProperty("color", "$tool_tip_text");
        actionbarLabel.addProperty("enable_profanity_filter", true);
        actionbarLabel.addProperty("layer", 31);
        actionbarLabel.addProperty("text", "$actionbar_text");
        actionbarLabel.addProperty("shadow", true);
        actionbarLabel.addProperty("localize", false);
        actionbarLabel.addProperty("alpha", "@hud.anim_actionbar_text_alpha_out");
        JsonObject actionbar = new JsonObject();
        actionbar.addProperty("texture", CLEAR_TEXTURE);
        actionbar.add("modifications", relabel("actionbar_message", "twilight_actionbar", actionbarLabel,
                LayerLabels.Source.variable("$actionbar_text", LayerLabels.HUD_EMPTY),
                LayerEncoding.BLOCK_BYTES.get(TextLayoutTable.ACTIONBAR_LAYERS)));
        root.add("hud_actionbar_text", actionbar);

        JsonObject bossBinding = new JsonObject();
        bossBinding.addProperty("binding_name", "#bossName");
        bossBinding.addProperty("binding_type", "collection");
        bossBinding.addProperty("binding_collection_name", "boss_bars");
        JsonObject bossLabel = new JsonObject();
        bossLabel.addProperty("type", "label");
        bossLabel.addProperty("color", "$boss_name_text_color");
        bossLabel.addProperty("enable_profanity_filter", true);
        bossLabel.addProperty("text", "#bossName");
        bossLabel.addProperty("anchor_from", "top_middle");
        bossLabel.addProperty("anchor_to", "top_middle");
        bossLabel.addProperty("shadow", true);
        // Java never clips a boss bar name; Bedrock's label would end at the 182-unit bar with an ellipsis.
        JsonArray unclipped = new JsonArray();
        unclipped.add("default");
        unclipped.add("default");
        bossLabel.add("size", unclipped);
        // Top-anchored like a chest title: layer line spacing lifts the first line (measured 4 October 2026).
        JsonArray lifted = new JsonArray();
        lifted.add(0);
        lifted.add(LayerLabels.FIRST_LINE_LIFT);
        bossLabel.add("offset", lifted);
        JsonObject bossPanel = new JsonObject();
        bossPanel.add("modifications", relabelBoss(bossLabel, bossBinding));
        root.add("boss_name_panel", bossPanel);

        // Bars Java draws transparent or with the pack's sprites start with a zero-width marker: no vanilla bar.
        JsonObject bar = new JsonObject();
        bar.add("offset", array(0, 10));
        bar.add("bindings", visibility(bossBinding, "(not " + startsWith(LayerEncoding.HIDDEN_BAR) + ")"));
        JsonObject barControl = new JsonObject();
        barControl.add("progress_bar_for_collections@common.progress_bar_for_collections", bar);
        JsonArray barControls = new JsonArray();
        barControls.add(barControl);
        for (String colour : styled) {
            if (sprites.contains(colour + "_background") || sprites.contains(colour + "_progress")) {
                barControls.add(styledBar(colour, sprites, bossBinding));
            }
        }
        JsonObject removeBar = new JsonObject();
        removeBar.addProperty("array_name", "controls");
        removeBar.addProperty("operation", "remove");
        removeBar.addProperty("control_name", "progress_bar_for_collections");
        JsonArray healthModifications = new JsonArray();
        healthModifications.add(removeBar);
        healthModifications.addAll(LayerLabels.insertBack(barControls));
        JsonObject health = new JsonObject();
        health.add("modifications", healthModifications);
        root.add("boss_health_panel", health);
        return root;
    }

    /**
     * A boss bar of one colour drawn like Java's {@code BossHealthOverlay}: background sprite, notch
     * background, progress sprite cut at the progress, notch progress. Bedrock's own bar is one tinted
     * white texture, so the sprites are images of their own; the name's marker selects colour and notches.
     */
    private static JsonObject styledBar(String colour, java.util.Set<String> sprites, JsonObject bossBinding) {
        int colourIndex = BossBars.COLOURS.indexOf(colour);
        String colourMarker = LayerEncoding.styledBarColour(colourIndex);
        JsonArray images = new JsonArray();
        int layer = 1;
        String background = colour + "_background";
        if (sprites.contains(background)) images.add(spriteImage("background", background, layer, false, null, bossBinding));
        for (int notch = 0; notch < BossBars.NOTCHES.size(); notch++) {
            String sprite = BossBars.NOTCHES.get(notch) + "_background";
            if (sprites.contains(sprite)) {
                images.add(spriteImage("notch_background_" + notch, sprite, layer + 1, false,
                        LayerEncoding.styledBar(colourIndex, notch + 1), bossBinding));
            }
        }
        String progress = colour + "_progress";
        if (sprites.contains(progress)) images.add(spriteImage("progress", progress, layer + 2, true, null, bossBinding));
        for (int notch = 0; notch < BossBars.NOTCHES.size(); notch++) {
            String sprite = BossBars.NOTCHES.get(notch) + "_progress";
            if (sprites.contains(sprite)) {
                images.add(spriteImage("notch_progress_" + notch, sprite, layer + 3, true,
                        LayerEncoding.styledBar(colourIndex, notch + 1), bossBinding));
            }
        }
        JsonObject panel = new JsonObject();
        panel.addProperty("type", "panel");
        panel.addProperty("anchor_from", "top_middle");
        panel.addProperty("anchor_to", "top_middle");
        panel.add("size", array(182, 5));
        panel.add("offset", array(0, 10));
        panel.add("controls", images);
        panel.add("bindings", visibleWhen(bossBinding, colourMarker));
        JsonObject control = new JsonObject();
        control.add("twilight_boss_bar_" + colour, panel);
        return control;
    }

    private static JsonObject spriteImage(String name, String sprite, int layer, boolean progress, String marker,
                                          JsonObject bossBinding) {
        JsonObject image = new JsonObject();
        image.addProperty("type", "image");
        image.addProperty("texture", BossBars.BEDROCK_FOLDER + sprite);
        image.add("size", array(182, 5));
        image.addProperty("layer", layer);
        JsonArray bindings = marker == null ? new JsonArray() : visibleWhen(bossBinding, marker);
        if (progress) {
            // The same cut as Bedrock's own boss bar (common.filled_progress_bar_for_collections).
            image.addProperty("clip_direction", "left");
            image.addProperty("clip_pixelperfect", false);
            JsonObject clip = new JsonObject();
            clip.addProperty("binding_name", "#progress_percentage");
            clip.addProperty("binding_name_override", "#clip_ratio");
            clip.addProperty("binding_type", "collection");
            clip.addProperty("binding_collection_name", "boss_bars");
            bindings.add(clip);
        }
        if (!bindings.isEmpty()) image.add("bindings", bindings);
        JsonObject control = new JsonObject();
        control.add("twilight_boss_" + name, image);
        return control;
    }

    /** Visible when the boss bar name starts with the bar {@code marker} (see {@link #startsWith}). */
    private static JsonArray visibleWhen(JsonObject bossBinding, String marker) {
        return visibility(bossBinding, startsWith(marker));
    }

    /**
     * Whether the boss bar name starts with a bar marker, directly or after the layered marker (cut by UTF-8
     * length, measured live). {@code marker} excludes Geyser's leading reset.
     */
    static String startsWith(String marker) {
        return "(" + prefixIs(LayerEncoding.CLIENT_PREFIX + marker) + " or "
                + prefixIs(LayerEncoding.CLIENT_PREFIX + LayerEncoding.LAYERED + marker) + ")";
    }

    /** Whether the boss bar name is a layered one (block labels) rather than one shown whole. */
    static String layered() {
        return prefixIs(LayerEncoding.CLIENT_PREFIX + LayerEncoding.LAYERED);
    }

    private static String prefixIs(String prefix) {
        return "(('%." + LayerEncoding.bytes(prefix) + "s' * #bossName) = '" + prefix + "')";
    }

    /**
     * Boss names: a layered name is shown by the block labels (lower layers first, block 0 at the vanilla
     * label's place); any other name, however long, by one label showing it whole.
     */
    private static JsonArray relabelBoss(JsonObject vanilla, JsonObject bossBinding) {
        int first = LayerEncoding.FIRST_BLOCK_BYTES.get(TextLayoutTable.BOSS_LAYERS);
        int block = LayerEncoding.BLOCK_BYTES.get(TextLayoutTable.BOSS_LAYERS);
        LayerLabels.Source source = LayerLabels.Source.binding(bossBinding, "#bossName");
        JsonObject template = vanilla.deepCopy();
        template.remove("text");
        template.remove("bindings");
        template.addProperty("shadow", false);
        JsonArray controls = LayerLabels.layerLabels("twilight_boss_name", template, source, first, block);
        JsonObject top = vanilla.deepCopy();
        LayerLabels.showBlock(top, source, 0, first, block);
        JsonObject topControl = new JsonObject();
        topControl.add("twilight_boss_name_layer_0", top);
        controls.add(topControl);
        for (JsonElement control : controls) {
            JsonObject label = control.getAsJsonObject().entrySet().iterator().next().getValue().getAsJsonObject();
            label.getAsJsonArray("bindings").add(visibilityView(layered()));
        }
        // The vanilla label shows every other name whole, with the same line spacing and lift.
        JsonObject whole = vanilla.deepCopy();
        whole.addProperty("localize", false);
        whole.addProperty("line_padding", LayerLabels.LINE_PADDING);
        JsonArray bindings = new JsonArray();
        bindings.add(bossBinding.deepCopy());
        bindings.add(visibilityView("(not " + layered() + ")"));
        whole.add("bindings", bindings);
        JsonObject wholeControl = new JsonObject();
        wholeControl.add("boss_name", whole);
        controls.add(wholeControl);

        JsonObject remove = new JsonObject();
        remove.addProperty("array_name", "controls");
        remove.addProperty("operation", "remove");
        remove.addProperty("control_name", "boss_name");
        JsonArray modifications = new JsonArray();
        modifications.add(remove);
        modifications.addAll(LayerLabels.insertBack(controls));
        return modifications;
    }

    private static JsonObject visibilityView(String expression) {
        JsonObject visible = new JsonObject();
        visible.addProperty("binding_type", "view");
        visible.addProperty("source_property_name", expression);
        visible.addProperty("target_property_name", "#visible");
        return visible;
    }

    private static JsonArray visibility(JsonObject bossBinding, String expression) {
        JsonArray bindings = new JsonArray();
        bindings.add(bossBinding.deepCopy());
        bindings.add(visibilityView(expression));
        return bindings;
    }

    private static JsonArray array(int... values) {
        JsonArray array = new JsonArray();
        for (int value : values) array.add(value);
        return array;
    }

    private static JsonArray relabel(String name, String prefix, JsonObject vanilla, LayerLabels.Source source,
                                     int blockBytes) {
        JsonObject label = vanilla.deepCopy();
        LayerLabels.showBlock(label, source, 0, blockBytes);
        JsonObject template = vanilla.deepCopy();
        template.remove("text");
        template.remove("bindings");
        // Lower layers carry the text Java draws without a shadow; the top (vanilla) label keeps it.
        template.addProperty("shadow", false);
        // Lower layers first, the vanilla label with the top layer last (drawn on top).
        JsonArray controls = LayerLabels.layerLabels(prefix, template, source, blockBytes);
        JsonObject control = new JsonObject();
        control.add(name, label);
        controls.add(control);

        JsonObject remove = new JsonObject();
        remove.addProperty("array_name", "controls");
        remove.addProperty("operation", "remove");
        remove.addProperty("control_name", name);
        JsonArray modifications = new JsonArray();
        modifications.add(remove);
        modifications.addAll(LayerLabels.insertBack(controls));
        return modifications;
    }
}
