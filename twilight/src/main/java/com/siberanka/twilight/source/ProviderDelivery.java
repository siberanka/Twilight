/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.source;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Which provider packs a server actually sends to Java players.
 *
 * <p>Servers often run several content plugins that each generate a pack, while only one is
 * delivered: for example ItemsAdder with hosting disabled ({@code no-host}) next to CraftEngine,
 * which sends its own pack that already contains the converted ItemsAdder content. Where the
 * undelivered pack disagrees (a character mapped to another image or advance), Java players see
 * the delivered one, so its sources must win. Only explicit settings are read; anything unclear
 * counts as delivered.
 */
final class ProviderDelivery {
    private ProviderDelivery() {}

    /** Whether the provider's plugin JAR is present (a folder alone can be leftover data). */
    static boolean installed(Path pluginsDirectory, String provider) {
        String[] names = switch (provider) {
            case "itemsadder" -> new String[]{"itemsadder"};
            case "craftengine" -> new String[]{"craft-engine", "craftengine"};
            case "modelengine" -> new String[]{"model-engine", "modelengine"};
            default -> new String[]{provider};
        };
        try (var files = Files.list(pluginsDirectory)) {
            return files.filter(Files::isRegularFile).map(path -> path.getFileName().toString().toLowerCase(Locale.ROOT))
                    .filter(name -> name.endsWith(".jar"))
                    .anyMatch(name -> java.util.Arrays.stream(names).anyMatch(name::contains));
        } catch (IOException unreadable) {
            return true;
        }
    }

    /** False only when the provider's own configuration says its pack is not sent. */
    static boolean delivers(Path pluginsDirectory, String provider) {
        return switch (provider) {
            case "itemsadder" -> !flag(pluginsDirectory.resolve("ItemsAdder/config.yml"),
                    "resource-pack.hosting.no-host.enabled").orElse(false);
            case "craftengine" -> flag(pluginsDirectory.resolve("CraftEngine/config.yml"),
                    "resource-pack.delivery.send-on-join").orElse(true);
            case "nexo" -> {
                Path settings = pluginsDirectory.resolve("Nexo/settings.yml");
                boolean preJoin = flag(settings, "Pack.dispatch.send_pre_join").orElse(true);
                boolean onJoin = flag(settings, "Pack.dispatch.send_on_join").orElse(true);
                String type = value(settings, "Pack.server.type").orElse("").toUpperCase(Locale.ROOT);
                yield (preJoin || onJoin) && !type.equals("NONE");
            }
            default -> true;
        };
    }

    static Optional<Boolean> flag(Path yaml, String path) {
        return value(yaml, path).map(value -> value.equalsIgnoreCase("true"));
    }

    /** A scalar from a block-style YAML file by dotted path (lists and anchors are not needed here). */
    static Optional<String> value(Path yaml, String path) {
        if (!Files.isRegularFile(yaml)) return Optional.empty();
        try {
            return Optional.ofNullable(scalars(Files.readString(yaml, StandardCharsets.UTF_8)).get(path));
        } catch (IOException | RuntimeException unreadable) {
            return Optional.empty();
        }
    }

    static Map<String, String> scalars(String text) {
        Map<String, String> values = new HashMap<>();
        Deque<int[]> indents = new ArrayDeque<>();
        Deque<String> keys = new ArrayDeque<>();
        for (String raw : text.split("\\R")) {
            String line = stripComment(raw);
            if (line.isBlank() || line.trim().startsWith("-")) continue;
            int indent = line.length() - line.stripLeading().length();
            int colon = line.indexOf(':');
            if (colon < 0) continue;
            String key = line.substring(indent, colon).trim();
            if (key.length() >= 2 && (key.startsWith("\"") || key.startsWith("'"))) key = key.substring(1, key.length() - 1);
            while (!indents.isEmpty() && indents.peek()[0] >= indent) {
                indents.pop();
                keys.pop();
            }
            String value = line.substring(colon + 1).trim();
            String full = keys.isEmpty() ? key : String.join(".", keys.reversed()) + '.' + key;
            if (value.isEmpty()) {
                indents.push(new int[]{indent});
                keys.push(key);
            } else {
                if ((value.startsWith("\"") && value.endsWith("\"") || value.startsWith("'") && value.endsWith("'"))
                        && value.length() >= 2) value = value.substring(1, value.length() - 1);
                values.put(full, value);
            }
        }
        return values;
    }

    private static String stripComment(String line) {
        boolean single = false, dbl = false;
        for (int index = 0; index < line.length(); index++) {
            char c = line.charAt(index);
            if (c == '\'' && !dbl) single = !single;
            else if (c == '"' && !single) dbl = !dbl;
            else if (c == '#' && !single && !dbl && (index == 0 || Character.isWhitespace(line.charAt(index - 1)))) {
                return line.substring(0, index);
            }
        }
        return line;
    }
}
