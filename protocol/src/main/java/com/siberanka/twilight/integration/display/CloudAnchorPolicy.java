/* Copyright (c) 2026 siberanka. Twilight License (PolyForm Noncommercial 1.0.0 + terms), see LICENSE. */
package com.siberanka.twilight.integration.display;

/** A Java invisible point cloud is a non-rendering anchor, not a lingering effect. */
final class CloudAnchorPolicy {
    private CloudAnchorPolicy() { }

    static boolean useAnchor(float radius, boolean invisible) {
        return invisible && radius == 0f;
    }
}
