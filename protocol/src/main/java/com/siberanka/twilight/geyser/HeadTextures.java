/* Copyright (c) 2026 siberanka. Twilight License (PolyForm Noncommercial 1.0.0 + terms), see LICENSE. */
package com.siberanka.twilight.geyser;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Player head skins: Mojang serves every skin as {@code textures.minecraft.net/texture/<hash>}, and a head names it in
 * its profile's {@code textures} property (Base64 JSON). The hash is what Geyser registers a custom skull block by and
 * what Twilight keys a head's display model by.
 */
public final class HeadTextures {
    private static final Pattern HASH = Pattern.compile("[0-9a-f]{16,64}");
    private static final Pattern URL = Pattern.compile("textures\\.minecraft\\.net/texture/([0-9a-fA-F]{16,64})");
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");

    private HeadTextures() {}

    /** Whether a skin hash is well formed (lower-case hex, as Mojang's URLs use). */
    public static boolean valid(String hash) {
        return hash != null && HASH.matcher(hash).matches();
    }

    /** Whether text is a Minecraft user name (Geyser can look its skin up). */
    public static boolean name(String text) {
        return text != null && NAME.matcher(text).matches();
    }

    /** The hash of a skin URL ({@code http(s)://textures.minecraft.net/texture/<hash>}), or null. */
    public static String fromUrl(String url) {
        if (url == null) return null;
        Matcher matcher = URL.matcher(url);
        if (!matcher.find()) return null;
        String hash = matcher.group(1).toLowerCase(Locale.ROOT);
        return valid(hash) ? hash : null;
    }

    /** The skin hash of a profile's Base64 {@code textures} value, or null when it names no skin. */
    public static String fromValue(String base64) {
        if (base64 == null || base64.length() > 8192) return null;
        try {
            String json = new String(Base64.getMimeDecoder().decode(base64.strip()), StandardCharsets.UTF_8);
            return json.contains("SKIN") || json.contains("textures") ? fromUrl(json) : null;
        } catch (IllegalArgumentException notBase64) {
            return null;
        }
    }
}
