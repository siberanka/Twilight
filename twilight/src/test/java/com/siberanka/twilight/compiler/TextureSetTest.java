package com.siberanka.twilight.compiler;

import com.siberanka.twilight.config.TwilightConfig;
import com.siberanka.twilight.source.ContentSource;
import com.siberanka.twilight.source.ResourceIndex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TextureSetTest {
    @TempDir Path root;

    @Test
    void resolvesExplicitVanillaTexturesAndTheirAnimationMetadataFromVerifiedCache() throws Exception {
        texture();
        Path data = cacheVanillaTexture();
        try (ResourceIndex index = index(); VanillaAssetCache vanilla = new VanillaAssetCache(data, "26.2", false)) {
            TextureSet atlas = TextureSet.atlas(index, List.of("minecraft:block/fire_0"), vanilla);
            assertEquals(8, atlas.height());
            assertEquals(Color.BLUE.getRGB(), ImageIO.read(new ByteArrayInputStream(atlas.png())).getRGB(0,0));
            assertEquals(Color.BLUE.getRGB(), TextureSet.layeredIcon(index, List.of("minecraft:block/fire_0"), vanilla).getRGB(0,0));
            // A texture that exists in no pack is Java's missing texture, recorded for the build report.
            TextureSet.MISSING_FILES.get().clear();
            var missing = TextureSet.atlas(index, List.of("unknown:block/fire_0"), vanilla);
            assertEquals(0xFF000000, ImageIO.read(new ByteArrayInputStream(missing.png())).getRGB(0, 0));
            assertTrue(TextureSet.MISSING_FILES.get().contains("unknown:block/fire_0"));
        }
    }

    @Test
    void customVanillaTextureDoesNotInheritLowerPriorityAnimationMetadata() throws Exception {
        texture();
        Path data = cacheVanillaTexture();
        Path custom = root.resolve("assets/minecraft/textures/block/fire_0.png");
        Files.createDirectories(custom.getParent());
        Files.copy(root.resolve("assets/demo/textures/item/test.png"), custom);
        try (ResourceIndex index = index(); VanillaAssetCache vanilla = new VanillaAssetCache(data, "26.2", false)) {
            TextureSet atlas = TextureSet.atlas(index, List.of("minecraft:block/fire_0"), vanilla);
            assertEquals(16, atlas.height());
            assertEquals(Color.RED.getRGB(), ImageIO.read(new ByteArrayInputStream(atlas.png())).getRGB(0,0));
        }
    }

    private Path cacheVanillaTexture() throws Exception {
        Path data = root.resolve("data");
        Path cache = Files.createDirectories(data.resolve("cache/vanilla/26.2"));
        Path jar = cache.resolve("client.jar");
        try (var zip = new java.util.zip.ZipOutputStream(Files.newOutputStream(jar))) {
            zip.putNextEntry(new java.util.zip.ZipEntry("assets/minecraft/textures/block/fire_0.png"));
            zip.write(Files.readAllBytes(root.resolve("assets/demo/textures/item/test.png")));
            zip.closeEntry();
            zip.putNextEntry(new java.util.zip.ZipEntry("assets/minecraft/textures/block/fire_0.png.mcmeta"));
            zip.write("{\"animation\":{\"frames\":[1,0]}}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        Files.writeString(cache.resolve("client.sha1"), java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(jar))));
        return data;
    }

    @Test
    void atlasAndIconUseAuthoredFirstFrameInsteadOfTheWholeSheet() throws Exception {
        texture();
        Files.writeString(root.resolve("assets/demo/textures/item/test.png.mcmeta"),
                "{\"animation\":{\"frames\":[{\"index\":1,\"time\":3},0]}}");
        try (ResourceIndex index = index()) {
            TextureSet atlas = TextureSet.atlas(index, List.of("demo:item/test"));
            assertEquals(8, atlas.height());
            assertEquals(8, atlas.region("demo:item/test").height());
            assertEquals(Color.BLUE.getRGB(), ImageIO.read(new ByteArrayInputStream(atlas.png())).getRGB(0, 0));
            BufferedImage icon = TextureSet.layeredIcon(index, List.of("demo:item/test"));
            assertEquals(8, icon.getHeight());
            assertEquals(Color.BLUE.getRGB(), icon.getRGB(0, 0));
        }
    }

    @Test
    void retainsTallStaticTexturesWithoutAnimationMetadata() throws Exception {
        texture();
        try (ResourceIndex index = index()) {
            assertEquals(16, TextureSet.atlas(index, List.of("demo:item/test")).height());
            BufferedImage icon = TextureSet.layeredIcon(index, List.of("demo:item/test"));
            assertEquals(16, icon.getHeight());
            assertEquals(Color.BLUE.getRGB(), icon.getRGB(0, 15));
        }
    }

    @Test
    void rejectsOutOfBoundsAnimationFrames() throws Exception {
        texture();
        Files.writeString(root.resolve("assets/demo/textures/item/test.png.mcmeta"),
                "{\"animation\":{\"frames\":[2]}}");
        try (ResourceIndex index = index()) {
            assertThrows(java.io.IOException.class, () -> TextureSet.atlas(index, List.of("demo:item/test")));
        }
    }

    private void texture() throws Exception {
        Path path = root.resolve("assets/demo/textures/item/test.png");
        Files.createDirectories(path.getParent());
        BufferedImage image = new BufferedImage(8, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 8; x++)
            image.setRGB(x, y, (y < 8 ? Color.RED : Color.BLUE).getRGB());
        ImageIO.write(image, "PNG", path.toFile());
    }

    private ResourceIndex index() throws Exception {
        var config = new TwilightConfig(false, true, false, false, 40, 100, 10_000_000, 1000,
                false, false, List.of(), "auto", false, false, 3);
        return ResourceIndex.build(List.of(new ContentSource("test", ContentSource.Kind.RESOURCE_PACK, root, 1)), config);
    }
}
