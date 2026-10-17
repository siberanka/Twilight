/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.compiler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.siberanka.twilight.config.TwilightConfig;
import com.siberanka.twilight.source.ContentSource;
import com.siberanka.twilight.source.CustomItemDescriptor;
import com.siberanka.twilight.source.ResourceIndex;
import com.siberanka.twilight.text.TextLayoutTable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class BedrockPackCompiler {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final long ZIP_TIME = 315_532_800_000L;
    private static final int MAX_PACK_VERSION_COMPONENT = 65_535;
    private static final int MAX_BEDROCK_SAFE_IDENTIFIER_LENGTH = 36;
    /** The Geyser item mappings inside the pack, for twilight-proxy (Bedrock ignores the file). */
    public static final String PACK_MAPPINGS = "twilight/geyser_item_mappings.json";
    private final Path dataDirectory;
    private final TwilightConfig config;
    private final String minecraftVersion;
    private Map<String, Map<String, ?>> serverBiomes = Map.of();
    private Set<String> currentBiomes = Set.of();
    private boolean nameplatePlugin;

    public BedrockPackCompiler(Path dataDirectory, TwilightConfig config) {
        this(dataDirectory, config, null);
    }

    public BedrockPackCompiler(Path dataDirectory, TwilightConfig config, String minecraftVersion) {
        this.dataDirectory = dataDirectory.toAbsolutePath().normalize();
        this.config = config;
        this.minecraftVersion = minecraftVersion;
    }

    /**
     * Custom biomes of the running server's registry (datapack and plugin biomes, such as
     * RealisticSeasons' seasonal ones) as Java biome definitions; their looks get Bedrock biome slots.
     */
    public BedrockPackCompiler withServerBiomes(Map<String, Map<String, ?>> biomes) {
        return withServerBiomes(biomes, Set.of());
    }

    /** @param current biomes a seasons plugin shows now: their looks get slots first */
    public BedrockPackCompiler withServerBiomes(Map<String, Map<String, ?>> biomes, Set<String> current) {
        this.serverBiomes = Map.copyOf(biomes);
        this.currentBiomes = Set.copyOf(current);
        return this;
    }

    /**
     * Whether the pack carries the Bedrock entity that shows custom items in Java item displays (furniture, model
     * bones). It holds a copy of every 3D item's geometry and animation in one entity, which the client builds at
     * load: on a server with thousands of 3D items that takes minutes on a phone. Only Twilight's display bridge on
     * the same server's Geyser uses it, so a pack for a proxy's Geyser leaves it out.
     */
    public BedrockPackCompiler withItemDisplays(boolean value) {
        this.itemDisplays = value;
        return this;
    }

    private boolean itemDisplays = true;
    /** Keys of the items shown in Java item displays ({@code DisplayedItems}); null gives every item a variant. */
    private Set<String> displayedItems;

    /**
     * Only items shown in item displays here (and model bones) get a display variant: the client builds every
     * variant's cubes while it loads the pack. Null (the default) gives every custom item one.
     */
    public BedrockPackCompiler withDisplayedItems(Set<String> keys) {
        this.displayedItems = keys == null ? null : Set.copyOf(keys);
        return this;
    }

    /** Items with their own model ({@code item=}) and block states ({@code block=}) seen in displays. */
    private Set<String> displayedOwn = Set.of();

    /**
     * Displays of items without custom data and block displays, seen on this server or reported by twilight-proxy:
     * they get display variants of the item's own model and of the block state's model.
     */
    public BedrockPackCompiler withDisplayedOwnModels(Set<String> keys) {
        this.displayedOwn = keys == null ? Set.of() : Set.copyOf(keys);
        return this;
    }

    private Set<String> headSkins = Set.of(), headNames = Set.of(), playerSkins = Set.of();

    /** The skins of the players who joined, most recent first (see {@code PlayerSkins}). */
    public BedrockPackCompiler withPlayerSkins(Set<String> skins) {
        this.playerSkins = skins == null ? Set.of() : new java.util.LinkedHashSet<>(skins);
        return this;
    }
    private SkinCache skins;

    /**
     * The player heads the server shows ({@code PlayerHeads}): their skins (hashes) and user names go into the pack
     * for Geyser's custom skulls, and heads seen in displays get a display model drawn with their skin.
     *
     * @param skinCache where downloaded skins are kept; {@code download} allows asking Mojang's texture server
     */
    public BedrockPackCompiler withPlayerHeads(Set<String> skins, Set<String> names, Path skinCache, boolean download) {
        this.headSkins = skins == null ? Set.of() : new java.util.TreeSet<>(skins);
        this.headNames = names == null ? Set.of() : new java.util.TreeSet<>(names);
        this.skins = skinCache == null ? null : new SkinCache(skinCache, download);
        return this;
    }
    private boolean pocketJavaLayout = true;
    private boolean customBlocks = true;

    /** {@code geyser.custom-blocks}: convert content plugins' custom blocks (note blocks, mushroom blocks, tripwire). */
    public BedrockPackCompiler withCustomBlocks(boolean value) {
        this.customBlocks = value;
        return this;
    }

    /**
     * {@code ui.pocket-container-layout}: true gives chest screens of Bedrock's pocket UI profile (phones) the
     * Java layout of desktop screens; false keeps Bedrock's two-column pocket screens.
     */
    public BedrockPackCompiler withPocketContainerLayout(boolean java) {
        this.pocketJavaLayout = java;
        return this;
    }
    private int maxGlyphCell = 512;

    /** {@code ui.max-glyph-cell}: the largest glyph cell (512, 256, 128 or 64 pixels). */
    public BedrockPackCompiler withMaxGlyphCell(int size) {
        this.maxGlyphCell = size;
        return this;
    }

    /** A nameplate plugin draws its own tag backgrounds (ui.nametag-background: auto hides Bedrock's box). */
    public BedrockPackCompiler withNameplatePlugin(boolean value) {
        this.nameplatePlugin = value;
        return this;
    }

    public BuildResult build(List<ContentSource> sources, List<CustomItemDescriptor> liveItems) throws IOException {
        try (VanillaAssetCache vanillaAssets = minecraftVersion == null ? null : new VanillaAssetCache(
                     dataDirectory, minecraftVersion, config.downloadVanillaAssets());
             ResourceIndex resources = vanillaAssets == null ? ResourceIndex.build(sources, config)
                     : ResourceIndex.build(sources, config, vanillaAssets::packFormat)) {
            return build(resources, liveItems, vanillaAssets);
        }
    }

    private BuildResult build(ResourceIndex resources, List<CustomItemDescriptor> liveItems,
                              VanillaAssetCache vanillaAssets) throws IOException {
        List<ItemCandidate> candidates = new ItemCandidateCollector(resources).collect(liveItems);
        JavaModelResolver resolver = new JavaModelResolver(resources, vanillaAssets);
        VanillaItemReference vanillaItems = new VanillaItemReference(resources, vanillaAssets);
        Map<String, byte[]> packFiles = new LinkedHashMap<>();
        JsonObject textureData = new JsonObject();
        JsonObject mappedItems = new JsonObject();
        List<String> problems = new ArrayList<>();
        List<String> notices = new ArrayList<>();
        DisplayEntityResources displays = new DisplayEntityResources();
        DisplayModels displayModels = new DisplayModels(resources, vanillaAssets, resolver);
        int converted = 0, threeDimensional = 0, displayOnly = 0;
        // Java item model -> Bedrock item: an item drawn with a custom block's model places that block.
        Map<String, String> itemModels = new LinkedHashMap<>();

        Map<String, CustomItemDescriptor.Equipment> worn = equipmentBySelector(liveItems);
        Set<String> emitted = new HashSet<>();
        for (ItemCandidate candidate : candidates) {
            TextureSet.MISSING_FILES.get().clear();
            try {
                // A custom selector may show a vanilla model (menu buttons often use a barrier).
                boolean customSelector = candidate.customModelData().isPresent() || !candidate.predicates().isEmpty();
                ResolvedJavaModel model = resolver.resolveAll(candidate.visualModels(), customSelector);
                if (!model.isThreeDimensional() && flatLayers(model).isEmpty()
                        && candidate.nativeStateModels().isEmpty()) {
                    // No elements and no layers: Java draws it with a special renderer (player skin heads,
                    // shields, banners). Geyser renders those natively; a converted icon would replace that.
                    notices.add(candidate.baseItem() + " -> " + candidate.visualModels()
                            + ": drawn by Java's special renderer; Bedrock keeps its native rendering");
                    continue;
                }
                String identifier = bedrockIdentifier(candidate);
                if (!emitted.add(identifier)) {
                    // Geyser matches one Bedrock item per selector; Java uses the first matching entry as well.
                    notices.add(candidate.baseItem() + " -> " + candidate.visualModels()
                            + ": another model already uses the same item selector; the first one is kept");
                    continue;
                }
                String safe = identifier.substring(identifier.indexOf(':') + 1);
                if (itemDisplays && modelBone(candidate) && model.isThreeDimensional()) {
                    // Only item displays show model bones: the display variant is all they need. Without a Bedrock
                    // item, attachable and icon each, a model-heavy pack loads faster and Geyser registers fewer items.
                    TextureSet atlas = TextureSet.atlas(resources, List.copyOf(usedTextures(model)), vanillaAssets);
                    displays.addModel(candidate.mappingModel(), geometry(safe, model, atlas, geometryBounds(model)), atlas.png(),
                            model.renderParts().stream().map(ResolvedJavaModel::display).toList(), oneSided(model));
                    converted++;
                    threeDimensional++;
                    displayOnly++;
                    if (!TextureSet.MISSING_FILES.get().isEmpty()) {
                        notices.add(candidate.baseItem() + " -> " + candidate.visualModels() + ": textures that exist in no pack "
                                + "show Java's missing texture, as on Java: " + TextureSet.MISSING_FILES.get());
                    }
                    continue;
                }
                String texturePath = "textures/twilight/" + safe;
                String iconKey = "twilight." + safe;
                List<String> layers = flatLayers(model);
                NativeWeapon nativeWeapon = nativeWeapon(candidate, model, resolver, resources, vanillaItems, vanillaAssets);
                DynamicWeapon dynamicWeapon = nativeWeapon == null ? dynamicWeapon(candidate, resolver) : null;
                boolean customFlat = !model.isThreeDimensional() && nativeWeapon == null &&
                        requiresCustomFlat(candidate, model, vanillaItems);
                byte[] icon;
                if (dynamicWeapon != null) {
                    Set<String> stateTextures = new LinkedHashSet<>();
                    for (ResolvedJavaModel state : dynamicWeapon.states()) stateTextures.addAll(usedTextures(state));
                    TextureSet atlas = TextureSet.atlas(resources, List.copyOf(stateTextures), vanillaAssets);
                    packFiles.put(texturePath + ".png", atlas.png());
                    icon = TextureSet.png(GuiIconRenderer.render(model, atlas, geometryElements(model)));
                    packFiles.put("textures/twilight/" + safe + "_icon.png", icon);
                    for (int state = 0; state < dynamicWeapon.states().size(); state++) {
                        String stateSafe = safe + "_state_" + state;
                        ResolvedJavaModel stateModel = dynamicWeapon.states().get(state);
                        GeometryBounds bounds = geometryBounds(stateModel);
                        packFiles.put("models/entity/geometry." + stateSafe + ".geo.json",
                                jsonBytes(geometry(stateSafe, stateModel, atlas, bounds)));
                        packFiles.put("animations/" + stateSafe + ".animation.json",
                                jsonBytes(animations(stateSafe, stateModel)));
                    }
                    packFiles.put("render_controllers/" + safe + ".render_controllers.json",
                            jsonBytes(dynamicRenderController(safe, dynamicWeapon.states().size())));
                    packFiles.put("attachables/" + safe + ".json",
                            jsonBytes(dynamicWeaponAttachable(identifier, safe, texturePath, dynamicWeapon.type(),
                                    dynamicWeapon.states().size())));
                    if (dynamicWeapon.states().stream().anyMatch(ResolvedJavaModel::isThreeDimensional)) threeDimensional++;
                } else if (model.isThreeDimensional() || customFlat) {
                    Set<String> usedTextures = usedTextures(model);
                    if (usedTextures.contains(TextureSet.MISSING)) {
                        notices.add(candidate.baseItem() + " -> " + candidate.visualModels()
                                + ": faces without a defined texture show Java's missing texture, as on Java");
                    }
                    TextureSet atlas = TextureSet.atlas(resources, List.copyOf(usedTextures), vanillaAssets);
                    GeometryBounds bounds = geometryBounds(model);
                    packFiles.put(texturePath + ".png", atlas.png());
                    icon = TextureSet.png(GuiIconRenderer.render(model, atlas, geometryElements(model)));
                    packFiles.put("textures/twilight/" + safe + "_icon.png", icon);
                    packFiles.put("models/entity/geometry." + safe + ".geo.json",
                            jsonBytes(geometry(safe, model, atlas, bounds)));
                    packFiles.put("animations/" + safe + ".animation.json",
                            jsonBytes(animations(safe, model)));
                    packFiles.put("attachables/" + safe + ".json",
                            jsonBytes(attachable(identifier, safe, texturePath, oneSided(model))));
                    if (itemDisplays && model.isThreeDimensional() && displayed(candidate, displayedItems)) {
                        displays.add(identifier, geometry(safe, model, atlas, bounds), packFiles.get(texturePath + ".png"),
                                model.renderParts().stream().map(ResolvedJavaModel::display).toList(), oneSided(model));
                    } else if (itemDisplays && displayedFlat(candidate, displayedItems)) {
                        flatDisplay(displayModels, identifier, model, displays, candidate, notices);
                    }
                    if (model.isThreeDimensional()) threeDimensional++;
                } else {
                    icon = TextureSet.png(TextureSet.layeredIcon(resources, layers, vanillaAssets));
                    packFiles.put(texturePath + ".png", icon);
                    if (itemDisplays && displayedFlat(candidate, displayedItems)) {
                        flatDisplay(displayModels, identifier, model, displays, candidate, notices);
                    }
                    if (nativeWeapon != null) {
                        List<String> framePaths = new ArrayList<>();
                        for (int frame = 0; frame < nativeWeapon.frames().size(); frame++) {
                            String framePath = texturePath + "_native_frame_" + frame;
                            packFiles.put(framePath + ".png", nativeWeapon.frames().get(frame));
                            framePaths.add(framePath);
                        }
                        packFiles.put("attachables/" + safe + ".json",
                                jsonBytes(nativeWeaponAttachable(identifier, nativeWeapon.type(), framePaths)));
                    }
                }

                JsonObject iconEntry = new JsonObject();
                iconEntry.addProperty("textures", model.isThreeDimensional() || customFlat || dynamicWeapon != null
                        ? "textures/twilight/" + safe + "_icon" : texturePath);
                textureData.add(iconKey, iconEntry);
                CustomItemDescriptor.Equipment equipment = worn.get(selectorKey(candidate));
                addMapping(mappedItems, candidate, identifier, iconKey, model.handheld(), equipment);
                if (equipment != null && equipment.assetId().isPresent()) {
                    wear(resources, vanillaAssets, candidate, identifier, safe, equipment, packFiles, notices);
                }
                if (model.isThreeDimensional() && candidate.visualModels().size() == 1) {
                    itemModels.putIfAbsent(JavaModelResolver.qualified(candidate.visualModels().getFirst(), "minecraft"), identifier);
                    // Providers often give the item another model file than the block, with the same shape and art.
                    itemModels.putIfAbsent(CustomBlockCompiler.signature(resources, model), identifier);
                }
                converted++;
                if (!TextureSet.MISSING_FILES.get().isEmpty()) {
                    notices.add(candidate.baseItem() + " -> " + candidate.visualModels() + ": textures that exist in no pack "
                            + "show Java's missing texture, as on Java: " + TextureSet.MISSING_FILES.get());
                }
            } catch (JavaModelResolver.MissingModelException missing) {
                // Broken content Java tolerates: it draws its missing model; Bedrock keeps the base item.
                notices.add(candidate.baseItem() + " -> " + candidate.visualModels() + ": " + missing.getMessage()
                        + "; Java draws its missing model, Bedrock keeps the base item");
            } catch (Exception failure) {
                problems.add(candidate.baseItem() + " -> " + candidate.visualModels() + ": " + failure.getMessage());
            }
        }
        TextureSet.MISSING_FILES.remove();

        if (itemDisplays) for (String key : new java.util.TreeSet<>(displayedOwn)) {
            try {
                if (key.startsWith("item=")) displayModels.addItem(key.substring(5), displays, notices);
                else if (key.startsWith("block=")) displayModels.addBlock(key, displays, notices);
                else if (key.startsWith("head=") && skins != null) {
                    String skin = key.substring(5);
                    var png = skins.skin(skin);
                    if (png.isPresent()) displayModels.addHead(skin, png.get(), displays);
                    else notices.add("display " + key + ": its skin could not be read from Mojang's texture server; "
                            + "the head shows nothing on Bedrock until a later build");
                }
            } catch (JavaModelResolver.MissingModelException missing) {
                notices.add("display " + key + ": " + missing.getMessage() + "; Java draws its missing model, Bedrock shows nothing");
            } catch (IOException | RuntimeException failure) {
                notices.add("display " + key + ": " + failure.getMessage() + "; it shows nothing on Bedrock");
            }
        }
        displays.finish(packFiles);
        packFiles.put(SHULKER_CONTROLLER, jsonBytes(invisibleShulkers()));
        TextureSet.SKINS.remove();
        if (!headSkins.isEmpty() || !headNames.isEmpty()) {
            JsonObject heads = new JsonObject();
            heads.addProperty("format", 1);
            JsonArray skinList = new JsonArray(), nameList = new JsonArray();
            int count = 0;
            for (String skin : headSkins) if (com.siberanka.twilight.geyser.HeadTextures.valid(skin) && count++ < com.siberanka.twilight.geyser.CustomSkulls.MAX) skinList.add(skin);
            for (String name : headNames) if (com.siberanka.twilight.geyser.HeadTextures.name(name) && count++ < com.siberanka.twilight.geyser.CustomSkulls.MAX) nameList.add(name);
            heads.add("skins", skinList);
            heads.add("names", nameList);
            packFiles.put(com.siberanka.twilight.geyser.CustomSkulls.PATH, jsonBytes(heads));
        }
        if (!playerSkins.isEmpty()) {
            JsonObject players = new JsonObject();
            players.addProperty("format", 1);
            JsonArray list = new JsonArray();
            for (String skin : playerSkins) {
                if (list.size() >= com.siberanka.twilight.geyser.CustomSkulls.MAX) break;
                if (com.siberanka.twilight.geyser.HeadTextures.valid(skin)) list.add(skin);
            }
            players.add("skins", list);
            packFiles.put(com.siberanka.twilight.geyser.CustomSkulls.PLAYERS_PATH, jsonBytes(players));
        }
        CustomBlockCompiler.Result blocks = new CustomBlockCompiler.Result(0, 0, null);
        if (customBlocks) {
            try {
                blocks = new CustomBlockCompiler(resources, resolver, vanillaAssets, config.vanillaOverride(), packFiles, notices)
                        .compile(itemModels);
            } catch (IOException | RuntimeException failure) {
                problems.add("custom blocks: " + failure.getMessage());
            }
        }
        BitmapFontCompiler.Result fonts;
        // Text layout metrics serve every text surface; only chest titles also need the container layout.
        boolean textLayout = config.javaTextLayout();
        SoundCompiler.Result sounds;
        if (vanillaAssets == null) {
            fonts = new BitmapFontCompiler(resources, null, config.vanillaOverride(), textLayout, config.javaGlyphTint())
                    .maxCell(maxGlyphCell).compile(packFiles);
            sounds = new SoundCompiler(resources, config.vanillaOverride(), null).compile(packFiles);
        } else {
            fonts = new BitmapFontCompiler(resources, vanillaAssets, config.vanillaOverride(), textLayout, config.javaGlyphTint())
                    .maxCell(maxGlyphCell).compile(packFiles);
            sounds = new SoundCompiler(resources, config.vanillaOverride(), vanillaAssets).compile(packFiles);
        }
        problems.addAll(fonts.problems());
        problems.addAll(sounds.problems());
        notices.addAll(fonts.notices());
        notices.addAll(sounds.notices());
        LanguageCompiler.Result languages = LanguageCompiler.compile(resources, packFiles);
        languages.problems().forEach(problem -> notices.add("language file is malformed and ignored, as on Java: " + problem));
        if (vanillaAssets != null) {
            // Server-side lookup table for the biome bridge; Bedrock ignores the file.
            try {
                com.siberanka.twilight.world.BiomeMatcher matcher = VanillaBiomes.read(vanillaAssets);
                packFiles.put(com.siberanka.twilight.world.BiomeMatcher.PATH, jsonBytes(matcher.toJson()));
                if (config.bedrockBiomeMatching()) {
                    var slots = com.siberanka.twilight.world.BiomeSlots.assign(
                            CustomBiomes.read(resources, serverBiomes, matcher), matcher.vanilla().values(), currentBiomes);
                    if (!slots.isEmpty()) packFiles.putAll(slots.packFiles());
                }
            } catch (IOException | RuntimeException failure) {
                notices.add("vanilla biome appearances are unavailable; custom biomes keep Geyser's fallback: "
                        + failure.getMessage());
            }
        }
        // Lines that move back over themselves (text on a background) need one label per layer in the UI.
        java.util.Set<String> layerSurfaces = new java.util.TreeSet<>();
        if (fonts.layout() != null && config.javaTextLayers()) {
            if (config.javaContainerLayout()) layerSurfaces.add(TextLayoutTable.CHEST_LAYERS);
            if (config.javaTextSurfaces()) {
                layerSurfaces.add(TextLayoutTable.ACTIONBAR_LAYERS);
                layerSurfaces.add(TextLayoutTable.BOSS_LAYERS);
            }
        }
        // Boss bars: colours Java draws transparent are hidden, colours with redrawn sprites keep them.
        java.util.Set<String> styledBars = java.util.Set.of();
        java.util.Set<String> barSprites = java.util.Set.of();
        if (fonts.layout() != null && layerSurfaces.contains(TextLayoutTable.BOSS_LAYERS)) {
            styledBars = BossBars.styled(resources);
            barSprites = BossBars.writeSprites(resources, vanillaAssets, styledBars, packFiles);
            styledBars = BossBars.drawn(styledBars, barSprites);
        }
        if (fonts.layout() != null) {
            TextLayoutTable table = fonts.layout();
            // Bedrock's own chest UI keeps its title label at the left edge: no origin to reach.
            if (!config.javaContainerLayout()) table = table.withContainerOrigin(0);
            else table = table.withPocketJavaLayout(pocketJavaLayout).withPlainTitles(true);
            if (layerSurfaces.contains(TextLayoutTable.BOSS_LAYERS)) {
                table = table.withHiddenBossBars(BossBars.hidden(resources)).withStyledBossBars(styledBars);
            }
            packFiles.put(TextLayoutTable.PATH, table.withLayers(layerSurfaces).toJson().toString()
                    .getBytes(StandardCharsets.UTF_8));
        }
        if (config.javaContainerLayout()) {
            packFiles.put(JavaContainerUi.PATH, jsonBytes(JavaContainerUi.chestScreen(fonts.layout() != null,
                    layerSurfaces.contains(TextLayoutTable.CHEST_LAYERS), pocketJavaLayout)));
            packFiles.put(JavaContainerUi.COMMON_PATH, jsonBytes(JavaContainerUi.commonScreen()));
        }
        if (layerSurfaces.contains(TextLayoutTable.BOSS_LAYERS)) {
            packFiles.put(JavaHudUi.PATH, jsonBytes(JavaHudUi.hudScreen(styledBars, barSprites)));
            packFiles.put(JavaHudUi.CLEAR_TEXTURE + ".png", TextureSet.png(
                    new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB)));
        }

        String background = config.nametagBackground();
        if ("hidden".equals(background) || "auto".equals(background) && nameplatePlugin) {
            packFiles.put(NametagMaterial.PATH, NametagMaterial.hiddenBackground());
        }

        if (converted == 0 && !candidates.isEmpty() && !problems.isEmpty()) throw new IOException("No custom item could be converted; first problem: " + problems.get(0));
        if (config.strict() && !problems.isEmpty()) {
            throw new ConversionException("Strict conversion rejected " + problems.size() +
                    " content problem(s); first problem: " + problems.get(0), problems);
        }
        JsonObject itemAtlas = new JsonObject();
        itemAtlas.addProperty("resource_pack_name", "twilight");
        itemAtlas.addProperty("texture_name", "atlas.items");
        itemAtlas.add("texture_data", textureData);
        packFiles.put("textures/item_texture.json", jsonBytes(itemAtlas));
        JsonObject mappings = new JsonObject();
        mappings.addProperty("format_version", 2);
        mappings.add("items", mappedItems);
        // twilight-proxy reads the mappings from the pack it receives and registers them with the proxy's Geyser.
        packFiles.put(PACK_MAPPINGS, jsonBytes(mappings));
        minifyJson(packFiles);
        packFiles.put("manifest.json", jsonBytes(manifest(packFiles)));
        packFiles.put("README.txt", ("Generated by Twilight. Converted " + converted + " of " + candidates.size() +
                " custom item definitions.\n").getBytes(StandardCharsets.UTF_8));

        Path staging = dataDirectory.resolve("build/.staging");
        Path current = dataDirectory.resolve("build/current");
        deleteTree(staging);
        Files.createDirectories(staging.resolve("custom_mappings"));
        Path pack = staging.resolve("pack.zip");
        writeZip(pack, packFiles);
        Files.write(staging.resolve("custom_mappings/geyser_item_mappings.json"), jsonBytes(mappings));
        JsonObject report = new JsonObject();
        report.addProperty("candidates", candidates.size());
        report.addProperty("converted", converted);
        report.addProperty("three_dimensional", threeDimensional);
        report.addProperty("custom_blocks", blocks.blocks());
        report.addProperty("block_items", blocks.items());
        report.addProperty("display_variants", displays.size());
        report.addProperty("player_heads", headSkins.size() + headNames.size());
        report.addProperty("display_only_models", displayOnly);
        report.addProperty("glyphs", fonts.glyphs());
        report.addProperty("font_pages", fonts.pages());
        report.addProperty("vanilla_fallback_textures", fonts.vanillaFallbackTextures());
        report.addProperty("named_fonts", fonts.namedFonts());
        report.addProperty("named_font_glyphs", fonts.namedGlyphs());
        report.addProperty("aliased_glyphs", fonts.aliasedGlyphs());
        report.addProperty("text_layout_entries", fonts.layout() == null ? 0 : fonts.layout().entryCount());
        report.addProperty("sound_definitions", sounds.definitions());
        report.addProperty("sound_files", sounds.files());
        report.addProperty("vanilla_fallback_sounds", sounds.vanillaFallbackFiles());
        report.addProperty("minecraft_version", minecraftVersion == null ? "unavailable" : minecraftVersion);
        report.addProperty("vanilla_asset_download", config.downloadVanillaAssets());
        report.addProperty("skipped", candidates.size() - converted);
        report.add("problems", GSON.toJsonTree(problems));
        report.add("notices", GSON.toJsonTree(notices));
        report.addProperty("language_locales", languages.locales());
        report.addProperty("language_strings", languages.strings());
        report.addProperty("vanilla_override", config.vanillaOverride());
        Files.write(staging.resolve("build-report.json"), jsonBytes(report));
        validate(pack, mappings, converted - displayOnly);
        deleteTree(current.resolveSibling("previous"));
        if (Files.exists(current)) Files.move(current, current.resolveSibling("previous"), StandardCopyOption.REPLACE_EXISTING);
        Files.move(staging, current, StandardCopyOption.REPLACE_EXISTING);
        deleteTree(current.resolveSibling("previous"));
        return new BuildResult(current, candidates.size(), converted, threeDimensional, fonts.glyphs(), fonts.pages(),
                fonts.vanillaFallbackTextures(),
                fonts.namedFonts(), fonts.namedGlyphs(),
                sounds.definitions(), sounds.files(), sounds.vanillaFallbackFiles(),
                candidates.size() - converted, List.copyOf(problems), sha256(current.resolve("pack.zip")));
    }

    private JsonObject manifest(Map<String, byte[]> contentWithoutManifest) throws IOException {
        Properties identity = new Properties();
        Path identityFile = dataDirectory.resolve("pack-identity.properties");
        if (Files.isRegularFile(identityFile)) try (InputStream input = Files.newInputStream(identityFile)) { identity.load(input); }
        String header = identity.getProperty("header", UUID.randomUUID().toString());
        String module = identity.getProperty("module", UUID.randomUUID().toString());
        identity.setProperty("header", header); identity.setProperty("module", module);
        Files.createDirectories(dataDirectory);
        try (var output = Files.newOutputStream(identityFile)) { identity.store(output, "Stable Twilight Bedrock pack identity"); }
        int contentHash = java.util.Arrays.hashCode(contentWithoutManifest.values().stream()
                .flatMapToInt(bytes -> java.util.stream.IntStream.of(java.util.Arrays.hashCode(bytes))).toArray());
        // Bedrock serializes legacy manifest version vectors through a bounded
        // protocol field. Very large positive Java integers can make the client
        // reject the complete server pack stack before entering the world.
        int build = Math.floorMod(contentHash, MAX_PACK_VERSION_COMPONENT) + 1;

        JsonObject headerJson = new JsonObject();
        headerJson.addProperty("name", "Twilight");
        headerJson.addProperty("description", "Server-generated custom content for Geyser");
        headerJson.addProperty("uuid", header);
        headerJson.add("version", intArray(1, 0, build));
        headerJson.add("min_engine_version", intArray(1, 21, 0));
        JsonObject moduleJson = new JsonObject();
        moduleJson.addProperty("type", "resources");
        moduleJson.addProperty("uuid", module);
        moduleJson.add("version", intArray(1, 0, build));
        JsonObject manifest = new JsonObject();
        manifest.addProperty("format_version", 2);
        manifest.add("header", headerJson);
        JsonArray modules = new JsonArray(); modules.add(moduleJson); manifest.add("modules", modules);
        return manifest;
    }

    static List<String> flatLayers(ResolvedJavaModel model) {
        List<String> layers = new ArrayList<>();
        for (int index = 0; index < 32; index++) {
            String texture = model.textures().get("layer" + index);
            if (texture == null) break;
            layers.add(texture);
        }
        return layers;
    }

    private static Set<String> usedTextures(ResolvedJavaModel model) throws IOException {
        Set<String> textures = new LinkedHashSet<>();
        if (!model.parts().isEmpty()) {
            for (ResolvedJavaModel part : model.parts()) textures.addAll(usedTextures(part));
            return textures;
        }
        for (JsonElement element : geometryElements(model)) {
            JsonObject faces = element.getAsJsonObject().getAsJsonObject("faces");
            if (faces == null) continue;
            for (Map.Entry<String, JsonElement> face : faces.entrySet()) {
                JsonObject faceJson = face.getValue().getAsJsonObject();
                if (!faceJson.has("texture")) continue;
                String reference = faceJson.get("texture").getAsString();
                if (reference.startsWith("#")) {
                    String resolved = model.textures().get(reference.substring(1));
                    // Java draws its missing texture for a face whose variable no model defines.
                    textures.add(resolved == null ? TextureSet.MISSING : resolved);
                } else textures.add(JavaModelResolver.qualified(reference, "minecraft"));
            }
        }
        if (textures.isEmpty()) textures.addAll(flatLayers(model));
        if (textures.isEmpty()) throw new IOException("3D model has no face textures");
        return textures;
    }

    private static NativeWeapon nativeWeapon(ItemCandidate candidate, ResolvedJavaModel model,
                                             JavaModelResolver resolver, ResourceIndex resources,
                                             VanillaItemReference vanillaItems, VanillaAssetCache vanillaAssets) throws IOException {
        String type;
        int expected;
        if (candidate.baseItem().equals("minecraft:bow")) {
            type = "bow";
            expected = 4;
        } else if (candidate.baseItem().equals("minecraft:crossbow")) {
            type = "crossbow";
            expected = 6;
        } else return null;
        if (model.isThreeDimensional() || flatLayers(model).size() != 1) return null;

        List<String> stateModels = candidate.nativeStateModels().isEmpty()
                ? List.of(candidate.visualModel()) : candidate.nativeStateModels();
        int referenceState = 0;
        boolean chargedCrossbow = false;
        if (type.equals("crossbow") && stateModels.size() == 1) {
            if (hasChargeType(candidate, "arrow")) referenceState = 4;
            else if (hasChargeType(candidate, "rocket")) referenceState = 5;
            else return null;
            chargedCrossbow = true;
        }
        // Bedrock's built-in weapon geometry selects every state at runtime.
        // Reusing it is safe only when Java supplied a matching texture for
        // every state that Bedrock can select.
        if (!chargedCrossbow && stateModels.size() != expected) return null;
        List<byte[]> frames = new ArrayList<>(expected);
        for (String stateModel : stateModels) {
            if (frames.size() == expected) break;
            ResolvedJavaModel state = resolver.resolve(stateModel);
            List<String> stateLayers = flatLayers(state);
            if (state.isThreeDimensional() || stateLayers.size() != 1) return null;
            try {
                if (!vanillaItems.matchesWeaponState(type, referenceState + frames.size(), state)) return null;
            } catch (IOException unavailableReference) {
                return null;
            }
            frames.add(TextureSet.png(TextureSet.layeredIcon(resources, stateLayers, vanillaAssets)));
        }
        if (chargedCrossbow) while (frames.size() < expected) frames.add(frames.getFirst());
        return frames.size() == expected ? new NativeWeapon(type, List.copyOf(frames)) : null;
    }

    private static boolean hasChargeType(ItemCandidate candidate, String expected) {
        for (JsonElement predicateValue : candidate.predicates()) {
            if (!predicateValue.isJsonObject()) continue;
            JsonObject predicate = predicateValue.getAsJsonObject();
            if (predicate.has("property") && predicate.has("value") &&
                    predicate.get("property").getAsString().equals("charge_type") &&
                    predicate.get("value").getAsString().equals(expected)) return true;
        }
        return false;
    }

    private static DynamicWeapon dynamicWeapon(ItemCandidate candidate, JavaModelResolver resolver) throws IOException {
        if (!(candidate.baseItem().equals("minecraft:bow") || candidate.baseItem().equals("minecraft:crossbow")) ||
                candidate.nativeStateModels().size() < 4) return null;
        List<ResolvedJavaModel> states = new ArrayList<>(4);
        for (String stateModel : candidate.nativeStateModels().subList(0, 4)) {
            ResolvedJavaModel state = resolver.resolve(stateModel);
            if (!state.isThreeDimensional() && flatLayers(state).isEmpty()) return null;
            states.add(state);
        }
        String type = candidate.baseItem().equals("minecraft:bow") ? "bow" : "crossbow";
        return new DynamicWeapon(type, List.copyOf(states));
    }

    /**
     * A bone of a ModelEngine or BetterModel model: an item whose {@code item_model} only item displays show (the
     * plugins never hand it to players). With display models it needs no Bedrock item of its own.
     */
    /** A custom item Java draws flat gets its extruded display model only when a display named it (or with {@code all}). */
    static boolean displayedFlat(ItemCandidate candidate, Set<String> shown) {
        if (shown == null) return true;
        for (String key : displayKeys(candidate)) if (shown.contains(key)) return true;
        return false;
    }

    private static void flatDisplay(DisplayModels models, String identifier, ResolvedJavaModel model,
                                    DisplayEntityResources displays, ItemCandidate candidate, List<String> notices) {
        try {
            models.addFlatItem(identifier, model, displays);
        } catch (IOException | RuntimeException failure) {
            notices.add(candidate.baseItem() + " -> " + candidate.visualModels() + ": no display model ("
                    + failure.getMessage() + "); item displays of it show nothing on Bedrock");
        }
    }

    static boolean displayed(ItemCandidate candidate, Set<String> shown) {
        if (shown == null) return true;
        List<String> keys = displayKeys(candidate);
        if (keys.isEmpty()) return true; // No way to tell: shown rather than missing.
        for (String key : keys) if (shown.contains(key)) return true;
        return false;
    }

    /**
     * The keys an item display's stack would have for this item (see {@code DisplayedItems}): its custom model
     * data on the base item, or the item model it names.
     */
    static List<String> displayKeys(ItemCandidate candidate) {
        String base = candidate.baseItem().toLowerCase(java.util.Locale.ROOT);
        if (candidate.customModelData().isPresent()) return List.of(base + "|cmd=" + candidate.customModelData().getAsInt());
        List<String> keys = new ArrayList<>();
        if (!candidate.mappingModel().startsWith("minecraft:")) keys.add("model=" + candidate.mappingModel());
        for (JsonElement value : candidate.predicates()) {
            if (!value.isJsonObject()) continue;
            JsonObject predicate = value.getAsJsonObject();
            String property = predicate.has("property") ? predicate.get("property").getAsString() : "";
            if (!property.endsWith("custom_model_data")) continue;
            if (predicate.has("threshold")) keys.add(base + "|cmd=" + (int) Math.floor(predicate.get("threshold").getAsDouble()));
            if (predicate.has("value")) keys.add(base + "|str=" + predicate.get("value").getAsString());
        }
        return keys;
    }

    static boolean modelBone(ItemCandidate candidate) {
        return (candidate.source().equals("ModelEngine") || candidate.source().equals("BetterModel"))
                && candidate.customModelData().isEmpty() && candidate.predicates().isEmpty();
    }

    private static boolean requiresCustomFlat(ItemCandidate candidate, ResolvedJavaModel model,
                                              VanillaItemReference vanillaItems) {
        List<String> layers = flatLayers(model);
        if (layers.size() != 1) return true;
        if (candidate.baseItem().equals("minecraft:bow") || candidate.baseItem().equals("minecraft:crossbow")) {
            return true;
        }
        if (candidate.baseItem().equals("minecraft:fishing_rod")) {
            boolean cast = candidate.predicates().toString().contains("fishing_rod_cast");
            try { return !vanillaItems.matchesFishingRod(cast, model); }
            catch (IOException unavailableReference) { return true; }
        }
        return !JavaModelResolver.hasStandardFlatDisplay(model.display());
    }

    /**
     * Java's generated item model is represented as ordered, alpha-tested
     * sheets. A small depth keeps both faces stable in Bedrock while each
     * layer retains its own texture and Java display transform.
     */
    static JsonArray geometryElements(ResolvedJavaModel model) throws IOException {
        if (model.elements() != null && !model.elements().isEmpty()) return model.elements();
        List<String> layers = flatLayers(model);
        if (layers.isEmpty()) throw new IOException("Flat item has no texture layers");
        JsonArray elements = new JsonArray();
        for (int layer = 0; layer < layers.size(); layer++) {
            double front = 7.5 + layer * 0.0125;
            JsonObject element = new JsonObject();
            element.add("from", numberArray(0, 0, front));
            element.add("to", numberArray(16, 16, front + 0.01));
            JsonObject faces = new JsonObject();
            JsonObject north = new JsonObject();
            north.add("uv", numberArray(16, 0, 0, 16));
            north.addProperty("texture", "#layer" + layer);
            faces.add("north", north);
            JsonObject south = new JsonObject();
            south.add("uv", numberArray(0, 0, 16, 16));
            south.addProperty("texture", "#layer" + layer);
            faces.add("south", south);
            element.add("faces", faces);
            elements.add(element);
        }
        return elements;
    }

    private static JsonObject geometry(String safe, ResolvedJavaModel model, TextureSet atlas,
                                       GeometryBounds bounds) throws IOException {
        if (!model.parts().isEmpty()) {
            JsonObject result = null;
            JsonArray bones = new JsonArray();
            for (int i = 0; i < model.parts().size(); i++) {
                JsonObject part = geometry(safe, model.parts().get(i), atlas, bounds);
                if (result == null) result = part;
                JsonObject bone = part.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject()
                        .getAsJsonArray("bones").get(0).getAsJsonObject();
                bone.addProperty("name", "bone_" + i);
                bones.add(bone);
            }
            result.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().add("bones", bones);
            return result;
        }
        JsonArray cubes = new JsonArray();
        List<JsonObject> elements = new ArrayList<>();
        for (JsonElement elementValue : geometryElements(model)) {
            // Inverted elements show only their inside on Java: their faces become one-sided planes (see oneSided).
            JsonObject source = elementValue.getAsJsonObject();
            if (InvertedElements.inverted(source)) elements.addAll(InvertedElements.faces(source));
            else elements.add(source);
        }
        for (JsonObject element : elements) {
            double[] from = vector(element.getAsJsonArray("from"), 0, 0, 0);
            double[] to = vector(element.getAsJsonArray("to"), 16, 16, 16);
            JsonObject cube = new JsonObject();
            cube.add("origin", numberArray(from[0] - 8, from[1], 8 - to[2]));
            cube.add("size", numberArray(to[0] - from[0], to[1] - from[1], to[2] - from[2]));
            if (element.has("rotation")) {
                JsonObject rotation = element.getAsJsonObject("rotation");
                double[] pivot = vector(rotation.getAsJsonArray("origin"), 8, 8, 8);
                double angle = rotation.has("angle") ? rotation.get("angle").getAsDouble() : 0;
                String axis = rotation.has("axis") ? rotation.get("axis").getAsString() : "y";
                cube.add("pivot", numberArray(pivot[0] - 8, pivot[1], 8 - pivot[2]));
                cube.add("rotation", switch (axis) {
                    case "x" -> numberArray(angle, 0, 0);
                    case "z" -> numberArray(0, 0, -angle);
                    default -> numberArray(0, -angle, 0);
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
                double[] faceUv = face.has("uv") ? vector4(face.getAsJsonArray("uv"))
                        : defaultFaceUv(faceEntry.getKey(), from, to);
                JsonObject mapped = new JsonObject();
                mapped.add("uv", numberArray(region.x() + faceUv[0] * region.width() / 16.0,
                        region.y() + faceUv[1] * region.height() / 16.0));
                mapped.add("uv_size", numberArray((faceUv[2] - faceUv[0]) * region.width() / 16.0,
                        (faceUv[3] - faceUv[1]) * region.height() / 16.0));
                if (face.has("rotation")) mapped.addProperty("uv_rotation", face.get("rotation").getAsInt());
                uv.add(mapFace(faceEntry.getKey()), mapped);
            }
            cube.add("uv", uv);
            cubes.add(cube);
        }
        JsonObject description = new JsonObject();
        description.addProperty("identifier", "geometry.twilight." + safe);
        description.addProperty("texture_width", atlas.width());
        description.addProperty("texture_height", atlas.height());
        description.addProperty("visible_bounds_width", Math.max(1,
                Math.max(bounds.maxX() - bounds.minX(), bounds.maxZ() - bounds.minZ()) / 16.0 + 1));
        description.addProperty("visible_bounds_height", Math.max(1,
                (bounds.maxY() - bounds.minY()) / 16.0 + 1));
        description.add("visible_bounds_offset", numberArray(
                (bounds.minX() + bounds.maxX()) / 32.0,
                (bounds.minY() + bounds.maxY()) / 32.0,
                (bounds.minZ() + bounds.maxZ()) / 32.0));
        JsonObject bone = new JsonObject(); bone.addProperty("name", "bone");
        bone.addProperty("binding", "q.item_slot_to_bone_name(context.item_slot)"); bone.add("pivot", numberArray(0, 8, 0)); bone.add("cubes", cubes);
        JsonArray bones = new JsonArray(); bones.add(bone);
        JsonObject geometry = new JsonObject(); geometry.add("description", description); geometry.add("bones", bones);
        JsonArray geometries = new JsonArray(); geometries.add(geometry);
        JsonObject root = new JsonObject(); root.addProperty("format_version", "1.21.0"); root.add("minecraft:geometry", geometries);
        return root;
    }

    /**
     * Whether a model needs Java's one-sided faces: inverted elements show their inside only because Java draws
     * one side of each face. Such models use a one-sided material; others keep the two-sided one they always had.
     */
    static boolean oneSided(ResolvedJavaModel model) throws IOException {
        if (!model.parts().isEmpty()) {
            for (ResolvedJavaModel part : model.parts()) if (oneSided(part)) return true;
            return false;
        }
        if (model.elements() == null) return false;
        for (JsonElement element : model.elements()) {
            if (element.isJsonObject() && InvertedElements.inverted(element.getAsJsonObject())) return true;
        }
        return false;
    }

    /** The geometry of a model for its display variant (see {@link DisplayEntityResources}). */
    static JsonObject displayGeometry(String safe, ResolvedJavaModel model, TextureSet atlas) throws IOException {
        return geometry(safe, model, atlas, geometryBounds(model));
    }

    /** Every texture a model's faces draw. */
    static Set<String> modelTextures(ResolvedJavaModel model) throws IOException {
        return usedTextures(model);
    }

    static double[] defaultFaceUv(String face, double[] from, double[] to) {
        return switch (face) {
            case "down" -> new double[]{from[0], 16 - to[2], to[0], 16 - from[2]};
            case "up" -> new double[]{from[0], from[2], to[0], to[2]};
            case "north" -> new double[]{16 - to[0], 16 - to[1], 16 - from[0], 16 - from[1]};
            case "south" -> new double[]{from[0], 16 - to[1], to[0], 16 - from[1]};
            case "west" -> new double[]{from[2], 16 - to[1], to[2], 16 - from[1]};
            case "east" -> new double[]{16 - to[2], 16 - to[1], 16 - from[2], 16 - from[1]};
            default -> throw new IllegalArgumentException("Unknown Java face " + face);
        };
    }

    private static JsonObject attachable(String identifier, String safe, String texturePath, boolean oneSided) {
        JsonObject description = new JsonObject(); description.addProperty("identifier", identifier);
        JsonObject materials = new JsonObject(); materials.addProperty("default", oneSided ? ONE_SIDED : "entity_alphatest"); materials.addProperty("enchanted", "entity_alphatest_glint"); description.add("materials", materials);
        JsonObject textures = new JsonObject(); textures.addProperty("default", texturePath); textures.addProperty("enchanted", "textures/misc/enchanted_item_glint"); description.add("textures", textures);
        JsonObject geometries = new JsonObject(); geometries.addProperty("default", "geometry.twilight." + safe); description.add("geometry", geometries);
        JsonObject animationNames = new JsonObject();
        for (String view : List.of("first_person_right", "first_person_left", "third_person_right", "third_person_left", "head"))
            animationNames.addProperty(view, "animation.twilight." + safe + "." + view);
        description.add("animations", animationNames);
        JsonArray animate = new JsonArray();
        animate.add(condition("first_person_right", "context.is_first_person == 1.0 && context.item_slot == 'main_hand'"));
        animate.add(condition("first_person_left", "context.is_first_person == 1.0 && context.item_slot == 'off_hand'"));
        animate.add(condition("third_person_right", "context.is_first_person == 0.0 && context.item_slot == 'main_hand'"));
        animate.add(condition("third_person_left", "context.is_first_person == 0.0 && context.item_slot == 'off_hand'"));
        animate.add(condition("head", "context.is_first_person == 0.0 && context.item_slot == 'head'"));
        JsonObject scripts = new JsonObject(); scripts.add("animate", animate); description.add("scripts", scripts);
        JsonArray controllers = new JsonArray(); controllers.add("controller.render.item_default"); description.add("render_controllers", controllers);
        JsonObject attachable = new JsonObject(); attachable.add("description", description);
        JsonObject root = new JsonObject(); root.addProperty("format_version", "1.21.0"); root.add("minecraft:attachable", attachable); return root;
    }

    private static JsonObject nativeWeaponAttachable(String identifier, String type, List<String> framePaths) {
        List<String> textureKeys = type.equals("bow")
                ? List.of("default", "bow_pulling_0", "bow_pulling_1", "bow_pulling_2")
                : List.of("default", "crossbow_pulling_0", "crossbow_pulling_1", "crossbow_pulling_2",
                "crossbow_arrow", "crossbow_rocket");
        List<String> geometries = type.equals("bow")
                ? List.of("geometry.bow_standby", "geometry.bow_pulling_0", "geometry.bow_pulling_1", "geometry.bow_pulling_2")
                : List.of("geometry.crossbow_standby", "geometry.crossbow_pulling_0", "geometry.crossbow_pulling_1",
                "geometry.crossbow_pulling_2", "geometry.crossbow_arrow", "geometry.crossbow_rocket");
        JsonObject description = new JsonObject();
        description.addProperty("identifier", identifier);
        JsonObject materials = new JsonObject();
        materials.addProperty("default", "entity_alphatest");
        materials.addProperty("enchanted", "entity_alphatest_glint");
        description.add("materials", materials);
        JsonObject textures = new JsonObject();
        for (int index = 0; index < textureKeys.size(); index++) textures.addProperty(textureKeys.get(index), framePaths.get(index));
        textures.addProperty("enchanted", "textures/misc/enchanted_item_glint");
        description.add("textures", textures);
        JsonObject geometry = new JsonObject();
        for (int index = 0; index < textureKeys.size(); index++) geometry.addProperty(textureKeys.get(index), geometries.get(index));
        description.add("geometry", geometry);
        JsonObject animations = new JsonObject();
        animations.addProperty("wield", "animation." + type + ".wield");
        animations.addProperty("wield_first_person_pull", "animation." + type + ".wield_first_person_pull");
        description.add("animations", animations);
        JsonObject scripts = new JsonObject();
        JsonArray preAnimation = new JsonArray();
        preAnimation.add("variable.charge_amount = math.clamp((query.main_hand_item_max_duration - " +
                "(query.main_hand_item_use_duration - query.frame_alpha + 1.0)) / 10.0, 0.0, 1.0f);");
        scripts.add("pre_animation", preAnimation);
        JsonArray animate = new JsonArray();
        animate.add("wield");
        animate.add(condition("wield_first_person_pull",
                "query.main_hand_item_use_duration > 0.0f && c.is_first_person"));
        scripts.add("animate", animate);
        description.add("scripts", scripts);
        JsonArray controllers = new JsonArray();
        controllers.add("controller.render." + type);
        description.add("render_controllers", controllers);
        JsonObject attachable = new JsonObject(); attachable.add("description", description);
        JsonObject root = new JsonObject(); root.addProperty("format_version", "1.21.0");
        root.add("minecraft:attachable", attachable);
        return root;
    }

    private static JsonObject dynamicWeaponAttachable(String identifier, String safe, String texturePath,
                                                      String type, int stateCount) {
        JsonObject description = new JsonObject();
        description.addProperty("identifier", identifier);
        JsonObject materials = new JsonObject();
        materials.addProperty("default", "entity_alphatest");
        materials.addProperty("enchanted", "entity_alphatest_glint");
        description.add("materials", materials);
        JsonObject textures = new JsonObject();
        textures.addProperty("default", texturePath);
        textures.addProperty("enchanted", "textures/misc/enchanted_item_glint");
        description.add("textures", textures);
        JsonObject geometry = new JsonObject();
        geometry.addProperty("default", "geometry.twilight." + safe + "_state_0");
        for (int state = 0; state < stateCount; state++)
            geometry.addProperty("state_" + state, "geometry.twilight." + safe + "_state_" + state);
        description.add("geometry", geometry);
        JsonObject animationNames = new JsonObject();
        List<String> views = List.of("first_person_right", "first_person_left", "third_person_right", "third_person_left", "head");
        for (int state = 0; state < stateCount; state++) for (String view : views)
            animationNames.addProperty("state_" + state + '_' + view,
                    "animation.twilight." + safe + "_state_" + state + '.' + view);
        description.add("animations", animationNames);

        JsonObject scripts = new JsonObject();
        JsonArray preAnimation = new JsonArray();
        preAnimation.add("v.twilight_model_index = " + dynamicStateExpression(type) + ";");
        scripts.add("pre_animation", preAnimation);
        JsonArray animate = new JsonArray();
        for (int state = 0; state < stateCount; state++) {
            String selected = "v.twilight_model_index == " + state;
            animate.add(condition("state_" + state + "_first_person_right",
                    selected + " && context.is_first_person == 1.0 && context.item_slot == 'main_hand'"));
            animate.add(condition("state_" + state + "_first_person_left",
                    selected + " && context.is_first_person == 1.0 && context.item_slot == 'off_hand'"));
            animate.add(condition("state_" + state + "_third_person_right",
                    selected + " && context.is_first_person == 0.0 && context.item_slot == 'main_hand'"));
            animate.add(condition("state_" + state + "_third_person_left",
                    selected + " && context.is_first_person == 0.0 && context.item_slot == 'off_hand'"));
            animate.add(condition("state_" + state + "_head",
                    selected + " && context.is_first_person == 0.0 && context.item_slot == 'head'"));
        }
        scripts.add("animate", animate);
        description.add("scripts", scripts);
        JsonArray controllers = new JsonArray();
        controllers.add("controller.render.twilight." + safe + "_runtime");
        description.add("render_controllers", controllers);
        JsonObject attachable = new JsonObject(); attachable.add("description", description);
        JsonObject root = new JsonObject(); root.addProperty("format_version", "1.21.0");
        root.add("minecraft:attachable", attachable);
        return root;
    }

    private static JsonObject dynamicRenderController(String safe, int stateCount) {
        JsonArray geometries = new JsonArray();
        for (int state = 0; state < stateCount; state++) geometries.add("Geometry.state_" + state);
        JsonObject geometryArrays = new JsonObject();
        geometryArrays.add("Array.twilight_geometries", geometries);
        JsonObject arrays = new JsonObject(); arrays.add("geometries", geometryArrays);
        JsonObject controller = new JsonObject();
        controller.add("arrays", arrays);
        controller.addProperty("geometry", "Array.twilight_geometries[v.twilight_model_index]");
        JsonArray materials = new JsonArray();
        JsonObject material = new JsonObject();
        material.addProperty("*", "variable.is_enchanted ? Material.enchanted : Material.default");
        materials.add(material);
        controller.add("materials", materials);
        JsonArray textures = new JsonArray(); textures.add("Texture.default"); textures.add("Texture.enchanted");
        controller.add("textures", textures);
        JsonObject controllers = new JsonObject();
        controllers.add("controller.render.twilight." + safe + "_runtime", controller);
        JsonObject root = new JsonObject(); root.addProperty("format_version", "1.8.0");
        root.add("render_controllers", controllers);
        return root;
    }

    private static String dynamicStateExpression(String type) {
        String progress = type.equals("crossbow")
                ? "math.clamp(1.0 - q.item_remaining_use_duration(c.item_slot, 1.0), 0.0, 1.0)"
                : "math.clamp(q.item_in_use_duration, 0.0, 1.0)";
        String secondThreshold = type.equals("crossbow") ? "0.58" : "0.65";
        String finalThreshold = type.equals("crossbow") ? "1.0" : "0.9";
        return "(q.is_using_item && q.item_remaining_use_duration(c.item_slot) > 0.0) ? (" + progress +
                " >= " + finalThreshold + " ? 3 : (" + progress + " >= " + secondThreshold + " ? 2 : 1)) : 0";
    }

    private static JsonObject animations(String safe, ResolvedJavaModel model) {
        if (model.parts().isEmpty()) return animations(safe, model.display());
        JsonObject result = null;
        for (int i = 0; i < model.parts().size(); i++) {
            JsonObject part = animations(safe, model.parts().get(i).display());
            for (var entry : part.getAsJsonObject("animations").entrySet()) {
                JsonObject bones = entry.getValue().getAsJsonObject().getAsJsonObject("bones");
                bones.add("bone_" + i, bones.remove("bone"));
                if (result != null) result.getAsJsonObject("animations").getAsJsonObject(entry.getKey())
                        .getAsJsonObject("bones").add("bone_" + i, bones.get("bone_" + i));
            }
            if (result == null) result = part;
        }
        return result;
    }

    private static JsonObject animations(String safe, JsonObject display) {
        JsonObject definitions = new JsonObject();
        JsonObject firstRight = transform(display, "firstperson_righthand", null);
        JsonObject firstLeft = transform(display, "firstperson_lefthand", firstRight);
        JsonObject thirdRight = transform(display, "thirdperson_righthand", null);
        JsonObject thirdLeft = transform(display, "thirdperson_lefthand", thirdRight);
        definitions.add("animation.twilight." + safe + ".first_person_right", animation(mapFirst(firstRight)));
        definitions.add("animation.twilight." + safe + ".first_person_left", animation(mapFirst(
                mirror(firstLeft))));
        definitions.add("animation.twilight." + safe + ".third_person_right", animation(mapThird(thirdRight)));
        definitions.add("animation.twilight." + safe + ".third_person_left", animation(mapThird(
                mirror(thirdLeft))));
        definitions.add("animation.twilight." + safe + ".head", animation(mapHead(transform(display, "head", null))));
        JsonObject root = new JsonObject(); root.addProperty("format_version", "1.8.0"); root.add("animations", definitions); return root;
    }

    private static JsonObject animation(MappedTransform mapped) {
        JsonObject bone = new JsonObject(); bone.add("position", numberArray(mapped.position)); bone.add("rotation", numberArray(mapped.rotation)); bone.add("scale", numberArray(mapped.scale));
        JsonObject bones = new JsonObject(); bones.add("bone", bone);
        JsonObject animation = new JsonObject(); animation.addProperty("loop", true); animation.add("bones", bones); return animation;
    }

    private static MappedTransform mapFirst(JsonObject transform) {
        double[] t = vector(transform.getAsJsonArray("translation"), 0, 0, 0), r = vector(transform.getAsJsonArray("rotation"), 0, 0, 0), s = vector(transform.getAsJsonArray("scale"), 1, 1, 1);
        Quaternion q = Quaternion.axis(-90, 1, 0, 0).mul(Quaternion.axis(r[0], 0, 0, 1)).mul(Quaternion.axis(r[1], 1, 0, 0)).mul(Quaternion.axis(-r[2], 0, 1, 0));
        return new MappedTransform(new double[]{-t[1], 12.5 + t[2], t[0]}, q.eulerXYZ(), s);
    }

    private static MappedTransform mapThird(JsonObject transform) {
        double[] t = vector(transform.getAsJsonArray("translation"), 0, 0, 0), r = vector(transform.getAsJsonArray("rotation"), 0, 0, 0), s = vector(transform.getAsJsonArray("scale"), 1, 1, 1);
        // Java display is intrinsic XYZ. Bedrock reverses X/Y rotation signs,
        // while Z keeps its sign. Bridge our Z-reflected geometry to Bedrock's
        // X-reflected model frame before applying the authored display pose.
        Quaternion q = Quaternion.axis(90, 1, 0, 0)
                .mul(Quaternion.axis(-r[0], 1, 0, 0))
                .mul(Quaternion.axis(-r[1], 0, 1, 0))
                .mul(Quaternion.axis(r[2], 0, 0, 1))
                .mul(Quaternion.axis(180, 0, 1, 0));
        return new MappedTransform(new double[]{-t[0], 12.5 + t[2], -t[1]}, q.eulerXYZ(), s);
    }

    private static MappedTransform mapHead(JsonObject transform) {
        double[] t = vector(transform.getAsJsonArray("translation"), 0, 0, 0), r = vector(transform.getAsJsonArray("rotation"), 0, 0, 0), s = vector(transform.getAsJsonArray("scale"), 1, 1, 1);
        return new MappedTransform(new double[]{-t[0] * .655, 20 + t[1] * .655, t[2] * .655}, new double[]{-r[0], -r[1], r[2]}, scale(s, .655));
    }

    private static JsonObject transform(JsonObject display, String name, JsonObject fallback) {
        JsonObject result = fallback == null ? new JsonObject() : fallback.deepCopy();
        if (display.has(name)) result = display.getAsJsonObject(name).deepCopy();
        if (!result.has("translation")) result.add("translation", numberArray(0, 0, 0));
        if (!result.has("rotation")) result.add("rotation", numberArray(0, 0, 0));
        if (!result.has("scale")) result.add("scale", numberArray(1, 1, 1));
        return result;
    }

    private static JsonObject mirror(JsonObject input) {
        // Java ItemTransform.apply mirrors every left-hand context, including
        // explicitly authored ones; the fallback only selects the source pose.
        JsonObject result = input.deepCopy();
        double[] r = vector(result.getAsJsonArray("rotation"), 0, 0, 0), t = vector(result.getAsJsonArray("translation"), 0, 0, 0);
        result.add("rotation", numberArray(r[0], -r[1], -r[2])); result.add("translation", numberArray(-t[0], t[1], t[2])); return result;
    }

    private static GeometryBounds geometryBounds(ResolvedJavaModel model) throws IOException {
        GeometryBounds bounds = GeometryBounds.empty();
        for (JsonElement elementValue : geometryElements(model)) {
            JsonObject element = elementValue.getAsJsonObject();
            double[] from = vector(element.getAsJsonArray("from"), 0, 0, 0);
            double[] to = vector(element.getAsJsonArray("to"), 16, 16, 16);
            JsonObject rotation = element.has("rotation") ? element.getAsJsonObject("rotation") : null;
            double[] pivot = rotation == null ? new double[]{8, 8, 8}
                    : vector(rotation.getAsJsonArray("origin"), 8, 8, 8);
            double angle = rotation != null && rotation.has("angle")
                    ? Math.toRadians(rotation.get("angle").getAsDouble()) : 0;
            String axis = rotation != null && rotation.has("axis")
                    ? rotation.get("axis").getAsString() : "y";
            double cosine = Math.cos(angle), sine = Math.sin(angle);
            for (int mask = 0; mask < 8; mask++) {
                double x = ((mask & 1) == 0 ? from[0] : to[0]) - pivot[0];
                double y = ((mask & 2) == 0 ? from[1] : to[1]) - pivot[1];
                double z = ((mask & 4) == 0 ? from[2] : to[2]) - pivot[2];
                double rotatedX = x, rotatedY = y, rotatedZ = z;
                switch (axis) {
                    case "x" -> {
                        rotatedY = y * cosine - z * sine;
                        rotatedZ = y * sine + z * cosine;
                    }
                    case "z" -> {
                        rotatedX = x * cosine - y * sine;
                        rotatedY = x * sine + y * cosine;
                    }
                    case "y" -> {
                        rotatedX = x * cosine + z * sine;
                        rotatedZ = -x * sine + z * cosine;
                    }
                    default -> throw new IOException("Unsupported element rotation axis " + axis);
                }
                bounds = bounds.include(rotatedX + pivot[0] - 8,
                        rotatedY + pivot[1], 8 - (rotatedZ + pivot[2]));
            }
        }
        if (!bounds.finite()) throw new IOException("3D model geometry bounds must be finite");
        return bounds;
    }

    private static void addMapping(JsonObject mappedItems, ItemCandidate candidate, String identifier, String iconKey,
                                   boolean displayHandheld, CustomItemDescriptor.Equipment equipment) {
        JsonArray definitions = mappedItems.has(candidate.baseItem()) ? mappedItems.getAsJsonArray(candidate.baseItem()) : new JsonArray();
        JsonObject mapping = new JsonObject();
        if (candidate.customModelData().isPresent()) { mapping.addProperty("type", "legacy"); mapping.addProperty("custom_model_data", candidate.customModelData().getAsInt()); }
        else { mapping.addProperty("type", "definition"); mapping.addProperty("model", candidate.mappingModel()); }
        mapping.addProperty("bedrock_identifier", identifier);
        if (!candidate.predicates().isEmpty()) {
            mapping.add("predicate", candidate.predicates().size() == 1
                    ? candidate.predicates().get(0).deepCopy() : candidate.predicates().deepCopy());
            if (candidate.predicates().size() > 1) mapping.addProperty("predicate_strategy", "and");
        }
        if (candidate.priority() != 0) mapping.addProperty("priority", candidate.priority());
        JsonObject options = new JsonObject();
        options.addProperty("icon", iconKey);
        if (displayHandheld) options.addProperty("display_handheld", true);
        mapping.add("bedrock_options", options);
        if (equipment != null && !equipment.slot().equals("body")) {
            // The provider may make any item wearable: Geyser then lets Bedrock players wear it in that slot too.
            JsonObject equippable = new JsonObject();
            equippable.addProperty("slot", equipment.slot());
            JsonObject components = new JsonObject();
            components.add("minecraft:equippable", equippable);
            mapping.add("components", components);
        }
        definitions.add(mapping); mappedItems.add(candidate.baseItem(), definitions);
    }

    /** Bedrock's entity material that draws one side of each face with alpha testing, as Java draws item models. */
    static final String ONE_SIDED = "entity_alphatest_one_sided";
    static final String SHULKER_CONTROLLER = "render_controllers/twilight_shulker.render_controllers.json";

    /**
     * Invisible shulkers. Furniture plugins (CraftEngine) block building with invisible shulkers; Java draws nothing,
     * but Bedrock draws invisible shulkers, so a shell stood over every such piece of furniture. Geyser marks the
     * shulkers it sends (the bribed flag) for a pack to hide; Bedrock's own shulker controller, with its parts
     * hidden while such a shulker is invisible.
     */
    static JsonObject invisibleShulkers() {
        JsonArray variants = new JsonArray();
        for (String colour : List.of("black", "red", "green", "brown", "blue", "purple", "cyan", "silver", "gray", "pink",
                "lime", "yellow", "light_blue", "magenta", "orange", "white", "undyed")) variants.add("Texture." + colour);
        JsonObject textureArrays = new JsonObject();
        textureArrays.add("Array.variants", variants);
        JsonObject arrays = new JsonObject();
        arrays.add("textures", textureArrays);
        JsonObject controller = new JsonObject();
        controller.add("arrays", arrays);
        controller.addProperty("geometry", "Geometry.default");
        JsonArray materials = new JsonArray();
        JsonObject material = new JsonObject();
        material.addProperty("*", "Material.default");
        materials.add(material);
        controller.add("materials", materials);
        JsonArray textures = new JsonArray();
        textures.add("Array.variants[query.variant]");
        controller.add("textures", textures);
        JsonArray visibility = new JsonArray();
        JsonObject all = new JsonObject();
        all.addProperty("*", "!(query.is_invisible && query.is_bribed)");
        visibility.add(all);
        controller.add("part_visibility", visibility);
        JsonObject controllers = new JsonObject();
        controllers.add("controller.render.shulker", controller);
        JsonObject root = new JsonObject();
        root.addProperty("format_version", "1.8.0");
        root.add("render_controllers", controllers);
        return root;
    }

    /** The equipment of the live items by the selector Geyser matches them with (the first in a stable order). */
    static Map<String, CustomItemDescriptor.Equipment> equipmentBySelector(List<CustomItemDescriptor> liveItems) {
        Map<String, CustomItemDescriptor.Equipment> result = new java.util.TreeMap<>();
        List<CustomItemDescriptor> ordered = new ArrayList<>(liveItems == null ? List.of() : liveItems);
        ordered.sort(java.util.Comparator.comparing(CustomItemDescriptor::provider).thenComparing(CustomItemDescriptor::baseItem)
                .thenComparing(item -> item.itemModel().orElse(""))
                .thenComparingInt(item -> item.customModelData().orElse(Integer.MIN_VALUE)));
        for (CustomItemDescriptor item : ordered) {
            if (item.equipment().isEmpty()) continue;
            item.itemModel().ifPresent(model -> result.putIfAbsent(item.baseItem() + "|definition|"
                    + JavaModelResolver.qualified(model, "minecraft"), item.equipment().get()));
            if (item.customModelData().isPresent()) {
                result.putIfAbsent(item.baseItem() + "|legacy|" + item.customModelData().getAsInt(), item.equipment().get());
            }
        }
        return result;
    }

    static String selectorKey(ItemCandidate candidate) {
        return candidate.customModelData().isPresent()
                ? candidate.baseItem() + "|legacy|" + candidate.customModelData().getAsInt()
                : candidate.baseItem() + "|definition|" + candidate.mappingModel();
    }

    /** Worn custom armour and elytras: the equipment asset Java draws on the body (see {@link EquipmentModels}). */
    private static void wear(ResourceIndex resources, VanillaAssetCache vanillaAssets, ItemCandidate candidate,
                             String identifier, String safe, CustomItemDescriptor.Equipment equipment,
                             Map<String, byte[]> packFiles, List<String> notices) {
        String asset = equipment.assetId().orElseThrow();
        String label = candidate.baseItem() + " -> " + candidate.visualModels() + " worn as " + asset;
        try {
            Optional<JsonObject> layers = EquipmentModels.layers(resources, vanillaAssets, asset);
            if (layers.isEmpty()) {
                notices.add(label + ": the equipment asset exists in no pack; Java draws nothing on the body, nor does Bedrock");
                return;
            }
            Optional<EquipmentModels.Look> look = EquipmentModels.look(equipment.slot(), layers.get());
            if (look.isEmpty()) return;
            List<String> missing = new ArrayList<>();
            Optional<java.awt.image.BufferedImage> texture = Optional.empty();
            String variant = asset + '|' + look.get().layer();
            // ItemsAdder's armour is dyed leather a shader draws by colour (see ItemsAdderArmours).
            Optional<ItemsAdderArmours.Rendering> custom = equipment.dye().isPresent() && !look.get().wings()
                    ? ItemsAdderArmours.forColour(resources, equipment.dye().getAsInt()) : Optional.empty();
            if (custom.isPresent()) {
                String sprite = custom.get().layer(look.get().layer());
                texture = EquipmentModels.layer(resources, vanillaAssets, sprite, custom.get().tint());
                if (texture.isEmpty()) missing.add(sprite);
                variant = sprite + '|' + custom.get().tint();
            }
            if (texture.isEmpty()) {
                texture = EquipmentModels.texture(resources, vanillaAssets, layers.get(), look.get().layer(), equipment.dye(), missing);
                if (equipment.dye().isPresent()) variant += "|dye=" + equipment.dye().getAsInt();
            }
            if (!missing.isEmpty()) notices.add(label + ": equipment textures that exist in no pack are left out: " + missing);
            if (texture.isEmpty()) return;
            String raw = asset.replaceAll("[^a-z0-9_]", "_");
            if (raw.length() > 40) raw = raw.substring(raw.length() - 40);
            String texturePath = "textures/twilight/equipment/" + raw + '_' + shortHash(variant);
            packFiles.putIfAbsent(texturePath + ".png", TextureSet.png(texture.get()));
            packFiles.put("attachables/" + safe + ".worn.json",
                    jsonBytes(EquipmentModels.attachable(identifier, equipment, look.get(), texturePath)));
        } catch (IOException | RuntimeException failure) {
            notices.add(label + ": " + failure.getMessage() + "; worn, it shows nothing on Bedrock");
        }
    }

    /**
     * The Bedrock item of a Java selector. It depends only on what Geyser matches (the Java item, its custom
     * model data or item model, and the predicates), never on the model a pack draws: every backend of a proxy
     * network then gives the same Java item the same Bedrock item, the proxy's single Geyser registry has no
     * conflicting entries, and each server's own pack decides how that item looks there.
     */
    static String bedrockIdentifier(ItemCandidate candidate) {
        String selector = candidate.customModelData().isPresent()
                ? candidate.baseItem() + "_" + candidate.customModelData().getAsInt()
                : candidate.mappingModel();
        String raw = selector.toLowerCase(Locale.ROOT).replace(':', '_').replaceAll("[^a-z0-9._-]", "_");
        // Keep every emitted resource-pack path below Geyser's 80-character
        // portability boundary, including dynamic `_state_N.animation.json`
        // suffixes. The stable hash remains the collision boundary when the
        // readable model portion has to be shortened.
        int readableLength = MAX_BEDROCK_SAFE_IDENTIFIER_LENGTH - 13; // '_' plus the 12-character hash
        if (raw.length() > readableLength) raw = raw.substring(raw.length() - readableLength);
        String key = candidate.customModelData().isPresent()
                ? candidate.baseItem() + "|legacy|" + candidate.customModelData().getAsInt()
                : candidate.baseItem() + "|definition|" + candidate.mappingModel();
        return "twilight:" + raw + "_" + shortHash(key + '|' + candidate.predicates());
    }

    private static String shortHash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)), 0, 6); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static String mapFace(String face) { return switch (face) { case "north" -> "south"; case "south" -> "north"; default -> face; }; }
    private static JsonObject condition(String animation, String expression) { JsonObject value = new JsonObject(); value.addProperty(animation, expression); return value; }
    private static double[] scale(double[] value, double factor) { return new double[]{value[0] * factor, value[1] * factor, value[2] * factor}; }
    private static double[] vector(JsonArray value, double x, double y, double z) { if (value == null || value.size() < 3) return new double[]{x,y,z}; return new double[]{value.get(0).getAsDouble(),value.get(1).getAsDouble(),value.get(2).getAsDouble()}; }
    private static double[] vector4(JsonArray value) throws IOException { if (value == null || value.size() < 4) throw new IOException("Face UV must contain four numbers"); return new double[]{value.get(0).getAsDouble(),value.get(1).getAsDouble(),value.get(2).getAsDouble(),value.get(3).getAsDouble()}; }
    static JsonArray numberArray(double... values) { JsonArray array = new JsonArray(); for (double value : values) array.add(value); return array; }
    static JsonArray intArray(int... values) { JsonArray array = new JsonArray(); for (int value : values) array.add(value); return array; }
    private static final Gson COMPACT = new GsonBuilder().disableHtmlEscaping().create();

    /**
     * Writes every JSON file of the pack without indentation. The client reads all of them while it loads the
     * pack; pretty printing doubled the size of large packs (geometry and animations of thousands of models).
     */
    static void minifyJson(Map<String, byte[]> packFiles) {
        for (Map.Entry<String, byte[]> entry : packFiles.entrySet()) {
            if (!entry.getKey().endsWith(".json")) continue;
            try {
                JsonElement parsed = com.google.gson.JsonParser.parseString(new String(entry.getValue(), StandardCharsets.UTF_8));
                entry.setValue(COMPACT.toJson(parsed).getBytes(StandardCharsets.UTF_8));
            } catch (RuntimeException notJson) {
                // Kept as written: Bedrock reads some files with comments that strict JSON rejects.
            }
        }
    }

    static byte[] jsonBytes(JsonElement element) { return GSON.toJson(element).getBytes(StandardCharsets.UTF_8); }

    private static void validate(Path pack, JsonObject mappings, int expected) throws IOException {
        if (!Files.isRegularFile(pack) || Files.size(pack) == 0) throw new IOException("Generated pack is empty");
        int definitions = 0; for (JsonElement array : mappings.getAsJsonObject("items").asMap().values()) definitions += array.getAsJsonArray().size();
        if (definitions != expected) throw new IOException("Mapping count mismatch: expected " + expected + ", got " + definitions);
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(pack.toFile())) {
            if (zip.getEntry("manifest.json") == null || zip.getEntry("textures/item_texture.json") == null) throw new IOException("Generated pack lacks required files");
            var entries = zip.entries(); while (entries.hasMoreElements()) { ZipEntry e = entries.nextElement(); if (e.getName().endsWith(".json")) try (InputStream in = zip.getInputStream(e)) { JsonParser.parseReader(new java.io.InputStreamReader(in, StandardCharsets.UTF_8)); } }
        }
    }

    private static void writeZip(Path path, Map<String, byte[]> files) throws IOException {
        Files.createDirectories(path.getParent());
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
            for (Map.Entry<String, byte[]> file : files.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
                ZipEntry entry = new ZipEntry(file.getKey()); entry.setTime(ZIP_TIME); zip.putNextEntry(entry); zip.write(file.getValue()); zip.closeEntry();
            }
        }
    }

    private static String sha256(Path path) throws IOException { try { MessageDigest d=MessageDigest.getInstance("SHA-256"); try(InputStream in=Files.newInputStream(path)){byte[] b=new byte[65536]; for(int n;(n=in.read(b))>=0;)if(n>0)d.update(b,0,n);} return HexFormat.of().formatHex(d.digest()).toUpperCase(Locale.ROOT);}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);} }
    private static void deleteTree(Path path) throws IOException { if(!Files.exists(path))return; try(var stream=Files.walk(path)){for(Path entry:stream.sorted(Comparator.reverseOrder()).toList()){if(Files.isSymbolicLink(entry))throw new IOException("Symbolic build path rejected: "+entry); Files.deleteIfExists(entry);}} }

    private record MappedTransform(double[] position, double[] rotation, double[] scale) {}
    private record NativeWeapon(String type, List<byte[]> frames) {}
    private record DynamicWeapon(String type, List<ResolvedJavaModel> states) {}
    private record GeometryBounds(double minX, double minY, double minZ,
                                  double maxX, double maxY, double maxZ) {
        static GeometryBounds empty() {
            return new GeometryBounds(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                    Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
                    Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY);
        }

        GeometryBounds include(double x, double y, double z) {
            return new GeometryBounds(Math.min(minX, x), Math.min(minY, y), Math.min(minZ, z),
                    Math.max(maxX, x), Math.max(maxY, y), Math.max(maxZ, z));
        }

        boolean finite() {
            return Double.isFinite(minX) && Double.isFinite(minY) && Double.isFinite(minZ)
                    && Double.isFinite(maxX) && Double.isFinite(maxY) && Double.isFinite(maxZ);
        }

    }
    private record Quaternion(double x,double y,double z,double w) {
        static Quaternion axis(double degrees,double ax,double ay,double az){double h=Math.toRadians(degrees)/2,s=Math.sin(h);return new Quaternion(ax*s,ay*s,az*s,Math.cos(h));}
        Quaternion mul(Quaternion b){return new Quaternion(w*b.x+x*b.w+y*b.z-z*b.y,w*b.y-x*b.z+y*b.w+z*b.x,w*b.z+x*b.y-y*b.x+z*b.w,w*b.w-x*b.x-y*b.y-z*b.z);}
        double[] eulerXYZ() {
            double xx = x * x, yy = y * y, zz = z * z;
            double m00 = 1 - 2 * (yy + zz), m01 = 2 * (x * y - z * w);
            double m10 = 2 * (x * y + z * w), m11 = 1 - 2 * (xx + zz);
            double m20 = 2 * (x * z - y * w), m21 = 2 * (y * z + x * w);
            double m22 = 1 - 2 * (xx + yy);
            double sy = Math.max(-1, Math.min(1, -m20));
            double yAngle = Math.asin(sy), xAngle, zAngle;
            if (Math.abs(Math.cos(yAngle)) > 1.0e-7) {
                xAngle = Math.atan2(m21, m22);
                zAngle = Math.atan2(m10, m00);
            } else {
                // At +90 degrees m01 = sin(x-z); at -90 it is -sin(x+z).
                // Choose z=0 without changing the represented orientation.
                xAngle = Math.atan2(sy * m01, m11);
                zAngle = 0;
            }
            return new double[]{Math.toDegrees(xAngle), Math.toDegrees(yAngle), Math.toDegrees(zAngle)};
        }
    }
}
