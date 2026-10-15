/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.text;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Java font metrics and their Bedrock representation, generated with the pack.
 *
 * <p>An entry is either a visible bitmap glyph, drawn on Bedrock by {@code bedrock},
 * or an advance-only character such as a space provider, a negative-height bitmap
 * or an empty TrueType glyph. Bedrock draws a glyph's first inked column one unit
 * after the pen and advances by its inked width plus one; Java draws column zero at
 * the pen. {@link TextLayout} uses these numbers to reproduce Java positions with
 * invisible spacer glyphs.
 */
public final class TextLayoutTable {
    /** Pack path of the serialized table. */
    public static final String PATH = "twilight/text-layout.json";
    /** With Twilight's chest UI, the Bedrock title label starts this many GUI units left of Java's title origin. */
    public static final int ORIGIN = 256;
    public static final String DEFAULT_FONT = "minecraft:default";

    /**
     * @param bedrock Bedrock code point, or -1 for an advance-only character
     * @param wide variant with one invisible trailing column (advance + 1), or -1
     * @param left first inked column in the Bedrock cell (Java draws it at pen + left)
     * @param width inked width on Bedrock
     * @param advance Java advance in GUI units
     */
    public record Entry(int bedrock, int wide, int left, int width, float advance) {
        public static Entry advanceOnly(float advance) { return new Entry(-1, -1, 0, 0, advance); }

        public boolean visible() { return bedrock >= 0; }
    }

    private final Map<String, Map<Integer, Entry>> fonts;
    private final int spacerFirst;
    private final int spacerCount;
    private final int containerOrigin;
    private final Map<Integer, Float> textAdvances;
    private final Map<Integer, Integer> shades;
    private final java.util.Set<String> layerSurfaces;
    private Map<String, Integer> nativeFonts = Map.of();
    private java.util.Set<String> hiddenBossBars = java.util.Set.of();
    private java.util.Set<String> styledBossBars = java.util.Set.of();
    private boolean pocketJavaLayout;
    private boolean plainTitles;
    /**
     * UTF-8 size of the plain title block: with {@link #plainTitles} the chest UI shows the first this many bytes of
     * a title in a label at Java's title position and the rest in the labels {@link #ORIGIN} units left of it. A
     * title without custom characters is sent as it is (and shows in the first label, where Bedrock's own text
     * needs no spacers); a laid-out title is sent after this many zero-width bytes.
     */
    public static final int PLAIN_TITLE_BYTES = 192;

    public TextLayoutTable(Map<String, Map<Integer, Entry>> fonts, int spacerFirst, int spacerCount) {
        this(fonts, spacerFirst, spacerCount, ORIGIN);
    }

    public TextLayoutTable(Map<String, Map<Integer, Entry>> fonts, int spacerFirst, int spacerCount,
                           int containerOrigin) {
        this(fonts, spacerFirst, spacerCount, containerOrigin, Map.of());
    }

    /**
     * @param containerOrigin {@link #ORIGIN} when the pack moves the chest title label, otherwise 0
     * @param textAdvances Java advances of ordinary default-font characters (Java's own font sheets),
     *                     used only to centre lines with Java's rounding; empty when unknown
     */
    public TextLayoutTable(Map<String, Map<Integer, Entry>> fonts, int spacerFirst, int spacerCount,
                           int containerOrigin, Map<Integer, Float> textAdvances) {
        this(fonts, spacerFirst, spacerCount, containerOrigin, textAdvances, Map.of());
    }

    /**
     * @param shades Bedrock glyph -> copy darkened like Java's uncoloured container title (Java multiplies
     *               glyph images by the text colour, Bedrock never tints private-use glyphs)
     */
    public TextLayoutTable(Map<String, Map<Integer, Entry>> fonts, int spacerFirst, int spacerCount,
                           int containerOrigin, Map<Integer, Float> textAdvances, Map<Integer, Integer> shades) {
        this(fonts, spacerFirst, spacerCount, containerOrigin, textAdvances, shades, java.util.Set.of());
    }

    /**
     * @param layerSurfaces surfaces whose generated UI shows {@link LayerEncoding} layers
     *                      ({@link #CHEST_LAYERS}, {@link #ACTIONBAR_LAYERS}, {@link #BOSS_LAYERS})
     */
    public TextLayoutTable(Map<String, Map<Integer, Entry>> fonts, int spacerFirst, int spacerCount,
                           int containerOrigin, Map<Integer, Float> textAdvances, Map<Integer, Integer> shades,
                           java.util.Set<String> layerSurfaces) {
        if (containerOrigin != 0 && containerOrigin != ORIGIN) throw new IllegalArgumentException("origin " + containerOrigin);
        Map<String, Map<Integer, Entry>> copy = new TreeMap<>();
        fonts.forEach((font, entries) -> copy.put(font, Collections.unmodifiableMap(new TreeMap<>(entries))));
        this.fonts = Collections.unmodifiableMap(copy);
        this.spacerFirst = spacerFirst;
        this.spacerCount = spacerCount;
        this.containerOrigin = containerOrigin;
        this.textAdvances = Collections.unmodifiableMap(new TreeMap<>(textAdvances));
        this.shades = Collections.unmodifiableMap(new TreeMap<>(shades));
        this.layerSurfaces = java.util.Set.copyOf(layerSurfaces);
    }

    /** Same metrics for a pack whose chest title label does (or does not) start left of Java's origin. */
    public TextLayoutTable withContainerOrigin(int origin) {
        return new TextLayoutTable(fonts, spacerFirst, spacerCount, origin, textAdvances, shades, layerSurfaces)
                .withNativeFonts(nativeFonts).withHiddenBossBars(hiddenBossBars)
                .withStyledBossBars(styledBossBars).withPocketJavaLayout(pocketJavaLayout).withPlainTitles(plainTitles);
    }

    /** Same metrics for a pack whose chest UI shows titles without custom characters in a label of their own. */
    public TextLayoutTable withPlainTitles(boolean value) {
        TextLayoutTable copy = withNativeFonts(nativeFonts);
        copy.plainTitles = value;
        return copy;
    }

    /** Whether plain chest titles are sent unchanged (see {@link #PLAIN_TITLE_BYTES}). */
    public boolean plainTitles() { return plainTitles && containerOrigin > 0; }

    /**
     * Same metrics for a pack whose chest screens use the Java layout on Bedrock's pocket UI profile too
     * (phones), so titles there are laid out like on desktop instead of only substituting glyphs.
     */
    public TextLayoutTable withPocketJavaLayout(boolean value) {
        TextLayoutTable copy = withNativeFonts(nativeFonts);
        copy.pocketJavaLayout = value;
        return copy;
    }

    /** Whether pocket (phone) chest screens use the Java layout of desktop screens. */
    public boolean pocketJavaLayout() { return pocketJavaLayout; }

    /** Same metrics for a pack whose UI shows layered text on these surfaces. */
    public TextLayoutTable withLayers(java.util.Set<String> surfaces) {
        return new TextLayoutTable(fonts, spacerFirst, spacerCount, containerOrigin, textAdvances, shades, surfaces)
                .withNativeFonts(nativeFonts).withHiddenBossBars(hiddenBossBars)
                .withStyledBossBars(styledBossBars).withPocketJavaLayout(pocketJavaLayout).withPlainTitles(plainTitles);
    }

    /**
     * Same metrics plus named fonts that draw Java's own font sheets at their vanilla size (for example
     * CustomNameplates' vertically shifted text): their characters keep the default advances.
     *
     * @param fonts font -> vertical shift in units (down positive)
     */
    public TextLayoutTable withNativeFonts(Map<String, Integer> fonts) {
        TextLayoutTable copy = new TextLayoutTable(this.fonts, spacerFirst, spacerCount, containerOrigin, textAdvances,
                shades, layerSurfaces);
        copy.nativeFonts = Collections.unmodifiableMap(new TreeMap<>(fonts));
        copy.hiddenBossBars = hiddenBossBars;
        copy.styledBossBars = styledBossBars;
        copy.pocketJavaLayout = pocketJavaLayout;
        copy.plainTitles = plainTitles;
        return copy;
    }

    /** Same metrics plus the boss bar colours the Java packs draw transparent (lower-case Java names). */
    public TextLayoutTable withHiddenBossBars(java.util.Set<String> colours) {
        TextLayoutTable copy = withNativeFonts(nativeFonts);
        copy.hiddenBossBars = java.util.Set.copyOf(colours);
        return copy;
    }

    /** Same metrics plus the boss bar colours the generated HUD draws with the packs' sprites. */
    public TextLayoutTable withStyledBossBars(java.util.Set<String> colours) {
        TextLayoutTable copy = withNativeFonts(nativeFonts);
        copy.styledBossBars = java.util.Set.copyOf(colours);
        return copy;
    }

    /** Whether the generated HUD draws boss bars of this colour (lower-case Java name) with the packs' sprites. */
    public boolean bossBarStyled(String colour) {
        return styledBossBars.contains(colour);
    }

    /** Whether Java draws boss bars of this colour (lower-case Java name) invisibly. */
    public boolean bossBarHidden(String colour) {
        return hiddenBossBars.contains(colour);
    }

    public Map<String, Integer> nativeFonts() { return nativeFonts; }

    /** Java advance of an ordinary character in a font, or NaN when unknown. */
    public float textAdvance(String font, int codePoint) {
        boolean ordinary = font == null || DEFAULT_FONT.equals(font) || nativeFonts.containsKey(font);
        return ordinary ? textAdvance(codePoint) : Float.NaN;
    }

    /** Chest titles of the generated chest UI. */
    public static final String CHEST_LAYERS = "chest";
    /** The action bar of the generated HUD. */
    public static final String ACTIONBAR_LAYERS = "actionbar";
    /** Boss bar names of the generated HUD. */
    public static final String BOSS_LAYERS = "boss";
    /** Version of the layered UI contract; 2 adds {@link LayerEncoding#LAYERED} boss names. */
    public static final int LAYER_FORMAT = 2;

    /** Whether the pack's UI shows layered text on a surface. */
    public boolean layers(String surface) {
        return layerSurfaces.contains(surface);
    }

    /** The darkened copy of a Bedrock glyph for uncoloured container titles, or -1. */
    public int shade(int bedrock) {
        Integer copy = shades.get(bedrock);
        return copy == null ? -1 : copy;
    }

    public Map<Integer, Integer> shades() { return shades; }

    /** Java advance of an ordinary default-font character, or NaN when unknown. */
    public float textAdvance(int codePoint) {
        if (codePoint == ' ') return 4;
        Float advance = textAdvances.get(codePoint);
        return advance == null ? Float.NaN : advance;
    }

    /** Units the Bedrock chest title label starts left of Java's title origin. */
    public int containerOrigin() { return containerOrigin; }

    public Entry lookup(String font, int codePoint) {
        Map<Integer, Entry> entries = fonts.get(font == null ? DEFAULT_FONT : font);
        Entry entry = entries == null ? null : entries.get(codePoint);
        // A pack's own glyph wins; otherwise the code point is an offset tag's move.
        if (entry == null && OffsetTags.isMove(codePoint)) return Entry.advanceOnly(OffsetTags.move(codePoint));
        return entry;
    }

    public boolean hasFont(String font) { return fonts.containsKey(font); }
    public Map<String, Map<Integer, Entry>> fonts() { return fonts; }
    /** Smallest spacer advance; a spacer with inked width w advances w + 1. */
    public int minimumSpacer() { return 2; }
    public int maximumSpacer() { return spacerCount + 1; }

    /** Code point of an invisible spacer with exactly this Bedrock advance. */
    public int spacer(int advance) {
        if (advance < minimumSpacer() || advance > maximumSpacer()) throw new IllegalArgumentException("spacer " + advance);
        return spacerFirst + advance - minimumSpacer();
    }

    /** Whether a code point is one of the invisible spacers. */
    public boolean isSpacer(int codePoint) {
        return codePoint >= spacerFirst && codePoint < spacerFirst + spacerCount;
    }

    public int entryCount() { return fonts.values().stream().mapToInt(Map::size).sum(); }

    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.addProperty("origin", containerOrigin);
        root.addProperty("spacer_first", spacerFirst);
        root.addProperty("spacer_count", spacerCount);
        JsonObject fontsJson = new JsonObject();
        fonts.forEach((font, entries) -> {
            JsonObject entriesJson = new JsonObject();
            entries.forEach((codePoint, entry) -> {
                JsonArray value = new JsonArray();
                if (entry.visible()) {
                    value.add(entry.bedrock());
                    value.add(entry.wide());
                    value.add(entry.left());
                    value.add(entry.width());
                }
                value.add(entry.advance());
                entriesJson.add(Integer.toHexString(codePoint).toUpperCase(java.util.Locale.ROOT), value);
            });
            fontsJson.add(font, entriesJson);
        });
        root.add("fonts", fontsJson);
        if (!textAdvances.isEmpty()) {
            JsonObject text = new JsonObject();
            textAdvances.forEach((codePoint, advance) ->
                    text.addProperty(Integer.toHexString(codePoint).toUpperCase(java.util.Locale.ROOT), advance));
            root.add("text_advances", text);
        }
        if (!hiddenBossBars.isEmpty()) {
            JsonArray hidden = new JsonArray();
            new java.util.TreeSet<>(hiddenBossBars).forEach(hidden::add);
            root.add("hidden_boss_bars", hidden);
        }
        if (!styledBossBars.isEmpty()) {
            JsonArray styled = new JsonArray();
            new java.util.TreeSet<>(styledBossBars).forEach(styled::add);
            root.add("styled_boss_bars", styled);
        }
        if (!nativeFonts.isEmpty()) {
            JsonObject native_ = new JsonObject();
            nativeFonts.forEach(native_::addProperty);
            root.add("native_fonts", native_);
        }
        if (!layerSurfaces.isEmpty()) {
            // Block size per surface: the UI cuts at these sizes, so the runtime must encode with the same.
            JsonObject layers = new JsonObject();
            new java.util.TreeSet<>(layerSurfaces).forEach(surface -> layers.addProperty(surface,
                    LayerEncoding.BLOCK_BYTES.get(surface)));
            root.add("layers", layers);
            root.addProperty("layer_format", LAYER_FORMAT);
        }
        if (pocketJavaLayout) root.addProperty("pocket_layout", "java");
        if (plainTitles) root.addProperty("plain_titles", PLAIN_TITLE_BYTES);
        if (!shades.isEmpty()) {
            JsonObject shaded = new JsonObject();
            shades.forEach((glyph, copy) ->
                    shaded.addProperty(Integer.toHexString(glyph).toUpperCase(java.util.Locale.ROOT), copy));
            root.add("shades", shaded);
        }
        return root;
    }

    public static TextLayoutTable fromJson(JsonObject root) {
        int origin = root.get("origin").getAsInt();
        if (root.get("version").getAsInt() != 1 || (origin != ORIGIN && origin != 0)) {
            throw new IllegalArgumentException("Unsupported text layout table");
        }
        Map<String, Map<Integer, Entry>> fonts = new LinkedHashMap<>();
        for (var font : root.getAsJsonObject("fonts").entrySet()) {
            Map<Integer, Entry> entries = new LinkedHashMap<>();
            for (var entry : font.getValue().getAsJsonObject().entrySet()) {
                JsonArray value = entry.getValue().getAsJsonArray();
                int codePoint = Integer.parseInt(entry.getKey(), 16);
                if (value.size() == 5) {
                    entries.put(codePoint, new Entry(value.get(0).getAsInt(), value.get(1).getAsInt(),
                            value.get(2).getAsInt(), value.get(3).getAsInt(), value.get(4).getAsFloat()));
                } else {
                    entries.put(codePoint, Entry.advanceOnly(value.get(0).getAsFloat()));
                }
            }
            fonts.put(font.getKey(), entries);
        }
        Map<Integer, Float> text = new LinkedHashMap<>();
        if (root.has("text_advances")) {
            for (var entry : root.getAsJsonObject("text_advances").entrySet()) {
                text.put(Integer.parseInt(entry.getKey(), 16), entry.getValue().getAsFloat());
            }
        }
        Map<Integer, Integer> shades = new LinkedHashMap<>();
        if (root.has("shades")) {
            for (var entry : root.getAsJsonObject("shades").entrySet()) {
                shades.put(Integer.parseInt(entry.getKey(), 16), entry.getValue().getAsInt());
            }
        }
        java.util.Set<String> layerSurfaces = new java.util.HashSet<>();
        // A surface keeps layers only when the pack's UI cuts at the block size this runtime encodes.
        if (root.has("layers") && root.get("layers").isJsonObject()) {
            for (var entry : root.getAsJsonObject("layers").entrySet()) {
                Integer block = LayerEncoding.BLOCK_BYTES.get(entry.getKey());
                if (block != null && entry.getValue().getAsInt() == block) layerSurfaces.add(entry.getKey());
            }
            // Boss names carry the layered marker since format 2; an older pack's HUD would cut them.
            int format = root.has("layer_format") ? root.get("layer_format").getAsInt() : 1;
            if (format < LAYER_FORMAT) layerSurfaces.remove(BOSS_LAYERS);
        }
        Map<String, Integer> nativeFonts = new LinkedHashMap<>();
        if (root.has("native_fonts")) {
            for (var entry : root.getAsJsonObject("native_fonts").entrySet()) nativeFonts.put(entry.getKey(), entry.getValue().getAsInt());
        }
        return new TextLayoutTable(fonts, root.get("spacer_first").getAsInt(), root.get("spacer_count").getAsInt(),
                origin, text, shades, layerSurfaces).withNativeFonts(nativeFonts)
                .withHiddenBossBars(colours(root, "hidden_boss_bars")).withStyledBossBars(colours(root, "styled_boss_bars"))
                .withPocketJavaLayout(root.has("pocket_layout") && "java".equals(root.get("pocket_layout").getAsString()))
                .withPlainTitles(root.has("plain_titles") && root.get("plain_titles").getAsInt() == PLAIN_TITLE_BYTES);
    }

    private static java.util.Set<String> colours(JsonObject root, String key) {
        java.util.Set<String> colours = new java.util.HashSet<>();
        if (root.has(key)) root.getAsJsonArray(key).forEach(colour -> colours.add(colour.getAsString()));
        return colours;
    }

}
