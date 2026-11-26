/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.geyser;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

/**
 * The wings a Bedrock player's own custom elytra can show. Geyser sends the player's own glider to Bedrock as the
 * vanilla elytra (the client only glides with it), so the vanilla elytra's attachable picks its texture from an
 * entity property of the wearer instead: {@link #PROPERTY} is 0 for vanilla wings and {@code n} for the n-th
 * wings texture listed in {@link #PATH}. The pack lists the Bedrock items of each wings texture; the runtime
 * sets the property from the item a player wears.
 */
public final class ElytraWings {
    public static final String PATH = "twilight/elytra_wings.json";
    public static final String PROPERTY = "twilight:elytra";
    /** Wings textures a pack can list; 0 is the vanilla wings. */
    public static final int MAX = 31;
    private static final long MAX_BYTES = 1024 * 1024;
    private static final int FORMAT = 1;
    public static final ElytraWings EMPTY = new ElytraWings(Map.of());

    private final Map<String, Integer> indices;

    private ElytraWings(Map<String, Integer> indices) {
        this.indices = indices;
    }

    public boolean isEmpty() {
        return indices.isEmpty();
    }

    /** The property value for a worn Bedrock item: its wings' index, or 0 (vanilla wings). */
    public int indexOf(String bedrockIdentifier) {
        Integer index = bedrockIdentifier == null ? null : indices.get(bedrockIdentifier);
        return index == null ? 0 : index;
    }

    /** The file for wings textures in index order (the first is index 1) with the Bedrock items that wear each. */
    public static String json(List<List<String>> items) {
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT);
        JsonArray wings = new JsonArray();
        for (List<String> group : items) {
            JsonArray array = new JsonArray();
            for (String identifier : group) array.add(identifier);
            wings.add(array);
        }
        root.add("wings", wings);
        return root.toString();
    }

    /** Validates a file from a pack: a pack is input, so nothing is trusted. */
    public static ElytraWings parse(String json) throws IOException {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (!root.has("format") || root.get("format").getAsInt() != FORMAT) throw new IOException("Unsupported elytra wings format");
            JsonArray wings = root.getAsJsonArray("wings");
            if (wings.size() > MAX) throw new IOException("More than " + MAX + " wings textures");
            Map<String, Integer> indices = new LinkedHashMap<>();
            int index = 1;
            for (JsonElement group : wings) {
                for (JsonElement identifier : group.getAsJsonArray()) {
                    String value = identifier.getAsString();
                    if (!value.startsWith(WornElytra.CUSTOM_PREFIX) || value.length() > 128) throw new IOException("Invalid identifier " + value);
                    indices.putIfAbsent(value, index);
                }
                index++;
            }
            return new ElytraWings(Map.copyOf(indices));
        } catch (RuntimeException invalid) {
            throw new IOException("Invalid elytra wings file", invalid);
        }
    }

    /** The wings of a pack, or {@link #EMPTY} when it has none. */
    public static ElytraWings read(Path pack) throws IOException {
        if (!Files.isRegularFile(pack)) return EMPTY;
        try (ZipFile zip = new ZipFile(pack.toFile())) {
            var entry = zip.getEntry(PATH);
            if (entry == null) return EMPTY;
            try (InputStream in = zip.getInputStream(entry)) {
                byte[] bytes = in.readNBytes((int) MAX_BYTES + 1);
                if (bytes.length > MAX_BYTES) throw new IOException("Elytra wings file exceeds size limit");
                return parse(new String(bytes, StandardCharsets.UTF_8));
            }
        }
    }
}
