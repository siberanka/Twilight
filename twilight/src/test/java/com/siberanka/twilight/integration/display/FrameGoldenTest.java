/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.integration.display;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Golden comparison of Java's ItemFrameRenderer chain (facing offset, facing rotation, half block out, item turn, half
 * scale, the model's {@code fixed} display transform) with Twilight's Bedrock chain (pose bones, the half turn of
 * the display entity, context bones), for every facing and item rotation, in world space.
 */
class FrameGoldenTest {
    private static final double TOLERANCE = 2e-3;
    private static final int[][] STEPS = {{0, 0, 1}, {1, 0, 0}, {-1, 0, 0}, {0, 0, -1}, {0, 1, 0}, {0, -1, 0}};
    private static final float[] YAW = {0, 270, 90, 180, 0, 0};
    private static final String[] NAMES = {"south", "east", "west", "north", "up", "down"};
    /** Java display.fixed as translation (1/16), rotation, scale: the globe's, a tilted one and the identity. */
    private static final double[][] FIXED = {
            {2, 0, -16, -90, 0, 0, 2, 2, 2},
            {1, -3, 5, 20, 35, -50, 1.5, 0.75, 1.25},
            {0, 0, 0, 0, 0, 0, 1, 1, 1}};
    private static final double[][] POINTS = {{.5, .5, .5}, {0, 0, 0}, {1, 1, 1}, {1, 0, .3}, {.2, .9, .7}};

    @Test void everyFacingMatchesJavaForEveryItemRotationAndFixedTransform() {
        for (double[] fixed : FIXED) for (int turns = 0; turns < 8; turns++) for (int facing = 0; facing < 6; facing++) {
            double[][] java = java(STEPS[facing], YAW[facing], turns, fixed);
            double[][] bedrock = bedrock(FramePose.pose(STEPS[facing], YAW[facing], turns), fixed);
            for (double[] point : POINTS) {
                double[] c = {point[0] - .5, point[1] - .5, point[2] - .5};
                double[] expected = apply(java, c), actual = apply(bedrock, new double[]{c[0], c[1], -c[2]});
                actual[2] = -actual[2];
                for (int i = 0; i < 3; i++) assertEquals(expected[i], actual[i], TOLERANCE,
                        NAMES[facing] + " turns " + turns + " fixed " + fixed[0] + "," + fixed[2] + " point " + point[0]
                                + "," + point[1] + "," + point[2] + " axis " + i);
            }
        }
    }

    @Test void northIsExactlyTheIdentityFrame() {
        float[] pose = FramePose.pose(STEPS[3], 180, 0);
        assertArrayEquals(new float[]{0, 0, 0, 1}, new float[]{pose[6], pose[7], pose[8], pose[9]}, 0f,
                "no -0, no sign noise: the left turn is the exact identity");
        assertEquals(0.03125f, pose[2], 0f);
        for (int turns = 0; turns < 8; turns++) for (int facing = 0; facing < 6; facing++) {
            float[] p = FramePose.pose(STEPS[facing], YAW[facing], turns);
            for (float value : p) assertTrue(Float.isFinite(value));
            for (int offset : new int[]{6, 10}) {
                double length = 0;
                for (int i = offset; i < offset + 4; i++) length += (double) p[i] * p[i];
                assertEquals(1, length, 1e-6, NAMES[facing] + " quaternion is a unit");
            }
        }
    }

    // Java: ItemFrameRenderer, then ItemTransform.apply for the fixed context.
    private static double[][] java(int[] step, float yRot, int turns, double[] fixed) {
        double xRot = step[1] == 0 ? 0 : -90 * step[1], yTurn = step[1] == 0 ? 180 - yRot : 180;
        double[][] m = translate(step[0] * .46875, step[1] * .46875, step[2] * .46875);
        m = mul(m, rotate(0, xRot));
        m = mul(m, rotate(1, yTurn));
        m = mul(m, translate(0, 0, .5));
        m = mul(m, rotate(2, turns * 45));
        m = mul(m, scale(.5, .5, .5));
        m = mul(m, translate(fixed[0] / 16, fixed[1] / 16, fixed[2] / 16));
        m = mul(m, mul(rotate(0, fixed[3]), mul(rotate(1, fixed[4]), rotate(2, fixed[5]))));
        return mul(m, scale(fixed[6], fixed[7], fixed[8]));
    }

    // Twilight: bones translation, lz ly lx, scale, rz ry rx, item_flip, ctx_t, ctx_x ctx_y ctx_z, ctx_s.
    private static double[][] bedrock(float[] pose, double[] fixed) {
        double[] left = euler(pose, 6), right = euler(pose, 10);
        double[][] m = translate(pose[0], pose[1], -pose[2]);
        m = mul(m, mul(bone(2, -left[2]), mul(bone(1, -left[1]), bone(0, left[0]))));
        m = mul(m, scale(pose[3], pose[4], pose[5]));
        m = mul(m, mul(bone(2, -right[2]), mul(bone(1, -right[1]), bone(0, right[0]))));
        m = mul(m, bone(1, 180));
        // DisplayEntityResources.contextValues: translation x, y, -z; rotation x, -y, -z; scale as is.
        m = mul(m, translate(fixed[0] / 16, fixed[1] / 16, -fixed[2] / 16));
        m = mul(m, mul(bone(0, fixed[3]), mul(bone(1, -fixed[4]), bone(2, -fixed[5]))));
        return mul(m, scale(fixed[6], fixed[7], fixed[8]));
    }

    /** Bedrock's bone rotation sense in the mirrored (-Z) space: X and Z reversed against a right-handed rotation. */
    private static double[][] bone(int axis, double degrees) {
        return rotate(axis, axis == 1 ? degrees : -degrees);
    }

    /** DisplayEntityResources.quaternionScript, in floats like Molang. */
    private static double[] euler(float[] pose, int offset) {
        float x = pose[offset], y = pose[offset + 1], z = pose[offset + 2], w = pose[offset + 3];
        float n = Math.max((float) Math.sqrt(x * x + y * y + z * z + w * w), 0.000001f);
        x /= n; y /= n; z /= n; w /= n;
        float sy = Math.clamp(2 * (w * y - z * x), -1f, 1f);
        boolean singular = Math.abs(sy) > 0.9999999f;
        double ex = singular ? 0 : Math.atan2(2 * (w * x + y * z), 1 - 2 * (x * x + y * y));
        double ez = singular ? Math.atan2(2 * (w * z - x * y), 1 - 2 * (x * x + z * z))
                : Math.atan2(2 * (w * z + x * y), 1 - 2 * (y * y + z * z));
        return new double[]{Math.toDegrees(ex), Math.toDegrees(Math.asin(sy)), Math.toDegrees(ez)};
    }

    private static double[][] identity() {
        return new double[][]{{1, 0, 0, 0}, {0, 1, 0, 0}, {0, 0, 1, 0}, {0, 0, 0, 1}};
    }

    private static double[][] translate(double x, double y, double z) {
        double[][] m = identity(); m[0][3] = x; m[1][3] = y; m[2][3] = z; return m;
    }

    private static double[][] scale(double x, double y, double z) {
        double[][] m = identity(); m[0][0] = x; m[1][1] = y; m[2][2] = z; return m;
    }

    private static double[][] rotate(int axis, double degrees) {
        double c = Math.cos(Math.toRadians(degrees)), s = Math.sin(Math.toRadians(degrees));
        double[][] m = identity();
        switch (axis) {
            case 0 -> { m[1][1] = c; m[1][2] = -s; m[2][1] = s; m[2][2] = c; }
            case 1 -> { m[0][0] = c; m[0][2] = s; m[2][0] = -s; m[2][2] = c; }
            default -> { m[0][0] = c; m[0][1] = -s; m[1][0] = s; m[1][1] = c; }
        }
        return m;
    }

    private static double[][] mul(double[][] a, double[][] b) {
        double[][] r = new double[4][4];
        for (int i = 0; i < 4; i++) for (int j = 0; j < 4; j++) for (int k = 0; k < 4; k++) r[i][j] += a[i][k] * b[k][j];
        return r;
    }

    private static double[] apply(double[][] m, double[] p) {
        double[] r = new double[3];
        for (int i = 0; i < 3; i++) r[i] = m[i][0] * p[0] + m[i][1] * p[1] + m[i][2] * p[2] + m[i][3];
        return r;
    }
}
