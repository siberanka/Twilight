package com.siberanka.twilight.compiler;

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
    private final TextureAnimation.Animated animated;

    private TextureSet(BufferedImage image, Map<String, Region> regions, TextureAnimation.Animated animated) {
        this.image = image;
        this.regions = regions;
        this.animated = animated;
    }

    /** The animation of this atlas's sprites ({@code .mcmeta}), or null when nothing in it moves. */
    TextureAnimation.Animated animation() { return animated; }

    /** The atlas on a square canvas (Bedrock flipbooks are strips of square frames); regions stay where they are. */
    TextureSet squared() {
        if (animated == null) return this;
        TextureAnimation.Animated square = animated.squared();
        return new TextureSet(square.base(), regions, square);
    }

    /** Animation notes of the current conversion (invalid frame sizes, skipped frames), like {@link #MISSING_FILES}. */
    static final ThreadLocal<java.util.Set<String>> ANIMATION_NOTES = ThreadLocal.withInitial(java.util.LinkedHashSet::new);

    /** The animation of one sprite as a whole image (block faces are cut from it), or null for a still sprite. */
    static TextureAnimation.Animated animatedSprite(ResourceIndex resources, String sprite, VanillaAssetCache vanillaAssets)
            throws IOException {
        Loaded loaded = load(resources, sprite, vanillaAssets);
        if (loaded.track() == null) return null;
        return new TextureAnimation.Animated(loaded.first(), List.of(new TextureAnimation.Layer(loaded.track(), 0, 0)));
    }

    /** Whether any of the sprites has an animation. */
    static boolean anyAnimated(ResourceIndex resources, java.util.Collection<String> sprites, VanillaAssetCache vanillaAssets)
            throws IOException {
        for (String sprite : sprites) if (load(resources, sprite, vanillaAssets).track() != null) return true;
        return false;
    }

    private record Loaded(BufferedImage first, TextureAnimation.Track track) {}

    static TextureSet atlas(ResourceIndex resources, List<String> identifiers) throws IOException {
        return atlas(resources, identifiers, null);
    }

    static TextureSet atlas(ResourceIndex resources, List<String> identifiers, VanillaAssetCache vanillaAssets) throws IOException {
        Map<String, BufferedImage> images = new LinkedHashMap<>();
        Map<String, TextureAnimation.Track> tracks = new LinkedHashMap<>();
        for (String identifier : identifiers) {
            if (images.containsKey(identifier)) continue;
            Loaded loaded = load(resources, identifier, vanillaAssets);
            images.put(identifier, loaded.first());
            if (loaded.track() != null) tracks.put(identifier, loaded.track());
        }
        if (images.isEmpty()) throw new IOException("Model has no resolved textures");
        int width = images.values().stream().mapToInt(BufferedImage::getWidth).sum();
        int height = images.values().stream().mapToInt(BufferedImage::getHeight).max().orElseThrow();
        BufferedImage atlas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = atlas.createGraphics();
        Map<String, Region> regions = new LinkedHashMap<>();
        List<TextureAnimation.Layer> layers = new ArrayList<>();
        try {
            graphics.setComposite(AlphaComposite.Src);
            int x = 0;
            for (Map.Entry<String, BufferedImage> entry : images.entrySet()) {
                BufferedImage source = entry.getValue();
                graphics.drawImage(source, x, 0, null);
                regions.put(entry.getKey(), new Region(x, 0, source.getWidth(), source.getHeight()));
                if (tracks.containsKey(entry.getKey())) layers.add(new TextureAnimation.Layer(tracks.get(entry.getKey()), x, 0));
                x += source.getWidth();
            }
        } finally { graphics.dispose(); }
        return new TextureSet(atlas, Map.copyOf(regions),
                layers.isEmpty() ? null : new TextureAnimation.Animated(atlas, List.copyOf(layers)));
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

    /**
     * A sprite multiplied by a Java tint colour (grass, foliage, dyes, potions: Java multiplies the vertex colour
     * of faces with a tint index). Bedrock draws the pack's texture as is, so the colour is baked into a copy.
     */
    static String tinted(int argb, String sprite) {
        return argb == -1 ? sprite : TINT + String.format("%08x", argb) + "#" + sprite;
    }

    private static final String TINT = "tint#";
    /** Player skins a conversion draws ({@code skin#<hash>} sprites), set by the compiler for the build's thread. */
    static final ThreadLocal<java.util.Map<String, byte[]>> SKINS = ThreadLocal.withInitial(java.util.HashMap::new);
    static final String SKIN = "skin#";

    private static BufferedImage loadFrame(ResourceIndex resources, String sprite, VanillaAssetCache vanillaAssets) throws IOException {
        return load(resources, sprite, vanillaAssets).first();
    }

    private static BufferedImage tint(BufferedImage base, int argb) {
        BufferedImage tinted = new BufferedImage(base.getWidth(), base.getHeight(), BufferedImage.TYPE_INT_ARGB);
        int ta = argb >>> 24, tr = argb >> 16 & 255, tg = argb >> 8 & 255, tb = argb & 255;
        for (int y = 0; y < base.getHeight(); y++) for (int x = 0; x < base.getWidth(); x++) {
            int pixel = base.getRGB(x, y);
            tinted.setRGB(x, y, ((pixel >>> 24) * ta / 255) << 24 | ((pixel >> 16 & 255) * tr / 255) << 16
                    | ((pixel >> 8 & 255) * tg / 255) << 8 | (pixel & 255) * tb / 255);
        }
        return tinted;
    }

    private static Loaded load(ResourceIndex resources, String sprite, VanillaAssetCache vanillaAssets) throws IOException {
        if (sprite.startsWith(TINT)) {
            int split = sprite.indexOf('#', TINT.length());
            int argb = (int) Long.parseLong(sprite.substring(TINT.length(), split), 16);
            Loaded base = load(resources, sprite.substring(split + 1), vanillaAssets);
            return new Loaded(tint(base.first(), argb),
                    base.track() == null ? null : base.track().map(frame -> tint(frame, argb)));
        }
        if (sprite.startsWith(SKIN)) {
            byte[] skin = SKINS.get().get(sprite.substring(SKIN.length()));
            BufferedImage image = skin == null ? null : PngImages.read(skin);
            return new Loaded(image == null ? missingTexture() : image, null);
        }
        if (sprite.equals(MISSING)) return new Loaded(missingTexture(), null);
        String identifier = spriteTexture(resources, sprite);
        String path = JavaModelResolver.texturePath(identifier);
        var custom = resources.find(path);
        byte[] bytes;
        if (custom.isPresent()) bytes = custom.get().readBytes();
        else if (path.startsWith("assets/minecraft/textures/") && vanillaAssets != null
                && vanillaAssets.readTexture(path).isPresent()) {
            bytes = vanillaAssets.readTexture(path).orElseThrow();
        } else {
            // Recoloured sprites Java's atlases generate (armour trims) have no file of their own.
            var generated = PalettedSprites.sprite(resources, vanillaAssets, identifier);
            if (generated.isPresent()) return new Loaded(generated.get(), null);
            MISSING_FILES.get().add(identifier);
            return new Loaded(missingTexture(), null);
        }
        BufferedImage image = PngImages.read(bytes);
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) throw new IOException("Invalid PNG " + identifier);
        var metadata = resources.find(path + ".mcmeta");
        String animation = metadata.isPresent() ? metadata.get().readUtf8() : null;
        if (animation == null && custom.isEmpty() && vanillaAssets != null) {
            var vanillaMetadata = vanillaAssets.readTexture(path + ".mcmeta");
            if (vanillaMetadata.isPresent()) animation = new String(vanillaMetadata.get(), java.nio.charset.StandardCharsets.UTF_8);
        }
        List<String> notes = new ArrayList<>();
        TextureAnimation.Parsed parsed = TextureAnimation.parse(identifier, image, animation, notes);
        ANIMATION_NOTES.get().addAll(notes);
        return new Loaded(parsed.first(), parsed.track());
    }

    /** Java's 16x16 missing texture: magenta and black quarters, black in the top-left. */
    static BufferedImage missingTexture() {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            image.setRGB(x, y, (y < 8) ^ (x < 8) ? 0xFFF800F8 : 0xFF000000);
        }
        return image;
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
