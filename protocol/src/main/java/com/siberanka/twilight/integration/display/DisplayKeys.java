/* Copyright (c) 2026 siberanka. Twilight License (PolyForm Noncommercial 1.0.0 + terms), see LICENSE. */
package com.siberanka.twilight.integration.display;

import java.util.regex.Pattern;

/**
 * Names of the items shown in Java item displays, shared by the backend (which learns them) and the proxy (which
 * reports the ones it could not show): {@code model=<namespace:path>} for an item model,
 * {@code <namespace:item>|cmd=<n>} and {@code <namespace:item>|str=<text>} for custom model data.
 */
public final class DisplayKeys {
    private static final Pattern KEY = Pattern.compile("model=[a-z0-9_.-]{1,64}:[a-z0-9_./-]{1,256}"
            + "|[a-z0-9_.-]{1,64}:[a-z0-9_./-]{1,128}\\|(cmd=-?\\d{1,10}|str=[\\x21-\\x7b\\x7d\\x7e]{1,128})");

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
}
