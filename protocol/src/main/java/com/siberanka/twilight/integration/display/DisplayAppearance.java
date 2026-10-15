/* Copyright (c) 2026 siberanka. Twilight License (PolyForm Noncommercial 1.0.0 + terms), see LICENSE. */
package com.siberanka.twilight.integration.display;

/** Keeps the selected item while a provider temporarily disables display rendering. */
final class DisplayAppearance {
    private int variant = -1;
    private int context;
    private float viewRange = 1;

    void variant(int value) { variant = value; }
    void context(byte value) { context = Math.clamp((int) value, 0, 8); }
    void viewRange(float value) { viewRange = value; }
    int context() { return context; }

    int packed() {
        // Java compares distance against the squared range. Zero (including -0)
        // and NaN therefore render nothing. Negative ranges are not a hide flag.
        return variant < 0 || viewRange == 0 || Float.isNaN(viewRange) ? -9 : variant * 9 + context;
    }
}
