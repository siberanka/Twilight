/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.integration.display;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.function.Function;

/**
 * Where a Geyser build keeps the translator definitions of Java entity types, reached by name so that one Twilight
 * jar runs on all of them:
 * <ul>
 *   <li>{@link Strategy#LEGACY_MAP}: {@code Registries.JAVA_ENTITY_TYPES}, a map from Geyser's entity type to its
 *       definition (Geyser 2.11 up to 2.12.1 build 1263);</li>
 *   <li>{@link Strategy#JAVA_REGISTRY}: {@code BuiltInJavaRegistries.ENTITY_TYPE}, an id-indexed registry whose
 *       entries are swapped in place (build 1298 and newer, where the map is gone).</li>
 * </ul>
 * When neither exists the registry is {@link #unavailable()}; callers then skip the feature that needs it.
 */
final class EntityTypeRegistry {
    static final String LEGACY_OWNER = "org.geysermc.geyser.registry.Registries";
    static final String LEGACY_FIELD = "JAVA_ENTITY_TYPES";
    static final String MODERN_OWNER = "org.geysermc.geyser.registry.java.BuiltInJavaRegistries";
    static final String MODERN_FIELD = "ENTITY_TYPE";

    enum Strategy { LEGACY_MAP, JAVA_REGISTRY, NONE }

    private final Strategy strategy;
    private final Object registry;
    private final String note;

    private EntityTypeRegistry(Strategy strategy, Object registry, String note) {
        this.strategy = strategy;
        this.registry = registry;
        this.note = note;
    }

    /** Resolves against the running Geyser. */
    static EntityTypeRegistry detect() {
        ClassLoader loader = EntityTypeRegistry.class.getClassLoader();
        return resolve(name -> {
            try {
                return Class.forName(name, true, loader);
            } catch (ReflectiveOperationException | LinkageError absent) {
                return null;
            }
        });
    }

    /** Resolves through {@code classes}, which maps a class name to the class or null; the legacy map wins when both exist. */
    static EntityTypeRegistry resolve(Function<String, Class<?>> classes) {
        Object legacy = staticValue(classes.apply(LEGACY_OWNER), LEGACY_FIELD);
        if (legacy != null) {
            Method get = method(legacy.getClass(), "get");
            if (get != null && value(get, legacy) instanceof Map<?, ?>) {
                return new EntityTypeRegistry(Strategy.LEGACY_MAP, legacy, "Registries." + LEGACY_FIELD);
            }
        }
        Object modern = staticValue(classes.apply(MODERN_OWNER), MODERN_FIELD);
        if (modern != null && entries(modern) != null) {
            return new EntityTypeRegistry(Strategy.JAVA_REGISTRY, modern, "BuiltInJavaRegistries." + MODERN_FIELD);
        }
        return unavailable();
    }

    static EntityTypeRegistry unavailable() {
        return new EntityTypeRegistry(Strategy.NONE, null, "no entity type registry found");
    }

    Strategy strategy() { return strategy; }

    boolean available() { return strategy != Strategy.NONE; }

    /** A short description for logs. */
    String describe() { return note; }

    /**
     * The definition registered for a Java entity type, or null when none.
     *
     * @param key     Geyser's entity type object (GeyserEntityType); used by the legacy map only
     * @param ordinal the protocol library's entity type ordinal, the id of the modern registry
     */
    Object current(Object key, int ordinal) {
        try {
            switch (strategy) {
                case LEGACY_MAP:
                    return legacyMap().get(key);
                case JAVA_REGISTRY: {
                    Object entry = entry(ordinal);
                    return entry == null || !bound(entry) ? null : invoke(entry, "data");
                }
                default:
                    return null;
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            return null;
        }
    }

    /** Swaps {@code expected} for {@code replacement}; false when the registry no longer holds {@code expected}. */
    boolean replace(Object key, int ordinal, Object expected, Object replacement) {
        try {
            switch (strategy) {
                case LEGACY_MAP:
                    return legacyMap().replace(key, expected, replacement);
                case JAVA_REGISTRY: {
                    Object entry = entry(ordinal);
                    if (entry == null || !bound(entry) || invoke(entry, "data") != expected) return false;
                    swap(entry, expected, replacement);
                    return true;
                }
                default:
                    return false;
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            return false;
        }
    }

    /** Removes {@code expected}, a definition that Geyser's builder added (as opposed to one swapped in by {@link #replace}). */
    void remove(Object key, int ordinal, Object expected) {
        try {
            switch (strategy) {
                case LEGACY_MAP:
                    legacyMap().remove(key, expected);
                    return;
                case JAVA_REGISTRY: {
                    Object entry = entry(ordinal);
                    if (entry == null || !bound(entry) || invoke(entry, "data") != expected) return;
                    forget(entry, expected);
                    return;
                }
                default:
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            // Closing must not fail; a definition that stays registered is replaced on the next start.
        }
    }

    @SuppressWarnings("unchecked")
    private Map<Object, Object> legacyMap() throws ReflectiveOperationException {
        return (Map<Object, Object>) invoke(registry, "get");
    }

    /**
     * The registry entry for an id. Geyser fires its entity definition event while this registry is still being
     * filled, when its lookup methods refuse to answer ({@code must be frozen to access its entries}); the backing
     * id map is read directly instead.
     */
    private Object entry(int ordinal) {
        Map<Object, Object> byId = entries(registry);
        return byId == null ? null : byId.get(ordinal);
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> entries(Object registry) {
        try {
            return field(registry.getClass(), "byId").get(registry) instanceof Map<?, ?> found ? (Map<Object, Object>) found : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError absent) {
            return null;
        }
    }

    private static boolean bound(Object entry) throws ReflectiveOperationException {
        return Boolean.TRUE.equals(invoke(entry, "isBound"));
    }

    /** Replaces an entry's value and the registry's reverse lookup by value. */
    private void swap(Object entry, Object expected, Object replacement) throws ReflectiveOperationException {
        field(entry.getClass(), "data").set(entry, replacement);
        Map<Object, Object> byValue = map("byValue");
        if (byValue != null) {
            if (byValue.get(expected) == entry) byValue.remove(expected);
            byValue.put(replacement, entry);
        }
    }

    private void forget(Object entry, Object expected) throws ReflectiveOperationException {
        for (String name : new String[] {"byId", "byKey"}) {
            Map<Object, Object> map = map(name);
            if (map != null) map.values().removeIf(value -> value == entry);
        }
        Map<Object, Object> byValue = map("byValue");
        if (byValue != null) byValue.remove(expected);
    }

    @SuppressWarnings("unchecked")
    private Map<Object, Object> map(String name) throws ReflectiveOperationException {
        Field field = field(registry.getClass(), name);
        return field.get(registry) instanceof Map<?, ?> found ? (Map<Object, Object>) found : null;
    }

    private static Object invoke(Object target, String name) throws ReflectiveOperationException {
        Method method = method(target.getClass(), name);
        if (method == null) throw new NoSuchMethodException(target.getClass().getName() + "." + name);
        return method.invoke(target);
    }

    private static Object value(Method method, Object target) {
        try {
            return method.invoke(target);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            return null;
        }
    }

    private static Object staticValue(Class<?> owner, String name) {
        if (owner == null) return null;
        try {
            return owner.getField(name).get(null);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError absent) {
            return null;
        }
    }

    private static Method method(Class<?> type, String name, Class<?>... parameters) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Method method = current.getDeclaredMethod(name, parameters);
                method.setAccessible(true);
                return method;
            } catch (ReflectiveOperationException | RuntimeException next) {
                // Try the superclass.
            }
        }
        try {
            Method method = type.getMethod(name, parameters);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException | RuntimeException none) {
            return null;
        }
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException | RuntimeException next) {
                // Try the superclass.
            }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }
}
