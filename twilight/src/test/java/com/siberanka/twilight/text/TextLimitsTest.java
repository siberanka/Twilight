package com.siberanka.twilight.text;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Crafted text cannot make the layout or its spacer output grow without bound. */
class TextLimitsTest {
    @Test
    void refusesTextLongerThanTheLimit() {
        TextLayout.checkInput(List.of(new TextLayout.Segment("x".repeat(TextLayout.MAX_INPUT_CHARS), null)));
        List<TextLayout.Segment> tooLong = List.of(new TextLayout.Segment("x".repeat(TextLayout.MAX_INPUT_CHARS / 2), null),
                new TextLayout.Segment("y".repeat(TextLayout.MAX_INPUT_CHARS / 2 + 1), null));
        assertThrows(IllegalArgumentException.class, () -> TextLayout.checkInput(tooLong));
        // Segments without text (formatting only) count as empty.
        TextLayout.checkInput(List.of(new TextLayout.Segment(null, null)));
    }
}
