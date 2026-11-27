/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.conflicts;

import java.util.Locale;

/** Who renders one kind of Bedrock content when Twilight and another tool both could. */
public enum Owner {
    /** Twilight yields to a specialised renderer that is installed, and renders itself otherwise. */
    AUTO,
    /** Twilight always renders; the other tool should be removed. */
    TWILIGHT,
    /** Twilight never renders this kind. */
    OTHER;

    /** Parses a setting; anything else is an error naming the option. */
    public static Owner parse(Object value, String option) {
        String text = value == null ? "auto" : String.valueOf(value).strip().toLowerCase(Locale.ROOT);
        return switch (text) {
            case "auto" -> AUTO;
            case "twilight" -> TWILIGHT;
            case "other" -> OTHER;
            default -> throw new IllegalArgumentException(option + " must be auto, twilight or other");
        };
    }
}
