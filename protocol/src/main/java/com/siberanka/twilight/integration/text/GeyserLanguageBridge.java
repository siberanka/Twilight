/* Copyright (c) 2026 siberanka. Twilight License (PolyForm Noncommercial 1.0.0 + terms), see LICENSE. */
package com.siberanka.twilight.integration.text;

import com.google.gson.JsonParser;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.event.EventRegistrar;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostInitializeEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostReloadEvent;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.text.MinecraftLocale;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipFile;

/**
 * Lets Geyser render Java text with the server's resource-pack translations.
 *
 * <p>Geyser translates Java's translatable text (item names, titles, messages) with the
 * vanilla language files. Java players see the packs' strings instead: datapack and plugin
 * content names, and overrides such as a hidden {@code container.inventory} or a GUI image as
 * {@code container.enderchest}. When a Bedrock player enters the game, the pack strings for
 * the player's locale and for {@code en_us} (Java's fallback) are laid over Geyser's loaded
 * maps, reproducing Java's order: pack locale, vanilla locale, pack English, vanilla English.
 */
public final class GeyserLanguageBridge implements AutoCloseable {
    private static final String PREFIX = "twilight/lang/";

    private final java.util.function.Supplier<java.util.Collection<Path>> packs;
    private final Logger logger;
    private final EventRegistrar registrar;
    private final Wrapper wrapper = new Wrapper();
    private final Set<Map<String, String>> applied = Collections.newSetFromMap(new IdentityHashMap<>());
    private volatile Map<String, Map<String, String>> overlays = Map.of();
    private volatile PacketTranslator<? extends Packet> original;
    private volatile boolean failureLogged;

    private GeyserLanguageBridge(Object owner, java.util.function.Supplier<java.util.Collection<Path>> packs, Logger logger) {
        this.packs = packs;
        this.logger = logger;
        this.registrar = EventRegistrar.of(owner);
    }

    public static GeyserLanguageBridge create(Object owner, Path servedPack, Logger logger) {
        return create(owner, () -> java.util.List.of(servedPack), logger);
    }

    /**
     * The strings of several packs (on a proxy, every server's), merged; for a key that two packs translate
     * differently the first pack wins. Call {@link #refresh()} when the packs change.
     */
    public static GeyserLanguageBridge create(Object owner, java.util.function.Supplier<java.util.Collection<Path>> packs, Logger logger) {
        GeyserLanguageBridge bridge = new GeyserLanguageBridge(owner, packs, logger);
        com.siberanka.twilight.geyser.GeyserEvents.subscribe(bridge.registrar, GeyserPostInitializeEvent.class, event -> bridge.attach());
        com.siberanka.twilight.geyser.GeyserEvents.subscribe(bridge.registrar, GeyserPostReloadEvent.class, event -> bridge.attach());
        if (Registries.JAVA_PACKET_TRANSLATORS.get().get(ClientboundLoginPacket.class) != null) bridge.attach();
        return bridge;
    }

    /** Reads the packs' strings again; sessions that enter the game afterwards get them. */
    public synchronized void refresh() {
        Map<String, Map<String, String>> merged = new HashMap<>();
        for (Path pack : packs.get()) {
            try {
                for (var locale : read(pack).entrySet()) {
                    Map<String, String> strings = merged.computeIfAbsent(locale.getKey(), ignored -> new HashMap<>());
                    locale.getValue().forEach(strings::putIfAbsent);
                }
            } catch (IOException | RuntimeException failure) {
                logger.log(Level.WARNING, "Could not read the translations of the Twilight pack " + pack.getFileName(), failure);
            }
        }
        overlays = Map.copyOf(merged);
        synchronized (applied) { applied.clear(); }
    }

    private synchronized void attach() {
        refresh();
        var translators = Registries.JAVA_PACKET_TRANSLATORS.get();
        PacketTranslator<? extends Packet> current = translators.get(ClientboundLoginPacket.class);
        if (current == null) {
            logger.warning("Geyser has no login translator; resource-pack translations are not applied.");
            return;
        }
        if (current != wrapper) {
            original = current;
            translators.put(ClientboundLoginPacket.class, wrapper);
        }
        if (!overlays.isEmpty()) {
            logger.info("Resource-pack translations active for Bedrock players (" + overlays.size() + " locales).");
        }
    }

    static Map<String, Map<String, String>> read(Path pack) throws IOException {
        if (!Files.isRegularFile(pack)) return Map.of();
        Map<String, Map<String, String>> result = new HashMap<>();
        try (ZipFile zip = new ZipFile(pack.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                String name = entry.getName();
                if (!name.startsWith(PREFIX) || !name.endsWith(".json") || entry.getSize() > 16L * 1024 * 1024) continue;
                try (Reader reader = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
                    Map<String, String> strings = new HashMap<>();
                    for (var value : JsonParser.parseReader(reader).getAsJsonObject().entrySet()) {
                        strings.put(value.getKey(), value.getValue().getAsString());
                    }
                    result.put(name.substring(PREFIX.length(), name.length() - 5).toLowerCase(Locale.ROOT), strings);
                }
            }
        }
        return result;
    }

    /** Lays the pack strings of {@code locale} over Geyser's loaded map for it (once per loaded map). */
    static boolean overlay(Map<String, Map<String, String>> loaded, Map<String, Map<String, String>> overlays,
                           String locale, Set<Map<String, String>> applied) {
        Map<String, String> strings = overlays.get(locale);
        Map<String, String> base = loaded.get(locale);
        if (strings == null || base == null) return false;
        synchronized (applied) {
            if (applied.contains(base)) return false;
            // Replace the map reference at once; readers on other threads never see a half-merged map.
            Map<String, String> merged = new HashMap<>(base);
            merged.putAll(strings);
            loaded.put(locale, merged);
            applied.add(merged);
            return true;
        }
    }

    @Override public synchronized void close() {
        com.siberanka.twilight.geyser.GeyserEvents.unregisterAll(registrar);
        if (original != null) Registries.JAVA_PACKET_TRANSLATORS.get().replace(ClientboundLoginPacket.class, wrapper, original);
    }

    private final class Wrapper extends PacketTranslator<ClientboundLoginPacket> {
        @SuppressWarnings("unchecked")
        @Override public void translate(GeyserSession session, ClientboundLoginPacket packet) {
            Map<String, Map<String, String>> current = overlays;
            if (!current.isEmpty()) {
                try {
                    String locale = session.locale() == null ? "en_us" : session.locale().toLowerCase(Locale.ROOT);
                    overlay(MinecraftLocale.LOCALE_MAPPINGS, current, "en_us", applied);
                    overlay(MinecraftLocale.LOCALE_MAPPINGS, current, locale, applied);
                } catch (RuntimeException | LinkageError failure) {
                    if (!failureLogged) {
                        failureLogged = true;
                        logger.log(Level.WARNING, "Resource-pack translations could not be applied for this Geyser build.", failure);
                    }
                }
            }
            ((PacketTranslator<ClientboundLoginPacket>) original).translate(session, packet);
        }

        @Override public boolean shouldExecuteInEventLoop() {
            return original.shouldExecuteInEventLoop();
        }
    }
}
