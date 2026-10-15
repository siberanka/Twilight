package com.siberanka.twilight.compiler;

import com.google.gson.JsonArray;

import java.util.List;
import java.util.OptionalInt;

record ItemCandidate(
        String baseItem,
        String mappingModel,
        List<String> visualModels,
        OptionalInt customModelData,
        String source,
        JsonArray predicates,
        int priority,
        List<String> nativeStateModels
) {
    ItemCandidate(String baseItem, String model, OptionalInt customModelData, String source) {
        this(baseItem, model, List.of(model), customModelData, source, new JsonArray(), 0, List.of());
    }

    String visualModel() { return visualModels.getFirst(); }
}
