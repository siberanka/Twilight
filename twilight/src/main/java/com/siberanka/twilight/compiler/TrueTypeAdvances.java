/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.compiler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads horizontal advances and outline presence from a TrueType font, as Java's
 * {@code ttf} font provider sees them.
 *
 * <p>Java uses STB TrueType: the advance width is read as a signed 16-bit value and
 * scaled by {@code size / (hhea.ascent - hhea.descent)}. Negative-space fonts rely on
 * the signed reading. Only cmap formats 4 and 12 are needed for BMP and
 * supplementary characters.
 *
 * <p>Java 26.2 loads TrueType files with FreeType, which refuses a font without a
 * {@code maxp} table; such a provider then contributes no characters. A live test on
 * 3 October 2026 showed a real negative-space font lacking {@code maxp} rendering its
 * characters with fallback glyphs instead of negative advances, so it is rejected here too.
 */
final class TrueTypeAdvances {
    private TrueTypeAdvances() {}

    /** Advance in GUI units and whether the glyph has an outline. */
    record Metrics(float advance, boolean visible) {}

    static Map<Integer, Metrics> read(byte[] font, float size) throws IOException {
        try {
            ByteBuffer data = ByteBuffer.wrap(font);
            int tables = data.getShort(4) & 0xFFFF;
            Map<String, int[]> directory = new HashMap<>();
            for (int index = 0; index < tables; index++) {
                int record = 12 + 16 * index;
                String tag = new String(font, record, 4, java.nio.charset.StandardCharsets.US_ASCII);
                directory.put(tag, new int[]{data.getInt(record + 8), data.getInt(record + 12)});
            }
            int[] hhea = require(directory, "hhea");
            int[] hmtx = require(directory, "hmtx");
            int[] cmap = require(directory, "cmap");
            require(directory, "maxp");
            int ascent = data.getShort(hhea[0] + 4);
            int descent = data.getShort(hhea[0] + 6);
            int metrics = data.getShort(hhea[0] + 34) & 0xFFFF;
            if (ascent - descent == 0 || metrics == 0) throw new IOException("invalid hhea metrics");
            float scale = size / (ascent - descent);
            int[] loca = directory.get("loca");
            int[] glyf = directory.get("glyf");
            int[] head = directory.get("head");
            boolean longOffsets = head != null && data.getShort(head[0] + 50) != 0;

            Map<Integer, Metrics> result = new LinkedHashMap<>();
            for (Map.Entry<Integer, Integer> entry : characterMap(data, cmap[0]).entrySet()) {
                int glyph = entry.getValue();
                int metric = Math.min(glyph, metrics - 1);
                short advance = data.getShort(hmtx[0] + 4 * metric); // signed, like stbtt_GetGlyphHMetrics
                boolean visible = loca == null || glyf == null
                        || visibleOutline(data, loca[0], glyf[0], glyph, longOffsets, scale);
                result.put(entry.getKey(), new Metrics(advance * scale, visible));
            }
            return result;
        } catch (IndexOutOfBoundsException malformed) {
            throw new IOException("truncated TrueType data", malformed);
        }
    }

    /**
     * Negative-space fonts give each glyph a token outline (for example 1x1 font units, a tenth of
     * a pixel); Java's rasterizer leaves it effectively blank. Outlines covering under a quarter of
     * a GUI pixel are treated as spacing only.
     */
    private static boolean visibleOutline(ByteBuffer data, int loca, int glyf, int glyph, boolean longOffsets,
                                          float scale) {
        int start = longOffsets ? data.getInt(loca + 4 * glyph) : 2 * (data.getShort(loca + 2 * glyph) & 0xFFFF);
        int end = longOffsets ? data.getInt(loca + 4 * (glyph + 1)) : 2 * (data.getShort(loca + 2 * (glyph + 1)) & 0xFFFF);
        if (end - start < 10) return false;
        int header = glyf + start;
        if (data.getShort(header) == 0) return false;
        float width = (data.getShort(header + 6) - data.getShort(header + 2)) * Math.abs(scale);
        float height = (data.getShort(header + 8) - data.getShort(header + 4)) * Math.abs(scale);
        return width * height >= 0.25f;
    }

    private static Map<Integer, Integer> characterMap(ByteBuffer data, int cmap) throws IOException {
        int subtables = data.getShort(cmap + 2) & 0xFFFF;
        int format4 = -1, format12 = -1;
        for (int index = 0; index < subtables; index++) {
            int offset = cmap + data.getInt(cmap + 8 + 8 * index);
            int format = data.getShort(offset) & 0xFFFF;
            if (format == 12) format12 = offset;
            else if (format == 4 && format4 < 0) format4 = offset;
        }
        Map<Integer, Integer> result = new LinkedHashMap<>();
        if (format12 >= 0) {
            int groups = data.getInt(format12 + 12);
            for (int group = 0; group < groups; group++) {
                int record = format12 + 16 + 12 * group;
                int start = data.getInt(record), end = data.getInt(record + 4), first = data.getInt(record + 8);
                for (int codePoint = start; codePoint <= end && codePoint <= Character.MAX_CODE_POINT; codePoint++) {
                    result.put(codePoint, first + codePoint - start);
                }
            }
            return result;
        }
        if (format4 < 0) throw new IOException("no supported cmap subtable");
        int segments = (data.getShort(format4 + 6) & 0xFFFF) / 2;
        int ends = format4 + 14, starts = ends + 2 * segments + 2;
        int deltas = starts + 2 * segments, ranges = deltas + 2 * segments;
        for (int segment = 0; segment < segments; segment++) {
            int end = data.getShort(ends + 2 * segment) & 0xFFFF;
            int start = data.getShort(starts + 2 * segment) & 0xFFFF;
            int delta = data.getShort(deltas + 2 * segment);
            int range = data.getShort(ranges + 2 * segment) & 0xFFFF;
            for (int codePoint = start; codePoint <= end && codePoint != 0xFFFF; codePoint++) {
                int glyph;
                if (range == 0) glyph = (codePoint + delta) & 0xFFFF;
                else {
                    int address = ranges + 2 * segment + range + 2 * (codePoint - start);
                    glyph = data.getShort(address) & 0xFFFF;
                    if (glyph != 0) glyph = (glyph + delta) & 0xFFFF;
                }
                if (glyph != 0) result.put(codePoint, glyph);
            }
        }
        return result;
    }

    private static int[] require(Map<String, int[]> directory, String tag) throws IOException {
        int[] table = directory.get(tag);
        if (table == null) throw new IOException("missing " + tag + " table");
        return table;
    }
}
