/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.settings;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The outline of a YAML configuration file, line by line: where every key starts and ends, its comments and the
 * exact span of its value. It lets a file be updated by inserting and replacing lines, so that everything else an
 * administrator wrote (comments, order, quoting, spacing, unknown keys) stays byte for byte as it was.
 *
 * <p>Supported: nested mappings, scalars (plain or quoted), one-line lists ({@code [a, b]}), block lists
 * ({@code - a}) and block scalars ({@code |}, {@code >}). Lists, block scalars, flow mappings, anchors and tags are
 * kept as opaque values that are never changed. Tabs and keys that cannot be read are refused, and the caller then
 * leaves the file alone.
 */
final class YamlOutline {
    static final int MAX_LINES = 20_000;

    enum Kind { SCALAR, INLINE_LIST, SECTION, OPAQUE }

    /**
     * One key.
     *
     * @param line         index of the key's line
     * @param end          index after the key's last line (its children, list items or block scalar)
     * @param commentStart index of the first comment line directly above the key (no blank line between)
     * @param valueStart   offset of the value in the key's line, or -1 for a section
     * @param valueEnd     offset after the value (before a trailing comment)
     * @param keyEnd       offset of the colon after the key
     */
    record Entry(String path, String parent, String key, int indent, int line, int end, int commentStart,
                 Kind kind, String value, int valueStart, int valueEnd, int keyEnd) {}

    final List<String> lines;
    final List<Entry> entries;
    final Map<String, Entry> byPath;

    private YamlOutline(List<String> lines, List<Entry> entries) {
        this.lines = lines;
        this.entries = entries;
        Map<String, Entry> index = new LinkedHashMap<>();
        for (Entry entry : entries) index.put(entry.path(), entry);
        this.byPath = Collections.unmodifiableMap(index);
    }

    Entry get(String path) {
        return byPath.get(path);
    }

    /** The direct children of a section ({@code ""} is the root), in file order. */
    List<Entry> children(String parent) {
        List<Entry> children = new ArrayList<>();
        for (Entry entry : entries) if (entry.parent().equals(parent)) children.add(entry);
        return children;
    }

    static List<String> split(String text) {
        List<String> lines = new ArrayList<>(List.of(text.replace("\r\n", "\n").split("\n", -1)));
        // A final newline does not start another line.
        if (!lines.isEmpty() && lines.getLast().isEmpty()) lines.removeLast();
        return lines;
    }

    /** Reads the outline; throws when the file uses something it cannot follow safely. */
    static YamlOutline parse(String text) {
        List<String> lines = split(text);
        if (lines.size() > MAX_LINES) throw new IllegalArgumentException("more than " + MAX_LINES + " lines");
        List<Entry> entries = new ArrayList<>();
        Deque<Integer> sectionIndents = new ArrayDeque<>();
        Deque<String> sectionPaths = new ArrayDeque<>();
        Map<String, Boolean> seen = new LinkedHashMap<>();
        int opaqueIndent = -1; // lines deeper than this belong to an opaque value (block list or block scalar)
        boolean listAtSameIndent = false;
        List<Object[]> raw = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.indexOf('\t') >= 0 && !line.strip().startsWith("#")) throw new IllegalArgumentException("tab on line " + (index + 1));
            String stripped = line.strip();
            if (stripped.isEmpty() || stripped.startsWith("#")) continue;
            int indent = indent(line);
            if (opaqueIndent >= 0) {
                boolean item = stripped.startsWith("- ") || stripped.equals("-");
                if (indent > opaqueIndent || listAtSameIndent && indent == opaqueIndent && item) continue;
                opaqueIndent = -1;
                listAtSameIndent = false;
            }
            if (stripped.startsWith("- ") || stripped.equals("-")) {
                // A list item right under a key without a value: that key holds a block list.
                if (raw.isEmpty()) throw new IllegalArgumentException("list without a key on line " + (index + 1));
                Object[] last = (Object[]) raw.getLast();
                if (last[2] != Kind.SECTION || (int) last[1] > indent || (int) last[0] != previousContent(lines, index)) {
                    throw new IllegalArgumentException("list item without a key on line " + (index + 1));
                }
                last[2] = Kind.OPAQUE;
                // It was opened as a section: close it again.
                sectionIndents.pop();
                sectionPaths.pop();
                opaqueIndent = (int) last[1];
                listAtSameIndent = true;
                continue;
            }
            if (stripped.startsWith("{") || stripped.startsWith("[") || stripped.startsWith("&") || stripped.startsWith("*")
                    || stripped.startsWith("!") || stripped.startsWith("?") || stripped.startsWith("---") || stripped.startsWith("...")) {
                throw new IllegalArgumentException("unsupported YAML on line " + (index + 1));
            }
            int colon = keyEnd(line, indent);
            if (colon < 0) throw new IllegalArgumentException("expected 'key: value' on line " + (index + 1));
            String key = unquote(line.substring(indent, colon).strip());
            if (key.isEmpty()) throw new IllegalArgumentException("empty key on line " + (index + 1));
            while (!sectionIndents.isEmpty() && indent <= sectionIndents.peek()) {
                sectionIndents.pop();
                sectionPaths.pop();
            }
            String parent = sectionPaths.isEmpty() ? "" : sectionPaths.peek();
            String path = parent.isEmpty() ? key : parent + "." + key;
            if (seen.put(path, true) != null) throw new IllegalArgumentException("duplicate key '" + path + "' on line " + (index + 1));
            int valueStart = colon + 1;
            while (valueStart < line.length() && line.charAt(valueStart) == ' ') valueStart++;
            int valueEnd = valueEnd(line, valueStart);
            String value = line.substring(valueStart, valueEnd);
            Kind kind;
            if (value.isEmpty()) {
                kind = Kind.SECTION;
                sectionIndents.push(indent);
                sectionPaths.push(path);
            } else if (value.startsWith("|") || value.startsWith(">")) {
                kind = Kind.OPAQUE;
                opaqueIndent = indent;
            } else if (value.startsWith("[") && value.endsWith("]") && value.indexOf('[', 1) < 0 && value.indexOf('{') < 0) {
                kind = Kind.INLINE_LIST;
            } else if (value.startsWith("{") || value.startsWith("[") || value.startsWith("&") || value.startsWith("*")
                    || value.startsWith("!")) {
                // Flow mappings, multi-line lists, anchors, aliases and tags: the file is only read.
                throw new IllegalArgumentException("unsupported YAML on line " + (index + 1));
            } else {
                if (value.startsWith("\"") || value.startsWith("'")) unquote(value); // validates the quotes
                kind = Kind.SCALAR;
            }
            raw.add(new Object[]{index, indent, kind, path, parent, key, value, valueStart, valueEnd, colon});
        }
        // Ends and comments, now that every key is known.
        for (int i = 0; i < raw.size(); i++) {
            Object[] item = raw.get(i);
            int line = (int) item[0], indent = (int) item[1];
            int next = lines.size();
            for (int j = i + 1; j < raw.size(); j++) {
                if ((int) raw.get(j)[1] <= indent) {
                    next = (int) raw.get(j)[0];
                    break;
                }
            }
            // The next key's comments are not part of this one.
            int end = next;
            if (next < lines.size()) end = commentStart(lines, next);
            while (end > line + 1 && lines.get(end - 1).isBlank()) end--;
            if (next == lines.size()) {
                while (end > line + 1 && (lines.get(end - 1).isBlank() || lines.get(end - 1).strip().startsWith("#")
                        && indent(lines.get(end - 1)) <= indent)) end--;
            }
            Kind kind = (Kind) item[2];
            entries.add(new Entry((String) item[3], (String) item[4], (String) item[5], indent, line, Math.max(end, line + 1),
                    commentStart(lines, line), kind, (String) item[6], kind == Kind.SECTION ? -1 : (int) item[7],
                    kind == Kind.SECTION ? -1 : (int) item[8], (int) item[9]));
        }
        return new YamlOutline(List.copyOf(lines), List.copyOf(entries));
    }

    private static int previousContent(List<String> lines, int index) {
        for (int i = index - 1; i >= 0; i--) {
            String stripped = lines.get(i).strip();
            if (!stripped.isEmpty() && !stripped.startsWith("#")) return i;
        }
        return -1;
    }

    /** The first line of the comment block directly above {@code line}. */
    static int commentStart(List<String> lines, int line) {
        int start = line;
        while (start > 0 && lines.get(start - 1).strip().startsWith("#")) start--;
        return start;
    }

    static int indent(String line) {
        int indent = 0;
        while (indent < line.length() && line.charAt(indent) == ' ') indent++;
        return indent;
    }

    /** Offset of the colon that ends the key, or -1. */
    private static int keyEnd(String line, int from) {
        char quote = 0;
        for (int i = from; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quote != 0) {
                if (c == quote) quote = 0;
                continue;
            }
            if ((c == '"' || c == '\'') && i == from) {
                quote = c;
                continue;
            }
            if (c == '#' && (i == from || line.charAt(i - 1) == ' ')) return -1;
            if (c == ':' && (i + 1 == line.length() || line.charAt(i + 1) == ' ')) return i;
        }
        return -1;
    }

    /** Offset after a value: before a comment (" #" outside quotes) and trailing spaces. */
    static int valueEnd(String line, int start) {
        char quote = 0;
        int end = line.length();
        for (int i = start; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    if (quote == '\'' && i + 1 < line.length() && line.charAt(i + 1) == '\'') {
                        i++;
                        continue;
                    }
                    quote = 0;
                } else if (c == '\\' && quote == '"') {
                    i++;
                }
                continue;
            }
            if ((c == '"' || c == '\'') && i == start) {
                quote = c;
                continue;
            }
            if (c == '#' && i > start && line.charAt(i - 1) == ' ') {
                end = i;
                break;
            }
        }
        if (quote != 0) throw new IllegalArgumentException("unclosed quote in: " + line.strip());
        while (end > start && line.charAt(end - 1) == ' ') end--;
        return end;
    }

    /** A scalar without its quotes. */
    static String unquote(String value) {
        if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"") || value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        if (value.startsWith("\"") || value.startsWith("'")) throw new IllegalArgumentException("unclosed quote: " + value);
        return value;
    }
}
