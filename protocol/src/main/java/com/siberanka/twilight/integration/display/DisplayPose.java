/* Copyright (c) 2026 siberanka. Twilight License (PolyForm Noncommercial 1.0.0 + terms), see LICENSE. */
package com.siberanka.twilight.integration.display;

import java.util.Arrays;

/** Immutable Java display transformation: translation, scale, left and right quaternion. */
public final class DisplayPose {
    public static final int SIZE = 14;
    private final float[] values;

    public DisplayPose(float[] values) {
        if (values.length != SIZE) throw new IllegalArgumentException("Expected 14 transformation components");
        this.values = values.clone();
        for (float value : values) if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite display pose");
        normalize(this.values, 6);
        normalize(this.values, 10);
    }

    public static DisplayPose identity() {
        return new DisplayPose(new float[]{0, 0, 0, 1, 1, 1, 0, 0, 0, 1, 0, 0, 0, 1});
    }

    public float value(int index) { return values[index]; }
    public float[] values() { return values.clone(); }

    public DisplayPose interpolate(DisplayPose target, double progress) {
        if (!Double.isFinite(progress)) throw new IllegalArgumentException("Non-finite interpolation progress");
        double t = Math.clamp(progress, 0, 1);
        float[] result = new float[SIZE];
        for (int i = 0; i < 6; i++) result[i] = (float) (values[i] + (target.values[i] - values[i]) * t);
        for (int offset : new int[]{6, 10}) {
            double dot = 0;
            for (int i = offset; i < offset + 4; i++) dot += values[i] * (double) target.values[i];
            double sign = dot < 0 ? -1 : 1;
            dot = Math.min(1, Math.abs(dot));
            double a = 1 - t, b = t;
            if (dot <= 0.9995) {
                double angle = Math.acos(dot), denominator = Math.sin(angle);
                a = Math.sin((1 - t) * angle) / denominator;
                b = Math.sin(t * angle) / denominator;
            }
            for (int i = offset; i < offset + 4; i++) result[i] = (float) (a * values[i] + b * sign * target.values[i]);
        }
        return new DisplayPose(result);
    }

    private static void normalize(float[] values, int offset) {
        double squared = 0;
        for (int i = offset; i < offset + 4; i++) squared += (double) values[i] * values[i];
        if (squared < 1e-20) throw new IllegalArgumentException("Zero display quaternion");
        double length = Math.sqrt(squared);
        for (int i = offset; i < offset + 4; i++) values[i] /= (float) length;
    }

    @Override public boolean equals(Object other) { return other instanceof DisplayPose pose && Arrays.equals(values, pose.values); }
    @Override public int hashCode() { return Arrays.hashCode(values); }
}
