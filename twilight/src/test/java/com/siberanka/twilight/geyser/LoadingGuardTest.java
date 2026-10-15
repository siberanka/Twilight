package com.siberanka.twilight.geyser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LoadingGuardTest {
    /**
     * Without forward-player-ping, Geyser's protocol library answers keep-alives and Geyser answers pings itself.
     * A second answer from the guard made Paper drop every Bedrock player on a server with Geyser-Spigot
     * ("keepalive response without matching challenge"), so the guard answers only for forwarding sessions.
     */
    @Test
    void answersOnlyWhenGeyserForwardsPingsToTheClient() {
        assertFalse(LoadingGuard.answers(false, 0, 1_000, 300_000), "Geyser already answers");
        assertTrue(LoadingGuard.answers(true, 0, 1_000, 300_000));
    }

    @Test
    void stopsAnsweringAfterTheLimit() {
        assertTrue(LoadingGuard.answers(true, 0, 300_000, 300_000));
        assertFalse(LoadingGuard.answers(true, 0, 300_001, 300_000));
        assertFalse(LoadingGuard.answers(true, 0, 1_000, 0), "0 only logs loading times");
    }
}
