package com.siberanka.twilight.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TwilightConfigTest {
    private static TwilightConfig config(String nametagBackground) {
        return new TwilightConfig(false, true, true, true, 40, 100, 1_073_741_824L, 100_000, true, true, List.of(),
                "auto", true, true, 3, true, true, true, true, true, true, true, nametagBackground,
                java.util.Map.of("itemsadder", "generated", "oraxen", "off"), true, false);
    }

    @Test
    void defaultsNeedNoSetup() {
        TwilightConfig shortForm = new TwilightConfig(false, true, true, true, 40, 100, 1_073_741_824L, 100_000, true,
                true, List.of(), "auto", true, true, 3);
        assertEquals("auto", shortForm.nametagBackground());
        assertTrue(shortForm.bedrockBiomeMatching());
        assertTrue(shortForm.javaTextLayers());
    }

    @Test
    void rejectsUnknownNametagBackgrounds() {
        for (String value : TwilightConfig.NAMETAG_BACKGROUNDS) assertEquals(value, config(value).nametagBackground());
        assertThrows(IllegalArgumentException.class, () -> config("sometimes"));
    }

    @Test
    void providerSourcesDefaultToAutomatic() {
        TwilightConfig config = config("auto");
        assertEquals("generated", config.sourceMode("itemsadder"));
        assertEquals("off", config.sourceMode("oraxen"));
        assertEquals("auto", config.sourceMode("nexo"));
        assertFalse(config.sendPackToBedrock());
        assertThrows(IllegalArgumentException.class, () -> new TwilightConfig(false, true, true, true, 40, 100,
                1_073_741_824L, 100_000, true, true, java.util.List.of(), "auto", true, true, 3, true, true, true, true,
                true, true, true, "auto", java.util.Map.of("nexo", "sometimes"), true, true));
    }

    @Test
    void withStrictChangesOnlyStrictness() {
        TwilightConfig strict = config("hidden");
        TwilightConfig relaxed = strict.withStrict(false);
        assertFalse(relaxed.strict());
        assertEquals(strict, relaxed.withStrict(true));
    }
}
