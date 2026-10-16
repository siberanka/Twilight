/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.network;

import com.siberanka.twilight.geyser.GeyserEvents;
import io.netty.channel.Channel;
import org.cloudburstmc.protocol.bedrock.BedrockPeer;
import org.cloudburstmc.protocol.bedrock.data.PacketCompressionAlgorithm;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.BatchCompression;
import org.cloudburstmc.protocol.bedrock.netty.codec.packet.BedrockPacketCodec;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.event.EventRegistrar;
import org.geysermc.geyser.api.event.bedrock.SessionDisconnectEvent;
import org.geysermc.geyser.api.event.bedrock.SessionJoinEvent;
import org.geysermc.geyser.api.event.bedrock.SessionLoginEvent;
import org.geysermc.geyser.session.GeyserSession;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * Twilight's network layer for Bedrock players on one Geyser (a backend's own or a proxy's): puts a
 * {@link SessionNetwork} into every Bedrock connection and runs the shared line-of-sight workers. Works the same on
 * Paper, Folia, Spigot, Velocity and BungeeCord, because it only looks at Bedrock packets.
 */
public final class BedrockNetwork implements AutoCloseable {
    private static final Field PEER_CHANNEL = peerChannel();

    private final EventRegistrar registrar;
    private final Supplier<NetworkSettings> settings;
    private final Consumer<String> info;
    private final BiConsumer<String, Throwable> warn;
    private final ThreadPoolExecutor workers;
    private final int threads;
    private final Map<GeyserSession, Attached> sessions = new ConcurrentHashMap<>();
    private final AtomicLong warnedAt = new AtomicLong();
    private final AtomicInteger warnings = new AtomicInteger();
    private volatile boolean closed;

    private record Attached(SessionNetwork handler, Channel channel, BatchCompression compression, int baseLevel) {}

    private BedrockNetwork(Object owner, Supplier<NetworkSettings> settings, int threads, Consumer<String> info,
                           BiConsumer<String, Throwable> warn) {
        this.registrar = EventRegistrar.of(owner);
        this.settings = settings;
        this.info = info;
        this.warn = warn;
        this.threads = threads;
        AtomicInteger counter = new AtomicInteger();
        // A bounded queue: when every worker is busy, a player's test waits for its next round instead of piling up.
        this.workers = new ThreadPoolExecutor(threads, threads, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(256),
                runnable -> {
                    Thread thread = new Thread(runnable, "Twilight-Network-" + counter.incrementAndGet());
                    thread.setDaemon(true);
                    thread.setPriority(Thread.NORM_PRIORITY - 1);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
        this.workers.allowCoreThreadTimeOut(true);
    }

    /**
     * Starts on the running Geyser: every Bedrock connection, current and future, gets the network layer.
     *
     * @param threads line-of-sight worker threads ({@link Threads#network})
     */
    public static BedrockNetwork start(Object owner, Supplier<NetworkSettings> settings, int threads,
                                       Consumer<String> info, BiConsumer<String, Throwable> warn) {
        if (PEER_CHANNEL == null) throw new IllegalStateException("this Geyser's Bedrock connection layout is unknown");
        BedrockNetwork network = new BedrockNetwork(owner, settings, threads, info, warn);
        GeyserEvents.subscribe(network.registrar, SessionLoginEvent.class, event -> network.attach(event.connection()));
        GeyserEvents.subscribe(network.registrar, SessionJoinEvent.class, event -> network.attach(event.connection()));
        GeyserEvents.subscribe(network.registrar, SessionDisconnectEvent.class, event -> network.forget(event.connection()));
        try {
            for (GeyserSession session : GeyserImpl.getInstance().onlineConnections()) network.attach(session);
        } catch (RuntimeException | LinkageError notRunning) {
            // Geyser has not started yet: connections attach as they log in.
        }
        return network;
    }

    public int threads() {
        return threads;
    }

    private void attach(Object connection) {
        if (closed || !(connection instanceof GeyserSession session) || sessions.containsKey(session)) return;
        if (!settings.get().enabled()) return;
        try {
            BedrockPeer peer = session.getUpstream().getSession().getPeer();
            Channel channel = (Channel) PEER_CHANNEL.get(peer);
            if (channel == null || !channel.isActive()) return;
            BatchCompression compression = null;
            int base = -1;
            var strategy = peer.getCompressionStrategy();
            if (strategy != null) {
                BatchCompression current = strategy.getDefaultCompression();
                if (current != null && current.getAlgorithm() == PacketCompressionAlgorithm.ZLIB) {
                    compression = current;
                    base = current.getLevel();
                }
            }
            long self = -1;
            try {
                self = session.getPlayerEntity().geyserId();
            } catch (RuntimeException notYet) {
                // Known from StartGame.
            }
            BatchCompression zlib = compression;
            int baseLevel = base;
            SessionNetwork handler = new SessionNetwork(host(session, zlib, baseLevel), self, System::nanoTime);
            Attached attached = new Attached(handler, channel, zlib, baseLevel);
            if (sessions.putIfAbsent(session, attached) != null) return;
            channel.eventLoop().execute(() -> {
                try {
                    if (closed || !channel.isActive() || channel.pipeline().get(SessionNetwork.NAME) != null
                            || channel.pipeline().get(BedrockPacketCodec.NAME) == null) {
                        sessions.remove(session, attached);
                        return;
                    }
                    channel.pipeline().addAfter(BedrockPacketCodec.NAME, SessionNetwork.NAME, handler);
                } catch (RuntimeException failure) {
                    sessions.remove(session, attached);
                    warn("Could not add the Bedrock network layer for " + name(session), failure);
                }
            });
        } catch (Exception | LinkageError failure) {
            warn("Could not add the Bedrock network layer for " + name(session), failure);
        }
    }

    private SessionNetwork.Host host(GeyserSession session, BatchCompression compression, int base) {
        return new SessionNetwork.Host() {
            @Override public NetworkSettings settings() {
                return settings.get();
            }

            @Override public int ping() {
                try {
                    return session.ping();
                } catch (RuntimeException unknown) {
                    return 0;
                }
            }

            @Override public Occlusion.Blocks blocks() {
                return session.isClosed() ? null : GeyserBlocks.of(session);
            }

            @Override public Executor workers() {
                return workers;
            }

            @Override public IntConsumer compression() {
                return compression == null ? null : level -> compression.setLevel(Math.max(1, Math.min(9, level)));
            }

            @Override public int baseCompression() {
                return base;
            }

            @Override public void failed(String message, Throwable failure) {
                warn(message + " (" + name(session) + ")", failure);
            }
        };
    }

    private void forget(Object connection) {
        if (connection instanceof GeyserSession session) sessions.remove(session);
    }

    /** At most a few warnings an hour: a broken layer must not flood the console. */
    private void warn(String message, Throwable failure) {
        long now = System.currentTimeMillis();
        long since = warnedAt.get();
        if (now - since > TimeUnit.HOURS.toMillis(1) && warnedAt.compareAndSet(since, now)) warnings.set(0);
        if (warnings.incrementAndGet() <= 5) warn.accept(message, failure);
    }

    /** One line per Bedrock player for {@code /twilight network}. */
    public List<String> report() {
        List<String> lines = new ArrayList<>();
        NetworkSettings current = settings.get();
        lines.add("Bedrock network: " + (current.enabled() ? "on" : "off") + ", culling " + (current.culling() ? "on" : "off")
                + ", walls " + (current.wallCulling() ? "on" : "off") + ", " + threads + " line-of-sight thread(s), "
                + sessions.size() + " player(s)");
        sessions.forEach((session, attached) -> {
            var stats = attached.handler().stats();
            long total = stats.passed() + stats.held();
            double saved = total == 0 ? 0 : 100.0 * (stats.held() - stats.flushed()) / total;
            lines.add(String.format(Locale.ROOT,
                    "%s: ping %d ms, inputs %s/s, strain %.0f%%, entities %d (%d hidden, %d waiting), updates held %d,"
                            + " merged away %.1f%%, zlib %s, at %s",
                    name(session), stats.ping(), stats.inputRate() < 0 ? "?" : String.format(Locale.ROOT, "%.0f", stats.inputRate()),
                    stats.strain() * 100, stats.tracked(), stats.hidden(), stats.holding(), stats.held(), Math.max(0, saved),
                    stats.compression() < 0 ? String.valueOf(attached.baseLevel()) : String.valueOf(stats.compression()),
                    stats.view()));
        });
        return lines;
    }

    @Override
    public void close() {
        closed = true;
        try {
            GeyserEvents.unregisterAll(registrar);
        } catch (RuntimeException | LinkageError gone) {
            // Geyser is shutting down as well.
        }
        sessions.forEach((session, attached) -> {
            try {
                attached.channel().eventLoop().execute(() -> {
                    attached.handler().detach();
                    if (attached.compression() != null && attached.baseLevel() > 0) attached.compression().setLevel(attached.baseLevel());
                });
            } catch (RuntimeException closing) {
                // The connection is closing too.
            }
        });
        sessions.clear();
        workers.shutdownNow();
    }

    private static String name(GeyserSession session) {
        try {
            String name = session.bedrockUsername();
            return name == null ? "a Bedrock player" : name;
        } catch (RuntimeException unknown) {
            return "a Bedrock player";
        }
    }

    private static Field peerChannel() {
        try {
            Field field = BedrockPeer.class.getDeclaredField("channel");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unknown) {
            return null;
        }
    }
}
