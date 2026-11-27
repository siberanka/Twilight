package com.siberanka.twilight.source;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The light custom blocks give off. Content plugins keep it in their block settings (CraftEngine's
 * {@code luminance}), not in the resource pack, so it is read from the plugin's API when a build starts and
 * handed to the pack compiler, which tells Bedrock's custom blocks to emit it.
 */
public final class BlockLights {
    private BlockLights() {}

    /**
     * @return Java block state ({@code minecraft:note_block[instrument=hat,note=1,powered=false]}, properties by
     *         name) to light level 1..15; empty when no provider has lit custom blocks or its API is not readable
     */
    public static Map<String, Integer> collect() {
        Map<String, Integer> lights = new LinkedHashMap<>();
        Plugin provider = Bukkit.getPluginManager().getPlugin("CraftEngine");
        if (provider == null || !provider.isEnabled()) return lights;
        try {
            Class<?> api = Class.forName("net.momirealms.craftengine.bukkit.api.CraftEngineBlocks", false,
                    provider.getClass().getClassLoader());
            if (api.getMethod("loadedBlocks").invoke(null) instanceof Map<?, ?> blocks) {
                for (Object definition : List.copyOf(blocks.values())) addCraftEngine(definition, lights);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unreadable) {
            // Light is cosmetic: another CraftEngine version only costs the glow of its custom blocks.
            provider.getLogger().fine("Block light levels could not be read: " + unreadable);
        }
        return lights;
    }

    private static void addCraftEngine(Object definition, Map<String, Integer> lights) throws ReflectiveOperationException {
        Object provider = call(definition, "variantProvider");
        if (provider == null || !(call(provider, "states") instanceof Iterable<?> states)) return;
        for (Object state : states) {
            Object settings = call(state, "settings");
            if (settings == null || !(call(settings, "luminance") instanceof Integer level) || level <= 0) continue;
            for (String accessor : List.of("vanillaBlockState", "visualBlockState")) {
                Object wrapper = call(state, accessor);
                if (wrapper == null || !(call(wrapper, "getAsString") instanceof String text)) continue;
                String javaState = normalize(text);
                if (javaState != null) lights.merge(javaState, Math.min(level, 15), Math::max);
            }
        }
    }

    private static Object call(Object target, String name) throws ReflectiveOperationException {
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == 0) {
                method.setAccessible(true);
                return method.invoke(target);
            }
        }
        return null;
    }

    /** {@code note_block[powered=false,note=1]} to {@code minecraft:note_block[note=1,powered=false]}; null if not a state. */
    public static String normalize(String state) {
        if (state == null) return null;
        String text = state.trim();
        int open = text.indexOf('[');
        String id = open < 0 ? text : text.substring(0, open);
        if (id.isEmpty() || open >= 0 && !text.endsWith("]")) return null;
        if (id.indexOf(':') < 0) id = "minecraft:" + id;
        if (open < 0) return id;
        Map<String, String> properties = new TreeMap<>();
        String body = text.substring(open + 1, text.length() - 1);
        if (!body.isEmpty()) {
            for (String pair : body.split(",")) {
                int equals = pair.indexOf('=');
                if (equals <= 0) return null;
                properties.put(pair.substring(0, equals).trim(), pair.substring(equals + 1).trim());
            }
        }
        if (properties.isEmpty()) return id;
        List<String> parts = new ArrayList<>();
        properties.forEach((key, value) -> parts.add(key + "=" + value));
        return id + "[" + String.join(",", parts) + "]";
    }
}
