/* Copyright (c) 2026 siberanka. Twilight License (PolyForm Noncommercial 1.0.0 + terms), see LICENSE. */
package com.siberanka.twilight.geyser;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.geysermc.geyser.api.event.EventRegistrar;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomSkullsEvent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.zip.ZipFile;

/**
 * Player heads with their skins in Bedrock inventories, menus, hands and holograms. Without a custom skull block a
 * Bedrock player head is Steve's; Geyser turns a registered skin into a custom block whose item draws that skin, but
 * only for skins it registered when it started. The skins come from the packs ({@value #PATH}: the heads a server's
 * menus, configurations and players showed), on a backend from its own pack, on a proxy from every server's.
 */
public final class CustomSkulls implements AutoCloseable {
    public static final String PATH = "twilight/player_heads.json";
    /**
     * The skins of the players who joined a server, most recent first: heads of players ({@code head-%player%},
     * top lists) show their skins too. They never ask for a restart; Geyser takes them at its next start.
     */
    public static final String PLAYERS_PATH = "twilight/player_skins.json";
    /** Upper bound on registered skulls: each is a Bedrock block, and clients load every one of them. */
    public static final int MAX = 4096;
    private static final long MAX_BYTES = 4L * 1024 * 1024;

    /** The skins (hashes) and user names of a set of packs, and the skins of their servers' players. */
    public record Heads(Set<String> skins, Set<String> names, Set<String> players) {
        static final Heads EMPTY = new Heads(Set.of(), Set.of(), Set.of());

        public int size() {
            return skins.size() + names.size() + players.size();
        }

        /** Whether the heads the servers show (not their players' skins) are the same. */
        public boolean sameShown(Heads other) {
            return skins.equals(other.skins) && names.equals(other.names);
        }
    }

    private final EventRegistrar registrar;
    private final Supplier<Collection<Path>> packs;
    private final Consumer<String> info;
    private final BiConsumer<String, Throwable> warn;
    private volatile Heads registered;

    private CustomSkulls(Object owner, Supplier<Collection<Path>> packs, Consumer<String> info, BiConsumer<String, Throwable> warn) {
        this.registrar = EventRegistrar.of(owner);
        this.packs = packs;
        this.info = info;
        this.warn = warn;
    }

    /** Subscribes to Geyser's custom skull definition; call before Geyser starts, as for custom blocks. */
    public static CustomSkulls start(Object owner, Supplier<Collection<Path>> packs, Consumer<String> info,
                                     BiConsumer<String, Throwable> warn) {
        CustomSkulls skulls = new CustomSkulls(owner, packs, info, warn);
        GeyserEvents.subscribe(skulls.registrar, GeyserDefineCustomSkullsEvent.class, skulls::define);
        return skulls;
    }

    /** What Geyser registered when it started, or null before. */
    public Heads registered() {
        return registered;
    }

    /** The heads the packs list now (for telling whether Geyser needs a restart). */
    public Heads current() {
        return read(packs.get());
    }

    private void define(GeyserDefineCustomSkullsEvent event) {
        Heads heads = read(packs.get());
        int count = 0;
        for (String skin : heads.skins()) {
            try {
                event.register(skin, GeyserDefineCustomSkullsEvent.SkullTextureType.SKIN_HASH);
                count++;
            } catch (RuntimeException failure) {
                warn.accept("Could not register the player head " + skin + " with Geyser", failure);
            }
        }
        for (String skin : heads.players()) {
            try {
                event.register(skin, GeyserDefineCustomSkullsEvent.SkullTextureType.SKIN_HASH);
                count++;
            } catch (RuntimeException failure) {
                warn.accept("Could not register the skin " + skin + " with Geyser", failure);
            }
        }
        for (String name : heads.names()) {
            try {
                event.register(name, GeyserDefineCustomSkullsEvent.SkullTextureType.USERNAME);
                count++;
            } catch (RuntimeException failure) {
                warn.accept("Could not register the player head of " + name + " with Geyser", failure);
            }
        }
        registered = heads;
        if (count > 0) info.accept("Registered " + count + " player head skin(s) for Bedrock players.");
    }

    /**
     * The heads of every pack, merged; a pack that cannot be read adds none. The heads servers show come first; the
     * players' skins fill what is left of {@link #MAX}, taken round-robin from the packs (each most recent first).
     */
    public static Heads read(Collection<Path> packs) {
        Set<String> skins = new TreeSet<>(), names = new TreeSet<>();
        List<List<String>> players = new ArrayList<>();
        for (Path pack : packs) {
            if (pack == null || !Files.isRegularFile(pack)) continue;
            try (ZipFile zip = new ZipFile(pack.toFile())) {
                JsonObject root = json(zip, PATH);
                if (root != null && root.has("skins")) for (JsonElement value : root.getAsJsonArray("skins")) {
                    String skin = value.getAsString();
                    if (HeadTextures.valid(skin) && skins.size() + names.size() < MAX) skins.add(skin);
                }
                if (root != null && root.has("names")) for (JsonElement value : root.getAsJsonArray("names")) {
                    String name = value.getAsString();
                    if (HeadTextures.name(name) && skins.size() + names.size() < MAX) names.add(name);
                }
                JsonObject joined = json(zip, PLAYERS_PATH);
                if (joined != null && joined.has("skins")) {
                    List<String> list = new ArrayList<>();
                    for (JsonElement value : joined.getAsJsonArray("skins")) {
                        String skin = value.getAsString();
                        if (HeadTextures.valid(skin) && list.size() < MAX) list.add(skin);
                    }
                    players.add(list);
                }
            } catch (IOException | RuntimeException unreadable) {
                // Another server's pack, or one being replaced: its heads come with the next read.
            }
        }
        Set<String> recent = new LinkedHashSet<>();
        for (int index = 0; skins.size() + names.size() + recent.size() < MAX; index++) {
            boolean any = false;
            for (List<String> list : players) {
                if (index >= list.size()) continue;
                any = true;
                if (!skins.contains(list.get(index))) recent.add(list.get(index));
                if (skins.size() + names.size() + recent.size() >= MAX) break;
            }
            if (!any) break;
        }
        if (skins.isEmpty() && names.isEmpty() && recent.isEmpty()) return Heads.EMPTY;
        return new Heads(Set.copyOf(skins), Set.copyOf(names), java.util.Collections.unmodifiableSet(recent));
    }

    private static JsonObject json(ZipFile zip, String path) throws IOException {
        var entry = zip.getEntry(path);
        if (entry == null || entry.getSize() > MAX_BYTES) return null;
        byte[] bytes;
        try (InputStream input = zip.getInputStream(entry)) {
            bytes = input.readNBytes((int) MAX_BYTES + 1);
        }
        if (bytes.length > MAX_BYTES) return null;
        return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Override public void close() {
        try {
            org.geysermc.geyser.api.GeyserApi.api().eventBus().unregisterAll(registrar);
        } catch (RuntimeException | LinkageError gone) {
            // Geyser is shutting down as well.
        }
    }
}
