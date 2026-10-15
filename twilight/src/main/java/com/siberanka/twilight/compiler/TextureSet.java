package com.siberanka.twilight.compiler;

import com.google.gson.JsonParser;
import com.siberanka.twilight.source.ResourceIndex;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class TextureSet {
    private final BufferedImage image;
    private final Map<String, Region> regions;

    private TextureSet(BufferedImage image, Map<String, Region> regions) {
        this.image = image;
        this.regions = regions;
    }

    static TextureSet atlas(ResourceIndex resources, List<String> identifiers) throws IOException {
        return atlas(resources, identifiers, null);
    }

    static TextureSet atlas(ResourceIndex resources, List<String> identifiers, VanillaAssetCache vanillaAssets) throws IOException {
        Map<String, BufferedImage> images = new LinkedHashMap<>();
        for (String identifier : identifiers) {
            if (images.containsKey(identifier)) continue;
            images.put(identifier, loadFrame(resources, identifier, vanillaAssets));
        }
        if (images.isEmpty()) throw new IOException("Model has no resolved textures");
        int width = images.values().stream().mapToInt(BufferedImage::getWidth).sum();
        int height = images.values().stream().mapToInt(BufferedImage::getHeight).max().orElseThrow();
        BufferedImage atlas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = atlas.createGraphics();
        Map<String, Region> regions = new LinkedHashMap<>();
        try {
            graphics.setComposite(AlphaComposite.Src);
            int x = 0;
            for (Map.Entry<String, BufferedImage> entry : images.entrySet()) {
                BufferedImage source = entry.getValue();
                graphics.drawImage(source, x, 0, null);
                regions.put(entry.getKey(), new Region(x, 0, source.getWidth(), source.getHeight()));
                x += source.getWidth();
            }
        } finally { graphics.dispose(); }
        return new TextureSet(atlas, Map.copyOf(regions));
    }

    static BufferedImage layeredIcon(ResourceIndex resources, List<String> identifiers) throws IOException {
        return layeredIcon(resources, identifiers, null);
    }

    static BufferedImage layeredIcon(ResourceIndex resources, List<String> identifiers, VanillaAssetCache vanillaAssets) throws IOException {
        List<BufferedImage> layers = new ArrayList<>();
        int width = 0, height = 0;
        for (String identifier : identifiers) {
            BufferedImage frame = loadFrame(resources, identifier, vanillaAssets);
            layers.add(frame);
            width = Math.max(width, frame.getWidth());
            height = Math.max(height, frame.getHeight());
        }
        if (layers.isEmpty()) throw new IOException("Flat item has no texture layers");
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = result.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            for (BufferedImage layer : layers) graphics.drawImage(layer, 0, 0, width, height, null);
        } finally { graphics.dispose(); }
        return result;
    }

    byte[] png() throws IOException { return png(image); }

    /** Java's missing texture, used where a model leaves a face's texture variable undefined. */
    static final String MISSING = "minecraft:missingno";
    /**
     * Textures the current conversion referenced that exist in no pack: Java draws its missing texture for
     * them, and so does the converted pack. The compiler reports them per item as notices.
     */
    static final ThreadLocal<java.util.Set<String>> MISSING_FILES = ThreadLocal.withInitial(java.util.LinkedHashSet::new);

    /** The texture file a model's sprite reference stands for: itself, or the file an atlas renames. */
    static String spriteTexture(ResourceIndex resources, String identifier) {
        if (resources.find(JavaModelResolver.texturePath(identifier)).isPresent()) return identifier;
        return resources.atlasSprites().texture(identifier).orElse(identifier);
    }

    /** The first frame of a sprite as Java samples it (atlas renames, animation strips, missing texture). */
    static BufferedImage frame(ResourceIndex resources, String sprite, VanillaAssetCache vanillaAssets) throws IOException {
        return loadFrame(resources, sprite, vanillaAssets);
    }

    private static BufferedImage loadFrame(ResourceIndex resources, String sprite, VanillaAssetCache vanillaAssets) throws IOException {
        if (sprite.equals(MISSING)) return missingTexture();
        String identifier = spriteTexture(resources, sprite);
        String path = JavaModelResolver.texturePath(identifier);
        var custom = resources.find(path);
        byte[] bytes;
        if (custom.isPresent()) bytes = custom.get().readBytes();
        else if (path.startsWith("assets/minecraft/textures/") && vanillaAssets != null
                && vanillaAssets.readTexture(path).isPresent()) {
            bytes = vanillaAssets.readTexture(path).orElseThrow();
        } else {
            MISSING_FILES.get().add(identifier);
            return missingTexture();
        }
        BufferedImage image = PngImages.read(bytes);
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) throw new IOException("Invalid PNG " + identifier);
        var metadata = resources.find(path + ".mcmeta");
        String animation = metadata.isPresent() ? metadata.get().readUtf8() : null;
        if (animation == null && custom.isEmpty() && vanillaAssets != null) {
            var vanillaMetadata = vanillaAssets.readTexture(path + ".mcmeta");
            if (vanillaMetadata.isPresent()) animation = new String(vanillaMetadata.get(), java.nio.charset.StandardCharsets.UTF_8);
        }
        return firstFrame(identifier, image, animation);
    }

    /** Java's 16x16 missing texture: magenta and black quarters, black in the top-left. */
    static BufferedImage missingTexture() {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            image.setRGB(x, y, (y < 8) ^ (x < 8) ? 0xFFF800F8 : 0xFF000000);
        }
        return image;
    }

    // Static item export samples the authored first frame. Never map an entire
    // animation sheet onto one face or guess animation from a PNG's aspect ratio.
    private static BufferedImage firstFrame(String identifier, BufferedImage image, String metadata) throws IOException {
        if (metadata == null) return image;
        try {
            var root = JsonParser.parseString(metadata).getAsJsonObject();
            if (!root.has("animation")) return image;
            var animation = root.getAsJsonObject("animation");
            int width = animation.has("width") ? animation.get("width").getAsInt() : -1;
            int height = animation.has("height") ? animation.get("height").getAsInt() : -1;
            if (width == -1 && height == -1) width = height = Math.min(image.getWidth(), image.getHeight());
            else {
                if (width == -1) width = image.getWidth();
                if (height == -1) height = image.getHeight();
            }
            if (width < 1 || height < 1 || image.getWidth() % width != 0 || image.getHeight() % height != 0)
                throw new IOException("Invalid animation frame dimensions for " + identifier);
            int frame = 0;
            if (animation.has("frames") && !animation.getAsJsonArray("frames").isEmpty()) {
                var first = animation.getAsJsonArray("frames").get(0);
                frame = first.isJsonObject() ? first.getAsJsonObject().get("index").getAsInt() : first.getAsInt();
            }
            int columns = image.getWidth() / width;
            if (frame < 0 || frame >= (long) columns * (image.getHeight() / height))
                throw new IOException("Invalid first animation frame for " + identifier);
            return image.getSubimage((frame % columns) * width, (frame / columns) * height, width, height);
        } catch (RuntimeException invalid) {
            throw new IOException("Invalid animation metadata for " + identifier, invalid);
        }
    }
    int width() { return image.getWidth(); }
    int sample(Region region, double u, double v) {
        int x = Math.clamp((int) Math.floor(u * region.width() / 16), 0, region.width() - 1);
        int y = Math.clamp((int) Math.floor(v * region.height() / 16), 0, region.height() - 1);
        return image.getRGB(region.x() + x, region.y() + y);
    }
    int height() { return image.getHeight(); }
    Region region(String identifier) throws IOException {
        Region region = regions.get(identifier);
        if (region == null) throw new IOException("Texture is outside atlas: " + identifier);
        return region;
    }

    static byte[] png(BufferedImage image) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "PNG", output)) throw new IOException("PNG encoder is unavailable");
        return output.toByteArray();
    }

    record Region(int x, int y, int width, int height) {}
}
