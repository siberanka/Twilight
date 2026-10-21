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
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Decides when a custom flat item is only a recolour of Mojang's item. */
final class VanillaItemReference {
    private static final List<String> BOW = List.of(
            "item/bow", "item/bow_pulling_0", "item/bow_pulling_1", "item/bow_pulling_2");
    private static final List<String> CROSSBOW = List.of(
            "item/crossbow", "item/crossbow_pulling_0", "item/crossbow_pulling_1",
            "item/crossbow_pulling_2", "item/crossbow_arrow", "item/crossbow_firework");

    private final ResourceIndex resources;
    private final VanillaAssetCache vanilla;
    private final Map<String, ReferenceModel> models = new LinkedHashMap<>();

    VanillaItemReference(ResourceIndex resources, VanillaAssetCache vanilla) {
        this.resources = resources;
        this.vanilla = vanilla;
    }

    boolean matchesWeaponState(String type, int state, ResolvedJavaModel candidate) throws IOException {
        List<String> states = type.equals("bow") ? BOW : CROSSBOW;
        if (state < 0 || state >= states.size()) return false;
        return matches(states.get(state), candidate);
    }

    boolean matchesFishingRod(boolean cast, ResolvedJavaModel candidate) throws IOException {
        return matches(cast ? "item/fishing_rod_cast" : "item/fishing_rod", candidate);
    }

    private boolean matches(String modelIdentifier, ResolvedJavaModel candidate) throws IOException {
        if (vanilla == null || candidate.isThreeDimensional()) return false;
        List<String> candidateLayers = BedrockPackCompiler.flatLayers(candidate);
        if (candidateLayers.size() != 1) return false;
        ReferenceModel baseline = models.get(modelIdentifier);
        if (baseline == null) {
            baseline = resolve(modelIdentifier);
            models.put(modelIdentifier, baseline);
        }
        if (!candidate.display().equals(baseline.display()) || baseline.layers().size() != 1) return false;
        String customPath = JavaModelResolver.texturePath(TextureSet.spriteTexture(resources, candidateLayers.getFirst()));
        String baselineTexture = baseline.layers().getFirst();
        String baselinePath = JavaModelResolver.texturePath(baselineTexture);
        // Animated sprites change more than colour even if their first frame
        // happens to share the vanilla alpha mask.
        if (resources.find(customPath + ".mcmeta").isPresent() ||
                vanilla.readTexture(baselinePath + ".mcmeta").isPresent()) return false;
        byte[] customBytes = resources.find(customPath)
                .orElseThrow(() -> new IOException("Missing texture " + candidateLayers.getFirst())).readBytes();
        byte[] vanillaBytes = vanilla.readTexture(baselinePath)
                .orElseThrow(() -> new IOException("Vanilla texture is absent: " + baselineTexture));
        return sameSilhouette(image(customBytes, candidateLayers.getFirst()),
                image(vanillaBytes, baseline.layers().getFirst()));
    }

    private ReferenceModel resolve(String identifier) throws IOException {
        try {
            String current = JavaModelResolver.qualified(identifier, "minecraft");
            ArrayDeque<JsonObject> chain = new ArrayDeque<>();
            Set<String> seen = new HashSet<>();
            for (int depth = 0; depth < 64; depth++) {
                if (!seen.add(current)) throw new IOException("Vanilla model parent cycle: " + current);
                JsonObject json = JavaModelResolver.builtinModel(current);
                if (json == null) {
                    String requested = current;
                    byte[] bytes = vanilla.readModel(JavaModelResolver.modelPath(requested))
                            .orElseThrow(() -> new IOException("Vanilla model is absent: " + requested));
                    json = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
                }
                chain.addFirst(json);
                if (!json.has("parent")) break;
                current = JavaModelResolver.qualified(json.get("parent").getAsString(), "minecraft");
                if (current.startsWith("minecraft:builtin/") && JavaModelResolver.builtinModel(current) == null) break;
            }
            Map<String, String> textures = new LinkedHashMap<>();
            JsonObject display = new JsonObject();
            for (JsonObject json : chain) {
                if (json.has("textures")) for (Map.Entry<String, JsonElement> texture
                        : json.getAsJsonObject("textures").entrySet()) {
                    String sprite = JavaModelResolver.textureValue(texture.getValue());
                    if (sprite != null) textures.put(texture.getKey(), sprite);
                }
                if (json.has("display")) for (Map.Entry<String, JsonElement> transform
                        : json.getAsJsonObject("display").entrySet()) {
                    display.add(transform.getKey(), transform.getValue().deepCopy());
                }
            }
            Map<String, String> resolved = new LinkedHashMap<>();
            for (int layer = 0; layer < 32; layer++) {
                String key = "layer" + layer;
                if (!textures.containsKey(key)) break;
                resolved.put(key, resolveTexture(key, textures));
            }
            return new ReferenceModel(List.copyOf(resolved.values()), display);
        } catch (RuntimeException invalid) {
            throw new IOException("Invalid vanilla item model " + identifier, invalid);
        }
    }

    private static String resolveTexture(String key, Map<String, String> textures) throws IOException {
        String value = textures.get(key);
        Set<String> seen = new HashSet<>();
        while (value != null && value.startsWith("#")) {
            String reference = value.substring(1);
            if (!seen.add(reference)) throw new IOException("Vanilla texture reference cycle at #" + reference);
            value = textures.get(reference);
        }
        if (value == null) throw new IOException("Missing vanilla texture reference #" + key);
        return JavaModelResolver.qualified(value, "minecraft");
    }

    private static BufferedImage image(byte[] bytes, String name) throws IOException {
        BufferedImage image = PngImages.read(bytes);
        if (image == null || image.getWidth() < 1 || image.getHeight() < 1) {
            throw new IOException("Invalid PNG " + name);
        }
        return image;
    }

    static boolean sameSilhouette(BufferedImage candidate, BufferedImage baseline) {
        if (candidate.getWidth() != baseline.getWidth() || candidate.getHeight() != baseline.getHeight()) return false;
        for (int y = 0; y < baseline.getHeight(); y++) for (int x = 0; x < baseline.getWidth(); x++) {
            if ((candidate.getRGB(x, y) >>> 24 == 0) != (baseline.getRGB(x, y) >>> 24 == 0)) return false;
        }
        return true;
    }

    private record ReferenceModel(List<String> layers, JsonObject display) {}

}
