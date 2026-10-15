package com.siberanka.twilight.text;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The layered string survives Geyser's legacy-code normalisation byte for byte, and Bedrock's string
 * expressions ({@code '%.Ns' * t}, {@code t - prefix}) cut it back into the layers.
 */
class LayerEncodingTest {
    private static final int BLOCK = LayerEncoding.BLOCK_BYTES.get(TextLayoutTable.CHEST_LAYERS);

    @Test
    void blocksSurviveGeyserAndSplitBackIntoLayers() {
        // Bottom first, as LayeredTextLayout returns them; visible characters are a multiple of three bytes.
        List<String> layers = List.of("BG@f@e020@e001@lAbc", "@r@e@lGold@r@b@011f@e021", "@cXY@lZ");
        layers = layers.stream().map(LayerEncodingTest::codes).toList();
        String encoded = LayerEncoding.encode(layers, BLOCK, Integer.MAX_VALUE);
        assertNotNull(encoded);
        String sent = geyser(LayerEncoding.CLIENT_PREFIX + encoded);
        assertEquals(LayerEncoding.CLIENT_PREFIX + encoded, sent, "Geyser's normalisation leaves every byte in place");
        for (int block = 0; block < layers.size(); block++) {
            String text = block(sent, block, BLOCK);
            String expected = layers.get(layers.size() - 1 - block).replaceFirst("^" + RESET, "");
            assertTrue(text.contains(expected), "block " + block + " carries layer " + (layers.size() - 1 - block));
            if (block < layers.size() - 1) assertEquals(BLOCK, bytes(text));
            String tail = text.substring(text.indexOf(expected) + expected.length());
            assertTrue(tail.replace(SECTION + "o", "").replace(RESET, "").isEmpty(), tail);
        }
        assertEquals("", block(sent, layers.size(), BLOCK), "no further block");
    }

    @Test
    void bossBarNamesStayWithinBedrocksCharacterLimit() {
        int block = LayerEncoding.BLOCK_BYTES.get(TextLayoutTable.BOSS_LAYERS);
        String background = codes("@e020").repeat(20);
        String encoded = LayerEncoding.encode(List.of(background, "Abc"), block, LayerEncoding.BOSS_CHARACTERS);
        assertNotNull(encoded);
        assertTrue(encoded.startsWith("Abc"), "top layer first");
        assertTrue(encoded.endsWith(background), "the widest bottom layer comes last, unfilled");
        assertNull(LayerEncoding.encode(List.of(background, "x".repeat(93)), block, LayerEncoding.BOSS_CHARACTERS),
                "the top layer does not fit its block");
        String wide = codes("@e020").repeat(30);
        assertNull(LayerEncoding.encode(List.of(wide, wide, "Abc"), block, 100), "too many characters");
    }

    @Test
    void bossNamesHaveALargerFirstBlockForTextOverBackgrounds() {
        int first = LayerEncoding.FIRST_BLOCK_BYTES.get(TextLayoutTable.BOSS_LAYERS);
        int block = LayerEncoding.BLOCK_BYTES.get(TextLayoutTable.BOSS_LAYERS);
        assertTrue(first > block);
        // CustomNameplates' default boss bar: three backgrounds, three texts over them (measured 5 October 2026).
        String text = "Time: 0:00 PM" + codes("@f81a") + "Your Location: " + codes("@7") + "Unknown world" + codes("@r")
                + " (-104,200,0)" + codes("@f81a") + "Weather: Sunny ";
        String backgrounds = codes("@f70c@f81f@f81c@f711@f801@f70f@f70d").repeat(3);
        List<String> layers = List.of(backgrounds, codes("@f803@f702@f805@f60e@f70e@f808@f700"), codes("@f804@f738@f73a"), text);
        String lead = LayerEncoding.LAYERED + LayerEncoding.HIDDEN_BAR;
        String encoded = LayerEncoding.encode(layers, first, block, LayerEncoding.BOSS_CHARACTERS, lead);
        assertNotNull(encoded, "fits Bedrock's 256 characters");
        assertNull(LayerEncoding.encode(layers, block, block, LayerEncoding.BOSS_CHARACTERS, lead), "did not fit uniform blocks");
        String sent = geyser(LayerEncoding.CLIENT_PREFIX + encoded);
        assertEquals(LayerEncoding.CLIENT_PREFIX + encoded, sent);
        byte[] all = sent.getBytes(StandardCharsets.UTF_8);
        for (int index = 0; index < layers.size(); index++) {
            int start = LayerEncoding.blockStart(index, first, block);
            int size = index == 0 ? first : block;
            String cut = new String(Arrays.copyOfRange(all, start, Math.min(all.length, start + size)), StandardCharsets.UTF_8);
            assertTrue(cut.contains(layers.get(layers.size() - 1 - index)), "block " + index);
        }
    }

    @Test
    void layersThatDoNotFitTheirBlockAreRejected() {
        assertNull(LayerEncoding.encode(List.of("x", codes("@e020").repeat(300)), BLOCK, Integer.MAX_VALUE), "top block too long");
        assertEquals("abc", LayerEncoding.encode(List.of("abc"), BLOCK, Integer.MAX_VALUE), "a single (shifted) layer is sent as it is");
        assertNull(LayerEncoding.encode(List.of("c", "ab"), BLOCK, Integer.MAX_VALUE), "length not a multiple of three");
    }

    private static final String SECTION = "\u00a7";
    private static final String RESET = "\u00a7r";

    /** Test notation: '@x' is a section sign code, '@hhhh' a code point. */
    private static String codes(String notation) {
        StringBuilder out = new StringBuilder();
        for (int index = 0; index < notation.length(); index++) {
            char c = notation.charAt(index);
            if (c != '@') { out.append(c); continue; }
            String hex = notation.substring(index + 1, Math.min(notation.length(), index + 5));
            if (hex.matches("[0-9a-f]{4}") && Integer.parseInt(hex, 16) > 0xff) {
                out.appendCodePoint(Integer.parseInt(hex, 16));
                index += 4;
            } else {
                out.append(SECTION).append(notation.charAt(++index));
            }
        }
        return out.toString();
    }

    @Test
    void styledBossBarMarkersSurviveGeyserAndSelectExactlyOneColourAndOverlay() {
        String hidden = LayerEncoding.CLIENT_PREFIX + LayerEncoding.HIDDEN_BAR;
        String name = codes("@6Gold @lBoss");
        for (int colour = 0; colour < 7; colour++) {
            for (int overlay = 0; overlay < 5; overlay++) {
                String marker = LayerEncoding.styledBar(colour, overlay);
                String sent = geyser(LayerEncoding.CLIENT_PREFIX + marker + name);
                assertEquals(LayerEncoding.CLIENT_PREFIX + marker + name, sent, "Geyser leaves the marker in place");
                assertTrue(sent.startsWith(hidden), "the vanilla bar is hidden");
                for (int other = 0; other < 7; other++) {
                    String prefix = LayerEncoding.CLIENT_PREFIX + LayerEncoding.styledBarColour(other);
                    assertEquals(other == colour, block(sent, 0, bytes(prefix)).equals(prefix), colour + "/" + other);
                }
                for (int other = 0; other < 5; other++) {
                    String prefix = LayerEncoding.CLIENT_PREFIX + LayerEncoding.styledBar(colour, other);
                    assertEquals(other == overlay, block(sent, 0, bytes(prefix)).equals(prefix), overlay + "/" + other);
                }
            }
        }
        // A hidden bar whose name starts with a colour or a reset never selects a styled colour.
        String hiddenName = geyser(hidden + codes("@6Gold"));
        for (int colour = 0; colour < 7; colour++) {
            String prefix = LayerEncoding.CLIENT_PREFIX + LayerEncoding.styledBarColour(colour);
            assertNotEquals(prefix, block(hiddenName, 0, bytes(prefix)));
        }
    }

    @Test
    void paddingNeverHasTwoResetsInARow() {
        for (int count = 1; count < 9; count++) {
            String padding = LayerEncoding.padding(count);
            assertEquals(3 * count, bytes(padding));
            assertTrue(padding.endsWith("\u00a7r"));
            assertFalse(padding.contains("\u00a7r\u00a7r"), padding);
        }
    }

    /** Bedrock JSON UI: '%.Ns' * text takes N UTF-8 bytes; text - prefix removes the prefix. */
    private static String block(String text, int layer, int size) {
        byte[] all = text.getBytes(StandardCharsets.UTF_8);
        int start = Math.min(all.length, layer * size);
        int end = Math.min(all.length, start + size);
        return new String(Arrays.copyOfRange(all, start, end), StandardCharsets.UTF_8);
    }

    private static int bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8).length;
    }

    /** Port of Geyser 2.11.3 MessageTranslator's post-processing of a legacy string (leading reset included). */
    private static String geyser(String legacy) {
        String colors = "0123456789abcdefghijmnpqstuv", decorations = "klo";
        StringBuilder out = new StringBuilder();
        int applied = 0;
        boolean lastReset = false;
        for (int index = 0; index < legacy.length(); index++) {
            char c = legacy.charAt(index);
            if (c != '\u00a7' || index == legacy.length() - 1) {
                out.append(c);
                lastReset = false;
                continue;
            }
            char next = legacy.charAt(++index);
            if (lastReset && next == 'r') continue;
            if (!lastReset && colors.indexOf(next) != -1 && applied != 0) out.append("\u00a7r");
            out.append('\u00a7').append(next);
            lastReset = next == 'r';
            if (next == 'r') applied = 0;
            else if (colors.indexOf(next) != -1) applied = 1 << colors.indexOf(next);
            else if (decorations.indexOf(next) != -1) applied |= 1 << (colors.length() + decorations.indexOf(next));
        }
        String result = out.toString();
        if (result.endsWith("\u00a7")) result = result.substring(0, result.length() - 1);
        if (result.endsWith("\u00a7r")) result = result.substring(0, result.length() - 2);
        return result;
    }
}
