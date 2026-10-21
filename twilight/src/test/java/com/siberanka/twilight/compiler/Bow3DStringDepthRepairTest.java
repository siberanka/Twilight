package com.siberanka.twilight.compiler;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

/** Regression guard: no double drawback, no projectile changes. */
class Bow3DStringDepthRepairTest {
    @Test
    void neverMovesAlreadyConvertedStringArrowOrCrossbow() {
        Map<String, byte[]> pack = new LinkedHashMap<>();
        for (int state = 0; state < 4; state++) {
            String key = "models/entity/geometry.example_bow_state_" + state + ".geo.json";
            pack.put(key, ("{\"origin\":[1,2," + (state * 3.125) + "],\"arrow\":true}")
                    .getBytes(StandardCharsets.UTF_8));
        }
        pack.put("models/entity/geometry.example_crossbow_state_1.geo.json",
                "crossbow unchanged".getBytes(StandardCharsets.UTF_8));
        Map<String, byte[]> original = new LinkedHashMap<>();
        pack.forEach((k,v) -> original.put(k,v.clone()));
        assertEquals(0, Bow3DStringDepthRepair.apply(pack));
        assertEquals(original.keySet(), pack.keySet());
        original.forEach((k,v) -> assertArrayEquals(v, pack.get(k), k));
        assertFalse(pack.containsKey("twilight/3d-bow-string-depth-repair.json"));
    }

    @Test
    void existingJavaTextureUvMotionAlreadyFollowsTheArrowWithoutGeometryOffset() {
        // Measurements from Twilight(20261014-222920)(1).mcpack, NOT production constants.
        // The 24px UV window maps onto a 6-model-unit string ribbon.
        // Each state's two opaque texels lie at the shown x positions.
        // Its trailing visible texel aligns exactly with the untouched arrow.
        double bowRibbonZ = -9.5;
        double ribbonLength = 6;
        double uvWidth = 24;
        double[] atlasOffsets = {0, 192, 288};
        double[] trailingVisiblePixels = {1, 205, 311};
        double[] arrowZ = {-3.75, -6.75, -9.25};
        for (int i = 0; i < arrowZ.length; i++) {
            double stringAtArrowHeight = bowRibbonZ + ribbonLength
                    - (trailingVisiblePixels[i] - atlasOffsets[i]) / uvWidth * ribbonLength;
            assertEquals(arrowZ[i], stringAtArrowHeight, 0.000001,
                    "The texture itself already encodes this state's drawback");
        }
    }

    @Test
    void differentBowPositionIsNotReplacedWithAnyHardcodedOffset() {
        byte[] independent = "{\"stringZ\":2.734,\"arrowZ\":5.321}".getBytes(StandardCharsets.UTF_8);
        Map<String, byte[]> pack = new LinkedHashMap<>();
        pack.put("models/entity/geometry.another_bow.geo.json", independent.clone());
        assertEquals(0, Bow3DStringDepthRepair.apply(pack));
        assertTrue(Arrays.equals(independent, pack.get("models/entity/geometry.another_bow.geo.json")));
    }
}
