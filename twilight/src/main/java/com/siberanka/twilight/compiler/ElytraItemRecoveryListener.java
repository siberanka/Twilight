/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the GNU LGPL v3.0 or later.
 */
package com.siberanka.twilight.compiler;

import org.bukkit.NamespacedKey;
import org.bukkit.block.Container;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;

/**
 * Defensive normalization of Elytras that left a temporarily masked Bedrock chest slot.
 *
 * <p>All mutations are on Bukkit's primary thread. LOWEST handlers repair an item before
 * its Bukkit transaction where possible; post-transaction passes handle shift-click,
 * dragging, container moves and third-party inventory GUIs. The flight bridge is the
 * sole owner of masking and does so only in the Bedrock player's real chest slot.
 *
 * <p>Not an item packet hook. External plugins that bypass Bukkit events, transform
 * the PDC away or serialize masked offline inventories cannot be fully intercepted.
 */
public final class ElytraItemRecoveryListener implements Listener {
    private final Plugin plugin;
    private final NamespacedKey marker;

    ElytraItemRecoveryListener(Plugin plugin, NamespacedKey marker) {
        this.plugin = plugin;
        this.marker = marker;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerDrop(PlayerDropItemEvent event) {
        restoreGroundItem(event.getItemDrop(), marker);
        schedulePlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSpawn(ItemSpawnEvent event) {
        restoreGroundItem(event.getEntity(), marker);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMerge(ItemMergeEvent event) {
        restoreGroundItem(event.getEntity(), marker);
        restoreGroundItem(event.getTarget(), marker);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        restoreGroundItem(event.getItem(), marker);
        if (event.getEntity() instanceof Player player) schedulePlayer(player);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHopperPickup(InventoryPickupItemEvent event) {
        restoreGroundItem(event.getItem(), marker);
        scheduleInventory(event.getInventory());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHopperMove(InventoryMoveItemEvent event) {
        // setItem(restored) can prevent Bukkit from removing the original source
        // stack (documented InventoryMoveItemEvent semantics), duplicating items.
        // Cancel this one transfer and normalize the source before the next attempt.
        if (ElytraMaskedModel.savedModel(event.getItem(), marker) != null) {
            event.setCancelled(true);
        }
        // Bukkit may already have removed the source stack at event time.
        // Wait until cancellation restores it, then normalize on the next tick.
        scheduleInventory(event.getSource());
        scheduleInventory(event.getDestination());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        // Avoid modifying the dispensed stack mid-transaction. Retry on a future
        // dispenser activation, after the source inventory is normalized.
        if (ElytraMaskedModel.savedModel(event.getItem(), marker) != null) {
            event.setCancelled(true);
            if (event.getBlock().getState() instanceof Container container) {
                scheduleInventory(container.getInventory());
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        for (ItemStack drop : event.getDrops()) ElytraMaskedModel.restore(drop, marker);
        // A keep-inventory death may retain the chest item. Repair after respawn as well.
        schedulePlayer(event.getEntity());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onOtherDeath(EntityDeathEvent event) {
        for (ItemStack drop : event.getDrops()) ElytraMaskedModel.restore(drop, marker);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        // Important for /invsee: the item is restored BEFORE Bukkit moves it to
        // an admin's cursor/inventory, not merely at the next 2-tick poll.
        ItemStack clicked = event.getCurrentItem();
        if (ElytraMaskedModel.restore(clicked, marker)) event.setCurrentItem(clicked);
        if (event.getWhoClicked() instanceof Player player) schedulePlayer(player);
        scheduleInventory(event.getView().getTopInventory());
        scheduleInventory(event.getView().getBottomInventory());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) schedulePlayer(player);
        scheduleInventory(event.getView().getTopInventory());
        scheduleInventory(event.getView().getBottomInventory());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onOpen(InventoryOpenEvent event) {
        scheduleInventory(event.getInventory());
        if (event.getPlayer() instanceof Player player) schedulePlayer(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        scheduleInventory(event.getInventory());
        if (event.getPlayer() instanceof Player player) schedulePlayer(player);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        ItemStack main = event.getMainHandItem();
        ItemStack offhand = event.getOffHandItem();
        if (ElytraMaskedModel.restore(main, marker)) event.setMainHandItem(main);
        if (ElytraMaskedModel.restore(offhand, marker)) event.setOffHandItem(offhand);
        schedulePlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        schedulePlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        schedulePlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        restorePlayer(event.getPlayer(), marker, true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onKick(PlayerKickEvent event) {
        if (!event.isCancelled()) restorePlayer(event.getPlayer(), marker, true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        for (org.bukkit.entity.Entity entity : event.getChunk().getEntities()) {
            restoreEntity(entity, marker);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityInteraction(PlayerInteractEntityEvent event) {
        org.bukkit.entity.Entity target = event.getRightClicked();
        // Item frames and armor stands do not produce InventoryClickEvent.
        plugin.getServer().getScheduler().runTask(plugin, () -> restoreEntity(target, marker));
        schedulePlayer(event.getPlayer());
    }

    private static void restoreEntity(org.bukkit.entity.Entity entity, NamespacedKey marker) {
        if (entity instanceof Item dropped) {
            restoreGroundItem(dropped, marker);
        } else if (entity instanceof ItemFrame frame) {
            ItemStack displayed = frame.getItem();
            if (ElytraMaskedModel.restore(displayed, marker)) frame.setItem(displayed, false);
        } else if (entity instanceof ArmorStand stand && stand.getEquipment() != null) {
            var equipment = stand.getEquipment();
            ItemStack[] armor = equipment.getArmorContents();
            boolean changed = false;
            for (ItemStack stack : armor) changed |= ElytraMaskedModel.restore(stack, marker);
            if (changed) equipment.setArmorContents(armor);
            ItemStack mainHand = equipment.getItemInMainHand();
            if (ElytraMaskedModel.restore(mainHand, marker)) equipment.setItemInMainHand(mainHand);
            ItemStack offhand = equipment.getItemInOffHand();
            if (ElytraMaskedModel.restore(offhand, marker)) equipment.setItemInOffHand(offhand);
        }
    }

    private void schedulePlayer(Player player) {
        plugin.getServer().getScheduler().runTask(plugin,
                () -> { if (player.isOnline()) restorePlayer(player, marker, false); });
    }

    private void scheduleInventory(Inventory inventory) {
        plugin.getServer().getScheduler().runTask(plugin,
                () -> restoreInventory(inventory, marker));
    }

    static void restoreGroundItem(Item entity, NamespacedKey marker) {
        ItemStack stack = entity.getItemStack();
        if (ElytraMaskedModel.restore(stack, marker)) entity.setItemStack(stack);
    }

    static void restoreLoadedWorldItems(Plugin plugin, NamespacedKey marker) {
        for (org.bukkit.World world : plugin.getServer().getWorlds()) {
            for (Item item : world.getEntitiesByClass(Item.class)) restoreGroundItem(item, marker);
            for (ItemFrame frame : world.getEntitiesByClass(ItemFrame.class)) restoreEntity(frame, marker);
            for (ArmorStand stand : world.getEntitiesByClass(ArmorStand.class)) restoreEntity(stand, marker);
        }
    }

    /** Repair inventory items, never the equipped chest slot of a connected Bedrock player. */
    static void restorePlayer(Player player, NamespacedKey marker, boolean includeChest) {
        PlayerInventory inventory = player.getInventory();
        restoreInventory(inventory, marker);
        if (includeChest) {
            ItemStack chest = inventory.getChestplate();
            if (ElytraMaskedModel.restore(chest, marker)) inventory.setChestplate(chest);
        }
        ItemStack cursor = player.getItemOnCursor();
        if (ElytraMaskedModel.restore(cursor, marker)) player.setItemOnCursor(cursor);
        Inventory opened = player.getOpenInventory().getTopInventory();
        if (opened != inventory) restoreInventory(opened, marker);
        restoreInventory(player.getEnderChest(), marker);
    }

    static void restoreInventory(Inventory inventory, NamespacedKey marker) {
        if (inventory instanceof PlayerInventory player) {
            ItemStack[] storage = player.getStorageContents();
            boolean changed = false;
            for (ItemStack stack : storage) changed |= ElytraMaskedModel.restore(stack, marker);
            if (changed) player.setStorageContents(storage);
            ItemStack offhand = player.getItemInOffHand();
            if (ElytraMaskedModel.restore(offhand, marker)) player.setItemInOffHand(offhand);
            ItemStack helmet = player.getHelmet();
            if (ElytraMaskedModel.restore(helmet, marker)) player.setHelmet(helmet);
            ItemStack leggings = player.getLeggings();
            if (ElytraMaskedModel.restore(leggings, marker)) player.setLeggings(leggings);
            ItemStack boots = player.getBoots();
            if (ElytraMaskedModel.restore(boots, marker)) player.setBoots(boots);
            // Never unmask chest here: the flight bridge manages this one slot.
            return;
        }
        ItemStack[] contents = inventory.getContents();
        for (int index = 0; index < contents.length; index++) {
            if (ElytraMaskedModel.restore(contents[index], marker)) {
                inventory.setItem(index, contents[index]);
            }
        }
    }
}
