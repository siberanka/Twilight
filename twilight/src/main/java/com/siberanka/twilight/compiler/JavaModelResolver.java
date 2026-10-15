package com.siberanka.twilight.compiler;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.siberanka.twilight.source.ResourceIndex;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

final class JavaModelResolver {
    private final ResourceIndex resources;
    private final VanillaAssetCache vanilla;

    JavaModelResolver(ResourceIndex resources) {
        this(resources, null);
    }

    JavaModelResolver(ResourceIndex resources, VanillaAssetCache vanilla) {
        this.resources = resources;
        this.vanilla = vanilla;
    }

    ResolvedJavaModel resolveAll(List<String> identifiers) throws IOException {
        return resolveAll(identifiers, false);
    }

    /**
     * @param vanillaModels the selector (custom model data, predicates) is custom, so its models may be
     *                      plain vanilla ones, e.g. a menu button that shows {@code minecraft:item/barrier}
     */
    ResolvedJavaModel resolveAll(List<String> identifiers, boolean vanillaModels) throws IOException {
        if (identifiers.isEmpty()) throw new IOException("Model list is empty");
        if (identifiers.size() == 1) return resolve(identifiers.getFirst(), vanillaModels);
        List<ResolvedJavaModel> models = new ArrayList<>(identifiers.size());
        for (String identifier : identifiers) models.add(resolve(identifier, vanillaModels));
        return merge(models);
    }

    ResolvedJavaModel resolve(String identifier) throws IOException {
        return resolve(identifier, false);
    }

    ResolvedJavaModel resolve(String identifier, boolean vanillaTopLevel) throws IOException {
        String current = qualified(identifier, "minecraft");
        ArrayDeque<JsonObject> chain = new ArrayDeque<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        boolean handheld = false;
        for (int depth = 0; depth < 64; depth++) {
            if (!seen.add(current)) throw new IOException("Model parent cycle: " + current);
            if (isHandheldParent(current)) handheld = true;
            ResourceIndex.Asset asset = resources.find(modelPath(current)).orElse(null);
            if (asset == null) {
                JsonObject builtin = builtinModel(current);
                JsonObject cached = null;
                // A custom model may inherit a vanilla parent that the source
                // pack does not copy. The requested top-level model itself
                // must still come from a discovered custom source; otherwise
                // an untouched vanilla item could become a custom mapping.
                if ((depth > 0 || vanillaTopLevel) && vanilla != null && current.startsWith("minecraft:")) {
                    try {
                        var bytes = vanilla.readModel(modelPath(current));
                        if (bytes.isPresent()) {
                            try { cached = JsonParser.parseString(new String(bytes.get(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject(); }
                            catch (RuntimeException invalid) { throw new IOException("Invalid vanilla Java model " + current, invalid); }
                        }
                    } catch (IOException unavailable) {
                        if (builtin == null) throw unavailable;
                    }
                }
                if (cached != null) {
                    chain.addFirst(cached);
                    if (!cached.has("parent")) break;
                    current = qualified(cached.get("parent").getAsString(), "minecraft");
                    continue;
                }
                if (builtin != null) {
                    chain.addFirst(builtin);
                    if (!builtin.has("parent")) break;
                    current = qualified(builtin.get("parent").getAsString(), "minecraft");
                    continue;
                }
                if (isBuiltinParent(current)) break;
                throw new MissingModelException("Missing Java model " + current);
            }
            JsonObject json = JsonParser.parseString(asset.readUtf8()).getAsJsonObject();
            chain.addFirst(json);
            if (!json.has("parent")) break;
            current = qualified(json.get("parent").getAsString(), "minecraft");
        }
        if (chain.isEmpty()) throw new IOException("No model data for " + identifier);

        Map<String, String> textures = new LinkedHashMap<>();
        JsonObject display = new JsonObject();
        boolean frontLight = false;
        com.google.gson.JsonArray elements = null;
        for (JsonObject json : chain) {
            if (json.has("gui_light")) frontLight = json.get("gui_light").getAsString().equals("front");
            if (json.has("textures") && json.get("textures").isJsonObject()) {
                for (Map.Entry<String, JsonElement> texture : json.getAsJsonObject("textures").entrySet()) {
                    String sprite = textureValue(texture.getValue());
                    if (sprite != null) textures.put(texture.getKey(), sprite);
                }
            }
            if (json.has("display") && json.get("display").isJsonObject()) {
                for (Map.Entry<String, JsonElement> transform : json.getAsJsonObject("display").entrySet()) {
                    display.add(transform.getKey(), transform.getValue().deepCopy());
                }
            }
            if (json.has("elements") && json.get("elements").isJsonArray()) elements = json.getAsJsonArray("elements").deepCopy();
        }
        Map<String, String> resolvedTextures = new LinkedHashMap<>();
        for (String key : textures.keySet()) resolvedTextures.put(key, resolveTexture(key, textures, "minecraft"));
        return new ResolvedJavaModel(qualified(identifier, "minecraft"), elements, Map.copyOf(resolvedTextures),
                display, handheld, frontLight);
    }

    private static ResolvedJavaModel merge(List<ResolvedJavaModel> models) {
        com.google.gson.JsonArray elements = new com.google.gson.JsonArray();
        Map<String, String> textures = new LinkedHashMap<>();
        JsonObject display = new JsonObject();
        int layer = 0;
        for (int modelIndex = 0; modelIndex < models.size(); modelIndex++) {
            ResolvedJavaModel model = models.get(modelIndex);
            if (display.isEmpty() && !model.display().isEmpty()) display = model.display().deepCopy();
            Map<String, String> renamed = new LinkedHashMap<>();
            for (Map.Entry<String, String> texture : model.textures().entrySet()) {
                String key = texture.getKey().startsWith("layer") ? "layer" + layer++ : "m" + modelIndex + '_' + texture.getKey();
                renamed.put(texture.getKey(), key);
                textures.put(key, texture.getValue());
            }
            if (model.elements() == null) continue;
            for (JsonElement elementValue : model.elements()) {
                JsonObject element = elementValue.getAsJsonObject().deepCopy();
                JsonObject faces = element.getAsJsonObject("faces");
                if (faces != null) for (Map.Entry<String, JsonElement> face : faces.entrySet()) {
                    JsonObject faceJson = face.getValue().getAsJsonObject();
                    if (!faceJson.has("texture")) continue;
                    String reference = faceJson.get("texture").getAsString();
                    if (reference.startsWith("#")) {
                        String key = renamed.get(reference.substring(1));
                        if (key != null) faceJson.addProperty("texture", "#" + key);
                    }
                }
                elements.add(element);
            }
        }
        return new ResolvedJavaModel("twilight:composite", elements.isEmpty() ? null : elements,
                Map.copyOf(textures), display, models.getFirst().handheld(), models.getFirst().frontLight(), models);
    }

    /** A texture entry: a sprite name, or (since 1.21.6) an object such as {"sprite":..., "force_translucent":true}. */
    static String textureValue(JsonElement value) {
        if (value == null || value.isJsonNull()) return null;
        if (value.isJsonPrimitive()) return value.getAsString();
        if (value.isJsonObject() && value.getAsJsonObject().has("sprite")) {
            JsonElement sprite = value.getAsJsonObject().get("sprite");
            return sprite.isJsonPrimitive() ? sprite.getAsString() : null;
        }
        return null;
    }

    private static String resolveTexture(String key, Map<String, String> textures, String fallbackNamespace) throws IOException {
        String value = textures.get(key);
        java.util.Set<String> seen = new java.util.HashSet<>();
        while (value != null && value.startsWith("#")) {
            String reference = value.substring(1);
            if (!seen.add(reference)) throw new IOException("Texture reference cycle at #" + reference);
            value = textures.get(reference);
        }
        if (value == null) throw new IOException("Missing texture reference #" + key);
        return qualified(value, fallbackNamespace);
    }

    static String modelPath(String identifier) {
        String qualified = qualified(identifier, "minecraft");
        int colon = qualified.indexOf(':');
        return "assets/" + qualified.substring(0, colon) + "/models/" + qualified.substring(colon + 1) + ".json";
    }

    static String texturePath(String identifier) {
        String qualified = qualified(identifier, "minecraft");
        int colon = qualified.indexOf(':');
        String path = qualified.substring(colon + 1);
        if (!path.endsWith(".png")) path += ".png";
        return "assets/" + qualified.substring(0, colon) + "/textures/" + path;
    }

    static String qualified(String identifier, String fallbackNamespace) {
        String value = identifier.startsWith("minecraft:") ? identifier.substring("minecraft:".length()) : identifier;
        if (identifier.contains(":")) return identifier;
        return fallbackNamespace + ":" + value;
    }

    static String namespace(String identifier) {
        int colon = identifier.indexOf(':');
        return colon < 0 ? "minecraft" : identifier.substring(0, colon);
    }

    static boolean hasStandardFlatDisplay(JsonObject display) {
        return display.equals(builtinDisplay("minecraft:item/generated")) ||
                display.equals(builtinDisplay("minecraft:item/handheld")) ||
                display.equals(builtinDisplay("minecraft:item/handheld_rod"));
    }

    private static JsonObject builtinDisplay(String identifier) {
        ArrayDeque<JsonObject> chain = new ArrayDeque<>();
        String current = identifier;
        for (int depth = 0; depth < 8; depth++) {
            JsonObject model = builtinModel(current);
            if (model == null) break;
            chain.addFirst(model);
            if (!model.has("parent")) break;
            current = qualified(model.get("parent").getAsString(), "minecraft");
        }
        JsonObject display = new JsonObject();
        for (JsonObject model : chain) if (model.has("display")) {
            for (Map.Entry<String, JsonElement> entry : model.getAsJsonObject("display").entrySet()) {
                display.add(entry.getKey(), entry.getValue().deepCopy());
            }
        }
        return display;
    }

    private static boolean isBuiltinParent(String identifier) {
        return identifier.equals("minecraft:item/generated") || identifier.equals("minecraft:item/handheld") ||
                identifier.equals("minecraft:item/handheld_rod") || identifier.equals("minecraft:item/handheld_mace") ||
                identifier.equals("minecraft:block/block") ||
                identifier.startsWith("minecraft:builtin/");
    }

    private static boolean isHandheldParent(String identifier) {
        return identifier.equals("minecraft:item/handheld") ||
                identifier.equals("minecraft:item/handheld_rod") ||
                identifier.equals("minecraft:item/handheld_mace");
    }

    static JsonObject builtinModel(String identifier) {
        return switch (identifier) {
            // These definitions are the hash-verified Minecraft 26.2 client
            // item templates. Resolving them is required to preserve Java's
            // inherited pose when a generated or handheld model becomes a
            // Bedrock attachable.
            case "minecraft:item/generated" -> JsonParser.parseString("""
                    {"parent":"minecraft:builtin/generated","gui_light":"front","display":{
                    "ground":{"rotation":[0,0,0],"translation":[0,2,0],"scale":[0.5,0.5,0.5]},
                    "head":{"rotation":[0,180,0],"translation":[0,13,7],"scale":[1,1,1]},
                    "thirdperson_righthand":{"rotation":[0,0,0],"translation":[0,3,1],"scale":[0.55,0.55,0.55]},
                    "firstperson_righthand":{"rotation":[0,-90,25],"translation":[1.13,3.2,1.13],"scale":[0.68,0.68,0.68]},
                    "fixed":{"rotation":[0,180,0],"scale":[1,1,1]}}}
                    """).getAsJsonObject();
            case "minecraft:item/handheld" -> JsonParser.parseString("""
                    {"parent":"minecraft:item/generated","display":{
                    "thirdperson_righthand":{"rotation":[0,-90,55],"translation":[0,4,0.5],"scale":[0.85,0.85,0.85]},
                    "thirdperson_lefthand":{"rotation":[0,90,-55],"translation":[0,4,0.5],"scale":[0.85,0.85,0.85]},
                    "firstperson_righthand":{"rotation":[0,-90,25],"translation":[1.13,3.2,1.13],"scale":[0.68,0.68,0.68]},
                    "firstperson_lefthand":{"rotation":[0,90,-25],"translation":[1.13,3.2,1.13],"scale":[0.68,0.68,0.68]}}}
                    """).getAsJsonObject();
            case "minecraft:item/handheld_rod" -> JsonParser.parseString("""
                    {"parent":"minecraft:item/handheld","display":{
                    "thirdperson_righthand":{"rotation":[0,90,55],"translation":[0,4,2.5],"scale":[0.85,0.85,0.85]},
                    "thirdperson_lefthand":{"rotation":[0,-90,-55],"translation":[0,4,2.5],"scale":[0.85,0.85,0.85]},
                    "firstperson_righthand":{"rotation":[0,90,25],"translation":[0,1.6,0.8],"scale":[0.68,0.68,0.68]},
                    "firstperson_lefthand":{"rotation":[0,-90,-25],"translation":[0,1.6,0.8],"scale":[0.68,0.68,0.68]}}}
                    """).getAsJsonObject();
            case "minecraft:block/cube_all" -> JsonParser.parseString("""
                    {"parent":"minecraft:block/block","elements":[{"from":[0,0,0],"to":[16,16,16],"faces":{
                    "down":{"texture":"#all","cullface":"down"},"up":{"texture":"#all","cullface":"up"},
                    "north":{"texture":"#all","cullface":"north"},"south":{"texture":"#all","cullface":"south"},
                    "west":{"texture":"#all","cullface":"west"},"east":{"texture":"#all","cullface":"east"}}}]}
                    """).getAsJsonObject();
            case "minecraft:block/cube_column" -> JsonParser.parseString("""
                    {"parent":"minecraft:block/block","elements":[{"from":[0,0,0],"to":[16,16,16],"faces":{
                    "down":{"texture":"#end","cullface":"down"},"up":{"texture":"#end","cullface":"up"},
                    "north":{"texture":"#side","cullface":"north"},"south":{"texture":"#side","cullface":"south"},
                    "west":{"texture":"#side","cullface":"west"},"east":{"texture":"#side","cullface":"east"}}}]}
                    """).getAsJsonObject();
            case "minecraft:block/orientable" -> JsonParser.parseString("""
                    {"parent":"minecraft:block/block","elements":[{"from":[0,0,0],"to":[16,16,16],"faces":{
                    "down":{"texture":"#top","cullface":"down"},"up":{"texture":"#top","cullface":"up"},
                    "north":{"texture":"#front","cullface":"north"},"south":{"texture":"#side","cullface":"south"},
                    "west":{"texture":"#side","cullface":"west"},"east":{"texture":"#side","cullface":"east"}}}]}
                    """).getAsJsonObject();
            case "minecraft:block/template_glazed_terracotta" -> JsonParser.parseString("""
                    {"parent":"minecraft:block/block","elements":[{"from":[0,0,0],"to":[16,16,16],"faces":{
                    "down":{"texture":"#pattern","cullface":"down"},"up":{"texture":"#pattern","cullface":"up"},
                    "north":{"texture":"#pattern","cullface":"north"},"south":{"texture":"#pattern","cullface":"south"},
                    "west":{"texture":"#pattern","cullface":"west"},"east":{"texture":"#pattern","cullface":"east"}}}]}
                    """).getAsJsonObject();
            case "minecraft:block/template_torch" -> JsonParser.parseString("""
                    {"parent":"minecraft:block/block","elements":[{"from":[7,0,7],"to":[9,10,9],"faces":{
                    "down":{"texture":"#torch","uv":[7,6,9,8]},"up":{"texture":"#torch","uv":[7,6,9,8]},
                    "north":{"texture":"#torch","uv":[7,6,9,16]},"south":{"texture":"#torch","uv":[7,6,9,16]},
                    "west":{"texture":"#torch","uv":[7,6,9,16]},"east":{"texture":"#torch","uv":[7,6,9,16]}}}]}
                    """).getAsJsonObject();
            case "minecraft:block/template_torch_wall" -> JsonParser.parseString("""
                    {"parent":"minecraft:block/block","elements":[{"from":[7,3,7],"to":[9,13,9],
                    "rotation":{"origin":[8,3,8],"axis":"x","angle":-22.5},"faces":{
                    "down":{"texture":"#torch","uv":[7,6,9,8]},"up":{"texture":"#torch","uv":[7,6,9,8]},
                    "north":{"texture":"#torch","uv":[7,6,9,16]},"south":{"texture":"#torch","uv":[7,6,9,16]},
                    "west":{"texture":"#torch","uv":[7,6,9,16]},"east":{"texture":"#torch","uv":[7,6,9,16]}}}]}
                    """).getAsJsonObject();
            default -> null;
        };
    }

    /** A model that exists in no pack: Java draws its missing model for such an item. */
    static final class MissingModelException extends IOException {
        MissingModelException(String message) {
            super(message);
        }
    }
}
