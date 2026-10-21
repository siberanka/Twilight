/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.geyser;

/**
 * Twilight wraps some of Geyser's Java packet translators, and several of its parts may wrap the same packet type
 * (text layout and rider names both handle entity data). Each wrapper hands the packet to the translator it
 * replaced. When Geyser reloads, every part installs itself again; a part that wrapped the current translator
 * without looking would wrap a chain it is already in, and two wrappers would hand packets to each other forever
 * (a stack overflow on every entity update). A wrapper that is already in the chain therefore stays where it is.
 */
public final class TranslatorChain {
    /** A Twilight translator that hands packets on to the translator it wraps. */
    public interface Link {
        /** The translator this one hands packets to (Object: tests load Twilight without Geyser's classes). */
        Object next();
    }

    private TranslatorChain() {}

    /** Whether {@code wrapper} is {@code current} or anywhere in the chain below it. */
    public static boolean installed(Object current, Object wrapper) {
        Object at = current;
        for (int depth = 0; at != null && depth < 64; depth++) {
            if (at == wrapper) return true;
            at = at instanceof Link link ? link.next() : null;
        }
        return false;
    }
}
