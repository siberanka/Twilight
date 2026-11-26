package com.siberanka.twilight.integration.display;

import com.siberanka.twilight.integration.display.EntityTypeRegistry.Strategy;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/** The strategy choice and the registry edits, against fakes shaped like the two Geyser generations. */
class EntityTypeRegistryTest {
    // ---- fakes -----------------------------------------------------------------------------------------------

    /** Geyser up to build 1263: Registries.JAVA_ENTITY_TYPES is a registry wrapping a map. */
    public static final class LegacyOwner {
        public static final LegacyRegistry JAVA_ENTITY_TYPES = new LegacyRegistry();
    }

    public static final class LegacyRegistry {
        private final Map<Object, Object> map = new HashMap<>();
        public Map<Object, Object> get() { return map; }
        public Object get(Object key) { return map.get(key); }
    }

    /** Geyser from build 1298: Registries has no such field. */
    public static final class ModernRegistriesOwner {
        public static final Object BEDROCK_ENTITY_DEFINITIONS = new Object();
    }

    /** BuiltInJavaRegistries, present in both generations. */
    public static final class BuiltInOwner {
        public static final ModernRegistry ENTITY_TYPE = new ModernRegistry();
    }

    public static final class EmptyBuiltInOwner {
        public static final Object ITEM = new Object();
    }

    public static final class ModernRegistry {
        private final Map<Integer, FakeEntry> byId = new TreeMap<>();
        private final Map<String, FakeEntry> byKey = new HashMap<>();
        private final Map<Object, FakeEntry> byValue = new IdentityHashMap<>();

        public Optional<FakeEntry> getById(int id) { return Optional.ofNullable(byId.get(id)); }

        void put(int id, String key, Object value) {
            FakeEntry entry = new FakeEntry(id, key);
            if (value != null) entry.bind(value);
            byId.put(id, entry);
            byKey.put(key, entry);
            if (value != null) byValue.put(value, entry);
        }
    }

    public static final class FakeEntry {
        private final int id;
        private final String key;
        private Object data;
        FakeEntry(int id, String key) { this.id = id; this.key = key; }
        void bind(Object value) { data = value; }
        public Object data() { return data; }
        public boolean isBound() { return data != null; }
    }

    private static Function<String, Class<?>> classes(Class<?> registries, Class<?> builtIn) {
        Map<String, Class<?>> known = new HashMap<>();
        if (registries != null) known.put(EntityTypeRegistry.LEGACY_OWNER, registries);
        if (builtIn != null) known.put(EntityTypeRegistry.MODERN_OWNER, builtIn);
        return known::get;
    }

    // ---- strategy choice -------------------------------------------------------------------------------------

    @Test void legacyMapIsChosenWhenGeyserStillHasIt() {
        assertEquals(Strategy.LEGACY_MAP, EntityTypeRegistry.resolve(classes(LegacyOwner.class, BuiltInOwner.class)).strategy());
        assertEquals(Strategy.LEGACY_MAP, EntityTypeRegistry.resolve(classes(LegacyOwner.class, null)).strategy());
    }

    @Test void javaRegistryIsChosenWhenTheMapIsGone() {
        EntityTypeRegistry registry = EntityTypeRegistry.resolve(classes(ModernRegistriesOwner.class, BuiltInOwner.class));
        assertEquals(Strategy.JAVA_REGISTRY, registry.strategy());
        assertTrue(registry.available());
    }

    @Test void neitherRegistryMeansUnavailableNotAFailure() {
        assertEquals(Strategy.NONE, EntityTypeRegistry.resolve(classes(ModernRegistriesOwner.class, EmptyBuiltInOwner.class)).strategy());
        assertEquals(Strategy.NONE, EntityTypeRegistry.resolve(classes(null, null)).strategy());
        EntityTypeRegistry none = EntityTypeRegistry.unavailable();
        assertFalse(none.available());
        assertNull(none.current("key", 5));
        assertFalse(none.replace("key", 5, "a", "b"));
        assertDoesNotThrow(() -> none.remove("key", 5, "a"));
    }

    // ---- legacy map ------------------------------------------------------------------------------------------

    @Test void legacyMapReadsReplacesAndRemoves() {
        LegacyOwner.JAVA_ENTITY_TYPES.get().clear();
        LegacyOwner.JAVA_ENTITY_TYPES.get().put("item", "geyser-item");
        EntityTypeRegistry registry = EntityTypeRegistry.resolve(classes(LegacyOwner.class, null));
        assertEquals("geyser-item", registry.current("item", 55));
        assertFalse(registry.replace("item", 55, "other", "ours"));
        assertTrue(registry.replace("item", 55, "geyser-item", "ours"));
        assertEquals("ours", registry.current("item", 55));
        registry.remove("item", 55, "unrelated");
        assertEquals("ours", registry.current("item", 55));
        registry.remove("item", 55, "ours");
        assertNull(registry.current("item", 55));
    }

    // ---- java registry ---------------------------------------------------------------------------------------

    @Test void javaRegistryReadsByIdAndIgnoresUnboundEntries() {
        BuiltInOwner.ENTITY_TYPE.put(10, "minecraft:item", "geyser-item");
        BuiltInOwner.ENTITY_TYPE.put(11, "minecraft:item_display", null);
        EntityTypeRegistry registry = EntityTypeRegistry.resolve(classes(ModernRegistriesOwner.class, BuiltInOwner.class));
        assertEquals("geyser-item", registry.current(null, 10));
        assertNull(registry.current(null, 11), "an entry without a definition is not an owner");
        assertNull(registry.current(null, 99), "an unknown id is not an owner");
    }

    @Test void javaRegistrySwapsTheDefinitionAndItsReverseLookup() {
        Object original = new Object(), ours = new Object();
        BuiltInOwner.ENTITY_TYPE.put(20, "minecraft:item_frame", original);
        EntityTypeRegistry registry = EntityTypeRegistry.resolve(classes(ModernRegistriesOwner.class, BuiltInOwner.class));
        assertFalse(registry.replace(null, 20, new Object(), ours), "only the expected definition is swapped");
        assertSame(original, registry.current(null, 20));
        assertTrue(registry.replace(null, 20, original, ours));
        assertSame(ours, registry.current(null, 20));
        FakeEntry entry = BuiltInOwner.ENTITY_TYPE.getById(20).orElseThrow();
        assertSame(entry, BuiltInOwner.ENTITY_TYPE.byValue.get(ours));
        assertNull(BuiltInOwner.ENTITY_TYPE.byValue.get(original));
        // Closing puts Geyser's own definition back.
        assertTrue(registry.replace(null, 20, ours, original));
        assertSame(original, registry.current(null, 20));
        assertSame(entry, BuiltInOwner.ENTITY_TYPE.byValue.get(original));
    }

    @Test void javaRegistryRemovesAnAddedDefinitionEverywhere() {
        Object added = new Object();
        BuiltInOwner.ENTITY_TYPE.put(30, "minecraft:item_display", added);
        EntityTypeRegistry registry = EntityTypeRegistry.resolve(classes(ModernRegistriesOwner.class, BuiltInOwner.class));
        registry.remove(null, 30, new Object());
        assertSame(added, registry.current(null, 30), "a definition that is not the given one stays");
        registry.remove(null, 30, added);
        assertNull(registry.current(null, 30));
        assertTrue(BuiltInOwner.ENTITY_TYPE.getById(30).isEmpty());
        assertFalse(BuiltInOwner.ENTITY_TYPE.byKey.containsKey("minecraft:item_display"));
        assertNull(BuiltInOwner.ENTITY_TYPE.byValue.get(added));
    }
}
