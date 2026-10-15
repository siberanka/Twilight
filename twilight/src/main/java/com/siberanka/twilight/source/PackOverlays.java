/* Copyright (c) 2026 siberanka. Twilight License (PolyForm Noncommercial 1.0.0 + terms), see LICENSE. */
package com.siberanka.twilight.source;

import com.google.gson.*;
import java.io.IOException;
import java.util.*;

/** Selects pack overlays in declaration order, independent of ZIP/filesystem order. */
final class PackOverlays {
    private final String prefix;
    private final List<String> active;
    private final boolean declared;

    private PackOverlays(String prefix, List<String> active, boolean declared) {
        this.prefix = prefix;
        this.active = active;
        this.declared = declared;
    }

    static PackOverlays read(Map<String, ResourceIndex.Asset> entries, ResourceIndex.PackFormatSupplier format, ContentSource.Kind kind) throws IOException {
        List<String> metadata = entries.keySet().stream()
                .filter(path -> path.equals("pack.mcmeta") || path.endsWith("/pack.mcmeta"))
                .sorted(Comparator.comparingInt(String::length).thenComparing(String::compareTo)).toList();
        if (metadata.isEmpty()) return new PackOverlays("", List.of(), false);
        String path = metadata.getFirst();
        try {
            JsonObject root = JsonParser.parseString(entries.get(path).readUtf8()).getAsJsonObject();
            if (!root.has("overlays")) return new PackOverlays("", List.of(), false);
            JsonArray overlays = root.getAsJsonObject("overlays").getAsJsonArray("entries");
            if (overlays == null || overlays.isEmpty()) return new PackOverlays("", List.of(), false);
            ResourceIndex.PackFormat selected = format.get(kind);
            List<String> active = new ArrayList<>();
            Set<String> directories = new HashSet<>();
            for (JsonElement value : overlays) {
                JsonObject entry = value.getAsJsonObject();
                String directory = entry.get("directory").getAsString();
                // Java permits version-like names and uppercase letters. Keep
                // path segments confined to the pack when accepting dots.
                if (!directory.matches("[a-zA-Z0-9_.-]+") || directory.equals(".") || directory.equals("..") || !directories.add(directory)) {
                    throw new IOException("Invalid or duplicate overlay directory: " + directory);
                }
                ResourceIndex.PackFormat min, max;
                if (entry.has("min_format") && entry.has("max_format")) {
                    min = parse(entry.get("min_format"), false);
                    max = parse(entry.get("max_format"), true);
                } else {
                    JsonElement range = entry.get("formats");
                    if (range == null) throw new IOException("Overlay has no format range: " + directory);
                    if (range.isJsonObject()) {
                        min = parse(range.getAsJsonObject().get("min_inclusive"), false);
                        max = parse(range.getAsJsonObject().get("max_inclusive"), true);
                    } else if (range.isJsonArray()) {
                        JsonArray pair = range.getAsJsonArray();
                        if (pair.size() != 2) throw new IOException("Invalid overlay format range");
                        min = parse(pair.get(0), false);
                        max = parse(pair.get(1), true);
                    } else {
                        min = parse(range, false);
                        max = parse(range, true);
                    }
                }
                if (min.compareTo(max) > 0) throw new IOException("Reversed overlay range: " + directory);
                if (selected.compareTo(min) >= 0 && selected.compareTo(max) <= 0) active.add(directory);
            }
            return new PackOverlays(path.substring(0, path.length() - "pack.mcmeta".length()), List.copyOf(active), true);
        } catch (RuntimeException failure) {
            throw new IOException("Invalid pack overlay metadata: " + path, failure);
        }
    }

    private static ResourceIndex.PackFormat parse(JsonElement value, boolean upper) throws IOException {
        if (value == null) throw new IOException("Missing overlay format endpoint");
        if (value.isJsonArray()) {
            JsonArray pair = value.getAsJsonArray();
            if (pair.size() != 2) throw new IOException("Invalid pack format version");
            return new ResourceIndex.PackFormat(integer(pair.get(0)), integer(pair.get(1)));
        }
        return new ResourceIndex.PackFormat(integer(value), upper ? Integer.MAX_VALUE : 0);
    }

    private static int integer(JsonElement value) throws IOException {
        double number = value.getAsDouble();
        if (!Double.isFinite(number) || number < 0 || number != Math.rint(number) || number > Integer.MAX_VALUE)
            throw new IOException("Invalid pack format number");
        return (int) number;
    }

    int order(String original) {
        if (!declared) return 0;
        if (!original.startsWith(prefix)) return -1;
        String path = original.substring(prefix.length());
        if (path.startsWith("assets/") || path.startsWith("data/")) return 0;
        for (int i = active.size() - 1; i >= 0; i--) if (path.startsWith(active.get(i) + "/")) return i + 1;
        return -1;
    }
}
