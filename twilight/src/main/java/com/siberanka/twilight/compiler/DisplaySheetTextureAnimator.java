package com.siberanka.twilight.compiler;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.siberanka.twilight.source.ResourceIndex;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Transfers Java .mcmeta animations into the visible UV windows of display-index-v2 sheets.
 *
 * Unlike bitmap-search animation fixes, this reads the actual Java model's texture identifiers
 * and each texture's animation metadata. It therefore also works when the first frame is entirely
 * transparent, or when two different animations share a sheet.
 * Geometry, transforms, items, and worn armour are not changed.
 */
final class DisplaySheetTextureAnimator {
    private static final String INDEX = "twilight/display-index-v2.json";
    private static final String ENTITY = "entity/twilight_display.entity.json";
    private static final String CONTROLLERS = "render_controllers/twilight_display.json";
    private static final String REPORT = "twilight/display-animations-v3.json";
    private static final String NORMAL = "controller.render.twilight.display";
    private static final String LIT = "controller.render.twilight.display_lit";
    private static final int MAX_TICKS = 512;
    private static final int MAX_SPRITE_DIMENSION = 2048;
    private static final long MAX_TOTAL_SHEET_PIXELS = 1024L * 1024 * 1024;
    private static final String[] SECTIONS = {"variants", "models", "vanilla", "blocks"};

    private DisplaySheetTextureAnimator() { }

    static int apply(ResourceIndex resources, List<ItemCandidate> candidates, VanillaAssetCache vanilla,
                     Map<String, byte[]> files) {
        if (!files.containsKey(INDEX) || !files.containsKey(ENTITY) || !files.containsKey(CONTROLLERS)) return 0;
        try {
            JsonObject index = json(files, INDEX);
            if (index.get("format").getAsInt() != 2) return 0;
            int parts = index.get("parts").getAsInt();
            if (parts < 1 || parts > 1024) return 0;
            JsonObject entity = json(files, ENTITY);
            JsonObject description = entity.getAsJsonObject("minecraft:client_entity").getAsJsonObject("description");
            JsonObject render = json(files, CONTROLLERS);
            JsonObject controllers = render.getAsJsonObject("render_controllers");
            JsonObject normal = controllers.getAsJsonObject(NORMAL);
            JsonObject lit = controllers.getAsJsonObject(LIT);
            if (normal == null || lit == null) return 0;
            JsonObject textures = description.getAsJsonObject("textures");
            JsonArray scripts = description.getAsJsonObject("scripts").getAsJsonArray("pre_animation");
            JsonArray textureSlots = slots(normal);
            if (textures == null || scripts == null || textureSlots == null || !textureSlots.equals(slots(lit))) return 0;
            if (normal.getAsJsonArray("textures") == null || lit.getAsJsonArray("textures") == null) return 0;
            int sheets = 0;
            while (textures.has("s" + sheets)) sheets++;
            if (sheets == 0) return 0;

            Map<String, ItemCandidate> itemLookup = new HashMap<>(), modelLookup = new HashMap<>();
            for (ItemCandidate item : candidates) {
                itemLookup.putIfAbsent(BedrockPackCompiler.bedrockIdentifier(item), item);
                modelLookup.putIfAbsent(item.mappingModel(), item);
            }
            JavaModelResolver resolver = new JavaModelResolver(resources, vanilla);
            Map<String, AnimatedSprite> spriteCache = new HashMap<>();
            Set<String> spriteChecked = new HashSet<>();
            Map<Integer, BufferedImage> sheetImages = new HashMap<>();
            Map<Integer, JsonObject> sheetGeometry = new HashMap<>();
            List<Plan> planned = new ArrayList<>();
            JsonArray diagnostics = new JsonArray();
            int inspected = 0;
            for (String section : SECTIONS) {
                JsonObject entries = index.getAsJsonObject(section);
                if (entries == null) continue;
                for (Map.Entry<String, JsonElement> entry : entries.entrySet()) {
                    String id = entry.getKey();
                    int ordinal;
                    try { ordinal = entry.getValue().getAsJsonArray().get(0).getAsInt(); }
                    catch (RuntimeException ignored) { continue; }
                    if (ordinal < 0 || ordinal / parts >= sheets) continue;
                    try {
                        ItemCandidate item = "variants".equals(section) ? itemLookup.get(id) : modelLookup.get(id);
                        ResolvedJavaModel model = item != null
                                ? resolver.resolveAll(item.visualModels(), item.customModelData().isPresent() || !item.predicates().isEmpty())
                                : resolver.resolve(id, true);
                        if (!model.isThreeDimensional()) continue;
                        List<String> sprites = List.copyOf(BedrockPackCompiler.modelTextures(model));
                        if (sprites.isEmpty()) continue;
                        Map<String, AnimatedSprite> animated = new LinkedHashMap<>();
                        for (String sprite : sprites) {
                            if (spriteChecked.add(sprite)) spriteCache.put(sprite, readAnimation(resources, vanilla, sprite));
                            AnimatedSprite animation = spriteCache.get(sprite);
                            if (animation != null) animated.put(sprite, animation);
                        }
                        if (animated.isEmpty()) continue;
                        inspected++;
                        int sheetNumber = ordinal / parts;
                        BufferedImage image = sheetImages.computeIfAbsent(sheetNumber,
                                n -> png(files, "textures/twilight/display_sheet" + n + ".png"));
                        JsonObject geo = sheetGeometry.computeIfAbsent(sheetNumber,
                                n -> optionalJson(files, "models/entity/display.sheet" + n + ".geo.json"));
                        if (image == null || geo == null) {
                            diagnostic(diagnostics, id, "missing_sheet", 0); continue;
                        }
                        List<Rect> faces = visibleFaces(geo, ordinal % parts, image.getWidth(), image.getHeight());
                        if (faces.isEmpty()) { diagnostic(diagnostics, id, "missing_visible_uv", 0); continue; }
                        TextureSet atlas = TextureSet.atlas(resources, sprites, vanilla);
                        BufferedImage source = PngImages.read(atlas.png());
                        String safe = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
                        // Rebuild the model's original display geometry, including its exact face UVs.
                        // This works for both normal furniture and display-only ModelEngine/BetterModel bones:
                        // the latter have no standalone models/entity/geometry.* file in sheet format v2.
                        // Compare the generated face UVs against the final sheet face UVs, never infer the
                        // translation from PNG pixels (a valid first frame may be completely transparent).
                        JsonObject originalGeo = BedrockPackCompiler.displayGeometry("animation_probe", model, atlas);
                        int[] origin = geometryOffset(originalGeo, geo, ordinal % parts, source, image);
                        if (origin == null) {
                            // Compatibility with any source variant rewritten by another local rendering pass.
                            JsonObject writtenGeo = optionalJson(files, "models/entity/geometry." + safe + ".geo.json");
                            if (writtenGeo != null) origin = geometryOffset(writtenGeo, geo, ordinal % parts, source, image);
                        }
                        if (origin == null) {
                            // Only for block/vanilla variants whose source geometry was baked differently.
                            // Do not require bitmap matching for normal furniture or model bones.
                            origin = imageOffset(source, image, faces, atlas, animated);
                        }
                        if (origin == null) { diagnostic(diagnostics, id, "atlas_origin_unresolved", 0); continue; }
                        List<Clip> clips = new ArrayList<>();
                        for (Map.Entry<String, AnimatedSprite> animation : animated.entrySet()) {
                            TextureSet.Region region = atlas.region(animation.getKey());
                            Rect target = new Rect(origin[0] + region.x(), origin[1] + region.y(),
                                    origin[0] + region.x() + region.width(), origin[1] + region.y() + region.height());
                            List<Rect> used = new ArrayList<>();
                            for (Rect face : faces) {
                                Rect overlap = target.intersection(face);
                                if (overlap != null) used.add(overlap);
                            }
                            if (!used.isEmpty()) clips.add(new Clip(animation.getValue(), target, used));
                        }
                        if (clips.isEmpty()) { diagnostic(diagnostics, id, "animation_not_on_visible_uv", 0); continue; }
                        int period = 1;
                        for (Clip clip : clips) period = lcm(period, clip.animation.frames.size());
                        if (period > MAX_TICKS || period * (long) image.getWidth() * image.getHeight() > MAX_TOTAL_SHEET_PIXELS) {
                            diagnostic(diagnostics, id, "animation_size_limit", clips.size()); continue;
                        }
                        planned.add(new Plan(id, ordinal, image, clips, period));
                    } catch (IOException | RuntimeException malformed) {
                        diagnostic(diagnostics, id, "invalid_model_or_metadata", 0);
                    }
                }
            }
            // Always produce a report; zero repaired must never again masquerade as a successful fix.
            int repaired = 0;
            if (!planned.isEmpty()) {
                Map<String, byte[]> additions = new LinkedHashMap<>();
                JsonObject newTextures = new JsonObject();
                JsonArray extraSlots = new JsonArray();
                JsonArray selectors = new JsonArray();
                selectors.add("v.twilight_sheet_frame=math.floor(v.var/" + parts + ");");
                int originalSlots = textureSlots.size();
                int serial = 0;
                for (Plan plan : planned) {
                    try {
                        List<Integer> timeline = new ArrayList<>();
                        Map<String, Integer> duplicate = new HashMap<>();
                        for (int tick = 0; tick < plan.period; tick++) {
                            BufferedImage frame = draw(plan, tick);
                            byte[] bytes = TextureSet.png(frame);
                            String hash = hash(bytes);
                            Integer indexOfFrame = duplicate.get(hash);
                            if (indexOfFrame == null) {
                                String key = "anim_v3_" + plan.ordinal + "_" + serial++;
                                String path = "textures/twilight/display_animated/" + key;
                                additions.put(path + ".png", bytes);
                                newTextures.addProperty(key, path);
                                indexOfFrame = originalSlots + extraSlots.size();
                                extraSlots.add("Texture." + key);
                                duplicate.put(hash, indexOfFrame);
                            }
                            timeline.add(indexOfFrame);
                        }
                        String expression = Integer.toString(timeline.getLast());
                        for (int i = timeline.size() - 2; i >= 0; i--)
                            expression = "(v.twilight_sheet_tick==" + i + "?" + timeline.get(i) + ":" + expression + ")";
                        selectors.add("v.var==" + plan.ordinal + "?{v.twilight_sheet_tick=math.mod(math.floor(q.life_time*20),"
                                + plan.period + ");v.twilight_sheet_frame=" + expression + ";};");
                        diagnostic(diagnostics, plan.id, "repaired", duplicate.size());
                        repaired++;
                    } catch (IOException | RuntimeException failed) {
                        diagnostic(diagnostics, plan.id, "cannot_encode_frames", 0);
                    }
                }
                if (repaired > 0) {
                    files.putAll(additions);
                    for (Map.Entry<String, JsonElement> item : newTextures.entrySet()) textures.add(item.getKey(), item.getValue());
                    for (JsonElement e : extraSlots) { textureSlots.add(e); slots(lit).add(e.deepCopy()); }
                    for (JsonElement e : selectors) scripts.add(e);
                    String selected = "Array.textures[v.twilight_sheet_frame]";
                    normal.getAsJsonArray("textures").set(0, new JsonPrimitive(selected));
                    lit.getAsJsonArray("textures").set(0, new JsonPrimitive(selected));
                    files.put(ENTITY, BedrockPackCompiler.jsonBytes(entity));
                    files.put(CONTROLLERS, BedrockPackCompiler.jsonBytes(render));
                }
            }
            JsonObject report = new JsonObject();
            report.addProperty("algorithm", "mcmeta_to_visible_display_sheet_uv_v3");
            report.addProperty("animated_models_scanned", inspected);
            report.addProperty("repaired", repaired);
            report.add("details", diagnostics);
            files.put(REPORT, BedrockPackCompiler.jsonBytes(report));
            return repaired;
        } catch (RuntimeException fatal) {
            JsonObject report = new JsonObject();
            report.addProperty("algorithm", "mcmeta_to_visible_display_sheet_uv_v3");
            report.addProperty("repaired", 0);
            report.addProperty("error", fatal.getClass().getSimpleName() + ": " + fatal.getMessage());
            files.put(REPORT, BedrockPackCompiler.jsonBytes(report));
            return 0;
        }
    }

    private static void diagnostic(JsonArray report, String id, String status, int frames) {
        JsonObject entry = new JsonObject();
        entry.addProperty("item", id);
        entry.addProperty("status", status);
        entry.addProperty("frames", frames);
        report.add(entry);
    }

    static BufferedImage draw(Plan plan, int tick) {
        BufferedImage copy = new BufferedImage(plan.sheet.getWidth(), plan.sheet.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = copy.createGraphics();
        try { g.drawImage(plan.sheet, 0, 0, null); } finally { g.dispose(); }
        for (Clip clip : plan.clips) {
            BufferedImage frame = clip.animation.frames.get(tick % clip.animation.frames.size());
            for (Rect visible : clip.visible) {
                for (int y = visible.y0; y < visible.y1; y++)
                    for (int x = visible.x0; x < visible.x1; x++)
                        copy.setRGB(x, y, frame.getRGB(x - clip.target.x0, y - clip.target.y0));
            }
        }
        return copy;
    }

    /** Use the UV translation of the actual sheet bones; this works with fully transparent first frames. */
    static int[] geometryOffset(JsonObject original, JsonObject sheet, int part,
                                        BufferedImage atlas, BufferedImage base) {
        if (original == null) return null;
        List<JsonObject> src = meshes(original), dst = partMeshes(sheet, part);
        if (src.size() != dst.size() || src.isEmpty()) return null;
        Double dx = null, dy = null;
        int linked = 0;
        for (int mesh = 0; mesh < src.size(); mesh++) {
            JsonArray sa = src.get(mesh).getAsJsonArray("cubes"), da = dst.get(mesh).getAsJsonArray("cubes");
            if (sa == null || da == null || sa.size() != da.size()) return null;
            for (int cube = 0; cube < sa.size(); cube++) {
                JsonObject sf = sa.get(cube).getAsJsonObject().getAsJsonObject("uv");
                JsonObject df = da.get(cube).getAsJsonObject().getAsJsonObject("uv");
                if (sf == null || df == null) continue;
                for (Map.Entry<String, JsonElement> face : sf.entrySet()) {
                    if (!df.has(face.getKey())) continue;
                    JsonObject originalFace = face.getValue().getAsJsonObject();
                    JsonObject targetFace = df.getAsJsonObject(face.getKey());
                    if (!originalFace.has("uv") || !targetFace.has("uv")) continue;
                    JsonArray a = originalFace.getAsJsonArray("uv"), b = targetFace.getAsJsonArray("uv");
                    double x = b.get(0).getAsDouble() - a.get(0).getAsDouble();
                    double y = b.get(1).getAsDouble() - a.get(1).getAsDouble();
                    if (dx != null && (Math.abs(dx - x) > .01 || Math.abs(dy - y) > .01)) return null;
                    dx = x; dy = y; linked++;
                }
            }
        }
        if (linked == 0) return null;
        int x = (int) Math.round(dx), y = (int) Math.round(dy);
        // Face UVs give an exact transform independent of alpha, palettes or sprite recycling.
        // A bitmap equality test would reject valid geometry when another repair changed the
        // first-frame pixels, defeating the purpose of using the geometry as provenance.
        if (Math.abs(x - dx) > 0.01 || Math.abs(y - dy) > 0.01) return null;
        if (x < 0 || y < 0 || x + atlas.getWidth() > base.getWidth()
                || y + atlas.getHeight() > base.getHeight()) return null;
        return new int[]{x, y};
    }

    /** Fallback for display-only model bones with no standalone attachable geometry. */
    private static int[] imageOffset(BufferedImage atlas, BufferedImage sheet, List<Rect> visible,
                                     TextureSet textureSet, Map<String, AnimatedSprite> sprites) throws IOException {
        int w = atlas.getWidth(), h = atlas.getHeight();
        if (w > sheet.getWidth() || h > sheet.getHeight()) return null;
        int ax = -1, ay = -1, alpha = 0;
        // Prefer opaque pixels outside animated sprites for a stable, distinctive anchor.
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int pixel = atlas.getRGB(x, y);
            if ((pixel >>> 24) <= alpha) continue;
            boolean animated = false;
            for (String sprite : sprites.keySet()) {
                TextureSet.Region region = textureSet.region(sprite);
                if (x >= region.x() && x < region.x() + region.width()
                        && y >= region.y() && y < region.y() + region.height()) { animated = true; break; }
            }
            if (!animated) { ax = x; ay = y; alpha = pixel >>> 24; }
        }
        if (ax < 0) for (int y = 0; y < h && ax < 0; y++) for (int x = 0; x < w; x++)
            if ((atlas.getRGB(x, y) >>> 24) != 0) { ax = x; ay = y; break; }
        if (ax < 0) return null;
        int anchor = atlas.getRGB(ax, ay);
        int[] result = null;
        for (int y = 0; y <= sheet.getHeight() - h; y++) {
            for (int x = 0; x <= sheet.getWidth() - w; x++) {
                if (sheet.getRGB(x + ax, y + ay) != anchor || !matches(atlas, sheet, x, y)) continue;
                boolean relevant = false;
                for (String name : sprites.keySet()) {
                    TextureSet.Region region = textureSet.region(name);
                    Rect target = new Rect(x + region.x(), y + region.y(),
                            x + region.x() + region.width(), y + region.y() + region.height());
                    for (Rect face : visible) if (target.intersection(face) != null) relevant = true;
                }
                if (!relevant) continue;
                if (result != null && (result[0] != x || result[1] != y)) return null;
                result = new int[]{x, y};
            }
        }
        return result;
    }

    private static boolean matches(BufferedImage atlas, BufferedImage sheet, int x, int y) {
        if (x < 0 || y < 0 || x + atlas.getWidth() > sheet.getWidth() || y + atlas.getHeight() > sheet.getHeight()) return false;
        for (int row = 0; row < atlas.getHeight(); row++) for (int col = 0; col < atlas.getWidth(); col++)
            if (atlas.getRGB(col, row) != sheet.getRGB(x + col, y + row)) return false;
        return true;
    }

    private static List<JsonObject> meshes(JsonObject geometry) {
        List<JsonObject> result = new ArrayList<>();
        for (JsonElement e : geometry.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones")) {
            JsonObject bone = e.getAsJsonObject();
            if (bone.has("cubes") && bone.getAsJsonArray("cubes").size() > 0) result.add(bone);
        }
        return result;
    }

    private static List<JsonObject> partMeshes(JsonObject geometry, int part) {
        Map<Integer, JsonObject> sorted = new TreeMap<>();
        for (JsonObject bone : meshes(geometry)) {
            String name = bone.has("name") ? bone.get("name").getAsString() : "";
            String first = "twp" + part + "_", second = "twc" + part + "_";
            String number = name.startsWith(first) ? name.substring(first.length())
                    : name.startsWith(second) ? name.substring(second.length()) : null;
            if (number == null || !number.matches("\\d+")) continue;
            sorted.put(Integer.parseInt(number), bone);
        }
        return new ArrayList<>(sorted.values());
    }

    private static List<Rect> visibleFaces(JsonObject geo, int part, int width, int height) {
        Set<Rect> faces = new LinkedHashSet<>();
        for (JsonObject bone : partMeshes(geo, part)) {
            for (JsonElement cube : bone.getAsJsonArray("cubes")) {
                JsonObject uv = cube.getAsJsonObject().getAsJsonObject("uv");
                if (uv == null) continue;
                for (Map.Entry<String, JsonElement> face : uv.entrySet()) {
                    JsonObject info = face.getValue().getAsJsonObject();
                    JsonArray coordinates = info.getAsJsonArray("uv"), size = info.getAsJsonArray("uv_size");
                    if (coordinates == null || size == null) continue;
                    double x = coordinates.get(0).getAsDouble(), y = coordinates.get(1).getAsDouble();
                    double x1 = x + size.get(0).getAsDouble(), y1 = y + size.get(1).getAsDouble();
                    Rect rect = new Rect(Math.max(0, (int) Math.floor(Math.min(x, x1))),
                            Math.max(0, (int) Math.floor(Math.min(y, y1))),
                            Math.min(width, (int) Math.ceil(Math.max(x, x1))),
                            Math.min(height, (int) Math.ceil(Math.max(y, y1))));
                    if (rect.width() > 0 && rect.height() > 0) faces.add(rect);
                }
            }
        }
        return new ArrayList<>(faces);
    }

    private static AnimatedSprite readAnimation(ResourceIndex resources, VanillaAssetCache vanilla, String sprite) throws IOException {
        if (sprite.startsWith("twilight:tinted/")) return null;
        String path = JavaModelResolver.texturePath(TextureSet.spriteTexture(resources, sprite));
        var source = resources.find(path);
        var meta = resources.find(path + ".mcmeta");
        byte[] imageBytes = source.isPresent() ? source.get().readBytes() : null;
        String metadata = meta.isPresent() ? meta.get().readUtf8() : null;
        if (vanilla != null && path.startsWith("assets/minecraft/textures/")) {
            if (imageBytes == null) imageBytes = vanilla.readTexture(path).orElse(null);
            if (metadata == null) {
                var data = vanilla.readTexture(path + ".mcmeta");
                if (data.isPresent()) metadata = new String(data.get(), StandardCharsets.UTF_8);
            }
        }
        if (imageBytes == null || metadata == null) return null;
        JsonObject root;
        try { root = JsonParser.parseString(metadata).getAsJsonObject(); }
        catch (RuntimeException invalid) { return null; }
        JsonObject animation = root.getAsJsonObject("animation");
        if (animation == null) return null;
        BufferedImage strip = PngImages.read(imageBytes);
        if (strip == null) return null;
        return parseFrames(sprite, strip, animation);
    }

    /** Parsed without inspecting atlas pixels, including animations that start transparent. */
    static AnimatedSprite parseFrames(String sprite, BufferedImage strip, JsonObject animation) {
        int w = animation.has("width") ? animation.get("width").getAsInt() : -1;
        int h = animation.has("height") ? animation.get("height").getAsInt() : -1;
        if (w == -1 && h == -1) w = h = Math.min(strip.getWidth(), strip.getHeight());
        else { if (w == -1) w = strip.getWidth(); if (h == -1) h = strip.getHeight(); }
        if (w < 1 || h < 1 || w > MAX_SPRITE_DIMENSION || h > MAX_SPRITE_DIMENSION
                || strip.getWidth() % w != 0 || strip.getHeight() % h != 0) return null;
        int cols = strip.getWidth() / w, count = cols * (strip.getHeight() / h);
        int frametime = animation.has("frametime") ? animation.get("frametime").getAsInt() : 1;
        if (frametime < 1 || frametime > MAX_TICKS || count < 2) return null;
        JsonArray declared = animation.getAsJsonArray("frames");
        int length = declared == null ? count : declared.size();
        if (length == 0 || length > MAX_TICKS) return null;
        List<BufferedImage> ticks = new ArrayList<>();
        Set<Integer> different = new HashSet<>();
        for (int i = 0; i < length; i++) {
            JsonElement element = declared == null ? new JsonPrimitive(i) : declared.get(i);
            int id = element.isJsonObject() ? element.getAsJsonObject().get("index").getAsInt() : element.getAsInt();
            int time = element.isJsonObject() && element.getAsJsonObject().has("time")
                    ? element.getAsJsonObject().get("time").getAsInt() : frametime;
            if (id < 0 || id >= count || time < 1 || ticks.size() + time > MAX_TICKS) return null;
            BufferedImage frame = strip.getSubimage((id % cols) * w, (id / cols) * h, w, h);
            different.add(id);
            for (int tick = 0; tick < time; tick++) ticks.add(frame);
        }
        if (different.size() < 2 || ticks.size() < 2) return null;
        return new AnimatedSprite(sprite, ticks);
    }

    private static int lcm(int a, int b) {
        int u = a, v = b;
        while (v != 0) { int t = u % v; u = v; v = t; }
        long result = (long) a / u * b;
        return result > MAX_TICKS ? MAX_TICKS + 1 : (int) result;
    }

    private static JsonArray slots(JsonObject controller) {
        JsonObject arrays = controller.getAsJsonObject("arrays");
        if (arrays == null) return null;
        JsonObject textures = arrays.getAsJsonObject("textures");
        return textures == null ? null : textures.getAsJsonArray("Array.textures");
    }
    private static JsonObject json(Map<String, byte[]> files, String path) {
        return JsonParser.parseString(new String(files.get(path), StandardCharsets.UTF_8)).getAsJsonObject();
    }
    private static JsonObject optionalJson(Map<String, byte[]> files, String path) {
        return files.containsKey(path) ? json(files, path) : null;
    }
    private static BufferedImage png(Map<String, byte[]> files, String path) {
        byte[] bytes = files.get(path);
        if (bytes == null) return null;
        try { return PngImages.read(bytes); } catch (IOException invalid) { return null; }
    }
    private static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException absent) { throw new IllegalStateException(absent); }
    }
    record Rect(int x0, int y0, int x1, int y1) {
        int width() { return x1 - x0; }
        int height() { return y1 - y0; }
        Rect intersection(Rect b) {
            int left = Math.max(x0, b.x0), top = Math.max(y0, b.y0);
            int right = Math.min(x1, b.x1), bottom = Math.min(y1, b.y1);
            return right <= left || bottom <= top ? null : new Rect(left, top, right, bottom);
        }
    }
    record AnimatedSprite(String id, List<BufferedImage> frames) { }
    record Clip(AnimatedSprite animation, Rect target, List<Rect> visible) { }
    record Plan(String id, int ordinal, BufferedImage sheet, List<Clip> clips, int period) { }
}
