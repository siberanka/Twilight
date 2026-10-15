/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.proxy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The proxy's Geyser registers custom items once, at start, for every server behind the proxy. Twilight
 * puts each backend's Geyser item mappings into the pack it shares; this class reads them from the packs,
 * keeps only well-formed Twilight entries, merges them (the same Java selector is the same Bedrock item on
 * every backend) and reports selectors that two servers map differently.
 */
final class ItemMappings {
    /** Where Twilight puts the mappings inside its pack. */
    static final String PACK_ENTRY = "twilight/geyser_item_mappings.json";
    /** The merged file in Geyser's {@code custom_mappings} folder. */
    static final String FILE = "twilight-proxy_item_mappings.json";

    private static final long MAX_ENTRY_BYTES = 8L * 1024 * 1024;
    private static final int MAX_MAPPINGS = 50_000;
    private static final int MAX_PREDICATE_CHARS = 4096;
    private static final Pattern JAVA_ITEM = Pattern.compile("minecraft:[a-z0-9_./-]{1,128}");
    private static final Pattern MODEL = Pattern.compile("[a-z0-9_.-]{1,64}:[a-z0-9_./-]{1,256}");
    private static final Pattern BEDROCK_ITEM = Pattern.compile("twilight:[a-z0-9._-]{1,80}");
    private static final Pattern ICON = Pattern.compile("[a-z0-9._-]{1,128}");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** Merged mappings, the selectors two servers map differently, and the entries that were dropped. */
    /**
     * {@code conflicts}: selectors two servers map to different Bedrock items (one server shows a wrong item);
     * {@code optionDifferences}: selectors with the same Bedrock item but other options (held like a tool, icon),
     * where the first server's options are used.
     */
    record Merge(JsonObject mappings, int count, List<String> conflicts, int invalid, int optionDifferences) {
        byte[] bytes() {
            return (GSON.toJson(mappings) + "\n").getBytes(StandardCharsets.UTF_8);
        }
    }

    private ItemMappings() {}

    /** The mappings Twilight put into {@code pack}, if it has any. */
    static Optional<JsonObject> read(Path pack) throws IOException {
        try (ZipFile zip = new ZipFile(pack.toFile())) {
            ZipEntry entry = zip.getEntry(PACK_ENTRY);
            if (entry == null || entry.isDirectory()) return Optional.empty();
            try (InputStream in = zip.getInputStream(entry)) {
                byte[] bytes = in.readNBytes((int) MAX_ENTRY_BYTES + 1);
                if (bytes.length > MAX_ENTRY_BYTES) throw new IOException(PACK_ENTRY + " is larger than 8 MiB");
                JsonElement root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
                if (!root.isJsonObject()) throw new IOException(PACK_ENTRY + " is not a JSON object");
                return Optional.of(root.getAsJsonObject());
            } catch (RuntimeException malformed) {
                throw new IOException(PACK_ENTRY + " is not valid JSON");
            }
        }
    }

    /** Merges the mappings of each server (in server-name order; the first mapping of a selector wins). */
    static Merge merge(Map<String, JsonObject> servers) {
        Map<String, JsonArray> items = new TreeMap<>();
        Map<String, Seen> selectors = new HashMap<>();
        Map<String, String> identifiers = new HashMap<>();
        List<String> conflicts = new ArrayList<>();
        int count = 0, invalid = 0, optionDifferences = 0;
        for (var server : new TreeMap<>(servers).entrySet()) {
            JsonObject root = server.getValue();
            if (!root.has("format_version") || !root.get("format_version").isJsonPrimitive()
                    || root.get("format_version").getAsInt() != 2 || !root.has("items") || !root.get("items").isJsonObject()) {
                invalid++;
                continue;
            }
            for (var item : root.getAsJsonObject("items").entrySet()) {
                if (!JAVA_ITEM.matcher(item.getKey()).matches() || !item.getValue().isJsonArray()) {
                    invalid++;
                    continue;
                }
                for (JsonElement element : item.getValue().getAsJsonArray()) {
                    JsonObject mapping = clean(element);
                    if (mapping == null) {
                        invalid++;
                        continue;
                    }
                    String selector = selector(item.getKey(), mapping);
                    String identifier = mapping.get("bedrock_identifier").getAsString();
                    Seen seen = selectors.get(selector);
                    if (seen != null) {
                        if (seen.mapping().get("bedrock_identifier").getAsString().equals(identifier)) {
                            if (!seen.mapping().equals(mapping)) optionDifferences++;
                        } else {
                            conflicts.add(describe(item.getKey(), mapping) + ": " + seen.server() + " -> "
                                    + seen.mapping().get("bedrock_identifier").getAsString() + ", " + server.getKey() + " -> "
                                    + identifier + " (kept " + seen.server() + ")");
                        }
                        continue;
                    }
                    String owner = identifiers.get(identifier);
                    if (owner != null && !owner.equals(selector)) {
                        conflicts.add(identifier + " is used for two selectors; " + server.getKey() + "'s "
                                + describe(item.getKey(), mapping) + " was dropped");
                        continue;
                    }
                    if (count >= MAX_MAPPINGS) {
                        invalid++;
                        continue;
                    }
                    selectors.put(selector, new Seen(server.getKey(), mapping));
                    identifiers.put(identifier, selector);
                    items.computeIfAbsent(item.getKey(), key -> new JsonArray()).add(mapping);
                    count++;
                }
            }
        }
        JsonObject merged = new JsonObject();
        merged.addProperty("format_version", 2);
        JsonObject itemsJson = new JsonObject();
        items.forEach(itemsJson::add);
        merged.add("items", itemsJson);
        return new Merge(merged, count, List.copyOf(conflicts), invalid, optionDifferences);
    }

    private record Seen(String server, JsonObject mapping) {}

    /** A copy with only the keys Geyser's version 2 item mappings use, or null when anything is off. */
    static JsonObject clean(JsonElement element) {
        try {
            if (!element.isJsonObject()) return null;
            JsonObject source = element.getAsJsonObject();
            JsonObject copy = new JsonObject();
            String type = source.get("type").getAsString();
            copy.addProperty("type", type);
            if (type.equals("legacy")) {
                int data = source.get("custom_model_data").getAsInt();
                copy.addProperty("custom_model_data", data);
            } else if (type.equals("definition")) {
                String model = source.get("model").getAsString();
                if (!MODEL.matcher(model).matches()) return null;
                copy.addProperty("model", model);
            } else {
                return null;
            }
            String identifier = source.get("bedrock_identifier").getAsString();
            if (!BEDROCK_ITEM.matcher(identifier).matches()) return null;
            copy.addProperty("bedrock_identifier", identifier);
            if (source.has("predicate")) {
                JsonElement predicate = source.get("predicate");
                boolean objects = predicate.isJsonObject();
                if (predicate.isJsonArray()) {
                    objects = true;
                    for (JsonElement part : predicate.getAsJsonArray()) objects &= part.isJsonObject();
                }
                if (!objects || predicate.toString().length() > MAX_PREDICATE_CHARS) return null;
                copy.add("predicate", predicate.deepCopy());
            }
            if (source.has("predicate_strategy")) {
                String strategy = source.get("predicate_strategy").getAsString();
                if (!strategy.equals("and") && !strategy.equals("or")) return null;
                copy.addProperty("predicate_strategy", strategy);
            }
            if (source.has("priority")) copy.addProperty("priority", source.get("priority").getAsInt());
            JsonObject options = new JsonObject();
            if (source.has("bedrock_options")) {
                JsonObject given = source.getAsJsonObject("bedrock_options");
                if (given.has("icon")) {
                    String icon = given.get("icon").getAsString();
                    if (!ICON.matcher(icon).matches()) return null;
                    options.addProperty("icon", icon);
                }
                if (given.has("display_handheld")) options.addProperty("display_handheld", given.get("display_handheld").getAsBoolean());
            }
            copy.add("bedrock_options", options);
            return copy;
        } catch (RuntimeException malformed) {
            return null;
        }
    }

    /** What Geyser matches on: the Java item, custom model data or item model, and the predicates. */
    private static String selector(String item, JsonObject mapping) {
        return item + '|' + mapping.get("type").getAsString() + '|'
                + (mapping.has("custom_model_data") ? mapping.get("custom_model_data").getAsString() : mapping.get("model").getAsString())
                + '|' + (mapping.has("predicate") ? mapping.get("predicate").toString() : "")
                + '|' + (mapping.has("predicate_strategy") ? mapping.get("predicate_strategy").getAsString() : "");
    }

    private static String describe(String item, JsonObject mapping) {
        return item + (mapping.has("custom_model_data") ? " custom_model_data " + mapping.get("custom_model_data").getAsInt()
                : " model " + mapping.get("model").getAsString())
                + (mapping.has("predicate") ? " " + mapping.get("predicate") : "");
    }

    /**
     * Writes the merged mappings into {@code custom_mappings} when they differ from the file there. Returns
     * true when the file changed.
     */
    static boolean write(Path customMappings, Merge merge) throws IOException {
        Files.createDirectories(customMappings);
        Path target = customMappings.resolve(FILE);
        byte[] bytes = merge.bytes();
        if (Files.isRegularFile(target) && java.util.Arrays.equals(Files.readAllBytes(target), bytes)) return false;
        Path temporary = customMappings.resolve(FILE + ".tmp");
        Files.write(temporary, bytes);
        try {
            Files.move(temporary, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return true;
    }

    /**
     * How many selectors of another mappings file map to a different Bedrock item than {@code merge} (0 when the
     * file cannot be read).
     */
    static int clashes(Path file, Merge merge) {
        JsonObject other;
        try {
            if (Files.size(file) > MAX_ENTRY_BYTES) return 0;
            JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (!root.isJsonObject() || !root.getAsJsonObject().has("items")) return 0;
            other = root.getAsJsonObject().getAsJsonObject("items");
        } catch (IOException | RuntimeException unreadable) {
            return 0;
        }
        Map<String, String> ours = new HashMap<>();
        for (var item : merge.mappings().getAsJsonObject("items").entrySet()) {
            for (JsonElement mapping : item.getValue().getAsJsonArray()) {
                JsonObject entry = mapping.getAsJsonObject();
                ours.put(selector(item.getKey(), entry), entry.get("bedrock_identifier").getAsString());
            }
        }
        int clashes = 0;
        for (var item : other.entrySet()) {
            if (!item.getValue().isJsonArray()) continue;
            for (JsonElement element : item.getValue().getAsJsonArray()) {
                JsonObject entry = clean(element);
                if (entry == null) {
                    entry = cleanForeign(element);
                    if (entry == null) continue;
                }
                String identifier = ours.get(selector(item.getKey(), entry));
                if (identifier != null && !identifier.equals(entry.get("bedrock_identifier").getAsString())) clashes++;
            }
        }
        return clashes;
    }

    /** The selector part of a mapping whose Bedrock identifier is not a Twilight one (an older tool's). */
    private static JsonObject cleanForeign(JsonElement element) {
        try {
            JsonObject source = element.getAsJsonObject();
            JsonObject copy = source.deepCopy();
            copy.addProperty("bedrock_identifier", "twilight:foreign");
            JsonObject cleaned = clean(copy);
            if (cleaned != null) cleaned.addProperty("bedrock_identifier", source.get("bedrock_identifier").getAsString());
            return cleaned;
        } catch (RuntimeException malformed) {
            return null;
        }
    }

    /** Twilight mapping files copied into the proxy's Geyser by hand, which would register the items twice. */
    static List<String> copies(Path customMappings) throws IOException {
        if (!Files.isDirectory(customMappings)) return List.of();
        try (var files = Files.list(customMappings)) {
            return files.map(file -> file.getFileName().toString())
                    .filter(name -> name.startsWith("twilight_") && name.endsWith(".json"))
                    .sorted().toList();
        }
    }
}
