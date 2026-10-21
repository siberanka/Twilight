package com.siberanka.twilight.geyser;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BedrockOffhandPolicyTest {

    @Test
    void reliesOnTheJavaBaseMaterialNotTheCustomNameOrRenderedModel() {
        for (String base : new String[] {
                "minecraft:shield", "minecraft:totem_of_undying", "minecraft:arrow", "minecraft:tipped_arrow",
                "minecraft:filled_map", "minecraft:firework_rocket", "minecraft:nautilus_shell"
        }) assertTrue(BedrockOffhandPolicy.allowsBaseItem(base), base);
        for (String base : new String[] {
                "minecraft:paper", "minecraft:diamond_sword", "minecraft:diamond_pickaxe", "minecraft:bow",
                "minecraft:crossbow", "minecraft:trident", "minecraft:fishing_rod", "minecraft:stick",
                "minecraft:map", "minecraft:spectral_arrow", "minecraft:totem", "minecraft:iron_axe"
        }) assertFalse(BedrockOffhandPolicy.allowsBaseItem(base), base);
        assertFalse(BedrockOffhandPolicy.allowsBaseItem(null));
    }

    @Test
    void allCustomMappingsUseBaseMaterialAndPreserveEverythingElse() {
        String original = """
                {"format_version":2,"items":{
                  "minecraft:paper":[
                    {"type":"legacy","custom_model_data":10000,"bedrock_identifier":"twilight:custom_shield_sprite","bedrock_options":{"icon":"shield"}},
                    {"type":"definition","model":"example:tool","bedrock_identifier":"twilight:custom_tool","predicate":{"type":"condition","property":"custom_model_data"},"priority":2,"bedrock_options":{"icon":"tool","display_handheld":true,"allow_offhand":true}}
                  ],
                  "minecraft:shield":[
                    {"type":"legacy","custom_model_data":10000,"bedrock_identifier":"twilight:custom_sword_sprite","bedrock_options":{"icon":"sword","allow_offhand":false}}
                  ],
                  "minecraft:filled_map":[
                    {"type":"legacy","custom_model_data":10001,"bedrock_identifier":"twilight:map_custom","bedrock_options":{"icon":"map"}}
                  ],
                  "minecraft:bow":[{"type":"legacy","custom_model_data":10003,"bedrock_identifier":"twilight:bow","bedrock_options":{"icon":"bow"}}]
                }}
                """;
        Map<String, byte[]> pack = new LinkedHashMap<>();
        String mappingPath = "twilight/geyser_item_mappings.json";
        pack.put(mappingPath, original.getBytes(StandardCharsets.UTF_8));
        pack.put("textures/twilight/unrelated.png", new byte[] {1,2,3});
        assertEquals(4, BedrockOffhandPolicy.apply(pack));
        JsonObject actual = JsonParser.parseString(new String(pack.get(mappingPath), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject expected = JsonParser.parseString(original).getAsJsonObject();
        // Compare copies after stripping only the property this feature owns.
        JsonObject normalizedActual = actual.deepCopy();
        for (String base : expected.getAsJsonObject("items").keySet()) {
            var a = normalizedActual.getAsJsonObject("items").getAsJsonArray(base);
            var e = expected.getAsJsonObject("items").getAsJsonArray(base);
            for (int i=0;i<a.size();i++) {
                var opts = a.get(i).getAsJsonObject().getAsJsonObject("bedrock_options");
                var old = e.get(i).getAsJsonObject().getAsJsonObject("bedrock_options");
                opts.remove("allow_offhand");
                old.remove("allow_offhand");
            }
            assertEquals(e,a,"All selector and rendering fields must be untouched: "+base);
        }
        var defs = actual.getAsJsonObject("items");
        assertFalse(defs.getAsJsonArray("minecraft:paper").get(0).getAsJsonObject().getAsJsonObject("bedrock_options").get("allow_offhand").getAsBoolean());
        assertFalse(defs.getAsJsonArray("minecraft:paper").get(1).getAsJsonObject().getAsJsonObject("bedrock_options").get("allow_offhand").getAsBoolean());
        assertFalse(defs.getAsJsonArray("minecraft:bow").get(0).getAsJsonObject().getAsJsonObject("bedrock_options").get("allow_offhand").getAsBoolean());
        assertEquals(0, BedrockOffhandPolicy.apply(pack), "Idempotent on second pass");
        assertArrayEquals(new byte[] {1,2,3},pack.get("textures/twilight/unrelated.png"));
    }

    @Test
    void emptyPackOrMissingMappingsIsNoOp() {
        assertEquals(0, BedrockOffhandPolicy.apply(new LinkedHashMap<>()));
    }
}
