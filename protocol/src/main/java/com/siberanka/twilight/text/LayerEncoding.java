/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.text;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Carries several layers of one Bedrock text in a single string for the labels of the
 * generated UI (chest title, action bar, boss bar names).
 *
 * <p>Bedrock's JSON UI can cut a string by UTF-8 length ({@code '%.Ns' * text}) and remove a
 * prefix ({@code text - prefix}), measured live on 4 October 2026. The sent string is a row of
 * fixed-size blocks, top layer first: the vanilla label shows block 0 and draws last, extra
 * labels show the following blocks below it. The bottom layer (usually the widest background)
 * comes last and needs no filling, which keeps boss bar names within Bedrock's 256 characters.
 * Text that is not layered is block 0 on its own and keeps its vanilla label.
 *
 * <p>Blocks are filled up with zero-width formatting codes ending with a reset, so the next
 * layer starts unformatted and Geyser's legacy-code normalisation (it adds a leading reset)
 * leaves every byte in place.
 */
public final class LayerEncoding {
    /** Block size of each surface in UTF-8 bytes. */
    public static final Map<String, Integer> BLOCK_BYTES = Map.of(
            TextLayoutTable.CHEST_LAYERS, 768, TextLayoutTable.ACTIONBAR_LAYERS, 768, TextLayoutTable.BOSS_LAYERS, 96);
    /**
     * Size of block 0 per surface (the top layer, with the zero-width markers). Boss bars get a larger first
     * block: text drawn over backgrounds (CustomNameplates) is the widest layer, the backgrounds below are short.
     */
    public static final Map<String, Integer> FIRST_BLOCK_BYTES = Map.of(
            TextLayoutTable.CHEST_LAYERS, 768, TextLayoutTable.ACTIONBAR_LAYERS, 768, TextLayoutTable.BOSS_LAYERS, 120);
    /** Bedrock shows at most this many characters of a boss bar name (measured: 256, then an ellipsis). */
    public static final int BOSS_CHARACTERS = 256;
    /** Zero-width start of a boss bar name whose bar Java draws transparent (the HUD then hides Bedrock's bar). */
    public static final String HIDDEN_BAR = "\u00a7o\u00a7r";
    /**
     * Zero-width start of a boss bar name whose bar the HUD draws with the pack's sprites: the hidden
     * marker (no vanilla bar), an italic reset, then the Java colour and overlay as colour-code digits
     * (protocol order), each after a reset so Geyser's normalisation inserts nothing.
     */
    public static String styledBar(int colour, int overlay) {
        if (overlay < 0 || overlay > 9) throw new IllegalArgumentException("overlay " + overlay);
        return styledBarColour(colour) + RESET + "\u00a7" + overlay + RESET;
    }

    /** Start of {@link #styledBar} shared by every overlay of a colour. */
    public static String styledBarColour(int colour) {
        if (colour < 0 || colour > 9) throw new IllegalArgumentException("colour " + colour);
        return HIDDEN_BAR + ITALIC + RESET + "\u00a7" + colour;
    }

    /**
     * Zero-width start of a layered boss bar name (before any bar marker). Boss names that are not layered
     * can be longer than block 0; the HUD shows them in one label, and the block labels only when this
     * marker is present.
     */
    public static final String LAYERED = "\u00a7k\u00a7r";

    /** Geyser starts every converted text with a reset. */
    public static final String CLIENT_PREFIX = "\u00a7r";
    private static final String RESET = "\u00a7r";
    private static final String ITALIC = "\u00a7o";

    private LayerEncoding() {}

    /**
     * @param layers     each layer as Bedrock legacy text (Geyser's conversion without the leading reset), bottom first
     * @param blockBytes block size of the surface
     * @param characters most characters the client shows, including Geyser's prefix
     * @return the string to send without Geyser's leading reset, or null when a layer does not fit its block
     */
    public static String encode(List<String> layers, int blockBytes, int characters) {
        return encode(layers, blockBytes, characters, "");
    }

    /** @param lead zero-width codes before block 0 (counted in it), such as {@link #HIDDEN_BAR} */
    public static String encode(List<String> layers, int blockBytes, int characters, String lead) {
        return encode(layers, blockBytes, blockBytes, characters, lead);
    }

    /**
     * @param firstBytes size of block 0
     * @param blockBytes size of every further block
     */
    public static String encode(List<String> layers, int firstBytes, int blockBytes, int characters, String lead) {
        if (layers.isEmpty() || layers.size() > LayeredTextLayout.MAX_LAYERS) return null;
        StringBuilder out = new StringBuilder(lead);
        int block = 0;
        for (int index = layers.size() - 1; index >= 0; index--, block++) {
            String layer = layers.get(index);
            // A reset right after the previous block's closing reset would be dropped by Geyser.
            while (layer.startsWith(RESET)) layer = layer.substring(RESET.length());
            out.append(layer);
            int size = block == 0 ? firstBytes : blockBytes;
            int used = bytes(CLIENT_PREFIX) + bytes(out.toString()) - blockStart(block, firstBytes, blockBytes);
            if (index == 0) {
                if (used > size) return null;
                break;
            }
            int remaining = size - used;
            if (remaining < bytes(RESET) || remaining % 3 != 0) return null;
            out.append(padding(remaining / 3));
        }
        return CLIENT_PREFIX.length() + out.length() > characters ? null : out.toString();
    }

    /** UTF-8 offset of a block: block 0 has its own size, the others share one. */
    public static int blockStart(int block, int firstBytes, int blockBytes) {
        return block == 0 ? 0 : firstBytes + (block - 1) * blockBytes;
    }

    /** {@code count} zero-width codes ending with a reset, never two resets in a row. */
    static String padding(int count) {
        StringBuilder out = new StringBuilder();
        if (count % 2 == 1) out.append(RESET);
        for (int index = 0; index < count / 2; index++) out.append(ITALIC).append(RESET);
        return out.toString();
    }

    public static int bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8).length;
    }
}
