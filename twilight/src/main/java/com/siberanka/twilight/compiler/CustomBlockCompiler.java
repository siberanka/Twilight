/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.compiler;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.siberanka.twilight.source.ResourceIndex;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Converts the custom blocks content plugins draw through vanilla block states (ItemsAdder's and CraftEngine's
 * note blocks, mushroom blocks and tripwire) into Bedrock blocks.
 *
 * <p>Java shows such a block through the block state's model in {@code assets/minecraft/blockstates}. Every
 * Java state with a custom model becomes one Bedrock block without properties, named after the Java state, so a
 * proxy's single Geyser registry gives a state the same Bedrock block on every backend while each server's pack
 * decides its look. Full cubes use Bedrock's unit cube with one texture per face; other shapes get a block
 * geometry. Twilight's Geyser runtime registers them ({@link #PATH}) and gives the custom items that place them
 * the block as icon and dropped model, as Java draws them in 3D.
 */
final class CustomBlockCompiler {
    /** Pack entry read by Twilight's and twilight-proxy's Geyser runtime (Bedrock ignores it). */
    static final String PATH = "twilight/geyser_blocks.json";
    static final String TEXTURE_FOLDER = "textures/twilight_blocks/";
    static final int FORMAT = 1;
    private static final String[] FACES = {"up", "down", "north", "south", "east", "west"};
    private static final List<String> BOOLEAN = List.of("false", "true");
    private static final List<String> SIX_FACES = List.of("down", "east", "north", "south", "up", "west");

    /**
     * Vanilla blocks content plugins reuse for custom blocks, with Java's property values. Java orders a state's
     * properties by name, which is also how Geyser names states.
     */
    static final Map<String, Map<String, List<String>>> BLOCKS;

    static {
        Map<String, Map<String, List<String>>> blocks = new TreeMap<>();
        Map<String, List<String>> note = new TreeMap<>();
        note.put("instrument", List.of("harp", "basedrum", "snare", "hat", "bass", "flute", "bell", "guitar", "chime",
                "xylophone", "iron_xylophone", "cow_bell", "didgeridoo", "bit", "banjo", "pling", "zombie", "skeleton",
                "creeper", "dragon", "wither_skeleton", "piglin", "custom_head"));
        List<String> notes = new ArrayList<>();
        for (int i = 0; i <= 24; i++) notes.add(Integer.toString(i));
        note.put("note", List.copyOf(notes));
        note.put("powered", BOOLEAN);
        blocks.put("note_block", note);
        Map<String, List<String>> mushroom = new TreeMap<>();
        for (String face : SIX_FACES) mushroom.put(face, BOOLEAN);
        blocks.put("brown_mushroom_block", mushroom);
        blocks.put("red_mushroom_block", mushroom);
        blocks.put("mushroom_stem", mushroom);
        Map<String, List<String>> tripwire = new TreeMap<>();
        for (String property : List.of("attached", "disarmed", "east", "north", "powered", "south", "west")) {
            tripwire.put(property, BOOLEAN);
        }
        blocks.put("tripwire", tripwire);
        BLOCKS = java.util.Collections.unmodifiableMap(blocks);
    }

    record Result(int blocks, int items, JsonObject file) {}

    private record Variant(String model, int x, int y, boolean uvlock) {}

    private final ResourceIndex resources;
    private final JavaModelResolver resolver;
    private final VanillaAssetCache vanilla;
    private final boolean vanillaOverride;
    private final Map<String, byte[]> packFiles;
    private final List<String> notices;
    private final JsonObject terrain = new JsonObject();

    CustomBlockCompiler(ResourceIndex resources, JavaModelResolver resolver, VanillaAssetCache vanilla,
                        boolean vanillaOverride, Map<String, byte[]> packFiles, List<String> notices) {
        this.resources = resources;
        this.resolver = resolver;
        this.vanilla = vanilla;
        this.vanillaOverride = vanillaOverride;
        this.packFiles = packFiles;
        this.notices = notices;
    }

    /**
     * @param itemModels Java item model -> Bedrock item identifier of the converted custom items; an item whose
     *                   model is a block's model places that block
     */
    Result compile(Map<String, String> itemModels) throws IOException {
        JsonArray blocks = new JsonArray();
        JsonObject items = new JsonObject();
        JsonArray geometries = new JsonArray();
        List<String> skipped = new ArrayList<>();
        int conflicts = 0;
        for (Map.Entry<String, Map<String, List<String>>> block : BLOCKS.entrySet()) {
            Map<String, Variant> states = new LinkedHashMap<>();
            String path = "assets/minecraft/blockstates/" + block.getKey() + ".json";
            // Content plugins each write their own states into the same file; Java uses the effective pack, the
            // others' states are merged in (the effective pack wins where two define the same state).
            for (ResourceIndex.Asset asset : resources.findAll(path)) {
                JsonObject root;
                try {
                    root = JsonParser.parseString(asset.readUtf8()).getAsJsonObject();
                } catch (RuntimeException invalid) {
                    notices.add(path + " from " + asset.source().provider() + " is not valid JSON; its blocks are skipped");
                    continue;
                }
                if (root.has("multipart") && !root.has("variants")) {
                    notices.add(path + " from " + asset.source().provider() + " uses multipart; its custom blocks keep the vanilla look on Bedrock");
                    continue;
                }
                if (!root.has("variants") || !root.get("variants").isJsonObject()) continue;
                for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("variants").entrySet()) {
                    Variant variant = variant(entry.getValue());
                    if (variant == null) continue;
                    List<String> expanded = expand(block.getValue(), entry.getKey());
                    if (expanded == null) {
                        skipped.add("minecraft:" + block.getKey() + "[" + entry.getKey() + "]");
                        continue;
                    }
                    for (String state : expanded) {
                        Variant previous = states.putIfAbsent(state, variant);
                        if (previous != null && !previous.equals(variant)) conflicts++;
                    }
                }
            }
            Map<Variant, Look> looks = new LinkedHashMap<>();
            for (Map.Entry<String, Variant> state : states.entrySet()) {
                Variant variant = state.getValue();
                Look look = looks.get(variant);
                if (look == null && !looks.containsKey(variant)) {
                    look = look(variant);
                    looks.put(variant, look);
                }
                if (look == null) continue;
                String javaState = "minecraft:" + block.getKey() + "[" + state.getKey() + "]";
                String name = name(block.getKey(), javaState);
                blocks.add(entry(block.getKey(), state.getKey(), javaState, name, look, variant, geometries));
                String item = itemModels.get(JavaModelResolver.qualified(variant.model(), "minecraft"));
                if (item == null && look.signature() != null) item = itemModels.get(look.signature());
                if (item != null && !items.has(item)) items.addProperty(item, name);
            }
        }
        if (!skipped.isEmpty()) {
            notices.add(skipped.size() + " block state(s) name properties or values the Java block does not have; Java ignores them too, e.g. "
                    + skipped.subList(0, Math.min(3, skipped.size())));
        }
        if (conflicts > 0) {
            notices.add(conflicts + " custom block state(s) are defined differently by two packs; the effective pack's look is used, as on Java");
        }
        if (!geometries.isEmpty()) {
            JsonObject geometry = new JsonObject();
            geometry.addProperty("format_version", "1.21.0");
            geometry.add("minecraft:geometry", geometries);
            packFiles.put("models/blocks/twilight_blocks.geo.json", BedrockPackCompiler.jsonBytes(geometry));
        }
        if (terrain.size() > 0) {
            JsonObject atlas = new JsonObject();
            atlas.addProperty("resource_pack_name", "twilight");
            atlas.addProperty("texture_name", "atlas.terrain");
            atlas.addProperty("padding", 8);
            atlas.addProperty("num_mip_levels", 4);
            atlas.add("texture_data", terrain);
            packFiles.put("textures/terrain_texture.json", BedrockPackCompiler.jsonBytes(atlas));
        }
        JsonObject file = new JsonObject();
        file.addProperty("format", FORMAT);
        file.add("blocks", blocks);
        file.add("items", items);
        if (!blocks.isEmpty()) packFiles.put(PATH, BedrockPackCompiler.jsonBytes(file));
        return new Result(blocks.size(), items.size(), file);
    }

    /** One Bedrock look: a unit cube with a texture per face, or a block geometry with one atlas. */
    private record Look(boolean cube, Map<String, String> faceTextures, String atlasTexture, JsonObject geometry,
                        String renderMethod, String signature) {}

    /**
     * A model's shape and art independent of its file and texture variable names: the elements with every face's
     * texture replaced by the texture file it draws. An item and a block with the same signature look the same.
     */
    static String signature(ResourceIndex resources, ResolvedJavaModel model) {
        if (model.elements() == null || model.elements().isEmpty()) return null;
        JsonArray elements = model.elements().deepCopy();
        for (JsonElement element : elements) {
            JsonObject faces = element.getAsJsonObject().getAsJsonObject("faces");
            if (faces == null) continue;
            for (Map.Entry<String, JsonElement> face : faces.entrySet()) {
                JsonObject value = face.getValue().getAsJsonObject();
                value.remove("cullface");
                if (!value.has("texture")) continue;
                String reference = value.get("texture").getAsString();
                String sprite = reference.startsWith("#") ? model.textures().get(reference.substring(1))
                        : JavaModelResolver.qualified(reference, "minecraft");
                value.addProperty("texture", sprite == null ? TextureSet.MISSING : TextureSet.spriteTexture(resources, sprite));
            }
        }
        return "look:" + elements;
    }

    private Look look(Variant variant) {
        ResolvedJavaModel model;
        try {
            model = resolver.resolve(variant.model(), true);
        } catch (IOException missing) {
            notices.add("custom block model " + variant.model() + ": " + missing.getMessage() + "; Bedrock keeps the vanilla block");
            return null;
        }
        try {
            if (!custom(model)) return null;
            if (model.elements() == null || model.elements().isEmpty()) {
                notices.add("custom block model " + variant.model() + " has no elements; Bedrock keeps the vanilla block");
                return null;
            }
            if (variant.uvlock() && (variant.x() != 0 || variant.y() != 0)) {
                notices.add("custom block model " + variant.model() + " locks its UVs while rotated; Bedrock rotates the textures with the block");
            }
            JsonObject cube = fullCube(model.elements());
            if (cube != null) {
                Map<String, String> faces = new LinkedHashMap<>();
                boolean translucent = false, cutout = false;
                for (String face : FACES) {
                    JsonObject javaFace = cube.getAsJsonObject("faces").getAsJsonObject(face);
                    BufferedImage image = faceImage(model, javaFace, face);
                    int alpha = alphaKind(image);
                    translucent |= alpha == 2;
                    cutout |= alpha == 1;
                    faces.put(face, texture(image));
                }
                return new Look(true, faces, null, null, translucent ? "blend" : cutout ? "alpha_test" : "opaque",
                        signature(resources, model));
            }
            List<String> used = new ArrayList<>();
            for (String texture : model.textures().values()) if (!used.contains(texture)) used.add(texture);
            for (JsonElement element : model.elements()) {
                JsonObject faces = element.getAsJsonObject().getAsJsonObject("faces");
                if (faces == null) continue;
                for (Map.Entry<String, JsonElement> face : faces.entrySet()) {
                    String reference = face.getValue().getAsJsonObject().has("texture")
                            ? face.getValue().getAsJsonObject().get("texture").getAsString() : null;
                    if (reference != null && reference.startsWith("#") && !model.textures().containsKey(reference.substring(1))
                            && !used.contains(TextureSet.MISSING)) used.add(TextureSet.MISSING);
                }
            }
            if (used.isEmpty()) used.add(TextureSet.MISSING);
            TextureSet atlas = TextureSet.atlas(resources, used, vanilla);
            BufferedImage image = PngImages.read(atlas.png());
            int alpha = alphaKind(image);
            boolean[] clamped = new boolean[1];
            JsonObject geometry = geometry(model, atlas, clamped);
            if (clamped[0]) {
                notices.add("custom block model " + variant.model() + " reaches past the space Bedrock allows a block (2 pixels"
                        + " less than Java on each side); its outermost pixels are pulled in");
            }
            return new Look(false, Map.of(), texture(image), geometry,
                    alpha == 2 ? "blend" : "alpha_test", signature(resources, model));
        } catch (IOException | RuntimeException failure) {
            notices.add("custom block model " + variant.model() + " could not be converted (" + failure.getMessage()
                    + "); Bedrock keeps the vanilla block");
            return null;
        }
    }

    private JsonObject entry(String block, String properties, String javaState, String name, Look look, Variant variant,
                             JsonArray geometries) {
        JsonObject entry = new JsonObject();
        entry.addProperty("name", name);
        entry.addProperty("state", javaState);
        JsonObject materials = new JsonObject();
        String key = "twilight_b_" + name.substring(name.lastIndexOf('_') + 1);
        if (look.cube()) {
            entry.addProperty("geometry", "minecraft:geometry.full_block");
            for (String face : FACES) {
                // Keys follow the Java state, so every backend's pack can draw the same Bedrock block its own way.
                String faceKey = key + "_" + face;
                terrainTexture(faceKey, look.faceTextures().get(face));
                materials.add(face, material(faceKey, look.renderMethod()));
            }
        } else {
            String geometry = "geometry." + key;
            JsonObject copy = look.geometry().deepCopy();
            copy.getAsJsonObject("description").addProperty("identifier", geometry);
            geometries.add(copy);
            entry.addProperty("geometry", geometry);
            terrainTexture(key, look.atlasTexture());
            materials.add("*", material(key, look.renderMethod()));
        }
        entry.add("materials", materials);
        if (variant.x() != 0 || variant.y() != 0) entry.add("rotation", BedrockPackCompiler.intArray(variant.x(), variant.y()));
        shape(block, properties, entry);
        return entry;
    }

    /** Java's collision and outline of the block, as Bedrock boxes (origin x mirrored like Geyser's). */
    static void shape(String block, String properties, JsonObject entry) {
        JsonArray collision = new JsonArray();
        JsonArray selection;
        if (block.equals("tripwire")) {
            boolean attached = properties.contains("attached=true");
            selection = BedrockPackCompiler.numberArray(-8, attached ? 1 : 0, -8, 16, attached ? 1.5 : 8, 16);
        } else {
            selection = BedrockPackCompiler.numberArray(-8, 0, -8, 16, 16, 16);
            collision.add(selection.deepCopy());
        }
        entry.add("selection", selection);
        entry.add("collision", collision);
    }

    private static JsonObject material(String texture, String renderMethod) {
        JsonObject material = new JsonObject();
        material.addProperty("texture", texture);
        material.addProperty("render_method", renderMethod);
        return material;
    }

    private void terrainTexture(String key, String path) {
        JsonObject value = new JsonObject();
        value.addProperty("textures", path);
        terrain.add(key, value);
    }

    /** Stores an image once and returns its pack path without extension. */
    private String texture(BufferedImage image) throws IOException {
        byte[] png = TextureSet.png(image);
        String path = TEXTURE_FOLDER + hash(png);
        packFiles.putIfAbsent(path + ".png", png);
        return path;
    }

    /**
     * Whether the model draws content-plugin art: a texture outside the minecraft namespace, or one a pack adds
     * or replaces (replacing vanilla textures needs vanilla-override, as for items).
     */
    private boolean custom(ResolvedJavaModel model) throws IOException {
        for (String sprite : model.textures().values()) {
            if (sprite.equals(TextureSet.MISSING)) continue;
            String texture = TextureSet.spriteTexture(resources, sprite);
            if (!JavaModelResolver.namespace(texture).equals("minecraft")) return true;
            String path = JavaModelResolver.texturePath(texture);
            if (resources.find(path).isEmpty()) continue;
            if (vanillaOverride || vanilla == null || vanilla.readTexture(path).isEmpty()) return true;
        }
        return false;
    }

    /** The single element of a model that fills the block with six faces, or null. */
    static JsonObject fullCube(JsonArray elements) {
        if (elements.size() != 1) return null;
        JsonObject element = elements.get(0).getAsJsonObject();
        if (element.has("rotation")) {
            JsonObject rotation = element.getAsJsonObject("rotation");
            if (rotation.has("angle") && rotation.get("angle").getAsDouble() != 0) return null;
        }
        if (!same(element.getAsJsonArray("from"), 0, 0, 0) || !same(element.getAsJsonArray("to"), 16, 16, 16)) return null;
        JsonObject faces = element.getAsJsonObject("faces");
        if (faces == null) return null;
        for (String face : FACES) if (!faces.has(face) || !faces.getAsJsonObject(face).has("texture")) return null;
        return element;
    }

    private static boolean same(JsonArray vector, double x, double y, double z) {
        return vector != null && vector.size() == 3 && vector.get(0).getAsDouble() == x
                && vector.get(1).getAsDouble() == y && vector.get(2).getAsDouble() == z;
    }

    /** The part of the face's texture Java draws (its UV rectangle, flipped and rotated as given). */
    private BufferedImage faceImage(ResolvedJavaModel model, JsonObject face, String name) throws IOException {
        String reference = face.get("texture").getAsString();
        String sprite = reference.startsWith("#") ? model.textures().get(reference.substring(1))
                : JavaModelResolver.qualified(reference, "minecraft");
        BufferedImage source = TextureSet.frame(resources, sprite == null ? TextureSet.MISSING : sprite, vanilla);
        double[] uv = face.has("uv") ? uv(face.getAsJsonArray("uv")) : new double[]{0, 0, 16, 16};
        int x0 = (int) Math.round(Math.min(uv[0], uv[2]) * source.getWidth() / 16.0);
        int x1 = (int) Math.round(Math.max(uv[0], uv[2]) * source.getWidth() / 16.0);
        int y0 = (int) Math.round(Math.min(uv[1], uv[3]) * source.getHeight() / 16.0);
        int y1 = (int) Math.round(Math.max(uv[1], uv[3]) * source.getHeight() / 16.0);
        if (x1 <= x0 || y1 <= y0) throw new IOException("empty UV on the " + name + " face");
        BufferedImage image = new BufferedImage(x1 - x0, y1 - y0, BufferedImage.TYPE_INT_ARGB);
        boolean flipX = uv[0] > uv[2], flipY = uv[1] > uv[3];
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            int sx = flipX ? x1 - 1 - x : x0 + x, sy = flipY ? y1 - 1 - y : y0 + y;
            image.setRGB(x, y, source.getRGB(sx, sy));
        }
        int rotation = face.has("rotation") ? Math.floorMod(face.get("rotation").getAsInt(), 360) : 0;
        for (int turn = 0; turn < rotation / 90; turn++) image = clockwise(image);
        return image;
    }

    private static BufferedImage clockwise(BufferedImage image) {
        BufferedImage rotated = new BufferedImage(image.getHeight(), image.getWidth(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            rotated.setRGB(image.getHeight() - 1 - y, x, image.getRGB(x, y));
        }
        return rotated;
    }

    private static double[] uv(JsonArray value) throws IOException {
        if (value.size() < 4) throw new IOException("face UV must contain four numbers");
        return new double[]{value.get(0).getAsDouble(), value.get(1).getAsDouble(), value.get(2).getAsDouble(), value.get(3).getAsDouble()};
    }

    /** 0 opaque, 1 cut-out (only fully transparent pixels), 2 translucent. */
    static int alphaKind(BufferedImage image) {
        int kind = 0;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            int alpha = image.getRGB(x, y) >>> 24;
            if (alpha == 255) continue;
            if (alpha != 0) return 2;
            kind = 1;
        }
        return kind;
    }

    /**
     * A block geometry of the model's elements in one bone. Bedrock mirrors a block geometry's x axis against the
     * world (as Geyser's collision boxes do), so Java's east is geometry -x and its east and west faces swap.
     */
    static JsonObject geometry(ResolvedJavaModel model, TextureSet atlas) throws IOException {
        return geometry(model, atlas, new boolean[1]);
    }

    /** @param clamped set when an element reached past Bedrock's block bounds and was cut */
    static JsonObject geometry(ResolvedJavaModel model, TextureSet atlas, boolean[] clamped) throws IOException {
        JsonArray cubes = new JsonArray();
        for (JsonElement value : model.elements()) {
            JsonObject element = InvertedElements.normalized(value.getAsJsonObject());
            double[] from = vector(element.getAsJsonArray("from"));
            double[] to = vector(element.getAsJsonArray("to"));
            JsonObject cube = new JsonObject();
            // Bedrock rejects block boxes outside -14..30 pixels of the block (Java allows -16..32): the outermost
            // pixels of such elements are pulled in (the box is clamped, its texture keeps its whole face).
            double x0 = Math.max(-22, 8 - to[0]), x1 = Math.min(22, 8 - from[0]);
            double y0 = Math.max(-14, from[1]), y1 = Math.min(30, to[1]);
            double z0 = Math.max(-22, from[2] - 8), z1 = Math.min(22, to[2] - 8);
            if (x1 < x0 || y1 < y0 || z1 < z0) continue;
            if (x0 != 8 - to[0] || x1 != 8 - from[0] || y0 != from[1] || y1 != to[1] || z0 != from[2] - 8 || z1 != to[2] - 8) clamped[0] = true;
            cube.add("origin", BedrockPackCompiler.numberArray(x0, y0, z0));
            cube.add("size", BedrockPackCompiler.numberArray(x1 - x0, y1 - y0, z1 - z0));
            if (element.has("rotation")) {
                JsonObject rotation = element.getAsJsonObject("rotation");
                double[] pivot = rotation.has("origin") ? vector(rotation.getAsJsonArray("origin")) : new double[]{8, 8, 8};
                double angle = rotation.has("angle") ? rotation.get("angle").getAsDouble() : 0;
                String axis = rotation.has("axis") ? rotation.get("axis").getAsString() : "y";
                cube.add("pivot", BedrockPackCompiler.numberArray(8 - pivot[0], pivot[1], pivot[2] - 8));
                cube.add("rotation", switch (axis) {
                    case "x" -> BedrockPackCompiler.numberArray(-angle, 0, 0);
                    case "z" -> BedrockPackCompiler.numberArray(0, 0, angle);
                    default -> BedrockPackCompiler.numberArray(0, -angle, 0);
                });
            }
            JsonObject uv = new JsonObject();
            JsonObject faces = element.getAsJsonObject("faces");
            if (faces != null) for (Map.Entry<String, JsonElement> faceEntry : faces.entrySet()) {
                JsonObject face = faceEntry.getValue().getAsJsonObject();
                if (!face.has("texture")) continue;
                String reference = face.get("texture").getAsString();
                String texture = reference.startsWith("#") ? model.textures().get(reference.substring(1))
                        : JavaModelResolver.qualified(reference, "minecraft");
                if (texture == null) texture = TextureSet.MISSING;
                TextureSet.Region region = atlas.region(texture);
                double[] faceUv = face.has("uv") ? uv(face.getAsJsonArray("uv"))
                        : BedrockPackCompiler.defaultFaceUv(faceEntry.getKey(), from, to);
                JsonObject mapped = new JsonObject();
                mapped.add("uv", BedrockPackCompiler.numberArray(region.x() + faceUv[0] * region.width() / 16.0,
                        region.y() + faceUv[1] * region.height() / 16.0));
                mapped.add("uv_size", BedrockPackCompiler.numberArray((faceUv[2] - faceUv[0]) * region.width() / 16.0,
                        (faceUv[3] - faceUv[1]) * region.height() / 16.0));
                if (face.has("rotation")) mapped.addProperty("uv_rotation", face.get("rotation").getAsInt());
                String bedrockFace = switch (faceEntry.getKey()) {
                    case "east" -> "west";
                    case "west" -> "east";
                    default -> faceEntry.getKey();
                };
                uv.add(bedrockFace, mapped);
            }
            cube.add("uv", uv);
            cubes.add(cube);
        }
        JsonObject description = new JsonObject();
        description.addProperty("identifier", "geometry.twilight_block");
        description.addProperty("texture_width", atlas.width());
        description.addProperty("texture_height", atlas.height());
        JsonObject bone = new JsonObject();
        bone.addProperty("name", "block");
        bone.add("pivot", BedrockPackCompiler.numberArray(0, 0, 0));
        bone.add("cubes", cubes);
        JsonArray bones = new JsonArray();
        bones.add(bone);
        JsonObject geometry = new JsonObject();
        geometry.add("description", description);
        geometry.add("bones", bones);
        return geometry;
    }

    private static double[] vector(JsonArray value) throws IOException {
        if (value == null || value.size() < 3) throw new IOException("element corner must contain three numbers");
        return new double[]{value.get(0).getAsDouble(), value.get(1).getAsDouble(), value.get(2).getAsDouble()};
    }

    private static Variant variant(JsonElement value) {
        JsonElement first = value.isJsonArray() ? (value.getAsJsonArray().isEmpty() ? null : value.getAsJsonArray().get(0)) : value;
        if (first == null || !first.isJsonObject() || !first.getAsJsonObject().has("model")) return null;
        JsonObject object = first.getAsJsonObject();
        int x = object.has("x") ? Math.floorMod(object.get("x").getAsInt(), 360) : 0;
        int y = object.has("y") ? Math.floorMod(object.get("y").getAsInt(), 360) : 0;
        if (x % 90 != 0 || y % 90 != 0) return null;
        return new Variant(object.get("model").getAsString(), x, y,
                object.has("uvlock") && object.get("uvlock").getAsBoolean());
    }

    /**
     * Every complete Java state a variant key selects, with properties in Java's order; null when the key names
     * a property or value the block does not have. An empty key or a partial one selects all matching states.
     */
    static List<String> expand(Map<String, List<String>> domain, String key) {
        Map<String, String> fixed = new TreeMap<>();
        if (!key.isBlank()) {
            for (String pair : key.split(",")) {
                int equals = pair.indexOf('=');
                if (equals <= 0) return null;
                String property = pair.substring(0, equals).trim().toLowerCase(Locale.ROOT);
                String value = pair.substring(equals + 1).trim().toLowerCase(Locale.ROOT);
                List<String> values = domain.get(property);
                if (values == null || !values.contains(value)) return null;
                fixed.put(property, value);
            }
        }
        List<String> states = new ArrayList<>();
        states.add("");
        for (Map.Entry<String, List<String>> property : domain.entrySet()) {
            List<String> values = fixed.containsKey(property.getKey()) ? List.of(fixed.get(property.getKey())) : property.getValue();
            List<String> next = new ArrayList<>(states.size() * values.size());
            for (String prefix : states) for (String value : values) {
                next.add(prefix + (prefix.isEmpty() ? "" : ",") + property.getKey() + "=" + value);
            }
            states = next;
        }
        return states;
    }

    /** The Bedrock block of a Java state: readable block name plus a hash of the complete state. */
    static String name(String block, String javaState) {
        return "twilight_" + block + "_" + hash(javaState.getBytes(StandardCharsets.UTF_8));
    }

    private static String hash(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)).substring(0, 12);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
