/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.compiler;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.siberanka.twilight.source.ResourceIndex;
import com.siberanka.twilight.text.TextLayoutTable;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Converts Java bitmap-font graphs into Bedrock BMP glyph pages without contextual collisions. */
final class BitmapFontCompiler {
    private static final int MIN_CELL_SIZE = 16;
    private static final int MAX_CELL_SIZE = 512;
    // Bedrock places the texture cell at 4-cellSize/2 relative to ordinary text.
    // Java bitmap top is 7-ascent, hence textureY = cellSize/2+3-ascent.
    // Texture pixels remain GUI units even on larger pages: never scale a glyph
    // to the atlas resolution or fit an oversized image into a smaller cell.
    /** Spacer glyphs ink 1..SPACER_COUNT columns with alpha 1, advancing 2..SPACER_COUNT+1 on Bedrock. */
    static final int SPACER_COUNT = 32;
    private static final int SPACER_CELL = 32;
    private static final int FIRST_ALIAS_PAGE = 0xE2; // E0/E1 hold Bedrock's input icons
    private static final int LAST_ALIAS_PAGE = 0xF8;
    /** Wide variants (one invisible trailing column) are generated for glyphs up to this cell size. */
    private static final int MAX_WIDE_CELL = 64;
    /**
     * Java multiplies bitmap glyphs by the text colour and draws a container title without one at
     * 0x404040, darkening its images. Bedrock never tints private-use glyphs, so such titles use
     * copies pre-multiplied by this channel value.
     */
    static final int TITLE_SHADE = 0x40;
    /** Oversized images whose most opaque pixel stays at or below this alpha are spacing, not art. */
    private static final int FAINT_ALPHA = 25;
    /** A bitmap drawn this far above or below the line is the off-screen spacing idiom, not an image. */
    private static final int OFF_SCREEN = 2048;
    /** Java's own font sheets: characters drawn from them are text, which Bedrock renders natively. */
    private static final java.util.regex.Pattern VANILLA_FONT_SHEET = java.util.regex.Pattern.compile(
            "minecraft:font/(ascii|accented|nonlatin_european|unicode_page_[0-9a-f]{2})\\.png");
    /** Marks a character that Java renders as ordinary text from its own font sheets. */
    private static final Object NATIVE_TEXT = new Object();
    private final ResourceIndex resources;
    private final VanillaAssetCache vanillaAssets;
    private final boolean vanillaOverride;
    private final boolean textLayout;
    private final boolean shadedCopies;
    private final Map<String, ProblemBucket> problemGroups = new LinkedHashMap<>();
    // Content Java itself rejects: Bedrock already matches Java, so it is reported without failing a build.
    private final Map<String, ProblemBucket> noticeGroups = new LinkedHashMap<>();
    private final Map<Integer, Glyph> glyphs = new LinkedHashMap<>();
    private final Map<String, Map<Integer, Object>> layoutSources = new TreeMap<>();
    private final List<AliasRequest> aliasRequests = new ArrayList<>();
    private final Set<String> nonBmpGlyphs = new HashSet<>();
    private final Set<String> unsupportedBaselineProviders = new HashSet<>();
    private final Set<String> vanillaFallbackTextures = new HashSet<>();
    private int namedFonts;
    /** Named fonts drawn from Java's own sheets at their vanilla size: vertical shift (units down), or null if mixed. */
    private final Map<String, Integer> nativeFonts = new HashMap<>();
    private final Set<String> mixedNativeFonts = new HashSet<>();
    private int namedGlyphs;

    BitmapFontCompiler(ResourceIndex resources, VanillaAssetCache vanillaAssets) {
        this(resources, vanillaAssets, false);
    }

    BitmapFontCompiler(ResourceIndex resources, VanillaAssetCache vanillaAssets, boolean vanillaOverride) {
        this(resources, vanillaAssets, vanillaOverride, false);
    }

    BitmapFontCompiler(ResourceIndex resources, VanillaAssetCache vanillaAssets, boolean vanillaOverride,
                       boolean textLayout) {
        this(resources, vanillaAssets, vanillaOverride, textLayout, false);
    }

    /** The largest glyph cell; a page is 16 cells wide (512: 8192x8192 pixels, 256 MiB in the client). */
    private int maxCell = MAX_CELL_SIZE;

    /**
     * Caps glyph cells (and so glyph pages) at {@code size} pixels. A glyph that needs a larger cell only because
     * Java draws it far above or below the text line is moved vertically to fit; one wider than the cell is left
     * out as before.
     */
    BitmapFontCompiler maxCell(int size) {
        if (size != 64 && size != 128 && size != 256 && size != MAX_CELL_SIZE) {
            throw new IllegalArgumentException("ui.max-glyph-cell must be 64, 128, 256 or 512");
        }
        this.maxCell = size;
        return this;
    }

    /** @param shadedCopies give laid-out glyphs copies darkened like Java's uncoloured container titles */
    BitmapFontCompiler(ResourceIndex resources, VanillaAssetCache vanillaAssets, boolean vanillaOverride,
                       boolean textLayout, boolean shadedCopies) {
        this.shadedCopies = shadedCopies && textLayout;
        this.resources = resources;
        this.vanillaAssets = vanillaAssets;
        this.vanillaOverride = vanillaOverride;
        this.textLayout = textLayout;
    }

    Result compile(Map<String, byte[]> packFiles) throws IOException {
        List<String> definitions = resources.paths().stream().map(BitmapFontCompiler::fontIdentifier)
                .filter(java.util.Objects::nonNull).sorted().toList();
        if (definitions.isEmpty()) return new Result(0, 0, 0, 0, 0, 0, null, List.of(), List.of());

        Set<String> defaultClosure = new HashSet<>();
        if (definitions.contains("minecraft:default")) {
            Map<Integer, Object> defaultGlyphs = new LinkedHashMap<>();
            readFont("minecraft:default", new HashSet<>(), defaultClosure, defaultGlyphs);
            mergeDefaultFont(defaultGlyphs);
        }
        List<String> namedDefinitions = definitions.stream()
                .filter(identifier -> !identifier.equals("minecraft:default"))
                .filter(identifier -> !defaultClosure.contains(identifier)).toList();
        namedFonts = namedDefinitions.size();
        for (String identifier : namedDefinitions) {
            Map<Integer, Object> contextual = new LinkedHashMap<>();
            readFont(identifier, new HashSet<>(), new HashSet<>(), contextual);
            mergeNamedFont(identifier, contextual);
        }

        boolean layoutActive = textLayout && !layoutSources.isEmpty();
        int spacerPage = layoutActive ? freePage(Set.of()) : -1;
        Map<Integer, Integer> aliases = layoutActive ? allocateAliases(spacerPage) : Map.of();
        Map<Integer, Integer> wide = layoutActive ? allocateWideVariants(spacerPage) : Map.of();
        Map<Integer, Integer> shades = layoutActive && shadedCopies ? allocateShades(spacerPage, wide) : Map.of();

        // 256 code points per page, pages and glyphs in code point order.
        Map<Integer, List<Glyph>> pages = new TreeMap<>();
        new TreeMap<>(glyphs).forEach((codePoint, glyph) ->
                pages.computeIfAbsent(codePoint >>> 8, page -> new ArrayList<>()).add(glyph));
        Map<Integer, int[]> ink = new HashMap<>();
        for (Map.Entry<Integer, List<Glyph>> page : pages.entrySet()) {
            BufferedImage image = compose(page.getValue());
            if (image.getWidth() >= 8192) {
                notice("glyph pages of 8192x8192 pixels take 256 MiB each in the client and load slowly on phones;"
                        + " ui.max-glyph-cell: 256 makes them 4096x4096 but moves images drawn far from the line",
                        "glyph_%02X".formatted(page.getKey()));
            }
            packFiles.put("font/glyph_%02X.png".formatted(page.getKey()), TextureSet.png(image));
            if (layoutActive) measureInk(image, page.getValue(), ink);
        }
        TextLayoutTable table = null;
        if (layoutActive) {
            packFiles.put("font/glyph_%02X.png".formatted(spacerPage), TextureSet.png(spacerPage()));
            table = layoutTable(wide, shades, ink, spacerPage << 8, vanillaTextAdvances());
            packFiles.put(TextLayoutTable.PATH, table.toJson().toString().getBytes(StandardCharsets.UTF_8));
        }
        List<String> problems = new ArrayList<>(problemGroups.entrySet().stream()
                .map(entry -> entry.getValue().format(entry.getKey())).toList());
        if (!nonBmpGlyphs.isEmpty()) problems.add(nonBmpGlyphs.size() +
                " supplementary-plane glyph(s) cannot be represented by Bedrock BMP pages");
        if (!unsupportedBaselineProviders.isEmpty()) problems.add(unsupportedBaselineProviders.size() +
                " invalid Java bitmap height/ascent provider(s) were skipped");
        int direct = glyphs.size() - aliases.size() - wide.size() - shades.size();
        List<String> notices = noticeGroups.entrySet().stream()
                .map(entry -> entry.getValue().format(entry.getKey())).toList();
        return new Result(direct, pages.size(), vanillaFallbackTextures.size(), namedFonts, namedGlyphs,
                aliases.size(), table, List.copyOf(problems), notices);
    }

    private void readFont(String identifier, Set<String> active, Set<String> closure,
                          Map<Integer, Object> target) {
        String normalized = identifier(identifier, "minecraft");
        if (!active.add(normalized)) return;
        closure.add(normalized);
        try {
            // Java combines a font's definitions from every resource pack: higher-priority packs'
            // providers come first. findAll lists the effective pack first, so apply it last.
            List<ResourceIndex.Asset> definitions = resources.findAll(fontPath(normalized));
            if (definitions.isEmpty()) {
                if (!normalized.startsWith("minecraft:")) problem("missing referenced fonts", normalized);
                return;
            }
            for (int layer = definitions.size() - 1; layer >= 0; layer--) {
                ResourceIndex.Asset definition = definitions.get(layer);
                try { readProviders(normalized, definition, active, closure, target); }
                catch (com.google.gson.JsonParseException failure) {
                    // Java fails on the same file and skips this pack's definition, as done here.
                    notice("font definitions are malformed and ignored, as on Java", normalized + " -> "
                            + definition.source().provider() + " (" + definition.source().path().getFileName() + "): "
                            + firstLine(message(failure)));
                }
                catch (IOException | RuntimeException failure) {
                    problem("font definitions could not be parsed", normalized + " -> "
                            + definition.source().provider() + ": " + message(failure));
                }
            }
        } catch (RuntimeException failure) {
            problem("font definitions could not be parsed", normalized + " -> " + message(failure));
        } finally {
            active.remove(normalized);
        }
    }

    private void readProviders(String normalized, ResourceIndex.Asset definition, Set<String> active,
                               Set<String> closure, Map<Integer, Object> target) throws IOException {
        JsonElement parsed = JsonParser.parseString(definition.readUtf8());
        if (!parsed.isJsonObject()) {
            problem("font roots are not objects", normalized);
            return;
        }
        JsonElement providersElement = parsed.getAsJsonObject().get("providers");
        if (providersElement == null || !providersElement.isJsonArray()) return;
        var providers = providersElement.getAsJsonArray();
        // Java uses the first provider that defines a character: read in reverse and overwrite.
        List<JsonElement> lastFirst = new ArrayList<>(providers.size());
        providers.forEach(lastFirst::add);
        java.util.Collections.reverse(lastFirst);
        for (JsonElement element : lastFirst) {
            if (!element.isJsonObject()) {
                problem("font definitions contain non-object providers", normalized);
                continue;
            }
            JsonObject provider = element.getAsJsonObject();
            String type = string(provider, "type");
            type = type.substring(type.indexOf(':') + 1);
            if (type.equals("bitmap")) {
                try { readBitmap(normalized, provider, target); }
                catch (IOException failure) {
                    problem("bitmap providers could not be decoded", normalized + '|' + provider,
                            normalized + " -> " + message(failure));
                }
            }
            else if (type.equals("reference")) {
                String reference = string(provider, "id");
                if (!reference.isBlank()) readFont(reference, active, closure, target);
            }
            else if (type.equals("space") && provider.has("advances")) {
                readSpaces(normalized, provider, target);
            }
            else if (type.equals("ttf") && textLayout) {
                readTrueType(normalized, provider, target);
            }
            // Built-in providers retain Bedrock's native metrics.
        }
    }

    private void readSpaces(String font, JsonObject provider, Map<Integer, Object> target) {
        for (var advance : provider.getAsJsonObject("advances").entrySet()) {
            String key = advance.getKey();
            if (key.codePointCount(0, key.length()) != 1) continue;
            float value = advance.getValue().getAsFloat();
            if (textLayout) {
                target.put(key.codePointAt(0), new Advance(value));
            } else if (!(key.equals(" ") || key.equals(" ")) || value != 4.0f) {
                problem("custom font advances require a Bedrock layout adapter", font + '|' + provider, font);
                return;
            }
        }
    }

    private void readTrueType(String font, JsonObject provider, Map<Integer, Object> target) {
        String file = string(provider, "file");
        String[] split = identifier(file, "minecraft").split(":", 2);
        try {
            var asset = resources.find("assets/" + split[0] + "/font/" + split[1]);
            if (asset.isEmpty()) {
                problem("TrueType font files are missing", font + " -> " + file);
                return;
            }
            float size = provider.has("size") ? provider.get("size").getAsFloat() : 11f;
            Set<Integer> skipped = new HashSet<>();
            JsonElement skip = provider.get("skip");
            if (skip != null && skip.isJsonPrimitive()) skip.getAsString().codePoints().forEach(skipped::add);
            if (skip != null && skip.isJsonArray()) skip.getAsJsonArray().forEach(row -> row.getAsString().codePoints().forEach(skipped::add));
            for (var metric : TrueTypeAdvances.read(asset.get().readBytes(), size).entrySet()) {
                if (skipped.contains(metric.getKey())) continue;
                // Outlined TrueType text keeps Bedrock's native font; empty glyphs are spacing only.
                if (metric.getValue().visible()) target.remove(metric.getKey());
                else target.put(metric.getKey(), new Advance(metric.getValue().advance()));
            }
        } catch (IOException failure) {
            // Java (FreeType) refuses the same files, so its players see what Bedrock gets.
            notice("TrueType fonts are unreadable and ignored, as on Java", font + " -> " + file + ": " + message(failure));
        }
    }

    private void readBitmap(String font, JsonObject provider, Map<Integer, Object> target) throws IOException {
        String providerKey = font + '|' + provider;
        String file = string(provider, "file");
        JsonElement chars = provider.get("chars");
        if (file.isBlank() || chars == null || !chars.isJsonArray()) {
            problem("bitmap providers have invalid file/chars fields", providerKey, font);
            return;
        }
        String textureIdentifier = identifier(file, "minecraft");
        if (!vanillaOverride && VANILLA_FONT_SHEET.matcher(textureIdentifier).matches()) {
            // Fonts such as CustomNameplates' shifted text reuse Java's font sheets. That is text, not
            // an image: Bedrock keeps its own (tinted) glyph, so the character is laid out as ordinary text.
            for (JsonElement row : chars.getAsJsonArray()) {
                row.getAsString().codePoints().filter(codePoint -> codePoint != 0)
                        .forEach(codePoint -> target.put(codePoint, NATIVE_TEXT));
            }
            // At the sheet's vanilla height the characters keep Java's ordinary advances (only shifted vertically).
            boolean accented = textureIdentifier.endsWith("/accented.png");
            int vanillaHeight = accented ? 12 : 8, vanillaAscent = accented ? 10 : 7;
            if (integer(provider, "height", 8) != vanillaHeight) mixedNativeFonts.add(font);
            else {
                int shift = vanillaAscent - integer(provider, "ascent", vanillaAscent);
                Integer previous = nativeFonts.putIfAbsent(font, shift);
                if (previous != null && previous != shift) mixedNativeFonts.add(font);
            }
            return;
        }
        String texturePath = texturePath(textureIdentifier);
        List<ResourceIndex.Asset> textureAssets = resources.findAll(texturePath);
        BufferedImage image = null;
        IOException lastFailure = null;
        for (ResourceIndex.Asset textureAsset : textureAssets) {
            try {
                image = PngImages.read(textureAsset.readBytes());
                if (image != null) break;
                lastFailure = new IOException("not a valid PNG from " + textureAsset.source().provider());
            } catch (IOException failure) {
                lastFailure = new IOException(message(failure) + " from " + textureAsset.source().provider(), failure);
            }
        }
        if (image == null && textureIdentifier.startsWith("minecraft:") && vanillaAssets != null) {
            try {
                var vanilla = vanillaAssets.readTexture(texturePath);
                if (vanilla.isPresent()) {
                    image = PngImages.read(vanilla.get());
                    if (image == null) throw new IOException("cached vanilla entry is not a PNG");
                    vanillaFallbackTextures.add(texturePath);
                }
            } catch (IOException failure) {
                problem("verified vanilla texture cache could not resolve references", providerKey,
                        textureIdentifier + " -> " + message(failure));
            }
        }
        if (image == null && textureAssets.isEmpty()) {
            problem("bitmap textures are missing", providerKey, font + " -> " + textureIdentifier);
            return;
        }
        if (image == null && lastFailure != null) {
            throw new IOException(textureIdentifier + ": " + message(lastFailure), lastFailure);
        }
        if (image == null) {
            problem("bitmap textures are not valid PNG files", providerKey, textureIdentifier);
            return;
        }
        List<String> rows = new ArrayList<>();
        for (JsonElement row : chars.getAsJsonArray()) rows.add(row.getAsString());
        if (rows.isEmpty()) return;
        int columns = gridColumns(rows);
        if (columns < 1 || rows.stream().anyMatch(row -> row.codePoints().count() != columns)) {
            problem("bitmap providers have inconsistent chars row widths", providerKey, font);
            return;
        }
        if (!fitsGrid(image, columns, rows.size())) {
            problem("bitmap textures are not divisible by their glyph grids", providerKey, textureIdentifier);
            return;
        }
        int declaredHeight = integer(provider, "height", 8);
        int declaredAscent = integer(provider, "ascent", declaredHeight);
        int cellWidth = Math.floorDiv(image.getWidth(), columns);
        int cellHeight = Math.floorDiv(image.getHeight(), rows.size());
        float scale = declaredHeight / (float) cellHeight;
        if (declaredHeight < 1 && textLayout) {
            // Java's negative-space idiom: a negative height yields a negative advance and
            // places the image far outside the line. Only its advance is converted.
            for (int row = 0; row < rows.size(); row++) {
                int[] codePoints = rows.get(row).codePoints().toArray();
                for (int column = 0; column < codePoints.length; column++) {
                    if (codePoints[column] == 0) continue;
                    int actual = actualWidth(image, column * cellWidth, row * cellHeight, cellWidth, cellHeight);
                    target.put(codePoints[column], new Advance(javaAdvance(actual, scale)));
                }
            }
            return;
        }
        if (declaredHeight < 1 || declaredAscent > declaredHeight) {
            unsupportedBaselineProviders.add(providerKey);
            return;
        }
        double displayWidth = cellWidth * (declaredHeight / (double) cellHeight);
        boolean offScreen = 7 - declaredAscent > OFF_SCREEN || 7 - declaredAscent + declaredHeight < -OFF_SCREEN;
        if (displayWidth > maxCell || offScreen) {
            // Bedrock cannot draw these. Java's transparent or off-screen ones only advance the pen.
            boolean visible = false;
            for (int row = 0; row < rows.size(); row++) {
                int[] codePoints = rows.get(row).codePoints().toArray();
                for (int column = 0; column < codePoints.length; column++) {
                    if (codePoints[column] == 0) continue;
                    int actual = actualWidth(image, column * cellWidth, row * cellHeight, cellWidth, cellHeight);
                    if (textLayout) target.put(codePoints[column], new Advance(javaAdvance(actual, scale)));
                    visible |= !offScreen && maximumAlpha(image, column * cellWidth, row * cellHeight,
                            cellWidth, cellHeight) > FAINT_ALPHA;
                }
            }
            if (!textLayout) {
                problem("oversized bitmap glyphs require a Bedrock UI adapter", providerKey,
                        font + " -> " + textureIdentifier + " (" + displayWidth + "x" + declaredHeight + ")");
            } else if (visible) {
                // Screen-sized overlays (fades, crate backdrops): the layout keeps their advance, so what
                // follows them stays at Java's position; only the overlay itself is missing on Bedrock.
                notice("glyphs larger than Bedrock's glyph cell are left out; text after them keeps Java's position",
                        font + " -> " + textureIdentifier + " (" + displayWidth + "x" + declaredHeight + ")");
            }
            return;
        }
        for (int row = 0; row < rows.size(); row++) {
            int[] codePoints = rows.get(row).codePoints().toArray();
            for (int column = 0; column < codePoints.length; column++) {
                int codePoint = codePoints[column];
                if (codePoint == 0) continue;
                int actual = actualWidth(image, column * cellWidth, row * cellHeight, cellWidth, cellHeight);
                int advance = javaAdvance(actual, scale);
                if (actual == 0 && textLayout) {
                    // Bedrock gives a fully transparent glyph a native width; Java advances by one.
                    target.put(codePoint, new Advance(advance));
                    continue;
                }
                if (!isBmp(codePoint) && !textLayout) {
                    nonBmpGlyphs.add(font + " -> U+%X".formatted(codePoint));
                    continue;
                }
                Glyph glyph = new Glyph(codePoint, image, column * cellWidth, row * cellHeight,
                        cellWidth, cellHeight, declaredHeight, declaredAscent, MIN_CELL_SIZE, advance);
                int requiredCellSize = minimumCellSize(glyph, displayWidth, maxCell);
                if (requiredCellSize == 0 && maxCell < MAX_CELL_SIZE) {
                    Glyph moved = moveIntoCell(glyph, maxCell);
                    int movedCell = moved == null ? 0 : minimumCellSize(moved, displayWidth, maxCell);
                    if (movedCell != 0) {
                        notice("glyphs drawn far above or below the line were moved vertically to fit ui.max-glyph-cell",
                                font + " -> " + textureIdentifier + " (ascent " + declaredAscent + " -> " + moved.declaredAscent() + ")");
                        target.put(codePoint, moved.withCellSize(movedCell));
                        continue;
                    }
                }
                if (requiredCellSize == 0) {
                    problem("bitmap glyph baselines require a Bedrock layout adapter", providerKey,
                            font + " -> " + textureIdentifier + " (height=" + declaredHeight
                                    + ", ascent=" + declaredAscent + ")");
                    continue;
                }
                target.put(codePoint, glyph.withCellSize(requiredCellSize));
            }
        }
    }

    private static int maximumAlpha(BufferedImage image, int x, int y, int width, int height) {
        int maximum = 0;
        for (int row = 0; row < height; row++) for (int column = 0; column < width; column++) {
            maximum = Math.max(maximum, image.getRGB(x + column, y + row) >>> 24);
        }
        return maximum;
    }

    /** Java's BitmapProvider: rightmost column containing any non-zero alpha, plus one. */
    static int actualWidth(BufferedImage image, int x, int y, int width, int height) {
        for (int column = width - 1; column >= 0; column--) {
            for (int row = 0; row < height; row++) {
                if ((image.getRGB(x + column, y + row) >>> 24) != 0) return column + 1;
            }
        }
        return 0;
    }

    /** Java's BitmapProvider advance: {@code (int) (0.5 + actualWidth * scale) + 1}. */
    static int javaAdvance(int actualWidth, float scale) {
        return (int) (0.5f + actualWidth * scale) + 1;
    }

    private void mergeNamedFont(String font, Map<Integer, Object> contextual) {
        for (Map.Entry<Integer, Object> entry : contextual.entrySet()) {
            int codePoint = entry.getKey();
            if (entry.getValue() == NATIVE_TEXT) continue;
            if (entry.getValue() instanceof Advance advance) {
                layout(font, codePoint, advance);
                continue;
            }
            Glyph candidate = (Glyph) entry.getValue();
            Glyph existing = glyphs.get(codePoint);
            if (existing != null && sameGlyph(existing, candidate)) {
                layout(font, codePoint, codePoint);
                continue;
            }
            if (!isBmpPrivateUse(codePoint) || existing != null || clustered(candidate)) {
                if (textLayout) {
                    alias(font, codePoint, candidate);
                } else if (!isBmpPrivateUse(codePoint)) {
                    problem("named font glyphs require contextual Bedrock remapping",
                            font + " -> U+%04X".formatted(codePoint));
                } else {
                    problem("named font glyphs conflict on Bedrock's global codepoint map",
                            font + " -> U+%04X".formatted(codePoint));
                }
                continue;
            }
            glyphs.put(codePoint, candidate);
            layout(font, codePoint, codePoint);
            namedGlyphs++;
        }
    }

    private void mergeDefaultFont(Map<Integer, Object> defaults) {
        String font = TextLayoutTable.DEFAULT_FONT;
        for (Map.Entry<Integer, Object> entry : defaults.entrySet()) {
            int codePoint = entry.getKey();
            if (entry.getValue() == NATIVE_TEXT) continue;
            if (entry.getValue() instanceof Advance advance) {
                layout(font, codePoint, advance);
                continue;
            }
            Glyph glyph = (Glyph) entry.getValue();
            if (((vanillaOverride && isBmp(codePoint)) || isBmpPrivateUse(codePoint)) && !clustered(glyph)) {
                glyphs.put(codePoint, glyph);
                layout(font, codePoint, codePoint);
            } else if (textLayout) {
                // Java replaces this character for every Java player; Bedrock receives a private-use alias
                // through the title layout so its own glyph for the character stays intact.
                alias(font, codePoint, glyph);
            } else {
                problem("default font glyphs require vanilla-override",
                        "minecraft:default -> U+%04X".formatted(codePoint));
            }
        }
    }

    private void layout(String font, int codePoint, Object value) {
        if (textLayout) layoutSources.computeIfAbsent(font, ignored -> new TreeMap<>()).put(codePoint, value);
    }

    /**
     * Large glyphs (menu backgrounds, screen art) go to pages of their own through the title layout. A page is
     * as large as its largest cell: one 512-pixel glyph among small ones made a whole 8192x8192 page (256 MiB in
     * the client), and a pack had seven of them. Grouped, the same glyphs fill one or two such pages, which phones
     * with little memory can still hold. Only with the text layout, which maps them back at runtime.
     */
    private boolean clustered(Glyph glyph) {
        return textLayout && glyph.cellSize() >= CLUSTERED_CELL;
    }

    /** Cell size from which glyphs are grouped on pages of their own. */
    static final int CLUSTERED_CELL = 256;

    private void alias(String font, int codePoint, Glyph glyph) {
        AliasRequest request = new AliasRequest(font, codePoint, glyph);
        aliasRequests.add(request);
        layout(font, codePoint, request);
    }

    /** Places alias glyphs on private-use pages unused by direct glyphs, grouping similar cell sizes. */
    private Map<Integer, Integer> allocateAliases(int spacerPage) {
        Map<Integer, Integer> allocated = new HashMap<>();
        if (aliasRequests.isEmpty()) return allocated;
        Map<GlyphKey, Integer> shared = new HashMap<>();
        Set<Integer> reserved = new HashSet<>(Set.of(spacerPage));
        List<AliasRequest> ordered = new ArrayList<>(aliasRequests);
        ordered.sort(Comparator.comparingInt((AliasRequest request) -> -request.glyph().cellSize())
                .thenComparing(AliasRequest::font).thenComparingInt(AliasRequest::codePoint));
        int page = -1, slot = 256, overflow = 0;
        for (AliasRequest request : ordered) {
            GlyphKey key = GlyphKey.of(request.glyph());
            Integer existing = shared.get(key);
            if (existing != null) {
                request.bedrock = existing;
                continue;
            }
            if (slot > 255) {
                page = freePage(reserved);
                slot = 0;
                if (page < 0) {
                    overflow++;
                    continue;
                }
                reserved.add(page);
            }
            if (page < 0) {
                overflow++;
                continue;
            }
            int codePoint = page << 8 | slot++;
            glyphs.put(codePoint, request.glyph().withCodePoint(codePoint));
            allocated.put(codePoint, request.codePoint());
            shared.put(key, codePoint);
            request.bedrock = codePoint;
        }
        if (overflow > 0) problem("glyph aliases exceed Bedrock's private-use pages", overflow + " glyphs");
        return allocated;
    }

    /**
     * Gives small visible glyphs a variant with one invisible trailing column, so the title
     * layout can absorb a one-unit gap (Bedrock has no one-unit spacer). Returns glyph -> variant.
     */
    private Map<Integer, Integer> allocateWideVariants(int spacerPage) {
        Map<Integer, Integer> variants = new TreeMap<>();
        Set<Integer> laidOut = new HashSet<>();
        layoutSources.values().forEach(sources -> sources.values().forEach(source -> {
            if (source instanceof Integer direct) laidOut.add(direct);
            else if (source instanceof AliasRequest request && request.bedrock >= 0) laidOut.add(request.bedrock);
        }));
        List<Integer> candidates = laidOut.stream().filter(codePoint -> glyphs.get(codePoint) != null
                && glyphs.get(codePoint).cellSize() <= MAX_WIDE_CELL).sorted(
                Comparator.comparingInt((Integer codePoint) -> -glyphs.get(codePoint).cellSize())
                        .thenComparingInt(codePoint -> codePoint)).toList();
        Set<Integer> reserved = new HashSet<>(Set.of(spacerPage));
        int page = -1, slot = 256;
        for (int codePoint : candidates) {
            if (slot > 255) {
                page = freePage(reserved);
                if (page < 0) break; // aliases keep priority; remaining one-unit gaps are approximations
                reserved.add(page);
                slot = 0;
            }
            int variant = page << 8 | slot++;
            glyphs.put(variant, glyphs.get(codePoint).withCodePoint(variant).withTrailingMarker());
            variants.put(codePoint, variant);
        }
        return variants;
    }

    /**
     * Darkened copies of every laid-out glyph and wide variant, used by container titles without a
     * colour (see {@link #TITLE_SHADE}). They take the private-use pages left after aliases and wide
     * variants, largest cells first; glyphs left over keep Bedrock's undarkened image. Returns
     * glyph -> copy.
     */
    private Map<Integer, Integer> allocateShades(int spacerPage, Map<Integer, Integer> wide) {
        Set<Integer> laidOut = new java.util.TreeSet<>();
        layoutSources.values().forEach(sources -> sources.values().forEach(source -> {
            int code = source instanceof AliasRequest request ? request.bedrock : source instanceof Integer direct ? direct : -1;
            if (code >= 0 && glyphs.containsKey(code)) laidOut.add(code);
        }));
        for (int code : List.copyOf(laidOut)) {
            Integer variant = wide.get(code);
            if (variant != null) laidOut.add(variant);
        }
        List<Integer> ordered = laidOut.stream().sorted(Comparator.comparingInt((Integer codePoint) ->
                -glyphs.get(codePoint).cellSize()).thenComparingInt(codePoint -> codePoint)).toList();
        Map<Integer, Integer> shades = new TreeMap<>();
        Map<ShadeKey, Integer> shared = new HashMap<>();
        Map<BufferedImage, BufferedImage> darkened = new java.util.IdentityHashMap<>();
        Set<Integer> reserved = new HashSet<>(Set.of(spacerPage));
        int page = -1, slot = 256, missing = 0;
        for (int codePoint : ordered) {
            Glyph glyph = glyphs.get(codePoint);
            ShadeKey key = new ShadeKey(GlyphKey.of(glyph), glyph.trailingMarker());
            Integer existing = shared.get(key);
            if (existing != null) {
                shades.put(codePoint, existing);
                continue;
            }
            if (slot > 255) {
                page = freePage(reserved);
                if (page < 0) {
                    missing = ordered.size() - shades.size();
                    break;
                }
                reserved.add(page);
                slot = 0;
            }
            int copy = page << 8 | slot++;
            glyphs.put(copy, glyph.withCodePoint(copy).withImage(darkened.computeIfAbsent(glyph.image(),
                    BitmapFontCompiler::shade)));
            shades.put(codePoint, copy);
            shared.put(key, copy);
        }
        if (missing > 0) notice("container titles without a colour keep some images undarkened on Bedrock: "
                + "the private-use glyph pages are full", missing + " glyphs");
        return shades;
    }

    private record ShadeKey(GlyphKey glyph, boolean trailingMarker) {}

    /** The texture as Java draws it with the {@link #TITLE_SHADE} colour: each channel multiplied, alpha kept. */
    static BufferedImage shade(BufferedImage source) {
        BufferedImage shaded = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int argb = source.getRGB(x, y);
                int red = ((argb >>> 16 & 0xFF) * TITLE_SHADE + 127) / 255;
                int green = ((argb >>> 8 & 0xFF) * TITLE_SHADE + 127) / 255;
                int blue = ((argb & 0xFF) * TITLE_SHADE + 127) / 255;
                shaded.setRGB(x, y, argb & 0xFF000000 | red << 16 | green << 8 | blue);
            }
        }
        return shaded;
    }

    private int freePage(Set<Integer> reserved) {
        Set<Integer> used = new HashSet<>();
        glyphs.keySet().forEach(codePoint -> used.add(codePoint >>> 8));
        for (int page = LAST_ALIAS_PAGE; page >= FIRST_ALIAS_PAGE; page--) {
            if (!used.contains(page) && !reserved.contains(page)) return page;
        }
        return -1;
    }

    /**
     * Java's advances for its own font sheets (include/default.json of the client), so centred lines
     * can follow Java's integer rounding. Empty when the vanilla client is unavailable.
     */
    private Map<Integer, Float> vanillaTextAdvances() {
        Map<Integer, Float> advances = new TreeMap<>();
        if (vanillaAssets == null) return advances;
        try {
            var definition = vanillaAssets.readFontDefinition("assets/minecraft/font/include/default.json");
            if (definition.isEmpty()) return advances;
            JsonElement providers = JsonParser.parseString(new String(definition.get(), StandardCharsets.UTF_8))
                    .getAsJsonObject().get("providers");
            if (providers == null || !providers.isJsonArray()) return advances;
            for (JsonElement element : providers.getAsJsonArray()) {
                JsonObject provider = element.getAsJsonObject();
                String file = identifier(string(provider, "file"), "minecraft");
                if (!string(provider, "type").endsWith("bitmap") || !VANILLA_FONT_SHEET.matcher(file).matches()) continue;
                var bytes = vanillaAssets.readTexture(texturePath(file));
                if (bytes.isEmpty()) continue;
                BufferedImage image = PngImages.read(bytes.get());
                List<String> rows = new ArrayList<>();
                provider.getAsJsonArray("chars").forEach(row -> rows.add(row.getAsString()));
                if (image == null || rows.isEmpty()) continue;
                int columns = gridColumns(rows);
                if (columns < 1) continue;
                int cellWidth = Math.floorDiv(image.getWidth(), columns), cellHeight = Math.floorDiv(image.getHeight(), rows.size());
                float scale = integer(provider, "height", 8) / (float) cellHeight;
                for (int row = 0; row < rows.size(); row++) {
                    int[] codePoints = rows.get(row).codePoints().toArray();
                    for (int column = 0; column < codePoints.length && column < columns; column++) {
                        if (codePoints[column] == 0 || advances.containsKey(codePoints[column])) continue;
                        int actual = actualWidth(image, column * cellWidth, row * cellHeight, cellWidth, cellHeight);
                        advances.put(codePoints[column], (float) javaAdvance(actual, scale));
                    }
                }
            }
        } catch (IOException | RuntimeException failure) {
            // Centring then keeps Bedrock's exact rounding; nothing else depends on these widths.
            advances.clear();
        }
        return advances;
    }

    /** The glyph columns of a bitmap provider: the code points of its first {@code chars} row. */
    private static int gridColumns(List<String> rows) {
        return (int) rows.getFirst().codePoints().count();
    }

    /** Whether a texture divides into equal cells for the grid. */
    private static boolean fitsGrid(BufferedImage image, int columns, int rowCount) {
        return Math.floorMod(image.getWidth(), columns) == 0 && Math.floorMod(image.getHeight(), rowCount) == 0;
    }

    private TextLayoutTable layoutTable(Map<Integer, Integer> wide, Map<Integer, Integer> shades,
                                        Map<Integer, int[]> ink, int spacerFirst, Map<Integer, Float> textAdvances) {
        Map<String, Map<Integer, TextLayoutTable.Entry>> fonts = new TreeMap<>();
        layoutSources.forEach((font, sources) -> {
            Map<Integer, TextLayoutTable.Entry> entries = new TreeMap<>();
            sources.forEach((codePoint, source) -> {
                if (source instanceof Advance advance) {
                    entries.put(codePoint, TextLayoutTable.Entry.advanceOnly(advance.value()));
                    return;
                }
                int bedrock = source instanceof AliasRequest request ? request.bedrock : (Integer) source;
                if (bedrock < 0) return;
                int[] columns = ink.get(bedrock);
                Glyph glyph = glyphs.get(bedrock);
                if (columns == null || glyph == null) return;
                Integer variant = wide.get(bedrock);
                int[] variantColumns = variant == null ? null : ink.get(variant);
                boolean usable = variantColumns != null && variantColumns[0] == columns[0]
                        && variantColumns[1] == columns[1] + 1;
                entries.put(codePoint, new TextLayoutTable.Entry(bedrock, usable ? variant : -1,
                        columns[0], columns[1], glyph.javaAdvance()));
            });
            if (!entries.isEmpty()) fonts.put(font, entries);
        });
        // Only copies whose ink matches the original: the layout positions both alike.
        Map<Integer, Integer> usableShades = new TreeMap<>();
        shades.forEach((original, copy) -> {
            int[] columns = ink.get(original), shaded = ink.get(copy);
            if (columns != null && shaded != null && columns[0] == shaded[0] && columns[1] == shaded[1]) {
                usableShades.put(original, copy);
            }
        });
        Map<String, Integer> native_ = new TreeMap<>(nativeFonts);
        native_.keySet().removeAll(mixedNativeFonts);
        native_.remove(TextLayoutTable.DEFAULT_FONT);
        return new TextLayoutTable(fonts, spacerFirst, SPACER_COUNT, TextLayoutTable.ORIGIN, textAdvances, usableShades)
                .withNativeFonts(native_);
    }

    /** First inked column and inked width of each glyph cell, as Bedrock measures them (alpha above zero). */
    private static void measureInk(BufferedImage page, List<Glyph> members, Map<Integer, int[]> ink) {
        int cellSize = page.getWidth() / 16;
        for (Glyph glyph : members) {
            int slot = glyph.codePoint() & 0xFF;
            int cellX = (slot & 15) * cellSize, cellY = (slot >>> 4) * cellSize;
            int first = -1, last = -1;
            for (int x = 0; x < cellSize; x++) {
                for (int y = 0; y < cellSize; y++) {
                    if ((page.getRGB(cellX + x, cellY + y) >>> 24) != 0) {
                        if (first < 0) first = x;
                        last = x;
                        break;
                    }
                }
            }
            if (first >= 0) ink.put(glyph.codePoint(), new int[]{first, last - first + 1});
        }
    }

    /** Invisible spacers: slot n inks columns 0..n with alpha 1, so Bedrock advances n + 2. */
    private static BufferedImage spacerPage() {
        BufferedImage page = new BufferedImage(SPACER_CELL * 16, SPACER_CELL * 16, BufferedImage.TYPE_INT_ARGB);
        for (int slot = 0; slot < SPACER_COUNT; slot++) {
            int cellX = (slot & 15) * SPACER_CELL, cellY = (slot >>> 4) * SPACER_CELL;
            for (int x = 0; x <= slot; x++) page.setRGB(cellX + x, cellY + SPACER_CELL / 2, 0x01FFFFFF);
        }
        return page;
    }

    private static boolean sameGlyph(Glyph left, Glyph right) {
        if (left.width() != right.width() || left.height() != right.height() ||
                left.declaredHeight() != right.declaredHeight() || left.declaredAscent() != right.declaredAscent()) {
            return false;
        }
        for (int y = 0; y < left.height(); y++) {
            for (int x = 0; x < left.width(); x++) {
                if (left.image().getRGB(left.x() + x, left.y() + y) !=
                        right.image().getRGB(right.x() + x, right.y() + y)) return false;
            }
        }
        return true;
    }

    private static BufferedImage compose(List<Glyph> glyphs) {
        // Enlarge the atlas, not its glyphs. Correcting the native cell origin
        // keeps existing heights/baselines stable when a wide glyph joins a page.
        int cellSize = glyphs.stream().mapToInt(Glyph::cellSize).max().orElse(MIN_CELL_SIZE);
        BufferedImage page = new BufferedImage(cellSize * 16, cellSize * 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = page.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.Src);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            for (Glyph glyph : glyphs) {
                double displayWidth = Math.max(1.0, glyph.width() * (glyph.declaredHeight() / (double) glyph.height()));
                int width = Math.max(1, (int) Math.round(displayWidth));
                int height = glyph.declaredHeight();
                int slot = glyph.codePoint() & 0xFF;
                int cellX = (slot & 15) * cellSize;
                int cellY = (slot >>> 4) * cellSize;
                int x = cellX;
                int y = cellY + cellSize / 2 + 3 - glyph.declaredAscent();
                // Transparent source padding may extend beyond the cell, but
                // must never clear or paint an adjacent code point's pixels.
                graphics.setClip(cellX, cellY, cellSize, cellSize);
                graphics.drawImage(glyph.image(), x, y, x + width, y + height,
                        glyph.x(), glyph.y(), glyph.x() + glyph.width(), glyph.y() + glyph.height(), null);
                if (glyph.trailingMarker()) markTrailingColumn(page, cellX, cellY, cellSize);
            }
        } finally {
            graphics.dispose();
        }
        return page;
    }

    /** Alpha-1 pixel right of the last inked column: invisible, but Bedrock counts it as ink. */
    private static void markTrailingColumn(BufferedImage page, int cellX, int cellY, int cellSize) {
        int last = -1;
        for (int x = cellSize - 1; x >= 0 && last < 0; x--) {
            for (int y = 0; y < cellSize; y++) {
                if ((page.getRGB(cellX + x, cellY + y) >>> 24) != 0) { last = x; break; }
            }
        }
        if (last >= 0 && last + 1 < cellSize) page.setRGB(cellX + last + 1, cellY + cellSize / 2, 0x01FFFFFF);
    }

    /** The glyph with the ascent closest to its own that fits a {@code cell}-pixel cell, or null. */
    private static Glyph moveIntoCell(Glyph glyph, int cell) {
        int highest = cell / 2 + 3;
        int lowest = highest - (cell - glyph.declaredHeight());
        if (lowest > highest) return null;
        int ascent = Math.max(lowest, Math.min(highest, glyph.declaredAscent()));
        return new Glyph(glyph.codePoint(), glyph.image(), glyph.x(), glyph.y(), glyph.width(), glyph.height(),
                glyph.declaredHeight(), ascent, glyph.cellSize(), glyph.javaAdvance(), glyph.trailingMarker());
    }

    private static int minimumCellSize(Glyph glyph, double displayWidth, int maxCell) {
        for (int size = MIN_CELL_SIZE; size <= maxCell; size *= 2) {
            if (displayWidth <= size && fitsBaseline(glyph, size)) return size;
        }
        return 0;
    }

    private static boolean fitsBaseline(Glyph glyph, int cellSize) {
        long top = (long) cellSize / 2 + 3 - glyph.declaredAscent();
        if (top >= 0 && top + glyph.declaredHeight() <= cellSize) return true;
        double scale = glyph.declaredHeight() / (double) glyph.height();
        for (int y = 0; y < glyph.height(); y++) {
            if (top + y * scale >= 0 && top + (y + 1) * scale <= cellSize) continue;
            for (int x = 0; x < glyph.width(); x++) {
                if ((glyph.image().getRGB(glyph.x() + x, glyph.y() + y) >>> 24) != 0) return false;
            }
        }
        return true;
    }

    private static String fontPath(String identifier) {
        String[] split = identifier(identifier, "minecraft").split(":", 2);
        return "assets/" + split[0] + "/font/" + split[1] + ".json";
    }

    private static String fontIdentifier(String path) {
        if (!path.startsWith("assets/") || !path.endsWith(".json")) return null;
        int namespaceEnd = path.indexOf('/', "assets/".length());
        if (namespaceEnd < 0 || !path.startsWith("font/", namespaceEnd + 1)) return null;
        String namespace = path.substring("assets/".length(), namespaceEnd);
        String value = path.substring(namespaceEnd + "/font/".length(), path.length() - ".json".length());
        return namespace.isBlank() || value.isBlank() ? null : namespace + ':' + value;
    }

    private static String texturePath(String identifier) {
        String[] split = identifier.split(":", 2);
        String path = split[1];
        if (!path.endsWith(".png")) path += ".png";
        return "assets/" + split[0] + "/textures/" + path;
    }

    private static String identifier(String value, String defaultNamespace) {
        String normalized = value.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains(":") ? normalized : defaultNamespace + ':' + normalized;
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString() : "";
    }

    private static int integer(JsonObject object, String key, int fallback) {
        try { return object.has(key) ? object.get(key).getAsInt() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static boolean isBmp(int codePoint) {
        return codePoint >= 0 && codePoint <= Character.MAX_VALUE &&
                (codePoint < Character.MIN_SURROGATE || codePoint > Character.MAX_SURROGATE);
    }

    private static boolean isBmpPrivateUse(int codePoint) {
        return codePoint >= 0xE000 && codePoint <= 0xF8FF;
    }

    private static String message(Throwable failure) {
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }

    private void notice(String category, String example) {
        noticeGroups.computeIfAbsent(category, ignored -> new ProblemBucket()).add(example, example);
    }

    private static String firstLine(String message) {
        int end = message.indexOf('\n');
        return end < 0 ? message : message.substring(0, end).trim();
    }

    private void problem(String category, String example) {
        problem(category, example, example);
    }

    private void problem(String category, String key, String example) {
        problemGroups.computeIfAbsent(category, ignored -> new ProblemBucket()).add(key, example);
    }

    private static final class ProblemBucket {
        private int count;
        private final Set<String> seen = new HashSet<>();
        private final List<String> examples = new ArrayList<>();

        void add(String key, String example) {
            if (!seen.add(key)) return;
            count++;
            if (examples.size() < 5 && !examples.contains(example)) examples.add(example);
        }

        String format(String category) {
            return count + " " + category + (examples.isEmpty() ? "" : "; examples: " + String.join(", ", examples));
        }
    }

    /**
     * @param glyphs directly mapped glyphs (aliases excluded)
     * @param aliasedGlyphs glyphs that Bedrock receives through private-use aliases in laid-out text
     * @param layout Java metrics for the runtime title layout, or null when no custom font content exists
     * @param notices content Java rejects as well (Bedrock matches Java); never fails a strict build
     */
    record Result(int glyphs, int pages, int vanillaFallbackTextures, int namedFonts, int namedGlyphs,
                  int aliasedGlyphs, TextLayoutTable layout, List<String> problems, List<String> notices) {}

    private record Advance(float value) {}

    private static final class AliasRequest {
        private final String font;
        private final int codePoint;
        private final Glyph glyph;
        private int bedrock = -1;

        AliasRequest(String font, int codePoint, Glyph glyph) {
            this.font = font;
            this.codePoint = codePoint;
            this.glyph = glyph;
        }

        String font() { return font; }
        int codePoint() { return codePoint; }
        Glyph glyph() { return glyph; }
    }

    private record GlyphKey(BufferedImage image, int x, int y, int width, int height, int declaredHeight,
                            int declaredAscent) {
        static GlyphKey of(Glyph glyph) {
            return new GlyphKey(glyph.image(), glyph.x(), glyph.y(), glyph.width(), glyph.height(),
                    glyph.declaredHeight(), glyph.declaredAscent());
        }

        @Override public boolean equals(Object other) {
            return other instanceof GlyphKey key && key.image == image && key.x == x && key.y == y
                    && key.width == width && key.height == height && key.declaredHeight == declaredHeight
                    && key.declaredAscent == declaredAscent;
        }

        @Override public int hashCode() {
            return java.util.Objects.hash(System.identityHashCode(image), x, y, width, height, declaredHeight, declaredAscent);
        }
    }

    private record Glyph(int codePoint, BufferedImage image, int x, int y, int width, int height,
                         int declaredHeight, int declaredAscent, int cellSize, int javaAdvance,
                         boolean trailingMarker) {
        Glyph(int codePoint, BufferedImage image, int x, int y, int width, int height,
              int declaredHeight, int declaredAscent, int cellSize, int javaAdvance) {
            this(codePoint, image, x, y, width, height, declaredHeight, declaredAscent, cellSize, javaAdvance, false);
        }

        Glyph withCellSize(int size) {
            return new Glyph(codePoint, image, x, y, width, height, declaredHeight, declaredAscent, size, javaAdvance,
                    trailingMarker);
        }

        Glyph withCodePoint(int alias) {
            return new Glyph(alias, image, x, y, width, height, declaredHeight, declaredAscent, cellSize, javaAdvance,
                    trailingMarker);
        }

        Glyph withImage(BufferedImage replacement) {
            return new Glyph(codePoint, replacement, x, y, width, height, declaredHeight, declaredAscent, cellSize,
                    javaAdvance, trailingMarker);
        }

        Glyph withTrailingMarker() {
            return new Glyph(codePoint, image, x, y, width, height, declaredHeight, declaredAscent, cellSize, javaAdvance,
                    true);
        }
    }
}
