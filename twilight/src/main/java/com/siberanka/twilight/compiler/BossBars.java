/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.compiler;

import com.siberanka.twilight.source.ResourceIndex;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Boss bar sprites of the Java packs.
 *
 * <p>Colours the packs draw invisibly (CustomNameplates and HUD plugins replace the white bar with
 * transparent sprites and use only the boss bar's name) are hidden on Bedrock. Colours whose
 * sprites a pack redraws (a custom health bar) are shown with those sprites: Bedrock tints one
 * white texture, so the generated HUD draws the Java background, progress and notch sprites
 * itself, as Java does ({@code BossHealthOverlay}).
 */
final class BossBars {
    /** Java's boss bar colours in protocol order. */
    static final List<String> COLOURS = List.of("pink", "blue", "red", "green", "yellow", "purple", "white");
    /** Java's notched overlays in protocol order (after {@code progress}, which has no overlay). */
    static final List<String> NOTCHES = List.of("notched_6", "notched_10", "notched_12", "notched_20");
    static final String JAVA_FOLDER = "assets/minecraft/textures/gui/sprites/boss_bar/";
    /** Bedrock texture folder of the converted sprites (without extension in UI definitions). */
    static final String BEDROCK_FOLDER = "textures/ui/twilight_boss_bar/";

    private BossBars() {}

    static Set<String> hidden(ResourceIndex resources) {
        Set<String> hidden = new TreeSet<>();
        for (String colour : COLOURS) {
            if (transparent(resources, colour + "_background") && transparent(resources, colour + "_progress")) {
                hidden.add(colour);
            }
        }
        return hidden;
    }

    /** Colours whose background or progress sprite a pack redraws visibly. */
    static Set<String> styled(ResourceIndex resources) {
        Set<String> hidden = hidden(resources);
        Set<String> styled = new TreeSet<>();
        for (String colour : COLOURS) {
            if (hidden.contains(colour)) continue;
            if (readable(resources, colour + "_background") || readable(resources, colour + "_progress")) {
                styled.add(colour);
            }
        }
        return styled;
    }

    /**
     * Bedrock textures for the styled colours and every notch overlay: the pack's sprite, else the
     * vanilla one (a pack may redraw only the progress sprite). Sprites that cannot be read are left out.
     *
     * @return the sprite names (for example {@code red_progress}) that were written
     */
    static Set<String> writeSprites(ResourceIndex resources, VanillaAssetCache vanilla, Set<String> styled,
                                    Map<String, byte[]> packFiles) {
        Set<String> written = new TreeSet<>();
        if (styled.isEmpty()) return written;
        List<String> sprites = new java.util.ArrayList<>();
        for (String colour : styled) {
            sprites.add(colour + "_background");
            sprites.add(colour + "_progress");
        }
        for (String notch : NOTCHES) {
            sprites.add(notch + "_background");
            sprites.add(notch + "_progress");
        }
        for (String sprite : sprites) {
            Optional<BufferedImage> image = image(resources, vanilla, sprite);
            if (image.isEmpty()) continue;
            try {
                packFiles.put(BEDROCK_FOLDER + sprite + ".png", TextureSet.png(image.get()));
                written.add(sprite);
            } catch (IOException ignored) {
                // an unencodable sprite is left out like an unreadable one
            }
        }
        return written;
    }

    /** Styled colours with at least one converted sprite (the others keep Bedrock's bar). */
    static Set<String> drawn(Set<String> styled, Set<String> sprites) {
        Set<String> drawn = new TreeSet<>();
        for (String colour : styled) {
            if (sprites.contains(colour + "_background") || sprites.contains(colour + "_progress")) drawn.add(colour);
        }
        return drawn;
    }

    private static Optional<BufferedImage> image(ResourceIndex resources, VanillaAssetCache vanilla, String sprite) {
        var asset = resources.find(JAVA_FOLDER + sprite + ".png");
        try {
            if (asset.isPresent()) return Optional.of(PngImages.read(asset.get().readBytes()));
            if (vanilla == null) return Optional.empty();
            Optional<byte[]> bytes = vanilla.readTexture(JAVA_FOLDER + sprite + ".png");
            return bytes.isEmpty() ? Optional.empty() : Optional.of(PngImages.read(bytes.get()));
        } catch (IOException | RuntimeException unreadable) {
            return Optional.empty();
        }
    }

    private static boolean readable(ResourceIndex resources, String sprite) {
        var asset = resources.find(JAVA_FOLDER + sprite + ".png");
        if (asset.isEmpty()) return false;
        try {
            PngImages.read(asset.get().readBytes());
            return true;
        } catch (IOException | RuntimeException unreadable) {
            return false;
        }
    }

    private static boolean transparent(ResourceIndex resources, String sprite) {
        var asset = resources.find(JAVA_FOLDER + sprite + ".png");
        if (asset.isEmpty()) return false;
        try {
            BufferedImage image = PngImages.read(asset.get().readBytes());
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    if ((image.getRGB(x, y) >>> 24) != 0) return false;
                }
            }
            return true;
        } catch (IOException | RuntimeException unreadable) {
            return false;
        }
    }
}
