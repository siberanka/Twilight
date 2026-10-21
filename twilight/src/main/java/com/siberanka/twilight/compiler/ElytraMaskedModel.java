/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the GNU LGPL v3.0 or later.
 */
package com.siberanka.twilight.compiler;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Reversible server-side Elytra model substitution. Only the chest equipment slot of a
 * connected Bedrock player may retain a masked stack. All other locations are repaired.
 *
 * <p>The original model is stored on the ItemStack itself so the repair survives item copies,
 * inventory moves and server restarts. This is NOT a per-client packet substitution.
 */
final class ElytraMaskedModel {
    static final String MARKER = "elytra_original_item_model";

    private ElytraMaskedModel() {
    }

    static String savedModel(ItemStack stack, NamespacedKey marker) {
        if (stack == null || stack.getType() != Material.ELYTRA) return null;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return null;
        String saved = meta.getPersistentDataContainer().get(marker, PersistentDataType.STRING);
        return saved == null || saved.isBlank() ? null : saved;
    }

    /** Masks a custom Elytra in the real chest slot. Returns false if the model cannot be saved. */
    static boolean mask(ItemStack stack, NamespacedKey marker) {
        if (stack == null || stack.getType() != Material.ELYTRA) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || meta.getItemModel() == null) return false;
        String model = meta.getItemModel().toString();
        if (NamespacedKey.fromString(model) == null) return false;
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(marker, PersistentDataType.STRING, model);
        meta.setItemModel(null);
        return stack.setItemMeta(meta);
    }

    /**
     * Restores a masked Elytra. Idempotent. If another plugin assigned a new item_model,
     * preserve that newer model and simply discard the obsolete marker.
     */
    static boolean restore(ItemStack stack, NamespacedKey marker) {
        if (stack == null || stack.getType() != Material.ELYTRA) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer data = meta.getPersistentDataContainer();
        String original = data.get(marker, PersistentDataType.STRING);
        if (original == null || original.isBlank()) return false;
        NamespacedKey key = NamespacedKey.fromString(original);
        if (key == null) return false; // Preserve bad data rather than silently deleting it.
        if (meta.getItemModel() == null) meta.setItemModel(key);
        data.remove(marker);
        return stack.setItemMeta(meta);
    }
}
