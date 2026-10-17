/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.geyser;

import org.geysermc.geyser.api.connection.GeyserConnection;
import org.geysermc.geyser.api.event.EventRegistrar;
import org.geysermc.geyser.api.event.bedrock.SessionDisconnectEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostInitializeEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostReloadEvent;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundPingPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundPongPacket;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Keeps a Bedrock player connected while the client is still loading its resource packs.
 *
 * <p>After the packs are downloaded, the Bedrock client builds them into its world (textures, models, fonts)
 * before it is in game; a large pack takes minutes on a phone. Geyser has already logged the player in to the
 * Java server by then. With Geyser's {@code forward-player-ping}, Java keep-alives and pings wait for the busy
 * client, so the Java server or the proxy drops the player ("Timed out", "read timed out"). Until the client reports that it is
 * in game, for at most {@code limit}, this answers them at once, as Geyser does without ping forwarding. It
 * also logs how long each client took and why one left while still loading, so a pack that is too heavy for
 * the players' devices shows in the console.
 *
 * <p>Without ping forwarding nothing is answered here: Geyser's protocol library already answers keep-alives
 * and Geyser answers pings itself, and a second answer makes Paper drop the player ("keepalive response
 * without matching challenge").
 */
public final class LoadingGuard implements AutoCloseable {
    private final long limitMillis;
    private final Consumer<String> log;
    private final LongSupplier clock;
    private final EventRegistrar registrar;
    private final Wrapper<ClientboundKeepAlivePacket> keepAlive;
    private final Wrapper<ClientboundPingPacket> ping;
    /** Sessions seen while loading: when, and whether they were answered for. Weak keys release closed sessions. */
    private final Map<GeyserSession, long[]> loading = new WeakHashMap<>();

    private LoadingGuard(Object owner, long limitMillis, Consumer<String> log, LongSupplier clock) {
        this.limitMillis = limitMillis;
        this.log = log;
        this.clock = clock;
        this.registrar = EventRegistrar.of(owner);
        this.keepAlive = new Wrapper<>((session, packet) -> session.sendDownstreamPacket(new ServerboundKeepAlivePacket(packet.getPingId())));
        this.ping = new Wrapper<>((session, packet) -> session.sendDownstreamPacket(new ServerboundPongPacket(packet.getId())));
    }

    /** Starts guarding; {@code limitSeconds} 0 only logs loading times and disconnects. */
    public static LoadingGuard start(Object owner, int limitSeconds, Consumer<String> log) {
        LoadingGuard guard = new LoadingGuard(owner, Math.max(0, limitSeconds) * 1000L, log, System::currentTimeMillis);
        GeyserEvents.subscribe(guard.registrar, GeyserPostInitializeEvent.class, event -> guard.attach());
        GeyserEvents.subscribe(guard.registrar, GeyserPostReloadEvent.class, event -> guard.attach());
        GeyserEvents.subscribe(guard.registrar, SessionDisconnectEvent.class, guard::disconnected);
        if (Registries.JAVA_PACKET_TRANSLATORS.get().get(ClientboundKeepAlivePacket.class) != null) guard.attach();
        return guard;
    }

    private synchronized void attach() {
        var translators = Registries.JAVA_PACKET_TRANSLATORS.get();
        install(translators, ClientboundKeepAlivePacket.class, keepAlive);
        install(translators, ClientboundPingPacket.class, ping);
    }

    @SuppressWarnings("unchecked")
    private static <P extends Packet> void install(Map<Class<? extends Packet>, PacketTranslator<? extends Packet>> translators,
                                                   Class<P> type, Wrapper<P> wrapper) {
        PacketTranslator<? extends Packet> current = translators.get(type);
        if (current == null || TranslatorChain.installed(current, wrapper)) return;
        wrapper.original = (PacketTranslator<P>) current;
        translators.put(type, wrapper);
    }

    /**
     * Whether a keep-alive or ping is answered for a client that is still loading: only when Geyser forwards them
     * to the client (otherwise they are answered already), and only within the limit since loading began.
     */
    static boolean answers(boolean forwarding, long loadingSince, long now, long limitMillis) {
        return forwarding && limitMillis > 0 && now - loadingSince <= limitMillis;
    }

    /** True when the packet was answered here because the client is still loading. */
    private boolean answerWhileLoading(GeyserSession session) {
        long now = clock.getAsLong();
        boolean forwarding = forwardsPing(session);
        synchronized (loading) {
            if (loaded(session)) {
                long[] state = loading.remove(session);
                if (state != null && now - state[0] >= 10_000) {
                    log.accept(name(session) + " finished loading its resource packs after " + (now - state[0]) / 1000 + " s"
                            + (state[1] > 0 ? "; the connection was kept alive for it meanwhile." : "."));
                }
                return false;
            }
            long[] state = loading.computeIfAbsent(session, ignored -> new long[]{now, 0});
            if (!answers(forwarding, state[0], now, limitMillis)) return false;
            state[1]++;
            return true;
        }
    }

    private void disconnected(SessionDisconnectEvent event) {
        GeyserConnection connection = event.connection();
        if (!(connection instanceof GeyserSession session)) return;
        long[] state;
        synchronized (loading) {
            state = loading.remove(session);
        }
        if (state == null || loaded(session)) return;
        long seconds = (clock.getAsLong() - state[0]) / 1000;
        log.accept(name(session) + " left while its client was still loading the resource packs, after " + seconds + " s"
                + (limitMillis > 0 && seconds * 1000 > limitMillis ? " (longer than the protection of " + limitMillis / 1000 + " s)" : "")
                + ": " + event.disconnectReason());
    }

    /**
     * The client reported that it is in game (Bedrock's SetLocalPlayerAsInitialized). Geyser's own "spawned" flag
     * is set when the Java server places the player, long before a busy client has finished loading.
     */
    private static boolean loaded(GeyserSession session) {
        var upstream = session.getUpstream();
        return upstream != null && upstream.isInitialized();
    }

    /** Geyser's {@code forward-player-ping}: keep-alives and pings wait for the Bedrock client. */
    private static boolean forwardsPing(GeyserSession session) {
        try {
            return session.getGeyser().config().gameplay().forwardPlayerPing();
        } catch (RuntimeException | LinkageError unknown) {
            // Unknown config layout: never risk a second answer.
            return false;
        }
    }

    private static String name(GeyserSession session) {
        String name = session.bedrockUsername();
        return name == null ? "A Bedrock player" : name;
    }

    @Override
    public synchronized void close() {
        GeyserEvents.unregisterAll(registrar);
        var translators = Registries.JAVA_PACKET_TRANSLATORS.get();
        if (keepAlive.original != null) translators.replace(ClientboundKeepAlivePacket.class, keepAlive, keepAlive.original);
        if (ping.original != null) translators.replace(ClientboundPingPacket.class, ping, ping.original);
    }

    private final class Wrapper<P extends Packet> extends PacketTranslator<P> implements TranslatorChain.Link {
        private final java.util.function.BiConsumer<GeyserSession, P> answer;
        private volatile PacketTranslator<P> original;

        private Wrapper(java.util.function.BiConsumer<GeyserSession, P> answer) {
            this.answer = answer;
        }

        @Override
        public void translate(GeyserSession session, P packet) {
            if (answerWhileLoading(session)) {
                answer.accept(session, packet);
                return;
            }
            original.translate(session, packet);
        }

        @Override
        public boolean shouldExecuteInEventLoop() {
            return original == null || original.shouldExecuteInEventLoop();
        }

        @Override
        public Object next() {
            return original;
        }
    }
}
