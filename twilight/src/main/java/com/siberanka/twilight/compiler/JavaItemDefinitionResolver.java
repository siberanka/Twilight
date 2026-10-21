package com.siberanka.twilight.compiler;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.siberanka.twilight.source.ResourceIndex;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Expands Java 1.21.4+ item definition trees into Geyser-compatible visual variants.
 *
 * <p>Geyser's item mappings know only some of Java's properties, under their own names; a definition that names
 * another one is refused by Geyser as a whole (the item then shows as its base item). Properties Bedrock cannot
 * evaluate (using an item, the display context, pull progress, the time...) therefore become the state a player
 * sees most: the {@code on_false} branch of a condition, the fallback of a select or range (else its first case
 * or lowest entry).
 */
final class JavaItemDefinitionResolver {
    /** Geyser's condition, match and range properties (item mappings v2). */
    static final Set<String> CONDITIONS = Set.of("broken", "damaged", "custom_model_data", "has_component", "fishing_rod_cast");
    static final Set<String> MATCHES = Set.of("charge_type", "trim_material", "context_dimension", "custom_model_data");
    static final Set<String> RANGES = Set.of("bundle_fullness", "damage", "count", "custom_model_data");

    private final ResourceIndex resources;

    JavaItemDefinitionResolver(ResourceIndex resources) {
        this.resources = resources;
    }

    List<Variant> resolve(String identifier) throws IOException {
        String qualified = JavaModelResolver.qualified(identifier, "minecraft");
        ResourceIndex.Asset asset = resources.find(itemPath(qualified)).orElse(null);
        if (asset == null) return List.of(new Variant(List.of(qualified), new JsonArray(), 0));
        JsonObject root = JsonParser.parseString(asset.readUtf8()).getAsJsonObject();
        JsonElement model = root.get("model");
        if (model == null || !model.isJsonObject()) throw new IOException("Item definition has no model object: " + qualified);
        List<Variant> variants = new ArrayList<>();
        visit(model.getAsJsonObject(), JavaModelResolver.namespace(qualified), new JsonArray(), 0, variants, 0);
        if (variants.isEmpty()) throw new IOException("Item definition has no renderable model: " + qualified);
        return List.copyOf(variants);
    }

    private void visit(JsonObject node, String namespace, JsonArray predicates, int priority,
                       List<Variant> output, int depth) throws IOException {
        if (depth > 64) throw new IOException("Item definition nesting exceeds 64 levels");
        String type = namespaced(node, "type", "minecraft:model");
        switch (type) {
            case "minecraft:model" -> {
                if (!node.has("model")) throw new IOException("minecraft:model node has no model identifier");
                output.add(new Variant(List.of(JavaModelResolver.itemModel(node.get("model").getAsString(), namespace, resources)),
                        predicates.deepCopy(), priority));
            }
            case "minecraft:condition" -> {
                if (!CONDITIONS.contains(property(node))) {
                    // Bedrock cannot tell: the usual state (not using, not selected, ...).
                    visit(requiredObject(node, "on_false"), namespace, predicates, priority, output, depth + 1);
                    return;
                }
                JsonObject predicate = conditionPredicate(node, true);
                visit(requiredObject(node, "on_true"), namespace, append(predicates, predicate), priority + 1, output, depth + 1);
                JsonObject inverse = predicate.deepCopy();
                inverse.addProperty("expected", false);
                visit(requiredObject(node, "on_false"), namespace, append(predicates, inverse), priority, output, depth + 1);
            }
            case "minecraft:range_dispatch" -> {
                String property = property(node);
                JsonArray entries = node.has("entries") ? node.getAsJsonArray("entries") : new JsonArray();
                if (!RANGES.contains(property)) {
                    JsonObject usual = node.has("fallback") && node.get("fallback").isJsonObject() ? node.getAsJsonObject("fallback") : null;
                    double lowest = Double.MAX_VALUE;
                    if (usual == null) {
                        for (JsonElement entryValue : entries) {
                            JsonObject entry = entryValue.getAsJsonObject();
                            double threshold = entry.has("threshold") ? entry.get("threshold").getAsDouble() : 0;
                            if (threshold < lowest && entry.has("model") && entry.get("model").isJsonObject()) {
                                lowest = threshold;
                                usual = entry.getAsJsonObject("model");
                            }
                        }
                    }
                    if (usual != null) visit(usual, namespace, predicates, priority, output, depth + 1);
                    return;
                }
                int rank = entries.size();
                for (JsonElement entryValue : entries) {
                    JsonObject entry = entryValue.getAsJsonObject();
                    JsonObject predicate = new JsonObject();
                    predicate.addProperty("type", "range_dispatch");
                    predicate.addProperty("property", property);
                    predicate.addProperty("threshold", entry.get("threshold").getAsDouble());
                    copy(node, predicate, "index", "normalize");
                    if (node.has("scale") && node.get("scale").getAsDouble() != 0) {
                        predicate.addProperty("threshold", entry.get("threshold").getAsDouble() / node.get("scale").getAsDouble());
                    }
                    visit(requiredObject(entry, "model"), namespace, append(predicates, predicate), priority + rank--,
                            output, depth + 1);
                }
                if (node.has("fallback") && node.get("fallback").isJsonObject()) {
                    visit(node.getAsJsonObject("fallback"), namespace, predicates, priority, output, depth + 1);
                }
            }
            case "minecraft:select" -> {
                String property = property(node);
                JsonArray cases = node.has("cases") ? node.getAsJsonArray("cases") : new JsonArray();
                if (!MATCHES.contains(property)) {
                    JsonObject usual = node.has("fallback") && node.get("fallback").isJsonObject() ? node.getAsJsonObject("fallback") : null;
                    if (usual == null && !cases.isEmpty() && cases.get(0).isJsonObject()) {
                        usual = requiredObject(cases.get(0).getAsJsonObject(), "model");
                    }
                    if (usual != null) visit(usual, namespace, predicates, priority, output, depth + 1);
                    return;
                }
                for (JsonElement caseValue : cases) {
                    JsonObject selected = caseValue.getAsJsonObject();
                    JsonArray values = selected.get("when").isJsonArray()
                            ? selected.getAsJsonArray("when") : singleton(selected.get("when"));
                    for (JsonElement value : values) {
                        JsonObject predicate = new JsonObject();
                        predicate.addProperty("type", "match");
                        predicate.addProperty("property", property);
                        predicate.addProperty("value", value.getAsString());
                        copy(node, predicate, "index");
                        visit(requiredObject(selected, "model"), namespace, append(predicates, predicate), priority + 1,
                                output, depth + 1);
                    }
                }
                if (node.has("fallback") && node.get("fallback").isJsonObject()) {
                    visit(node.getAsJsonObject("fallback"), namespace, predicates, priority, output, depth + 1);
                }
            }
            case "minecraft:composite" -> {
                JsonArray models = node.has("models") ? node.getAsJsonArray("models") : new JsonArray();
                List<String> visualModels = new ArrayList<>();
                for (JsonElement child : models) collectStaticModels(child.getAsJsonObject(), namespace, visualModels, depth + 1);
                if (!visualModels.isEmpty()) output.add(new Variant(List.copyOf(visualModels), predicates.deepCopy(), priority));
            }
            case "minecraft:special" -> {
                if (!node.has("base")) throw new IOException("Special item model has no base model");
                output.add(new Variant(List.of(JavaModelResolver.itemModel(node.get("base").getAsString(), namespace, resources)),
                        predicates.deepCopy(), priority));
            }
            case "minecraft:empty" -> { }
            default -> throw new IOException("Unsupported item definition node: " + type);
        }
    }

    private static JsonObject conditionPredicate(JsonObject node, boolean expected) throws IOException {
        JsonObject predicate = new JsonObject();
        predicate.addProperty("type", "condition");
        predicate.addProperty("property", property(node));
        predicate.addProperty("expected", expected);
        copy(node, predicate, "index", "component");
        return predicate;
    }

    private void collectStaticModels(JsonObject node, String namespace, List<String> output, int depth) throws IOException {
        if (depth > 64) throw new IOException("Composite item definition nesting exceeds 64 levels");
        String type = namespaced(node, "type", "minecraft:model");
        if (type.equals("minecraft:model")) {
            if (!node.has("model")) throw new IOException("Composite minecraft:model node has no model identifier");
            output.add(JavaModelResolver.itemModel(node.get("model").getAsString(), namespace, resources));
            return;
        }
        if (type.equals("minecraft:composite")) {
            if (!node.has("models") || !node.get("models").isJsonArray()) return;
            for (JsonElement child : node.getAsJsonArray("models")) {
                collectStaticModels(child.getAsJsonObject(), namespace, output, depth + 1);
            }
            return;
        }
        if (!type.equals("minecraft:empty")) throw new IOException("Dynamic node inside composite is unsupported: " + type);
    }

    /** The property under Geyser's name ({@code fishing_rod/cast} is {@code fishing_rod_cast}). */
    private static String property(JsonObject node) throws IOException {
        if (!node.has("property")) throw new IOException("Dynamic item node has no property");
        String value = node.get("property").getAsString();
        value = value.startsWith("minecraft:") ? value.substring("minecraft:".length()) : value;
        return switch (value) {
            case "fishing_rod/cast" -> "fishing_rod_cast";
            case "bundle/fullness" -> "bundle_fullness";
            default -> value;
        };
    }

    private static String namespaced(JsonObject node, String key, String fallback) {
        String value = node.has(key) ? node.get(key).getAsString() : fallback;
        return value.contains(":") ? value : "minecraft:" + value;
    }

    private static JsonObject requiredObject(JsonObject node, String key) throws IOException {
        if (!node.has(key) || !node.get(key).isJsonObject()) throw new IOException("Item definition node has no " + key + " object");
        return node.getAsJsonObject(key);
    }

    private static JsonArray append(JsonArray source, JsonObject predicate) {
        JsonArray result = source.deepCopy();
        result.add(predicate);
        return result;
    }

    private static JsonArray singleton(JsonElement value) {
        JsonArray result = new JsonArray();
        result.add(value);
        return result;
    }

    private static void copy(JsonObject source, JsonObject target, String... keys) {
        for (String key : keys) if (source.has(key)) target.add(key, source.get(key).deepCopy());
    }

    static String itemPath(String identifier) {
        String qualified = JavaModelResolver.qualified(identifier, "minecraft");
        int colon = qualified.indexOf(':');
        return "assets/" + qualified.substring(0, colon) + "/items/" + qualified.substring(colon + 1) + ".json";
    }

    record Variant(List<String> visualModels, JsonArray predicates, int priority) {
        String visualModel() { return visualModels.getFirst(); }
    }
}
