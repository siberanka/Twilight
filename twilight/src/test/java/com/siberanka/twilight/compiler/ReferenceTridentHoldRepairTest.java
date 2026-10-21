package com.siberanka.twilight.compiler;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Regression test: 3D tridents with a vanilla wield controller must keep bone poses as maps. */
class ReferenceTridentHoldRepairTest {
    @Test
    void threeDimensionalTridentUsesBoneMapsInsteadOfBooleans() {
        Map<String, byte[]> pack = fixture();
        ReferenceTridentHoldRepair.AssetReader source = path -> switch (path) {
            case "assets/example/items/custom_trident.json" -> bytes("""
                    {"model":{"type":"minecraft:model","model":"example:item/custom_trident"}}
                    """);
            case "assets/example/models/item/custom_trident.json" -> bytes("""
                    {"display":{"firstperson_righthand":{"rotation":[10,20,30],
                     "translation":[1,2,3],"scale":[1,1,1]},
                     "thirdperson_righthand":{"rotation":[0,90,0]}}}
                    """);
            default -> null;
        };

        assertEquals(2, ReferenceTridentHoldRepair.apply(pack, source));

        Map<?, ?> animations = object(json(pack.get("animations/custom_trident.animation.json")));
        Map<?, ?> entries = object(animations.get("animations"));
        for (String view : new String[]{"first_person_right", "first_person_left",
                "third_person_right", "third_person_left", "head"}) {
            Map<?, ?> animation = object(entries.get("animation.twilight.custom_trident." + view));
            assertEquals(Boolean.TRUE, animation.get("loop"));
            Map<?, ?> bones = object(animation.get("bones"));
            assertTrue(bones.containsKey("bone"));
            assertTrue(bones.containsKey("bone_x"));
            assertTrue(bones.containsKey("bone_y"));
            assertTrue(bones.containsKey("bone_z"));
            assertTrue(bones.containsKey("bone_basis"));

            Map<?, ?> offset = object(entries.get("animation.twilight.custom_trident.java_throw_offset_" + view));
            assertFalse(object(offset.get("bones")).containsKey("bone"));
        }

        Map<?, ?> root = object(json(pack.get("attachables/custom_trident.json")));
        Map<?, ?> description = object(object(root.get("minecraft:attachable")).get("description"));
        Map<?, ?> scripts = object(description.get("scripts"));
        assertEquals(11, ((java.util.List<?>) scripts.get("animate")).size());
    }

    static Map<String, byte[]> fixture() {
        Map<String, byte[]> pack = new LinkedHashMap<>();
        pack.put("twilight/geyser_item_mappings.json", bytes("""
                {"format_version":2,"items":{"minecraft:trident":[{
                "type":"definition","model":"example:custom_trident",
                "bedrock_identifier":"twilight:custom_trident"}]}}
                """));
        pack.put("attachables/custom_trident.json", bytes("""
                {"format_version":"1.10.0","minecraft:attachable":{"description":{
                "identifier":"twilight:custom_trident",
                "animations":{"wield":"controller.animation.trident.wield"},
                "scripts":{"animate":["wield"]}}}}
                """));
        pack.put("models/entity/geometry.custom_trident.geo.json", bytes("""
                {"format_version":"1.21.0","minecraft:geometry":[{"description":
                {"identifier":"geometry.twilight.custom_trident"},"bones":[
                {"name":"pole","cubes":[]},{"name":"bone","cubes":[]},
                {"name":"bone_x","cubes":[]},{"name":"bone_y","cubes":[]},
                {"name":"bone_z","cubes":[]},
                {"name":"bone_basis","cubes":[{},{}]}]}]}
                """));
        pack.put("animations/custom_trident.animation.json", bytes("""
                {"format_version":"1.10.0","animations":{}}
                """));
        return pack;
    }

    static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    static Object json(byte[] bytes) {
        return BedrockAnimationBridge.Json.read(bytes);
    }

    static Map<?, ?> object(Object value) {
        assertInstanceOf(Map.class, value);
        return (Map<?, ?>) value;
    }
}
