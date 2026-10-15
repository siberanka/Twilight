package com.siberanka.twilight.text;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Left-aligned and centred text surfaces (chat, action bar, titles, boss bars, name tags)
 * against independent Java and Bedrock rendering models.
 */
class TextSurfaceLayoutTest {
    private static final int H_WIDTH = 6;
    private static final int SPACER_FIRST = 0xF800;
    // -8 / +5 / +300 shifts, an 8-wide icon and a 16-cell image with three transparent columns.
    private static final TextLayoutTable TABLE = new TextLayoutTable(Map.of(TextLayoutTable.DEFAULT_FONT, Map.of(
            0xE001, TextLayoutTable.Entry.advanceOnly(-8),
            0xE002, TextLayoutTable.Entry.advanceOnly(5),
            0xE003, TextLayoutTable.Entry.advanceOnly(300),
            0xE004, TextLayoutTable.Entry.advanceOnly(-1),
            0xE010, new TextLayoutTable.Entry(0xE010, 0xF010, 0, 8, 9),
            0xE011, new TextLayoutTable.Entry(0xE011, 0xF011, 3, 10, 14))),
            SPACER_FIRST, 32, TextLayoutTable.ORIGIN, letters());

    /** Every ordinary character of these tests is modelled with Java's 'H' width. */
    private static Map<Integer, Float> letters() {
        Map<Integer, Float> advances = new HashMap<>();
        for (int codePoint = '!'; codePoint <= '~'; codePoint++) advances.put(codePoint, (float) H_WIDTH);
        return advances;
    }

    @Test
    void centredLinesLandWhereJavaCentresThem() {
        for (String text : List.of("\uE010", "\uE001\uE010", "\uE001\uE001\uE001\uE010", "\uE010\uE001",
                "\uE003\uE010", "\uE001H", "H\uE002H", "\uE010 Shop", "\uE011\uE011H", "\uE002\uE011\uE001",
                "\uE010\uE002\uE011\uE002\uE010", "\uE004\uE010", "\uE001\uE001\uE001\uE001H",
                "Rank \uE011 Name")) {
            assertExact(text, TextLayout.Mode.CENTERED);
        }
    }

    @Test
    void centredLinesFollowJavasIntegerRoundingForOddWidths() {
        // Java centres at -width / 2 in integer arithmetic; Bedrock centres exactly (measured: a 39-unit
        // CustomNameplates bar sat half a unit left). Odd widths, positive and negative, must match.
        for (String text : List.of("\uE010\uE010\uE010", "H \uE010", "\uE001\uE001\uE001\uE001\uE001\uE010",
                "\uE004\uE004\uE004", "Shop \uE011")) {
            assertExact(text, TextLayout.Mode.CENTERED);
        }
    }

    @Test
    void everyCentredBalanceIsReachableWithoutAOneUnitSpacer() {
        for (int shift = -400; shift <= 400; shift++) {
            TextLayoutTable table = new TextLayoutTable(Map.of(TextLayoutTable.DEFAULT_FONT, Map.of(
                    0xE001, TextLayoutTable.Entry.advanceOnly(shift),
                    0xE010, new TextLayoutTable.Entry(0xE010, -1, 0, 8, 9))), SPACER_FIRST, 32);
            assertExact(table, "\uE001\uE010", TextLayout.Mode.CENTERED);
            assertExact(table, "\uE010\uE001", TextLayout.Mode.CENTERED);
        }
    }

    @Test
    void centredLinesArePaddedIndependently() {
        assertExact("\uE001\uE010\nH\uE002H\n\uE003\uE011", TextLayout.Mode.CENTERED);
        assertExact("Top\n\n\uE001\uE001\uE010", TextLayout.Mode.CENTERED);
    }

    @Test
    void leftAlignedLinesKeepJavaRelativePositions() {
        // Lines start where their first character needs: a leading glyph or negative shift (which Java
        // draws at or left of the line start) moves the whole line, never one character against another.
        for (String text : List.of("\uE011H", "H \uE010", "H\uE002H", "\uE002\uE011\uE010", "\uE010",
                "\uE001\uE010\uE002H", "\uE010\uE010", "\uE002\uE010\uE002\uE010\nH \uE011", "\uE010\n\uE001\uE011")) {
            assertExact(text, TextLayout.Mode.LEFT);
        }
        // A glyph directly after ordinary text still needs a backwards move: reported, not hidden.
        assertEquals(1, TextLayout.layout(TABLE, List.of(new TextLayout.Segment("H\uE010", null)),
                TextLayout.Mode.LEFT).approximations());
    }

    @Test
    void textWithoutCustomCharactersIsUnchanged() {
        for (TextLayout.Mode mode : List.of(TextLayout.Mode.LEFT, TextLayout.Mode.CENTERED)) {
            TextLayout.Result result = TextLayout.layout(TABLE,
                    List.of(new TextLayout.Segment("Hello ", null), new TextLayout.Segment(null, null),
                            new TextLayout.Segment(" world\nline", null)), mode);
            assertEquals("Hello  world\nline", String.join("", result.texts()), "spaces stay literal");
            assertEquals("", result.suffix());
            assertEquals(0, result.customCharacters());
        }
    }

    @Test
    void spacesBeforeGlyphsBecomeSpacersOnlyWhereJavaNeedsIt() {
        String laidOut = TextLayout.layout(TABLE, "Buy now \uE010 for 5", TextLayout.Mode.LEFT);
        assertTrue(laidOut.startsWith("Buy now"), laidOut);
        assertTrue(laidOut.endsWith(" for 5"), laidOut);
        assertExact("Buy now \uE010 for 5", TextLayout.Mode.LEFT);
        assertExact("  \uE010  x", TextLayout.Mode.CENTERED);
    }

    @Test
    void namedFontSpacesStaySpacesEvenWhenBedrockIsAhead() {
        // CustomNameplates' shift fonts define their own four-unit space; text drawn back over an image keeps it.
        TextLayoutTable table = new TextLayoutTable(Map.of(
                TextLayoutTable.DEFAULT_FONT, TABLE.fonts().get(TextLayoutTable.DEFAULT_FONT),
                "demo:shift", Map.of((int) ' ', TextLayoutTable.Entry.advanceOnly(4))),
                SPACER_FIRST, 32, TextLayoutTable.ORIGIN, letters());
        TextLayout.Result plain = TextLayout.layout(table, List.of(new TextLayout.Segment("Ab Cd", "demo:shift")),
                TextLayout.Mode.LEFT);
        assertEquals("Ab Cd", String.join("", plain.texts()));
        TextLayout.Result back = TextLayout.layout(table, List.of(new TextLayout.Segment("", null),
                new TextLayout.Segment("Ab Cd", "demo:shift")), TextLayout.Mode.LEFT);
        assertTrue(String.join("", back.texts()).endsWith("Ab Cd"), "words keep their space");
        assertTrue(back.approximations() > 0, "the backwards move is still reported");
    }

    @Test
    void legacyFormattingCodesTakeNoSpace() {
        // Pack translations carry legacy codes ("\u00a7f" before an image); both clients draw them with no advance.
        String laidOut = TextLayout.layout(TABLE, "\u00a7f\uE002\uE011\u00a7cH", TextLayout.Mode.LEFT);
        assertTrue(laidOut.startsWith("\u00a7f"), laidOut);
        assertTrue(laidOut.contains("\u00a7cH"), laidOut);
        assertEquals(TextLayout.layout(TABLE, "\uE002\uE011H", TextLayout.Mode.LEFT),
                laidOut.replace("\u00a7f", "").replace("\u00a7c", ""));
    }

    @Test
    void chestTitlesWithoutTheContainerOriginUseTheLeftLayout() {
        TextLayoutTable plain = TABLE.withContainerOrigin(0);
        assertEquals(0, plain.containerOrigin());
        String text = "\uE002\uE011\uE010";
        assertEquals(TextLayout.layout(TABLE, text, TextLayout.Mode.LEFT), TextLayout.layout(plain, text).texts().getFirst());
        assertEquals(0, TextLayoutTable.fromJson(plain.toJson()).containerOrigin());
        assertEquals(TextLayoutTable.ORIGIN, TextLayoutTable.fromJson(TABLE.toJson()).containerOrigin());
    }

    // --- rendering models ---------------------------------------------------------------------

    private static void assertExact(String text, TextLayout.Mode mode) {
        assertExact(TABLE, text, mode);
    }

    private static void assertExact(TextLayoutTable table, String text, TextLayout.Mode mode) {
        TextLayout.Result result = TextLayout.layout(table, List.of(new TextLayout.Segment(text, null)), mode);
        assertEquals(0, result.approximations(), escape(text));
        String bedrock = result.texts().getFirst() + result.suffix();
        String[] javaLines = text.split("\n", -1), bedrockLines = bedrock.split("\n", -1);
        assertEquals(javaLines.length, bedrockLines.length);
        for (int line = 0; line < javaLines.length; line++) {
            List<double[]> java = java(table, javaLines[line], mode);
            List<double[]> actual = bedrock(table, bedrockLines[line], mode);
            assertEquals(java.size(), actual.size(), escape(text));
            for (int index = 0; index < java.size(); index++) {
                assertArrayEquals(java.get(index), actual.get(index), 1e-9,
                        escape(text) + " line " + line + " ink " + index + " (" + mode + ")");
            }
        }
    }

    /** Java: column zero at the pen; a centred line starts at -width / 2 (integer division). */
    private static List<double[]> java(TextLayoutTable table, String line, TextLayout.Mode mode) {
        List<double[]> ink = new ArrayList<>();
        double pen = 0;
        for (int codePoint : line.codePoints().toArray()) {
            TextLayoutTable.Entry entry = table.lookup(null, codePoint);
            if (entry == null && codePoint == ' ') { pen += 4; continue; }
            if (entry == null) { ink.add(new double[]{pen, codePoint}); pen += H_WIDTH; continue; }
            if (entry.visible()) ink.add(new double[]{pen + entry.left(), entry.bedrock()});
            pen += entry.advance();
        }
        // Java: x = -width / 2 with integer division (truncated towards zero).
        return shift(ink, mode == TextLayout.Mode.CENTERED ? (double) (-(long) Math.ceil(pen) / 2) : firstInk(ink));
    }

    /** Bedrock: glyph ink one unit after the pen, advance = width + 1; spacers advance by their size. */
    private static List<double[]> bedrock(TextLayoutTable table, String line, TextLayout.Mode mode) {
        Map<Integer, TextLayoutTable.Entry> glyphs = new HashMap<>();
        table.fonts().values().forEach(entries -> entries.values().forEach(entry -> {
            if (!entry.visible()) return;
            glyphs.put(entry.bedrock(), entry);
            if (entry.wide() >= 0) glyphs.put(entry.wide(), new TextLayoutTable.Entry(entry.bedrock(), -1,
                    entry.left(), entry.width() + 1, entry.advance()));
        }));
        List<double[]> ink = new ArrayList<>();
        double pen = 0;
        for (int codePoint : line.codePoints().toArray()) {
            if (codePoint >= SPACER_FIRST && codePoint <= table.spacer(table.maximumSpacer())) {
                pen += codePoint - SPACER_FIRST + table.minimumSpacer();
                continue;
            }
            TextLayoutTable.Entry glyph = glyphs.get(codePoint);
            if (glyph != null) { ink.add(new double[]{pen + 1, glyph.bedrock()}); pen += glyph.width() + 1; continue; }
            if (codePoint == ' ') { pen += 4; continue; }
            ink.add(new double[]{pen, codePoint});
            pen += H_WIDTH;
        }
        return shift(ink, mode == TextLayout.Mode.CENTERED ? -pen / 2 : firstInk(ink));
    }

    /** Left-aligned lines are compared relative to their first ink: only relative positions are defined. */
    private static double firstInk(List<double[]> ink) {
        return ink.isEmpty() ? 0 : -ink.getFirst()[0];
    }

    private static List<double[]> shift(List<double[]> ink, double offset) {
        ink.forEach(point -> point[0] += offset);
        return ink;
    }

    private static String escape(String value) {
        StringBuilder out = new StringBuilder();
        value.codePoints().forEach(cp -> out.append(cp < 128 && cp != '\n' ? Character.toString(cp)
                : "\\u%04X".formatted(cp)));
        return out.toString();
    }
}
