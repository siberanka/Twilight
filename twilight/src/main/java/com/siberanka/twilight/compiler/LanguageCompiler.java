/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.compiler;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.siberanka.twilight.source.ResourceIndex;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resource-pack translations, as Java players see them.
 *
 * <p>Packs override vanilla strings (an empty {@code container.inventory} hides the
 * inventory label of image menus, {@code container.enderchest} becomes a GUI image) and
 * supply names for datapack and plugin content (items, advancements). Java merges
 * {@code assets/<namespace>/lang/<locale>.json} from every pack and namespace, higher
 * packs winning. The merged strings per locale go to {@link #PATH} for the runtime
 * (Geyser renders Java text with them); keys Bedrock's own UI draws become Bedrock
 * language files.
 */
final class LanguageCompiler {
    static final String PATH = "twilight/lang/";
    /** Strings Bedrock's chest and inventory screens draw themselves (not sent by Geyser). */
    static final Set<String> BEDROCK_UI_KEYS = Set.of("container.inventory");
    private static final Pattern LANG = Pattern.compile("assets/[^/]+/lang/([a-z]{2,3}_[a-z0-9]{2,4})\\.json");

    private LanguageCompiler() {}

    record Result(int locales, int strings, List<String> problems) {}

    static Result compile(ResourceIndex resources, Map<String, byte[]> packFiles) {
        Map<String, Map<String, String>> locales = new TreeMap<>();
        List<String> problems = new ArrayList<>();
        for (String path : resources.paths().stream().sorted().toList()) {
            Matcher matcher = LANG.matcher(path);
            if (!matcher.matches()) continue;
            String locale = matcher.group(1);
            // Lowest-priority layer first, so higher packs overwrite shared keys.
            for (ResourceIndex.Asset asset : resources.findAll(path).reversed()) {
                try {
                    JsonElement root = JsonParser.parseString(asset.readUtf8());
                    if (!root.isJsonObject()) continue;
                    Map<String, String> strings = locales.computeIfAbsent(locale, ignored -> new TreeMap<>());
                    for (var entry : root.getAsJsonObject().entrySet()) {
                        if (entry.getValue().isJsonPrimitive()) strings.put(entry.getKey(), entry.getValue().getAsString());
                    }
                } catch (IOException | RuntimeException failure) {
                    problems.add(path + " (" + asset.source().provider() + "): " + failure.getMessage());
                }
            }
        }
        int strings = 0;
        for (var entry : locales.entrySet()) {
            JsonObject json = new JsonObject();
            entry.getValue().forEach(json::addProperty);
            packFiles.put(PATH + entry.getKey() + ".json", json.toString().getBytes(StandardCharsets.UTF_8));
            strings += entry.getValue().size();
            StringBuilder bedrock = new StringBuilder();
            for (String key : BEDROCK_UI_KEYS) {
                String value = entry.getValue().get(key);
                if (value == null) continue;
                // Bedrock treats a blank string as missing and shows its own label: a lone reset code draws nothing.
                if (value.isBlank()) value = "\u00a7r";
                bedrock.append(key).append('=').append(value.replace("\n", " ")).append("\t#\n");
            }
            if (!bedrock.isEmpty()) {
                packFiles.put("texts/" + bedrockLanguage(entry.getKey()) + ".lang", bedrock.toString().getBytes(StandardCharsets.UTF_8));
            }
        }
        return new Result(locales.size(), strings, problems);
    }

    /** Java {@code tr_tr} is Bedrock {@code tr_TR}. */
    static String bedrockLanguage(String javaLocale) {
        int split = javaLocale.indexOf('_');
        return javaLocale.substring(0, split) + '_' + javaLocale.substring(split + 1).toUpperCase(Locale.ROOT);
    }
}
