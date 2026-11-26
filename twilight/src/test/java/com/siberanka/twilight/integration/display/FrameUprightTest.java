/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.integration.display;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Geyser turns a Java frame entity by the facing's yaw (pitch for up and down) in every move it sends. The pose is
 * relative to an unturned entity, so a turned display drew the model back to front inside the wall (north) or
 * sideways: the frame must override every Geyser path that carries a rotation, or north frames show nothing.
 * Geyser is not on the test class path, so the compiled class is read for its method names.
 */
class FrameUprightTest {
    @Test void everyRotatingMovePathIsOverridden() throws Exception {
        byte[] bytes;
        try (var in = FrameUprightTest.class.getResourceAsStream("TwilightItemFrame.class")) {
            bytes = in.readAllBytes();
        }
        String pool = new String(bytes, StandardCharsets.ISO_8859_1);
        for (String method : new String[]{"moveRelativeRaw", "moveAbsoluteRaw", "teleport", "updatePositionAndRotation",
                "updateRotation", "updateHeadLookRotation"}) {
            assertTrue(pool.contains(method), "TwilightItemFrame must override " + method + " and keep the entity unturned");
        }
    }
}
