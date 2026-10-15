package com.siberanka.twilight;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.util.function.Consumer;

final class ServerScheduler {
    private final Plugin plugin;

    ServerScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    void execute(Runnable task) {
        try {
            Object scheduler = Bukkit.getServer().getClass().getMethod("getGlobalRegionScheduler").invoke(Bukkit.getServer());
            scheduler.getClass().getMethod("execute", Plugin.class, Runnable.class).invoke(scheduler, plugin, task);
        } catch (NoSuchMethodException ignored) {
            Bukkit.getScheduler().runTask(plugin, task);
        } catch (IllegalAccessException | InvocationTargetException failure) {
            throw new IllegalStateException("Could not schedule a global server task", failure);
        }
    }

    void delayed(Runnable task, long ticks) {
        try {
            Object scheduler = Bukkit.getServer().getClass().getMethod("getGlobalRegionScheduler").invoke(Bukkit.getServer());
            Consumer<Object> consumer = ignored -> task.run();
            scheduler.getClass().getMethod("runDelayed", Plugin.class, Consumer.class, long.class)
                    .invoke(scheduler, plugin, consumer, ticks);
        } catch (NoSuchMethodException ignored) {
            Bukkit.getScheduler().runTaskLater(plugin, task, ticks);
        } catch (IllegalAccessException | InvocationTargetException failure) {
            throw new IllegalStateException("Could not schedule a delayed global server task", failure);
        }
    }
}
