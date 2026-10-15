package com.siberanka.twilight.text;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lines that move back over earlier characters, laid out as Bedrock layers, against independent Java and
 * Bedrock rendering models: every character must land on its Java pixel in some layer, and characters Java
 * draws over others must be in the same or a higher layer.
 */
class LayeredTextLayoutTest {
    private static final int H_WIDTH = 6;
    private static final int SPACER_FIRST = 0xF800;
    // A 40-wide background, a 10-wide icon, a 64-wide banner, negative shifts as CustomNameplates and
    // ItemsAdder use them, and a shaded copy of the background.
    private static final TextLayoutTable TABLE = new TextLayoutTable(Map.of(TextLayoutTable.DEFAULT_FONT, Map.of(
            0xE001, TextLayoutTable.Entry.advanceOnly(-41),
            0xE002, TextLayoutTable.Entry.advanceOnly(-8),
            0xE003, TextLayoutTable.Entry.advanceOnly(-100),
            0xE004, TextLayoutTable.Entry.advanceOnly(3),
            0xE005, TextLayoutTable.Entry.advanceOnly(25),
            0xE020, new TextLayoutTable.Entry(0xE020, 0xF020, 0, 40, 41),
            0xE021, new TextLayoutTable.Entry(0xE021, 0xF021, 1, 9, 11),
            0xE022, new TextLayoutTable.Entry(0xE022, -1, 0, 64, 65))),
            SPACER_FIRST, 32, TextLayoutTable.ORIGIN, letters(), Map.of(0xE020, 0xE120));

    private static Map<Integer, Float> letters() {
        Map<Integer, Float> advances = new HashMap<>();
        for (int codePoint = '!'; codePoint <= '~'; codePoint++) advances.put(codePoint, (float) H_WIDTH);
        advances.put((int) '\u011f', (float) H_WIDTH); // a two-byte character
        return advances;
    }

    @Test
    void textOnABackgroundBecomesTwoLayersAtJavaPositions() {
        // CustomNameplates' background text: background, back over it, icon and text inside.
        for (TextLayout.Mode mode : List.of(TextLayout.Mode.CENTERED, TextLayout.Mode.CONTAINER)) {
            String line = "\ue020\ue001 \ue021Hi";
            LayeredTextLayout.Result result = layout(line, mode);
            assertNotNull(result, mode.name());
            assertEquals(2, result.count(), mode.name());
            assertExact(line, result, mode);
        }
    }

    @Test
    void fourAndEightUnitMovesUseSpacesToSaveBossBarCharacters() {
        // Words on a background: their spaces stay one-byte spaces instead of three-byte spacers.
        String line = " Ab Cd  Ef";
        LayeredTextLayout.Result result = layout(line, TextLayout.Mode.CENTERED);
        assertEquals(2, result.count());
        assertExact(line, result, TextLayout.Mode.CENTERED);
        String top = text(result.layers().get(1));
        assertTrue(top.contains("Ab Cd"), "no spacer between the words: " + top);
        // Whatever the widths, every layer stays a multiple of three bytes (the block padding needs it).
        for (String other : List.of(" A", " Ab", " A b", "  Ab ",
                "  Ab", " x y  z Cd")) {
            for (TextLayout.Mode mode : List.of(TextLayout.Mode.CENTERED, TextLayout.Mode.CONTAINER)) {
                LayeredTextLayout.Result layered = layout(other, mode);
                if (layered != null) assertExact(other, layered, mode);
            }
        }
        for (int letters = 0; letters <= 6; letters++) {
            for (int spaces = 0; spaces <= 3; spaces++) {
                String other = "" + " ".repeat(spaces) + "x".repeat(letters);
                LayeredTextLayout.Result layered = layout(other, TextLayout.Mode.CENTERED);
                if (layered != null) assertExact(other, layered, TextLayout.Mode.CENTERED);
            }
        }
    }

    @Test
    void severalBackgroundsShareTwoLayers() {
        // A CustomNameplates boss bar: three backgrounds, each with text drawn back over it, then a shift past it.
        String line = "\ue020\ue001 Ab \ue005\ue020\ue001 Cd \ue005\ue020\ue001 \u011fE";
        for (TextLayout.Mode mode : List.of(TextLayout.Mode.CENTERED, TextLayout.Mode.CONTAINER)) {
            LayeredTextLayout.Result result = layout(line, mode);
            assertEquals(2, result.count(), "later backgrounds go back to the bottom layer");
            assertExact(line, result, mode);
        }
    }

    @Test
    void overlappingImagesKeepJavasDrawingOrder() {
        // A banner over a panel, text over the banner, then an icon over everything.
        String line = "\ue002\ue022\ue003\ue020\ue001 Shop\ue002\ue002\ue002\ue021\ue005\ue005\ue005\ue005";
        for (TextLayout.Mode mode : List.of(TextLayout.Mode.CENTERED, TextLayout.Mode.CONTAINER)) {
            LayeredTextLayout.Result result = layout(line, mode);
            assertNotNull(result, mode.name());
            assertTrue(result.count() >= 2 && result.count() <= LayeredTextLayout.MAX_LAYERS);
            assertExact(line, result, mode);
        }
    }

    @Test
    void linesWithoutBackwardMovesStayInOneLabel() {
        assertNull(layout("\ue020\ue004\ue021 Hi", TextLayout.Mode.CENTERED));
        assertNull(layout("\ue002\ue002\ue020", TextLayout.Mode.CONTAINER), "a leading shift is within the origin");
        assertNull(LayeredTextLayout.layout(TABLE, List.of(new TextLayout.Segment("\ue020\ue001Hi", null)),
                TextLayout.Mode.LEFT), "chat has no layer labels");
        assertNull(LayeredTextLayout.layout(TABLE, List.of(new TextLayout.Segment("\ue020\ue001Hi", null),
                new TextLayout.Segment(null, null)), TextLayout.Mode.CENTERED), "opaque components cannot be measured");
    }

    @Test
    void layersCarryCodesStylesAndShadedCopies() {
        List<TextLayout.Segment> segments = List.of(new TextLayout.Segment("\ue020\ue001", null, false, true),
                new TextLayout.Segment(" \u00a7eGold", null, true, false));
        LayeredTextLayout.Result result = LayeredTextLayout.layout(TABLE, segments, TextLayout.Mode.CONTAINER);
        assertNotNull(result);
        String bottom = text(result.layers().getFirst());
        assertTrue(bottom.contains("\ue120"), "uncoloured title background uses its darkened copy");
        LayeredTextLayout.Run gold = result.layers().get(1).stream().filter(run -> run.segment() == 1).findFirst().orElseThrow();
        assertTrue(gold.text().startsWith("\u00a7eGold"), gold.text());
        for (List<LayeredTextLayout.Run> layer : result.layers()) {
            for (LayeredTextLayout.Run run : layer) {
                if (run.text().codePoints().anyMatch(TABLE::isSpacer)) assertNotEquals(1, run.segment(), "no spacer in bold text");
            }
        }
    }

    @Test
    void shiftedJavaFontSheetsGetTheirOwnLowerLayer() {
        // CustomNameplates draws background text in a font made of Java's own sheets with ascent 3 (4 units lower).
        TextLayoutTable table = TABLE.withNativeFonts(Map.of("nameplates:shift_1", 4));
        List<TextLayout.Segment> segments = List.of(new TextLayout.Segment("\ue020\ue001 \ue021", null),
                new TextLayout.Segment("Hi", "nameplates:shift_1"));
        LayeredTextLayout.Result result = LayeredTextLayout.layout(table, segments, TextLayout.Mode.CENTERED, 2);
        assertNotNull(result);
        assertEquals(List.of(0, 0, 8), result.lines(), "background, icon over it, text 4 units (8 half-lines) lower");
        assertTrue(text(result.layers().get(2)).contains("Hi"));
        assertFalse(text(result.layers().get(2)).contains("\ue021"), "the icon is not shifted");
        // Shifted text alone, without any move back, still needs its own (lower) label.
        LayeredTextLayout.Result alone = LayeredTextLayout.layout(table,
                List.of(new TextLayout.Segment("Hi", "nameplates:shift_1")), TextLayout.Mode.CONTAINER);
        assertNotNull(alone);
        assertEquals(List.of(4), alone.lines());
        int bytes = 4 + text(alone.layers().getFirst()).getBytes(StandardCharsets.UTF_8).length;
        assertEquals(0, bytes % 3, "newlines count towards the three-byte rule");
    }

    // --- rendering models ---------------------------------------------------------------------

    private static LayeredTextLayout.Result layout(String line, TextLayout.Mode mode) {
        return LayeredTextLayout.layout(TABLE, List.of(new TextLayout.Segment(line, null)), mode);
    }

    private static String text(List<LayeredTextLayout.Run> runs) {
        StringBuilder out = new StringBuilder();
        runs.forEach(run -> out.append(run.text()));
        return out.toString();
    }

    /** Ink: x, character and draw order (Java sequence or Bedrock layer * 10000 + sequence). */
    private static void assertExact(String line, LayeredTextLayout.Result result, TextLayout.Mode mode) {
        List<double[]> java = new ArrayList<>();
        double pen = 0;
        int order = 0;
        for (int codePoint : line.codePoints().toArray()) {
            TextLayoutTable.Entry entry = TABLE.lookup(null, codePoint);
            if (entry == null && codePoint == ' ') { pen += 4; continue; }
            if (entry == null) { java.add(new double[]{pen, codePoint, order++, H_WIDTH}); pen += H_WIDTH; continue; }
            if (entry.visible()) java.add(new double[]{pen + entry.left(), entry.bedrock(), order++, entry.width()});
            pen += entry.advance();
        }
        double javaStart = mode == TextLayout.Mode.CENTERED ? -Math.floor(pen / 2) : 0;
        java.forEach(ink -> ink[0] += javaStart);

        Map<Integer, Integer> wide = new HashMap<>();
        Map<Integer, TextLayoutTable.Entry> glyphs = new HashMap<>();
        TABLE.fonts().values().forEach(entries -> entries.values().forEach(entry -> {
            if (!entry.visible()) return;
            glyphs.put(entry.bedrock(), entry);
            if (entry.wide() >= 0) { glyphs.put(entry.wide(), entry); wide.put(entry.wide(), 1); }
        }));
        List<double[]> bedrock = new ArrayList<>();
        double width = -1;
        for (int layer = 0; layer < result.count(); layer++) {
            String text = text(result.layers().get(layer));
            assertEquals(0, text.getBytes(StandardCharsets.UTF_8).length % 3, "layer bytes multiple of three");
            List<double[]> ink = new ArrayList<>();
            double bedrockPen = 0;
            int sequence = 0;
            int[] codePoints = text.codePoints().toArray();
            for (int index = 0; index < codePoints.length; index++) {
                int codePoint = codePoints[index];
                if (codePoint == '\u00a7') { index++; continue; }
                if (TABLE.isSpacer(codePoint)) { bedrockPen += codePoint - SPACER_FIRST + TABLE.minimumSpacer(); continue; }
                if (codePoint == ' ') { bedrockPen += 4; continue; }
                int shadedOriginal = codePoint == 0xE120 ? 0xE020 : codePoint;
                TextLayoutTable.Entry glyph = glyphs.get(shadedOriginal);
                if (glyph != null) {
                    ink.add(new double[]{bedrockPen + 1, glyph.bedrock(), layer * 10000 + sequence++, glyph.width()});
                    bedrockPen += glyph.width() + 1 + wide.getOrDefault(codePoint, 0);
                    continue;
                }
                ink.add(new double[]{bedrockPen, codePoint, layer * 10000 + sequence++, H_WIDTH});
                bedrockPen += H_WIDTH;
            }
            if (mode == TextLayout.Mode.CENTERED) {
                if (width < 0) width = bedrockPen;
                assertEquals(width, bedrockPen, "every centred layer has the same width");
            }
            double start = mode == TextLayout.Mode.CENTERED ? -bedrockPen / 2 : -TextLayoutTable.ORIGIN;
            ink.forEach(point -> point[0] += start);
            bedrock.addAll(ink);
        }
        assertEquals(java.size(), bedrock.size(), "every character drawn once");
        // Match each Java character to the Bedrock character at the same position.
        List<double[]> remaining = new ArrayList<>(bedrock);
        double[] layerOf = new double[java.size()];
        for (int index = 0; index < java.size(); index++) {
            double[] ink = java.get(index);
            double[] match = remaining.stream().filter(point -> point[1] == ink[1] && Math.abs(point[0] - ink[0]) < 1e-9)
                    .findFirst().orElseThrow(() -> new AssertionError("no Bedrock ink at Java x " + ink[0] + " for "
                            + Integer.toHexString((int) ink[1]) + " in " + mode));
            remaining.remove(match);
            layerOf[index] = match[2];
        }
        // Wherever Java draws a later character over an earlier one, Bedrock must draw it later too.
        for (int earlier = 0; earlier < java.size(); earlier++) {
            for (int later = earlier + 1; later < java.size(); later++) {
                double[] a = java.get(earlier), b = java.get(later);
                boolean overlap = a[0] < b[0] + b[3] && b[0] < a[0] + a[3];
                if (overlap) assertTrue(layerOf[earlier] < layerOf[later], "drawing order of " + earlier + " and " + later);
            }
        }
    }
}
