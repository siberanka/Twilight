/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.compiler;

import com.siberanka.twilight.config.TwilightConfig;
import com.siberanka.twilight.source.ContentSource;
import com.siberanka.twilight.source.CustomItemDescriptor;
import com.siberanka.twilight.source.ResourceIndex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Recoloured atlas sprites (armour trims) and builds that do not depend on the order providers list items in. */
class PalettedSpritesTest {
    @TempDir Path root;

    private void png(String path, int... argb) throws Exception {
        BufferedImage image = new BufferedImage(argb.length, 1, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < argb.length; i++) image.setRGB(i, 0, argb[i]);
        Path file = root.resolve(path);
        Files.createDirectories(file.getParent());
        ImageIO.write(image, "PNG", file.toFile());
    }

    private ResourceIndex index() throws Exception {
        return ResourceIndex.build(List.of(new ContentSource("test", ContentSource.Kind.RESOURCE_PACK, root, 1)),
                new TwilightConfig(false, true, false, false, 40, 100, 10_000_000, 1000, false, false, List.of(),
                        "auto", false, false, 3));
    }

    @Test void trimsAreRecolouredLikeJavasAtlas() throws Exception {
        int keyA = 0xFF101010, keyB = 0xFF202020, goldA = 0xFFFFD700, goldB = 0x80AA8800;
        png("assets/minecraft/textures/trims/items/helmet_trim.png", keyA, keyB, 0x00000000, 0xFF123456, 0x40101010);
        png("assets/minecraft/textures/palettes/trim_base.png", keyA, keyB);
        png("assets/minecraft/textures/palettes/trim/gold.png", goldA, goldB);
        Files.createDirectories(root.resolve("assets/minecraft/atlases"));
        Files.writeString(root.resolve("assets/minecraft/atlases/items.json"), """
                {"sources":[{"type":"minecraft:paletted_permutations","palette_key":"minecraft:trim_base",
                  "permutations":{"gold":"minecraft:trim/gold"},"textures":["minecraft:trims/items/helmet_trim"]}]}
                """);
        try (ResourceIndex index = index()) {
            BufferedImage sprite = PalettedSprites.sprite(index, null, "minecraft:trims/items/helmet_trim_gold").orElseThrow();
            assertEquals(goldA, sprite.getRGB(0, 0), "key colour 0 becomes the gold palette's colour 0");
            assertEquals(0x80AA8800, sprite.getRGB(1, 0), "alpha multiplies");
            assertEquals(0, sprite.getRGB(2, 0) >>> 24, "transparent stays transparent");
            assertEquals(0xFF123456, sprite.getRGB(3, 0), "colours outside the key stay");
            assertEquals(0x40, sprite.getRGB(4, 0) >>> 24, "the pixel's own alpha is kept");
            assertTrue(PalettedSprites.sprite(index, null, "minecraft:trims/items/helmet_trim_diamond").isEmpty());
            assertTrue(PalettedSprites.sprite(index, null, "minecraft:item/stick").isEmpty());
        }
    }

    @Test void olderPaletteLocationsWorkToo() throws Exception {
        png("assets/minecraft/textures/trims/items/boots_trim.png", 0xFF010101);
        png("assets/minecraft/textures/trims/color_palettes/trim_palette.png", 0xFF010101);
        png("assets/minecraft/textures/trims/color_palettes/iron.png", 0xFFCCCCCC);
        Files.createDirectories(root.resolve("assets/minecraft/atlases"));
        Files.writeString(root.resolve("assets/minecraft/atlases/blocks.json"), """
                {"sources":[{"type":"paletted_permutations","palette_key":"trims/color_palettes/trim_palette",
                  "permutations":{"iron":"trims/color_palettes/iron"},"textures":["trims/items/boots_trim"]}]}
                """);
        try (ResourceIndex index = index()) {
            assertEquals(0xFFCCCCCC, PalettedSprites.sprite(index, null, "trims/items/boots_trim_iron").orElseThrow().getRGB(0, 0));
        }
    }

    /** CraftEngine lists its items in another order on every start: the build must not change because of it. */
    @Test void candidatesDoNotDependOnTheProvidersOrder() throws Exception {
        List<CustomItemDescriptor> items = new ArrayList<>();
        for (String name : List.of("lantern", "torch", "melon", "anvil", "coil", "safe")) {
            Files.createDirectories(root.resolve("assets/default/items"));
            Files.writeString(root.resolve("assets/default/items/" + name + ".json"),
                    "{\"model\":{\"type\":\"model\",\"model\":\"default:item/" + name + "\"}}");
            items.add(new CustomItemDescriptor("CraftEngine", "minecraft:nether_brick", Optional.of("default:" + name),
                    OptionalInt.empty(), name));
        }
        try (ResourceIndex index = index()) {
            String first = new ItemCandidateCollector(index).collect(items).toString();
            Random random = new Random(3);
            for (int i = 0; i < 10; i++) {
                List<CustomItemDescriptor> shuffled = new ArrayList<>(items);
                Collections.shuffle(shuffled, random);
                assertEquals(first, new ItemCandidateCollector(index).collect(shuffled).toString());
            }
        }
    }
}
