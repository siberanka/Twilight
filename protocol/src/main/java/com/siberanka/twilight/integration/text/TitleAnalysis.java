/* Copyright (c) 2026 siberanka. Twilight License (PolyForm Noncommercial 1.0.0 + terms), see LICENSE. */
package com.siberanka.twilight.integration.text;

import com.siberanka.twilight.text.OffsetTags;
import com.siberanka.twilight.text.TextLayout;
import com.siberanka.twilight.text.TextLayoutTable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What a menu title holds, as Bedrock players get it from a pack: plain text (shown as it is), images and moves the
 * layout places, offset tags a plugin left unreplaced, and private-use characters the pack has no glyph for (Bedrock
 * draws its own or nothing there, where Java shows the pack's image).
 *
 * @param text    the title's characters, formatting removed
 * @param plain   no custom character: Bedrock shows it as Java sent it, at Java's position
 * @param glyphs  images and characters the pack draws
 * @param moves   negative and positive spaces (including offset tags)
 * @param tags    offset tags left in the text ({@code :offset_-8:}, {@code <shift:-8>}...)
 * @param missing private-use characters without a glyph in the pack, as {@code U+E123} (font in brackets)
 */
public record TitleAnalysis(String text, boolean plain, int glyphs, int moves, int tags, List<String> missing) {
    private static final int MAX_MISSING = 16;

    /** Analyses a title component (Paper's or Geyser's Adventure, relocated or not). */
    public static TitleAnalysis of(TextLayoutTable table, Object component) throws ReflectiveOperationException {
        List<TextLayout.Segment> segments = AdventureTextLayout.titleSegments(component);
        StringBuilder text = new StringBuilder();
        int glyphs = 0, moves = 0, tags = 0;
        Set<String> missing = new LinkedHashSet<>();
        for (TextLayout.Segment segment : segments) {
            if (segment.text() == null) continue;
            int[] codePoints = segment.text().codePoints().toArray();
            for (int index = 0; index < codePoints.length; index++) {
                int codePoint = codePoints[index];
                if (codePoint == '§' && index + 1 < codePoints.length) {
                    index++;
                    continue;
                }
                if (OffsetTags.isMove(codePoint)) {
                    // The layout reads offset tags as moves; the report names them as the plugin wrote them.
                    tags++;
                    moves++;
                    text.append("<offset ").append(OffsetTags.move(codePoint)).append('>');
                    continue;
                }
                TextLayoutTable.Entry entry = table == null ? null : table.lookup(segment.font(), codePoint);
                // Images and moves are named, so the report reads without the pack's font.
                if (entry != null && codePoint != ' ' && (entry.visible() || privateUse(codePoint))) {
                    text.append(entry.visible() ? String.format(Locale.ROOT, "[image U+%04X]", codePoint)
                            : "[move " + Math.round(entry.advance()) + "]");
                } else if (entry == null && privateUse(codePoint)) {
                    text.append(String.format(Locale.ROOT, "[no image U+%04X]", codePoint));
                } else {
                    text.appendCodePoint(codePoint);
                }
                if (entry != null) {
                    if (entry.visible()) glyphs++;
                    else if (codePoint != ' ') moves++;
                } else if (privateUse(codePoint) && missing.size() < MAX_MISSING) {
                    String name = String.format(Locale.ROOT, "U+%04X", codePoint);
                    String font = segment.font();
                    missing.add(font == null || TextLayoutTable.DEFAULT_FONT.equals(font) ? name : name + " (" + font + ")");
                }
            }
        }
        boolean plain = glyphs == 0 && moves == 0 && missing.isEmpty();
        return new TitleAnalysis(text.toString(), plain, glyphs, moves, tags, new ArrayList<>(missing));
    }

    private static boolean privateUse(int codePoint) {
        return codePoint >= 0xE000 && codePoint <= 0xF8FF || codePoint >= 0xF0000 && !OffsetTags.isMove(codePoint);
    }
}
