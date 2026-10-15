/* Copyright (c) 2026 siberanka. Twilight License (PolyForm Noncommercial 1.0.0 + terms), see LICENSE. */
package com.siberanka.twilight.integration.display;

/** Java display timing: only start-delay metadata resets the interpolation clock. */
final class DisplayTimeline {
    private DisplayPose from = DisplayPose.identity(), to = DisplayPose.identity();
    private long startNanos, durationNanos;
    private boolean initialized, started;

    Snapshot update(DisplayPose pose, Integer durationTicks, Integer delayTicks, long now) {
        DisplayPose previous = sample(now);
        if (delayTicks != null) {
            startNanos = now + Math.clamp(delayTicks, -72_000, 72_000) * 50_000_000L;
            started = true;
        }
        if (durationTicks != null) durationNanos = Math.clamp(durationTicks, 0, 72_000) * 50_000_000L;
        if (pose != null) {
            from = initialized && durationNanos > 0 ? previous : pose;
            to = pose;
            initialized = true;
        }
        float seconds = durationNanos / 1_000_000_000f;
        float delay = started ? (float) Math.clamp((startNanos - now) / 1_000_000_000d, -3600, 3600) : -seconds;
        return new Snapshot(from, to, seconds, delay);
    }

    DisplayPose sample(long now) {
        double progress = !started || durationNanos == 0 ? 1 : (now - startNanos) / (double) durationNanos;
        return from.interpolate(to, progress);
    }

    record Snapshot(DisplayPose from, DisplayPose to, float seconds, float delay) {}
}
