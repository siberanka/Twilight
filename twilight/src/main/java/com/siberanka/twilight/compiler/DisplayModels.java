/* Copyright (c) 2026 siberanka. Twilight License (PolyForm Noncommercial 1.0.0 + terms), see LICENSE. */
package com.siberanka.twilight.compiler;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.siberanka.twilight.integration.display.DisplayKeys;
import com.siberanka.twilight.source.ResourceIndex;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Display models Java draws without a custom item: items with their own model ({@code item=} keys) and the block
 * states of block displays ({@code block=} keys), plus Java's extrusion of flat items.
 *
 * <p>Java draws a flat (generated) item as a slab one pixel thick with the sprite on both sides and the edge pixels
 * on its sides (ItemModelGenerator); Bedrock's own item rendering only applies to items it draws itself, so displays
 * need that slab as geometry. A block display draws the block state's model with the block's corner at the origin
 * (BlockDisplayRenderer), rotated as the block state file says, and tinted with the colours Java uses without a world
 * (BlockColors with no level).
 */
final class DisplayModels {
    private static final String[] FACES = {"down", "up", "north", "south", "west", "east"};
    private static final double EPSILON = 1e-4;

    private final ResourceIndex resources;
    private final VanillaAssetCache vanilla;
    private final JavaModelResolver resolver;
    private BufferedImage grass;

    DisplayModels(ResourceIndex resources, VanillaAssetCache vanilla, JavaModelResolver resolver) {
        this.resources = resources;
        this.vanilla = vanilla;
        this.resolver = resolver;
    }

    // ---------------------------------------------------------------- items with their own model

    /** A display variant for {@code item=<id>}; false (with a notice) when Java draws it with a special renderer. */
    boolean addItem(String id, DisplayEntityResources displays, List<String> notices) throws IOException {
        Optional<JsonObject> definition = definition("items", id);
        ResolvedJavaModel model;
        Map<Integer, Integer> tints = new LinkedHashMap<>();
        if (definition.isPresent()) {
            List<String> models = new ArrayList<>();
            if (!defaultModels(definition.get().getAsJsonObject("model"), JavaModelResolver.namespace(id), models, tints, 0)
                    || models.isEmpty()) {
                notices.add("display item " + id + ": Java draws it with a special renderer; it shows nothing on Bedrock");
                return false;
            }
            model = resolver.resolveAll(models, true);
        } else {
            model = resolver.resolve(JavaModelResolver.namespace(id) + ":item/" + id.substring(id.indexOf(':') + 1), true);
        }
        boolean flat = !model.isThreeDimensional();
        if (flat) {
            if (BedrockPackCompiler.flatLayers(model).isEmpty()) {
                notices.add("display item " + id + ": Java draws it with a special renderer; it shows nothing on Bedrock");
                return false;
            }
            model = extruded(model, tints);
        } else {
            model = tinted(model, tints);
        }
        add(displays, "vanilla_" + safe(id), model, flat, DisplayEntityResources.Section.VANILLA, id);
        return true;
    }

    /**
     * Java's default model of an item definition: the plain model, the false branch of conditions, the fallback of
     * selections and ranges, every model of a composite; false for special renderers and empty models.
     */
    private boolean defaultModels(JsonObject node, String namespace, List<String> models, Map<Integer, Integer> tints, int depth)
            throws IOException {
        if (node == null || depth > 64) return false;
        String type = JavaModelResolver.qualified(node.has("type") ? node.get("type").getAsString() : "model", "minecraft");
        switch (type) {
            case "minecraft:model" -> {
                if (!node.has("model")) return false;
                models.add(JavaModelResolver.qualified(node.get("model").getAsString(), namespace));
                if (node.has("tints") && tints.isEmpty()) {
                    JsonArray list = node.getAsJsonArray("tints");
                    for (int i = 0; i < list.size(); i++) tints.put(i, itemTint(list.get(i).getAsJsonObject()));
                }
                return true;
            }
            case "minecraft:condition" -> {
                return defaultModels(object(node, "on_false"), namespace, models, tints, depth + 1);
            }
            case "minecraft:select", "minecraft:range_dispatch" -> {
                JsonObject fallback = object(node, "fallback");
                if (fallback != null) return defaultModels(fallback, namespace, models, tints, depth + 1);
                String list = type.equals("minecraft:select") ? "cases" : "entries";
                if (!node.has(list) || node.getAsJsonArray(list).isEmpty()) return false;
                return defaultModels(object(node.getAsJsonArray(list).get(0).getAsJsonObject(), "model"), namespace, models,
                        tints, depth + 1);
            }
            case "minecraft:composite" -> {
                boolean any = false;
                if (node.has("models")) for (JsonElement child : node.getAsJsonArray("models")) {
                    any |= defaultModels(child.getAsJsonObject(), namespace, models, tints, depth + 1);
                }
                return any;
            }
            default -> {
                return false; // minecraft:special (chests, shields, heads, banners) and minecraft:empty
            }
        }
    }

    /** The colour an item tint source gives without the data Java would read (its default). */
    private int itemTint(JsonObject source) throws IOException {
        String type = JavaModelResolver.qualified(source.has("type") ? source.get("type").getAsString() : "", "minecraft");
        return switch (type) {
            case "minecraft:constant" -> source.has("value") ? color(source.get("value")) : -1;
            case "minecraft:grass" -> grassColor(source.has("temperature") ? source.get("temperature").getAsDouble() : 0.5,
                    source.has("downfall") ? source.get("downfall").getAsDouble() : 1.0);
            default -> source.has("default") ? color(source.get("default")) : -1;
        };
    }

    private static int color(JsonElement value) {
        if (value.isJsonArray()) {
            JsonArray rgb = value.getAsJsonArray();
            int r = (int) Math.round(rgb.get(0).getAsDouble() * 255), g = (int) Math.round(rgb.get(1).getAsDouble() * 255),
                    b = (int) Math.round(rgb.get(2).getAsDouble() * 255);
            return 0xFF000000 | r << 16 | g << 8 | b;
        }
        int argb = value.getAsInt();
        // Java tints are ARGB; colours written without alpha are opaque.
        return (argb >>> 24) == 0 ? argb | 0xFF000000 : argb;
    }

    /** GrassColor.get(temperature, downfall): the grass colour map at Java's coordinates. */
    private int grassColor(double temperature, double downfall) throws IOException {
        if (grass == null) grass = TextureSet.frame(resources, "minecraft:colormap/grass", vanilla);
        double t = Math.clamp(temperature, 0, 1), d = Math.clamp(downfall, 0, 1) * t;
        int x = (int) ((1 - t) * 255), y = (int) ((1 - d) * 255);
        if (grass.getWidth() != 256 || grass.getHeight() != 256) return 0xFF91BD59;
        return grass.getRGB(x, y) | 0xFF000000;
    }

    // ---------------------------------------------------------------- flat items

    /** A custom item Java draws flat: its display variant is Java's extrusion of its layers. */
    void addFlatItem(String identifier, ResolvedJavaModel flat, DisplayEntityResources displays) throws IOException {
        add(displays, "flat_" + safe(identifier), extruded(flat, Map.of()), true, DisplayEntityResources.Section.ITEM, identifier);
    }

    /**
     * Java's extrusion of a generated item: per layer, the opaque pixels as rectangles one pixel thick (z 7.5 to 8.5),
     * front and back showing the sprite, sides showing the edge pixels. Later layers sit a hair outside earlier ones,
     * where Java draws them over the same depth.
     */
    ResolvedJavaModel extruded(ResolvedJavaModel flat, Map<Integer, Integer> tints) throws IOException {
        List<String> layers = BedrockPackCompiler.flatLayers(flat);
        JsonArray elements = new JsonArray();
        Map<String, String> textures = new LinkedHashMap<>(flat.textures());
        for (int layer = 0; layer < layers.size(); layer++) {
            String variable = "layer" + layer;
            Integer tint = tints.get(layer);
            if (tint != null && tint != -1) {
                variable = "tinted" + layer;
                textures.put(variable, TextureSet.tinted(tint, layers.get(layer)));
            }
            BufferedImage image = TextureSet.frame(resources, layers.get(layer), vanilla);
            int width = image.getWidth(), height = image.getHeight();
            boolean[][] opaque = new boolean[height][width];
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) opaque[y][x] = (image.getRGB(x, y) >>> 24) != 0;
            double front = 7.5 - layer * 0.01, back = 8.5 + layer * 0.01;
            for (int[] rectangle : rectangles(opaque)) {
                elements.add(slab(rectangle, width, height, front, back, "#" + variable));
                if (elements.size() > 4096) throw new IOException("Flat item sprite is too detailed to extrude");
            }
        }
        if (elements.isEmpty()) throw new IOException("Flat item has no opaque pixels");
        return new ResolvedJavaModel(flat.identifier(), elements, Map.copyOf(textures), flat.display(), flat.handheld(),
                flat.frontLight());
    }

    /** Greedy cover of the opaque pixels with rectangles {x0, y0, x1, y1} (exclusive ends). */
    static List<int[]> rectangles(boolean[][] opaque) {
        int height = opaque.length, width = height == 0 ? 0 : opaque[0].length;
        boolean[][] used = new boolean[height][width];
        List<int[]> result = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!opaque[y][x] || used[y][x]) continue;
                int x1 = x;
                while (x1 < width && opaque[y][x1] && !used[y][x1]) x1++;
                int y1 = y + 1;
                grow:
                while (y1 < height) {
                    for (int i = x; i < x1; i++) if (!opaque[y1][i] || used[y1][i]) break grow;
                    y1++;
                }
                for (int row = y; row < y1; row++) for (int i = x; i < x1; i++) used[row][i] = true;
                result.add(new int[]{x, y, x1, y1});
            }
        }
        return result;
    }

    private static JsonObject slab(int[] r, int width, int height, double front, double back, String texture) {
        double pw = 16.0 / width, ph = 16.0 / height;
        double u0 = r[0] * pw, u1 = r[2] * pw, v0 = r[1] * ph, v1 = r[3] * ph;
        JsonObject element = new JsonObject();
        element.add("from", BedrockPackCompiler.numberArray(u0, 16 - v1, front));
        element.add("to", BedrockPackCompiler.numberArray(u1, 16 - v0, back));
        JsonObject faces = new JsonObject();
        faces.add("south", face(texture, u0, v0, u1, v1));
        faces.add("north", face(texture, u1, v0, u0, v1));
        faces.add("up", face(texture, u0, v0, u1, v0 + ph));
        faces.add("down", face(texture, u0, v1 - ph, u1, v1));
        faces.add("west", face(texture, u0, v0, u0 + pw, v1));
        faces.add("east", face(texture, u1 - pw, v0, u1, v1));
        element.add("faces", faces);
        return element;
    }

    private static JsonObject face(String texture, double u0, double v0, double u1, double v1) {
        JsonObject face = new JsonObject();
        face.add("uv", BedrockPackCompiler.numberArray(u0, v0, u1, v1));
        face.addProperty("texture", texture);
        return face;
    }

    // ---------------------------------------------------------------- block states

    /** A display variant for a block state key; false (with a notice) when Java draws no model for it. */
    boolean addBlock(String key, DisplayEntityResources displays, List<String> notices) throws IOException {
        String block = DisplayKeys.blockId(key);
        Map<String, String> state = DisplayKeys.blockProperties(key);
        Optional<JsonObject> definition = definition("blockstates", block);
        if (definition.isEmpty()) {
            notices.add("block display " + key + ": no block state file; it shows nothing on Bedrock");
            return false;
        }
        List<Applied> applied = applied(definition.get(), state);
        JsonArray elements = new JsonArray();
        Map<String, String> textures = new LinkedHashMap<>();
        int tint = blockTint(block, state);
        for (int part = 0; part < applied.size(); part++) {
            Applied model = applied.get(part);
            ResolvedJavaModel resolved = resolver.resolve(model.model(), true);
            if (resolved.elements() == null || resolved.elements().isEmpty()) continue;
            String prefix = "p" + part + "_";
            for (Map.Entry<String, String> texture : resolved.textures().entrySet()) {
                textures.put(prefix + texture.getKey(), texture.getValue());
            }
            for (JsonElement value : bake(resolved.elements(), model.x(), model.y(), model.uvlock())) {
                JsonObject element = value.getAsJsonObject();
                JsonObject faces = element.getAsJsonObject("faces");
                if (faces != null) for (Map.Entry<String, JsonElement> entry : faces.entrySet()) {
                    JsonObject face = entry.getValue().getAsJsonObject();
                    if (!face.has("texture")) continue;
                    String reference = face.get("texture").getAsString();
                    if (!reference.startsWith("#")) continue;
                    String variable = prefix + reference.substring(1);
                    if (face.has("tintindex") && tint != -1 && textures.containsKey(variable)) {
                        String tinted = "tint_" + variable;
                        textures.put(tinted, TextureSet.tinted(tint, textures.get(variable)));
                        variable = tinted;
                    }
                    face.addProperty("texture", "#" + variable);
                }
                elements.add(element);
            }
        }
        if (elements.isEmpty()) {
            notices.add("block display " + key + ": Java draws no block model for it (a block entity or an invisible "
                    + "block); it shows nothing on Bedrock");
            return false;
        }
        // BlockDisplayRenderer draws the model from the block's corner and without the item renderer's half turn;
        // the display variant's mesh is centred and turned, so this transform undoes both.
        JsonObject display = new JsonObject();
        JsonObject none = new JsonObject();
        none.add("translation", BedrockPackCompiler.numberArray(-8, 8, -8));
        none.add("rotation", BedrockPackCompiler.numberArray(0, 180, 0));
        display.add("none", none);
        ResolvedJavaModel model = new ResolvedJavaModel(block, elements, Map.copyOf(textures), display, false, false);
        add(displays, "block_" + Integer.toHexString(key.hashCode()), model, false, DisplayEntityResources.Section.BLOCK, key);
        return true;
    }

    record Applied(String model, int x, int y, boolean uvlock) {}

    /** The models Java draws for a block state: the first matching variant, or every matching multipart case. */
    static List<Applied> applied(JsonObject definition, Map<String, String> state) {
        List<Applied> result = new ArrayList<>();
        if (definition.has("variants")) {
            for (Map.Entry<String, JsonElement> entry : definition.getAsJsonObject("variants").entrySet()) {
                if (!variantMatches(entry.getKey(), state)) continue;
                result.add(model(entry.getValue()));
                return result;
            }
        }
        if (definition.has("multipart")) {
            for (JsonElement value : definition.getAsJsonArray("multipart")) {
                JsonObject part = value.getAsJsonObject();
                if (part.has("when") && !when(part.getAsJsonObject("when"), state)) continue;
                result.add(model(part.get("apply")));
            }
        }
        return result;
    }

    private static boolean variantMatches(String key, Map<String, String> state) {
        if (key.isEmpty() || key.equals("normal")) return true;
        for (String pair : key.split(",")) {
            int equals = pair.indexOf('=');
            if (equals < 0 || !pair.substring(equals + 1).equals(state.get(pair.substring(0, equals)))) return false;
        }
        return true;
    }

    private static boolean when(JsonObject condition, Map<String, String> state) {
        if (condition.has("OR")) {
            for (JsonElement any : condition.getAsJsonArray("OR")) if (when(any.getAsJsonObject(), state)) return true;
            return false;
        }
        if (condition.has("AND")) {
            for (JsonElement all : condition.getAsJsonArray("AND")) if (!when(all.getAsJsonObject(), state)) return false;
            return true;
        }
        for (Map.Entry<String, JsonElement> entry : condition.entrySet()) {
            String expected = entry.getValue().getAsString(), actual = state.get(entry.getKey());
            boolean negate = expected.startsWith("!");
            if (negate) expected = expected.substring(1);
            boolean any = false;
            for (String option : expected.split("\\|")) any |= option.equals(actual);
            if (any == negate) return false;
        }
        return true;
    }

    /** A variant or apply entry; of a weighted list, the first model (Java picks one by its fixed seed). */
    private static Applied model(JsonElement value) {
        JsonObject object = value.isJsonArray() ? value.getAsJsonArray().get(0).getAsJsonObject() : value.getAsJsonObject();
        int x = object.has("x") ? Math.floorMod(object.get("x").getAsInt(), 360) : 0;
        int y = object.has("y") ? Math.floorMod(object.get("y").getAsInt(), 360) : 0;
        return new Applied(JavaModelResolver.qualified(object.get("model").getAsString(), "minecraft"), x, y,
                object.has("uvlock") && object.get("uvlock").getAsBoolean());
    }

    /** BlockColors without a level: the default grass and foliage colours Java tints such blocks with. */
    private int blockTint(String block, Map<String, String> state) throws IOException {
        String name = block.startsWith("minecraft:") ? block.substring(10) : null;
        if (name == null) return -1;
        return switch (name) {
            case "grass_block", "short_grass", "tall_grass", "fern", "large_fern", "potted_fern", "bush" -> grassColor(0.5, 1.0);
            case "oak_leaves", "jungle_leaves", "acacia_leaves", "dark_oak_leaves", "mangrove_leaves", "vine" -> 0xFF48B518;
            case "birch_leaves" -> 0xFF80A755;
            case "spruce_leaves" -> 0xFF619961;
            case "lily_pad" -> 0xFF71C35C;
            case "redstone_wire" -> redstone(Integer.parseInt(state.getOrDefault("power", "0")));
            case "pumpkin_stem", "melon_stem" -> {
                int age = Integer.parseInt(state.getOrDefault("age", "0"));
                yield 0xFF000000 | (age * 32) << 16 | (255 - age * 8) << 8 | age * 4;
            }
            case "attached_pumpkin_stem", "attached_melon_stem" -> 0xFFE0C71C;
            default -> -1;
        };
    }

    /** RedStoneWireBlock.getColorForPower. */
    private static int redstone(int power) {
        float level = Math.clamp(power, 0, 15) / 15f;
        float r = level * 0.6f + (level > 0 ? 0.4f : 0.3f);
        float g = Math.clamp(level * level * 0.7f - 0.5f, 0, 1);
        float b = Math.clamp(level * level * 0.6f - 0.7f, 0, 1);
        return 0xFF000000 | (int) (r * 255) << 16 | (int) (g * 255) << 8 | (int) (b * 255);
    }

    // ---------------------------------------------------------------- rotation baking

    /**
     * Elements rotated as a block state's {@code x} and {@code y} rotate them (BlockModelRotation: around the block's
     * centre, first X by -x, then Y by -y), faces keeping the texture they show: the element rotation turns with them,
     * and {@code uvlock} keeps textures aligned with the world as Java does for faces with default UVs.
     */
    static JsonArray bake(JsonArray elements, int x, int y, boolean uvlock) {
        if (x == 0 && y == 0) return elements.deepCopy();
        JsonArray result = new JsonArray();
        for (JsonElement value : elements) {
            JsonObject element = value.getAsJsonObject();
            double[] from = vector(element.getAsJsonArray("from")), to = vector(element.getAsJsonArray("to"));
            double[] min = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE}, max = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
            for (int corner = 0; corner < 8; corner++) {
                double[] point = rotate(new double[]{(corner & 1) == 0 ? from[0] : to[0], (corner & 2) == 0 ? from[1] : to[1],
                        (corner & 4) == 0 ? from[2] : to[2]}, x, y);
                for (int axis = 0; axis < 3; axis++) {
                    min[axis] = Math.min(min[axis], point[axis]);
                    max[axis] = Math.max(max[axis], point[axis]);
                }
            }
            JsonObject baked = element.deepCopy();
            baked.add("from", BedrockPackCompiler.numberArray(round(min[0]), round(min[1]), round(min[2])));
            baked.add("to", BedrockPackCompiler.numberArray(round(max[0]), round(max[1]), round(max[2])));
            if (element.has("rotation")) baked.add("rotation", rotation(element.getAsJsonObject("rotation"), x, y));
            JsonObject faces = element.getAsJsonObject("faces");
            if (faces != null) {
                JsonObject turned = new JsonObject();
                for (Map.Entry<String, JsonElement> entry : faces.entrySet()) {
                    String direction = direction(rotate(normal(entry.getKey()), x, y, false));
                    JsonObject face = entry.getValue().getAsJsonObject().deepCopy();
                    double[] uv = face.has("uv") ? vector4(face.getAsJsonArray("uv"))
                            : BedrockPackCompiler.defaultFaceUv(entry.getKey(), from, to);
                    int faceRotation = face.has("rotation") ? Math.floorMod(face.get("rotation").getAsInt(), 360) : 0;
                    face.remove("rotation");
                    if (face.has("cullface")) face.addProperty("cullface", direction(rotate(normal(face.get("cullface").getAsString()), x, y, false)));
                    if (uvlock) {
                        face.add("uv", BedrockPackCompiler.numberArray(BedrockPackCompiler.defaultFaceUv(direction, min, max)));
                    } else {
                        double[][] positions = positions(entry.getKey(), from, to), turnedPositions = positions(direction, min, max);
                        double[][] uvs = new double[4][];
                        for (int i = 0; i < 4; i++) {
                            double[] rotated = rotate(positions[i], x, y);
                            for (int k = 0; k < 4; k++) if (same(rotated, turnedPositions[k])) uvs[k] = vertexUv(uv, faceRotation, i);
                        }
                        int solved = solve(uvs, face);
                        if (solved < 0) face.add("uv", BedrockPackCompiler.numberArray(uv));
                        else if (solved != 0) face.addProperty("rotation", solved * 90);
                    }
                    turned.add(direction, face);
                }
                baked.add("faces", turned);
            }
            result.add(baked);
        }
        return result;
    }

    /** FaceBakery: the UV Java gives vertex {@code i} of a face with UV rectangle {@code uv} and rotation. */
    private static double[] vertexUv(double[] uv, int rotation, int i) {
        int j = (i + rotation / 90) % 4;
        return new double[]{j == 0 || j == 1 ? uv[0] : uv[2], j == 0 || j == 3 ? uv[1] : uv[3]};
    }

    /** The UV rectangle and rotation that give each vertex its UV; the rotation (quarter turns) or -1. */
    private static int solve(double[][] uvs, JsonObject face) {
        for (double[] uv : uvs) if (uv == null) return -1;
        for (int rotation = 0; rotation < 4; rotation++) {
            double[] start = null, end = null;
            for (int k = 0; k < 4; k++) {
                int j = (k + rotation) % 4;
                if (j == 0) start = uvs[k];
                if (j == 2) end = uvs[k];
            }
            double[] rect = {start[0], start[1], end[0], end[1]};
            boolean fits = true;
            for (int k = 0; k < 4 && fits; k++) {
                double[] expected = vertexUv(rect, rotation * 90, k);
                fits = Math.abs(expected[0] - uvs[k][0]) < EPSILON && Math.abs(expected[1] - uvs[k][1]) < EPSILON;
            }
            if (fits) {
                face.add("uv", BedrockPackCompiler.numberArray(rect));
                return rotation;
            }
        }
        return -1;
    }

    /** FaceInfo: the four vertices of a face, in the order Java assigns UVs to them. */
    static double[][] positions(String face, double[] f, double[] t) {
        return switch (face) {
            case "down" -> new double[][]{{f[0], f[1], t[2]}, {f[0], f[1], f[2]}, {t[0], f[1], f[2]}, {t[0], f[1], t[2]}};
            case "up" -> new double[][]{{f[0], t[1], f[2]}, {f[0], t[1], t[2]}, {t[0], t[1], t[2]}, {t[0], t[1], f[2]}};
            case "north" -> new double[][]{{t[0], t[1], f[2]}, {t[0], f[1], f[2]}, {f[0], f[1], f[2]}, {f[0], t[1], f[2]}};
            case "south" -> new double[][]{{f[0], t[1], t[2]}, {f[0], f[1], t[2]}, {t[0], f[1], t[2]}, {t[0], t[1], t[2]}};
            case "west" -> new double[][]{{f[0], t[1], f[2]}, {f[0], f[1], f[2]}, {f[0], f[1], t[2]}, {f[0], t[1], t[2]}};
            case "east" -> new double[][]{{t[0], t[1], t[2]}, {t[0], f[1], t[2]}, {t[0], f[1], f[2]}, {t[0], t[1], f[2]}};
            default -> throw new IllegalArgumentException("Unknown face " + face);
        };
    }

    private static JsonObject rotation(JsonObject rotation, int x, int y) {
        JsonObject result = rotation.deepCopy();
        String axis = rotation.has("axis") ? rotation.get("axis").getAsString() : "y";
        double[] vector = rotate(switch (axis) { case "x" -> new double[]{1, 0, 0}; case "z" -> new double[]{0, 0, 1}; default -> new double[]{0, 1, 0}; }, x, y, false);
        int index = Math.abs(vector[0]) > 0.5 ? 0 : Math.abs(vector[1]) > 0.5 ? 1 : 2;
        result.addProperty("axis", String.valueOf("xyz".charAt(index)));
        double angle = rotation.has("angle") ? rotation.get("angle").getAsDouble() : 0;
        result.addProperty("angle", vector[index] < 0 ? -angle : angle);
        if (rotation.has("origin")) {
            double[] origin = rotate(vector(rotation.getAsJsonArray("origin")), x, y);
            result.add("origin", BedrockPackCompiler.numberArray(round(origin[0]), round(origin[1]), round(origin[2])));
        }
        return result;
    }

    private static double[] rotate(double[] point, int x, int y) {
        return rotate(point, x, y, true);
    }

    /** Rx(-x) then Ry(-y) (JOML's rotateYXZ), around the block centre for points, around the origin for directions. */
    private static double[] rotate(double[] point, int x, int y, boolean centred) {
        double c = centred ? 8 : 0;
        double px = point[0] - c, py = point[1] - c, pz = point[2] - c;
        double a = Math.toRadians(-x), cos = Math.cos(a), sin = Math.sin(a);
        double ny = py * cos - pz * sin, nz = py * sin + pz * cos;
        py = ny;
        pz = nz;
        double b = Math.toRadians(-y);
        cos = Math.cos(b);
        sin = Math.sin(b);
        double nx = px * cos + pz * sin;
        nz = -px * sin + pz * cos;
        return new double[]{round(nx + c), round(py + c), round(nz + c)};
    }

    private static double[] normal(String face) {
        return switch (face) {
            case "down" -> new double[]{0, -1, 0};
            case "up" -> new double[]{0, 1, 0};
            case "north" -> new double[]{0, 0, -1};
            case "south" -> new double[]{0, 0, 1};
            case "west" -> new double[]{-1, 0, 0};
            default -> new double[]{1, 0, 0};
        };
    }

    private static String direction(double[] normal) {
        for (String face : FACES) {
            double[] candidate = normal(face);
            if (same(candidate, normal)) return face;
        }
        throw new IllegalStateException("Rotated face is not axis aligned");
    }

    private static boolean same(double[] a, double[] b) {
        return Math.abs(a[0] - b[0]) < EPSILON && Math.abs(a[1] - b[1]) < EPSILON && Math.abs(a[2] - b[2]) < EPSILON;
    }

    private static double round(double value) {
        double rounded = Math.round(value * 10_000) / 10_000.0;
        return rounded + 0.0;
    }

    private static double[] vector(JsonArray array) {
        return new double[]{array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble()};
    }

    private static double[] vector4(JsonArray array) {
        return new double[]{array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble(), array.get(3).getAsDouble()};
    }

    // ---------------------------------------------------------------- shared

    private void add(DisplayEntityResources displays, String safe, ResolvedJavaModel model, boolean flat,
                     DisplayEntityResources.Section section, String key) throws IOException {
        TextureSet atlas = TextureSet.atlas(resources, List.copyOf(BedrockPackCompiler.modelTextures(model)), vanilla);
        displays.add(section, key, BedrockPackCompiler.displayGeometry(safe, model, atlas), atlas.png(),
                model.renderParts().stream().map(ResolvedJavaModel::display).toList(), flat);
    }

    /** Java's 3D model with its item tints baked into texture copies. */
    static ResolvedJavaModel tinted(ResolvedJavaModel model, Map<Integer, Integer> tints) {
        if (tints.isEmpty() || model.elements() == null || !model.parts().isEmpty()) return model;
        JsonArray elements = model.elements().deepCopy();
        Map<String, String> textures = new LinkedHashMap<>(model.textures());
        for (JsonElement value : elements) {
            JsonObject faces = value.getAsJsonObject().getAsJsonObject("faces");
            if (faces == null) continue;
            for (Map.Entry<String, JsonElement> entry : faces.entrySet()) {
                JsonObject face = entry.getValue().getAsJsonObject();
                if (!face.has("tintindex") || !face.has("texture")) continue;
                Integer tint = tints.get(face.get("tintindex").getAsInt());
                String reference = face.get("texture").getAsString();
                if (tint == null || tint == -1 || !reference.startsWith("#") || !textures.containsKey(reference.substring(1))) continue;
                String variable = "tint" + face.get("tintindex").getAsInt() + "_" + reference.substring(1);
                textures.put(variable, TextureSet.tinted(tint, textures.get(reference.substring(1))));
                face.addProperty("texture", "#" + variable);
            }
        }
        return new ResolvedJavaModel(model.identifier(), elements, Map.copyOf(textures), model.display(), model.handheld(),
                model.frontLight());
    }

    /** {@code assets/<ns>/<kind>/<path>.json} from the server's packs, else from the vanilla client. */
    private Optional<JsonObject> definition(String kind, String id) throws IOException {
        String namespace = JavaModelResolver.namespace(id), path = id.substring(id.indexOf(':') + 1);
        String file = "assets/" + namespace + "/" + kind + "/" + path + ".json";
        Optional<ResourceIndex.Asset> asset = resources.find(file);
        String text = null;
        if (asset.isPresent()) text = asset.get().readUtf8();
        else if (namespace.equals("minecraft") && vanilla != null) {
            Optional<byte[]> bytes = vanilla.readDefinition(file);
            if (bytes.isPresent()) text = new String(bytes.get(), StandardCharsets.UTF_8);
        }
        if (text == null) return Optional.empty();
        try {
            return Optional.of(JsonParser.parseString(text).getAsJsonObject());
        } catch (RuntimeException malformed) {
            throw new IOException("Malformed " + file, malformed);
        }
    }

    private static JsonObject object(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    private static String safe(String id) {
        return id.replace(':', '_').replace('/', '_').replace('.', '_').replace('-', '_');
    }
}
