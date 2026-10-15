package com.siberanka.twilight.integration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderCommandClassifierTest {
    @Test
    void recognizesPackMutationsFromConsolePlayersAndNamespacedCommands() {
        assertEquals("ItemsAdder:iareload", ProviderCommandClassifier.classify("/iareload").orElseThrow().toString());
        assertEquals("ItemsAdder:iazip", ProviderCommandClassifier.classify("iazip").orElseThrow().toString());
        assertEquals("Oraxen:oraxen pack", ProviderCommandClassifier.classify("/oraxen pack generate").orElseThrow().toString());
        assertEquals("Nexo:nexo reload", ProviderCommandClassifier.classify("nexo:nexo reload all").orElseThrow().toString());
        assertEquals("CraftEngine:ce reload", ProviderCommandClassifier.classify("/ce reload").orElseThrow().toString());
        assertEquals("ModelEngine:meg reload", ProviderCommandClassifier.classify(" /meg reload ").orElseThrow().toString());
    }

    @Test
    void ignoresReadOnlyAndUnrelatedCommands() {
        assertTrue(ProviderCommandClassifier.classify("/oraxen version").isEmpty());
        assertTrue(ProviderCommandClassifier.classify("/nexo give sword").isEmpty());
        assertTrue(ProviderCommandClassifier.classify("/twilight reload").isEmpty());
        assertTrue(ProviderCommandClassifier.classify("/reload").isEmpty());
        assertTrue(ProviderCommandClassifier.classify(" ").isEmpty());
    }
}
