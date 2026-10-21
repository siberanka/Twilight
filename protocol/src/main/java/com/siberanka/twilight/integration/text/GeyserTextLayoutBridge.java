/* Copyright (c) 2026 siberanka. Twilight License (PolyForm Noncommercial 1.0.0 + terms), see LICENSE. */
package com.siberanka.twilight.integration.text;

import com.siberanka.twilight.integration.TranslatorChainGuard;

import com.google.gson.JsonParser;
import com.siberanka.twilight.text.LayerEncoding;
import com.siberanka.twilight.text.TextLayout;
import com.siberanka.twilight.text.TextLayoutTable;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.event.EventRegistrar;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostInitializeEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostReloadEvent;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundBossEventPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundDisguisedChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundPlayerChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSystemChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEntityDataPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundOpenScreenPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.scoreboard.ClientboundSetObjectivePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.scoreboard.ClientboundSetPlayerTeamPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.scoreboard.ClientboundSetScorePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.title.ClientboundSetActionBarTextPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.title.ClientboundSetSubtitleTextPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.title.ClientboundSetTitleTextPacket;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipFile;

/**
 * Lays out Java text for Bedrock players with Java font metrics before Geyser translates it.
 *
 * <p>Chest titles always pass through the layout: the generated chest UI starts the title
 * label {@link TextLayoutTable#containerOrigin()} units left of Java's origin. The other
 * surfaces (chat, action bar, titles, boss bars, scoreboards, entity names and text
 * displays, other container titles) are rewritten only when they contain characters the
 * table knows, so custom-font glyphs, Java-remapped characters and space shifts look like
 * Java everywhere; other packets reach Geyser unchanged.
 *
 * <p>Geyser fills its translator registry during start-up and may reload packs, so the
 * bridge attaches after Geyser's initialize and reload events. The table comes from a
 * {@link LayoutSource}: the pack a server's own Geyser serves, or on a proxy the pack each
 * Bedrock session loaded for its server. Packet fields are read and replaced by name, because
 * Geyser relocates Adventure on some platforms; components are handled by {@link AdventureTextLayout}.
 */
public final class GeyserTextLayoutBridge implements AutoCloseable {
    /** The layout table and pack translation keys of one pack. */
    public record Layout(TextLayoutTable table, Set<String> packKeys) {}

    /** Where the layout of a Bedrock session's pack comes from. */
    public interface LayoutSource {
        /** Reads again what may have changed; called when Geyser initialises or reloads. */
        void refresh() throws IOException;

        /** The layout of the pack this session loaded, or null when it has none. */
        Layout layout(GeyserSession session);

        /** For the log: "for 12 Bedrock packet types (15 font entries)". */
        String describe();
    }

    /** The one pack a server's own Geyser serves. */
    public static LayoutSource servedPack(Path pack) {
        return new LayoutSource() {
            private volatile Layout layout;

            @Override public void refresh() throws IOException {
                TextLayoutTable table = readTable(pack);
                Set<String> keys = new java.util.HashSet<>();
                GeyserLanguageBridge.read(pack).values().forEach(strings -> keys.addAll(strings.keySet()));
                layout = table == null ? null : new Layout(table, Set.copyOf(keys));
            }

            @Override public Layout layout(GeyserSession session) {
                return layout;
            }

            @Override public String describe() {
                Layout current = layout;
                return current == null ? "idle: the served pack has no layout table" : current.table().entryCount() + " font entries";
            }
        };
    }

    /** The layout of a pack file, for sources that cache it per pack. */
    public static Layout readLayout(Path pack) throws IOException {
        TextLayoutTable table = readTable(pack);
        if (table == null) return null;
        Set<String> keys = new java.util.HashSet<>();
        GeyserLanguageBridge.read(pack).values().forEach(strings -> keys.addAll(strings.keySet()));
        return new Layout(table, Set.copyOf(keys));
    }

    private final LayoutSource source;
    private final Logger logger;
    private final EventRegistrar registrar;
    private final Map<Class<? extends Packet>, Surface<?>> surfaces = new LinkedHashMap<>();
    private final Set<String> loggedFailures = ConcurrentHashMap.newKeySet();

    private GeyserTextLayoutBridge(Object owner, LayoutSource source, Logger logger, boolean allSurfaces) {
        this.source = source;
        this.logger = logger;
        this.registrar = EventRegistrar.of(owner);
        surface(ClientboundOpenScreenPacket.class, this::openScreen);
        if (!allSurfaces) return;
        surface(ClientboundSystemChatPacket.class, (session, packet, table) -> packet.isOverlay()
                ? TextSurfaces.rewrite(packet, table, hud(session, TextLayoutTable.ACTIONBAR_LAYERS), TextLayout.Mode.CENTERED, "Content")
                : TextSurfaces.rewrite(packet, table, translations(session), TextLayout.Mode.LEFT, "Content"));
        surface(ClientboundPlayerChatPacket.class, (session, packet, table) -> TextSurfaces.rewrite(packet, table, translations(session), TextLayout.Mode.LEFT, "Content", "UnsignedContent", "Name", "TargetName"));
        surface(ClientboundDisguisedChatPacket.class, (session, packet, table) -> TextSurfaces.rewrite(packet, table, translations(session), TextLayout.Mode.LEFT, "Message", "Name", "TargetName"));
        surface(ClientboundSetActionBarTextPacket.class, (session, packet, table) -> TextSurfaces.rewrite(packet, table, hud(session, TextLayoutTable.ACTIONBAR_LAYERS), TextLayout.Mode.CENTERED, "Text"));
        surface(ClientboundSetTitleTextPacket.class, (session, packet, table) -> TextSurfaces.rewrite(packet, table, translations(session), TextLayout.Mode.CENTERED, "Text"));
        surface(ClientboundSetSubtitleTextPacket.class, (session, packet, table) -> TextSurfaces.rewrite(packet, table, translations(session), TextLayout.Mode.CENTERED, "Text"));
        surface(ClientboundBossEventPacket.class, this::bossBar);
        surface(ClientboundSetObjectivePacket.class, (session, packet, table) -> TextSurfaces.rewrite(packet, table, translations(session), TextLayout.Mode.CENTERED, "DisplayName"));
        surface(ClientboundSetPlayerTeamPacket.class, (session, packet, table) -> TextSurfaces.rewrite(packet, table, translations(session), TextLayout.Mode.LEFT, "DisplayName", "PlayerPrefix", "PlayerSuffix"));
        surface(ClientboundSetScorePacket.class, (session, packet, table) -> TextSurfaces.rewrite(packet, table, translations(session), TextLayout.Mode.LEFT, "Display"));
        surface(ClientboundSetEntityDataPacket.class, (session, packet, table) -> TextSurfaces.entityData(packet, table, translations(session)));
    }

    /**
     * Follows Geyser's lifecycle; text passes through unchanged while the served pack has no layout table.
     *
     * @param allSurfaces also lay out chat, titles, boss bars, scoreboards and entity names, not only containers
     */
    public static GeyserTextLayoutBridge create(Object owner, Path servedPack, Logger logger, boolean allSurfaces) {
        return create(owner, servedPack(servedPack), logger, allSurfaces);
    }

    /** As {@link #create(Object, Path, Logger, boolean)}, with the layout of each session from {@code source}. */
    public static GeyserTextLayoutBridge create(Object owner, LayoutSource source, Logger logger, boolean allSurfaces) {
        GeyserTextLayoutBridge bridge = new GeyserTextLayoutBridge(owner, source, logger, allSurfaces);
        com.siberanka.twilight.geyser.GeyserEvents.subscribe(bridge.registrar, GeyserPostInitializeEvent.class, event -> bridge.attach());
        com.siberanka.twilight.geyser.GeyserEvents.subscribe(bridge.registrar, GeyserPostReloadEvent.class, event -> bridge.attach());
        if (Registries.JAVA_PACKET_TRANSLATORS.get().get(ClientboundOpenScreenPacket.class) != null) bridge.attach();
        return bridge;
    }

    private <P extends Packet> void surface(Class<P> type, Rewrite<P> rewrite) {
        surfaces.put(type, new Surface<>(type, rewrite));
    }

    private synchronized void attach() {
        try {
            source.refresh();
        } catch (IOException | RuntimeException failure) {
            logger.log(Level.SEVERE, "Could not read the text layout of the served Twilight pack", failure);
        }
        var translators = Registries.JAVA_PACKET_TRANSLATORS.get();
        List<String> missing = new ArrayList<>();
        for (Surface<?> surface : surfaces.values()) {
            PacketTranslator<? extends Packet> current = translators.get(surface.type);
            if (current == null) {
                missing.add(surface.type.getSimpleName());
                continue;
            }
            // Keep the upstream typed-chain check and the local compatibility guard.
            if (!com.siberanka.twilight.geyser.TranslatorChain.installed(current, surface)
                    && !TranslatorChainGuard.contains(current, surface)) {
                surface.original = current;
                translators.put(surface.type, surface);
            }
        }
        if (!missing.isEmpty()) logger.warning("Geyser has no translator for " + missing + "; their text keeps Bedrock's layout.");
        logger.info("Java text layout active for " + (surfaces.size() - missing.size()) + " Bedrock packet types ("
                + source.describe() + ").");
    }

    /**
     * Pack strings for translation keys the resource packs define, in the session's language with Java's
     * fallback order (Geyser's language maps carry them, see {@link GeyserLanguageBridge}); null when none.
     */
    private java.util.function.Function<String, String> translations(GeyserSession session) {
        Layout layout = source.layout(session);
        Set<String> keys = layout == null ? Set.of() : layout.packKeys();
        if (keys.isEmpty()) return null;
        String locale = session.locale() == null ? "en_us" : session.locale();
        return key -> keys.contains(key) ? org.geysermc.geyser.text.MinecraftLocale.getLocaleString(key, locale) : null;
    }

    /**
     * Colour, overlay and last Java title of every boss bar of a session: titles arrive without the
     * style after the first packet, and a style change must re-send the title with the new marker.
     * Sessions are weak keys, so bars of closed sessions are released.
     */
    private final Map<GeyserSession, Map<java.util.UUID, BossBarState>> bossBars =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    /** Packets a rewrite asks to translate after the current one (same thread). */
    private final ThreadLocal<List<Packet>> followUps = ThreadLocal.withInitial(ArrayList::new);

    private static final class BossBarState {
        int colour = -1;
        int overlay;
        Object title;
        String marker = "";
    }

    private ClientboundBossEventPacket bossBar(GeyserSession session, ClientboundBossEventPacket packet,
                                               TextLayoutTable table) throws ReflectiveOperationException {
        Map<java.util.UUID, BossBarState> bars = bossBars.computeIfAbsent(session, ignored -> new ConcurrentHashMap<>());
        if (packet.getAction() == org.geysermc.mcprotocollib.protocol.data.game.BossBarAction.REMOVE) {
            bars.remove(packet.getUuid());
            return packet;
        }
        BossBarState state = bars.computeIfAbsent(packet.getUuid(), ignored -> new BossBarState());
        if (packet.getColor() != null) state.colour = packet.getColor().ordinal();
        if (packet.getDivision() != null) state.overlay = packet.getDivision().ordinal();
        String colour = state.colour < 0 || state.colour >= BOSS_BAR_COLOURS.size() ? "" : BOSS_BAR_COLOURS.get(state.colour);
        String lead = table.bossBarHidden(colour) ? LayerEncoding.HIDDEN_BAR
                : table.bossBarStyled(colour) ? LayerEncoding.styledBar(state.colour, Math.min(state.overlay, 9)) : "";
        // By name: Geyser relocates Adventure on some platforms, so getTitle() cannot be linked directly.
        Object title = TextSurfaces.get(packet, "Title");
        if (title == null) {
            // A style change: Geyser updates only the colour, so the marker in the name is sent again.
            if (!lead.equals(state.marker) && state.title != null) {
                followUps.get().add(TextSurfaces.with(
                        packet.withAction(org.geysermc.mcprotocollib.protocol.data.game.BossBarAction.UPDATE_TITLE),
                        "Title", state.title));
            }
            return packet;
        }
        state.title = title;
        state.marker = lead;
        // Layered names start with their own marker: the HUD shows other names whole, however long.
        java.util.function.Function<List<Object>, Object> encode = layers(session, table, TextLayoutTable.BOSS_LAYERS,
                LayerEncoding.LAYERED + lead);
        boolean[] layered = {false};
        java.util.function.Function<List<Object>, Object> tracked = encode == null ? null : components -> {
            Object encoded = encode.apply(components);
            layered[0] = encoded != null;
            return encoded;
        };
        TextSurfaces.Options options = new TextSurfaces.Options(translations(session), false, tracked, 1, true);
        ClientboundBossEventPacket laidOut = TextSurfaces.rewrite(packet, table, options, TextLayout.Mode.CENTERED, "Title");
        // Layered titles carry the marker in their first block; others get it in front.
        return lead.isEmpty() || layered[0] ? laidOut : TextSurfaces.prepend(laidOut, "Title", lead);
    }

    /** Java's boss bar colour names in protocol order. */
    private static final List<String> BOSS_BAR_COLOURS = List.of("pink", "blue", "red", "green", "yellow", "purple", "white");

    /** Action bar and boss bar options: the generated HUD shows layered lines. */
    private TextSurfaces.Options hud(GeyserSession session, String surface) {
        // The action bar label is centred vertically: half of every added line moves it up.
        int lines = TextLayoutTable.ACTIONBAR_LAYERS.equals(surface) ? 2 : 1;
        Layout layout = source.layout(session);
        return new TextSurfaces.Options(translations(session), false,
                layers(session, layout == null ? null : layout.table(), surface, ""), lines, true);
    }

    /**
     * Encodes the layers of a line for a surface whose generated UI shows them, or null. The result is
     * plain text that Geyser converts to exactly the encoded bytes (checked with Geyser's own conversion,
     * otherwise the line keeps the single-label layout).
     */
    private java.util.function.Function<List<Object>, Object> layers(GeyserSession session, TextLayoutTable current,
                                                                       String surface, String lead) {
        if (current == null || !current.layers(surface)) return null;
        boolean bossBar = TextLayoutTable.BOSS_LAYERS.equals(surface);
        int block = LayerEncoding.BLOCK_BYTES.get(surface);
        int first = LayerEncoding.FIRST_BLOCK_BYTES.get(surface);
        int characters = bossBar ? LayerEncoding.BOSS_CHARACTERS : Integer.MAX_VALUE;
        String locale = session.locale() == null ? "en_us" : session.locale();
        return components -> {
            try {
                List<String> legacy = new ArrayList<>();
                for (Object layer : components) legacy.add(GeyserMessages.convert(layer, locale, false));
                String encoded = LayerEncoding.encode(legacy, first, block, characters, lead);
                // Geyser escapes percent signs in boss bar names, which would move the block boundaries.
                if (encoded == null || bossBar && encoded.indexOf('%') >= 0) return null;
                Object candidate = AdventureTextLayout.plainText(components.getFirst(), encoded);
                return ("\u00a7r" + encoded).equals(GeyserMessages.convert(candidate, locale, true)) ? candidate : null;
            } catch (ReflectiveOperationException | RuntimeException failure) {
                logOnce(LayerEncoding.class, failure);
                return null;
            }
        };
    }

    /** Geyser's Java-to-Bedrock text conversion, resolved by name (Geyser may relocate Adventure). */
    private static final class GeyserMessages {
        private static final Map<String, java.lang.reflect.Method> METHODS = new ConcurrentHashMap<>();

        static String convert(Object component, String locale, boolean leadingReset) throws ReflectiveOperationException {
            String name = leadingReset ? "convertMessage" : "convertMessageRaw";
            java.lang.reflect.Method method = METHODS.get(name);
            if (method == null) {
                for (java.lang.reflect.Method candidate : org.geysermc.geyser.translator.text.MessageTranslator.class.getMethods()) {
                    Class<?>[] parameters = candidate.getParameterTypes();
                    if (candidate.getName().equals(name) && parameters.length == 2 && parameters[1] == String.class
                            && parameters[0].isInstance(component)) method = candidate;
                }
                if (method == null) throw new NoSuchMethodException("MessageTranslator." + name);
                METHODS.put(name, method);
            }
            return (String) method.invoke(null, component, locale);
        }
    }

    static TextLayoutTable readTable(Path pack) throws IOException {
        if (!Files.isRegularFile(pack)) return null;
        try (ZipFile zip = new ZipFile(pack.toFile())) {
            var entry = zip.getEntry(TextLayoutTable.PATH);
            if (entry == null) return null;
            if (entry.getSize() > 64L * 1024 * 1024) throw new IOException("Text layout table exceeds size limit");
            try (Reader reader = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
                return TextLayoutTable.fromJson(JsonParser.parseReader(reader).getAsJsonObject());
            }
        }
    }

    @Override public synchronized void close() {
        com.siberanka.twilight.geyser.GeyserEvents.unregisterAll(registrar);
        var translators = Registries.JAVA_PACKET_TRANSLATORS.get();
        for (Surface<?> surface : surfaces.values()) {
            if (surface.original != null) translators.replace(surface.type, surface, surface.original);
        }
    }

    private ClientboundOpenScreenPacket openScreen(GeyserSession session, ClientboundOpenScreenPacket packet,
                                                   TextLayoutTable table) throws ReflectiveOperationException {
        // Bedrock's own pocket layout keeps its centred native title; only glyph substitution applies there.
        // A pack whose pocket chest screens use the Java layout lays their titles out like desktop ones.
        var profile = session.getClientData() == null ? null : SafeUiProfile.get(session.getClientData());
        boolean pocket = profile != null && "POCKET".equals(profile.toString()) && !table.pocketJavaLayout();
        if (!pocket && table.plainTitles() && TextSurfaces.CHEST_SCREENS.contains(packet.getType())) {
            return chestTitle(session, packet, table);
        }
        return TextSurfaces.openScreen(packet, table, pocket, translations(session),
                layers(session, table, TextLayoutTable.CHEST_LAYERS, ""));
    }

    /**
     * A chest title for a pack with plain title labels (see {@link TextLayoutTable#PLAIN_TITLE_BYTES}): a title
     * without custom characters goes out as it is and shows at Java's position in its own label; a laid-out
     * title follows a block of zero-width codes, so that only the labels left of Java's origin show it.
     */
    private ClientboundOpenScreenPacket chestTitle(GeyserSession session, ClientboundOpenScreenPacket packet,
                                                   TextLayoutTable table) throws ReflectiveOperationException {
        Object title = TextSurfaces.get(packet, "Title");
        if (!AdventureTextLayout.isComponent(title)) return packet;
        String locale = session.locale() == null ? "en_us" : session.locale();
        var translations = translations(session);
        Object plain = AdventureTextLayout.plainTitle(table, title, translations);
        if (plain != null && LayerEncoding.bytes(GeyserMessages.convert(plain, locale, true)) <= TextLayoutTable.PLAIN_TITLE_BYTES) {
            return plain == title ? packet : TextSurfaces.with(packet, "Title", plain);
        }
        ClientboundOpenScreenPacket laidOut = TextSurfaces.openScreen(packet, table, false, translations,
                layers(session, table, TextLayoutTable.CHEST_LAYERS, ""));
        // Geyser's leading reset and the filler make the plain block; the reset after it starts the laid-out text
        // as Geyser's own reset started it before, so the layer blocks keep their offsets.
        String filler = LayerEncoding.filler(TextLayoutTable.PLAIN_TITLE_BYTES - LayerEncoding.bytes(LayerEncoding.CLIENT_PREFIX));
        Object candidate = AdventureTextLayout.prepend(TextSurfaces.get(laidOut, "Title"), filler + LayerEncoding.CLIENT_PREFIX);
        String converted = GeyserMessages.convert(candidate, locale, true);
        if (converted.startsWith(LayerEncoding.CLIENT_PREFIX + filler + LayerEncoding.CLIENT_PREFIX)) {
            return TextSurfaces.with(laidOut, "Title", candidate);
        }
        // Geyser rewrote the zero-width block: the title is shown as Java sent it rather than off-screen.
        return packet;
    }

    private void logOnce(Class<?> type, Throwable failure) {
        if (loggedFailures.add(type.getName())) {
            logger.log(Level.WARNING, "Java text layout failed for " + type.getSimpleName()
                    + "; such text is shown unchanged. Further failures of this kind are not logged.", failure);
        }
    }

    @FunctionalInterface
    interface Rewrite<P> {
        P apply(GeyserSession session, P packet, TextLayoutTable table) throws ReflectiveOperationException;
    }

    private final class Surface<P extends Packet> extends PacketTranslator<P> implements com.siberanka.twilight.geyser.TranslatorChain.Link {
        private final Class<P> type;
        private final Rewrite<P> rewrite;
        private volatile PacketTranslator<? extends Packet> original;

        private Surface(Class<P> type, Rewrite<P> rewrite) {
            this.type = type;
            this.rewrite = rewrite;
        }

        @SuppressWarnings("unchecked")
        @Override public void translate(GeyserSession session, P packet) {
            P forwarded = packet;
            Layout layout = null;
            try {
                layout = source.layout(session);
            } catch (RuntimeException failure) {
                logOnce(LayoutSource.class, failure);
            }
            TextLayoutTable current = layout == null ? null : layout.table();
            if (current != null) {
                try {
                    forwarded = rewrite.apply(session, packet, current);
                } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                    logOnce(type, failure);
                }
            }
            ((PacketTranslator<P>) original).translate(session, forwarded);
            List<Packet> pending = followUps.get();
            if (pending.isEmpty()) return;
            List<Packet> next = List.copyOf(pending);
            pending.clear();
            for (Packet followUp : next) {
                if (type.isInstance(followUp)) translate(session, type.cast(followUp));
            }
        }

        @Override public boolean shouldExecuteInEventLoop() {
            return original.shouldExecuteInEventLoop();
        }

        @Override public Object next() {
            return original;
        }
    }
}
