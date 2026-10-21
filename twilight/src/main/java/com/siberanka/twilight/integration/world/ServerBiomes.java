/* Copyright (c) 2026 siberanka. Twilight License (PolyForm Noncommercial 1.0.0 + terms), see LICENSE. */
package com.siberanka.twilight.integration.world;

import com.google.gson.Gson;
import com.google.gson.JsonParser;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Reads the custom biomes of the running server's registry: datapack biomes and the ones plugins
 * register at runtime (RealisticSeasons' seasonal biomes). Each is encoded with the codec the
 * server uses to send it to clients, so the result is the same data a Java player receives.
 */
public final class ServerBiomes {
    private static final Gson GSON = new Gson();

    private ServerBiomes() {}

    /** Custom biome key -> definition as nested maps (the network JSON form). */
    @SuppressWarnings("unchecked")
    public static Map<String, Map<String, ?>> read(Object bukkitServer) throws ReflectiveOperationException {
        Object server = bukkitServer.getClass().getMethod("getServer").invoke(bukkitServer);
        ClassLoader loader = server.getClass().getClassLoader();
        Class<?> resourceKey = Class.forName("net.minecraft.resources.ResourceKey", true, loader);
        Class<?> registryAccess = Class.forName("net.minecraft.core.RegistryAccess", true, loader);
        Class<?> registryClass = Class.forName("net.minecraft.core.Registry", true, loader);
        Class<?> biome = Class.forName("net.minecraft.world.level.biome.Biome", true, loader);
        Class<?> dynamicOps = Class.forName("com.mojang.serialization.DynamicOps", true, loader);
        Class<?> encoder = Class.forName("com.mojang.serialization.Encoder", true, loader);
        Class<?> dataResult = Class.forName("com.mojang.serialization.DataResult", true, loader);
        Class<?> provider = Class.forName("net.minecraft.core.HolderLookup$Provider", true, loader);

        Object access = server.getClass().getMethod("registryAccess").invoke(server);
        Object key = Class.forName("net.minecraft.core.registries.Registries", true, loader).getField("BIOME").get(null);
        Object registry = registryAccess.getMethod("lookupOrThrow", resourceKey).invoke(access, key);
        Object codec;
        try {
            codec = biome.getField("NETWORK_CODEC").get(null);
        } catch (NoSuchFieldException older) {
            codec = biome.getField("DIRECT_CODEC").get(null);
        }
        Object ops = Class.forName("com.mojang.serialization.JsonOps", true, loader).getField("INSTANCE").get(null);
        Object registryOps = Class.forName("net.minecraft.resources.RegistryOps", true, loader)
                .getMethod("create", dynamicOps, provider).invoke(null, ops, access);
        var encodeStart = encoder.getMethod("encodeStart", dynamicOps, Object.class);
        var result = dataResult.getMethod("result");

        Map<String, Map<String, ?>> biomes = new TreeMap<>();
        for (Object entry : (Set<?>) registryClass.getMethod("entrySet").invoke(registry)) {
            Map.Entry<?, ?> pair = (Map.Entry<?, ?>) entry;
            String name = name(pair.getKey());
            if (name == null || name.startsWith("minecraft:")) continue;
            Optional<?> json = (Optional<?>) result.invoke(encodeStart.invoke(codec, registryOps, pair.getValue()));
            if (json.isEmpty()) continue;
            Object parsed = GSON.fromJson(JsonParser.parseString(json.get().toString()), Map.class);
            if (parsed instanceof Map<?, ?> map) biomes.put(name, (Map<String, ?>) map);
        }
        return biomes;
    }

    /** "ResourceKey[minecraft:worldgen/biome / ns:path]" -> "ns:path". */
    static String name(Object resourceKey) {
        String text = String.valueOf(resourceKey);
        int start = text.lastIndexOf(" / ");
        int end = text.lastIndexOf(']');
        return start < 0 || end < start ? null : text.substring(start + 3, end);
    }
}
