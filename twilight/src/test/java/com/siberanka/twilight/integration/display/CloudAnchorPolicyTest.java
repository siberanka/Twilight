package com.siberanka.twilight.integration.display;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CloudAnchorPolicyTest {
    @Test void radiusZeroCloudsAreAnchorsWhetherOrNotJavaFlagsThemInvisible() {
        assertTrue(CloudAnchorPolicy.useAnchor(0));
        assertTrue(CloudAnchorPolicy.useAnchor(-0f));
    }

    @Test void ordinaryCloudsAndInvalidRadiiKeepTheirTranslator() {
        for (float radius : new float[]{Float.MIN_VALUE, 0.1f, 0.5f, 3, 32, -1, Float.NaN, Float.POSITIVE_INFINITY}) {
            assertFalse(CloudAnchorPolicy.useAnchor(radius), "radius " + radius);
        }
    }

    @Test void anchorsAreAlwaysInvisibleAndCloudsKeepJavasFlag() {
        assertTrue(CloudAnchorPolicy.invisible(0, false), "a visible radius-0 cloud must not show an armor stand");
        assertTrue(CloudAnchorPolicy.invisible(0, true));
        assertTrue(CloudAnchorPolicy.invisible(3, true));
        assertFalse(CloudAnchorPolicy.invisible(3, false));
    }
}
