/* Copyright (c) 2026 siberanka. Twilight License (PolyForm Noncommercial 1.0.0 + terms), see LICENSE. */
package com.siberanka.twilight.integration.world;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Sends whole chunks again to one player, after the server changed only their biomes.
 *
 * <p>Java updates biomes in place ({@code /fillbiome}, seasons plugins); Geyser has no translation
 * for that packet, so Bedrock players kept the old biomes until the chunk reloaded. A chunk sent
 * again to that player only goes through the normal chunk path (and other plugins' packet
 * listeners, such as a seasons plugin's), which Geyser translates with the new biomes. Requests of
 * one tick are merged; chunks that are no longer loaded are skipped.
 */
public final class ChunkResender {
    private final Plugin plugin;
    private final Map<UUID, Set<Long>> pending = new HashMap<>();
    private boolean scheduled;
    private boolean reflectionFailed;

    public ChunkResender(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Any thread: queue chunks for a player and send them on the next server tick. */
    public synchronized void request(UUID player, Iterable<long[]> chunks) {
        Set<Long> set = pending.computeIfAbsent(player, ignored -> new LinkedHashSet<>());
        for (long[] chunk : chunks) set.add(chunk[0] << 32 | (chunk[1] & 0xFFFFFFFFL));
        if (scheduled) return;
        scheduled = true;
        Bukkit.getScheduler().runTask(plugin, this::flush);
    }

    private void flush() {
        Map<UUID, Set<Long>> work;
        synchronized (this) {
            work = new HashMap<>(pending);
            pending.clear();
            scheduled = false;
        }
        work.forEach((uuid, chunks) -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) return;
            World world = player.getWorld();
            for (long key : chunks) {
                int x = (int) (key >> 32), z = (int) key;
                if (!world.isChunkLoaded(x, z)) continue;
                send(player, world, x, z);
            }
        });
    }

    @SuppressWarnings("deprecation") // refreshChunk is the only API that resends a chunk
    private void send(Player player, World world, int x, int z) {
        if (!reflectionFailed) {
            try {
                Object level = world.getClass().getMethod("getHandle").invoke(world);
                Object source = level.getClass().getMethod("getChunkSource").invoke(level);
                Object chunk = source.getClass().getMethod("getChunkNow", int.class, int.class).invoke(source, x, z);
                if (chunk == null) return;
                Object light = level.getClass().getMethod("getLightEngine").invoke(level);
                Object packet = chunkPacket(chunk, light);
                Object handle = player.getClass().getMethod("getHandle").invoke(player);
                Object connection = handle.getClass().getField("connection").get(handle);
                Method send = method(connection.getClass(), "send", 1);
                send.invoke(connection, packet);
                return;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                reflectionFailed = true;
                plugin.getLogger().log(Level.WARNING, "Could not send a chunk to one player; biome updates reach "
                        + "Bedrock players by refreshing the chunk for everyone instead.", failure);
            }
        }
        world.refreshChunk(x, z);
    }

    private static Object chunkPacket(Object chunk, Object light) throws ReflectiveOperationException {
        Class<?> type = Class.forName("net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket",
                true, chunk.getClass().getClassLoader());
        for (Constructor<?> constructor : type.getConstructors()) {
            Class<?>[] parameters = constructor.getParameterTypes();
            if (parameters.length < 2 || !parameters[0].isInstance(chunk) || !parameters[1].isInstance(light)) continue;
            Object[] arguments = new Object[parameters.length];
            arguments[0] = chunk;
            arguments[1] = light;
            for (int index = 2; index < parameters.length; index++) {
                arguments[index] = parameters[index] == boolean.class ? Boolean.TRUE : null;
            }
            return constructor.newInstance(arguments);
        }
        throw new NoSuchMethodException("ClientboundLevelChunkWithLightPacket(LevelChunk, LightEngine, ...)");
    }

    private static Method method(Class<?> type, String name, int parameters) throws NoSuchMethodException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == parameters) return method;
            }
        }
        throw new NoSuchMethodException(type.getName() + "#" + name);
    }
}
