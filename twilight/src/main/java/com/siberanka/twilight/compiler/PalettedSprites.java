/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.compiler;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.siberanka.twilight.source.ResourceIndex;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.regex.Pattern;

/**
 * Sprites Java's atlases generate by recolouring ({@code paletted_permutations}): armour trims on items
 * ({@code trims/items/helmet_trim_gold} is {@code trims/items/helmet_trim} with the gold palette). No file holds
 * them, so a model naming one (trimmed custom armour of ItemsAdder, CraftEngine and other packs) would get the missing
 * texture; the sprite is built here the way Java builds it: every pixel whose colour is in the key palette takes the
 * colour at the same place in the permutation's palette, keeping its own alpha.
 *
 * <p>The atlases of the vanilla client and of every pack are read. Palette identifiers are looked up as Java 26
 * names them ({@code textures/palettes/<id>.png}) and as older versions did ({@code textures/<id>.png}).
 */
final class PalettedSprites {
    private static final Pattern ATLAS = Pattern.compile("assets/[^/]+/atlases/[^/]+\\.json");
    private static final String[] VANILLA_ATLASES = {"items", "blocks", "armor_trims", "gui"};
    /** The atlas sources of a build's resources (read once per build). */
    private static final Map<ResourceIndex, List<Source>> SOURCES = new WeakHashMap<>();

    record Source(List<String> textures, String paletteKey, Map<String, String> permutations, String separator) {}

    private PalettedSprites() {}

    /** The generated sprite {@code identifier} ({@code namespace:path}), when an atlas generates it. */
    static Optional<BufferedImage> sprite(ResourceIndex resources, VanillaAssetCache vanilla, String identifier) throws IOException {
        String sprite = JavaModelResolver.qualified(identifier, "minecraft");
        for (Source source : sources(resources, vanilla)) {
            for (String texture : source.textures()) {
                String prefix = texture + source.separator();
                if (!sprite.startsWith(prefix)) continue;
                String permutation = source.permutations().get(sprite.substring(prefix.length()));
                if (permutation == null) continue;
                BufferedImage base = image(resources, vanilla, JavaModelResolver.texturePath(texture));
                BufferedImage key = palette(resources, vanilla, source.paletteKey());
                BufferedImage colours = palette(resources, vanilla, permutation);
                if (base == null || key == null || colours == null) return Optional.empty();
                return Optional.of(recolour(base, key, colours));
            }
        }
        return Optional.empty();
    }

    /** Java's palette swap: key colour at index i becomes the permutation colour at index i, alpha multiplied. */
    static BufferedImage recolour(BufferedImage base, BufferedImage key, BufferedImage colours) {
        Map<Integer, Integer> mapping = new HashMap<>();
        int count = Math.min(key.getWidth() * key.getHeight(), colours.getWidth() * colours.getHeight());
        for (int i = 0; i < count; i++) {
            int from = key.getRGB(i % key.getWidth(), i / key.getWidth());
            int to = colours.getRGB(i % colours.getWidth(), i / colours.getWidth());
            mapping.putIfAbsent(from | 0xFF000000, to);
        }
        BufferedImage result = new BufferedImage(base.getWidth(), base.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < base.getHeight(); y++) {
            for (int x = 0; x < base.getWidth(); x++) {
                int pixel = base.getRGB(x, y);
                int alpha = pixel >>> 24;
                if (alpha == 0) {
                    result.setRGB(x, y, 0);
                    continue;
                }
                Integer mapped = mapping.get(pixel | 0xFF000000);
                if (mapped == null) {
                    result.setRGB(x, y, pixel);
                } else {
                    int mappedAlpha = mapped >>> 24;
                    result.setRGB(x, y, (alpha * mappedAlpha / 255) << 24 | mapped & 0xFFFFFF);
                }
            }
        }
        return result;
    }

    private static BufferedImage palette(ResourceIndex resources, VanillaAssetCache vanilla, String identifier) throws IOException {
        String qualified = JavaModelResolver.qualified(identifier, "minecraft");
        int colon = qualified.indexOf(':');
        String namespace = qualified.substring(0, colon), path = qualified.substring(colon + 1);
        BufferedImage modern = image(resources, vanilla, "assets/" + namespace + "/textures/palettes/" + path + ".png");
        return modern != null ? modern : image(resources, vanilla, "assets/" + namespace + "/textures/" + path + ".png");
    }

    private static BufferedImage image(ResourceIndex resources, VanillaAssetCache vanilla, String path) throws IOException {
        if (path.contains("..")) return null;
        var asset = resources.find(path);
        byte[] bytes = null;
        if (asset.isPresent()) bytes = asset.get().readBytes();
        else if (vanilla != null && path.startsWith("assets/minecraft/textures/")) bytes = vanilla.readTexture(path).orElse(null);
        if (bytes == null) return null;
        BufferedImage image = PngImages.read(bytes);
        return image == null || image.getWidth() <= 0 || image.getHeight() <= 0 || image.getWidth() * (long) image.getHeight() > 4_194_304
                ? null : image;
    }

    private static List<Source> sources(ResourceIndex resources, VanillaAssetCache vanilla) {
        synchronized (SOURCES) {
            List<Source> known = SOURCES.get(resources);
            if (known != null) return known;
        }
        List<Source> sources = new ArrayList<>();
        if (vanilla != null) {
            for (String atlas : VANILLA_ATLASES) {
                try {
                    var bytes = vanilla.readAtlas("assets/minecraft/atlases/" + atlas + ".json");
                    if (bytes.isPresent()) read(new String(bytes.get(), StandardCharsets.UTF_8), sources);
                } catch (IOException | RuntimeException ignored) {
                    // An atlas this client version does not have.
                }
            }
        }
        for (String path : resources.paths().stream().filter(value -> ATLAS.matcher(value).matches()).sorted().toList()) {
            for (ResourceIndex.Asset asset : resources.findAll(path)) {
                try {
                    read(asset.readUtf8(), sources);
                } catch (IOException | RuntimeException ignored) {
                    // Java skips a broken atlas definition.
                }
            }
        }
        List<Source> result = List.copyOf(sources);
        synchronized (SOURCES) {
            SOURCES.put(resources, result);
        }
        return result;
    }

    private static void read(String json, List<Source> sources) {
        JsonElement list = JsonParser.parseString(json).getAsJsonObject().get("sources");
        if (list == null || !list.isJsonArray()) return;
        for (JsonElement element : list.getAsJsonArray()) {
            if (!element.isJsonObject() || sources.size() >= 1024) continue;
            JsonObject source = element.getAsJsonObject();
            String type = source.has("type") ? source.get("type").getAsString() : "";
            if (!type.endsWith("paletted_permutations") || !source.has("textures") || !source.has("palette_key")
                    || !source.has("permutations") || !source.get("permutations").isJsonObject()) continue;
            List<String> textures = new ArrayList<>();
            for (JsonElement texture : source.getAsJsonArray("textures")) {
                if (textures.size() < 256) textures.add(JavaModelResolver.qualified(texture.getAsString(), "minecraft"));
            }
            Map<String, String> permutations = new LinkedHashMap<>();
            for (var entry : source.getAsJsonObject("permutations").entrySet()) {
                if (permutations.size() < 256) permutations.put(entry.getKey(), entry.getValue().getAsString());
            }
            String separator = source.has("separator") ? source.get("separator").getAsString() : "_";
            sources.add(new Source(List.copyOf(textures), source.get("palette_key").getAsString(), Map.copyOf(permutations), separator));
        }
    }
}
