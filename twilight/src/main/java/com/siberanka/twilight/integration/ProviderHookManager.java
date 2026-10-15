package com.siberanka.twilight.integration;

import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;

/** Optional, reflection-only provider hooks; file discovery remains the compatibility fallback. */
public final class ProviderHookManager {
    private static final Map<String, List<HookSpec>> EVENTS = Map.of(
            "itemsadder", List.of(
                    HookSpec.event("dev.lone.itemsadder.api.Events.ItemsAdderFirstLoadEvent"),
                    HookSpec.event("dev.lone.itemsadder.api.Events.ItemsAdderLoadDataEvent"),
                    HookSpec.event("dev.lone.itemsadder.api.Events.ItemsAdderPackCompressedEvent")),
            "craftengine", List.of(
                    HookSpec.event("net.momirealms.craftengine.bukkit.api.event.CraftEngineReloadEvent"),
                    HookSpec.event("net.momirealms.craftengine.bukkit.api.event.AsyncResourcePackGenerateEvent")),
            "oraxen", List.of(
                    HookSpec.event("io.th0rgal.oraxen.api.events.OraxenItemsLoadedEvent"),
                    HookSpec.event("io.th0rgal.oraxen.api.events.OraxenPackGeneratedEvent"),
                    HookSpec.event("io.th0rgal.oraxen.api.events.OraxenPackUploadEvent")),
            "nexo", List.of(
                    HookSpec.event("com.nexomc.nexo.api.events.NexoItemsLoadedEvent"),
                    HookSpec.event("com.nexomc.nexo.api.events.NexoPackGeneratedEvent")),
            "bettermodel", List.of(
                    HookSpec.filtered("kr.toxicity.model.api.bukkit.event.BetterModelBukkitEvent",
                            "kr.toxicity.model.api.event.PluginEndReloadEvent"),
                    HookSpec.event("kr.toxicity.model.api.event.ModelReloadEvent")),
            "modelengine", List.of(
                    HookSpec.property("com.ticxo.modelengine.api.events.ModelRegistrationEvent", "getPhase", "FINISHED"),
                    HookSpec.event("com.ticxo.modelengine.api.events.ModelReloadEvent")),
            "realisticseasons", List.of(
                    HookSpec.event("me.casperge.realisticseasons.api.SeasonChangeEvent"))
    );

    private final Plugin owner;
    private final Listener listener = new Listener() { };
    private final Consumer<String> callback;
    private final Map<String, Integer> registered = new LinkedHashMap<>();

    public ProviderHookManager(Plugin owner, Consumer<String> callback) {
        this.owner = owner;
        this.callback = callback;
    }

    public synchronized int register(Plugin provider) {
        if (!provider.isEnabled()) return 0;
        String key = provider.getName().toLowerCase(Locale.ROOT);
        if (!EVENTS.containsKey(key) || registered.containsKey(key)) return 0;
        int count = 0;
        for (HookSpec hook : EVENTS.get(key)) {
            try {
                ClassLoader loader = provider.getClass().getClassLoader();
                Class<?> loaded = Class.forName(hook.eventClass(), false, loader);
                if (!Event.class.isAssignableFrom(loaded)) continue;
                @SuppressWarnings("unchecked") Class<? extends Event> eventClass = (Class<? extends Event>) loaded;
                Class<?> nestedType = hook.nestedSourceClass() == null ? null :
                        Class.forName(hook.nestedSourceClass(), false, loader);
                java.lang.reflect.Method sourceMethod = nestedType == null ? null : loaded.getMethod("source");
                java.lang.reflect.Method propertyMethod = hook.propertyMethod() == null ? null :
                        loaded.getMethod(hook.propertyMethod());
                EventExecutor executor = (ignored, event) -> {
                    if (nestedType != null) {
                        try {
                            Object source = sourceMethod.invoke(event);
                            if (!nestedType.isInstance(source)) return;
                        } catch (ReflectiveOperationException failure) {
                            throw new EventException(failure);
                        }
                    }
                    if (propertyMethod != null) {
                        try {
                            Object value = propertyMethod.invoke(event);
                            if (!hook.expectedValue().equals(String.valueOf(value))) return;
                        } catch (ReflectiveOperationException failure) {
                            throw new EventException(failure);
                        }
                    }
                    callback.accept(provider.getName() + ':' + event.getEventName());
                };
                PluginManager manager = owner.getServer().getPluginManager();
                manager.registerEvent(eventClass, listener, EventPriority.MONITOR, executor, owner, true);
                count++;
            } catch (ClassNotFoundException ignored) {
                // Provider version does not expose this event; fingerprint/file discovery remains active.
            } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
                owner.getLogger().log(Level.WARNING, "Could not register optional " + provider.getName() + " hook " + hook.eventClass(), failure);
            }
        }
        registered.put(key, count);
        return count;
    }

    public synchronized Map<String, Integer> registeredHooks() { return Map.copyOf(registered); }

    private record HookSpec(String eventClass, String nestedSourceClass, String propertyMethod, String expectedValue) {
        private static HookSpec event(String eventClass) { return new HookSpec(eventClass, null, null, null); }
        private static HookSpec filtered(String eventClass, String nestedSourceClass) {
            return new HookSpec(eventClass, nestedSourceClass, null, null);
        }
        private static HookSpec property(String eventClass, String propertyMethod, String expectedValue) {
            return new HookSpec(eventClass, null, propertyMethod, expectedValue);
        }
    }
}
