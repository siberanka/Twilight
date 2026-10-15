package com.siberanka.twilight.compiler;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.siberanka.twilight.source.CustomItemDescriptor;
import com.siberanka.twilight.source.ResourceIndex;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Comparator;

final class ItemCandidateCollector {
    private final ResourceIndex resources;

    ItemCandidateCollector(ResourceIndex resources) {
        this.resources = resources;
    }

    List<ItemCandidate> collect(List<CustomItemDescriptor> liveItems) throws IOException {
        Map<String, ItemCandidate> candidates = new LinkedHashMap<>();
        collectLegacy(candidates);
        JavaItemDefinitionResolver definitions = new JavaItemDefinitionResolver(resources);
        for (CustomItemDescriptor item : liveItems) {
            if (item.itemModel().isEmpty()) continue;
            String mappingModel = JavaModelResolver.qualified(item.itemModel().get(), "minecraft");
            for (JavaItemDefinitionResolver.Variant variant : definitions.resolve(mappingModel)) {
                String key = item.baseItem() + '|' + mappingModel + '|' + variant.visualModels() + '|' + variant.predicates();
                candidates.putIfAbsent(key, new ItemCandidate(item.baseItem(), mappingModel, variant.visualModels(),
                        OptionalInt.empty(), item.provider(), variant.predicates(), variant.priority(), List.of()));
            }
        }
        return List.copyOf(candidates.values());
    }

    private void collectLegacy(Map<String, ItemCandidate> candidates) throws IOException {
        String prefix = "assets/minecraft/models/item/";
        for (String path : resources.pathsStartingWith(prefix)) {
            JsonObject root = JsonParser.parseString(resources.find(path).orElseThrow().readUtf8()).getAsJsonObject();
            if (!root.has("overrides") || !root.get("overrides").isJsonArray()) continue;
            String itemPath = path.substring(prefix.length(), path.length() - ".json".length());
            String baseItem = "minecraft:" + itemPath;
            Map<Integer, List<LegacyOverride>> overrides = new LinkedHashMap<>();
            for (JsonElement element : root.getAsJsonArray("overrides")) {
                if (!element.isJsonObject()) continue;
                JsonObject override = element.getAsJsonObject();
                if (!override.has("model") || !override.has("predicate")) continue;
                JsonObject predicate = override.getAsJsonObject("predicate");
                if (!predicate.has("custom_model_data")) continue;
                double raw = predicate.get("custom_model_data").getAsDouble();
                if (!Double.isFinite(raw) || raw != Math.rint(raw) || raw < Integer.MIN_VALUE || raw > Integer.MAX_VALUE) continue;
                int cmd = (int) raw;
                String model = JavaModelResolver.qualified(override.get("model").getAsString(), "minecraft");
                overrides.computeIfAbsent(cmd, ignored -> new ArrayList<>()).add(new LegacyOverride(model, predicate));
            }
            for (Map.Entry<Integer, List<LegacyOverride>> entry : overrides.entrySet()) {
                int cmd = entry.getKey();
                List<LegacyOverride> variants = entry.getValue();
                LegacyOverride idle = variants.stream().filter(value -> value.predicate().size() == 1).findFirst().orElse(null);
                if (idle == null) continue;
                String key = baseItem + '|' + idle.model() + '|' + cmd;
                List<String> nativeStates = nativeStateModels(baseItem, idle.model(), variants);
                candidates.putIfAbsent(key, new ItemCandidate(baseItem, idle.model(), List.of(idle.model()), OptionalInt.of(cmd),
                        "legacy-pack", new JsonArray(), 0, nativeStates));
                if (baseItem.equals("minecraft:crossbow")) {
                    for (LegacyOverride variant : variants) {
                        if (!isEnabled(variant.predicate(), "charged") && !isEnabled(variant.predicate(), "firework")) continue;
                        JsonObject charge = new JsonObject();
                        charge.addProperty("type", "match");
                        charge.addProperty("property", "charge_type");
                        charge.addProperty("value", isEnabled(variant.predicate(), "firework") ? "rocket" : "arrow");
                        JsonArray predicates = new JsonArray();
                        predicates.add(charge);
                        String chargedKey = baseItem + '|' + variant.model() + '|' + cmd + '|' + charge.get("value").getAsString();
                        candidates.putIfAbsent(chargedKey, new ItemCandidate(baseItem, variant.model(), List.of(variant.model()),
                                OptionalInt.of(cmd), "legacy-pack", predicates, 2, List.of()));
                    }
                }
                if (baseItem.equals("minecraft:fishing_rod")) {
                    for (LegacyOverride variant : variants) {
                        if (!isEnabled(variant.predicate(), "cast")) continue;
                        JsonObject cast = new JsonObject();
                        cast.addProperty("type", "condition");
                        cast.addProperty("property", "fishing_rod_cast");
                        JsonArray predicates = new JsonArray();
                        predicates.add(cast);
                        String castKey = baseItem + '|' + variant.model() + '|' + cmd + "|cast";
                        candidates.putIfAbsent(castKey, new ItemCandidate(baseItem, variant.model(), List.of(variant.model()),
                                OptionalInt.of(cmd), "legacy-pack", predicates, 1, List.of()));
                    }
                }
            }
        }
    }

    private static List<String> nativeStateModels(String baseItem, String idle, List<LegacyOverride> variants) {
        if (baseItem.equals("minecraft:bow")) {
            List<String> states = new ArrayList<>();
            states.add(idle);
            variants.stream().filter(value -> isEnabled(value.predicate(), "pulling"))
                    .sorted(Comparator.comparingDouble(value -> number(value.predicate(), "pull")))
                    .map(LegacyOverride::model).limit(3).forEach(states::add);
            return List.copyOf(states);
        }
        if (baseItem.equals("minecraft:crossbow")) {
            List<String> states = new ArrayList<>();
            states.add(idle);
            variants.stream().filter(value -> isEnabled(value.predicate(), "pulling"))
                    .sorted(Comparator.comparingDouble(value -> number(value.predicate(), "pull")))
                    .map(LegacyOverride::model).limit(3).forEach(states::add);
            variants.stream().filter(value -> isEnabled(value.predicate(), "charged") && !isEnabled(value.predicate(), "firework"))
                    .map(LegacyOverride::model).findFirst().ifPresent(states::add);
            variants.stream().filter(value -> isEnabled(value.predicate(), "firework"))
                    .map(LegacyOverride::model).findFirst().ifPresent(states::add);
            return List.copyOf(states);
        }
        return List.of();
    }

    private static boolean isEnabled(JsonObject predicate, String key) {
        if (!predicate.has(key) || !predicate.get(key).isJsonPrimitive()) return false;
        var value = predicate.getAsJsonPrimitive(key);
        return value.isBoolean() ? value.getAsBoolean() : value.getAsDouble() > 0;
    }

    private static double number(JsonObject predicate, String key) {
        return predicate.has(key) ? predicate.get(key).getAsDouble() : 0;
    }

    private record LegacyOverride(String model, JsonObject predicate) { }
}
