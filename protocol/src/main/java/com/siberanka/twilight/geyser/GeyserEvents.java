/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.geyser;

import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.event.EventBus;
import org.geysermc.geyser.api.event.EventRegistrar;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * Subscribes to Geyser events without linking against Geyser's event library ({@code org.geysermc.event}).
 *
 * <p>Floodgate bundles its own unrelocated copy of that library. A proxy or server that loads Floodgate
 * before Geyser can then resolve {@code org.geysermc.event.*} for Twilight from Floodgate's copy while
 * Geyser's {@link EventBus} uses its own, and the JVM refuses the call with "loader constraint violation"
 * (seen on FlameCord). {@code subscribe} returns and takes types of that library, so it is called through
 * reflection: the method and {@code PostOrder} come from the class loader that defined Geyser's
 * {@link EventBus}, and Twilight's classes never refer to the library.
 */
public final class GeyserEvents {
    private GeyserEvents() {}

    /** Geyser's event bus. */
    public static EventBus<EventRegistrar> bus() {
        return GeyserApi.api().eventBus();
    }

    /** Subscribes {@code handler} to {@code event} with Geyser's default order. */
    public static <T> void subscribe(EventRegistrar owner, Class<T> event, Consumer<T> handler) {
        EventBus<EventRegistrar> bus = bus();
        try {
            Method subscribe = EventBus.class.getMethod("subscribe", Object.class, Class.class, Consumer.class);
            invoke(subscribe, bus, owner, event, handler);
        } catch (NoSuchMethodException missing) {
            throw new IllegalStateException("Geyser's event bus has no subscribe(owner, event, handler)", missing);
        }
    }

    /**
     * Subscribes {@code handler} after every other listener ({@code PostOrder.LAST}). Returns false when this
     * Geyser cannot order listeners; the handler is then subscribed with the default order.
     */
    public static <T> boolean subscribeLast(EventRegistrar owner, Class<T> event, Consumer<T> handler) {
        EventBus<EventRegistrar> bus = bus();
        try {
            Class<?> order = Class.forName("org.geysermc.event.PostOrder", false, EventBus.class.getClassLoader());
            Method subscribe = EventBus.class.getMethod("subscribe", Object.class, Class.class, Consumer.class, order);
            invoke(subscribe, bus, owner, event, handler, last(order));
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalArgumentException unordered) {
            subscribe(owner, event, handler);
            return false;
        }
    }

    /** Removes every listener of {@code owner}. */
    public static void unregisterAll(EventRegistrar owner) {
        bus().unregisterAll(owner);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object last(Class<?> order) {
        return Enum.valueOf((Class) order, "LAST");
    }

    private static void invoke(Method method, Object target, Object... arguments) {
        try {
            method.invoke(target, arguments);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException(cause);
        } catch (IllegalAccessException inaccessible) {
            throw new IllegalStateException("Geyser's event bus is not accessible", inaccessible);
        }
    }
}
