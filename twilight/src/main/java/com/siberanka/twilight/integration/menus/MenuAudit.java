/* Copyright (c) 2026 siberanka. Twilight License (PolyForm Noncommercial 1.0.0 + terms), see LICENSE. */
package com.siberanka.twilight.integration.menus;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.siberanka.twilight.integration.heads.PlayerHeads;
import com.siberanka.twilight.integration.text.TitleAnalysis;
import com.siberanka.twilight.text.TextLayoutTable;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * The menus this server's players open, whatever plugin made them, as Bedrock players get them: which titles stay
 * plain, which the layout places (images, moves), which still carry offset tags a plugin should have replaced, which
 * use characters the pack has no image for, and how many player heads and custom items they show. The live menus
 * are the truth a configuration file can only hint at (placeholders, plugins that build menus in code, translated
 * titles), so they are recorded as they open; {@code /twilight menus} reports them and writes
 * {@code reports/menus.json}.
 */
public final class MenuAudit implements Listener, AutoCloseable {
    /** Distinct menus kept (by plugin, size and title). */
    static final int MAX_MENUS = 1000;
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path file;
    private final Supplier<TextLayoutTable> table;
    private final Map<String, Menu> menus = new ConcurrentHashMap<>();

    /** One menu: where it came from, what its title holds and what it shows. */
    record Menu(String plugin, String type, int size, Object title, String text, int heads, int customItems,
                int opened, Instant last) {
        Menu seen(int heads, int customItems) {
            return new Menu(plugin, type, size, title, text, Math.max(this.heads, heads), Math.max(this.customItems, customItems),
                    opened + 1, Instant.now());
        }
    }

    private MenuAudit(Path file, Supplier<TextLayoutTable> table) {
        this.file = file;
        this.table = table;
    }

    /** @param table the layout table of the pack Bedrock players get (null when there is none) */
    public static MenuAudit start(Plugin plugin, Path file, Supplier<TextLayoutTable> table) {
        MenuAudit audit = new MenuAudit(file, table);
        org.bukkit.Bukkit.getPluginManager().registerEvents(audit, plugin);
        return audit;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        try {
            Inventory inventory = event.getInventory();
            Object title = title(event);
            if (title == null) return;
            String text = plainText(title);
            String owner = owner(inventory);
            String key = owner + '|' + inventory.getType() + '|' + inventory.getSize() + '|' + text;
            int heads = 0, custom = 0;
            for (ItemStack stack : inventory.getContents()) {
                if (stack == null || stack.getType().isAir()) continue;
                if (stack.getType() == Material.PLAYER_HEAD) {
                    List<String> found = new ArrayList<>();
                    PlayerHeads.head(stack, found);
                    if (!found.isEmpty()) heads++;
                }
                if (custom(stack)) custom++;
            }
            int h = heads, c = custom;
            Menu menu = menus.get(key);
            if (menu != null) menus.put(key, menu.seen(h, c));
            else if (menus.size() < MAX_MENUS) {
                menus.put(key, new Menu(owner, inventory.getType().name(), inventory.getSize(), title, text, h, c, 1, Instant.now()));
            }
        } catch (RuntimeException | LinkageError unreadable) {
            // A menu a plugin opened half built: nothing to record.
        }
    }

    /** Paper's title component, or the legacy title string on servers without one. */
    private static Object title(InventoryOpenEvent event) {
        try {
            return event.getView().title();
        } catch (LinkageError | RuntimeException spigot) {
            return null;
        }
    }

    private static String plainText(Object title) {
        try {
            return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize((net.kyori.adventure.text.Component) title);
        } catch (LinkageError | RuntimeException absent) {
            return String.valueOf(title);
        }
    }

    /** The plugin whose class holds the inventory (menu plugins use their own holders), or the inventory type. */
    private static String owner(Inventory inventory) {
        InventoryHolder holder;
        try {
            holder = inventory.getHolder(false);
        } catch (LinkageError | RuntimeException spigot) {
            holder = inventory.getHolder();
        }
        if (holder == null) return "unknown";
        try {
            return JavaPlugin.getProvidingPlugin(holder.getClass()).getName();
        } catch (IllegalArgumentException | IllegalStateException server) {
            return "server";
        }
    }

    private static boolean custom(ItemStack stack) {
        if (!stack.hasItemMeta()) return false;
        var meta = stack.getItemMeta();
        try {
            if (meta.hasItemModel()) return true;
        } catch (LinkageError | RuntimeException older) {
            // Before 1.21.4.
        }
        return meta.hasCustomModelData();
    }

    /** Summary lines for the command and the full report as JSON (written to {@code reports/menus.json}). */
    public List<String> report() throws IOException {
        TextLayoutTable layout = table.get();
        JsonArray entries = new JsonArray();
        int plain = 0, laidOut = 0, tagged = 0, missing = 0, heads = 0;
        Map<String, Integer> taggedBy = new LinkedHashMap<>(), missingBy = new LinkedHashMap<>();
        List<Menu> sorted = new ArrayList<>(menus.values());
        sorted.sort(Comparator.comparing(Menu::plugin).thenComparing(Menu::text));
        for (Menu menu : sorted) {
            TitleAnalysis analysis;
            try {
                analysis = TitleAnalysis.of(layout, menu.title());
            } catch (ReflectiveOperationException | RuntimeException failure) {
                continue;
            }
            if (analysis.plain()) plain++;
            else laidOut++;
            if (analysis.tags() > 0) {
                tagged++;
                taggedBy.merge(menu.plugin(), 1, Integer::sum);
            }
            if (!analysis.missing().isEmpty()) {
                missing++;
                missingBy.merge(menu.plugin(), 1, Integer::sum);
            }
            heads += menu.heads();
            JsonObject entry = new JsonObject();
            entry.addProperty("plugin", menu.plugin());
            entry.addProperty("type", menu.type());
            entry.addProperty("size", menu.size());
            entry.addProperty("title", analysis.text());
            entry.addProperty("bedrock", analysis.plain() ? "plain: shown as sent, at Java's position"
                    : "laid out: images and moves placed like Java");
            entry.addProperty("images", analysis.glyphs());
            entry.addProperty("moves", analysis.moves());
            entry.addProperty("unreplaced_offset_tags", analysis.tags());
            JsonArray absent = new JsonArray();
            analysis.missing().forEach(absent::add);
            entry.add("characters_without_image", absent);
            entry.addProperty("player_heads", menu.heads());
            entry.addProperty("custom_items", menu.customItems());
            entry.addProperty("opened", menu.opened());
            entry.addProperty("last_opened", menu.last().toString());
            entries.add(entry);
        }
        JsonObject root = new JsonObject();
        root.addProperty("generated", Instant.now().toString());
        root.addProperty("layout_table", layout != null);
        root.add("menus", entries);
        Files.createDirectories(file.toAbsolutePath().getParent());
        Files.writeString(file, JSON.toJson(root), StandardCharsets.UTF_8);

        List<String> lines = new ArrayList<>();
        lines.add("Menus opened since start: " + sorted.size() + " (" + plain + " plain titles, " + laidOut
                + " laid out" + (layout == null ? "; no layout table in the pack yet" : "") + ").");
        if (tagged > 0) lines.add(tagged + " title(s) still carry offset tags (Java players see them as text too): " + taggedBy
                + ". Update the plugin that should replace them; Bedrock already places them.");
        if (missing > 0) lines.add(missing + " title(s) use characters the pack has no image for: " + missingBy
                + ". Their font is missing from the scanned sources.");
        if (heads > 0) lines.add(heads + " player head(s) with a skin shown in menus.");
        lines.add("Details: " + file.getFileName() + " in plugins/Twilight/reports/.");
        return lines;
    }

    /** Forgets the recorded menus. */
    public void reset() {
        menus.clear();
    }

    @Override public void close() {
        HandlerList.unregisterAll(this);
    }
}
