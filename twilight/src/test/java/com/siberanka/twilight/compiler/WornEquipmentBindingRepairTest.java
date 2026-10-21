package com.siberanka.twilight.compiler;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WornEquipmentBindingRepairTest {
    private static final String IDENTIFIER = "twilight:topaz_helmet";
    private static final String WORN = "attachables/topaz_helmet.worn.json";

    @Test
    void bindsWearableToFinalMappingAndKeepsGeometryAndTexture() {
        Map<String, byte[]> files = fixture("minecraft:chainmail_helmet");
        assertEquals(1, BedrockPackCompiler.repairWornEquipmentBindings(files));
        JsonObject description = description(files, WORN);
        assertEquals("twilight:topaz_helmet.worn", description.get("identifier").getAsString());
        assertEquals("geometry.humanoid.armor.helmet", description.getAsJsonObject("geometry").get("default").getAsString());
        assertEquals("textures/topaz", description.getAsJsonObject("textures").get("default").getAsString());
        JsonObject binding = description.getAsJsonObject("item");
        assertEquals(1, binding.size());
        assertTrue(binding.get(IDENTIFIER).getAsString().contains("slot.armor.head"));
        assertFalse(binding.has("minecraft:chainmail_helmet"));
    }

    @Test
    void doesNotRewriteAlreadyCorrectWearablesOrLocalArmor() {
        Map<String, byte[]> files = fixture(IDENTIFIER);
        files.put("attachables/topaz_helmet.json", bytes("{\"minecraft:attachable\":{\"description\":{\"identifier\":\"local\"}}}"));
        byte[] correct = files.get(WORN).clone();
        byte[] local = files.get("attachables/topaz_helmet.json").clone();
        assertEquals(0, BedrockPackCompiler.repairWornEquipmentBindings(files));
        org.junit.jupiter.api.Assertions.assertArrayEquals(correct, files.get(WORN));
        org.junit.jupiter.api.Assertions.assertArrayEquals(local, files.get("attachables/topaz_helmet.json"));
    }

    private static Map<String, byte[]> fixture(String originalKey) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("twilight/geyser_item_mappings.json", bytes("""
                {"items":{"minecraft:chainmail_helmet":[{"bedrock_identifier":"twilight:topaz_helmet",
                "components":{"minecraft:equippable":{"slot":"head"}}}]}}
                """));
        files.put(WORN, bytes("""
                {"minecraft:attachable":{"description":{
                "identifier":"twilight:topaz_helmet.worn",
                "item":{"%s":"query.is_item_name_any('slot.armor.head', 0, 'twilight:topaz_helmet')"},
                "geometry":{"default":"geometry.humanoid.armor.helmet"},
                "textures":{"default":"textures/topaz"}}}}
                """.formatted(originalKey)));
        return files;
    }

    private static JsonObject description(Map<String, byte[]> files, String path) {
        return JsonParser.parseString(new String(files.get(path), StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonObject("minecraft:attachable").getAsJsonObject("description");
    }

    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
}
