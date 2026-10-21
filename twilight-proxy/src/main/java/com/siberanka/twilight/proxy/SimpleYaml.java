/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.proxy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The YAML subset twilight-proxy's configuration uses: nested maps of scalars, two-space or any
 * consistent indentation, comments, single or double quotes and one-line lists ({@code [a, b]}). It is
 * the same on Velocity and BungeeCord and has no dependency; anything outside the subset (block lists,
 * anchors, multi-line values) is rejected with the line number instead of being guessed.
 */
final class SimpleYaml {
    private static final int MAX_LINES = 10_000;

    private SimpleYaml() {}

    static Map<String, Object> parse(String text) {
        Map<String, Object> root = new LinkedHashMap<>();
        Deque<Map<String, Object>> maps = new ArrayDeque<>();
        Deque<Integer> indents = new ArrayDeque<>();
        maps.push(root);
        indents.push(-1);
        String[] lines = text.split("\r?\n", -1);
        if (lines.length > MAX_LINES) throw new IllegalArgumentException("configuration is too long");
        for (int number = 1; number <= lines.length; number++) {
            String line = stripComment(lines[number - 1]);
            if (line.isBlank()) continue;
            if (line.indexOf('\t') >= 0) throw error(number, "tabs are not allowed");
            int indent = 0;
            while (line.charAt(indent) == ' ') indent++;
            String content = line.substring(indent).stripTrailing();
            if (content.startsWith("- ") || content.equals("-")) throw error(number, "lists are not supported");
            int colon = keyEnd(content);
            if (colon <= 0) throw error(number, "expected 'key: value'");
            String key = unquote(content.substring(0, colon).strip(), number);
            String value = content.substring(colon + 1).strip();
            while (indent <= indents.peek()) {
                maps.pop();
                indents.pop();
            }
            Map<String, Object> parent = maps.peek();
            if (parent.containsKey(key)) throw error(number, "duplicate key '" + key + "'");
            if (value.isEmpty()) {
                Map<String, Object> child = new LinkedHashMap<>();
                parent.put(key, child);
                maps.push(child);
                indents.push(indent);
            } else if (value.startsWith("[") && value.endsWith("]") && value.indexOf('[', 1) < 0 && value.indexOf('{') < 0) {
                parent.put(key, flowList(value.substring(1, value.length() - 1), number));
            } else {
                if (value.startsWith("[") || value.startsWith("{") || value.startsWith("&") || value.startsWith("*")
                        || value.startsWith("|") || value.startsWith(">")) {
                    throw error(number, "only plain values are supported");
                }
                parent.put(key, unquote(value, number));
            }
        }
        return root;
    }

    /** The values of a one-line list ({@code [a, "b"]}); entries are plain or quoted values. */
    private static List<String> flowList(String body, int number) {
        if (body.isBlank()) return List.of();
        List<String> values = new ArrayList<>();
        for (String entry : body.split(",", -1)) {
            String value = entry.strip();
            if (value.isEmpty()) throw error(number, "empty list entry");
            values.add(unquote(value, number));
        }
        return List.copyOf(values);
    }

    private static int keyEnd(String content) {
        char quote = 0;
        for (int index = 0; index < content.length(); index++) {
            char c = content.charAt(index);
            if (quote != 0) {
                if (c == quote) quote = 0;
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == ':' && (index + 1 == content.length() || content.charAt(index + 1) == ' ')) {
                return index;
            }
        }
        return -1;
    }

    private static String stripComment(String line) {
        char quote = 0;
        for (int index = 0; index < line.length(); index++) {
            char c = line.charAt(index);
            if (quote != 0) {
                if (c == quote) quote = 0;
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '#' && (index == 0 || line.charAt(index - 1) == ' ')) {
                return line.substring(0, index);
            }
        }
        return line;
    }

    private static String unquote(String value, int number) {
        if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"") || value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        if (value.startsWith("\"") || value.startsWith("'")) throw error(number, "unterminated quote");
        return value;
    }

    private static IllegalArgumentException error(int line, String message) {
        return new IllegalArgumentException("config.yml line " + line + ": " + message);
    }
}
