package com.siberanka.twilight.source;

import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderRegistryEntriesTest {
    @Test
    void acceptsOraxenArrayAndMapRegistryShapes() {
        assertEquals(List.of("oraxen:bow", "oraxen:rod"),
                ProviderRegistryEntries.ids(new String[]{"oraxen:bow", "oraxen:rod"}).orElseThrow());

        LinkedHashMap<String, Object> craftEngine = new LinkedHashMap<>();
        craftEngine.put("craftengine:bow", new Object());
        craftEngine.put("craftengine:rod", new Object());
        assertEquals(List.of("craftengine:bow", "craftengine:rod"),
                ProviderRegistryEntries.ids(craftEngine).orElseThrow());
    }

    @Test
    void acceptsVersionTolerantIteratorAndStreamShapes() {
        Iterator<String> iterator = List.of("nexo:a", "nexo:b").iterator();
        assertEquals(List.of("nexo:a", "nexo:b"), ProviderRegistryEntries.ids(iterator).orElseThrow());
        assertEquals(List.of("itemsadder:a", "itemsadder:b"),
                ProviderRegistryEntries.ids(Stream.of("itemsadder:a", "itemsadder:b")).orElseThrow());
        assertTrue(ProviderRegistryEntries.ids("unsupported").isEmpty());
    }
}
