package com.siberanka.twilight.geyser;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

/**
 * Bedrock's normally obtainable offhand items, expressed as the ORIGINAL Java
 * material ID. A custom sword rendered as a shield is still a sword; a custom
 * shield rendered as a sword is still a shield.
 *
 * Geyser's custom item mapping option "allow_offhand" defaults to true and
 * must be explicitly false for every unsupported Java base material. These
 * mappings apply only to custom items; ordinary vanilla items remain Geyser's
 * responsibility. The Bukkit guard uses the same policy for attempted manual
 * transfers, including clients that bypass the Bedrock UI restriction.
 */
public final class BedrockOffhandPolicy {
    private static final Set<String> ALLOWED = Set.of(
            "minecraft:shield",
            "minecraft:totem_of_undying",
            "minecraft:arrow",
            "minecraft:tipped_arrow",
            "minecraft:firework_rocket",
            "minecraft:filled_map",
            "minecraft:nautilus_shell"
    );

    private BedrockOffhandPolicy() {
    }

    public static boolean allowsBaseItem(String javaMaterialKey) {
        return javaMaterialKey != null && ALLOWED.contains(javaMaterialKey);
    }

    /**
     * Apply after every other mapping transformer, so the embedded JSON, the
     * external Geyser mapping, and proxy-delivered mappings all agree. Does not
     * touch identifiers, predicates, priority, icons or display transforms.
     * Return the number of changed custom definitions.
     */
    public static int apply(Map<String, byte[]> packFiles) {
        String name = "twilight/geyser_item_mappings.json";
        byte[] bytes = packFiles.get(name);
        if (bytes == null) return 0;
        JsonObject root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject items = root.getAsJsonObject("items");
        if (items == null) return 0;
        int changed = 0;
        for (Map.Entry<String, JsonElement> base : items.entrySet()) {
            if (!base.getValue().isJsonArray()) continue;
            boolean supported = allowsBaseItem(base.getKey());
            JsonArray definitions = base.getValue().getAsJsonArray();
            for (JsonElement element : definitions) {
                if (!element.isJsonObject()) continue;
                JsonObject definition = element.getAsJsonObject();
                JsonObject options = definition.getAsJsonObject("bedrock_options");
                if (options == null) {
                    options = new JsonObject();
                    definition.add("bedrock_options", options);
                }
                if (supported) {
                    // Default in Geyser is true. Remove an outdated false set
                    // by another compiler pass, without gratuitous true fields.
                    if (options.has("allow_offhand") && !options.get("allow_offhand").getAsBoolean()) {
                        options.remove("allow_offhand");
                        changed++;
                    }
                } else if (!options.has("allow_offhand") || options.get("allow_offhand").getAsBoolean()) {
                    options.addProperty("allow_offhand", false);
                    changed++;
                }
            }
        }
        if (changed > 0) {
            packFiles.put(name, root.toString().getBytes(StandardCharsets.UTF_8));
        }
        return changed;
    }
}
