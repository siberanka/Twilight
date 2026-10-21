package com.siberanka.twilight.proxy;

import com.siberanka.twilight.integration.display.DisplayKeys;
import com.siberanka.twilight.protocol.PackChannel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class DisplaySightingsTest {
    private static final PackChannel.Key KEY = PackChannel.Key.derive("a-shared-secret-for-tests");

    @Test void reportsEachValidKeyOncePerServerSignedAndKeepsWhatCouldNotBeSent() {
        DisplaySightings sightings = new DisplaySightings();
        sightings.seen("Survival", List.of("model=craftengine:chair", "minecraft:paper|cmd=10001", "not a key", "model=craftengine:chair"));
        sightings.seen("survival", List.of("model=craftengine:chair"));
        assertEquals(2, sightings.pending());

        sightings.flush(List.of(KEY), (server, message) -> false);
        assertEquals(2, sightings.pending(), "nobody on the server: kept for the next attempt");

        Map<String, List<String>> sent = new ConcurrentHashMap<>();
        sightings.flush(List.of(KEY), (server, message) -> {
            PackChannel.Displayed displayed = assertInstanceOf(PackChannel.Displayed.class,
                    PackChannel.read(message, List.of(KEY), System.currentTimeMillis()));
            sent.put(server, displayed.keys());
            return true;
        });
        assertEquals(List.of("survival"), List.copyOf(sent.keySet()));
        assertEquals(2, sent.get("survival").size());
        assertEquals(0, sightings.pending());
        sightings.seen("survival", List.of("model=craftengine:chair"));
        assertEquals(0, sightings.pending(), "reported once per run");
    }

    @Test void largeBatchesAreSplitAndNothingIsSentWithoutASecret() {
        DisplaySightings sightings = new DisplaySightings();
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < 150; i++) keys.add("minecraft:paper|cmd=" + i);
        sightings.seen("lobby", keys);
        sightings.flush(List.of(), (server, message) -> fail("no secret, no message"));
        int[] messages = {0};
        while (sightings.pending() > 0) {
            sightings.flush(List.of(KEY), (server, message) -> {
                assertTrue(message.length <= PackChannel.MAX_MESSAGE);
                messages[0]++;
                return true;
            });
        }
        assertEquals(3, messages[0]);
    }

    @Test void displayedMessagesRejectTamperingAndBadKeys() {
        long now = System.currentTimeMillis();
        byte[] message = PackChannel.displayed(KEY, now, List.of("model=a:b", "minecraft:paper|str=lamp"));
        assertEquals(List.of("model=a:b", "minecraft:paper|str=lamp"),
                ((PackChannel.Displayed) PackChannel.read(message, List.of(KEY), now)).keys());
        byte[] tampered = message.clone();
        tampered[14] ^= 1;
        assertNull(PackChannel.read(tampered, List.of(KEY), now));
        assertNull(PackChannel.read(message, List.of(KEY), now + PackChannel.MAX_AGE_MILLIS + 60_000));
        assertThrows(IllegalArgumentException.class, () -> PackChannel.displayed(KEY, now, List.of("has space")));
        assertThrows(IllegalArgumentException.class, () -> PackChannel.displayed(KEY, now, List.of()));
        assertTrue(DisplayKeys.valid("minecraft:paper|cmd=-5"));
        assertFalse(DisplayKeys.valid("minecraft:paper|cmd=5|extra"));
        assertFalse(DisplayKeys.valid("model=UPPER:case"));
    }
}
