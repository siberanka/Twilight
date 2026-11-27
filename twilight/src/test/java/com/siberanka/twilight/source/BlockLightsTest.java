package com.siberanka.twilight.source;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class BlockLightsTest {
    /** Providers print states in their own order and with or without the namespace; the compiler keys by Java's order. */
    @Test
    void statesAreNormalizedLikeJava() {
        assertEquals("minecraft:note_block[instrument=hat,note=1,powered=false]",
                BlockLights.normalize("note_block[powered=false,note=1,instrument=hat]"));
        assertEquals("minecraft:cactus[age=3]", BlockLights.normalize(" minecraft:cactus[age=3] "));
        assertEquals("minecraft:stone", BlockLights.normalize("stone"));
        assertNull(BlockLights.normalize("note_block[powered"));
        assertNull(BlockLights.normalize("note_block[a=b"));
        assertNull(BlockLights.normalize(null));
    }
}
