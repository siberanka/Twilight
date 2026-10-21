/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.integration.display;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The item of an invisible frame sits where Java's ItemFrameRenderer draws it, at half size, turned with the frame. */
class FramePoseTest {
    private static final float EPSILON = 1e-5f;

    @Test void aFloorFrameDrawsItsItemOnTheFloor() {
        float[] pose = FramePose.pose(new int[]{0, 1, 0}, 0, 0);
        // The frame entity sits 0.46875 below the block centre; the item is half a block out of the frame: the floor.
        assertArrayEquals(new float[]{0, -0.03125f, 0}, new float[]{pose[0], pose[1], pose[2]}, EPSILON);
        assertArrayEquals(new float[]{0.5f, 0.5f, 0.5f}, new float[]{pose[3], pose[4], pose[5]}, EPSILON);
        assertArrayEquals(new float[]{0, 1, 0, 0}, new float[]{pose[10], pose[11], pose[12], pose[13]}, EPSILON,
                "the item display's half turn is undone");
    }

    @Test void aWallFrameDrawsItsItemOnTheWall() {
        float[] pose = FramePose.pose(new int[]{0, 0, -1}, 180, 0);
        assertEquals(0, pose[0], EPSILON);
        assertEquals(0, pose[1], EPSILON);
        assertEquals(0.03125f, pose[2], EPSILON, "back towards the wall the frame hangs on");
    }

    @Test void eachTurnIsAnEighth() {
        float[] none = FramePose.pose(new int[]{0, 0, 1}, 0, 0), half = FramePose.pose(new int[]{0, 0, 1}, 0, 4);
        double dot = 0;
        for (int i = 6; i < 10; i++) dot += none[i] * half[i];
        assertEquals(0, dot, EPSILON, "four eighths are a half turn: orthogonal quaternions");
    }
}
