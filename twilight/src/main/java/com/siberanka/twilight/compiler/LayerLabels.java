/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.compiler;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.siberanka.twilight.text.LayerEncoding;
import com.siberanka.twilight.text.LayeredTextLayout;

/**
 * JSON UI bindings that show the layers of a {@link LayerEncoding} string in separate labels.
 *
 * <p>Visibility bindings on string comparisons did not evaluate in chest screens (4 October 2026), so
 * every label simply shows its block: the vanilla label block 0, extra labels the following blocks.
 *
 * <p>Bedrock evaluates string expressions only in view bindings that read a binding property, so
 * text held in a variable ({@code $container_title}, {@code $actionbar_text}) is combined with an
 * always-present global property that contributes nothing ({@code '%.0s' * #gamepad_helper_visible});
 * a variable used in such an expression must be declared on the label itself. Both were measured
 * on Bedrock 1.21 on 4 October 2026.
 */
final class LayerLabels {
    /** Global properties every chest screen and the HUD bind (any value; it contributes nothing). */
    static final String CHEST_EMPTY = "#gamepad_helper_visible";
    static final String HUD_EMPTY = "#hud_title_text_string";
    static final String LAYER_PROPERTY = "#twilight_layer";
    /** Label lines one unit apart (Bedrock's line height is 10 units, measured 4 October 2026): newlines shift text down. */
    static final int LINE_PADDING = -9;
    /**
     * Bedrock centres each line's glyphs in its (now one unit) line: a top-anchored label's first line rises
     * by half the removed spacing, 4.5 units (chest titles and boss bar names, measured 4 October 2026). The centred action
     * bar label keeps its centre and is not lifted.
     */
    static final double FIRST_LINE_LIFT = -LINE_PADDING / 2.0;

    private LayerLabels() {}

    /**
     * Where a label's text comes from: a variable (with the always-present global property that makes its
     * expressions valid) or a (global or collection) binding property.
     */
    record Source(String variable, String empty, JsonObject binding, String property, int skipped) {
        static Source variable(String variable, String empty) {
            return new Source(variable, empty, null, null, 0);
        }

        static Source binding(JsonObject binding, String property) {
            return new Source(null, null, binding, property, 0);
        }

        /** The same text without its first {@code bytes} UTF-8 bytes (the plain title block). */
        Source skipping(int bytes) {
            return new Source(variable, empty, binding, property, bytes);
        }

        String base() { return variable != null ? "$twilight_text" : property; }

        String operand() {
            String text = base();
            return skipped == 0 ? text : "(" + text + " - ('%." + skipped + "s' * " + text + "))";
        }

        String close(String expression) {
            return empty == null ? expression : "(" + expression + " + ('%.0s' * " + empty + "))";
        }
    }

    /** Text of block {@code layer} (block 0 is the top layer). */
    static String blockExpression(Source source, int layer, int blockBytes) {
        return blockExpression(source, layer, blockBytes, blockBytes);
    }

    /** @param firstBytes size of block 0, see {@link LayerEncoding#FIRST_BLOCK_BYTES} */
    static String blockExpression(Source source, int layer, int firstBytes, int blockBytes) {
        String text = source.operand();
        int start = LayerEncoding.blockStart(layer, firstBytes, blockBytes);
        String rest = layer == 0 ? text : "(" + text + " - ('%." + start + "s' * " + text + "))";
        return source.close("('%." + (layer == 0 ? firstBytes : blockBytes) + "s' * " + rest + ")");
    }

    /**
     * Makes a label show block {@code layer} of its text. Text that is not layered fits block 0, so the
     * vanilla label shows it unchanged and the further labels stay empty; no visibility switch is needed.
     */
    static void showBlock(JsonObject label, Source source, int layer, int blockBytes) {
        showBlock(label, source, layer, blockBytes, blockBytes);
    }

    /** Makes a label show the first {@code bytes} UTF-8 bytes of the source's text (the plain title block). */
    static void showStart(JsonObject label, Source source, int bytes) {
        label.addProperty("text", LAYER_PROPERTY);
        label.addProperty("localize", false);
        JsonArray bindings = baseBindings(label, source);
        bindings.add(view(source.close("('%." + bytes + "s' * " + source.base() + ")"), LAYER_PROPERTY));
        label.add("bindings", bindings);
    }

    /** Makes a label show all of the source's text (after the bytes it skips). */
    static void showAll(JsonObject label, Source source) {
        label.addProperty("text", LAYER_PROPERTY);
        label.addProperty("localize", false);
        JsonArray bindings = baseBindings(label, source);
        bindings.add(view(source.close(source.operand()), LAYER_PROPERTY));
        label.add("bindings", bindings);
    }

    static void showBlock(JsonObject label, Source source, int layer, int firstBytes, int blockBytes) {
        label.addProperty("text", LAYER_PROPERTY);
        label.addProperty("localize", false);
        label.addProperty("line_padding", LINE_PADDING);
        JsonArray bindings = baseBindings(label, source);
        bindings.add(view(blockExpression(source, layer, firstBytes, blockBytes), LAYER_PROPERTY));
        label.add("bindings", bindings);
    }

    /**
     * Labels for blocks 1 and up, each a copy of {@code template} (position, font, colour, layer); the
     * vanilla label shows block 0, the top layer ({@link #showBlock}), and must be drawn after these.
     *
     * @return controls in drawing order: the last block (bottom layer) first
     */
    static JsonArray layerLabels(String prefix, JsonObject template, Source source, int blockBytes) {
        return layerLabels(prefix, template, source, blockBytes, blockBytes);
    }

    static JsonArray layerLabels(String prefix, JsonObject template, Source source, int firstBytes, int blockBytes) {
        JsonArray controls = new JsonArray();
        for (int layer = LayeredTextLayout.MAX_LAYERS - 1; layer >= 1; layer--) {
            JsonObject label = template.deepCopy();
            label.addProperty("type", "label");
            showBlock(label, source, layer, firstBytes, blockBytes);
            JsonObject control = new JsonObject();
            control.add(prefix + "_layer_" + layer, label);
            controls.add(control);
        }
        return controls;
    }

    private static JsonArray baseBindings(JsonObject label, Source source) {
        JsonArray bindings = new JsonArray();
        if (source.variable() != null) label.addProperty("$twilight_text", source.variable());
        else bindings.add(source.binding().deepCopy());
        if (source.empty() != null) {
            JsonObject empty = new JsonObject();
            empty.addProperty("binding_name", source.empty());
            // The chest property is bound like vanilla binds it (no type); the HUD's is a global binding.
            if (source.empty().equals(HUD_EMPTY)) empty.addProperty("binding_type", "global");
            bindings.add(empty);
        }
        return bindings;
    }

    private static JsonObject view(String expression, String target) {
        JsonObject binding = new JsonObject();
        binding.addProperty("binding_type", "view");
        binding.addProperty("source_property_name", expression);
        binding.addProperty("target_property_name", target);
        return binding;
    }

    /** {@code insert_back} of controls into the element's {@code controls} array. */
    static JsonArray insertBack(JsonArray controls) {
        return insert("insert_back", controls);
    }

    /** {@code insert_front} of controls: drawn before (below) the element's existing controls. */
    static JsonArray insertFront(JsonArray controls) {
        return insert("insert_front", controls);
    }

    private static JsonArray insert(String operation, JsonArray controls) {
        JsonObject insert = new JsonObject();
        insert.addProperty("array_name", "controls");
        insert.addProperty("operation", operation);
        insert.add("value", controls);
        JsonArray modifications = new JsonArray();
        modifications.add(insert);
        return modifications;
    }
}
