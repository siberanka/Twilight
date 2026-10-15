/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.text;

import java.util.ArrayList;
import java.util.List;

/**
 * Lays out a line whose Java rendering moves back over earlier characters (a negative space
 * after an image: text on a background, a banner over a panel) as several Bedrock layers.
 *
 * <p>A Bedrock label only moves right, so {@link TextLayout} has to approximate such moves.
 * Bedrock surfaces whose UI Twilight generates (chest titles, the action bar, titles and
 * boss bars) instead draw one label per layer at the same origin, see {@link LayerEncoding}.
 * Every character keeps its Java position: it goes to the lowest layer whose pen can still
 * reach it without covering a character Java draws on top of it, so Java's drawing order is
 * kept wherever characters overlap. Spacers share the run of the character they lead to, except
 * before bold text, where they are unstyled (Bedrock widens bold spacers).
 *
 * <p>Centred layers all get the same Bedrock width, so Bedrock centres every label where Java
 * centres the line. Every layer's UTF-8 length is made a multiple of three (formatting codes
 * and private-use glyphs already are), so blocks can be padded with formatting codes alone.
 */
public final class LayeredTextLayout {
    /** Upper bound of labels per surface in the generated UI. */
    public static final int MAX_LAYERS = 4;

    /**
     * @param segment index of the source segment whose style applies, or -1 for unstyled spacers
     */
    public record Run(int segment, String text) {}

    /**
     * Runs per layer, bottom layer first.
     *
     * @param lines newlines that start each layer: text Java draws lower (fonts made of its own sheets with a
     *              smaller ascent, such as CustomNameplates' shifted text) sits that many label lines lower
     */
    public record Result(List<List<Run>> layers, List<Integer> lines, List<Boolean> shadowless) {
        public int count() { return layers.size(); }
    }

    private record Item(int segment, String text, double javaX, double inkStart, double inkEnd, double required,
                        double bedrockAdvance, int wide, boolean code, int bytes, boolean bold, int shift,
                        boolean shadowless) {}

    private final TextLayoutTable table;
    private final int linesPerUnit;
    private final boolean shadows;
    private final List<Item> items = new ArrayList<>();
    private final List<StringBuilder> codes = new ArrayList<>();
    private double javaWidth;

    private LayeredTextLayout(TextLayoutTable table, int linesPerUnit, boolean shadows) {
        this.table = table;
        this.linesPerUnit = linesPerUnit;
        this.shadows = shadows;
    }

    /**
     * @return the layers, or null when the line needs no second layer or cannot be layered
     *         (opaque components, several lines, characters of unknown width, bold images)
     */
    public static Result layout(TextLayoutTable table, List<TextLayout.Segment> segments, TextLayout.Mode mode) {
        return layout(table, segments, mode, 1);
    }

    /**
     * @param linesPerUnit label lines per unit of vertical shift (the generated labels use one-unit lines;
     *                     a vertically centred label needs two, half of its growth moves it up)
     */
    public static Result layout(TextLayoutTable table, List<TextLayout.Segment> segments, TextLayout.Mode mode,
                                int linesPerUnit) {
        return layout(table, segments, mode, linesPerUnit, false);
    }

    /**
     * @param shadows the surface's top label draws shadows and the others do not (action bar, boss bars):
     *                shadowless text gets its own layers below the top one
     */
    public static Result layout(TextLayoutTable table, List<TextLayout.Segment> segments, TextLayout.Mode mode,
                                int linesPerUnit, boolean shadows) {
        if (mode == TextLayout.Mode.LEFT) return null;
        if (mode == TextLayout.Mode.CONTAINER && table.containerOrigin() == 0) return null;
        TextLayout.checkInput(segments);
        LayeredTextLayout layout = new LayeredTextLayout(table, linesPerUnit, shadows);
        if (!layout.measure(segments)) return null;
        if (mode == TextLayout.Mode.CONTAINER) {
            Placement placement = layout.place(table.containerOrigin(), -1);
            return placement == null || !placement.needed() ? null : placement.result();
        }
        int floorHalf = (int) Math.floor(layout.javaWidth / 2);
        if (layout.javaWidth != Math.rint(layout.javaWidth) || layout.javaWidth < 0) return null;
        double minimum = 0;
        for (Item item : layout.items) if (!item.code) minimum = Math.min(minimum, item.required);
        // Bedrock centres a label of width W at -W / 2, Java a line of width J at -floor(J / 2):
        // with every layer W = 2 * (origin + floor(J / 2)) wide, both put Java x = 0 at the same place.
        for (int origin = (int) Math.ceil(-minimum); origin < -minimum + 64; origin++) {
            Placement placement = layout.place(origin, 2 * (origin + floorHalf));
            if (placement != null) return placement.needed() ? placement.result() : null;
        }
        return null;
    }

    private boolean measure(List<TextLayout.Segment> segments) {
        double pen = 0;
        for (int index = 0; index < segments.size(); index++) {
            TextLayout.Segment segment = segments.get(index);
            codes.add(new StringBuilder());
            if (segment.text() == null) return false;
            boolean shaded = segment.shaded();
            int[] codePoints = segment.text().codePoints().toArray();
            for (int at = 0; at < codePoints.length; at++) {
                int codePoint = codePoints[at];
                if (codePoint == '\u00a7' && at + 1 < codePoints.length) {
                    int format = codePoints[++at];
                    if (format == 'r' || format == 'R') shaded = segment.shaded();
                    else if (Character.digit(format, 16) >= 0) shaded = false;
                    String code = new StringBuilder().appendCodePoint(codePoint).appendCodePoint(format).toString();
                    items.add(new Item(index, code, pen, pen, pen, pen, 0, -1, true, utf8(code), segment.bold(), 0, false));
                    continue;
                }
                if (codePoint == '\n') return false;
                String font = segment.font();
                TextLayoutTable.Entry entry = table.lookup(font, codePoint);
                boolean defaultFont = font == null || TextLayoutTable.DEFAULT_FONT.equals(font);
                if (entry == null && codePoint == ' ' && defaultFont) entry = TextLayoutTable.Entry.advanceOnly(4);
                if (entry == null) {
                    float advance = table.textAdvance(font, codePoint);
                    if (Float.isNaN(advance)) return false;
                    if (segment.bold()) advance += 1;
                    String text = Character.toString(codePoint);
                    // Text from Java's own sheets in a named font keeps that font's vertical shift (never upwards).
                    int shift = Math.max(0, table.nativeFonts().getOrDefault(font == null ? "" : font, 0));
                    items.add(new Item(index, text, pen, pen, pen + advance, pen, advance, -1, false, utf8(text),
                            segment.bold(), shift, shadows && segment.shadowless()));
                    pen += advance;
                } else if (!entry.visible()) {
                    pen += entry.advance();
                } else {
                    if (segment.bold()) return false;
                    int bedrock = entry.bedrock(), wide = entry.wide();
                    int copy = shaded ? table.shade(bedrock) : -1;
                    if (copy >= 0) {
                        bedrock = copy;
                        wide = wide < 0 ? -1 : table.shade(wide);
                    }
                    String text = Character.toString(bedrock);
                    double ink = pen + entry.left();
                    items.add(new Item(index, text, pen, ink, ink + entry.width(), ink - 1, entry.width() + 1,
                            wide, false, utf8(text), false, 0, shadows && segment.shadowless()));
                    pen += entry.advance();
                }
            }
        }
        javaWidth = pen;
        return true;
    }

    private static int utf8(String text) {
        return text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    }

    /** One label: its runs, Bedrock pen, rightmost ink and the glyph that may still be widened. */
    private final class Layer {
        final List<Run> runs = new ArrayList<>();
        final int[] codesApplied = new int[codes.size()];
        double pen;
        double right = Double.NEGATIVE_INFINITY;
        int widenRun = -1;
        int widenIndex = -1;
        int widenCode = -1;
        int bytes;
        int shift;
        boolean shadowless;

        boolean reachable(double target) {
            double gap = target - pen;
            if (Math.abs(gap - Math.rint(gap)) > 1e-6) return false;
            int units = (int) Math.rint(gap);
            return units == 0 || units >= table.minimumSpacer() || units == 1 && widenCode >= 0;
        }

        void moveTo(double target) {
            moveTo(target, -1, true);
        }

        void moveTo(double target, int segment) {
            moveTo(target, segment, true);
        }

        /**
         * @param segment run that takes the spacers: the next character's, unless it is bold (-1: unstyled)
         * @param spaces  whether 4 and 8 unit moves may be spaces (not once the byte length is final)
         */
        void moveTo(double target, int segment, boolean spaces) {
            int units = (int) Math.rint(target - pen);
            if (units == 1) {
                Run run = runs.get(widenRun);
                StringBuilder text = new StringBuilder(run.text());
                int length = Character.charCount(text.codePointAt(widenIndex));
                text.replace(widenIndex, widenIndex + length, Character.toString(widenCode));
                runs.set(widenRun, new Run(run.segment(), text.toString()));
                pen += 1;
            } else if (units >= table.minimumSpacer()) {
                // Bedrock's space advances four units like Java's: one byte each instead of a three-byte spacer
                // (boss bar names are limited to 256 characters, measured 4 October 2026).
                String spacers = spaces && (units == 4 || units == 8) ? " ".repeat(units / 4) : spacers(units);
                append(segment, spacers);
                bytes += utf8(spacers);
                pen += units;
            }
            widenCode = -1;
        }

        void append(int segment, String text) {
            if (!runs.isEmpty() && runs.getLast().segment() == segment) {
                Run last = runs.removeLast();
                runs.add(new Run(segment, last.text() + text));
            } else {
                runs.add(new Run(segment, text));
            }
        }

        void place(Item item) {
            String pending = codes.get(item.segment).substring(codesApplied[item.segment]);
            codesApplied[item.segment] = codes.get(item.segment).length();
            // Spacers join the character's own run (fewer formatting codes); Bedrock widens bold spacers.
            moveTo(item.required, item.bold ? -1 : item.segment);
            if (!pending.isEmpty()) append(item.segment, pending);
            int runIndex = runs.isEmpty() || runs.getLast().segment() != item.segment ? runs.size() : runs.size() - 1;
            int textIndex = runIndex < runs.size() ? runs.get(runIndex).text().length() : 0;
            append(item.segment, item.text);
            bytes += item.bytes;
            pen += item.bedrockAdvance;
            right = Math.max(right, item.inkEnd);
            widenRun = runIndex;
            widenIndex = textIndex;
            widenCode = item.wide;
        }
    }

    private final class Placement {
        final List<Layer> layers = new ArrayList<>();

        /** Several labels, or one that must sit lower than the vanilla label or draw no shadow. */
        boolean needed() {
            return layers.size() > 1 || !layers.isEmpty() && (layers.getFirst().shift > 0 || layers.getFirst().shadowless);
        }

        Result result() {
            List<List<Run>> out = new ArrayList<>();
            List<Integer> lines = new ArrayList<>();
            List<Boolean> shadowless = new ArrayList<>();
            for (Layer layer : layers) {
                out.add(List.copyOf(layer.runs));
                lines.add(layer.shift * linesPerUnit);
                shadowless.add(layer.shadowless);
            }
            // The top label (the vanilla one) draws shadows: shadowless text must not end up there.
            if (shadows && layers.getLast().shadowless) {
                out.add(List.of());
                lines.add(0);
                shadowless.add(false);
            }
            return new Result(List.copyOf(out), List.copyOf(lines), List.copyOf(shadowless));
        }
    }

    /**
     * @param origin Bedrock label position of Java x = 0
     * @param width  common Bedrock width of every layer (centred surfaces), or -1
     * @return null when a character cannot reach its Java position or the layers do not fit
     */
    private Placement place(int origin, int width) {
        for (StringBuilder builder : codes) builder.setLength(0);
        Placement placement = new Placement();
        for (Item item : items) {
            if (item.code) {
                codes.get(item.segment).append(item.text);
                continue;
            }
            double target = item.required + origin;
            if (target < 0) return null;
            double inkStart = item.inkStart + origin;
            Layer chosen = null;
            for (int index = 0; index < placement.layers.size() && chosen == null; index++) {
                Layer layer = placement.layers.get(index);
                if (layer.shift != item.shift || layer.shadowless != item.shadowless || !layer.reachable(target)) continue;
                boolean covered = false;
                for (int above = index + 1; above < placement.layers.size(); above++) {
                    covered |= placement.layers.get(above).right > inkStart + 1e-9;
                }
                if (!covered) chosen = layer;
            }
            if (chosen == null) {
                // One label stays free for the shadowed top when shadowless text could take the last.
                int limit = shadows && item.shadowless ? MAX_LAYERS - 1 : MAX_LAYERS;
                if (placement.layers.size() >= limit) return null;
                chosen = new Layer();
                chosen.shift = item.shift;
                chosen.shadowless = item.shadowless;
                chosen.bytes = item.shift * linesPerUnit; // its leading newlines
                if (!chosen.reachable(target)) return null;
                placement.layers.add(chosen);
            }
            chosen.place(new Item(item.segment, item.text, item.javaX, inkStart, item.inkEnd + origin, target,
                    item.bedrockAdvance, item.wide, false, item.bytes, item.bold, item.shift, item.shadowless));
        }
        for (Layer layer : placement.layers) {
            // UTF-8 length to a multiple of three with trailing spaces (4 units each), then the common width.
            int spaces = (3 - layer.bytes % 3) % 3;
            if (width >= 0) {
                double end = width - 4 * spaces;
                if (!layer.reachable(end)) return null;
                layer.moveTo(end, -1, false);
            }
            if (spaces > 0) {
                layer.append(-1, " ".repeat(spaces));
                layer.bytes += spaces;
                layer.pen += 4 * spaces;
            }
        }
        return placement;
    }

    private String spacers(int advance) {
        if (advance > TextLayout.MAX_ADVANCE) throw new IllegalArgumentException("move wider than " + TextLayout.MAX_ADVANCE + " units");
        StringBuilder out = new StringBuilder();
        int maximum = table.maximumSpacer();
        while (advance > maximum) {
            int step = advance - maximum >= table.minimumSpacer() ? maximum : maximum - table.minimumSpacer();
            out.appendCodePoint(table.spacer(step));
            advance -= step;
        }
        return out.appendCodePoint(table.spacer(advance)).toString();
    }
}
