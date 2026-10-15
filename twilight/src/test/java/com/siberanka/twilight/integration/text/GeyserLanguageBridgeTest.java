package com.siberanka.twilight.integration.text;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Java's order: pack locale, vanilla locale, pack English, vanilla English (Geyser falls back to en_us). */
class GeyserLanguageBridgeTest {
    @Test
    void overlaysPackStringsOncePerLoadedMap() {
        Map<String, Map<String, String>> loaded = new HashMap<>();
        loaded.put("en_us", new HashMap<>(Map.of("container.inventory", "Inventory", "container.chest", "Chest")));
        loaded.put("tr_tr", new HashMap<>(Map.of("container.inventory", "Envanter")));
        Map<String, Map<String, String>> packs = Map.of(
                "en_us", Map.of("container.inventory", " ", "item.dnt.relic", "Relic"),
                "tr_tr", Map.of("item.dnt.relic", "Kalinti"));
        Set<Map<String, String>> applied = Collections.newSetFromMap(new IdentityHashMap<>());
        assertTrue(GeyserLanguageBridge.overlay(loaded, packs, "en_us", applied));
        assertTrue(GeyserLanguageBridge.overlay(loaded, packs, "tr_tr", applied));
        assertFalse(GeyserLanguageBridge.overlay(loaded, packs, "tr_tr", applied), "applied once");
        // Turkish Java players keep the vanilla Turkish label (vanilla tr_tr beats pack en_us) ...
        assertEquals("Envanter", loaded.get("tr_tr").get("container.inventory"));
        // ... English ones see the pack's blank label, and datapack names come from the packs.
        assertEquals(" ", loaded.get("en_us").get("container.inventory"));
        assertEquals("Kalinti", loaded.get("tr_tr").get("item.dnt.relic"));
        assertEquals("Chest", loaded.get("en_us").get("container.chest"));
        // A reloaded locale (new map) is overlaid again.
        loaded.put("tr_tr", new HashMap<>(Map.of("container.inventory", "Envanter")));
        assertTrue(GeyserLanguageBridge.overlay(loaded, packs, "tr_tr", applied));
        assertFalse(GeyserLanguageBridge.overlay(loaded, packs, "de_de", applied), "no pack strings, nothing loaded");
    }
}
