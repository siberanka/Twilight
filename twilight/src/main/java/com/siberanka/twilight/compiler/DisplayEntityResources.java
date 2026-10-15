/* Copyright (c) 2026 siberanka. Twilight License (PolyForm Noncommercial 1.0.0 + terms), see LICENSE. */
package com.siberanka.twilight.compiler;

import com.google.gson.*;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Resource half of the item-display protocol. Java remains the animation authority.
 *
 * <p>A Bedrock client builds every geometry a client entity names while it loads the pack, which took about 40 ms
 * per geometry on a desktop: one geometry per item made a pack with 3,413 display variants load for 156 s. The
 * variants are therefore merged into sheets of up to {@link #PARTS} parts, one geometry and one texture per sheet,
 * and the client shows only the part of the variant the server selected. Everything per frame is O(1): the pose
 * comes from the entity's properties and so does the item's display transform for the current context, which the
 * server reads from the index and sends.
 */
final class DisplayEntityResources {
    /** Parts per sheet; the variant ordinal is {@code sheet * PARTS + part}. */
    static final int PARTS = 64;
    /** Largest texture side a sheet grows to (an item larger than this gets a sheet of its own). */
    static final int MAX_SHEET_SIDE = 4096;
    static final String INDEX = "twilight/display-index-v2.json";
    /** Java ItemDisplayContext network ordinals, including NONE. */
    static final String[] CONTEXTS = {"none", "thirdperson_lefthand", "thirdperson_righthand", "firstperson_lefthand",
            "firstperson_righthand", "head", "gui", "ground", "fixed"};
    private static final List<String> CHAIN = List.of("drop", "yaw", "pitch", "translation", "lz", "ly", "lx", "scale",
            "rz", "ry", "rx", "item_flip");
    private static final List<String> CONTEXT_CHAIN = List.of("ctx_t", "ctx_x", "ctx_y", "ctx_z", "ctx_s");
    private static final Gson JSON = new GsonBuilder().disableHtmlEscaping().create();

    private final List<Variant> variants = new ArrayList<>();
    private final Map<Section, Set<String>> keys = new EnumMap<>(Section.class);

    /** Where the server looks a variant up (see the index sections). */
    enum Section {
        /** Custom items by the Bedrock item Geyser maps them to. */
        ITEM("variants"),
        /** Model bones by their Java item model. */
        MODEL("models"),
        /** Items with their own model, by Java item identifier. */
        VANILLA("vanilla"),
        /** Block states of block displays, by block key. */
        BLOCK("blocks"),
        /** Player heads, by skin hash. */
        HEAD("heads");

        final String json;

        Section(String json) {
            this.json = json;
        }
    }

    /**
     * One item or block: its key, its compact geometry, its texture, the display transforms of its meshes and whether
     * Java draws it flat (Bedrock then draws it as a dropped item itself).
     */
    private record Variant(String identifier, Section section, byte[] geometry, byte[] texture, List<JsonObject> displays,
                           boolean flat) {}

    void add(String identifier, JsonObject itemGeometry, byte[] texturePng, JsonObject display) {
        add(identifier, itemGeometry, texturePng, Collections.singletonList(display));
    }

    /** An item Geyser maps to a Bedrock item: the server finds its variant by the Bedrock identifier. */
    void add(String identifier, JsonObject itemGeometry, byte[] texturePng, List<JsonObject> displays) {
        add(Section.ITEM, identifier, itemGeometry, texturePng, displays, false);
    }

    /**
     * A model bone (ModelEngine, BetterModel): shown only in item displays, so it has no Bedrock item and the
     * server finds its variant by the stack's {@code item_model} component.
     */
    void addModel(String itemModel, JsonObject itemGeometry, byte[] texturePng, List<JsonObject> displays) {
        add(Section.MODEL, itemModel, itemGeometry, texturePng, displays, false);
    }

    boolean contains(Section section, String key) {
        return keys.getOrDefault(section, Set.of()).contains(key);
    }

    void add(Section section, String key, JsonObject itemGeometry, byte[] texturePng, List<JsonObject> displays, boolean flat) {
        if (!keys.computeIfAbsent(section, ignored -> new HashSet<>()).add(key)) return;
        JsonArray meshes = body(itemGeometry).getAsJsonArray("bones");
        if (meshes.size() != displays.size()) throw new IllegalArgumentException("Composite mesh/pose count mismatch");
        List<JsonObject> copies = new ArrayList<>();
        for (JsonObject display : displays) copies.add(display == null ? new JsonObject() : display.deepCopy());
        variants.add(new Variant(key, section, JSON.toJson(itemGeometry).getBytes(StandardCharsets.UTF_8), texturePng,
                List.copyOf(copies), flat));
    }

    int size() {
        return variants.size();
    }

    void finish(Map<String, byte[]> files) throws IOException {
        if (variants.isEmpty()) return;
        // Items with the same texture (the bones of one model) share a sheet and its texture region.
        Map<String, List<Variant>> byTexture = new LinkedHashMap<>();
        for (Variant variant : variants) byTexture.computeIfAbsent(digest(variant.texture), key -> new ArrayList<>()).add(variant);

        Map<Section, JsonObject> sections = new EnumMap<>(Section.class);
        for (Section section : Section.values()) sections.put(section, new JsonObject());
        JsonArray flat = new JsonArray();
        JsonArray transforms = new JsonArray();
        Map<String, Integer> transformIds = new HashMap<>();
        List<Sheet> sheets = new ArrayList<>();
        Sheet sheet = new Sheet(0);
        for (Map.Entry<String, List<Variant>> group : byTexture.entrySet()) {
            for (Variant variant : group.getValue()) {
                int[] size = dimensions(variant.texture);
                if (sheet.parts.size() >= PARTS || !sheet.place(group.getKey(), variant.texture, size[0], size[1])) {
                    sheets.add(sheet);
                    sheet = new Sheet(sheets.size());
                    if (!sheet.place(group.getKey(), variant.texture, size[0], size[1])) throw new IOException("Unplaceable texture");
                }
                int ordinal = sheet.number * PARTS + sheet.parts.size();
                if (ordinal >= MAX_VARIANTS) throw new IOException("More than " + MAX_VARIANTS + " display variants");
                sheet.parts.add(new Part(variant, group.getKey()));
                JsonArray entry = new JsonArray();
                entry.add(ordinal);
                entry.add(sharedTransform(variant, transforms, transformIds));
                sections.get(variant.section).add(variant.identifier, entry);
                if (variant.flat) flat.add(ordinal);
            }
        }
        if (!sheet.parts.isEmpty()) sheets.add(sheet);

        JsonObject geometries = new JsonObject(), textures = new JsonObject(), animations = new JsonObject();
        JsonArray animate = new JsonArray();
        for (Sheet current : sheets) {
            String key = "s" + current.number;
            geometries.addProperty(key, "geometry.twilight.display.sheet" + current.number);
            textures.addProperty(key, "textures/twilight/display_sheet" + current.number);
            files.put("textures/twilight/display_sheet" + current.number + ".png", current.texture());
            JsonObject staticBones = new JsonObject();
            files.put("models/entity/display.sheet" + current.number + ".geo.json",
                    jsonBytes(current.geometry(staticBones)));
            if (!staticBones.isEmpty()) {
                // Composite items whose meshes have different display transforms: rare, so their per-mesh
                // transforms stay static animations selected by variant.
                for (Map.Entry<String, JsonElement> entry : staticBones.entrySet()) {
                    int ordinal = Integer.parseInt(entry.getKey());
                    String id = "animation.twilight.display.variant" + ordinal;
                    JsonObject animation = new JsonObject();
                    animation.addProperty("loop", true);
                    animation.add("bones", entry.getValue());
                    files.put("animations/display.variant" + ordinal + ".json",
                            jsonBytes(property("animations", property(id, animation), "1.10.0")));
                    animations.addProperty("v" + ordinal, id);
                    JsonObject condition = new JsonObject();
                    condition.addProperty("v" + ordinal, "v.var==" + ordinal);
                    animate.add(condition);
                }
            }
        }
        JsonObject index = new JsonObject();
        index.addProperty("format", 2);
        index.addProperty("parts", PARTS);
        for (Section section : Section.values()) {
            if (section.ordinal() < 2 || !sections.get(section).isEmpty()) index.add(section.json, sections.get(section));
        }
        if (!flat.isEmpty()) index.add("flat", flat);
        index.add("transforms", transforms);
        files.put(INDEX, jsonBytes(index));
        writeEntity(files, sheets.size(), geometries, textures, animations, animate);
    }

    static final int MAX_VARIANTS = com.siberanka.twilight.integration.display.DisplayIndex.MAX_VARIANTS;

    /**
     * The index of the item's display transforms in the shared table, sent by the server as the {@code twilight:c}
     * properties for the current context; -1 when every mesh is untransformed or the meshes differ (static).
     */
    private static int sharedTransform(Variant variant, JsonArray transforms, Map<String, Integer> ids) {
        JsonObject first = variant.displays.getFirst();
        for (JsonObject display : variant.displays) if (!display.equals(first)) return -1;
        double[] values = contextValues(first);
        if (identity(values)) return -1;
        JsonArray array = new JsonArray();
        for (double value : values) array.add(value);
        String key = array.toString();
        Integer existing = ids.get(key);
        if (existing != null) return existing;
        ids.put(key, transforms.size());
        transforms.add(array);
        return transforms.size() - 1;
    }

    /**
     * Bedrock bone values of a Java display block for each context: translation (3, pixels), rotation (3, degrees,
     * applied X then Y then Z as nested bones) and scale (3). Left contexts without their own entry use the right
     * one, mirrored like Java's ItemTransform.apply; Java's limits on translation and scale apply.
     */
    static double[] contextValues(JsonObject display) {
        double[] result = new double[CONTEXTS.length * 9];
        for (int c = 0; c < CONTEXTS.length; c++) {
            JsonObject transform = display == null ? null : object(display, CONTEXTS[c]);
            boolean left = c == 1 || c == 3;
            if (transform == null && display != null && left) transform = object(display, CONTEXTS[c + 1]);
            double[] translation = vector(transform, "translation", 0), rotation = vector(transform, "rotation", 0),
                    scale = vector(transform, "scale", 1);
            for (int axis = 0; axis < 3; axis++) {
                double t = Math.clamp(translation[axis], -80, 80), r = rotation[axis], s = Math.clamp(scale[axis], -4, 4);
                // ItemTransform.apply mirrors explicit left poses as well as fallback poses.
                if (left && axis == 0) t = -t;
                if (left && axis != 0) r = -r;
                // Java's +Z and the rotation sense of Y and Z are mirrored in Bedrock's bone space.
                if (axis == 2) t = -t;
                if (axis != 0) r = -r;
                // Adding zero turns -0 into 0, so equal transforms share one table entry.
                result[c * 9 + axis] = t + 0.0;
                result[c * 9 + 3 + axis] = r + 0.0;
                result[c * 9 + 6 + axis] = s + 0.0;
            }
        }
        return result;
    }

    private static boolean identity(double[] values) {
        for (int i = 0; i < values.length; i++) if (values[i] != (i % 9 >= 6 ? 1 : 0)) return false;
        return true;
    }

    private static JsonObject object(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    private static double[] vector(JsonObject transform, String key, double fallback) {
        double[] result = {fallback, fallback, fallback};
        if (transform == null || !transform.has(key) || !transform.get(key).isJsonArray()) return result;
        JsonArray array = transform.getAsJsonArray(key);
        for (int i = 0; i < 3 && i < array.size(); i++) {
            double value = array.get(i).getAsDouble();
            if (Double.isFinite(value)) result[i] = value;
        }
        return result;
    }

    private record Part(Variant variant, String texture) {}

    /** One geometry and texture holding up to {@link #PARTS} variants. */
    private static final class Sheet {
        final int number;
        final List<Part> parts = new ArrayList<>();
        final Map<String, int[]> placed = new LinkedHashMap<>();
        final Map<String, byte[]> images = new LinkedHashMap<>();
        final List<int[]> shelves = new ArrayList<>(); // y, height, used width
        int width, height;

        Sheet(int number) {
            this.number = number;
        }

        /** Shelf packing; a texture already in the sheet is reused. */
        boolean place(String key, byte[] png, int w, int h) {
            if (placed.containsKey(key)) return true;
            int[] position = null;
            for (int[] shelf : shelves) {
                if (h <= shelf[1] && shelf[2] + w <= MAX_SHEET_SIDE) {
                    position = new int[]{shelf[2], shelf[0]};
                    shelf[2] += w;
                    break;
                }
            }
            if (position == null) {
                boolean empty = placed.isEmpty();
                if (!empty && (height + h > MAX_SHEET_SIDE || w > MAX_SHEET_SIDE)) return false;
                position = new int[]{0, height};
                shelves.add(new int[]{height, h, w});
                height += h;
            }
            width = Math.max(width, position[0] + w);
            placed.put(key, position);
            images.put(key, png);
            return true;
        }

        byte[] texture() throws IOException {
            BufferedImage image = new BufferedImage(Math.max(1, width), Math.max(1, height), BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            try {
                graphics.setComposite(AlphaComposite.Src);
                for (Map.Entry<String, int[]> entry : placed.entrySet()) {
                    graphics.drawImage(PngImages.read(images.get(entry.getKey())), entry.getValue()[0], entry.getValue()[1], null);
                }
            } finally {
                graphics.dispose();
            }
            return TextureSet.png(image);
        }

        /** The sheet geometry; per-mesh static transforms of mixed composites go to {@code staticBones}. */
        JsonObject geometry(JsonObject staticBones) {
            JsonArray bones = new JsonArray();
            String parent = null;
            for (String name : CHAIN) {
                bones.add(bone(name, parent));
                parent = name;
            }
            for (String name : CONTEXT_CHAIN) {
                bones.add(bone(name, parent));
                parent = name;
            }
            for (int index = 0; index < parts.size(); index++) {
                Part part = parts.get(index);
                int[] offset = placed.get(part.texture);
                JsonObject item = JsonParser.parseString(new String(part.variant.geometry, StandardCharsets.UTF_8)).getAsJsonObject();
                JsonArray meshes = body(item).getAsJsonArray("bones");
                boolean mixed = meshes.size() > 1 && sharedTransformDiffers(part.variant.displays);
                JsonObject variantBones = new JsonObject();
                for (int mesh = 0; mesh < meshes.size(); mesh++) {
                    String prefix = "twp" + index + "_" + mesh;
                    String meshParent = "ctx_s";
                    if (mixed) {
                        for (String suffix : List.of("t", "x", "y", "z", "s")) {
                            bones.add(bone(prefix + suffix, meshParent));
                            meshParent = prefix + suffix;
                        }
                        addStaticTransforms(variantBones, prefix, part.variant.displays.get(mesh));
                    }
                    JsonObject source = meshes.get(mesh).getAsJsonObject();
                    JsonObject out = new JsonObject();
                    out.addProperty("name", prefix);
                    out.addProperty("parent", meshParent);
                    out.add("pivot", array(0, 0, 0));
                    JsonArray cubes = new JsonArray();
                    if (source.has("cubes")) for (JsonElement value : source.getAsJsonArray("cubes")) {
                        JsonObject cube = value.getAsJsonObject().deepCopy();
                        // Item rendering uses a centered mesh; attachables use a hand pivot at y=8.
                        for (String vector : List.of("origin", "pivot")) if (cube.has(vector)) {
                            JsonArray coordinates = cube.getAsJsonArray(vector);
                            coordinates.set(1, new JsonPrimitive(coordinates.get(1).getAsDouble() - 8));
                        }
                        if (cube.has("uv") && cube.get("uv").isJsonObject()) {
                            for (Map.Entry<String, JsonElement> face : cube.getAsJsonObject("uv").entrySet()) {
                                JsonObject mapped = face.getValue().getAsJsonObject();
                                JsonArray uv = mapped.getAsJsonArray("uv");
                                uv.set(0, new JsonPrimitive(uv.get(0).getAsDouble() + offset[0]));
                                uv.set(1, new JsonPrimitive(uv.get(1).getAsDouble() + offset[1]));
                            }
                        }
                        cubes.add(mirrored(cube));
                    }
                    out.add("cubes", cubes);
                    bones.add(out);
                }
                if (!variantBones.isEmpty()) staticBones.add(Integer.toString(number * PARTS + index), variantBones);
            }
            JsonObject description = new JsonObject();
            description.addProperty("identifier", "geometry.twilight.display.sheet" + number);
            description.addProperty("texture_width", Math.max(1, width));
            description.addProperty("texture_height", Math.max(1, height));
            // Animated translations can leave the static mesh bounds.
            description.addProperty("visible_bounds_width", 128);
            description.addProperty("visible_bounds_height", 128);
            JsonObject geometry = new JsonObject();
            geometry.add("description", description);
            geometry.add("bones", bones);
            JsonArray list = new JsonArray();
            list.add(geometry);
            JsonObject root = new JsonObject();
            root.addProperty("format_version", "1.21.0");
            root.add("minecraft:geometry", list);
            return root;
        }
    }

    /**
     * The item geometry's cube with its east and west faces exchanged. Inside the display entity's bone chain the
     * boxes sit where Java draws them, but a box's east texture appeared on its west side and the reverse (a
     * crafting table's front showed on the wrong side, a furnace facing east looked west; measured 15 October 2026
     * against Java), so the display mesh gives each side the other's texture.
     */
    static JsonObject mirrored(JsonObject cube) {
        JsonObject result = cube.deepCopy();
        if (result.has("uv") && result.get("uv").isJsonObject()) {
            JsonObject faces = result.getAsJsonObject("uv"), swapped = new JsonObject();
            for (Map.Entry<String, JsonElement> face : faces.entrySet()) {
                String name = switch (face.getKey()) { case "east" -> "west"; case "west" -> "east"; default -> face.getKey(); };
                swapped.add(name, face.getValue());
            }
            result.add("uv", swapped);
        }
        return result;
    }

    private static boolean sharedTransformDiffers(List<JsonObject> displays) {
        JsonObject first = displays.getFirst();
        for (JsonObject display : displays) if (!display.equals(first)) return true;
        return false;
    }

    /** Static per-context transforms of one mesh: Molang selects the context from the appearance property. */
    private static void addStaticTransforms(JsonObject bones, String prefix, JsonObject display) {
        double[] values = contextValues(display);
        for (int component = 0; component < 3; component++) {
            JsonArray expressions = new JsonArray();
            for (int axis = 0; axis < 3; axis++) {
                String expression = "0";
                for (int c = CONTEXTS.length - 1; c >= 0; c--) {
                    expression = "math.mod(q.property('twilight:appearance'),9)==" + c + "?" + values[c * 9 + component * 3 + axis]
                            + ":(" + expression + ")";
                }
                expressions.add(expression);
            }
            if (component == 1) {
                for (int axis = 0; axis < 3; axis++) {
                    JsonArray rotation = array(0, 0, 0);
                    rotation.set(axis, expressions.get(axis));
                    bones.add(prefix + "xyz".charAt(axis), property("rotation", rotation));
                }
            } else bones.add(prefix + (component == 2 ? "s" : "t"), property(component == 0 ? "position" : "scale", expressions));
        }
    }

    private static JsonObject bone(String name, String parent) {
        JsonObject bone = new JsonObject();
        bone.addProperty("name", name);
        if (parent != null) bone.addProperty("parent", parent);
        bone.add("pivot", array(0, 0, 0));
        return bone;
    }

    private static JsonObject body(JsonObject geometry) {
        return geometry.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
    }

    private static void writeEntity(Map<String, byte[]> files, int sheetCount, JsonObject geometries, JsonObject textures,
                                    JsonObject animations, JsonArray animate) {
        JsonObject description = new JsonObject();
        description.addProperty("identifier", "twilight:item_display");
        description.add("materials", stringProperty("default", "entity_alphatest"));
        description.add("textures", textures);
        description.add("geometry", geometries);
        animations.addProperty("pose", "animation.twilight.display_pose");
        description.add("animations", animations);
        animate.add("pose");
        JsonObject scripts = new JsonObject();
        scripts.add("animate", animate);
        // A dropped item's bob and spin start at a random phase, as Java's ItemEntity does on the client.
        StringBuilder initialize = new StringBuilder("v.rev=-1;v.init=0;v.start=0;v.sec=0;v.del=0;v.p=1;v.var=-1;v.part=-1;"
                + "v.phase=math.random(0,6.2831853);v.bb=0;v.drop=0;v.light=-1;v.br=1;");
        double[] identity = DisplayPoseValues.IDENTITY;
        for (int i = 0; i < identity.length; i++) {
            initialize.append("v.a").append(i).append('=').append(number(identity[i])).append(';');
            initialize.append("v.b").append(i).append('=').append(number(identity[i])).append(';');
        }
        initialize.append("v.t0=0;v.t1=0;v.t2=0;v.t3=1;v.t4=1;v.t5=1;v.lex=0;v.ley=0;v.lez=0;v.rex=0;v.rey=0;v.rez=0;");
        scripts.add("initialize", strings(initialize.toString()));
        scripts.add("pre_animation", strings(preAnimation().toArray(String[]::new)));
        description.add("scripts", scripts);
        // A brightness override replaces the world's light at the display (Java's lightmap at that light level).
        JsonArray controllers = new JsonArray();
        controllers.add(stringProperty("controller.render.twilight.display", "math.floor(q.property('twilight:style')/8)==0"));
        controllers.add(stringProperty("controller.render.twilight.display_lit", "math.floor(q.property('twilight:style')/8)>0"));
        description.add("render_controllers", controllers);
        files.put("entity/twilight_display.entity.json",
                jsonBytes(property("minecraft:client_entity", property("description", description), "1.10.0")));

        JsonObject arrays = new JsonObject();
        JsonArray geo = new JsonArray(), tex = new JsonArray();
        for (int i = 0; i < sheetCount; i++) {
            geo.add("Geometry.s" + i);
            tex.add("Texture.s" + i);
        }
        arrays.add("geometries", property("Array.models", geo));
        arrays.add("textures", property("Array.textures", tex));
        String sheet = "math.floor(math.floor(q.property('twilight:appearance')/9)/" + PARTS + ")";
        JsonObject controller = new JsonObject();
        controller.add("arrays", arrays);
        controller.addProperty("geometry", "Array.models[" + sheet + "]");
        controller.add("textures", strings("Array.textures[" + sheet + "]"));
        JsonArray materials = new JsonArray();
        materials.add(stringProperty("*", "Material.default"));
        controller.add("materials", materials);
        JsonArray visibility = new JsonArray();
        visibility.add(stringProperty("*", "q.property('twilight:appearance')>=0"));
        JsonObject hidden = new JsonObject();
        hidden.addProperty("twp*", false);
        visibility.add(hidden);
        for (int part = 0; part < PARTS; part++) visibility.add(stringProperty("twp" + part + "_*", "v.part==" + part));
        controller.add("part_visibility", visibility);
        JsonObject lit = controller.deepCopy();
        lit.addProperty("ignore_lighting", true);
        JsonObject overlay = new JsonObject();
        overlay.addProperty("r", 0);
        overlay.addProperty("g", 0);
        overlay.addProperty("b", 0);
        overlay.addProperty("a", "1-v.br");
        lit.add("overlay_color", overlay);
        JsonObject controllerFile = new JsonObject();
        controllerFile.add("controller.render.twilight.display", controller);
        controllerFile.add("controller.render.twilight.display_lit", lit);
        files.put("render_controllers/twilight_display.json", jsonBytes(property("render_controllers", controllerFile, "1.10.0")));

        JsonObject bones = new JsonObject();
        // A dropped item bobs and spins (ItemEntityRenderer): up by the bob plus a quarter of its ground scale.
        JsonObject drop = new JsonObject();
        drop.add("position", strings("0", "v.drop?(math.sin(57.29578*((q.life_time*20)/10+v.phase))*0.1+0.1+0.25*"
                + contextProperty(7) + ")*16:0", "0"));
        drop.add("rotation", strings("0", "v.drop?-57.29578*((q.life_time*20)/20+v.phase):0", "0"));
        bones.add("drop", drop);
        // Bedrock already rotates the actor by its body yaw. Applying it again
        // here rotates the entire display twice (hidden by zero-yaw fixtures).
        // Billboards (DisplayRenderer.calculateOrientation) face the camera's yaw (vertical, center) and pitch
        // (horizontal, center) instead of the entity's.
        bones.add("yaw", property("rotation", strings("0",
                "(v.bb==1||v.bb==3)?q.camera_rotation(1)-q.body_y_rotation-180:0", "0")));
        bones.add("pitch", property("rotation", strings(
                "v.drop?0:((v.bb==2||v.bb==3)?-q.camera_rotation(0):q.target_x_rotation)", "0", "0")));
        bones.add("translation", property("position", strings("v.t0*16", "v.t1*16", "-v.t2*16")));
        // Java's ItemDisplayRenderer adds Y=180 AFTER the display TRSR and BEFORE ItemTransform.
        bones.add("item_flip", property("rotation", array(0, 180, 0)));
        bones.add("scale", property("scale", strings("v.t3", "v.t4", "v.t5")));
        for (String q : List.of("l", "r")) for (int axis = 0; axis < 3; axis++) {
            JsonArray rotation = array(0, 0, 0);
            rotation.set(axis, new JsonPrimitive((axis == 0 ? "" : "-") + "v." + q + "e" + "xyz".charAt(axis)));
            bones.add(q + "xyz".charAt(axis), property("rotation", rotation));
        }
        // The item's display transform for the current context, sent by the server (see the index).
        bones.add("ctx_t", property("position", strings(contextProperty(0), contextProperty(1), contextProperty(2))));
        for (int axis = 0; axis < 3; axis++) {
            JsonArray rotation = array(0, 0, 0);
            rotation.set(axis, new JsonPrimitive(contextProperty(3 + axis)));
            bones.add("ctx_" + "xyz".charAt(axis), property("rotation", rotation));
        }
        bones.add("ctx_s", property("scale", strings(contextProperty(6), contextProperty(7), contextProperty(8))));
        JsonObject pose = new JsonObject();
        pose.addProperty("loop", true);
        pose.add("bones", bones);
        files.put("animations/twilight_display.json", jsonBytes(property("animations",
                property("animation.twilight.display_pose", pose), "1.10.0")));
    }

    private static String contextProperty(int index) {
        return "q.property('twilight:c" + index + "')";
    }

    /**
     * The client half of Java's display interpolation (Display#createInterpolatedRenderState): a new target pose
     * starts from the pose shown at that moment, unless the duration is zero or nothing was shown yet. Odd
     * revisions carry a new pose; even ones only move the start.
     */
    static List<String> preAnimation() {
        List<String> script = new ArrayList<>();
        // Style: billboard (2 bits), dropped item (1 bit), brightness override + 1 (block * 16 + sky).
        script.add("v.st=q.property('twilight:style');v.bb=math.mod(v.st,4);v.drop=math.mod(math.floor(v.st/4),2);"
                + "v.light=math.floor(v.st/8)-1;"
                + "v.light>=0?{v.lv=math.max(math.floor(v.light/16),math.mod(v.light,16))/15;v.lv=v.lv/(4-3*v.lv);"
                + "v.br=0.03+0.96*((v.lv+1-math.pow(1-v.lv,4))/2);};");
        script.add("v.changed=v.rev!=q.property('twilight:revision');"
                + "v.posed=v.changed&&math.mod(q.property('twilight:revision'),2)==1;"
                + "v.cap=v.posed&&v.init>0&&q.property('twilight:seconds')>0;");
        StringBuilder capture = new StringBuilder("v.cap?{v.p=" + progress() + ";");
        for (int i = 0; i < 6; i++) capture.append("v.a").append(i).append("=math.lerp(v.a").append(i).append(",v.b").append(i).append(",v.p);");
        capture.append(slerpScript("l", 6)).append(slerpScript("r", 10));
        for (int i = 0; i < 4; i++) {
            capture.append("v.a").append(6 + i).append("=v.l").append("xyzw".charAt(i)).append(';');
            capture.append("v.a").append(10 + i).append("=v.r").append("xyzw".charAt(i)).append(';');
        }
        script.add(capture.append("};").toString());
        StringBuilder target = new StringBuilder("v.posed?{");
        for (int i = 0; i < DisplayPoseValues.SIZE; i++) target.append("v.b").append(i).append("=q.property('twilight:b").append(i).append("');");
        script.add(target.append("};").toString());
        StringBuilder jump = new StringBuilder("v.posed&&!v.cap?{");
        for (int i = 0; i < DisplayPoseValues.SIZE; i++) jump.append("v.a").append(i).append("=v.b").append(i).append(';');
        script.add(jump.append("};").toString());
        script.add("v.changed?{v.sec=q.property('twilight:seconds');v.del=q.property('twilight:delay');v.start=q.life_time;"
                + "v.init=1;v.rev=q.property('twilight:revision');};");
        script.add("v.p=" + progress() + ";");
        StringBuilder lerp = new StringBuilder();
        for (int i = 0; i < 6; i++) lerp.append("v.t").append(i).append("=math.lerp(v.a").append(i).append(",v.b").append(i).append(",v.p);");
        script.add(lerp.toString());
        script.add(quaternionScript("l", 6));
        script.add(quaternionScript("r", 10));
        script.add("v.var=math.floor(q.property('twilight:appearance')/9);v.part=v.var<0?-1:v.var-math.floor(v.var/" + PARTS
                + ")*" + PARTS + ";");
        return script;
    }

    private static String progress() {
        return "v.sec<=0?1:math.clamp((q.life_time-v.start-v.del)/v.sec,0,1)";
    }

    /** SLERP of the {@code from}/{@code to} quaternion variables at {@code v.p} into v.qx..v.qw, normalized. */
    static String slerpScript(String q, int offset) {
        StringBuilder script = new StringBuilder();
        for (int i = 0; i < 4; i++) for (String end : List.of("a", "b")) script.append("v.").append(q).append(end).append(i)
                .append("=v.").append(end).append(offset + i).append(';');
        script.append("v.dot=");
        for (int i = 0; i < 4; i++) { if (i > 0) script.append('+'); script.append("v.").append(q).append('a').append(i).append("*v.").append(q).append('b').append(i); }
        script.append(";v.sign=v.dot<0?-1:1;v.dot=math.clamp(math.abs(v.dot),0,1);v.angle=math.acos(v.dot);v.den=math.max(math.sin(v.angle),0.000001);")
                .append("v.wa=v.dot>0.9995?1-v.p:math.sin((1-v.p)*v.angle)/v.den;v.wb=(v.dot>0.9995?v.p:math.sin(v.p*v.angle)/v.den)*v.sign;");
        for (int i = 0; i < 4; i++) script.append("v.").append(q).append("xyzw".charAt(i)).append("=v.wa*v.").append(q).append('a').append(i).append("+v.wb*v.").append(q).append('b').append(i).append(';');
        String x = "v." + q + "x", y = "v." + q + "y", z = "v." + q + "z", w = "v." + q + "w";
        script.append("v.n=math.max(math.sqrt(").append(x).append('*').append(x).append('+').append(y).append('*').append(y).append('+').append(z).append('*').append(z).append('+').append(w).append('*').append(w).append("),0.000001);");
        for (String v : List.of(x, y, z, w)) script.append(v).append('=').append(v).append("/v.n;");
        return script.toString();
    }

    /** SLERP at {@code v.p}, then the Euler angles (X, Y, Z, as nested bones) into v.qex..v.qez. */
    static String quaternionScript(String q, int offset) {
        StringBuilder script = new StringBuilder(slerpScript(q, offset));
        String x = "v." + q + "x", y = "v." + q + "y", z = "v." + q + "z", w = "v." + q + "w";
        // At +/-90 degrees, X and Z are not individually determined. Keep X=0
        // and recover the combined Z angle from the remaining matrix entries.
        script.append("v.sy=math.clamp(2*(").append(w).append('*').append(y).append('-').append(z).append('*').append(x).append("),-1,1);")
                .append("v.singular=math.abs(v.sy)>0.9999999;")
                .append("v.").append(q).append("ex=v.singular?0:math.atan2(2*(").append(w).append('*').append(x).append('+').append(y).append('*').append(z).append("),1-2*(").append(x).append('*').append(x).append('+').append(y).append('*').append(y).append("));")
                .append("v.").append(q).append("ey=math.asin(v.sy);")
                .append("v.").append(q).append("ez=v.singular?math.atan2(2*(").append(w).append('*').append(z).append('-').append(x).append('*').append(y).append("),1-2*(").append(x).append('*').append(x).append('+').append(z).append('*').append(z).append(")):")
                .append("math.atan2(2*(").append(w).append('*').append(z).append('+').append(x).append('*').append(y).append("),1-2*(").append(y).append('*').append(y).append('+').append(z).append('*').append(z).append("));");
        return script.toString();
    }

    /** The pose layout shared with the runtime: translation, scale, left and right quaternion. */
    static final class DisplayPoseValues {
        static final int SIZE = 14;
        static final double[] IDENTITY = {0, 0, 0, 1, 1, 1, 0, 0, 0, 1, 0, 0, 0, 1};

        private DisplayPoseValues() {}
    }

    private static int[] dimensions(byte[] png) throws IOException {
        if (png.length < 24 || !PngImages.isPng(png)) throw new IOException("Display texture is not a PNG");
        int width = ((png[16] & 0xff) << 24) | ((png[17] & 0xff) << 16) | ((png[18] & 0xff) << 8) | (png[19] & 0xff);
        int height = ((png[20] & 0xff) << 24) | ((png[21] & 0xff) << 16) | ((png[22] & 0xff) << 8) | (png[23] & 0xff);
        if (width <= 0 || height <= 0) throw new IOException("Display texture has no pixels");
        return new int[]{width, height};
    }

    private static String digest(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String number(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }

    private static byte[] jsonBytes(JsonObject value) { return JSON.toJson(value).getBytes(StandardCharsets.UTF_8); }
    private static JsonArray array(double... numbers) { JsonArray a = new JsonArray(); for (double n : numbers) a.add(n); return a; }
    private static JsonArray strings(String... values) { JsonArray a = new JsonArray(); for (String v : values) a.add(v); return a; }
    private static JsonObject stringProperty(String key, String value) { JsonObject o = new JsonObject(); o.addProperty(key, value); return o; }
    private static JsonObject property(String key, JsonElement value) { JsonObject o = new JsonObject(); o.add(key, value); return o; }
    private static JsonObject property(String key, JsonElement value, String version) { JsonObject o = property(key, value); o.addProperty("format_version", version); return o; }
}
