/* Copyright (c) 2026 siberanka. Twilight License (PolyForm Noncommercial 1.0.0 + terms), see LICENSE. */
package com.siberanka.twilight.integration.display;

/**
 * Java's ItemFrameRenderer pose of an invisible frame's item, as an item display transformation (translation,
 * scale, left and right rotation) relative to the frame entity: move by the facing times 0.46875, turn to face that
 * way, move half a block out, turn by eighths, halve; the right half turn undoes the item display's own.
 */
final class FramePose {
    private FramePose() {}

    /**
     * @param step  the facing direction's unit vector (x, y, z)
     * @param yRot  Java's {@code Direction.toYRot()} for horizontal facings
     * @param turns the frame's item rotation in eighths of a turn
     */
    static float[] pose(int[] step, float yRot, int turns) {
        boolean horizontal = step[1] == 0;
        float xRot = horizontal ? 0 : -90 * step[1];
        float yTurn = horizontal ? 180 - yRot : 180;
        double[] frame = multiply(axis(1, 0, 0, xRot), axis(0, 1, 0, yTurn));
        double[] out = rotate(frame, 0, 0, 0.5);
        double[] left = multiply(frame, axis(0, 0, 1, Math.floorMod(turns, 8) * 45f));
        return new float[]{
                (float) (step[0] * 0.46875 + out[0]), (float) (step[1] * 0.46875 + out[1]), (float) (step[2] * 0.46875 + out[2]),
                0.5f, 0.5f, 0.5f,
                (float) left[0], (float) left[1], (float) left[2], (float) left[3],
                0f, 1f, 0f, 0f};
    }

    /** A rotation quaternion (x, y, z, w) about a unit axis by degrees. */
    private static double[] axis(double x, double y, double z, double degrees) {
        double half = Math.toRadians(degrees) / 2, sin = Math.sin(half);
        return new double[]{x * sin, y * sin, z * sin, Math.cos(half)};
    }

    private static double[] multiply(double[] a, double[] b) {
        return new double[]{
                a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1],
                a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0],
                a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3],
                a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2]};
    }

    private static double[] rotate(double[] q, double x, double y, double z) {
        double[] result = multiply(multiply(q, new double[]{x, y, z, 0}), new double[]{-q[0], -q[1], -q[2], q[3]});
        return new double[]{result[0], result[1], result[2]};
    }
}
