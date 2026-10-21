/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.source;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ModelEngine's bone items read from the resource pack it generates, for when its model registry cannot be read
 * (an API that changed between ModelEngine versions, models still loading).
 *
 * <p>From Minecraft 1.21.4, ModelEngine R4 gives every rendered bone an item whose {@code item_model} component is
 * {@code modelengine:<model>/<bone>}, on the base item its config names ({@code Item-Model}, leather horse armour
 * by default), and writes the item definition to {@code resource pack/assets/modelengine/items/<model>/<bone>.json}.
 * With {@code Force-Custom-Model-Data: true} it uses custom model data instead, which the packs' item overrides
 * already describe; then nothing is read here.
 */
public final class ModelEngineFiles {
    private static final Pattern ITEM_MODEL = Pattern.compile("(?m)^\\s*Item-Model:\\s*['\"]?([A-Za-z0-9_:]+)['\"]?\\s*$");
    private static final Pattern FORCE_CMD = Pattern.compile("(?m)^\\s*Force-Custom-Model-Data:\\s*(true|false)\\s*$");
    private static final Pattern SAFE_PATH = Pattern.compile("[a-z0-9_./-]{1,200}");
    private static final int MAX_ITEMS = 50_000;

    private ModelEngineFiles() {}

    /** @param dataFolder ModelEngine's plugin folder */
    public static List<CustomItemDescriptor> descriptors(Path dataFolder) throws IOException {
        Path config = dataFolder.resolve("config.yml");
        String settings = Files.isRegularFile(config) ? Files.readString(config) : "";
        Matcher force = FORCE_CMD.matcher(settings);
        if (force.find() && force.group(1).equals("true")) return List.of();
        Matcher item = ITEM_MODEL.matcher(settings);
        String base = item.find() ? item.group(1).toLowerCase(Locale.ROOT) : "leather_horse_armor";
        if (!base.contains(":")) base = "minecraft:" + base;
        Path items = dataFolder.resolve("resource pack/assets/modelengine/items");
        if (!Files.isDirectory(items)) return List.of();
        Path root = items.toRealPath();
        List<CustomItemDescriptor> descriptors = new ArrayList<>();
        try (var files = Files.walk(root, 8)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                if (Files.isSymbolicLink(file) || !file.toRealPath().startsWith(root)) continue;
                String relative = root.relativize(file).toString().replace('\\', '/');
                if (!relative.endsWith(".json")) continue;
                String path = relative.substring(0, relative.length() - ".json".length());
                if (!SAFE_PATH.matcher(path).matches()) continue;
                descriptors.add(new CustomItemDescriptor("ModelEngine", base, Optional.of("modelengine:" + path),
                        OptionalInt.empty(), "modelengine:" + path));
                if (descriptors.size() >= MAX_ITEMS) break;
            }
        }
        return List.copyOf(descriptors);
    }
}
