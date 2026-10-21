/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.network;

/**
 * How well one Bedrock player keeps up: the network round trip, how regularly the client answers (a Bedrock client
 * sends its input every tick, 20 times a second; a device that cannot keep up sends fewer) and whether the
 * connection's send buffer is full. The result is a strain from 0 (keeps up easily) to 1 (struggling), smoothed so
 * one late packet does not change the limits; the limits in {@link Limits} follow it.
 *
 * <p>Confined to the player's network thread.
 */
public final class ConnectionQuality {
    /** Round trip below which the network is not the limit, and above which it is the whole limit. */
    static final int GOOD_PING = 60, BAD_PING = 400;
    /** Client inputs per second at which the device keeps up, and below which it is struggling. */
    static final double GOOD_INPUTS = 18, BAD_INPUTS = 8;
    private static final long INPUT_WINDOW_NANOS = 2_000_000_000L;

    private double strain;
    private boolean measured;
    private int ping;
    private int inputs;
    private long windowStart = Long.MIN_VALUE;
    private double inputRate = -1;

    /** Counts one client input (PlayerAuthInput) at {@code now}. */
    public void input(long now) {
        if (windowStart == Long.MIN_VALUE) windowStart = now;
        inputs++;
        long elapsed = now - windowStart;
        if (elapsed >= INPUT_WINDOW_NANOS) {
            inputRate = inputs * 1e9 / elapsed;
            inputs = 0;
            windowStart = now;
        }
    }

    /** Updates the strain from the round trip in milliseconds and whether the send buffer accepts more. */
    public void update(int pingMillis, boolean writable, long now) {
        ping = Math.max(0, Math.min(pingMillis, 60_000));
        // Inputs stopped arriving altogether (a menu, loading, a frozen client): the last rate stays, the window
        // tells after it ends.
        if (windowStart != Long.MIN_VALUE && now - windowStart > 2 * INPUT_WINDOW_NANOS) inputRate = 0;
        double pingStrain = clamp((ping - GOOD_PING) / (double) (BAD_PING - GOOD_PING));
        double inputStrain = inputRate < 0 ? 0 : clamp((GOOD_INPUTS - inputRate) / (GOOD_INPUTS - BAD_INPUTS));
        double target = Math.max(pingStrain, inputStrain);
        if (!writable) target = Math.max(target, 0.8);
        // Worse at once, better slowly: limits tighten quickly and relax over a few seconds.
        strain = !measured ? target : target > strain ? target : strain + (target - strain) * 0.1;
        measured = true;
    }

    public double strain() {
        return strain;
    }

    public int ping() {
        return ping;
    }

    /** Client inputs per second, or -1 before the first measurement. */
    public double inputRate() {
        return inputRate;
    }

    static double clamp(double value) {
        return value < 0 ? 0 : value > 1 ? 1 : value;
    }

    /**
     * The limits for one moment of one connection: the configured values, or for {@code auto} values that follow
     * the round trip (a later reveal needs more margin) and the strain (a struggling client gets less).
     */
    public record Limits(double halfAngleCos, double behindDistance, double playerBehindDistance, double wallDistance,
                         double playerWallDistance, int wallThickness, double farDistance, long farIntervalNanos,
                         long hiddenIntervalNanos, int packetsPerSecond) {

        public static Limits of(NetworkSettings settings, int ping, double strain) {
            double margin = Math.min(ping, 1000);
            double angle = settings.viewAngle() != NetworkSettings.AUTO ? settings.viewAngle()
                    : Math.min(280, 220 + margin / 10);
            double behind = settings.behindDistance() != NetworkSettings.AUTO ? settings.behindDistance()
                    : Math.min(24, 10 + margin / 40);
            double wall = settings.wallDistance() != NetworkSettings.AUTO ? settings.wallDistance()
                    : Math.min(32, 16 + margin / 30);
            int thickness = settings.wallThickness() != NetworkSettings.AUTO ? settings.wallThickness() : 3;
            double far = settings.farDistance() != NetworkSettings.AUTO ? settings.farDistance() : 40;
            long farInterval = (long) ((100 + 150 * strain) * 1_000_000L);
            long hidden = settings.hiddenUpdateMs() != NetworkSettings.AUTO ? settings.hiddenUpdateMs() * 1_000_000L
                    : (long) ((1000 + 2000 * strain) * 1_000_000L);
            int perSecond = settings.packetsPerSecond() != NetworkSettings.AUTO ? settings.packetsPerSecond()
                    : (int) Math.round(2400 * (1 - 0.75 * strain));
            double halfAngle = Math.toRadians(Math.min(angle, 360) / 2);
            return new Limits(Math.cos(halfAngle), behind, behind * 1.5, wall, wall * 1.5, thickness, far,
                    farInterval, hidden, perSecond);
        }
    }
}
