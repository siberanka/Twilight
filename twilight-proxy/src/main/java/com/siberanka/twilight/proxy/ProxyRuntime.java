/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.proxy;

import com.siberanka.twilight.geyser.CustomBlocks;
import com.siberanka.twilight.geyser.LoadingGuard;
import com.siberanka.twilight.integration.display.DisplayIndex;
import com.siberanka.twilight.integration.display.GeyserDisplayBridge;
import com.siberanka.twilight.integration.display.GeyserRiderNames;
import com.siberanka.twilight.integration.text.GeyserLanguageBridge;
import com.siberanka.twilight.integration.text.GeyserTextLayoutBridge;
import org.geysermc.geyser.session.GeyserSession;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Twilight's runtime on the proxy's Geyser, the part a backend's own Geyser would otherwise run: the Java text
 * layout (custom font glyphs, menu titles, aliases of ordinary characters and named fonts, spacing), the packs'
 * translations, keeping clients alive while they load large packs, custom blocks, Java item displays (furniture,
 * ModelEngine and BetterModel bones) and Java's rider name rule. Each Bedrock session gets the layout and display
 * variants of the pack it loaded for its server. Only this class and the bridges it starts touch Geyser's internals.
 */
final class ProxyRuntime implements AutoCloseable {
    private static final int MAX_CACHED = 64;

    private final GeyserTextLayoutBridge text;
    private final GeyserLanguageBridge language;
    private final LoadingGuard guard;
    private volatile GeyserDisplayBridge displays;
    private volatile GeyserRiderNames riderNames;
    private volatile SessionDisplays sessionDisplays;
    private final DisplaySightings sightings = new DisplaySightings();

    private ProxyRuntime(GeyserTextLayoutBridge text, GeyserLanguageBridge language, LoadingGuard guard) {
        this.text = text;
        this.language = language;
        this.guard = guard;
    }

    /**
     * Starts what {@code config} enables. {@code packOf} gives the pack file a Bedrock session (by XUID) loaded,
     * {@code packs} every server's current pack.
     */
    static ProxyRuntime start(Object owner, ProxyConfig config, Platform platform, Function<String, Optional<Path>> packOf,
                              Supplier<Collection<Path>> packs) {
        return start(owner, config, platform, packOf, packs, null, null);
    }

    /**
     * @param registered      custom blocks already subscribed in the proxy's load phase, or null
     * @param earlyDisplays   the display bridge subscribed in the load phase with its variant holder, or null
     */
    static ProxyRuntime start(Object owner, ProxyConfig config, Platform platform, Function<String, Optional<Path>> packOf,
                              Supplier<Collection<Path>> packs, CustomBlocks registered, EarlyDisplays earlyDisplays) {
        Logger logger = logger(platform);
        GeyserTextLayoutBridge text = null;
        GeyserLanguageBridge language = null;
        LoadingGuard guard = null;
        try {
            if (config.textLayout()) text = GeyserTextLayoutBridge.create(owner, new SessionLayouts(packOf), logger, true);
        } catch (RuntimeException | LinkageError failure) {
            platform.warn("Java text layout is unavailable for this Geyser build; custom font glyphs keep Bedrock's layout.", failure);
        }
        try {
            if (config.translations()) language = GeyserLanguageBridge.create(owner, packs, logger);
        } catch (RuntimeException | LinkageError failure) {
            platform.warn("Resource-pack translations are unavailable for this Geyser build.", failure);
        }
        try {
            guard = LoadingGuard.start(owner, config.loadingProtectionSeconds(), platform::info);
        } catch (RuntimeException | LinkageError failure) {
            platform.warn("Loading protection is unavailable for this Geyser build.", failure);
        }
        ProxyRuntime runtime = new ProxyRuntime(text, language, guard);
        try {
            if (registered != null) runtime.blocks = registered;
            else if (config.customBlocks()) runtime.blocks = CustomBlocks.start(owner, packs, platform::info, platform::warn);
        } catch (RuntimeException | LinkageError failure) {
            platform.warn("Custom blocks are unavailable for this Geyser build; they keep the vanilla look on Bedrock.", failure);
        }
        if (config.itemDisplays()) {
            try {
                SessionDisplays variants = new SessionDisplays(packOf);
                runtime.sessionDisplays = variants;
                if (earlyDisplays != null) {
                    earlyDisplays.variants = variants;
                    runtime.displays = earlyDisplays.bridge;
                } else {
                    runtime.displays = new GeyserDisplayBridge(owner, variants, logger, true);
                }
                DisplaySightings sightings = runtime.sightings;
                runtime.displays.onUnshown((session, keys) -> {
                    java.util.UUID player = session.javaUuid();
                    if (player != null) platform.currentServer(player).ifPresent(server -> sightings.seen(server, keys));
                });
            } catch (RuntimeException | LinkageError failure) {
                platform.warn("Item displays are unavailable for this Geyser build; furniture and model bones stay"
                        + " invisible for Bedrock players.", failure);
            }
        }
        try {
            runtime.riderNames = GeyserRiderNames.create(owner, logger);
        } catch (RuntimeException | LinkageError failure) {
            platform.warn("Java's rider name rule is unavailable for this Geyser build.", failure);
        }
        return runtime;
    }

    /**
     * The display bridge subscribed in the proxy's load phase (BungeeCord forks start Geyser before this plugin
     * enables); its variants follow the sessions' packs once the runtime starts.
     */
    static final class EarlyDisplays implements GeyserDisplayBridge.Variants {
        volatile GeyserDisplayBridge.Variants variants = session -> DisplayIndex.EMPTY;
        GeyserDisplayBridge bridge;

        @Override public DisplayIndex of(GeyserSession session) {
            return variants.of(session);
        }
    }

    /** Whether Java item displays are shown: Geyser registered the display entity with this runtime. */
    boolean showsItemDisplays() {
        GeyserDisplayBridge bridge = displays;
        return bridge != null && bridge.registered();
    }

    /** Items seen in displays without a model, to report to their servers. */
    DisplaySightings sightings() {
        return sightings;
    }

    /** Reads a new pack's display variants off the event loop, before a session needs them. */
    void prepare(Path pack) {
        SessionDisplays variants = sessionDisplays;
        if (variants != null) variants.load(pack);
    }

    private volatile CustomBlocks blocks;

    /**
     * The servers' packs changed: their translations are read again.
     *
     * @return a description of the custom blocks when they differ from what Geyser registered at start (Geyser
     *         registers blocks only then), otherwise null
     */
    String packsChanged() {
        if (language != null) language.refresh();
        CustomBlocks custom = blocks;
        CustomBlocks.Merged registered = custom == null ? null : custom.registered();
        if (registered == null) return null;
        CustomBlocks.Merged now = custom.current();
        return now.hash().equals(registered.hash()) ? null : now.blocks().size() + " custom block(s)";
    }

    @Override
    public void close() {
        for (AutoCloseable part : new AutoCloseable[]{text, language, guard, blocks, displays, riderNames}) {
            if (part == null) continue;
            try {
                part.close();
            } catch (Exception ignored) {
                // Geyser is shutting down as well.
            }
        }
    }

    /** The layout of the pack each session loaded, read once per pack version (versions never change). */
    private static final class SessionLayouts implements GeyserTextLayoutBridge.LayoutSource {
        private static final GeyserTextLayoutBridge.Layout NONE = new GeyserTextLayoutBridge.Layout(null, java.util.Set.of());
        private final Function<String, Optional<Path>> packOf;
        private final Map<Path, GeyserTextLayoutBridge.Layout> layouts = new ConcurrentHashMap<>();

        SessionLayouts(Function<String, Optional<Path>> packOf) {
            this.packOf = packOf;
        }

        @Override public void refresh() {
            layouts.clear();
        }

        @Override public GeyserTextLayoutBridge.Layout layout(GeyserSession session) {
            String xuid = session.xuid();
            if (xuid == null) return null;
            Optional<Path> pack = packOf.apply(xuid);
            if (pack.isEmpty()) return null;
            GeyserTextLayoutBridge.Layout layout = layouts.get(pack.get());
            if (layout == null) {
                try {
                    layout = GeyserTextLayoutBridge.readLayout(pack.get());
                } catch (IOException | RuntimeException unreadable) {
                    layout = null;
                }
                if (layouts.size() >= MAX_CACHED) layouts.clear();
                // A pack without a layout table (a server without custom fonts) is remembered as NONE.
                if (layout == null) layout = NONE;
                layouts.put(pack.get(), layout);
            }
            return layout == NONE || layout.table() == null ? null : layout;
        }

        @Override public String describe() {
            return "the layout of each server's pack";
        }
    }

    /** The display variants of the pack each session loaded, read once per pack version (versions never change). */
    static final class SessionDisplays implements GeyserDisplayBridge.Variants {
        private final Function<String, Optional<Path>> packOf;
        private final Map<Path, DisplayIndex> variants = new ConcurrentHashMap<>();

        SessionDisplays(Function<String, Optional<Path>> packOf) {
            this.packOf = packOf;
        }

        @Override public DisplayIndex of(GeyserSession session) {
            String xuid = session.xuid();
            if (xuid == null || xuid.isEmpty()) return DisplayIndex.EMPTY;
            Optional<Path> pack = packOf.apply(xuid);
            return pack.isEmpty() ? DisplayIndex.EMPTY : load(pack.get());
        }

        DisplayIndex load(Path pack) {
            DisplayIndex known = variants.get(pack);
            if (known != null) return known;
            DisplayIndex read;
            try {
                read = DisplayIndex.read(pack);
            } catch (IOException | RuntimeException unreadable) {
                // A malformed or hostile index shows nothing rather than wrong models.
                read = DisplayIndex.EMPTY;
            }
            if (variants.size() >= MAX_CACHED) variants.clear();
            variants.put(pack, read);
            return read;
        }
    }

    /** The bridges log through java.util.logging; this forwards to the proxy's log. */
    private static Logger logger(Platform platform) {
        return logger(platform::info, platform::warn);
    }

    static Logger logger(java.util.function.Consumer<String> info, java.util.function.BiConsumer<String, Throwable> warn) {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override public void publish(LogRecord record) {
                String message = record.getMessage();
                if (record.getLevel().intValue() >= Level.WARNING.intValue()) warn.accept(message, record.getThrown());
                else info.accept(message);
            }

            @Override public void flush() {
            }

            @Override public void close() {
            }
        });
        return logger;
    }
}
