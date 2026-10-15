/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.compiler;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.siberanka.twilight.source.ResourceIndex;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Converts layered Java sound registries and their OGG assets to Bedrock definitions. */
final class SoundCompiler {
    private final ResourceIndex resources;
    private final boolean vanillaOverride;
    private final VanillaAssetCache vanillaAssets;
    private final Map<String, JavaSound> definitions = new LinkedHashMap<>();
    private final Set<String> problems = new LinkedHashSet<>();
    // Definitions that are broken for Java players too; reported without failing a strict build.
    private final Set<String> notices = new LinkedHashSet<>();
    private JsonObject vanillaSoundRegistry;
    private boolean vanillaRegistryAttempted;

    SoundCompiler(ResourceIndex resources, boolean vanillaOverride, VanillaAssetCache vanillaAssets) {
        this.resources = resources;
        this.vanillaOverride = vanillaOverride;
        this.vanillaAssets = vanillaAssets;
    }

    Result compile(Map<String, byte[]> packFiles) {
        readDefinitions();
        JsonObject bedrockDefinitions = new JsonObject();
        Set<String> copiedFiles = new HashSet<>();
        Set<String> vanillaFallbackFiles = new HashSet<>();
        for (Map.Entry<String, JavaSound> entry : definitions.entrySet()) {
            if (!shouldCompile(entry.getKey(), entry.getValue())) continue;
            List<Variant> variants = resolveEvent(entry.getKey(), new HashSet<>());
            if (variants.isEmpty()) continue;
            JsonArray sounds = new JsonArray();
            Double maximumDistance = null;
            boolean conflictingDistance = false;
            for (Variant variant : variants) {
                String outputPath = "sounds/" + variant.file().replace(':', '/');
                String assetPath = javaSoundPath(variant.file());
                var asset = resources.find(assetPath);
                try {
                    byte[] bytes;
                    if (asset.isPresent()) bytes = asset.get().readBytes();
                    else if (variant.file().startsWith("minecraft:") && vanillaAssets != null) {
                        var vanilla = vanillaAssets.readSound(assetPath);
                        if (vanilla.isEmpty()) {
                            notices.add(entry.getKey() + " references a sound file that exists nowhere, as on Java: "
                                    + variant.file());
                            continue;
                        }
                        bytes = vanilla.get();
                        vanillaFallbackFiles.add(variant.file());
                    } else if (variant.file().startsWith("minecraft:")) {
                        // Without the vanilla client the file cannot be checked against Java's assets.
                        problem(entry.getKey() + " references missing sound " + variant.file());
                        continue;
                    } else {
                        notices.add(entry.getKey() + " references a sound file that exists nowhere, as on Java: "
                                + variant.file());
                        continue;
                    }
                    if (bytes.length < 4 || bytes[0] != 'O' || bytes[1] != 'g' || bytes[2] != 'g' || bytes[3] != 'S') {
                        problem(entry.getKey() + " references invalid OGG data " + variant.file());
                        continue;
                    }
                    if (copiedFiles.add(outputPath)) packFiles.put(outputPath + ".ogg", bytes);
                } catch (IOException failure) {
                    problem(entry.getKey() + " could not read " + variant.file() + ": " + message(failure));
                    continue;
                }
                JsonObject sound = new JsonObject();
                sound.addProperty("name", outputPath);
                if (variant.volume() != 1.0) sound.addProperty("volume", variant.volume());
                if (variant.pitch() != 1.0) sound.addProperty("pitch", variant.pitch());
                if (variant.weight() != 1) sound.addProperty("weight", variant.weight());
                if (variant.stream()) sound.addProperty("stream", true);
                sounds.add(sound);
                if (variant.attenuationDistance() != null) {
                    if (maximumDistance == null) maximumDistance = variant.attenuationDistance();
                    else if (!maximumDistance.equals(variant.attenuationDistance())) conflictingDistance = true;
                }
            }
            if (sounds.isEmpty()) continue;
            JsonObject definition = new JsonObject();
            definition.addProperty("category", category(entry.getKey()));
            definition.add("sounds", sounds);
            if (maximumDistance != null && !conflictingDistance) definition.addProperty("max_distance", maximumDistance);
            if (conflictingDistance) problem(entry.getKey() + " uses incompatible per-variant attenuation distances");
            bedrockDefinitions.add(entry.getKey(), definition);
        }
        if (!bedrockDefinitions.isEmpty()) {
            JsonObject root = new JsonObject();
            root.addProperty("format_version", "1.20.20");
            root.add("sound_definitions", bedrockDefinitions);
            packFiles.put("sounds/sound_definitions.json", BedrockPackCompiler.jsonBytes(root));
        }
        return new Result(bedrockDefinitions.size(), copiedFiles.size(), vanillaFallbackFiles.size(),
                List.copyOf(problems), List.copyOf(notices));
    }

    private void readDefinitions() {
        List<String> paths = resources.paths().stream()
                .filter(path -> path.matches("assets/[a-z0-9_.-]+/sounds\\.json"))
                .sorted().toList();
        for (String path : paths) {
            String namespace = path.substring("assets/".length(), path.length() - "/sounds.json".length());
            List<ResourceIndex.Asset> layers = resources.findAll(path);
            for (int index = layers.size() - 1; index >= 0; index--) {
                try {
                    JsonElement parsed = JsonParser.parseString(layers.get(index).readUtf8());
                    if (!parsed.isJsonObject()) {
                        problem(path + " is not a JSON object");
                        continue;
                    }
                    mergeLayer(namespace, parsed.getAsJsonObject(), path);
                } catch (IOException | RuntimeException failure) {
                    problem(path + " could not be parsed: " + message(failure));
                }
            }
        }
    }

    private void mergeLayer(String namespace, JsonObject root, String path) {
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            String identifier;
            try {
                identifier = identifier(entry.getKey(), namespace);
            } catch (IllegalArgumentException invalid) {
                problem(path + " contains invalid event " + entry.getKey());
                continue;
            }
            if (!entry.getValue().isJsonObject()) {
                problem(identifier + " is not a sound object");
                continue;
            }
            JsonObject value = entry.getValue().getAsJsonObject();
            JsonElement soundsElement = value.get("sounds");
            if (soundsElement == null || !soundsElement.isJsonArray()) {
                problem(identifier + " has no sounds array");
                continue;
            }
            List<JsonElement> sounds = new ArrayList<>();
            for (JsonElement sound : soundsElement.getAsJsonArray()) sounds.add(sound.deepCopy());
            boolean replace = booleanValue(value, "replace", false);
            JavaSound previous = definitions.get(identifier);
            if (!replace && previous != null) {
                List<JsonElement> merged = new ArrayList<>(previous.sounds());
                merged.addAll(sounds);
                sounds = merged;
            }
            definitions.put(identifier, new JavaSound(List.copyOf(sounds)));
        }
    }

    private List<Variant> resolveEvent(String identifier, Set<String> active) {
        if (!active.add(identifier)) {
            problem("cyclic sound event reference at " + identifier);
            return List.of();
        }
        JavaSound definition = definitions.get(identifier);
        if (definition == null) {
            problem("missing referenced sound event " + identifier);
            active.remove(identifier);
            return List.of();
        }
        List<Variant> result = new ArrayList<>();
        for (JsonElement element : definition.sounds()) {
            try {
                JsonObject properties = element.isJsonObject() ? element.getAsJsonObject() : null;
                String rawName = properties == null ? element.getAsString() : string(properties, "name");
                String name = identifier(rawName, "minecraft");
                String type = properties == null ? "file" : string(properties, "type");
                if (type.isBlank()) type = "file";
                type = type.substring(type.indexOf(':') + 1);
                double volume = number(properties, "volume", 1.0);
                double pitch = number(properties, "pitch", 1.0);
                int weight = integer(properties, "weight", 1);
                boolean stream = booleanValue(properties, "stream", false);
                Double attenuation = optionalNumber(properties, "attenuation_distance");
                if (volume <= 0 || pitch <= 0 || weight < 1) throw new IllegalArgumentException("invalid volume, pitch, or weight");
                if (type.equals("event")) {
                    for (Variant child : resolveEvent(name, active)) {
                        result.add(new Variant(child.file(), child.volume() * volume, child.pitch() * pitch,
                                Math.multiplyExact(child.weight(), weight), child.stream() || stream,
                                attenuation == null ? child.attenuationDistance() : attenuation));
                    }
                } else if (type.equals("file")) {
                    result.add(new Variant(name, volume, pitch, weight, stream, attenuation));
                } else problem(identifier + " uses unsupported sound type " + type);
            } catch (RuntimeException failure) {
                problem(identifier + " contains an invalid sound entry: " + message(failure));
            }
        }
        active.remove(identifier);
        return result;
    }

    private boolean shouldCompile(String identifier, JavaSound custom) {
        if (!identifier.startsWith("minecraft:") || vanillaOverride) return true;
        if (vanillaAssets == null) {
            problem(identifier + " cannot be checked against a verified vanilla sound registry");
            return false;
        }
        try {
            JsonObject root = vanillaSoundRegistry();
            String path = identifier.substring("minecraft:".length());
            JsonElement vanillaElement = root.get(path);
            if (vanillaElement == null) return true;
            if (vanillaElement.isJsonObject()) {
                JsonElement vanillaSounds = vanillaElement.getAsJsonObject().get("sounds");
                if (vanillaSounds != null && vanillaSounds.isJsonArray() &&
                        vanillaSounds.getAsJsonArray().asList().equals(custom.sounds())) return false;
            }
            // Bedrock keeps its own sound for the event; vanilla-override: true replaces it.
            notices.add(identifier + " changes a vanilla sound event; Bedrock keeps its own sound while vanilla-override is disabled");
            return false;
        } catch (IOException | RuntimeException failure) {
            problem(identifier + " could not be checked against vanilla sounds: " + message(failure));
            return false;
        }
    }

    private JsonObject vanillaSoundRegistry() throws IOException {
        if (vanillaSoundRegistry != null) return vanillaSoundRegistry;
        if (vanillaRegistryAttempted) throw new IOException("vanilla sound registry initialization failed");
        vanillaRegistryAttempted = true;
        var bytes = vanillaAssets.readSoundRegistry();
        if (bytes.isEmpty()) throw new IOException("verified vanilla asset index has no sounds.json registry");
        try {
            vanillaSoundRegistry = JsonParser.parseString(new String(bytes.get(), java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject();
            return vanillaSoundRegistry;
        } catch (RuntimeException invalid) {
            throw new IOException("verified vanilla sounds.json is invalid", invalid);
        }
    }

    private static String javaSoundPath(String identifier) {
        String[] parts = identifier.split(":", 2);
        return "assets/" + parts[0] + "/sounds/" + parts[1] + ".ogg";
    }

    private static String identifier(String value, String defaultNamespace) {
        if (value == null) throw new IllegalArgumentException("missing sound name");
        String normalized = value.toLowerCase(java.util.Locale.ROOT).replace('\\', '/');
        String qualified = normalized.contains(":") ? normalized : defaultNamespace + ':' + normalized;
        if (!qualified.matches("[a-z0-9_.-]+:[a-z0-9._/-]+") || qualified.contains("..") || qualified.endsWith("/")) {
            throw new IllegalArgumentException("unsafe sound identifier " + value);
        }
        if (qualified.endsWith(".ogg")) qualified = qualified.substring(0, qualified.length() - 4);
        return qualified;
    }

    private static String category(String identifier) {
        String path = identifier.substring(identifier.indexOf(':') + 1);
        if (path.startsWith("music")) return "music";
        if (path.startsWith("record") || path.contains("music_disc")) return "record";
        if (path.startsWith("ambient")) return "ambient";
        if (path.startsWith("weather")) return "weather";
        if (path.startsWith("block")) return "block";
        if (path.startsWith("ui")) return "ui";
        if (path.startsWith("player")) return "player";
        return "neutral";
    }

    private static String string(JsonObject value, String key) {
        if (value == null || !value.has(key) || !value.get(key).isJsonPrimitive()) return "";
        return value.get(key).getAsString();
    }

    private static double number(JsonObject value, String key, double fallback) {
        if (value == null || !value.has(key)) return fallback;
        return value.get(key).getAsDouble();
    }

    private static Double optionalNumber(JsonObject value, String key) {
        return value != null && value.has(key) ? value.get(key).getAsDouble() : null;
    }

    private static int integer(JsonObject value, String key, int fallback) {
        if (value == null || !value.has(key)) return fallback;
        return value.get(key).getAsInt();
    }

    private static boolean booleanValue(JsonObject value, String key, boolean fallback) {
        if (value == null || !value.has(key)) return fallback;
        return value.get(key).getAsBoolean();
    }

    private static String message(Throwable failure) {
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }

    private void problem(String value) {
        problems.add(value);
    }

    record Result(int definitions, int files, int vanillaFallbackFiles, List<String> problems, List<String> notices) {}
    private record JavaSound(List<JsonElement> sounds) {}
    private record Variant(String file, double volume, double pitch, int weight, boolean stream,
                           Double attenuationDistance) {}
}
