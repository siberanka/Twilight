/* Copyright (c) 2026 siberanka. Twilight License (PolyForm Noncommercial 1.0.0 + terms), see LICENSE. */
package com.siberanka.twilight.integration.display;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DisplayAppearanceTest {
    @Test void hiddenDisplayRetainsItemAndContextUpdatesUntilShownAgain() {
        var appearance = new DisplayAppearance();
        appearance.variant(37);
        appearance.context((byte) 5);
        assertEquals(338, appearance.packed());
        appearance.viewRange(0);
        assertEquals(-9, appearance.packed());
        appearance.variant(40);
        appearance.context((byte) 8);
        assertEquals(-9, appearance.packed());
        appearance.viewRange(1);
        assertEquals(368, appearance.packed());
    }

    @Test void emptyItemRemainsHiddenWhenRangeIsRestored() {
        var appearance = new DisplayAppearance();
        appearance.variant(3);
        appearance.viewRange(0);
        appearance.variant(-1);
        appearance.viewRange(2);
        assertEquals(-9, appearance.packed());
        appearance.variant(4);
        assertEquals(36, appearance.packed());
    }

    @Test void rangeVisibilityMatchesJavaSquaredDistanceEdgeCases() {
        var appearance = new DisplayAppearance();
        appearance.variant(2);
        for (float range : new float[]{0, -0f, Float.NaN}) {
            appearance.viewRange(range);
            assertEquals(-9, appearance.packed());
        }
        for (float range : new float[]{-1, 0.5f, Float.POSITIVE_INFINITY}) {
            appearance.viewRange(range);
            assertEquals(18, appearance.packed());
        }
    }
}
