package com.siberanka.twilight.compiler;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Restores Java texture animations on the visible UVs of Geyser's display-index-v2 sheets.
 *
 * <p>Each display entity uses one variant, but several variants share one texture sheet. Replacing
 * that sheet globally would incorrectly synchronize otherwise independent furniture animations.
 * Instead, each animated variant gets its own sequence of copies of the sheet with only its face
 * windows replaced by the matching Java frame pixels. The entity's render controller selects its
 * own sequence, while the underlying geometry and every unaffected variant remain unchanged.
 *
 * <p>The old display-index.json repair is intentionally independent and remains available for
 * older packs. There are no item names, texture names or provider-specific exceptions here.
 */
final class AnimatedDisplaySheetUvRepair {
    private static final String INDEX = "twilight/display-index-v2.json";
    private static final String ENTITY = "entity/twilight_display.entity.json";
    private static final String RENDER_CONTROLLERS = "render_controllers/twilight_display.json";
    private static final String REPORT = "twilight/animated-display-sheet-uv-report.json";
    private static final String NORMAL = "controller.render.twilight.display";
    private static final String LIT = "controller.render.twilight.display_lit";
    private static final int PARTS = 64;
    private static final int MAX_FRAMES = 512;
    private static final int MAX_SHEET_SIDE = 4096;
    // Bound temporary and output images. An excessively large animated sheet is skipped, not corrupted.
    private static final long MAX_FRAME_PIXELS = 128L * 1024 * 1024;
    private static final Pattern FRAME = Pattern.compile("Texture\\.java_anim_(\\d+)");
    private static final String SOURCE_SUFFIX = "_java_animation.json";

    private AnimatedDisplaySheetUvRepair() {}

    static int apply(Map<String, byte[]> files) {
        if (!files.containsKey(INDEX) || !files.containsKey(ENTITY)
                || !files.containsKey(RENDER_CONTROLLERS) || files.containsKey(REPORT)) return 0;
        try {
            JsonObject index = json(files, INDEX);
            if (index.get("format").getAsInt() != 2 || index.get("parts").getAsInt() != PARTS) return 0;
            JsonObject variants = index.getAsJsonObject("variants");
            if (variants == null) return 0;
            JsonObject entity = json(files, ENTITY);
            JsonObject description = entity.getAsJsonObject("minecraft:client_entity").getAsJsonObject("description");
            if (!"twilight:item_display".equals(description.get("identifier").getAsString())) return 0;
            JsonObject rcFile = json(files, RENDER_CONTROLLERS);
            JsonObject controllers = rcFile.getAsJsonObject("render_controllers");
            JsonObject normal = controllers.getAsJsonObject(NORMAL);
            JsonObject lit = controllers.getAsJsonObject(LIT);
            if (normal == null || lit == null) return 0;
            JsonArray normalTextures = textureArray(normal);
            JsonArray litTextures = textureArray(lit);
            if (!normalTextures.equals(litTextures) || normalTextures.size() == 0) return 0;
            int sheetCount = normalTextures.size();
            if (!selector(normal).equals(selector(lit))) return 0;
            if (!selector(normal).contains("Array.textures[")) return 0;

            JsonObject textures = description.getAsJsonObject("textures");
            JsonArray script = description.getAsJsonObject("scripts").getAsJsonArray("pre_animation");
            if (textures == null || script == null) return 0;
            List<Candidate> candidates = new ArrayList<>();
            List<String> ignored = new ArrayList<>();
            Map<Integer, JsonObject> geometries = new HashMap<>();
            Map<Integer, BufferedImage> sheetImages = new HashMap<>();
            List<String> animationPaths = files.keySet().stream()
                    .filter(p -> p.startsWith("render_controllers/") && p.endsWith(SOURCE_SUFFIX))
                    .sorted().toList();
            for (String path : animationPaths) {
                String sid = path.substring("render_controllers/".length(), path.length() - SOURCE_SUFFIX.length());
                String identifier = "twilight:" + sid;
                if (!variants.has(identifier)) continue;
                try {
                    JsonArray value = variants.getAsJsonArray(identifier);
                    int ordinal = value.get(0).getAsInt();
                    int sheet = ordinal / PARTS;
                    if (ordinal < 0 || sheet >= sheetCount) continue;
                    JsonObject geometry = geometries.computeIfAbsent(sheet,
                            s -> optionalJson(files, "models/entity/display.sheet" + s + ".geo.json"));
                    BufferedImage base = sheetImages.computeIfAbsent(sheet,
                            s -> optionalPng(files, "textures/twilight/display_sheet" + s + ".png"));
                    if (geometry == null || base == null || base.getWidth() > MAX_SHEET_SIDE
                            || base.getHeight() > MAX_SHEET_SIDE) continue;
                    Candidate candidate = inspect(files, path, sid, identifier, ordinal, geometry, base);
                    if (candidate == null || candidate.frames.size() * (long) base.getWidth()
                            * base.getHeight() > MAX_FRAME_PIXELS
                            || (!hasVisibleChanges(candidate) && locateInvisibleFace(candidate) == null)) {
                        ignored.add(identifier);
                    } else {
                        candidates.add(candidate);
                    }
                } catch (RuntimeException | IOException malformed) {
                    ignored.add(identifier);
                }
            }
            if (candidates.isEmpty()) return 0;

            // Plan the whole change before mutating the pack, to keep all originals intact on failure.
            Map<String, byte[]> generated = new LinkedHashMap<>();
            JsonObject extraTextures = new JsonObject();
            JsonArray extraEntries = new JsonArray();
            JsonArray extraScripts = new JsonArray();
            JsonArray reportItems = new JsonArray();
            extraScripts.add("v.twilight_uv_frame=math.floor(v.var/" + PARTS + ");");
            int offset = sheetCount;
            for (Candidate candidate : candidates) {
                BufferedImage sheet = sheetImages.get(candidate.ordinal / PARTS);
                Map<Integer, Integer> indices = new HashMap<>();
                RebakePlan plan = locateInvisibleFace(candidate);
                for (int frameIndex : candidate.sequence) {
                    if (indices.containsKey(frameIndex)) continue;
                    BufferedImage source = candidate.frames.get(frameIndex);
                    BufferedImage rebaked = plan == null ? source : rebakeInvisibleFace(source, plan);
                    BufferedImage copy = new BufferedImage(sheet.getWidth(), sheet.getHeight(), BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g = copy.createGraphics();
                    try { g.drawImage(sheet, 0, 0, null); } finally { g.dispose(); }
                    for (FaceLink link : candidate.faces) copyFace(rebaked, copy, link);
                    String name = "twilight_uv_v" + candidate.ordinal + "_f" + frameIndex;
                    String texture = "textures/twilight/display_animated/" + name;
                    String png = texture + ".png";
                    if (files.containsKey(png) || generated.containsKey(png) || textures.has(name)) return 0;
                    generated.put(png, TextureSet.png(copy));
                    extraTextures.addProperty(name, texture);
                    indices.put(frameIndex, offset + extraEntries.size());
                    extraEntries.add("Texture." + name);
                }
                // Texture array positions need not be consecutive if a Java sequence repeats frames.
                // Select by an explicit nested conditional, keeping Java's exact tick sequence.
                StringBuilder choice = new StringBuilder();
                for (int i = candidate.sequence.size() - 1; i >= 0; i--) {
                    int textureIndex = indices.get(candidate.sequence.get(i));
                    if (i == candidate.sequence.size() - 1) choice.append(textureIndex);
                    else choice.insert(0, "(v.twilight_tick==" + i + "?" + textureIndex + ":").append(')');
                }
                extraScripts.add("v.var==" + candidate.ordinal + "?{v.twilight_tick=math.mod(math.floor(" + candidate.clock + "),"
                        + candidate.sequence.size() + ");v.twilight_uv_frame=" + choice + ";};");
                JsonObject entry = new JsonObject();
                entry.addProperty("identifier", candidate.identifier);
                entry.addProperty("ordinal", candidate.ordinal);
                entry.addProperty("faces", candidate.faces.size());
                entry.addProperty("unique_frames", indices.size());
                entry.addProperty("java_ticks", candidate.sequence.size());
                reportItems.add(entry);
            }
            generated.forEach(files::put);
            for (Map.Entry<String, JsonElement> entry : extraTextures.entrySet())
                textures.add(entry.getKey(), entry.getValue());
            for (JsonElement entry : extraEntries) {
                normalTextures.add(entry.deepCopy());
                litTextures.add(entry.deepCopy());
            }
            for (JsonElement entry : extraScripts) script.add(entry.deepCopy());
            // pre_animation already computes v.var at its end; appended scripts run AFTER that assignment.
            setSelector(normal, "Array.textures[v.twilight_uv_frame]");
            setSelector(lit, "Array.textures[v.twilight_uv_frame]");
            files.put(ENTITY, BedrockPackCompiler.jsonBytes(entity));
            files.put(RENDER_CONTROLLERS, BedrockPackCompiler.jsonBytes(rcFile));
            JsonObject report = new JsonObject();
            report.addProperty("algorithm", "face_uv_sheet_v2");
            report.addProperty("repaired", candidates.size());
            report.add("models", reportItems);
            JsonArray skipped = new JsonArray();
            ignored.forEach(skipped::add);
            report.add("skipped", skipped);
            files.put(REPORT, BedrockPackCompiler.jsonBytes(report));
            return generated.size();
        } catch (IOException | RuntimeException malformed) {
            // Malformed/unsupported display assets must not prevent a pack from building.
            return 0;
        }
    }

    private static Candidate inspect(Map<String, byte[]> files, String path, String sid, String identifier,
                                     int ordinal, JsonObject targetGeometry, BufferedImage sheet) throws IOException {
        JsonObject attachRoot = optionalJson(files, "attachables/" + sid + ".json");
        JsonObject sourceGeometry = optionalJson(files, "models/entity/geometry." + sid + ".geo.json");
        JsonObject javaController = optionalJson(files, path);
        if (attachRoot == null || sourceGeometry == null || javaController == null) return null;
        JsonObject attach = attachRoot.getAsJsonObject("minecraft:attachable").getAsJsonObject("description");
        if (!identifier.equals(attach.get("identifier").getAsString())) return null;
        JsonObject arrayHolder = javaController.getAsJsonObject("render_controllers");
        if (arrayHolder.size() != 1) return null;
        JsonObject controller = arrayHolder.entrySet().iterator().next().getValue().getAsJsonObject();
        JsonObject frameArrays = controller.getAsJsonObject("arrays").getAsJsonObject("textures");
        JsonArray sequence = frameArrays.getAsJsonArray("Array.twilight_java_frames");
        if (sequence == null || sequence.size() < 2 || sequence.size() > MAX_FRAMES) return null;
        String selector = controller.getAsJsonArray("textures").get(0).getAsString();
        if (!selector.startsWith("Array.twilight_java_frames[")) return null;
        String clock;
        if (selector.contains("q.time_stamp")) clock = "q.time_stamp";
        else if (selector.contains("q.anim_time * 20")) clock = "q.life_time*20";
        else return null;
        TreeMap<Integer, BufferedImage> frames = new TreeMap<>();
        List<Integer> timeline = new ArrayList<>();
        JsonObject textureNames = attach.getAsJsonObject("textures");
        for (JsonElement frame : sequence) {
            Matcher match = FRAME.matcher(frame.getAsString());
            if (!match.matches()) return null;
            int index = Integer.parseInt(match.group(1));
            if (index > MAX_FRAMES) return null;
            timeline.add(index);
            if (!frames.containsKey(index)) {
                String texture = textureNames.get("java_anim_" + index).getAsString();
                BufferedImage image = optionalPng(files, texture + ".png");
                if (image == null) return null;
                frames.put(index, image);
            }
        }
        if (frames.size() < 2) return null;
        JsonObject source = sourceGeometry.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        JsonObject target = targetGeometry.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        int sw = source.getAsJsonObject("description").get("texture_width").getAsInt();
        int sh = source.getAsJsonObject("description").get("texture_height").getAsInt();
        if (sw > MAX_SHEET_SIDE || sh > MAX_SHEET_SIDE) return null;
        for (BufferedImage frame : frames.values())
            if (frame.getWidth() != sw || frame.getHeight() != sh) return null;
        List<JsonObject> sources = meshBones(source.getAsJsonArray("bones"));
        List<JsonObject> targets = new ArrayList<>();
        String prefixA = "twp" + (ordinal % PARTS) + "_";
        String prefixB = "twc" + (ordinal % PARTS) + "_";
        for (JsonElement element : target.getAsJsonArray("bones")) {
            JsonObject bone = element.getAsJsonObject();
            if (!bone.has("name") || !bone.has("cubes")) continue;
            String name = bone.get("name").getAsString();
            if (name.startsWith(prefixA) || name.startsWith(prefixB)) {
                String suffix = name.substring(name.indexOf('_') + 1);
                if (suffix.chars().allMatch(Character::isDigit)) targets.add(bone);
            }
        }
        targets.sort(Comparator.comparingInt(b -> Integer.parseInt(b.get("name").getAsString()
                .substring(b.get("name").getAsString().indexOf('_') + 1))));
        if (sources.size() != targets.size() || sources.isEmpty()) return null;
        List<FaceLink> faces = new ArrayList<>();
        for (int mesh = 0; mesh < sources.size(); mesh++) {
            JsonArray originalCubes = sources.get(mesh).getAsJsonArray("cubes");
            JsonArray sheetCubes = targets.get(mesh).getAsJsonArray("cubes");
            if (originalCubes.size() != sheetCubes.size()) return null;
            for (int cube = 0; cube < originalCubes.size(); cube++) {
                JsonObject originalFaces = originalCubes.get(cube).getAsJsonObject().getAsJsonObject("uv");
                JsonObject sheetFaces = sheetCubes.get(cube).getAsJsonObject().getAsJsonObject("uv");
                if (originalFaces.size() != sheetFaces.size()) return null;
                for (Map.Entry<String, JsonElement> face : originalFaces.entrySet()) {
                    if (!sheetFaces.has(face.getKey())) return null;
                    FaceLink link = faceLink(face.getValue().getAsJsonObject(), sheetFaces.getAsJsonObject(face.getKey()), sw, sh,
                            sheet.getWidth(), sheet.getHeight());
                    if (link == null) return null;
                    faces.add(link);
                }
            }
        }
        if (faces.isEmpty()) return null;
        return new Candidate(identifier, ordinal, frames, timeline, faces, clock);
    }

    private static List<JsonObject> meshBones(JsonArray array) {
        List<JsonObject> result = new ArrayList<>();
        for (JsonElement element : array) if (element.getAsJsonObject().has("cubes")) result.add(element.getAsJsonObject());
        return result;
    }

    private static FaceLink faceLink(JsonObject source, JsonObject target, int sw, int sh, int tw, int th) {
        JsonArray suv = source.getAsJsonArray("uv"), ssize = source.getAsJsonArray("uv_size");
        JsonArray tuv = target.getAsJsonArray("uv"), tsize = target.getAsJsonArray("uv_size");
        if (suv == null || ssize == null || tuv == null || tsize == null) return null;
        double sx = suv.get(0).getAsDouble(), sy = suv.get(1).getAsDouble();
        double tx = tuv.get(0).getAsDouble(), ty = tuv.get(1).getAsDouble();
        double dx = ssize.get(0).getAsDouble(), dy = ssize.get(1).getAsDouble();
        double ex = tsize.get(0).getAsDouble(), ey = tsize.get(1).getAsDouble();
        if (Math.abs(Math.abs(dx) - Math.abs(ex)) > 1e-6 || Math.abs(Math.abs(dy) - Math.abs(ey)) > 1e-6) return null;
        if (source.has("uv_rotation") != target.has("uv_rotation")) return null;
        if (source.has("uv_rotation") && source.get("uv_rotation").getAsInt() != target.get("uv_rotation").getAsInt()) return null;
        Rect from = rect(sx, sy, dx, dy, sw, sh);
        Rect to = rect(tx, ty, ex, ey, tw, th);
        if (from == null || to == null || from.width() != to.width() || from.height() != to.height()) return null;
        return new FaceLink(from, to, (dx < 0) != (ex < 0), (dy < 0) != (ey < 0));
    }

    private static Rect rect(double x, double y, double w, double h, int width, int height) {
        int x0 = (int) Math.round(Math.min(x, x + w));
        int y0 = (int) Math.round(Math.min(y, y + h));
        int x1 = (int) Math.round(Math.max(x, x + w));
        int y1 = (int) Math.round(Math.max(y, y + h));
        if (x0 < 0 || y0 < 0 || x1 > width || y1 > height || x1 <= x0 || y1 <= y0) return null;
        return new Rect(x0, y0, x1, y1);
    }

    private static boolean hasVisibleChanges(Candidate candidate) {
        BufferedImage first = candidate.frames.firstEntry().getValue();
        for (FaceLink link : candidate.faces) {
            Rect rect = link.from;
            for (int y = rect.y0; y < rect.y1; y++) for (int x = rect.x0; x < rect.x1; x++) {
                int expected = first.getRGB(x, y);
                for (BufferedImage frame : candidate.frames.values())
                    if (frame.getRGB(x, y) != expected) return true;
            }
        }
        return false;
    }

    /** Find at most one transparent face whose window matches an animated-but-unused Java region. */
    private static RebakePlan locateInvisibleFace(Candidate candidate) {
        if (candidate.frames.size() < 2) return null;
        BufferedImage first = candidate.frames.firstEntry().getValue();
        int w = first.getWidth(), h = first.getHeight();
        Rect changed = null;
        int totalChanged = 0, visibleChanged = 0;
        Set<Rect> visible = new HashSet<>();
        for (FaceLink face : candidate.faces) visible.add(face.from);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int original = first.getRGB(x, y);
            boolean different = false;
            for (BufferedImage next : candidate.frames.values()) {
                if (next.getRGB(x, y) != original) { different = true; break; }
            }
            if (!different) continue;
            totalChanged++;
            for (Rect face : visible) {
                if (face.contains(x, y)) { visibleChanged++; break; }
            }
            if (changed == null) changed = new Rect(x, y, x + 1, y + 1);
            else changed = new Rect(Math.min(changed.x0, x), Math.min(changed.y0, y),
                    Math.max(changed.x1, x + 1), Math.max(changed.y1, y + 1));
        }
        // Never relocate pixels that already animate a visible face: that animation was valid.
        if (changed == null || totalChanged < 2 || visibleChanged * 20 > totalChanged) return null;
        List<Rect> destinations = new ArrayList<>();
        for (Rect face : visible) {
            if (face.width() < changed.width() || face.width() > changed.width() + 2
                    || face.height() < changed.height() || face.height() > changed.height() + 2) continue;
            boolean clear = true;
            for (BufferedImage image : candidate.frames.values()) {
                for (int y = face.y0; y < face.y1 && clear; y++) for (int x = face.x0; x < face.x1; x++) {
                    if ((image.getRGB(x, y) >>> 24) != 0) { clear = false; break; }
                }
                if (!clear) break;
            }
            if (clear) destinations.add(face);
        }
        return destinations.size() == 1 ? new RebakePlan(changed, destinations.get(0)) : null;
    }

    private static BufferedImage rebakeInvisibleFace(BufferedImage frame, RebakePlan plan) {
        BufferedImage output = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = output.createGraphics();
        try { g.drawImage(frame, 0, 0, null); } finally { g.dispose(); }
        for (int y = 0; y < plan.source.height(); y++) for (int x = 0; x < plan.source.width(); x++)
            output.setRGB(plan.destination.x0 + x, plan.destination.y0 + y,
                    frame.getRGB(plan.source.x0 + x, plan.source.y0 + y));
        return output;
    }

    private static void copyFace(BufferedImage source, BufferedImage target, FaceLink face) {
        for (int y = 0; y < face.from.height(); y++) for (int x = 0; x < face.from.width(); x++) {
            int sx = face.from.x0 + (face.flipX ? face.from.width() - 1 - x : x);
            int sy = face.from.y0 + (face.flipY ? face.from.height() - 1 - y : y);
            target.setRGB(face.to.x0 + x, face.to.y0 + y, source.getRGB(sx, sy));
        }
    }

    private static JsonArray textureArray(JsonObject controller) {
        return controller.getAsJsonObject("arrays").getAsJsonObject("textures").getAsJsonArray("Array.textures");
    }
    private static String selector(JsonObject controller) { return controller.getAsJsonArray("textures").get(0).getAsString(); }
    private static void setSelector(JsonObject controller, String text) {
        controller.getAsJsonArray("textures").set(0, new com.google.gson.JsonPrimitive(text));
    }
    private static JsonObject json(Map<String, byte[]> files, String path) {
        return JsonParser.parseString(new String(files.get(path), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
    }
    private static JsonObject optionalJson(Map<String, byte[]> files, String path) {
        return files.containsKey(path) ? json(files, path) : null;
    }
    private static BufferedImage optionalPng(Map<String, byte[]> files, String path) {
        byte[] bytes = files.get(path);
        if (bytes == null) return null;
        try { return PngImages.read(bytes); } catch (IOException | RuntimeException invalid) { return null; }
    }
    private record Rect(int x0, int y0, int x1, int y1) {
        int width() { return x1 - x0; }
        int height() { return y1 - y0; }
        boolean contains(int x, int y) { return x >= x0 && x < x1 && y >= y0 && y < y1; }
    }
    private record FaceLink(Rect from, Rect to, boolean flipX, boolean flipY) {}
    private record RebakePlan(Rect source, Rect destination) {}
    private record Candidate(String identifier, int ordinal, TreeMap<Integer, BufferedImage> frames,
                             List<Integer> sequence, List<FaceLink> faces, String clock) {}
}
