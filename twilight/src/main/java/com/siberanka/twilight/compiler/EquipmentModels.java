/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.compiler;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.siberanka.twilight.source.CustomItemDescriptor;
import com.siberanka.twilight.source.ResourceIndex;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Worn equipment. Java draws an item's equipment asset ({@code assets/<ns>/equipment/<id>.json}, named by the
 * item's {@code equippable} component) on the body: armour layers on the humanoid model and wings for elytras. A
 * Bedrock custom item has no such look of its own, so worn custom armour showed nothing. Each asset becomes a Bedrock
 * texture (the asset's layers drawn over each other, as Java draws them) and an attachable that puts it on Bedrock's
 * own armour or elytra geometry; the texture layouts of both editions are the same.
 */
final class EquipmentModels {
    private static final Pattern IDENTIFIER = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    /** Equipment textures larger than this are refused (a 64x32 layout at 16 times Java's resolution). */
    private static final int MAX_SIZE = 1024;

    private EquipmentModels() {}

    /** What a slot draws: Bedrock's geometry and the variable that hides the entity's own armour there. */
    record Look(String layer, String geometry, String hideVariable, boolean wings) {}

    /** The look of an asset in a slot, or empty when Java draws nothing there for it. */
    static Optional<Look> look(String slot, JsonObject layers) {
        if (slot.equals("chest") && layers.has("wings") && !layers.has("humanoid")) {
            return Optional.of(new Look("wings", "geometry.elytra", "variable.chest_layer_visible", true));
        }
        return switch (slot) {
            case "head" -> Optional.of(new Look("humanoid", "geometry.humanoid.armor.helmet", "variable.helmet_layer_visible", false));
            case "chest" -> Optional.of(new Look("humanoid", "geometry.humanoid.armor.chestplate", "variable.chest_layer_visible", false));
            case "legs" -> Optional.of(new Look("humanoid_leggings", "geometry.humanoid.armor.leggings", "variable.leg_layer_visible", false));
            case "feet" -> Optional.of(new Look("humanoid", "geometry.humanoid.armor.boots", "variable.boot_layer_visible", false));
            default -> Optional.empty();
        };
    }

    /** The asset's layers ({@code layers} object), from the packs first and then the vanilla client. */
    static Optional<JsonObject> layers(ResourceIndex resources, VanillaAssetCache vanilla, String assetId) throws IOException {
        if (!IDENTIFIER.matcher(assetId).matches() || assetId.contains("..")) return Optional.empty();
        int colon = assetId.indexOf(':');
        String namespace = assetId.substring(0, colon), path = assetId.substring(colon + 1);
        // 1.21.4+ names the folder equipment/, 1.21.2 and 1.21.3 named it models/equipment/.
        for (String file : List.of("assets/" + namespace + "/equipment/" + path + ".json",
                "assets/" + namespace + "/models/equipment/" + path + ".json")) {
            Optional<ResourceIndex.Asset> asset = resources.find(file);
            byte[] bytes = asset.isPresent() ? asset.get().readBytes()
                    : vanilla != null && namespace.equals("minecraft") ? vanilla.readEquipment(file).orElse(null) : null;
            if (bytes == null) continue;
            try {
                JsonElement root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
                if (root.isJsonObject() && root.getAsJsonObject().get("layers") instanceof JsonObject layers) {
                    return Optional.of(layers);
                }
            } catch (RuntimeException malformed) {
                throw new IOException("equipment asset " + assetId + " is malformed: " + malformed.getMessage());
            }
            return Optional.empty();
        }
        return Optional.empty();
    }

    /**
     * The layers of one layer type drawn over each other as Java draws them undyed: a dyeable layer takes its
     * {@code color_when_undyed} and is left out without one; layers drawing the player's own texture (capes on
     * elytras) are left out. Empty when no layer could be drawn.
     */
    static Optional<BufferedImage> texture(ResourceIndex resources, VanillaAssetCache vanilla, JsonObject layers,
                                           String layerType, List<String> missing) throws IOException {
        if (!(layers.get(layerType) instanceof JsonArray list)) return Optional.empty();
        List<BufferedImage> images = new ArrayList<>();
        for (JsonElement element : list) {
            if (!(element instanceof JsonObject layer) || !layer.has("texture")) continue;
            if (layer.has("use_player_texture") && layer.get("use_player_texture").getAsBoolean()) continue;
            String texture = layer.get("texture").getAsString();
            if (!IDENTIFIER.matcher(JavaModelResolver.qualified(texture, "minecraft")).matches()) continue;
            int tint = -1;
            if (layer.get("dyeable") instanceof JsonObject dyeable) {
                if (!dyeable.has("color_when_undyed")) continue;
                tint = 0xFF000000 | dyeable.get("color_when_undyed").getAsInt();
            }
            String qualified = JavaModelResolver.qualified(texture, "minecraft");
            int colon = qualified.indexOf(':');
            String sprite = qualified.substring(0, colon) + ":entity/equipment/" + layerType + '/' + qualified.substring(colon + 1);
            String file = JavaModelResolver.texturePath(sprite);
            boolean exists = resources.find(file).isPresent() || vanilla != null
                    && file.startsWith("assets/minecraft/textures/") && vanilla.readTexture(file).isPresent();
            if (!exists) {
                // Java draws its missing texture on the body; a 16x16 checker would not fit the armour layout.
                missing.add(sprite);
                continue;
            }
            BufferedImage image = TextureSet.frame(resources, TextureSet.tinted(tint, sprite), vanilla);
            if (image.getWidth() > MAX_SIZE || image.getHeight() > MAX_SIZE) {
                throw new IOException("equipment texture " + sprite + " is larger than " + MAX_SIZE + " pixels");
            }
            images.add(image);
        }
        if (images.isEmpty()) return Optional.empty();
        int width = images.stream().mapToInt(BufferedImage::getWidth).max().orElseThrow();
        int height = images.stream().mapToInt(BufferedImage::getHeight).max().orElseThrow();
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = result.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            for (BufferedImage image : images) graphics.drawImage(image, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return Optional.of(result);
    }

    /**
     * The attachable that draws the equipment while the item is worn. It applies only in the item's own slot (an
     * {@code item} condition, as Bedrock's own armour attachables use), so a held item keeps its icon or model.
     */
    static JsonObject attachable(String identifier, CustomItemDescriptor.Equipment equipment, Look look, String texturePath) {
        JsonObject description = new JsonObject();
        description.addProperty("identifier", identifier + ".worn");
        JsonObject item = new JsonObject();
        item.addProperty(identifier, "query.is_item_name_any('" + slotName(equipment.slot()) + "', 0, '" + identifier + "')");
        description.add("item", item);
        JsonObject materials = new JsonObject();
        materials.addProperty("default", look.wings() ? "elytra" : "armor");
        materials.addProperty("enchanted", look.wings() ? "elytra_glint" : "armor_enchanted");
        description.add("materials", materials);
        JsonObject textures = new JsonObject();
        textures.addProperty("default", texturePath);
        textures.addProperty("enchanted", "textures/misc/enchanted_actor_glint");
        description.add("textures", textures);
        JsonObject geometry = new JsonObject();
        geometry.addProperty("default", look.geometry());
        description.add("geometry", geometry);
        JsonObject scripts = new JsonObject();
        scripts.addProperty("parent_setup", look.hideVariable() + " = 0.0;");
        if (look.wings()) {
            JsonObject animations = new JsonObject();
            animations.addProperty("default_controller", "controller.animation.elytra.default");
            animations.addProperty("default", "animation.elytra.default");
            animations.addProperty("gliding", "animation.elytra.gliding");
            animations.addProperty("sneaking", "animation.elytra.sneaking");
            animations.addProperty("sleeping", "animation.elytra.sleeping");
            animations.addProperty("swimming", "animation.elytra.swimming");
            description.add("animations", animations);
            JsonArray animate = new JsonArray();
            animate.add("default_controller");
            scripts.add("animate", animate);
        }
        description.add("scripts", scripts);
        JsonArray controllers = new JsonArray();
        controllers.add("controller.render.armor");
        description.add("render_controllers", controllers);
        JsonObject attachable = new JsonObject();
        attachable.add("description", description);
        JsonObject root = new JsonObject();
        root.addProperty("format_version", "1.10.0");
        root.add("minecraft:attachable", attachable);
        return root;
    }

    /** Bedrock's name of an equipment slot in item queries. */
    static String slotName(String slot) {
        return switch (slot) {
            case "head" -> "slot.armor.head";
            case "chest" -> "slot.armor.chest";
            case "legs" -> "slot.armor.legs";
            case "feet" -> "slot.armor.feet";
            default -> "slot.armor";
        };
    }
}
