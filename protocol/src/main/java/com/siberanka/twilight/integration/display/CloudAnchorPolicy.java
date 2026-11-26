/* Copyright (c) 2026 siberanka. Twilight License (PolyForm Noncommercial 1.0.0 + terms), see LICENSE. */
package com.siberanka.twilight.integration.display;

/**
 * A Java point cloud is a non-rendering anchor, not a lingering effect. The Java client emits
 * {@code ceil(PI * radius^2)} particles per tick for an area effect cloud, so a radius-0 cloud draws nothing
 * whether or not the entity is flagged invisible (furniture and model plugins use them as seat and mount
 * carriers). Bedrock clamps a cloud to a minimum radius of 0.5 and always draws its swirl, so the cloud is shown
 * as an inert invisible actor instead.
 */
final class CloudAnchorPolicy {
    private CloudAnchorPolicy() { }

    /** Whether a cloud of this radius is shown as an anchor; the Java invisible flag does not matter. */
    static boolean useAnchor(float radius) {
        return radius == 0f;
    }

    /** The invisible flag the Bedrock actor carries: the anchor stand must never be drawn. */
    static boolean invisible(float radius, boolean javaInvisible) {
        return javaInvisible || useAnchor(radius);
    }
}
