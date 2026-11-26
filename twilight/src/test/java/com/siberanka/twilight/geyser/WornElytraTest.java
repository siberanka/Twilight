/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.geyser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which chest item a Bedrock viewer gets for an elytra worn by another entity. */
class WornElytraTest {
    @Test void aCustomGliderOfAnotherEntityShowsItsCustomItem() {
        assertTrue(WornElytra.showsCustomItem(false, true, "twilight:default_flame_elytra_1dbd223499fa"));
    }

    @Test void aVanillaElytraIsLeftAlone() {
        assertFalse(WornElytra.showsCustomItem(false, true, "minecraft:elytra"));
    }

    @Test void theViewersOwnPlayerKeepsTheVanillaElytraToGlide() {
        assertFalse(WornElytra.showsCustomItem(true, true, "twilight:default_flame_elytra_1dbd223499fa"));
    }

    @Test void anUnmappedOrNonGliderStackIsLeftAlone() {
        assertFalse(WornElytra.showsCustomItem(false, true, null));
        assertFalse(WornElytra.showsCustomItem(false, false, "twilight:default_flame_elytra_1dbd223499fa"));
        assertFalse(WornElytra.showsCustomItem(false, true, "geyser_custom:something"));
    }
}
