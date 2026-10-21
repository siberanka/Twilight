/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.text;

import java.util.ArrayList;
import java.util.List;

/**
 * Rewrites Java text so Bedrock draws every character where Java does.
 *
 * <p>The layout tracks {@code delta}, Bedrock's pen minus Java's pen. Ordinary
 * characters are assumed to have equal widths on both clients and need
 * {@code delta == 0}. A bitmap glyph needs {@code delta == left - 1}, because
 * Bedrock draws its first inked column one unit after the pen while Java draws
 * column zero at the pen. Advance-only characters (spaces, negative shifts) move
 * only Java's pen. Gaps are filled with invisible spacers. Bedrock cannot advance
 * by exactly one unit, so a one-unit gap after a glyph uses that glyph's wide
 * variant (one invisible trailing column). Remaining backwards or one-unit moves
 * are counted as approximations and left unchanged. Outside chest titles, ordinary
 * spaces stay spaces wherever their width is all that separates two characters, so
 * Bedrock still wraps chat at them.
 *
 * <p>Where a line starts depends on the surface, see {@link Mode}.
 */
public final class TextLayout {
    /** How the Bedrock line relates to the Java line. */
    public enum Mode {
        /**
         * A chest title whose Bedrock label starts {@link TextLayoutTable#containerOrigin()} units
         * left of Java's title origin, so leading negative shifts are reproduced too.
         */
        CONTAINER,
        /**
         * Left-aligned lines whose start differs between the clients anyway: chat, scoreboard
         * lines, team affixes and titles of containers Bedrock draws with its own layout. Each
         * line starts where its first character needs, so everything on it keeps Java's relative
         * positions; a line Java starts with a negative shift or a glyph at the pen sits that many
         * units further right on Bedrock.
         */
        LEFT,
        /**
         * Lines both clients centre by width: action bar, titles, boss bars and name tags. Each
         * line is padded with leading or trailing spacers so Bedrock centres it where Java does;
         * a leading negative shift therefore moves the line like on Java.
         */
        CENTERED
    }

    /**
     * Text in render order; {@code text == null} marks a component Twilight cannot measure.
     *
     * @param bold Java widens bold characters by one unit; such lines are not width-corrected
     * @param shadowless Java draws it without a shadow (a transparent shadow colour, as CustomNameplates sets
     *                   for backgrounds and icons)
     * @param shaded a container title's text without a colour: Java darkens its images (see
     *               {@link TextLayoutTable#shade}); legacy colour codes in the text end it, {@code \u00a7r} restores it
     */
    public record Segment(String text, String font, boolean bold, boolean shaded, boolean shadowless) {
        /** Offset tags a plugin left unreplaced count as the moves they name ({@link OffsetTags}). */
        public Segment {
            text = OffsetTags.encode(text);
        }

        public Segment(String text, String font) {
            this(text, font, false);
        }

        public Segment(String text, String font, boolean bold) {
            this(text, font, bold, false);
        }

        public Segment(String text, String font, boolean bold, boolean shaded) {
            this(text, font, bold, shaded, false);
        }

        public Segment withShadowless(boolean value) {
            return new Segment(text, font, bold, shaded, value);
        }
    }

    /**
     * @param texts replacement text per segment; for an opaque segment, the spacers that must precede it
     * @param suffix spacers that must follow the whole text (centred padding of its last line)
     */
    public record Result(List<String> texts, String suffix, int customCharacters, int approximations) {}

    private final TextLayoutTable table;
    private final Mode mode;
    private final List<StringBuilder> outputs = new ArrayList<>();
    private double delta;
    private int approximations;
    private int customCharacters;
    // The most recent output is a glyph that may still be widened by one unit.
    private StringBuilder lastGlyphOutput;
    private int lastGlyphIndex = -1;
    private int lastGlyphWide = -1;
    // Left and centred lines: Bedrock's line start floats until the first character that needs a position.
    private boolean floating;
    private double lineOffset;
    private StringBuilder lineOutput;
    private int lineIndex;
    // Java's width of the current line, while every character on it has a known advance.
    private double javaWidth;
    private boolean javaWidthKnown;
    // Default-font spaces whose four units have not been emitted yet (left and centred lines).
    private int pendingSpaces;

    private TextLayout(TextLayoutTable table, Mode mode) {
        this.table = table;
        this.mode = mode;
    }

    /** Lays out a chest title ({@link Mode#CONTAINER}). */
    public static Result layout(TextLayoutTable table, List<Segment> segments) {
        return layout(table, segments, Mode.CONTAINER);
    }

    /**
     * Longest text laid out (Java's own limits are far lower for player input) and widest move a
     * line may need. Anything beyond is refused, so crafted text cannot make the layout or its
     * spacer output grow without bound; callers then send the text unchanged.
     */
    public static final int MAX_INPUT_CHARS = 16_384;
    static final int MAX_ADVANCE = 1 << 20;

    static void checkInput(List<Segment> segments) {
        long length = 0;
        for (Segment segment : segments) if (segment.text() != null) length += segment.text().length();
        if (length > MAX_INPUT_CHARS) throw new IllegalArgumentException("text is longer than " + MAX_INPUT_CHARS + " characters");
    }

    public static Result layout(TextLayoutTable table, List<Segment> segments, Mode mode) {
        checkInput(segments);
        // Without Twilight's chest UI the title label is Bedrock's own: nothing to align it with.
        if (mode == Mode.CONTAINER && table.containerOrigin() == 0) mode = Mode.LEFT;
        TextLayout layout = new TextLayout(table, mode);
        StringBuilder last = null;
        for (Segment segment : segments) {
            StringBuilder out = new StringBuilder();
            layout.outputs.add(out);
            if (last == null) layout.startLine(out);
            last = out;
            if (segment.bold()) layout.javaWidthKnown = false;
            if (segment.text() == null) {
                layout.javaWidthKnown = false;
                layout.align(out, 0);
            } else {
                layout.text(out, segment.text(), segment.font(), segment.shaded());
            }
        }
        String suffix = last == null ? "" : layout.endLine(null);
        List<String> texts = layout.outputs.stream().map(StringBuilder::toString).toList();
        return new Result(texts, suffix, layout.customCharacters, layout.approximations);
    }

    /**
     * Glyph substitution without positioning, for Bedrock's touch (pocket) layout whose title
     * label is centred and unrelated to Java's geometry: visible glyphs use their Bedrock code
     * points and Java-only spacing characters are dropped.
     */
    public static Result substitute(TextLayoutTable table, List<Segment> segments) {
        List<String> texts = new ArrayList<>();
        int custom = 0;
        for (Segment segment : segments) {
            if (segment.text() == null) { texts.add(""); continue; }
            StringBuilder out = new StringBuilder();
            boolean shaded = segment.shaded();
            int[] codePoints = segment.text().codePoints().toArray();
            for (int index = 0; index < codePoints.length; index++) {
                int codePoint = codePoints[index];
                if (codePoint == '\u00a7' && index + 1 < codePoints.length) {
                    shaded = shading(codePoints[index + 1], shaded, segment.shaded());
                    out.appendCodePoint(codePoint).appendCodePoint(codePoints[++index]);
                    continue;
                }
                TextLayoutTable.Entry entry = table.lookup(segment.font(), codePoint);
                if (entry == null) out.appendCodePoint(codePoint);
                else {
                    custom++;
                    if (entry.visible()) {
                        int copy = shaded ? table.shade(entry.bedrock()) : -1;
                        out.appendCodePoint(copy >= 0 ? copy : entry.bedrock());
                    }
                }
            }
            texts.add(out.toString());
        }
        return new Result(texts, "", custom, 0);
    }

    /** Single default-font chest title convenience. */
    public static Result layout(TextLayoutTable table, String text) {
        return layout(table, List.of(new Segment(text, null)));
    }

    /** Single default-font text convenience; the suffix is appended. */
    public static String layout(TextLayoutTable table, String text, Mode mode) {
        Result result = layout(table, List.of(new Segment(text, null)), mode);
        return result.texts().getFirst() + result.suffix();
    }

    /** Whether text after the legacy code {@code code} is still uncoloured title text. */
    private static boolean shading(int code, boolean current, boolean initial) {
        if (code == 'r' || code == 'R') return initial;
        return Character.digit(code, 16) >= 0 ? false : current;
    }

    private void text(StringBuilder out, String content, String font, boolean initiallyShaded) {
        int[] codePoints = content.codePoints().toArray();
        boolean shaded = initiallyShaded;
        for (int index = 0; index < codePoints.length; index++) {
            int codePoint = codePoints[index];
            if (codePoint == '\u00a7' && index + 1 < codePoints.length) {
                // Legacy formatting codes (pack translations use them) take no space on either client.
                shaded = shading(codePoints[index + 1], shaded, initiallyShaded);
                out.appendCodePoint(codePoint).appendCodePoint(codePoints[++index]);
                continue;
            }
            if (codePoint == '\n' && mode != Mode.CONTAINER) {
                out.append(endLine(out)).append('\n');
                startLine(out);
                continue;
            }
            TextLayoutTable.Entry entry = table.lookup(font, codePoint);
            if (entry == null && codePoint == ' ' && (font == null || TextLayoutTable.DEFAULT_FONT.equals(font))) {
                entry = TextLayoutTable.Entry.advanceOnly(4); // Java's vanilla space provider
                if (mode != Mode.CONTAINER) pendingSpaces++;
            } else if (entry != null && codePoint == ' ' && !entry.visible() && entry.advance() == 4 && mode != Mode.CONTAINER) {
                pendingSpaces++; // a named font's own four-unit space (CustomNameplates' shift fonts) stays a space
            }
            if (entry == null) {
                align(out, 0);
                out.appendCodePoint(codePoint);
                forgetGlyph();
                float advance = table.textAdvance(font, codePoint);
                if (Float.isNaN(advance)) javaWidthKnown = false;
                else javaWidth += advance;
            } else if (!entry.visible()) {
                javaWidth += entry.advance();
                delta -= entry.advance();
                if (codePoint != ' ') customCharacters++;
            } else {
                customCharacters++;
                align(out, entry.left() - 1);
                lastGlyphOutput = out;
                lastGlyphIndex = out.length();
                int copy = shaded ? table.shade(entry.bedrock()) : -1;
                if (copy >= 0) {
                    // The widened glyph must be darkened too; without a darkened variant it stays narrow.
                    lastGlyphWide = entry.wide() < 0 ? -1 : table.shade(entry.wide());
                    out.appendCodePoint(copy);
                } else {
                    lastGlyphWide = entry.wide();
                    out.appendCodePoint(entry.bedrock());
                }
                javaWidth += entry.advance();
                delta += entry.width() + 1 - entry.advance();
            }
        }
    }

    private void startLine(StringBuilder out) {
        forgetGlyph();
        pendingSpaces = 0;
        delta = mode == Mode.CONTAINER ? -table.containerOrigin() : 0;
        floating = mode != Mode.CONTAINER;
        lineOffset = 0;
        lineOutput = out;
        lineIndex = out.length();
        javaWidth = 0;
        javaWidthKnown = true;
    }

    /**
     * Pads a centred line. With Bedrock's content starting {@code offset} units after Java's
     * line start and ending {@code delta} units after Java's line end, {@code lead - trail}
     * must equal {@code offset + delta} for both clients to centre the line alike.
     *
     * @return the trailing spacers; leading spacers are inserted at the line start
     */
    private String endLine(StringBuilder out) {
        forgetGlyph();
        pendingSpaces = 0;
        if (mode != Mode.CENTERED || floating) return "";
        int balance = (int) Math.round(lineOffset + delta);
        // Java centres at -width / 2 in integer arithmetic (truncated towards zero) while Bedrock centres
        // exactly, so an odd Java width places Java's line half a unit towards its far end.
        if (javaWidthKnown && javaWidth == Math.rint(javaWidth) && Math.abs(javaWidth) % 2 == 1) {
            balance += javaWidth > 0 ? 1 : -1;
        }
        int lead = Math.max(balance, 0), trail = Math.max(-balance, 0);
        // No spacer advances by one unit; balance it against a larger spacer on the other side.
        if (lead == 1) { lead = 3; trail = 2; }
        if (trail == 1) { trail = 3; lead = 2; }
        lineOutput.insert(lineIndex, spacers(lead));
        return spacers(trail);
    }

    private void forgetGlyph() {
        lastGlyphOutput = null;
        lastGlyphWide = -1;
    }

    private void align(StringBuilder out, double required) {
        int spaces = pendingSpaces;
        pendingSpaces = 0;
        if (floating) {
            // Bedrock's pen is wherever the line needs it; the padding moves the line there.
            floating = false;
            if (spaces > 0 && required == 0 && required - delta == 4 * spaces) {
                out.repeat(' ', spaces); // leading spaces count on both clients alike
                lineOffset = 0;
            } else {
                lineOffset = required - delta;
            }
            delta = required;
            return;
        }
        int gap = (int) Math.round(required - delta);
        if (gap < 0 && spaces > 0) {
            // Bedrock is already past Java's pen (text Java draws back over an image, one label only): the
            // position is approximate anyway, but words keep their spaces.
            approximations++;
            forgetGlyph();
            delta += 4 * spaces;
            out.repeat(' ', spaces);
            return;
        }
        // Pending spaces stay literal when the rest of the gap can still be reproduced.
        int literal = spaces > 0 && gap >= 4 * spaces && reproducible(gap - 4 * spaces) ? spaces : 0;
        int rest = gap - 4 * literal;
        if (rest == 1 && widenable()) {
            int length = Character.charCount(lastGlyphOutput.codePointAt(lastGlyphIndex));
            lastGlyphOutput.replace(lastGlyphIndex, lastGlyphIndex + length, Character.toString(lastGlyphWide));
            delta += 1;
            rest = 0;
        }
        if (rest == 0 && literal == 0) return;
        forgetGlyph();
        if (rest != 0 && rest < table.minimumSpacer()) {
            approximations++;
            return;
        }
        delta += rest + 4 * literal;
        out.append(spacers(rest)).repeat(' ', literal);
    }

    private boolean reproducible(int advance) {
        return advance == 0 || advance >= table.minimumSpacer() || advance == 1 && widenable();
    }

    private boolean widenable() {
        return lastGlyphOutput != null && lastGlyphWide >= 0;
    }

    /** Invisible spacers advancing exactly {@code advance} units (0 or at least the minimum spacer). */
    private String spacers(int advance) {
        StringBuilder out = new StringBuilder();
        if (advance <= 0) return "";
        if (advance > MAX_ADVANCE) throw new IllegalArgumentException("move wider than " + MAX_ADVANCE + " units");
        int maximum = table.maximumSpacer();
        while (advance > maximum) {
            int step = advance - maximum >= table.minimumSpacer() ? maximum : maximum - table.minimumSpacer();
            out.appendCodePoint(table.spacer(step));
            advance -= step;
        }
        return out.appendCodePoint(table.spacer(advance)).toString();
    }
}
