/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the GNU LGPL v3.0 or later.
 */
package com.siberanka.twilight.compiler;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bedrock Elytra flight compatibility. The Bukkit chest item is temporarily masked ONLY
 * for a connected Bedrock player. All other inventory locations are repaired, and the
 * original item_model is stored as persistent item data for crash/restart recovery.
 *
 * <p>This is a server-side compromise: Java observers inspecting a worn Bedrock player's
 * chest slot may see the vanilla icon while it is worn. A Bukkit-only implementation
 * cannot provide different chest-slot contents to Java and Bedrock simultaneously.
 */
public final class ElytraVanillaFlightBridge {
    private static final boolean ENABLED = !Boolean.getBoolean("twilight.elytraBridge.disabled");
    private static final Path MAPPING = Path.of("plugins", "Geyser-Spigot", "custom_mappings", "twilight_item_mappings.json");
    private static final Map<UUID, Integer> LAST_VARIANT = new ConcurrentHashMap<>();
    private static volatile Plugin plugin;
    private static volatile BukkitTask task;
    private static volatile NamespacedKey modelMarker;
    private static volatile Map<String, Integer> variants = Map.of();
    private static volatile long lastModified = Long.MIN_VALUE;
    private static long ticks;

    private ElytraVanillaFlightBridge() {
    }

    public static synchronized void start(Plugin owningPlugin) {
        if (task != null) return;
        plugin = Objects.requireNonNull(owningPlugin, "owningPlugin");
        modelMarker = new NamespacedKey(owningPlugin, ElytraMaskedModel.MARKER);
        // Register repair hooks even when masking is disabled, to recover old masked items.
        owningPlugin.getServer().getPluginManager().registerEvents(
                new ElytraItemRecoveryListener(owningPlugin, modelMarker), owningPlugin);
        task = owningPlugin.getServer().getScheduler().runTaskTimer(
                owningPlugin, ElytraVanillaFlightBridge::tick, 10L, 2L);
        owningPlugin.getServer().getScheduler().runTask(owningPlugin,
                () -> ElytraItemRecoveryListener.restoreLoadedWorldItems(owningPlugin, modelMarker));
        owningPlugin.getLogger().info(ENABLED
                ? "Elytra native-flight compatibility enabled: chest-only server masking with item recovery."
                : "Elytra item recovery enabled; no new chest masking.");
    }

    public static synchronized void stop() {
        BukkitTask scheduled = task;
        task = null;
        if (scheduled != null) scheduled.cancel();
        Plugin current = plugin;
        NamespacedKey marker = modelMarker;
        if (current != null && marker != null) {
            for (Player player : current.getServer().getOnlinePlayers()) {
                ElytraItemRecoveryListener.restorePlayer(player, marker, true);
            }
            // Items that were already on the ground must not retain a stale masked model.
            ElytraItemRecoveryListener.restoreLoadedWorldItems(current, marker);
        }
        LAST_VARIANT.clear();
        modelMarker = null;
        plugin = null;
    }

    private static void tick() {
        Plugin currentPlugin = plugin;
        NamespacedKey marker = modelMarker;
        if (currentPlugin == null || marker == null || !currentPlugin.isEnabled()) return;
        try {
            Map<String, Integer> currentVariants = registry();
            Set<UUID> online = new HashSet<>();
            boolean geyserAvailable = geyserInstalled(currentPlugin);
            for (Player player : currentPlugin.getServer().getOnlinePlayers()) {
                UUID uuid = player.getUniqueId();
                online.add(uuid);
                // Repair every other position, including a container opened by another player.
                ElytraItemRecoveryListener.restorePlayer(player, marker, false);
                boolean bedrock = geyserAvailable && bedrockConnection(uuid);
                int variant = updateChest(player.getInventory(), marker, currentVariants,
                        ENABLED && bedrock);
                publishVariant(uuid, variant, geyserAvailable);
            }
            LAST_VARIANT.keySet().removeIf(uuid -> !online.contains(uuid));
            ticks++;
        } catch (RuntimeException | LinkageError failure) {
            currentPlugin.getLogger().warning("Elytra native-flight compatibility update failed: " + failure);
        }
    }

    /** For unit tests and the event listener: change ONLY the actual chest slot. */
    static int updateChest(PlayerInventory inventory, NamespacedKey marker,
                           Map<String, Integer> mapping, boolean maskForBedrock) {
        ItemStack chest = inventory.getChestplate();
        if (chest == null || chest.getType() != Material.ELYTRA) return 0;
        String saved = ElytraMaskedModel.savedModel(chest, marker);
        if (saved != null) {
            int variant = variantForModel(saved, mapping);
            if (!maskForBedrock || variant == 0) {
                if (ElytraMaskedModel.restore(chest, marker)) inventory.setChestplate(chest);
                if (!maskForBedrock) return variant;
                saved = null;
            } else {
                return variant;
            }
        }
        ItemMeta meta = chest.getItemMeta();
        if (meta == null || meta.getItemModel() == null) return 0;
        String original = meta.getItemModel().toString();
        int variant = variantForModel(original, mapping);
        if (maskForBedrock && variant != 0 && ElytraMaskedModel.mask(chest, marker)) {
            inventory.setChestplate(chest);
        }
        return variant;
    }

    public static int variantForModel(String model, Map<String, Integer> mapping) {
        if (model == null || mapping == null) return 0;
        return mapping.getOrDefault(model, 0);
    }

    private static boolean bedrockConnection(UUID uuid) {
        try {
            return ElytraGeyserApiAdapter.hasConnection(uuid);
        } catch (RuntimeException | LinkageError failure) {
            return false;
        }
    }

    private static void publishVariant(UUID uuid, int variant, boolean geyserAvailable) {
        Integer old = LAST_VARIANT.get(uuid);
        if (!geyserAvailable) {
            LAST_VARIANT.remove(uuid);
            return;
        }
        if (variant == 0) {
            if (old != null) {
                LAST_VARIANT.remove(uuid);
                sendVariant(uuid, 0);
            }
        } else {
            LAST_VARIANT.put(uuid, variant);
            if (!Objects.equals(old, variant) || ticks % 10L == 0L) sendVariant(uuid, variant);
        }
    }

    private static boolean geyserInstalled(Plugin currentPlugin) {
        Plugin geyser = currentPlugin.getServer().getPluginManager().getPlugin("Geyser-Spigot");
        return geyser != null && geyser.isEnabled();
    }

    private static void sendVariant(UUID playerUuid, int variant) {
        try {
            ElytraGeyserApiAdapter.sendVariant(playerUuid, variant);
        } catch (RuntimeException | LinkageError failure) {
            Plugin currentPlugin = plugin;
            if (currentPlugin != null) currentPlugin.getLogger().warning("Geyser Elytra VARIANT unavailable: " + failure);
        }
    }

    private static Map<String, Integer> registry() {
        try {
            if (!Files.isRegularFile(MAPPING)) return Map.of();
            long modified = Files.getLastModifiedTime(MAPPING).toMillis();
            if (modified == lastModified) return variants;
            Map<String, Object> root = object(BedrockAnimationBridge.Json.read(Files.readAllBytes(MAPPING)));
            Map<String, Object> items = object(root.get("items"));
            TreeSet<String> models = new TreeSet<>();
            if (items.get("minecraft:elytra") instanceof Iterable<?> entries) {
                for (Object entry : entries) {
                    Object name = object(entry).get("model");
                    if (name instanceof String model) models.add(model);
                }
            }
            Map<String, Integer> indexed = new HashMap<>();
            for (String model : models) indexed.put(model, indexed.size() + 1);
            variants = Collections.unmodifiableMap(indexed);
            lastModified = modified;
            Plugin currentPlugin = plugin;
            if (currentPlugin != null) currentPlugin.getLogger().info(
                    "Elytra Bridge loaded " + indexed.size() + " custom models from " + MAPPING);
            return variants;
        } catch (Exception failure) {
            Plugin currentPlugin = plugin;
            if (currentPlugin != null) currentPlugin.getLogger().warning("Elytra mapping invalid: " + failure);
            return variants;
        }
    }

    private static Map<String, Object> object(Object value) {
        if (!(value instanceof Map<?, ?> map)) return Map.of();
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() instanceof String key) result.put(key, entry.getValue());
        }
        return result;
    }
}
