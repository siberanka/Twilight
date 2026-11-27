/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.compiler;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Java's {@code .png.mcmeta} animations (frame size, frame order, per-frame time, interpolation) and their
 * conversion to the one thing every Bedrock target can play: a list of whole-texture states shown in a schedule
 * of equal tick units. Java steps its animations once per client tick (20 per second), and so does Bedrock.
 */
final class TextureAnimation {
    private TextureAnimation() {}

    /** Most distinct states (full texture copies) a block flipbook may hold. */
    static final int MAX_BLOCK_STATES = 64;
    /** Most distinct states (full texture copies) an entity texture may hold. */
    static final int MAX_ENTITY_STATES = 32;
    /** Most entries of a schedule (a flipbook's frame list, an entity texture array). */
    static final int MAX_UNITS = 1024;
    /** Longest combined cycle in ticks (20 minutes). */
    static final long MAX_PERIOD = 24_000;
    /** Most pixels all states of one texture may hold together. */
    static final long MAX_PIXELS = 16_000_000L;
    /** Tallest flipbook strip. */
    static final int MAX_STRIP_HEIGHT = 8192;

    /**
     * One sprite's animation as Java plays it: the frames of its sheet and a cycle of segments, each showing a
     * frame for some ticks.
     *
     * @param image index into {@code images} per segment
     * @param ticks duration per segment (at least 1)
     */
    record Track(String name, List<BufferedImage> images, int[] image, int[] ticks, boolean interpolate) {
        long period() {
            long sum = 0;
            for (int value : ticks) sum += value;
            return sum;
        }

        Track withoutInterpolation() {
            return interpolate ? new Track(name, images, image, ticks, false) : this;
        }

        Track map(java.util.function.UnaryOperator<BufferedImage> change) {
            List<BufferedImage> mapped = new ArrayList<>();
            for (BufferedImage frame : images) mapped.add(change.apply(frame));
            return new Track(name, List.copyOf(mapped), image, ticks, interpolate);
        }
    }

    /** A track drawn at a position of a larger image. */
    record Layer(Track track, int x, int y) {}

    /** An image with animated regions: {@code base} shows every track's first frame. */
    record Animated(BufferedImage base, List<Layer> layers) {
        Animated squared() {
            int side = Math.max(base.getWidth(), base.getHeight());
            BufferedImage image = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            try {
                graphics.setComposite(AlphaComposite.Src);
                graphics.drawImage(base, 0, 0, null);
            } finally { graphics.dispose(); }
            return new Animated(image, layers);
        }

        /** A stable text of what plays, for grouping equal animations. */
        String fingerprint() {
            StringBuilder text = new StringBuilder();
            for (Layer layer : layers) {
                text.append(layer.x).append(',').append(layer.y).append(',').append(layer.track.interpolate).append(';')
                        .append(Arrays.toString(layer.track.image)).append(Arrays.toString(layer.track.ticks)).append(';');
                for (BufferedImage frame : layer.track.images) text.append(Arrays.hashCode(pixels(frame))).append(',');
                text.append('|');
            }
            return text.toString();
        }

        Animated shifted(int dx, int dy, BufferedImage newBase) {
            List<Layer> moved = new ArrayList<>();
            for (Layer layer : layers) moved.add(new Layer(layer.track, layer.x + dx, layer.y + dy));
            return new Animated(newBase, moved);
        }
    }

    private static int[] pixels(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }

    /**
     * What plays: {@code schedule} lists, per unit of {@code unitTicks} ticks, the index of the state to show;
     * {@code blend} lets Bedrock's flipbook interpolate (every state lasts exactly one unit then).
     */
    record Plan(int unitTicks, int[] schedule, List<BufferedImage> states, boolean blend, List<String> notes) {}

    /** The outcome of reading one sprite: its first frame and, if it moves, the animation. */
    record Parsed(BufferedImage first, Track track) {}

    /**
     * Reads a sprite sheet with its metadata like Java's sprite loader. A frame size that does not divide the sheet
     * makes Java draw the missing texture; frames with a missing index or a duration below one are skipped; fewer
     * than two frames left means no animation; unreadable metadata is ignored.
     */
    static Parsed parse(String name, BufferedImage sheet, String metadata, List<String> notes) {
        if (metadata == null) return new Parsed(sheet, null);
        try {
            JsonObject root = com.google.gson.JsonParser.parseString(metadata).getAsJsonObject();
            if (!root.has("animation")) return new Parsed(sheet, null);
            JsonObject animation = root.getAsJsonObject("animation");
            int width = animation.has("width") ? animation.get("width").getAsInt() : -1;
            int height = animation.has("height") ? animation.get("height").getAsInt() : -1;
            int defaultTime = animation.has("frametime") ? animation.get("frametime").getAsInt() : 1;
            boolean interpolate = animation.has("interpolate") && animation.get("interpolate").getAsBoolean();
            if (defaultTime < 1) throw new IllegalArgumentException("frametime below one");
            if (width == -1 && height == -1) width = height = Math.min(sheet.getWidth(), sheet.getHeight());
            else {
                if (width == -1) width = sheet.getWidth();
                if (height == -1) height = sheet.getHeight();
            }
            if (width < 1 || height < 1 || sheet.getWidth() % width != 0 || sheet.getHeight() % height != 0) {
                notes.add(name + ": its animation frame size " + width + "x" + height + " does not divide the "
                        + sheet.getWidth() + "x" + sheet.getHeight() + " image; Java draws its missing texture for it");
                return new Parsed(TextureSet.missingTexture(), null);
            }
            int columns = sheet.getWidth() / width;
            int total = columns * (sheet.getHeight() / height);
            List<int[]> segments = new ArrayList<>();
            int skipped = 0;
            if (animation.has("frames") && animation.get("frames").isJsonArray() && !animation.getAsJsonArray("frames").isEmpty()) {
                for (JsonElement entry : animation.getAsJsonArray("frames")) {
                    int index, time = defaultTime;
                    if (entry.isJsonObject()) {
                        JsonObject object = entry.getAsJsonObject();
                        index = object.get("index").getAsInt();
                        if (object.has("time")) time = object.get("time").getAsInt();
                    } else index = entry.getAsInt();
                    if (time < 1 || index < 0 || index >= total) { skipped++; continue; }
                    segments.add(new int[]{index, time});
                }
            } else {
                for (int index = 0; index < total; index++) segments.add(new int[]{index, defaultTime});
            }
            if (skipped > 0) notes.add(name + ": " + skipped + " animation frame(s) have a missing index or a duration below one;"
                    + " Java skips them");
            List<BufferedImage> frames = new ArrayList<>();
            for (int index = 0; index < total; index++) {
                frames.add(sheet.getSubimage((index % columns) * width, (index / columns) * height, width, height));
            }
            int first = segments.isEmpty() ? 0 : segments.getFirst()[0];
            if (segments.size() <= 1) return new Parsed(frames.get(first), null);
            int[] image = new int[segments.size()], ticks = new int[segments.size()];
            for (int i = 0; i < image.length; i++) { image[i] = segments.get(i)[0]; ticks[i] = segments.get(i)[1]; }
            return new Parsed(frames.get(image[0]), new Track(name, List.copyOf(frames), image, ticks, interpolate));
        } catch (RuntimeException invalid) {
            notes.add(name + ": its animation metadata is not valid (" + invalid.getMessage() + "); the whole image is used");
            return new Parsed(sheet, null);
        }
    }

    /**
     * The states and schedule that play the layers together. Targets without interpolation get Java's
     * interpolated frames as extra states. A set that cannot be held in the limits freezes its slowest tracks (they
     * keep their first frame) and says so in the notes.
     *
     * @param flipbook true for a block flipbook (equal ticks per frame, Bedrock can blend consecutive frames), false
     *                 for entity textures
     */
    static Plan plan(Animated animated, boolean flipbook, int maxStates) {
        List<String> notes = new ArrayList<>();
        List<Layer> layers = new ArrayList<>(animated.layers());
        if (flipbook) {
            // Bedrock's flipbook blends whole frames only; one uniformly timed track can use that, others cannot.
            boolean blend = layers.size() == 1 && layers.getFirst().track.interpolate && uniform(layers.getFirst().track);
            if (!blend) {
                for (Layer layer : layers) if (layer.track.interpolate) {
                    notes.add(layer.track.name + ": interpolation is shown as the nearest frame on Bedrock");
                }
                layers.replaceAll(layer -> new Layer(layer.track.withoutInterpolation(), layer.x, layer.y));
            }
        }
        while (!layers.isEmpty()) {
            Plan plan = attempt(animated.base(), layers, flipbook, maxStates, notes);
            if (plan != null) return plan;
            int slowest = 0;
            for (int i = 1; i < layers.size(); i++) if (layers.get(i).track.period() > layers.get(slowest).track.period()) slowest = i;
            notes.add(layers.get(slowest).track.name + ": its animation does not fit the Bedrock limits together with the"
                    + " others (cycle, frame count or size); it shows its first frame");
            layers.remove(slowest);
        }
        return null;
    }

    private static boolean uniform(Track track) {
        for (int value : track.ticks) if (value != track.ticks[0]) return false;
        return track.image.length >= 2;
    }

    private static Plan attempt(BufferedImage base, List<Layer> layers, boolean flipbook, int maxStates, List<String> notes) {
        long pixels = (long) base.getWidth() * base.getHeight();
        int cap = (int) Math.min(maxStates, Math.min(flipbook ? MAX_STRIP_HEIGHT / Math.max(1, base.getHeight()) : Integer.MAX_VALUE,
                MAX_PIXELS / Math.max(1, pixels)));
        if (cap < 2) return null;
        long period = 1;
        for (Layer layer : layers) {
            long own = layer.track.period();
            period = period / gcd(period, own) * own;
            if (period > MAX_PERIOD) return null;
        }
        if (layers.size() == 1 && flipbook && layers.getFirst().track.interpolate && uniform(layers.getFirst().track)) {
            Track track = layers.getFirst().track;
            List<Integer> unique = new ArrayList<>();
            int[] schedule = new int[track.image.length];
            for (int i = 0; i < schedule.length; i++) {
                int position = unique.indexOf(track.image[i]);
                if (position < 0) { unique.add(track.image[i]); position = unique.size() - 1; }
                schedule[i] = position;
            }
            if (unique.size() > cap || schedule.length > MAX_UNITS) return null;
            List<BufferedImage> states = new ArrayList<>();
            for (int frame : unique) states.add(render(base, layers, new long[][]{{frame, frame, 0, 1}}));
            return new Plan(track.ticks[0], schedule, states, true, List.copyOf(notes));
        }
        long nonInterpolated = 0;
        boolean interpolating = false;
        for (Layer layer : layers) {
            if (layer.track.interpolate) { interpolating = true; continue; }
            for (int value : layer.track.ticks) nonInterpolated = gcd(nonInterpolated, value);
        }
        List<Long> steps = new ArrayList<>();
        if (nonInterpolated > 0) {
            if (interpolating) { for (long d = 1; d <= nonInterpolated; d++) if (nonInterpolated % d == 0) steps.add(d); }
            else steps.add(nonInterpolated);
        } else {
            for (long d = 1; d <= period; d++) if (period % d == 0) steps.add(d);
        }
        for (long step : steps) {
            if (period / step > MAX_UNITS) continue;
            int units = (int) (period / step);
            Map<String, Integer> index = new LinkedHashMap<>();
            List<long[][]> vectors = new ArrayList<>();
            int[] schedule = new int[units];
            boolean fits = true;
            for (int unit = 0; unit < units && fits; unit++) {
                long tick = unit * step;
                long[][] vector = new long[layers.size()][];
                StringBuilder key = new StringBuilder();
                for (int i = 0; i < vector.length; i++) {
                    vector[i] = sample(layers.get(i).track, tick);
                    key.append(Arrays.toString(vector[i]));
                }
                Integer found = index.get(key.toString());
                if (found == null) {
                    if (index.size() >= cap) { fits = false; break; }
                    found = index.size();
                    index.put(key.toString(), found);
                    vectors.add(vector);
                }
                schedule[unit] = found;
            }
            if (!fits) continue;
            List<BufferedImage> states = new ArrayList<>();
            for (long[][] vector : vectors) states.add(render(base, layers, vector));
            return new Plan((int) step, schedule, states, false, List.copyOf(notes));
        }
        return null;
    }

    /** {first frame, second frame, tick in segment, segment ticks} of a track at a tick of its cycle. */
    private static long[] sample(Track track, long tick) {
        long at = tick % track.period();
        int segment = 0;
        while (at >= track.ticks[segment]) at -= track.ticks[segment++];
        if (!track.interpolate) return new long[]{track.image[segment], track.image[segment], 0, 1};
        int next = (segment + 1) % track.image.length;
        return new long[]{track.image[segment], track.image[next], at, track.ticks[segment]};
    }

    private static BufferedImage render(BufferedImage base, List<Layer> layers, long[][] vector) {
        BufferedImage image = new BufferedImage(base.getWidth(), base.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.Src);
            graphics.drawImage(base, 0, 0, null);
            for (int i = 0; i < layers.size(); i++) {
                Layer layer = layers.get(i);
                long[] sample = vector[i];
                BufferedImage frame = layer.track.images.get((int) sample[0]);
                if (sample[2] != 0 && sample[0] != sample[1]) {
                    frame = blend(frame, layer.track.images.get((int) sample[1]), (double) sample[2] / sample[3]);
                }
                graphics.drawImage(frame, layer.x, layer.y, null);
            }
        } finally { graphics.dispose(); }
        return image;
    }

    /** Java's interpolation: every channel moves from the frame to the next one by the share of its time elapsed. */
    private static BufferedImage blend(BufferedImage from, BufferedImage to, double share) {
        BufferedImage result = new BufferedImage(from.getWidth(), from.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < from.getHeight(); y++) for (int x = 0; x < from.getWidth(); x++) {
            int a = from.getRGB(x, y), b = to.getRGB(x, y), mixed = 0;
            for (int shift = 0; shift <= 24; shift += 8) {
                int first = a >>> shift & 255, second = b >>> shift & 255;
                mixed |= (int) (first + (second - first) * share) << shift;
            }
            result.setRGB(x, y, mixed);
        }
        return result;
    }

    private static long gcd(long a, long b) {
        while (b != 0) { long t = a % b; a = b; b = t; }
        return a;
    }

    /** Stacks the states of a flipbook (square frames) from top to bottom. */
    static BufferedImage strip(List<BufferedImage> states) {
        int width = states.getFirst().getWidth(), height = states.getFirst().getHeight();
        BufferedImage image = new BufferedImage(width, height * states.size(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.Src);
            for (int i = 0; i < states.size(); i++) graphics.drawImage(states.get(i), 0, i * height, null);
        } finally { graphics.dispose(); }
        return image;
    }
}
