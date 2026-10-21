/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.compiler;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Orthographic, build-time rendering of a Java model in its authored GUI pose. */
final class GuiIconRenderer {
    private static final int SIZE = 64;
    private static final double PIXELS_PER_UNIT = SIZE / 16.0;

    private GuiIconRenderer() {}

    static BufferedImage render(ResolvedJavaModel model, TextureSet atlas, JsonArray elements) throws IOException {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        double[] depth = new double[SIZE * SIZE];
        Arrays.fill(depth, Double.NEGATIVE_INFINITY);
        List<List<Fragment>> translucent = new ArrayList<>(SIZE * SIZE);
        for (int i = 0; i < SIZE * SIZE; i++) translucent.add(null);
        // Composite children each apply their own display transform and lighting,
        // but share a depth buffer so intersections are resolved across children.
        for (ResolvedJavaModel part : model.renderParts()) {
            JsonObject gui = part.display().has("gui") ? part.display().getAsJsonObject("gui") : new JsonObject();
            Vec scale = vector(gui, "scale", new Vec(1, 1, 1)).clamp(-4, 4);
            Vec rotation = vector(gui, "rotation", Vec.ZERO);
            Vec translation = vector(gui, "translation", Vec.ZERO).clamp(-80, 80);
            for (JsonElement value : model.parts().isEmpty() ? elements : BedrockPackCompiler.geometryElements(part)) {
                JsonObject element = value.getAsJsonObject();
                Vec from = vector(element, "from", Vec.ZERO), to = vector(element, "to", new Vec(16, 16, 16));
                JsonObject faces = element.getAsJsonObject("faces");
                if (faces == null) continue;
                for (var entry : faces.entrySet()) {
                    JsonObject face = entry.getValue().getAsJsonObject();
                    if (!face.has("texture")) continue;
                    String reference = face.get("texture").getAsString();
                    String texture = reference.startsWith("#") ? part.textures().get(reference.substring(1))
                            : JavaModelResolver.qualified(reference, "minecraft");
                    if (texture == null) texture = TextureSet.MISSING; // as Java draws it
                    TextureSet.Region region = atlas.region(texture);
                    Vec[] vertices = vertices(entry.getKey(), from, to);
                    for (int i = 0; i < 4; i++) {
                        Vec point = elementRotation(vertices[i], element.getAsJsonObject("rotation"));
                        vertices[i] = point.subtract(new Vec(8, 8, 8)).multiply(scale).rotate(rotation).add(translation);
                    }
                    Vec normal = vertices[1].subtract(vertices[0]).cross(vertices[2].subtract(vertices[0])).normalized();
                    // GUI looks toward -Z. Keep the authored winding, including mirrored scales.
                    if (normal.z <= 1e-9) continue;
                    double shade = lighting(normal, part.frontLight());
                    double[] uv = face.has("uv") ? numbers(face.getAsJsonArray("uv"), 4)
                            : BedrockPackCompiler.defaultFaceUv(entry.getKey(), from.array(), to.array());
                    int turn = face.has("rotation") ? Math.floorMod(face.get("rotation").getAsInt(), 360) : 0;
                    if (turn % 90 != 0) throw new IOException("GUI face UV rotation must be a multiple of 90");
                    double[][] corners = {{uv[0], uv[1]}, {uv[0], uv[3]}, {uv[2], uv[3]}, {uv[2], uv[1]}};
                    Vertex[] quad = new Vertex[4];
                    for (int i = 0; i < 4; i++) {
                        Vec point = vertices[i];
                        double[] tex = corners[(i + turn / 90) % 4];
                        quad[i] = new Vertex(SIZE / 2.0 + point.x * PIXELS_PER_UNIT,
                                SIZE / 2.0 - point.y * PIXELS_PER_UNIT, point.z, tex[0], tex[1]);
                    }
                    rasterize(quad, atlas, region, shade, image, depth, translucent);
                }
            }
        }
        for (int i = 0; i < translucent.size(); i++) {
            List<Fragment> fragments = translucent.get(i);
            if (fragments == null) continue;
            fragments.sort(Comparator.comparingDouble(Fragment::depth));
            int x = i % SIZE, y = i / SIZE, color = image.getRGB(x, y);
            for (Fragment fragment : fragments) if (fragment.depth >= depth[i]) color = over(fragment.color, color);
            image.setRGB(x, y, color);
        }
        return image;
    }

    private static void rasterize(Vertex[] q, TextureSet atlas, TextureSet.Region region, double shade,
                                  BufferedImage image, double[] depth, List<List<Fragment>> translucent) {
        int minX = SIZE, minY = SIZE, maxX = -1, maxY = -1;
        for (Vertex v : q) {
            minX = Math.min(minX, (int) Math.floor(v.x)); minY = Math.min(minY, (int) Math.floor(v.y));
            maxX = Math.max(maxX, (int) Math.ceil(v.x)); maxY = Math.max(maxY, (int) Math.ceil(v.y));
        }
        for (int y = Math.max(0, minY); y <= Math.min(SIZE - 1, maxY); y++) {
            for (int x = Math.max(0, minX); x <= Math.min(SIZE - 1, maxX); x++) {
                Vertex sample = interpolate(q[0], q[1], q[2], x + .5, y + .5);
                if (sample == null) sample = interpolate(q[0], q[2], q[3], x + .5, y + .5);
                if (sample == null) continue;
                int pixel = y * SIZE + x;
                if (sample.z < depth[pixel]) continue;
                int color = atlas.sample(region, sample.u, sample.v), alpha = color >>> 24;
                if (alpha == 0) continue; // Transparent texels must not hide the faces behind them.
                color = (alpha << 24) | ((int) (((color >>> 16) & 255) * shade) << 16)
                        | ((int) (((color >>> 8) & 255) * shade) << 8) | (int) ((color & 255) * shade);
                if (alpha == 255) {
                    depth[pixel] = sample.z;
                    image.setRGB(x, y, color);
                } else {
                    if (translucent.get(pixel) == null) translucent.set(pixel, new ArrayList<>());
                    translucent.get(pixel).add(new Fragment(sample.z, color));
                }
            }
        }
    }

    private static Vertex interpolate(Vertex a, Vertex b, Vertex c, double x, double y) {
        double area = (b.y - c.y) * (a.x - c.x) + (c.x - b.x) * (a.y - c.y);
        if (Math.abs(area) < 1e-9) return null;
        double wa = ((b.y - c.y) * (x - c.x) + (c.x - b.x) * (y - c.y)) / area;
        double wb = ((c.y - a.y) * (x - c.x) + (a.x - c.x) * (y - c.y)) / area;
        double wc = 1 - wa - wb;
        if (Math.min(wa, Math.min(wb, wc)) < -1e-9) return null;
        return new Vertex(x, y, wa * a.z + wb * b.z + wc * c.z,
                wa * a.u + wb * b.u + wc * c.u, wa * a.v + wb * b.v + wc * c.v);
    }

    private static int over(int source, int destination) {
        double a = (source >>> 24) / 255.0, b = (destination >>> 24) / 255.0 * (1 - a), total = a + b;
        int result = (int) Math.round(total * 255) << 24;
        for (int shift : new int[]{16, 8, 0}) result |= (int) Math.round(
                (((source >>> shift) & 255) * a + ((destination >>> shift) & 255) * b) / total) << shift;
        return result;
    }

    private static Vec elementRotation(Vec point, JsonObject rotation) throws IOException {
        if (rotation == null) return point;
        Vec origin = vector(rotation, "origin", new Vec(8, 8, 8));
        double angle = rotation.has("angle") ? rotation.get("angle").getAsDouble() : 0;
        String axis = rotation.has("axis") ? rotation.get("axis").getAsString() : "y";
        Vec angles = switch (axis) {
            case "x" -> new Vec(angle, 0, 0);
            case "y" -> new Vec(0, angle, 0);
            case "z" -> new Vec(0, 0, angle);
            default -> throw new IOException("Unknown GUI element rotation axis " + axis);
        };
        Vec result = point.subtract(origin).rotate(angles);
        if (rotation.has("rescale") && rotation.get("rescale").getAsBoolean()) {
            double factor = 1 / Math.cos(Math.toRadians(angle));
            if (!Double.isFinite(factor) || Math.abs(factor) > 100) throw new IOException("Invalid rescaled GUI rotation");
            result = result.multiply(new Vec(axis.equals("x") ? 1 : factor,
                    axis.equals("y") ? 1 : factor, axis.equals("z") ? 1 : factor));
        }
        return result.add(origin);
    }

    private static Vec[] vertices(String face, Vec a, Vec b) throws IOException {
        return switch (face) {
            case "south" -> new Vec[]{new Vec(a.x,b.y,b.z), new Vec(a.x,a.y,b.z), new Vec(b.x,a.y,b.z), new Vec(b.x,b.y,b.z)};
            case "north" -> new Vec[]{new Vec(b.x,b.y,a.z), new Vec(b.x,a.y,a.z), new Vec(a.x,a.y,a.z), new Vec(a.x,b.y,a.z)};
            case "west" -> new Vec[]{new Vec(a.x,b.y,a.z), new Vec(a.x,a.y,a.z), new Vec(a.x,a.y,b.z), new Vec(a.x,b.y,b.z)};
            case "east" -> new Vec[]{new Vec(b.x,b.y,b.z), new Vec(b.x,a.y,b.z), new Vec(b.x,a.y,a.z), new Vec(b.x,b.y,a.z)};
            case "up" -> new Vec[]{new Vec(a.x,b.y,a.z), new Vec(a.x,b.y,b.z), new Vec(b.x,b.y,b.z), new Vec(b.x,b.y,a.z)};
            case "down" -> new Vec[]{new Vec(a.x,a.y,b.z), new Vec(a.x,a.y,a.z), new Vec(b.x,a.y,a.z), new Vec(b.x,a.y,b.z)};
            default -> throw new IOException("Unknown GUI face " + face);
        };
    }

    private static double lighting(Vec normal, boolean front) {
        // Java's GUI diffuse lights, expressed before the GUI's Y-axis flip.
        double sum = 0;
        for (double z : new double[]{-.7, .7}) {
            Vec light = new Vec(z < 0 ? .2 : -.2, 1, z).normalized()
                    .rotate(new Vec(135, 0, 0)).rotate(new Vec(0, -22.5, 0));
            if (!front) light = light.rotate(new Vec(185.5, 0, 0)).rotate(new Vec(0, 62, 0));
            else light = light.multiply(new Vec(1, -1, 1));
            sum += Math.max(0, normal.dot(light));
        }
        return Math.min(1, .4 + .6 * sum);
    }

    private static Vec vector(JsonObject object, String key, Vec fallback) throws IOException {
        if (!object.has(key)) return fallback;
        double[] values = numbers(object.getAsJsonArray(key), 3);
        return new Vec(values[0], values[1], values[2]);
    }

    private static double[] numbers(JsonArray array, int length) throws IOException {
        if (array.size() != length) throw new IOException("Invalid GUI model vector length");
        double[] result = new double[length];
        for (int i = 0; i < length; i++) {
            result[i] = array.get(i).getAsDouble();
            if (!Double.isFinite(result[i])) throw new IOException("Non-finite GUI model coordinate");
        }
        return result;
    }

    private record Vertex(double x, double y, double z, double u, double v) {}
    private record Fragment(double depth, int color) {}
    private record Vec(double x, double y, double z) {
        static final Vec ZERO = new Vec(0, 0, 0);
        Vec add(Vec v) { return new Vec(x + v.x, y + v.y, z + v.z); }
        Vec subtract(Vec v) { return new Vec(x - v.x, y - v.y, z - v.z); }
        Vec multiply(Vec v) { return new Vec(x * v.x, y * v.y, z * v.z); }
        Vec clamp(double min, double max) { return new Vec(Math.clamp(x,min,max), Math.clamp(y,min,max), Math.clamp(z,min,max)); }
        double dot(Vec v) { return x * v.x + y * v.y + z * v.z; }
        Vec cross(Vec v) { return new Vec(y*v.z-z*v.y, z*v.x-x*v.z, x*v.y-y*v.x); }
        Vec normalized() { double length = Math.sqrt(dot(this)); return length < 1e-12 ? ZERO : new Vec(x/length,y/length,z/length); }
        double[] array() { return new double[]{x, y, z}; }
        Vec rotate(Vec degrees) {
            // rotationXYZ multiplies Rx * Ry * Rz: apply Z, then Y, then X.
            double rz = Math.toRadians(degrees.z), ry = Math.toRadians(degrees.y), rx = Math.toRadians(degrees.x);
            double xx = x*Math.cos(rz)-y*Math.sin(rz), yy = x*Math.sin(rz)+y*Math.cos(rz);
            double xxx = xx*Math.cos(ry)+z*Math.sin(ry), zz = -xx*Math.sin(ry)+z*Math.cos(ry);
            return new Vec(xxx, yy*Math.cos(rx)-zz*Math.sin(rx), yy*Math.sin(rx)+zz*Math.cos(rx));
        }
    }
}
