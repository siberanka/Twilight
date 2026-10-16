/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.network;

/**
 * Whether walls hide an entity from a player. An entity counts as hidden only when every one of several lines of
 * sight (its feet, middle, top and both sides) crosses at least {@code thickness} solid, opaque blocks: an entity
 * behind a single block, a fence, glass, leaves or a doorway stays visible. Unknown blocks (unloaded chunks) count
 * as open, so a doubt always keeps an entity visible.
 *
 * <p>The block lookups of one test are limited by a budget; a test that runs out answers "visible".
 */
public final class Occlusion {
    /** Opaque, full blocks at a position (for example from Geyser's world view). */
    @FunctionalInterface
    public interface Blocks {
        boolean opaque(int x, int y, int z);
    }

    /** Block lookups left for a test. */
    public static final class Budget {
        private int left;

        public Budget(int lookups) {
            left = lookups;
        }

        boolean take() {
            return --left >= 0;
        }

        public boolean spent() {
            return left < 0;
        }
    }

    private Occlusion() {}

    /**
     * Whether an entity of {@code width} and {@code height} standing at {@code x, y, z} is hidden from the eye.
     */
    public static boolean hidden(double eyeX, double eyeY, double eyeZ, double x, double y, double z, double width,
                                 double height, int thickness, Blocks blocks, Budget budget) {
        if (!finite(eyeX, eyeY, eyeZ) || !finite(x, y, z)) return false;
        double dx = x - eyeX, dz = z - eyeZ;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        // Sideways, across the line of sight.
        double sideX = horizontal < 1e-6 ? 1 : -dz / horizontal, sideZ = horizontal < 1e-6 ? 0 : dx / horizontal;
        double half = Math.max(0.2, width / 2);
        double top = Math.max(0.2, height);
        double[][] targets = {
                {x, y + 0.1, z},
                {x, y + top * 0.5, z},
                {x, y + top - 0.05, z},
                {x + sideX * half, y + top * 0.5, z + sideZ * half},
                {x - sideX * half, y + top * 0.5, z - sideZ * half},
        };
        for (double[] target : targets) {
            if (!blocked(eyeX, eyeY, eyeZ, target[0], target[1], target[2], thickness, blocks, budget)) return false;
            if (budget.spent()) return false;
        }
        return true;
    }

    /**
     * Whether the line from the eye to the target crosses at least {@code thickness} opaque blocks, not counting
     * the block of the eye itself (a player standing in a block) or the last half block before the target.
     */
    static boolean blocked(double ax, double ay, double az, double bx, double by, double bz, int thickness,
                           Blocks blocks, Budget budget) {
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.5) return false;
        dx /= length;
        dy /= length;
        dz /= length;
        int x = floor(ax), y = floor(ay), z = floor(az);
        int stepX = dx > 0 ? 1 : dx < 0 ? -1 : 0, stepY = dy > 0 ? 1 : dy < 0 ? -1 : 0, stepZ = dz > 0 ? 1 : dz < 0 ? -1 : 0;
        double nextX = stepX == 0 ? Double.MAX_VALUE : ((stepX > 0 ? x + 1 - ax : ax - x) / Math.abs(dx));
        double nextY = stepY == 0 ? Double.MAX_VALUE : ((stepY > 0 ? y + 1 - ay : ay - y) / Math.abs(dy));
        double nextZ = stepZ == 0 ? Double.MAX_VALUE : ((stepZ > 0 ? z + 1 - az : az - z) / Math.abs(dz));
        double deltaX = stepX == 0 ? Double.MAX_VALUE : 1 / Math.abs(dx);
        double deltaY = stepY == 0 ? Double.MAX_VALUE : 1 / Math.abs(dy);
        double deltaZ = stepZ == 0 ? Double.MAX_VALUE : 1 / Math.abs(dz);
        double end = length - 0.5;
        int solid = 0;
        // Each step enters the next block along the line.
        for (int steps = 0; steps < 1024; steps++) {
            double travelled;
            if (nextX <= nextY && nextX <= nextZ) {
                travelled = nextX;
                x += stepX;
                nextX += deltaX;
            } else if (nextY <= nextZ) {
                travelled = nextY;
                y += stepY;
                nextY += deltaY;
            } else {
                travelled = nextZ;
                z += stepZ;
                nextZ += deltaZ;
            }
            if (travelled >= end) return false;
            if (!budget.take()) return false;
            if (blocks.opaque(x, y, z) && ++solid >= thickness) return true;
        }
        return false;
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    private static boolean finite(double x, double y, double z) {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                && Math.abs(x) < 3.0e7 && Math.abs(y) < 1.0e5 && Math.abs(z) < 3.0e7;
    }
}
