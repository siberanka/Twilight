package com.siberanka.twilight.integration.display;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CloudAnchorPolicyTest {
    @Test void invisiblePointCloudsAreAnchors() {
        assertTrue(CloudAnchorPolicy.useAnchor(0, true));
        assertTrue(CloudAnchorPolicy.useAnchor(-0f, true));
    }

    @Test void ordinaryCloudsAndInvalidRadiiKeepTheirTranslator() {
        assertFalse(CloudAnchorPolicy.useAnchor(0, false));
        for (float radius : new float[]{Float.MIN_VALUE, 0.1f, 0.5f, 3, 32, -1, Float.NaN, Float.POSITIVE_INFINITY}) {
            assertFalse(CloudAnchorPolicy.useAnchor(radius, true));
            assertFalse(CloudAnchorPolicy.useAnchor(radius, false));
        }
    }
}
