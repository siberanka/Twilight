package com.siberanka.twilight.compiler;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.siberanka.twilight.text.LayerEncoding;
import com.siberanka.twilight.text.LayeredTextLayout;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Generated chest and HUD labels for layered text (expression forms measured live on Bedrock). */
class LayerLabelsTest {
    @Test
    void blockExpressionsCutTheTextByBytes() {
        LayerLabels.Source title = LayerLabels.Source.variable("$container_title", LayerLabels.CHEST_EMPTY);
        assertEquals("(('%.768s' * $twilight_text) + ('%.0s' * #gamepad_helper_visible))",
                LayerLabels.blockExpression(title, 0, 768));
        assertEquals("(('%.768s' * ($twilight_text - ('%.1536s' * $twilight_text))) + ('%.0s' * #gamepad_helper_visible))",
                LayerLabels.blockExpression(title, 2, 768));
        JsonObject boss = new JsonObject();
        boss.addProperty("binding_name", "#bossName");
        assertEquals("('%.96s' * #bossName)", LayerLabels.blockExpression(LayerLabels.Source.binding(boss, "#bossName"), 0, 96),
                "a binding property needs no helper property");
    }

    @Test
    void chestTitleGetsOneLabelPerLayerAndHidesTheVanillaLabel() {
        JsonObject screen = JavaContainerUi.chestScreen(true, true);
        JsonObject vanilla = screen.getAsJsonObject("chest_label");
        assertEquals("$container_title", vanilla.get("$twilight_text").getAsString());
        assertEquals(LayerLabels.LAYER_PROPERTY, vanilla.get("text").getAsString(), "the vanilla label shows block 0");
        assertTrue(vanilla.getAsJsonArray("bindings").toString().contains("%.768s"));
        JsonObject panel = screen.getAsJsonObject("twilight_title_layers");
        assertEquals("$twilight_java_title_offset", panel.get("offset").getAsString());
        JsonArray labels = panel.getAsJsonArray("controls");
        assertEquals(LayeredTextLayout.MAX_LAYERS - 1, labels.size());
        for (int index = 0; index < labels.size(); index++) {
            int layer = LayeredTextLayout.MAX_LAYERS - 1 - index; // drawn bottom layer first
            JsonObject label = labels.get(index).getAsJsonObject().getAsJsonObject("twilight_title_layer_" + layer);
            assertEquals(LayerLabels.LAYER_PROPERTY, label.get("text").getAsString());
            assertTrue(label.getAsJsonArray("bindings").toString().contains("%." + layer * 768 + "s"));
        }
        for (String half : new String[]{"small_chest_panel_top_half", "large_chest_panel_top_half"}) {
            assertTrue(screen.getAsJsonObject(half).getAsJsonArray("modifications").toString()
                    .contains("twilight_title_layers@chest.twilight_title_layers"), half);
            assertTrue(screen.getAsJsonObject(half).getAsJsonArray("modifications").toString().contains("insert_front"),
                    "lower layers are drawn before the vanilla title label");
        }
        assertNull(JavaContainerUi.chestScreen(true, false).get("twilight_title_layers"), "layers can be disabled");
    }

    @Test
    void hudLabelsKeepVanillaValuesAndClearTheActionBarBackdrop() {
        JsonObject hud = JavaHudUi.hudScreen();
        JsonObject actionbar = hud.getAsJsonObject("hud_actionbar_text");
        assertEquals(JavaHudUi.CLEAR_TEXTURE, actionbar.get("texture").getAsString());
        JsonArray modifications = actionbar.getAsJsonArray("modifications");
        assertEquals("remove", modifications.get(0).getAsJsonObject().get("operation").getAsString());
        JsonArray inserted = modifications.get(1).getAsJsonObject().getAsJsonArray("value");
        assertEquals(LayeredTextLayout.MAX_LAYERS, inserted.size());
        JsonObject vanilla = inserted.get(inserted.size() - 1).getAsJsonObject().getAsJsonObject("actionbar_message");
        assertEquals("$actionbar_text", vanilla.get("$twilight_text").getAsString());
        assertEquals(31, vanilla.get("layer").getAsInt());
        JsonObject boss = hud.getAsJsonObject("boss_name_panel").getAsJsonArray("modifications").get(1).getAsJsonObject()
                .getAsJsonArray("value").get(0).getAsJsonObject().getAsJsonObject("twilight_boss_name_layer_3");
        assertTrue(boss.getAsJsonArray("bindings").toString().contains("\"binding_collection_name\":\"boss_bars\""));
        assertEquals("top_middle", boss.get("anchor_from").getAsString());
    }

    @Test
    void styledBossBarsDrawTheJavaSpritesInJavasOrder() {
        java.util.Set<String> sprites = java.util.Set.of("red_background", "red_progress", "notched_6_background",
                "notched_6_progress", "notched_20_progress");
        JsonObject hud = JavaHudUi.hudScreen(java.util.Set.of("red"), sprites);
        JsonArray inserted = hud.getAsJsonObject("boss_health_panel").getAsJsonArray("modifications").get(1)
                .getAsJsonObject().getAsJsonArray("value");
        JsonObject vanilla = inserted.get(0).getAsJsonObject()
                .getAsJsonObject("progress_bar_for_collections@common.progress_bar_for_collections");
        String hidden = com.siberanka.twilight.text.LayerEncoding.CLIENT_PREFIX + com.siberanka.twilight.text.LayerEncoding.HIDDEN_BAR;
        String layeredHidden = com.siberanka.twilight.text.LayerEncoding.CLIENT_PREFIX
                + com.siberanka.twilight.text.LayerEncoding.LAYERED + com.siberanka.twilight.text.LayerEncoding.HIDDEN_BAR;
        String barBindings = vanilla.getAsJsonArray("bindings").toString();
        assertTrue(barBindings.contains("(not ((('%." + hidden.getBytes(java.nio.charset.StandardCharsets.UTF_8).length + "s'"),
                "Bedrock's bar is hidden for hidden and styled colours: " + barBindings);
        assertTrue(barBindings.contains(layeredHidden), "also when the name is layered");
        JsonObject red = inserted.get(1).getAsJsonObject().getAsJsonObject("twilight_boss_bar_red");
        assertEquals("[0,10]", red.get("offset").toString());
        String colourMarker = com.siberanka.twilight.text.LayerEncoding.CLIENT_PREFIX
                + com.siberanka.twilight.text.LayerEncoding.styledBarColour(BossBars.COLOURS.indexOf("red"));
        assertTrue(red.getAsJsonArray("bindings").toString().contains(colourMarker));
        JsonArray images = red.getAsJsonArray("controls");
        java.util.List<String> order = new java.util.ArrayList<>();
        images.forEach(image -> order.add(image.getAsJsonObject().keySet().iterator().next()));
        assertEquals(java.util.List.of("twilight_boss_background", "twilight_boss_notch_background_0", "twilight_boss_progress",
                "twilight_boss_notch_progress_0", "twilight_boss_notch_progress_3"), order);
        JsonObject progress = images.get(2).getAsJsonObject().getAsJsonObject("twilight_boss_progress");
        assertEquals(BossBars.BEDROCK_FOLDER + "red_progress", progress.get("texture").getAsString());
        assertEquals("left", progress.get("clip_direction").getAsString());
        assertTrue(progress.getAsJsonArray("bindings").toString().contains("#progress_percentage"));
        JsonObject notch = images.get(4).getAsJsonObject().getAsJsonObject("twilight_boss_notch_progress_3");
        String notchMarker = com.siberanka.twilight.text.LayerEncoding.CLIENT_PREFIX
                + com.siberanka.twilight.text.LayerEncoding.styledBar(BossBars.COLOURS.indexOf("red"), 4);
        assertTrue(notch.getAsJsonArray("bindings").toString().contains(notchMarker), "notched_20 is Java overlay 4");
        assertEquals(2, inserted.size(), "colours without sprites keep Bedrock's bar");
    }

    @Test
    void longBossNamesThatAreNotLayeredStayWhole() {
        JsonObject hud = JavaHudUi.hudScreen();
        JsonArray names = hud.getAsJsonObject("boss_name_panel").getAsJsonArray("modifications").get(1).getAsJsonObject()
                .getAsJsonArray("value");
        assertEquals(LayeredTextLayout.MAX_LAYERS + 1, names.size(), "block labels plus the whole-name label");
        String layered = "('%." + (LayerEncoding.bytes(LayerEncoding.CLIENT_PREFIX + LayerEncoding.LAYERED)) + "s' * #bossName) = '"
                + LayerEncoding.CLIENT_PREFIX + LayerEncoding.LAYERED + "'";
        for (int index = 0; index < LayeredTextLayout.MAX_LAYERS; index++) {
            JsonObject label = names.get(index).getAsJsonObject().entrySet().iterator().next().getValue().getAsJsonObject();
            String bindings = label.getAsJsonArray("bindings").toString();
            assertTrue(bindings.contains(layered) && bindings.contains("#visible"), "block labels only for layered names");
        }
        JsonObject whole = names.get(LayeredTextLayout.MAX_LAYERS).getAsJsonObject().getAsJsonObject("boss_name");
        assertEquals("#bossName", whole.get("text").getAsString(), "the whole name, never cut at a block");
        assertEquals(LayerLabels.LINE_PADDING, whole.get("line_padding").getAsInt(), "same line spacing as the block labels");
        String wholeBindings = whole.getAsJsonArray("bindings").toString();
        assertTrue(wholeBindings.contains("(not (" + layered), "hidden for layered names: " + wholeBindings);
    }
}
