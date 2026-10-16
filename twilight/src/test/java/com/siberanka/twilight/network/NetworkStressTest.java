/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.network;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A crowded scene for one player: 1200 mobs within 100 blocks moving every tick for 20 seconds while the player
 * turns around, behind a few thick walls. Measures how many entity updates the client is spared and what the
 * layer costs per packet, and checks that the client ends with the server's state.
 */
class NetworkStressTest {
    @Test void aCrowdedSceneIsLighterAndStaysExact() {
        SessionNetworkTest.Rig rig = new SessionNetworkTest.Rig();
        // Walls: four 4-block thick slabs around the player at 30 blocks.
        rig.blocks = (x, y, z) -> y >= 55 && y <= 90 && (Math.abs(x) >= 30 && Math.abs(x) < 34 && Math.abs(z) < 20
                || Math.abs(z) >= 30 && Math.abs(z) < 34 && Math.abs(x) < 20);
        Random random = new Random(7);
        int count = 1200;
        float[][] positions = new float[count][];
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2, distance = 4 + random.nextDouble() * 96;
            positions[i] = new float[]{(float) (Math.cos(angle) * distance), 65, (float) (Math.sin(angle) * distance)};
            rig.spawn(100 + i, positions[i][0], positions[i][1], positions[i][2]);
        }
        rig.look(0, 65.62f, 0, 0, 0);
        rig.ticks(20);
        int before = rig.received;
        long sentBefore = rig.sent;
        long started = System.nanoTime();
        int ticks = 20 * 20;
        for (int tick = 0; tick < ticks; tick++) {
            // A slow turn: one full circle in 20 seconds.
            rig.look(0, 65.62f, 0, 0, tick * 360f / ticks);
            for (int i = 0; i < count; i++) {
                positions[i][0] += (random.nextFloat() - 0.5f) * 0.3f;
                positions[i][2] += (random.nextFloat() - 0.5f) * 0.3f;
                rig.move(100 + i, positions[i][0], 65, positions[i][2], random.nextFloat() * 360);
            }
            rig.tick();
        }
        long elapsed = System.nanoTime() - started;
        long offered = rig.sent - sentBefore;
        long delivered = rig.received - before;
        double spared = 100.0 * (offered - delivered) / offered;
        double perPacket = (double) elapsed / offered;
        System.out.printf(java.util.Locale.ROOT, "stress: %d entity updates offered, %d delivered (%.1f%% spared), %.0f ns per"
                + " packet including the test pipeline%n", offered, delivered, spared, perPacket);
        assertTrue(spared > 40, "a crowded scene is much lighter: " + spared);
        // Whatever was held, everything is exact once it is in view.
        rig.settings = new NetworkSettings(true, NetworkSettings.AUTO, false, true, NetworkSettings.AUTO, 128, false,
                NetworkSettings.AUTO, NetworkSettings.AUTO, NetworkSettings.AUTO, NetworkSettings.AUTO, 100_000, true);
        rig.ticks(5);
        rig.assertConsistent();
        assertTrue(rig.failures.isEmpty(), rig.failures.toString());
    }
}
