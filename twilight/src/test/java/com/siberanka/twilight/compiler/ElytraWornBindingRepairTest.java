package com.siberanka.twilight.compiler;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ElytraWornBindingRepairTest {
    @Test
    void bindsCustomWornIdentifiersToTheirOwnWingTexture() {
        Map<String, byte[]> pack = fixture();
        assertEquals(4, ElytraWornBindingRepair.apply(pack));
        var custom = pack.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("attachables/te_"))
                .map(entry -> description(entry.getValue()))
                .toList();
        assertEquals(2, custom.size());
        for (JsonObject entry : custom) {
            String id = entry.get("identifier").getAsString();
            int variant = id.equals("twilight:red_elytra") ? 1 : 2;
            assertEquals("textures/twilight/elytra/wings_" + variant,
                    entry.getAsJsonObject("textures").get("default").getAsString());
            assertEquals("geometry.elytra", entry.getAsJsonObject("geometry").get("default").getAsString());
            assertEquals("controller.animation.elytra.default",
                    entry.getAsJsonObject("animations").get("default_controller").getAsString());
            assertEquals("controller.render.armor", entry.getAsJsonArray("render_controllers").get(0).getAsString());
            assertFalse(entry.getAsJsonObject("scripts").toString().contains("twilight_elytra_worn_variant"));
        }
        String vanilla = new String(pack.get("attachables/twilight_vanilla_elytra.json"), StandardCharsets.UTF_8);
        assertTrue(vanilla.contains("c.owning_entity->q.variant"));
        String controller = new String(pack.get("render_controllers/twilight_elytra_variant.json"), StandardCharsets.UTF_8);
        assertTrue(controller.contains("Array.elytra_skins[v.twilight_elytra_worn_variant]"));
        assertEquals(0, ElytraWornBindingRepair.apply(pack), "A second pass must leave the pack unchanged");
    }

    @Test
    void skipsVariantWithoutATextureAndPreservesUnrelatedFiles() {
        Map<String, byte[]> pack = fixture();
        pack.remove("textures/twilight/elytra/wings_2.png");
        byte[] unrelated = {1, 2, 3};
        pack.put("unrelated.bin", unrelated);
        ElytraWornBindingRepair.apply(pack);
        assertEquals(1, pack.keySet().stream().filter(name -> name.startsWith("attachables/te_")).count());
        assertSame(unrelated, pack.get("unrelated.bin"));
        assertEquals(0, ElytraWornBindingRepair.apply(new LinkedHashMap<>()));
    }

    private static JsonObject description(byte[] data) {
        return JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject()
                .getAsJsonObject("minecraft:attachable").getAsJsonObject("description");
    }

    private static Map<String, byte[]> fixture() {
        Map<String, byte[]> pack = new LinkedHashMap<>();
        put(pack, "attachables/twilight_vanilla_elytra.json", """
                {"format_version":"1.10.0","minecraft:attachable":{"description":{
                  "identifier":"minecraft:elytra", "geometry":{"default":"geometry.elytra"},
                  "animations":{"default_controller":"controller.animation.elytra.default"},
                  "textures":{"default":"textures/entity/elytra","enchanted":"textures/misc/enchanted_item_glint"},
                  "scripts":{"pre_animation":["v.twilight_elytra_worn_variant = q.variant;"]},
                  "render_controllers":["controller.render.twilight_elytra_variant"]}}}
                """);
        put(pack, "twilight/elytra-wearer-variants.json", """
                {"variants":[{"item_model":"demo:red","variant":1},{"item_model":"demo:blue","variant":2}]}
                """);
        put(pack, "twilight/geyser_item_mappings.json", """
                {"format_version":2,"items":{"minecraft:elytra":[
                  {"model":"demo:red","bedrock_identifier":"twilight:red_elytra"},
                  {"model":"demo:blue","bedrock_identifier":"twilight:blue_elytra"}]}}
                """);
        put(pack, "render_controllers/twilight_elytra_variant.json", """
                {"render_controllers":{"controller.render.twilight_elytra_variant":{
                  "textures":["Array.elytra_skins[q.variant]","Texture.enchanted"]}}}
                """);
        pack.put("textures/twilight/elytra/wings_1.png", new byte[]{1});
        pack.put("textures/twilight/elytra/wings_2.png", new byte[]{2});
        return pack;
    }

    private static void put(Map<String, byte[]> pack, String name, String json) {
        pack.put(name, json.getBytes(StandardCharsets.UTF_8));
    }
}
