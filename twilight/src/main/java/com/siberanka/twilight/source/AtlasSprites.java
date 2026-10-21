/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.source;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Sprite names that Java's texture atlases give to texture files.
 *
 * <p>A model refers to atlas sprites, not files. Usually a sprite is named after its file, but
 * an atlas definition ({@code assets/<namespace>/atlases/<atlas>.json}) may rename it:
 * ItemsAdder's generated packs, for example, reference {@code "ia:2015"} and declare
 * {@code {"type":"single","resource":"iasurvival:item/lettuce","sprite":"ia:2015"}}. Java
 * loads every pack's definition of an atlas and appends their sources; this class resolves
 * {@code single} and {@code directory} sources the same way. Generated sources (unstitch,
 * paletted permutations) and filters are not modelled.
 */
public final class AtlasSprites {
    private static final Pattern ATLAS = Pattern.compile("assets/[^/]+/atlases/[^/]+\\.json");

    private final Map<String, String> singles;
    private final List<Directory> directories;

    private AtlasSprites(Map<String, String> singles, List<Directory> directories) {
        this.singles = singles;
        this.directories = directories;
    }

    /** Reads every atlas definition of every pack; unreadable definitions are skipped like Java skips them. */
    public static AtlasSprites read(ResourceIndex resources) {
        Map<String, String> singles = new LinkedHashMap<>();
        List<Directory> directories = new ArrayList<>();
        for (String path : resources.paths().stream().filter(value -> ATLAS.matcher(value).matches()).sorted().toList()) {
            // Lower-priority packs first: Java appends each pack's sources in load order.
            for (ResourceIndex.Asset asset : resources.findAll(path).reversed()) {
                try {
                    JsonElement sources = JsonParser.parseString(asset.readUtf8()).getAsJsonObject().get("sources");
                    if (sources == null || !sources.isJsonArray()) continue;
                    for (JsonElement element : sources.getAsJsonArray()) {
                        if (!element.isJsonObject()) continue;
                        JsonObject source = element.getAsJsonObject();
                        String type = string(source, "type");
                        type = type.substring(type.indexOf(':') + 1);
                        if (type.equals("single") && source.has("resource")) {
                            String resource = qualified(string(source, "resource"));
                            String sprite = source.has("sprite") ? qualified(string(source, "sprite")) : resource;
                            if (!sprite.equals(resource)) singles.put(sprite, resource);
                        } else if (type.equals("directory") && source.has("source")) {
                            String prefix = source.has("prefix") ? string(source, "prefix") : "";
                            String directory = string(source, "source");
                            if (!(directory + "/").equals(prefix)) directories.add(new Directory(directory, prefix));
                        }
                    }
                } catch (IOException | RuntimeException ignored) {
                    // Java logs and skips a broken atlas definition.
                }
            }
        }
        return new AtlasSprites(Map.copyOf(singles), List.copyOf(directories));
    }

    /** The texture a renamed sprite stands for ({@code namespace:path}), if an atlas renames it. */
    public Optional<String> texture(String sprite) {
        String qualified = qualified(sprite);
        String single = singles.get(qualified);
        if (single != null) return Optional.of(single);
        int colon = qualified.indexOf(':');
        String namespace = qualified.substring(0, colon), path = qualified.substring(colon + 1);
        for (Directory directory : directories) {
            if (path.startsWith(directory.prefix())) {
                return Optional.of(namespace + ':' + directory.source() + '/' + path.substring(directory.prefix().length()));
            }
        }
        return Optional.empty();
    }

    public int renamedSprites() {
        return singles.size();
    }

    private static String qualified(String identifier) {
        return identifier.indexOf(':') >= 0 ? identifier : "minecraft:" + identifier;
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
    }

    private record Directory(String source, String prefix) {}
}
