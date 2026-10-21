/* Copyright (c) 2026 siberanka. Twilight License (PolyForm Noncommercial 1.0.0 + terms), see LICENSE. */
package com.siberanka.twilight.integration.display;

import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;

/**
 * A pack's display variants ({@code twilight/display-index-v2.json}): items by their Bedrock identifier, model
 * bones (which have no Bedrock item) by their Java {@code item_model}, and the display transforms they send. A pack
 * is input from a backend or a file a proxy owner placed, so nothing in it is trusted.
 *
 * @param items   variants by Bedrock item identifier
 * @param models  variants by Java item model
 * @param vanilla variants of items shown with their own model, by Java item identifier
 * @param blocks  variants of block states shown in block displays, by {@link DisplayKeys#block} key
 * @param flat    ordinals of items Java draws flat (generated from their sprite); Bedrock draws those as dropped
 *                items itself
 * @param heads   variants of player heads, by skin hash
 */
public record DisplayIndex(Map<String, Variant> items, Map<String, Variant> models, Map<String, Variant> vanilla,
                           Map<String, Variant> blocks, Set<Integer> flat, Map<String, Variant> heads) {
    public DisplayIndex(Map<String, Variant> items, Map<String, Variant> models) {
        this(items, models, Map.of(), Map.of(), Set.of(), Map.of());
    }

    /** The pack entry that lists the display variants (format 2: variants in sheets of geometries). */
    public static final String INDEX = "twilight/display-index-v2.json";
    /** Variant ordinals stay below this: the packed appearance (ordinal * 9 + context) stays within the +-1,000,000 Geyser recommends for int properties. */
    public static final int MAX_VARIANTS = 1736 * 64;
    private static final int MAX_INDEX_CHARS = 16 * 1024 * 1024;
    public static final DisplayIndex EMPTY = new DisplayIndex(Map.of(), Map.of());

    /**
     * One item of the pack: its ordinal and, unless the item is untransformed, its display transform for each of
     * the 9 Java contexts (translation, rotation, scale in Bedrock bone space; 81 values).
     */
    public record Variant(int ordinal, float[] contexts) {}

    public boolean isEmpty() {
        return items.isEmpty() && models.isEmpty() && vanilla.isEmpty() && blocks.isEmpty() && heads.isEmpty();
    }

    /**
     * The display variants of a pack (ZIP or .mcpack); empty for packs without them, including packs of older
     * Twilight versions, whose variants use another layout.
     */
    public static DisplayIndex read(Path path) throws IOException {
        if (!Files.isRegularFile(path)) return EMPTY;
        try (ZipFile zip = new ZipFile(path.toFile())) {
            var entry = zip.getEntry(INDEX);
            if (entry == null) return EMPTY;
            if (entry.getSize() > MAX_INDEX_CHARS) throw new IOException("Display index exceeds size limit");
            try (Reader reader = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
                StringBuilder text = new StringBuilder();
                char[] buffer = new char[8192];
                for (int read; (read = reader.read(buffer)) >= 0;) {
                    text.append(buffer, 0, read);
                    // The declared size can lie; the inflated text is bounded as well.
                    if (text.length() > MAX_INDEX_CHARS) throw new IOException("Display index exceeds size limit");
                }
                return parse(text.toString());
            }
        }
    }

    /**
     * Validates an index: format 2, Bedrock identifiers, distinct ordinals within range, finite transforms within
     * Java's limits. A pack is input from a backend (or a file a proxy owner placed), so nothing is trusted.
     */
    static DisplayIndex parse(String json) throws IOException {
        try {
            var object = JsonParser.parseString(json).getAsJsonObject();
            if (!object.has("format") || object.get("format").getAsInt() != 2) return EMPTY;
            var table = object.getAsJsonArray("transforms");
            if (table.size() > MAX_VARIANTS) throw new IOException("Invalid display transforms");
            List<float[]> transforms = new ArrayList<>(table.size());
            for (var entry : table) {
                var values = entry.getAsJsonArray();
                if (values.size() != 81) throw new IOException("Invalid display transform");
                float[] contexts = new float[81];
                for (int i = 0; i < 81; i++) {
                    float value = values.get(i).getAsFloat();
                    // Translation within Java's +-80, scale within +-4; rotations are any finite angle.
                    float limit = switch (i % 9 / 3) { case 0 -> 80f; case 1 -> 100_000f; default -> 4f; };
                    if (!Float.isFinite(value) || Math.abs(value) > limit) throw new IOException("Invalid display transform");
                    contexts[i] = value;
                }
                transforms.add(contexts);
            }
            Set<Integer> used = new HashSet<>();
            Map<String, Variant> items = variants(object.getAsJsonObject("variants"), transforms, used);
            Map<String, Variant> models = object.has("models")
                    ? variants(object.getAsJsonObject("models"), transforms, used) : Map.of();
            Map<String, Variant> vanilla = object.has("vanilla")
                    ? variants(object.getAsJsonObject("vanilla"), transforms, used) : Map.of();
            Map<String, Variant> blocks = object.has("blocks")
                    ? variants(object.getAsJsonObject("blocks"), transforms, used) : Map.of();
            Map<String, Variant> heads = object.has("heads")
                    ? variants(object.getAsJsonObject("heads"), transforms, used) : Map.of();
            Set<Integer> flat = new HashSet<>();
            if (object.has("flat")) for (var value : object.getAsJsonArray("flat")) {
                int ordinal = value.getAsInt();
                if (!used.contains(ordinal) || flat.size() >= MAX_VARIANTS) throw new IOException("Invalid flat display variant");
                flat.add(ordinal);
            }
            return new DisplayIndex(items, models, vanilla, blocks, Set.copyOf(flat), heads);
        } catch (RuntimeException malformed) {
            throw new IOException("Malformed display variant index", malformed);
        }
    }

    private static Map<String, Variant> variants(com.google.gson.JsonObject section, List<float[]> transforms,
                                                  Set<Integer> used) throws IOException {
        Map<String, Variant> result = new LinkedHashMap<>();
        for (var value : section.entrySet()) {
            var entry = value.getValue().getAsJsonArray();
            if (entry.size() != 2) throw new IOException("Invalid display variant index");
            int ordinal = entry.get(0).getAsInt(), transform = entry.get(1).getAsInt();
            if (ordinal < 0 || ordinal >= MAX_VARIANTS || !used.add(ordinal)
                    || transform < -1 || transform >= transforms.size()
                    || !(IDENTIFIER.matcher(value.getKey()).matches()
                         || value.getKey().startsWith("block=") && DisplayKeys.valid(value.getKey())
                         || com.siberanka.twilight.geyser.HeadTextures.valid(value.getKey()))) {
                throw new IOException("Invalid display variant index");
            }
            result.put(value.getKey(), new Variant(ordinal, transform < 0 ? null : transforms.get(transform)));
        }
        return Map.copyOf(result);
    }

    private static final java.util.regex.Pattern IDENTIFIER = java.util.regex.Pattern.compile("[a-z0-9_.-]{1,64}:[a-z0-9_./-]{1,256}");
}
