/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.geyser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Two Twilight parts wrapping the same Geyser translator, then Geyser reloading: neither may wrap the chain it is
 * already in (that made them hand entity data to each other until the stack overflowed).
 */
class TranslatorChainTest {
    static final class Wrapper implements TranslatorChain.Link {
        Object original;

        @Override public Object next() {
            return original;
        }
    }

    @Test void aWrapperAlreadyInTheChainIsNotInstalledAgain() {
        Object geyser = new Object();
        Wrapper text = new Wrapper(), riders = new Wrapper();
        // Text layout wraps Geyser's translator, then rider names wrap the text layout.
        assertFalse(TranslatorChain.installed(geyser, text));
        text.original = geyser;
        assertFalse(TranslatorChain.installed(text, riders));
        riders.original = text;
        // Geyser reloads: both install again on the current chain (riders -> text -> geyser).
        assertTrue(TranslatorChain.installed(riders, text), "text layout is already below rider names");
        assertTrue(TranslatorChain.installed(riders, riders));
        // A Geyser that rebuilt its translators gets wrapped again.
        assertFalse(TranslatorChain.installed(new Object(), text));
    }

    @Test void stopsOnLoops() {
        Wrapper a = new Wrapper(), b = new Wrapper();
        a.original = b;
        b.original = a;
        assertFalse(TranslatorChain.installed(a, new Wrapper()), "a broken chain ends the walk");
    }
}
