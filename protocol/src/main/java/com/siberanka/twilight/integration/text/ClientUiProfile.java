/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.integration.text;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A Bedrock client's UI profile (classic desktop layout or pocket touch layout) as Geyser reports it, read by
 * name: the profile type and the accessors that lead to it have moved between Geyser and Floodgate builds, so a
 * missing or changed accessor must never break a screen. Anything unreadable counts as {@link #CLASSIC}.
 */
public enum ClientUiProfile {
    CLASSIC,
    POCKET;

    private static final Method NONE = missing();
    private static final Map<Class<?>, Method> SESSION_TO_DATA = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Method> DATA_TO_PROFILE = new ConcurrentHashMap<>();

    /** The profile of a Geyser session (any object with {@code getClientData().getUiProfile()}), or CLASSIC. */
    public static ClientUiProfile of(Object session) {
        try {
            Object data = call(SESSION_TO_DATA, session, "getClientData");
            Object profile = call(DATA_TO_PROFILE, data, "getUiProfile");
            return fromName(profile);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unreadable) {
            return CLASSIC;
        }
    }

    /** POCKET only for a value named "POCKET" (an enum constant or its text); everything else is CLASSIC. */
    static ClientUiProfile fromName(Object profile) {
        if (profile == null) return CLASSIC;
        String name = profile instanceof Enum<?> constant ? constant.name() : String.valueOf(profile);
        return "POCKET".equalsIgnoreCase(name.trim()) ? POCKET : CLASSIC;
    }

    private static Object call(Map<Class<?>, Method> cache, Object target, String name) throws ReflectiveOperationException {
        if (target == null) return null;
        Method method = cache.computeIfAbsent(target.getClass(), type -> find(type, name));
        return method == NONE ? null : method.invoke(target);
    }

    private static Method find(Class<?> type, String name) {
        try {
            Method method = type.getMethod(name);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException | RuntimeException absent) {
            return NONE;
        }
    }

    private static Method missing() {
        try {
            return ClientUiProfile.class.getDeclaredMethod("missing");
        } catch (NoSuchMethodException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
