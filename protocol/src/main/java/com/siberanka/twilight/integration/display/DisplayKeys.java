/* Copyright (c) 2026 siberanka. Twilight License (PolyForm Noncommercial 1.0.0 + terms), see LICENSE. */
package com.siberanka.twilight.integration.display;

import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Names of what Java displays and dropped items show, shared by the backend (which learns them) and the proxy (which
 * reports the ones it could not show): {@code model=<namespace:path>} for an item model,
 * {@code <namespace:item>|cmd=<n>} and {@code <namespace:item>|str=<text>} for custom model data,
 * {@code item=<namespace:item>} for an item without custom data (its own model) and
 * {@code block=<namespace:block>[name=value,...]} for the block state of a block display, properties sorted by name.
 */
public final class DisplayKeys {
    private static final String NAME = "[a-z0-9_]{1,32}=[a-z0-9_]{1,32}";
    private static final Pattern KEY = Pattern.compile("model=[a-z0-9_.-]{1,64}:[a-z0-9_./-]{1,256}"
            + "|[a-z0-9_.-]{1,64}:[a-z0-9_./-]{1,128}\\|(cmd=-?\\d{1,10}|str=[\\x21-\\x7b\\x7d\\x7e]{1,128})"
            + "|item=[a-z0-9_.-]{1,64}:[a-z0-9_./-]{1,128}"
            + "|block=[a-z0-9_.-]{1,64}:[a-z0-9_./-]{1,128}(\\[" + NAME + "(," + NAME + "){0,15}])?");

    private DisplayKeys() {}

    /** Whether {@code key} is well formed (anything else is ignored wherever keys arrive from). */
    public static boolean valid(String key) {
        return key != null && key.length() <= 400 && KEY.matcher(key).matches();
    }

    public static String model(String itemModel) {
        return "model=" + itemModel;
    }

    public static String customModelData(String baseItem, float value) {
        return baseItem + "|cmd=" + (int) Math.floor(value);
    }

    public static String customModelString(String baseItem, String value) {
        return baseItem + "|str=" + value;
    }

    /** An item shown with its own model (no custom model data, no item model of its own). */
    public static String item(String item) {
        return "item=" + item;
    }

    /** Whether a key names a custom item (rather than an item's own model or a block). */
    public static boolean custom(String key) {
        return !key.startsWith("item=") && !key.startsWith("block=");
    }

    /**
     * The key of a block state written as {@code namespace:block[name=value,...]} (Java's and Geyser's notation,
     * in any property order); null when it cannot be one.
     */
    public static String block(String state) {
        if (state == null) return null;
        int open = state.indexOf('[');
        String block = open < 0 ? state : state.substring(0, open);
        if (!block.contains(":")) block = "minecraft:" + block;
        if (open < 0) return "block=" + block;
        if (!state.endsWith("]")) return null;
        Map<String, String> properties = new TreeMap<>();
        String body = state.substring(open + 1, state.length() - 1);
        if (!body.isEmpty()) for (String pair : body.split(",")) {
            int equals = pair.indexOf('=');
            if (equals <= 0) return null;
            properties.put(pair.substring(0, equals).strip(), pair.substring(equals + 1).strip());
        }
        StringBuilder key = new StringBuilder("block=").append(block);
        if (!properties.isEmpty()) {
            key.append('[');
            properties.forEach((name, value) -> key.append(name).append('=').append(value).append(','));
            key.setCharAt(key.length() - 1, ']');
        }
        return key.toString();
    }

    /** The properties of a block key, in name order. */
    public static Map<String, String> blockProperties(String key) {
        Map<String, String> properties = new TreeMap<>();
        int open = key.indexOf('[');
        if (open < 0) return properties;
        for (String pair : key.substring(open + 1, key.length() - 1).split(",")) {
            int equals = pair.indexOf('=');
            properties.put(pair.substring(0, equals), pair.substring(equals + 1));
        }
        return properties;
    }

    /** The block of a block key ({@code namespace:block}). */
    public static String blockId(String key) {
        int open = key.indexOf('[');
        return key.substring("block=".length(), open < 0 ? key.length() : open);
    }
}
