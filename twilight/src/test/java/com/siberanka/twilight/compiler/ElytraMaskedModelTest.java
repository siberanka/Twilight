/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the GNU LGPL v3.0 or later.
 */
package com.siberanka.twilight.compiler;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Masked custom Elytras can only remain masked in the connected Bedrock player's chest slot. */
class ElytraMaskedModelTest {
    private static final NamespacedKey MARKER = NamespacedKey.fromString("twilight:elytra_original_item_model");
    private static final NamespacedKey CUSTOM = NamespacedKey.fromString("elytras:rainbow_elytra");

    @Test
    void chestMaskAndDropRestoreAreLossless() {
        FakeMeta meta = new FakeMeta(CUSTOM);
        ItemStack chest = item(meta);
        AtomicReference<ItemStack> equipped = new AtomicReference<>(chest);
        AtomicInteger writes = new AtomicInteger();
        PlayerInventory inventory = inventory(equipped, writes);
        Map<String, Integer> registry = Map.of(CUSTOM.toString(), 7);

        assertEquals(7, ElytraVanillaFlightBridge.updateChest(inventory, MARKER, registry, true));
        assertNull(meta.model.get());
        assertEquals(CUSTOM.toString(), meta.saved.get(MARKER));
        assertEquals(1, writes.get());
        assertEquals(7, ElytraVanillaFlightBridge.updateChest(inventory, MARKER, registry, true));
        assertEquals(1, writes.get(), "A repeated tick must not rewrite chest equipment");

        assertTrue(ElytraMaskedModel.restore(equipped.get(), MARKER));
        assertEquals(CUSTOM, meta.model.get());
        assertFalse(meta.saved.containsKey(MARKER));
        assertFalse(ElytraMaskedModel.restore(equipped.get(), MARKER));
    }

    @Test
    void javaPlayerNeverHasMaskedChestAndOldItemsAreRecovered() {
        FakeMeta meta = new FakeMeta(CUSTOM);
        ItemStack chest = item(meta);
        PlayerInventory inventory = inventory(new AtomicReference<>(chest), new AtomicInteger());
        Map<String, Integer> registry = Map.of(CUSTOM.toString(), 7);

        assertEquals(7, ElytraVanillaFlightBridge.updateChest(inventory, MARKER, registry, false));
        assertEquals(CUSTOM, meta.model.get());
        assertTrue(meta.saved.isEmpty());

        assertTrue(ElytraMaskedModel.mask(chest, MARKER));
        assertEquals(7, ElytraVanillaFlightBridge.updateChest(inventory, MARKER, registry, false));
        assertEquals(CUSTOM, meta.model.get());
        assertTrue(meta.saved.isEmpty());
    }

    @Test
    void vanillaElytrasAndNewerModelsArePreserved() {
        FakeMeta vanillaMeta = new FakeMeta(null);
        ItemStack vanilla = item(vanillaMeta);
        assertFalse(ElytraMaskedModel.mask(vanilla, MARKER));
        assertFalse(ElytraMaskedModel.restore(vanilla, MARKER));

        FakeMeta meta = new FakeMeta(CUSTOM);
        ItemStack custom = item(meta);
        assertTrue(ElytraMaskedModel.mask(custom, MARKER));
        NamespacedKey replacement = NamespacedKey.fromString("other:changed_by_plugin");
        meta.model.set(replacement);
        assertTrue(ElytraMaskedModel.restore(custom, MARKER));
        assertEquals(replacement, meta.model.get());
        assertTrue(meta.saved.isEmpty());
    }

    @Test
    void repairsCursorStorageAndContainerWithoutUnmaskingEquippedChest() {
        ItemStack chest = masked();
        ItemStack storage = masked();
        ItemStack cursor = masked();
        ItemStack containerItem = masked();
        ItemStack offhand = masked();
        PlayerInventory playerInventory = mock(PlayerInventory.class);
        when(playerInventory.getChestplate()).thenReturn(chest);
        when(playerInventory.getStorageContents()).thenReturn(new ItemStack[]{storage});
        when(playerInventory.getItemInOffHand()).thenReturn(offhand);

        Inventory container = mock(Inventory.class);
        when(container.getContents()).thenReturn(new ItemStack[]{containerItem});
        InventoryView view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(container);
        Player player = mock(Player.class);
        when(player.getInventory()).thenReturn(playerInventory);
        when(player.getItemOnCursor()).thenReturn(cursor);
        when(player.getOpenInventory()).thenReturn(view);
        when(player.getEnderChest()).thenReturn(container);

        ElytraItemRecoveryListener.restorePlayer(player, MARKER, false);
        assertEquals(CUSTOM.toString(), ElytraMaskedModel.savedModel(chest, MARKER),
                "The equipped Bedrock chest remains masked");
        for (ItemStack stack : new ItemStack[]{storage, offhand, cursor, containerItem}) {
            assertNull(ElytraMaskedModel.savedModel(stack, MARKER));
            assertEquals(CUSTOM, stack.getItemMeta().getItemModel());
        }
        verify(playerInventory).setStorageContents(any(ItemStack[].class));
        verify(playerInventory).setItemInOffHand(offhand);
        verify(player).setItemOnCursor(cursor);
        verify(container).setItem(0, containerItem);

        ElytraItemRecoveryListener.restorePlayer(player, MARKER, true);
        assertEquals(CUSTOM, chest.getItemMeta().getItemModel());
        verify(playerInventory).setChestplate(chest);
    }

    @Test
    void restoresDroppedEntityExactlyOnce() {
        ItemStack stack = masked();
        Item entity = mock(Item.class);
        when(entity.getItemStack()).thenReturn(stack);
        ElytraItemRecoveryListener.restoreGroundItem(entity, MARKER);
        ElytraItemRecoveryListener.restoreGroundItem(entity, MARKER);
        verify(entity, times(1)).setItemStack(stack);
        assertEquals(CUSTOM, stack.getItemMeta().getItemModel());
    }

    @Test
    void adminInventoryClickRestoresItemBeforeTheTransfer() {
        ItemStack equipped = masked();
        InventoryClickEvent click = mock(InventoryClickEvent.class);
        when(click.getCurrentItem()).thenReturn(equipped);
        InventoryView view = mock(InventoryView.class);
        when(click.getView()).thenReturn(view);
        when(view.getTopInventory()).thenReturn(mock(Inventory.class));
        when(view.getBottomInventory()).thenReturn(mock(Inventory.class));
        AtomicInteger deferred = new AtomicInteger();
        Plugin plugin = pluginWithScheduler(deferred);

        new ElytraItemRecoveryListener(plugin, MARKER).onClick(click);
        assertNull(ElytraMaskedModel.savedModel(equipped, MARKER));
        assertEquals(CUSTOM, equipped.getItemMeta().getItemModel());
        verify(click).setCurrentItem(equipped);
        assertEquals(2, deferred.get(), "Recheck both viewed inventories after the transaction");
    }

    @Test
    void deathDropsAreRestoredBeforeTheyBecomeEntities() {
        ItemStack dropped = masked();
        PlayerDeathEvent death = mock(PlayerDeathEvent.class);
        when(death.getDrops()).thenReturn(new java.util.ArrayList<>(java.util.List.of(dropped)));
        new ElytraItemRecoveryListener(pluginWithScheduler(new AtomicInteger()), MARKER).onDeath(death);
        assertEquals(CUSTOM, dropped.getItemMeta().getItemModel());
        assertNull(ElytraMaskedModel.savedModel(dropped, MARKER));
    }

    @Test
    void hopperCancelsMaskedTransferWithoutOverwritingTransactionStack() {
        ItemStack sourceStack = masked();
        InventoryMoveItemEvent move = mock(InventoryMoveItemEvent.class);
        when(move.getItem()).thenReturn(sourceStack);
        new ElytraItemRecoveryListener(pluginWithScheduler(new AtomicInteger()), MARKER).onHopperMove(move);
        verify(move).setCancelled(true);
        verify(move, never()).setItem(any(ItemStack.class));
    }

    private static Plugin pluginWithScheduler(AtomicInteger calls) {
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        // Do not execute callbacks here: Bukkit runs them next tick, not inside the event.
        when(scheduler.runTask(any(Plugin.class), any(Runnable.class))).thenAnswer(invocation -> {
            calls.incrementAndGet();
            return null;
        });
        Server server = mock(Server.class);
        when(server.getScheduler()).thenReturn(scheduler);
        Plugin plugin = mock(Plugin.class);
        when(plugin.getServer()).thenReturn(server);
        return plugin;
    }

    private static ItemStack item(FakeMeta meta) {
        // Do not construct a real ItemStack: it may query Bukkit's ItemFactory without a running server.
        ItemStack stack = mock(ItemStack.class);
        AtomicReference<ItemMeta> current = new AtomicReference<>(meta.itemMeta);
        when(stack.getType()).thenReturn(Material.ELYTRA);
        when(stack.getItemMeta()).thenAnswer(invocation -> current.get());
        when(stack.setItemMeta(any(ItemMeta.class))).thenAnswer(invocation -> {
            current.set(invocation.getArgument(0));
            return true;
        });
        return stack;
    }

    private static ItemStack masked() {
        ItemStack stack = item(new FakeMeta(CUSTOM));
        assertTrue(ElytraMaskedModel.mask(stack, MARKER));
        return stack;
    }

    private static PlayerInventory inventory(AtomicReference<ItemStack> chest, AtomicInteger writes) {
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(inventory.getChestplate()).thenAnswer(invocation -> chest.get());
        doAnswer(invocation -> {
            chest.set(invocation.getArgument(0));
            writes.incrementAndGet();
            return null;
        }).when(inventory).setChestplate(any(ItemStack.class));
        return inventory;
    }

    private static final class FakeMeta {
        private final AtomicReference<NamespacedKey> model;
        private final Map<NamespacedKey, String> saved = new HashMap<>();
        private final ItemMeta itemMeta;

        FakeMeta(NamespacedKey initialModel) {
            model = new AtomicReference<>(initialModel);
            PersistentDataContainer container = (PersistentDataContainer) Proxy.newProxyInstance(
                    PersistentDataContainer.class.getClassLoader(),
                    new Class<?>[]{PersistentDataContainer.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "get" -> saved.get(args[0]);
                        case "set" -> { saved.put((NamespacedKey) args[0], (String) args[2]); yield null; }
                        case "remove" -> { saved.remove(args[0]); yield null; }
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
            itemMeta = (ItemMeta) Proxy.newProxyInstance(ItemMeta.class.getClassLoader(),
                    new Class<?>[]{ItemMeta.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "getItemModel" -> model.get();
                        case "setItemModel" -> { model.set((NamespacedKey) args[0]); yield null; }
                        case "getPersistentDataContainer" -> container;
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }
    }
}
