package com.siberanka.twilight.compiler;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Repairs element faces that have no {@code texture} property but carry the reference under another key,
 * for example {@code "north":{"uv":[0,0,16,16],"demo:item/block":"#0"}} as some model exporters write it.
 * Vanilla requires {@code texture}, so Java clients most likely draw such a model as missing; Twilight still
 * converts the intended look so that one malformed model does not reject a whole strict conversion.
 * <p>
 * The repair works on the resolved model's own (already deep-copied) elements and only touches faces that
 * lack {@code texture}; well-formed models are left unchanged.
 */
final class ModelFaceRepair {
    private static final Set<String> VANILLA_FACE_KEYS = Set.of("uv", "texture", "cullface", "rotation", "tintindex");

    /** @param repaired faces that received a texture; @param keys the bogus keys that were replaced */
    record Result(int repaired, Set<String> keys) {}

    private ModelFaceRepair() {}

    /**
     * @param elements the model's elements (modified in place)
     * @param variables the model's texture variables with their raw values, parents included
     * @param resolved the same variables with every reference followed
     */
    static Result repair(JsonArray elements, Map<String, String> variables, Map<String, String> resolved) {
        Set<String> keys = new LinkedHashSet<>();
        int repaired = 0;
        if (elements == null) return new Result(0, keys);
        for (JsonElement elementValue : elements) {
            if (!elementValue.isJsonObject()) continue;
            JsonElement facesValue = elementValue.getAsJsonObject().get("faces");
            if (facesValue == null || !facesValue.isJsonObject()) continue;
            for (Map.Entry<String, JsonElement> face : facesValue.getAsJsonObject().entrySet()) {
                if (!face.getValue().isJsonObject()) continue;
                JsonObject faceJson = face.getValue().getAsJsonObject();
                if (faceJson.has("texture")) continue;
                String[] fix = decide(faceJson, variables, resolved);
                if (fix == null) continue;
                faceJson.remove(fix[1]);
                faceJson.addProperty("texture", fix[0]);
                keys.add(fix[1]);
                repaired++;
            }
        }
        return new Result(repaired, keys);
    }

    /** @return {reference, key to drop}, or null when the face is ambiguous or hopeless */
    private static String[] decide(JsonObject face, Map<String, String> variables, Map<String, String> resolved) {
        List<String> extra = new ArrayList<>();
        for (String key : face.keySet()) if (!VANILLA_FACE_KEYS.contains(key)) extra.add(key);
        if (extra.isEmpty()) return null;

        // 1. exactly one extra string property that is a reference to a variable the model defines
        List<String> references = new ArrayList<>();
        for (String key : extra) {
            String value = stringValue(face.get(key));
            if (value != null && value.startsWith("#") && variables.containsKey(value.substring(1))) references.add(key);
        }
        if (references.size() == 1) return new String[]{face.get(references.getFirst()).getAsString(), references.getFirst()};

        // 2. exactly one extra key that names a texture the model uses
        List<String> named = new ArrayList<>();
        String variable = null;
        for (String key : extra) {
            String match = variableNamed(key, variables, resolved);
            if (match != null) { named.add(key); if (variable == null) variable = match; }
        }
        if (named.size() == 1) return new String[]{"#" + variable, named.getFirst()};

        // 3. one non-particle variable, and an extra property that looks like a reference to something
        String only = null;
        int count = 0;
        for (String name : variables.keySet()) {
            if (name.equals("particle")) continue;
            only = name;
            count++;
        }
        if (count == 1) {
            List<String> hints = new ArrayList<>();
            for (String key : extra) {
                String value = stringValue(face.get(key));
                if (value != null && value.startsWith("#")) hints.add(key);
            }
            if (hints.size() == 1) return new String[]{"#" + only, hints.getFirst()};
        }
        return null;
    }

    private static String variableNamed(String key, Map<String, String> variables, Map<String, String> resolved) {
        String wanted = JavaModelResolver.qualified(key, "minecraft");
        for (Map.Entry<String, String> variable : variables.entrySet()) {
            if (variable.getKey().equals("particle") && variables.size() > 1) continue;
            String value = variable.getValue();
            if (value == null || value.startsWith("#")) value = resolved.get(variable.getKey());
            if (value != null && JavaModelResolver.qualified(value, "minecraft").equals(wanted)) return variable.getKey();
        }
        return null;
    }

    private static String stringValue(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : null;
    }

    /** Describes up to {@code limit} faces that still lack a texture, e.g. {@code element 0 face north: keys [uv, foo]}. */
    static String unrepaired(JsonArray elements, int limit) {
        if (elements == null) return "";
        List<String> found = new ArrayList<>();
        int total = 0;
        for (int index = 0; index < elements.size(); index++) {
            if (!elements.get(index).isJsonObject()) continue;
            JsonElement facesValue = elements.get(index).getAsJsonObject().get("faces");
            if (facesValue == null || !facesValue.isJsonObject()) continue;
            for (Map.Entry<String, JsonElement> face : facesValue.getAsJsonObject().entrySet()) {
                if (!face.getValue().isJsonObject() || face.getValue().getAsJsonObject().has("texture")) continue;
                total++;
                if (found.size() < limit) {
                    found.add("element " + index + " face " + face.getKey() + ": keys "
                            + new ArrayList<>(face.getValue().getAsJsonObject().keySet()));
                }
            }
        }
        if (found.isEmpty()) return "";
        String text = String.join("; ", found);
        return total > found.size() ? text + "; " + (total - found.size()) + " more" : text;
    }

    /** The one-line note for a model whose faces were repaired. */
    static String note(String model, Result result) {
        Map<String, Boolean> unique = new LinkedHashMap<>();
        for (String key : result.keys()) unique.put("\"" + key + "\"", true);
        String keys = String.join(", ", unique.keySet());
        return model + ": " + result.repaired() + " face(s) used non-standard key " + keys
                + " instead of \"texture\"; Java clients may show this model as missing - fix the source model";
    }
}
