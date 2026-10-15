/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.text;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pixel offsets written as tags that a content plugin was meant to replace before the text left the server:
 * ItemsAdder's {@code :offset_-8:} and {@code %img_offset_-8%}, Nexo's and Oraxen's {@code %nexo_shift_-8%} /
 * {@code %oraxen_shift_-8%}, and the {@code <shift:-8>} tag of CraftEngine, Nexo and Oraxen.
 *
 * <p>When such a tag reaches a client unreplaced (a plugin that formats menu titles itself, a provider that
 * handles Bedrock players differently, an outdated bridge), the art that follows it sits where the tag's
 * letters end instead of where the offset puts it. The layout reads each tag as the move it names: a code point
 * of Unicode's supplementary private use area B stands for it inside the layout (see {@link #encode}) and never
 * reaches the client.
 */
public final class OffsetTags {
    /** Largest move one tag may make, in Java GUI units. */
    static final int MAX_MOVE = 4096;
    private static final int BASE = 0x108000; // the move 0; moves -4096..4096 stay inside U+100000-U+10FFFD
    private static final Pattern TAG = Pattern.compile(
            ":offset_(-?\\d{1,4}):"
            + "|%img_offset_(-?\\d{1,4})%"
            + "|%(?:nexo|oraxen)_shift_(-?\\d{1,4})%"
            + "|<shift:(-?\\d{1,4})>", Pattern.CASE_INSENSITIVE);

    private OffsetTags() {}

    /** The text with every offset tag replaced by its move code point; text without tags is returned as is. */
    public static String encode(String text) {
        if (text == null || text.length() < 7
                || text.indexOf(':') < 0 && text.indexOf('%') < 0 && text.indexOf('<') < 0) return text;
        Matcher matcher = TAG.matcher(text);
        StringBuilder out = null;
        int last = 0;
        while (matcher.find()) {
            String digits = matcher.group(1) != null ? matcher.group(1) : matcher.group(2) != null ? matcher.group(2)
                    : matcher.group(3) != null ? matcher.group(3) : matcher.group(4);
            int move = Integer.parseInt(digits);
            if (Math.abs(move) > MAX_MOVE) continue;
            if (out == null) out = new StringBuilder(text.length());
            out.append(text, last, matcher.start()).appendCodePoint(BASE + move);
            last = matcher.end();
        }
        if (out == null) return text;
        return out.append(text, last, text.length()).toString();
    }

    /** How many offset tags a text holds (tags a plugin should have replaced before sending it). */
    public static int count(String text) {
        if (text == null || text.length() < 7) return 0;
        Matcher matcher = TAG.matcher(text);
        int count = 0;
        while (matcher.find()) count++;
        return count;
    }

    /** Whether a code point stands for a tag's move. */
    public static boolean isMove(int codePoint) {
        return codePoint >= BASE - MAX_MOVE && codePoint <= BASE + MAX_MOVE;
    }

    /** The move, in Java GUI units, a code point of {@link #isMove} stands for. */
    public static int move(int codePoint) {
        return codePoint - BASE;
    }
}
