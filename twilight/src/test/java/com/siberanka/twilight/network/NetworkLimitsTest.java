/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.network;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NetworkLimitsTest {
    @TempDir Path root;

    @Test void settingsAcceptAutoNumbersAndRejectNonsense() {
        var settings = NetworkSettings.parse(Map.<String, Object>of("view-angle", "200", "threads", 2,
                "behind-distance", "auto", "cull-players", "false")::get);
        assertEquals(200, settings.viewAngle());
        assertEquals(2, settings.threads());
        assertEquals(NetworkSettings.AUTO, settings.behindDistance());
        assertFalse(settings.cullPlayers());
        assertEquals(NetworkSettings.DEFAULTS, NetworkSettings.parse(key -> null));
        assertThrows(IllegalArgumentException.class, () -> NetworkSettings.parse(Map.<String, Object>of("view-angle", "45")::get));
        assertThrows(IllegalArgumentException.class, () -> NetworkSettings.parse(Map.<String, Object>of("threads", "-3")::get));
        assertThrows(IllegalArgumentException.class, () -> NetworkSettings.parse(Map.<String, Object>of("wall-thickness", 1.5)::get));
        assertThrows(IllegalArgumentException.class, () -> NetworkSettings.parse(Map.<String, Object>of("culling", "maybe")::get));
        assertThrows(IllegalArgumentException.class, () -> NetworkSettings.parse(Map.<String, Object>of("packets-per-second", "99999999999")::get));
    }

    @Test void limitsFollowPingAndStrain() {
        var good = ConnectionQuality.Limits.of(NetworkSettings.DEFAULTS, 20, 0);
        var far = ConnectionQuality.Limits.of(NetworkSettings.DEFAULTS, 300, 0);
        var struggling = ConnectionQuality.Limits.of(NetworkSettings.DEFAULTS, 20, 1);
        assertTrue(far.behindDistance() > good.behindDistance(), "a high ping reveals earlier");
        assertTrue(far.halfAngleCos() < good.halfAngleCos(), "and with a wider view");
        assertTrue(far.wallDistance() > good.wallDistance());
        assertTrue(struggling.packetsPerSecond() < good.packetsPerSecond(), "a struggling client gets fewer updates");
        assertTrue(struggling.hiddenIntervalNanos() > good.hiddenIntervalNanos());
        assertEquals(3, good.wallThickness());
        assertTrue(good.behindDistance() >= 10 && far.behindDistance() <= 24);
        var fixed = ConnectionQuality.Limits.of(new NetworkSettings(true, 1, true, true, 360, 5, true, 20, 4, 64, 500, 1000, true), 900, 1);
        assertEquals(5, fixed.behindDistance());
        assertEquals(-1, fixed.halfAngleCos(), 1e-9, "360 degrees: everything is in view");
        assertEquals(1000, fixed.packetsPerSecond());
    }

    @Test void qualityTightensAtOnceAndRelaxesSlowly() {
        ConnectionQuality quality = new ConnectionQuality();
        long now = 0;
        for (int i = 0; i < 60; i++) {
            quality.input(now);
            now += 50_000_000L;
        }
        quality.update(30, true, now);
        assertEquals(0, quality.strain(), 1e-9);
        quality.update(500, true, now);
        assertEquals(1, quality.strain(), 1e-9, "worse at once");
        quality.update(30, true, now);
        assertTrue(quality.strain() > 0.8, "better slowly");
        for (int i = 0; i < 100; i++) quality.update(30, true, now);
        assertTrue(quality.strain() < 0.01);
        quality.update(30, false, now);
        assertTrue(quality.strain() >= 0.8, "a full send buffer is strain");
        // A device that sends ten inputs a second instead of twenty is struggling.
        ConnectionQuality slow = new ConnectionQuality();
        long t = 0;
        for (int i = 0; i < 60; i++) {
            slow.input(t);
            t += 100_000_000L;
        }
        slow.update(30, true, t);
        assertTrue(slow.strain() > 0.6, "slow inputs: " + slow.strain());
    }

    @Test void threadsFollowTheServer() throws Exception {
        assertEquals(2, Threads.network(NetworkSettings.AUTO, 4));
        assertEquals(4, Threads.network(NetworkSettings.AUTO, 16));
        assertEquals(1, Threads.network(NetworkSettings.AUTO, 1));
        assertEquals(6, Threads.network(6, 4), "a configured count wins");
        assertTrue(Threads.network(NetworkSettings.AUTO, 0) >= 1);
        Path spigot = root.resolve("spigot.yml");
        Files.writeString(spigot, "settings:\n  debug: false\n  netty-threads: 8\n  bungeecord: false\n");
        assertEquals(8, Threads.nettyThreads(spigot));
        Files.writeString(spigot, "settings:\n  netty-threads: 0\n");
        assertEquals(0, Threads.nettyThreads(spigot));
        assertEquals(0, Threads.nettyThreads(root.resolve("missing.yml")));
    }
}
