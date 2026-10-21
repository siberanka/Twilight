package com.siberanka.twilight.compiler;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Repairs animated Java item atlases in *standalone* Bedrock attachables, including furniture
 * rendered by armor stands. This is deliberately separate from the item-display sheet handler:
 * the resource pack may contain no display-index at all when display models are disabled.
 *
 * <p>Never moves animation pixels on a guess. A re-bake requires a unique transparent, geometry-
 * referenced face with the right dimensions, and changed pixels which are not already visible.
 * Existing model geometries, UV coordinates, armor and item transforms are never modified.
 */
final class AnimatedAttachableUvRepair {
    static final String REPORT = "twilight/animated-attachable-uv-report.json";
    private static final String PREFIX = "render_controllers/";
    private static final String SUFFIX = "_java_animation.json";
    private static final Pattern REF = Pattern.compile("Texture\\.java_anim_(\\d+)");
    private static final int MAX_FRAMES = 512;
    private static final int MAX_PIXELS = 4096 * 4096;

    private AnimatedAttachableUvRepair() { }

    static int apply(Map<String, byte[]> files) {
        int moved = 0, clocks = 0;
        JsonArray diagnostics = new JsonArray();
        for (String controllerPath : List.copyOf(files.keySet())) {
            if (!controllerPath.startsWith(PREFIX) || !controllerPath.endsWith(SUFFIX)) continue;
            String safe = controllerPath.substring(PREFIX.length(), controllerPath.length() - SUFFIX.length());
            String attachPath = "attachables/" + safe + ".json";
            String geoPath = "models/entity/geometry." + safe + ".geo.json";
            if (!files.containsKey(attachPath)) continue;
            String status = "unchanged";
            try {
                JsonObject root = json(files.get(controllerPath));
                JsonObject controllers = root.getAsJsonObject("render_controllers");
                if (controllers == null || controllers.size() != 1) continue;
                JsonObject controller = controllers.entrySet().iterator().next().getValue().getAsJsonObject();
                JsonArray selector = controller.getAsJsonArray("textures");
                JsonObject arrays = controller.getAsJsonObject("arrays");
                JsonObject textureArrays = arrays == null ? null : arrays.getAsJsonObject("textures");
                JsonArray timeline = textureArrays == null ? null : textureArrays.getAsJsonArray("Array.twilight_java_frames");
                if (timeline == null || timeline.size() < 2 || timeline.size() > 512 || selector == null || selector.size() != 1) continue;

                // Render controllers do not own an animation clock: q.anim_time can stay at zero here.
                // time_stamp is the world 20-Hz tick clock, also used by Twilight's weapon animation bridge.
                String expression = selector.get(0).getAsString();
                String corrected = expression.replace("q.anim_time * 20", "q.time_stamp");
                if (!expression.equals(corrected)) {
                    selector.set(0, new JsonPrimitive(corrected));
                    files.put(controllerPath, BedrockPackCompiler.jsonBytes(root));
                    clocks++;
                }

                if (!files.containsKey(geoPath)) { status = "no_geometry"; continue; }
                JsonObject attachRoot = json(files.get(attachPath));
                JsonObject attachable = attachRoot.getAsJsonObject("minecraft:attachable");
                JsonObject description = attachable == null ? null : attachable.getAsJsonObject("description");
                JsonObject textures = description == null ? null : description.getAsJsonObject("textures");
                if (textures == null) { status = "no_textures"; continue; }
                List<Rect> faces = readFaceRects(json(files.get(geoPath)));
                if (faces.isEmpty()) { status = "no_faces"; continue; }

                Map<Integer, String> paths = new LinkedHashMap<>();
                for (JsonElement frame : timeline) {
                    Matcher m = REF.matcher(frame.getAsString());
                    if (!m.matches()) { paths.clear(); break; }
                    int index = Integer.parseInt(m.group(1));
                    if (index > MAX_FRAMES) { paths.clear(); break; }
                    String key = "java_anim_" + index;
                    if (!textures.has(key)) { paths.clear(); break; }
                    paths.put(index, textures.get(key).getAsString() + ".png");
                }
                if (paths.size() < 2 || paths.size() > MAX_FRAMES || !paths.values().stream().allMatch(files::containsKey)) {
                    status = "frames_missing"; continue;
                }
                List<BufferedImage> frames = new ArrayList<>();
                for (String framePath : paths.values()) {
                    BufferedImage image = PngImages.read(files.get(framePath));
                    if (image == null || (long) image.getWidth() * image.getHeight() > MAX_PIXELS) { frames.clear(); break; }
                    if (!frames.isEmpty() && (image.getWidth() != frames.getFirst().getWidth()
                            || image.getHeight() != frames.getFirst().getHeight())) { frames.clear(); break; }
                    frames.add(image);
                }
                if (frames.size() != paths.size()) { status = "invalid_frames"; continue; }
                Rect changed = changedBounds(frames);
                if (changed == null) { status = "identical_frames"; continue; }
                if (changedVisible(frames, faces) * 20 > changedPixels(frames)) {
                    // Don't stomp already-visible animations, including animations with several active regions.
                    status = "already_visible"; continue;
                }
                List<Rect> destinations = new ArrayList<>();
                for (Rect rect : faces) {
                    if (rect.width() < changed.width() || rect.width() > changed.width() + 2
                            || rect.height() < changed.height() || rect.height() > changed.height() + 2) continue;
                    if (rect.x0 < 0 || rect.y0 < 0 || rect.x1 > frames.getFirst().getWidth()
                            || rect.y1 > frames.getFirst().getHeight()) continue;
                    if (transparentAll(frames, rect)) destinations.add(rect);
                }
                if (destinations.size() != 1) {
                    status = destinations.isEmpty() ? "no_matching_transparent_face" : "ambiguous_faces";
                    continue;
                }
                Rect dest = destinations.getFirst();
                int shiftX = dest.x0 + (dest.width() - changed.width()) / 2;
                int shiftY = dest.y0 + (dest.height() - changed.height()) / 2;
                Map<String, byte[]> outputs = new LinkedHashMap<>();
                int n = 0;
                for (String framePath : paths.values()) {
                    BufferedImage original = frames.get(n++);
                    BufferedImage output = new BufferedImage(original.getWidth(), original.getHeight(), BufferedImage.TYPE_INT_ARGB);
                    java.awt.Graphics2D g = output.createGraphics();
                    try { g.drawImage(original, 0, 0, null); } finally { g.dispose(); }
                    for (int y = 0; y < changed.height(); y++) for (int x = 0; x < changed.width(); x++) {
                        output.setRGB(shiftX + x, shiftY + y, original.getRGB(changed.x0 + x, changed.y0 + y));
                    }
                    outputs.put(framePath, TextureSet.png(output));
                }
                files.putAll(outputs);
                moved++;
                status = "rebaked_to_visible_uv";
            } catch (IOException | RuntimeException invalid) {
                status = "invalid_assets";
            } finally {
                JsonObject item = new JsonObject();
                item.addProperty("model", safe);
                item.addProperty("status", status);
                diagnostics.add(item);
            }
        }
        JsonObject report = new JsonObject();
        report.addProperty("algorithm", "standalone_attachable_java_animation_face_uv");
        report.addProperty("clocks_fixed", clocks);
        report.addProperty("atlases_rebaked", moved);
        report.add("models", diagnostics);
        files.put(REPORT, BedrockPackCompiler.jsonBytes(report));
        return moved;
    }

    private static Rect changedBounds(List<BufferedImage> frames) {
        BufferedImage first = frames.getFirst();
        int x0 = first.getWidth(), y0 = first.getHeight(), x1 = 0, y1 = 0;
        for (int y = 0; y < first.getHeight(); y++) for (int x = 0; x < first.getWidth(); x++) {
            int baseline = first.getRGB(x, y);
            boolean changed = false;
            for (int f = 1; f < frames.size(); f++) {
                if (frames.get(f).getRGB(x, y) != baseline) { changed = true; break; }
            }
            if (!changed) continue;
            x0 = Math.min(x0, x); y0 = Math.min(y0, y);
            x1 = Math.max(x1, x + 1); y1 = Math.max(y1, y + 1);
        }
        return x1 > x0 && y1 > y0 ? new Rect(x0, y0, x1, y1) : null;
    }

    private static int changedPixels(List<BufferedImage> frames) {
        BufferedImage first = frames.getFirst();
        int changed = 0;
        for (int y = 0; y < first.getHeight(); y++) for (int x = 0; x < first.getWidth(); x++) {
            int baseline = first.getRGB(x, y);
            for (int n = 1; n < frames.size(); n++) {
                if (frames.get(n).getRGB(x, y) != baseline) { changed++; break; }
            }
        }
        return changed;
    }

    private static int changedVisible(List<BufferedImage> frames, List<Rect> faces) {
        BufferedImage first = frames.getFirst();
        int count = 0;
        for (int y = 0; y < first.getHeight(); y++) for (int x = 0; x < first.getWidth(); x++) {
            boolean visible = false;
            for (Rect rect : faces) if (rect.contains(x, y)) { visible = true; break; }
            if (!visible) continue;
            int baseline = first.getRGB(x, y);
            for (int n = 1; n < frames.size(); n++) {
                if (frames.get(n).getRGB(x, y) != baseline) { count++; break; }
            }
        }
        return count;
    }

    private static boolean transparentAll(List<BufferedImage> frames, Rect region) {
        for (BufferedImage image : frames) for (int y = region.y0; y < region.y1; y++)
            for (int x = region.x0; x < region.x1; x++)
                if ((image.getRGB(x, y) >>> 24) != 0) return false;
        return true;
    }

    private static List<Rect> readFaceRects(JsonObject geometry) {
        Set<Rect> rects = new LinkedHashSet<>();
        JsonArray geometries = geometry.getAsJsonArray("minecraft:geometry");
        if (geometries == null) return List.of();
        for (JsonElement layer : geometries) {
            JsonArray bones = layer.getAsJsonObject().getAsJsonArray("bones");
            if (bones == null) continue;
            for (JsonElement part : bones) {
                JsonArray cubes = part.getAsJsonObject().getAsJsonArray("cubes");
                if (cubes == null) continue;
                for (JsonElement cube : cubes) {
                    JsonObject uv = cube.getAsJsonObject().getAsJsonObject("uv");
                    if (uv == null) continue;
                    for (Map.Entry<String, JsonElement> entry : uv.entrySet()) {
                        JsonObject face = entry.getValue().getAsJsonObject();
                        JsonArray coords = face.getAsJsonArray("uv"), size = face.getAsJsonArray("uv_size");
                        if (coords == null || size == null) continue;
                        double u = coords.get(0).getAsDouble(), v = coords.get(1).getAsDouble();
                        double w = size.get(0).getAsDouble(), h = size.get(1).getAsDouble();
                        int x0 = (int) Math.round(Math.min(u, u + w));
                        int y0 = (int) Math.round(Math.min(v, v + h));
                        int x1 = (int) Math.round(Math.max(u, u + w));
                        int y1 = (int) Math.round(Math.max(v, v + h));
                        if (x1 > x0 && y1 > y0) rects.add(new Rect(x0, y0, x1, y1));
                    }
                }
            }
        }
        return List.copyOf(rects);
    }

    private static JsonObject json(byte[] data) {
        return JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
    }
    private record Rect(int x0, int y0, int x1, int y1) {
        int width() { return x1 - x0; }
        int height() { return y1 - y0; }
        boolean contains(int x, int y) { return x >= x0 && x < x1 && y >= y0 && y < y1; }
    }
}
