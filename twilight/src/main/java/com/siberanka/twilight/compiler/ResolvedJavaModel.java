package com.siberanka.twilight.compiler;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Map;
import java.util.List;

record ResolvedJavaModel(String identifier, JsonArray elements, Map<String, String> textures, JsonObject display,
                         boolean handheld, boolean frontLight, List<ResolvedJavaModel> parts) {
    ResolvedJavaModel(String identifier, JsonArray elements, Map<String, String> textures, JsonObject display,
                      boolean handheld, boolean frontLight) {
        this(identifier, elements, textures, display, handheld, frontLight, List.of());
    }
    ResolvedJavaModel { parts = List.copyOf(parts); }
    List<ResolvedJavaModel> renderParts() { return parts.isEmpty() ? List.of(this) : parts; }
    boolean isThreeDimensional() { return !parts.isEmpty() || elements != null && !elements.isEmpty(); }
}
