/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.integration.display;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;

/**
 * Geyser registries that differ between Geyser versions, reached by name so that one Twilight build runs on all
 * of them. Geyser 2.11 kept entity definitions also by Java identifier ({@code Registries.JAVA_ENTITY_IDENTIFIERS})
 * and Java items in {@code Registries.JAVA_ITEMS}; Geyser 2.12 has neither (entities are looked up by type only,
 * items through {@code BuiltInJavaRegistries.ITEM}).
 */
final class GeyserRegistries {
    private static final Object ENTITY_IDENTIFIERS = staticField("org.geysermc.geyser.registry.Registries", "JAVA_ENTITY_IDENTIFIERS");
    private static final Object LEGACY_ITEMS = staticField("org.geysermc.geyser.registry.Registries", "JAVA_ITEMS");
    private static final Object ITEM_REGISTRY = staticField("org.geysermc.geyser.registry.java.BuiltInJavaRegistries", "ITEM");

    private GeyserRegistries() {}

    /** Whether this Geyser keeps entity definitions by Java identifier as well (2.11 and older). */
    static boolean hasEntityIdentifiers() {
        return identifiers() != null;
    }

    /** The definition registered for a Java entity identifier, or {@code fallback} when Geyser has no such map. */
    static Object entity(String identifier, Object fallback) {
        Map<Object, Object> map = identifiers();
        return map == null ? fallback : map.get(identifier);
    }

    /** Replaces a definition by identifier; true when done or when this Geyser has no such map. */
    static boolean replaceEntity(String identifier, Object expected, Object replacement) {
        Map<Object, Object> map = identifiers();
        return map == null || map.replace(identifier, expected, replacement);
    }

    static void removeEntity(String identifier, Object expected) {
        Map<Object, Object> map = identifiers();
        if (map != null) map.remove(identifier, expected);
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> identifiers() {
        if (ENTITY_IDENTIFIERS == null) return null;
        try {
            Object map = ENTITY_IDENTIFIERS.getClass().getMethod("get").invoke(ENTITY_IDENTIFIERS);
            return map instanceof Map<?, ?> found ? (Map<Object, Object>) found : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            return null;
        }
    }

    /** The Java identifier ({@code minecraft:paper}) of a Java item id, or null. */
    static String itemIdentifier(int javaId) {
        Object item = null;
        try {
            if (LEGACY_ITEMS != null) {
                item = LEGACY_ITEMS.getClass().getMethod("get", int.class).invoke(LEGACY_ITEMS, javaId);
            } else if (ITEM_REGISTRY != null) {
                Method get = findMethod(ITEM_REGISTRY.getClass(), "get", int.class);
                Object found = get == null ? null : get.invoke(ITEM_REGISTRY, javaId);
                item = found instanceof Optional<?> optional ? optional.orElse(null) : found;
            }
            if (item == null) return null;
            Object identifier = item.getClass().getMethod("javaIdentifier").invoke(item);
            String text = identifier == null ? null : String.valueOf(identifier);
            return text == null || text.isBlank() ? null : text.contains(":") ? text : "minecraft:" + text;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unknown) {
            return null;
        }
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameters) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Method method = current.getMethod(name, parameters);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException | RuntimeException next) {
                // Try the next class.
            }
        }
        return null;
    }

    private static Object staticField(String owner, String name) {
        try {
            Field field = Class.forName(owner, true, GeyserRegistries.class.getClassLoader()).getField(name);
            return field.get(null);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError absent) {
            return null;
        }
    }
}
