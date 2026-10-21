/* Copyright (c) 2026 siberanka. Twilight License (PolyForm Noncommercial 1.0.0 + terms), see LICENSE. */
package com.siberanka.twilight.integration.text;

import com.siberanka.twilight.text.TextLayoutTable;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TitleAnalysisTest {
    private static final TextLayoutTable TABLE = new TextLayoutTable(Map.of(TextLayoutTable.DEFAULT_FONT, Map.of(
            0x4E03, TextLayoutTable.Entry.advanceOnly(-1),
            0x1F03, new TextLayoutTable.Entry(0xF700, -1, 0, 176, 177))), 0xF800, 32);

    /** Plain titles, laid-out ones, unreplaced offset tags and characters the pack has no image for. */
    @Test
    void tellsHowBedrockGetsATitle() throws Exception {
        TitleAnalysis plain = TitleAnalysis.of(TABLE, Component.text("Market Menu"));
        assertTrue(plain.plain());
        assertEquals("Market Menu", plain.text());

        TitleAnalysis art = TitleAnalysis.of(TABLE, Component.text("\u4E03\u1F03"));
        assertFalse(art.plain());
        assertEquals(1, art.glyphs());
        assertEquals(1, art.moves());

        TitleAnalysis tagged = TitleAnalysis.of(TABLE, Component.text(":offset_-8:\u1F03 Shop"));
        assertEquals(1, tagged.tags());
        assertTrue(tagged.text().startsWith("<offset -8>"), tagged.text());

        TitleAnalysis missing = TitleAnalysis.of(TABLE, Component.text("\uE123 Shop"));
        assertEquals(List.of("U+E123"), missing.missing());
        assertFalse(missing.plain());
    }
}
