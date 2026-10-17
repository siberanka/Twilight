/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.network;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.ScheduledFuture;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.netty.BedrockPacketWrapper;
import org.cloudburstmc.protocol.bedrock.packet.AddEntityPacket;
import org.cloudburstmc.protocol.bedrock.packet.AddItemEntityPacket;
import org.cloudburstmc.protocol.bedrock.packet.AddPlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.CameraInstructionPacket;
import org.cloudburstmc.protocol.bedrock.packet.ChangeDimensionPacket;
import org.cloudburstmc.protocol.bedrock.packet.MoveEntityAbsolutePacket;
import org.cloudburstmc.protocol.bedrock.packet.MoveEntityDeltaPacket;
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.cloudburstmc.protocol.bedrock.packet.RemoveEntityPacket;
import org.cloudburstmc.protocol.bedrock.packet.RespawnPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetEntityDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.StartGamePacket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Sits in one Bedrock player's connection, between Geyser and the packet encoder, and holds back entity updates the
 * player cannot see: movement and data of entities behind the player or behind walls, and part of the movement of
 * distant ones. Held updates are merged and sent as one when the entity comes into view, at a slow refresh rate
 * meanwhile, or when the connection has room; a revealed entity is never older than the server's latest state.
 * Everything else (chunks, inventories, chat, spawns, removals, sounds, effects) passes untouched and in order.
 *
 * <p>The limits follow the connection ({@link ConnectionQuality}): a player with a high round trip gets wider
 * margins (an entity is revealed a little before it can come into view), a struggling client gets fewer updates.
 *
 * <p>All state is confined to the connection's event loop. Lines of sight are tested on shared worker threads from
 * a snapshot and the result is handed back to the event loop. Any failure turns this handler into a plain pass-
 * through for the rest of the session after it has sent everything it held.
 */
public final class SessionNetwork extends ChannelDuplexHandler {
    public static final String NAME = "twilight-network";
    /** Entities followed at most per player; beyond, entities pass untouched. */
    static final int MAX_TRACKED = 16_384;
    /** Entities whose lines of sight are tested per round. */
    static final int MAX_OCCLUSION_CANDIDATES = 256;
    /** Lines of sight beyond this distance are not tested (the entity is far enough to be cheap anyway). */
    static final double MAX_OCCLUSION_RANGE = 96;
    static final int OCCLUSION_LOOKUPS = 20_000;
    /** Held entities flushed at most per reveal, so a quick turn cannot produce an unbounded burst. */
    static final int MAX_REVEAL = 512;
    static final long TICK_NANOS = TimeUnit.MILLISECONDS.toNanos(50);
    private static final long REVEAL_NANOS = TimeUnit.MILLISECONDS.toNanos(50);
    private static final double PLAYER_EYE = 1.62;

    /** What a connection needs from its server. */
    public interface Host {
        NetworkSettings settings();

        /** Round trip in milliseconds. */
        int ping();

        /** Blocks for line-of-sight tests, or null when the world cannot be read. */
        Occlusion.Blocks blocks();

        /** Runs line-of-sight tests; may reject when busy. */
        Executor workers();

        /** Sets the zlib level of the connection, or null when it cannot be changed. */
        IntConsumer compression();

        /** The level Geyser chose, or -1. */
        int baseCompression();

        void failed(String message, Throwable failure);
    }

    /** Counters for {@code /twilight network}; written on the event loop, read anywhere. */
    public static final class Stats {
        volatile long passed, held, flushed, refreshed;
        volatile int tracked, holding, hidden, compression = -1;
        volatile double strain, inputRate = -1;
        volatile int ping;
        volatile double[] view;

        public long passed() { return passed; }
        public long held() { return held; }
        public long flushed() { return flushed; }
        public long refreshed() { return refreshed; }
        public int tracked() { return tracked; }
        public int holding() { return holding; }
        public int hidden() { return hidden; }
        public int compression() { return compression; }
        public double strain() { return strain; }
        public double inputRate() { return inputRate; }
        public int ping() { return ping; }
        /** Where the player stands and looks, as the layer knows it. */
        public String view() {
            double[] known = view;
            if (known == null) return "unknown";
            return String.format(java.util.Locale.ROOT, "%.0f %.0f %.0f looking %s", known[0], known[1], known[2],
                    known.length < 6 ? "?" : String.format(java.util.Locale.ROOT, "%.2f %.2f %.2f", known[3], known[4], known[5]));
        }
    }

    private final Host host;
    private final LongSupplier clock;
    private final Map<Long, TrackedEntity> entities = new HashMap<>();
    private final ConnectionQuality quality = new ConnectionQuality();
    private final Stats stats = new Stats();
    private ChannelHandlerContext context;
    private ScheduledFuture<?> ticker;

    private long self;
    private boolean located, looking, camera;
    private double eyeX, eyeY, eyeZ;
    private double lookX, lookY, lookZ, cameraX, cameraY, cameraZ;
    private double revealX, revealY, revealZ, revealLookX, revealLookY = 2, revealLookZ;
    private long lastReveal = Long.MIN_VALUE;
    private ConnectionQuality.Limits limits;
    private double tokens;
    private long lastTick;
    private long nextOcclusion;
    private boolean occlusionRunning;
    private long nextCompression;
    private int compressionLevel = -1;
    private boolean failed;
    /** Line-of-sight tests failed once (a world that cannot be read from here): walls hide nothing. */
    private boolean wallsBroken;

    public SessionNetwork(Host host, long selfFallback, LongSupplier clock) {
        this.host = host;
        this.clock = clock;
        this.self = selfFallback;
        this.limits = ConnectionQuality.Limits.of(host.settings(), 0, 0);
    }

    public Stats stats() {
        return stats;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        context = ctx;
        lastTick = clock.getAsLong();
        tokens = limits.packetsPerSecond() / 2.0;
        try {
            ticker = ctx.executor().scheduleAtFixedRate(this::tickSafely, 50, 50, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException closing) {
            failed = true;
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        if (ticker != null) ticker.cancel(false);
    }

    // ---- outbound -------------------------------------------------------------------------------------------

    @Override
    public void write(ChannelHandlerContext ctx, Object message, ChannelPromise promise) throws Exception {
        if (!failed && message instanceof BedrockPacketWrapper wrapper && wrapper.getPacket() != null) {
            boolean hold;
            try {
                hold = outbound(ctx, wrapper);
            } catch (Throwable failure) {
                fail(ctx, failure);
                hold = false;
            }
            if (hold) {
                ReferenceCountUtil.release(wrapper);
                promise.trySuccess();
                return;
            }
        }
        ctx.write(message, promise);
    }

    /** True when the packet is held (merged into the entity's state); false sends it on. */
    private boolean outbound(ChannelHandlerContext ctx, BedrockPacketWrapper wrapper) {
        BedrockPacket packet = wrapper.getPacket();
        long now = clock.getAsLong();
        switch (packet) {
            case StartGamePacket start -> {
                self = start.getRuntimeEntityId();
                locate(start.getPlayerPosition());
                entities.clear();
            }
            case ChangeDimensionPacket change -> {
                // The client forgets every entity when it changes dimension.
                entities.clear();
                locate(change.getPosition());
            }
            case RespawnPacket respawn -> locate(respawn.getPosition());
            case CameraInstructionPacket instruction -> {
                // A camera elsewhere (cutscenes, camera presets) sees from another place: no culling meanwhile.
                if (instruction.getSetInstruction() != null || instruction.getTargetInstruction() != null
                        || instruction.getAttachInstruction() != null || instruction.getSplineInstruction() != null) {
                    camera = true;
                }
                if (instruction.getClear().isPresent() && instruction.getClear().getAsBoolean()) camera = false;
            }
            case AddEntityPacket add -> describe(track(add.getRuntimeEntityId(), false, add.getIdentifier(), add.getPosition(),
                    add.getRotation() == null ? 0 : add.getRotation().getX(), add.getRotation() == null ? 0 : add.getRotation().getY(),
                    add.getHeadRotation()), add.getMetadata());
            case AddPlayerPacket add -> describe(track(add.getRuntimeEntityId(), true, "minecraft:player", add.getPosition(),
                    add.getRotation() == null ? 0 : add.getRotation().getX(), add.getRotation() == null ? 0 : add.getRotation().getY(),
                    add.getRotation() == null ? 0 : add.getRotation().getZ()), add.getMetadata());
            case AddItemEntityPacket add -> describe(track(add.getRuntimeEntityId(), false, "minecraft:item", add.getPosition(), 0, 0, 0),
                    add.getMetadata());
            case RemoveEntityPacket remove -> entities.remove(remove.getUniqueEntityId());
            case MovePlayerPacket move when move.getRuntimeEntityId() == self -> locate(move.getPosition());
            case MoveEntityAbsolutePacket move -> {
                return movement(ctx, wrapper, move.getRuntimeEntityId(), packet, now);
            }
            case MoveEntityDeltaPacket move -> {
                return movement(ctx, wrapper, move.getRuntimeEntityId(), packet, now);
            }
            case MovePlayerPacket move -> {
                return movement(ctx, wrapper, move.getRuntimeEntityId(), packet, now);
            }
            case SetEntityDataPacket data -> {
                return data(ctx, wrapper, data, now);
            }
            default -> { }
        }
        stats.passed++;
        return false;
    }

    private static void describe(TrackedEntity entity, java.util.Map<? extends org.cloudburstmc.protocol.bedrock.data.entity.EntityDataType<?>, ?> metadata) {
        if (entity != null) entity.describe(metadata);
    }

    private TrackedEntity track(long id, boolean player, String identifier, Vector3f position, float pitch, float yaw, float headYaw) {
        if (id == self) return null;
        if (!entities.containsKey(id) && entities.size() >= MAX_TRACKED) {
            entities.remove(id);
            return null;
        }
        float width = 1, height = 2;
        if (player) {
            width = 0.6f;
            height = 1.8f;
        } else if (identifier != null && (identifier.equals("minecraft:item") || identifier.endsWith("arrow")
                || identifier.endsWith("snowball") || identifier.endsWith("xp_orb"))) {
            width = 0.3f;
            height = 0.3f;
        }
        // Display entities (Java item, block and text displays, model bones) draw their model through a
        // transformation that may place it blocks away from the entity.
        boolean offset = identifier != null && (identifier.contains("display") || identifier.startsWith("twilight:"));
        TrackedEntity entity = new TrackedEntity(id, player, offset, width, height, position, pitch, yaw, headYaw);
        entities.put(id, entity);
        return entity;
    }

    private void locate(Vector3f position) {
        if (position == null) return;
        float x = position.getX(), y = position.getY(), z = position.getZ();
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z) || Math.abs(x) > 3.0e7 || Math.abs(z) > 3.0e7
                || Math.abs(y) > 1.0e5) return;
        eyeX = x;
        eyeY = y;
        eyeZ = z;
        located = true;
    }

    private boolean movement(ChannelHandlerContext ctx, BedrockPacketWrapper wrapper, long id, BedrockPacket packet, long now) {
        TrackedEntity entity = entities.get(id);
        if (entity == null || id == self) {
            stats.passed++;
            return false;
        }
        long interval = holdInterval(entity, now);
        if (interval > 0 && now - entity.lastSent < interval || interval == 0 && !take()) {
            entity.holdMove(packet, 0, 0, now);
            stats.held++;
            return true;
        }
        if (entity.holding()) {
            // Older held updates must not arrive after this newer one: merge and send them together.
            entity.holdMove(packet, 0, 0, now);
            send(ctx, entity, now);
            stats.held++;
            return true;
        }
        entity.observe(packet);
        entity.lastSent = now;
        stats.passed++;
        return false;
    }

    private boolean data(ChannelHandlerContext ctx, BedrockPacketWrapper wrapper, SetEntityDataPacket packet, long now) {
        TrackedEntity entity = entities.get(packet.getRuntimeEntityId());
        if (entity == null || entity.id == self) {
            stats.passed++;
            return false;
        }
        entity.describe(packet.getMetadata());
        // Data is held only while the entity is hidden: visible entities show every change (names, poses, effects).
        boolean hidden = classify(entity) == Visibility.HIDDEN;
        if (hidden && now - entity.lastSent < hiddenInterval(entity)) {
            entity.holdData(packet, 0, 0, now);
            stats.held++;
            return true;
        }
        if (entity.holding()) {
            entity.holdData(packet, 0, 0, now);
            send(ctx, entity, now);
            stats.held++;
            return true;
        }
        stats.passed++;
        return false;
    }

    /**
     * How often a hidden entity is refreshed: entities hidden only by walls are in front of the player and may step
     * out at any moment, so they are kept fresher than the ones behind.
     */
    private long hiddenInterval(TrackedEntity entity) {
        long interval = limits.hiddenIntervalNanos();
        return entity.occluded ? Math.max(TICK_NANOS * 4, interval * 2 / 5) : interval;
    }

    /** Takes one update from the per-second budget. */
    private boolean take() {
        if (tokens >= 1) {
            tokens--;
            return true;
        }
        return false;
    }

    enum Visibility { NEAR, VISIBLE, HIDDEN }

    /**
     * How long an entity's movement may wait now: 0 when it goes at once if the budget allows, the refresh
     * interval of hidden entities, or the interval of distant ones. Near entities always go (-1).
     */
    private long holdInterval(TrackedEntity entity, long now) {
        NetworkSettings settings = host.settings();
        if (!settings.enabled()) return -1;
        Visibility visibility = classify(entity);
        if (visibility == Visibility.NEAR) return -1;
        if (visibility == Visibility.HIDDEN) return hiddenInterval(entity);
        double distance = Math.sqrt(entity.distanceSquared(eyeX, eyeY, eyeZ));
        if (distance <= limits.farDistance()) return 0;
        double share = Math.min(1, (distance - limits.farDistance()) / limits.farDistance());
        return Math.max(0, (long) (limits.farIntervalNanos() * share));
    }

    Visibility classify(TrackedEntity entity) {
        NetworkSettings settings = host.settings();
        if (!located || !settings.enabled()) return Visibility.NEAR;
        double distanceSquared = entity.distanceSquared(eyeX, eyeY, eyeZ);
        double behind = entity.player ? limits.playerBehindDistance() : limits.behindDistance();
        if (distanceSquared <= behind * behind) return Visibility.NEAR;
        if (!settings.culling() || camera || !looking || entity.player && !settings.cullPlayers()) return Visibility.VISIBLE;
        if (!inView(entity, Math.sqrt(distanceSquared))) return Visibility.HIDDEN;
        if (settings.wallCulling() && entity.occluded && !wallsBroken && !entity.named && !entity.offset) {
            double wall = entity.player ? limits.playerWallDistance() : limits.wallDistance();
            if (distanceSquared >= wall * wall) return Visibility.HIDDEN;
        }
        return Visibility.VISIBLE;
    }

    /** Whether the entity is within the view cone of the head or of the camera, widened by the entity's size. */
    private boolean inView(TrackedEntity entity, double distance) {
        if (distance < 1e-3) return true;
        double feet = entity.player ? entity.y - PLAYER_EYE : entity.y;
        double dx = entity.x - eyeX, dy = feet + entity.sizeHeight() / 2 - eyeY, dz = entity.z - eyeZ;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1e-3) return true;
        dx /= length;
        dy /= length;
        dz /= length;
        double widen = Math.atan2(Math.max(entity.sizeWidth(), entity.sizeHeight()) + 1.0, length);
        double limit = Math.acos(Math.max(-1, Math.min(1, limits.halfAngleCos()))) + widen;
        if (limit >= Math.PI) return true;
        double cosLimit = Math.cos(limit);
        if (dx * lookX + dy * lookY + dz * lookZ >= cosLimit) return true;
        return camera(dx, dy, dz) >= cosLimit;
    }

    private double camera(double dx, double dy, double dz) {
        if (cameraX == 0 && cameraY == 0 && cameraZ == 0) return -2;
        return dx * cameraX + dy * cameraY + dz * cameraZ;
    }

    // ---- inbound --------------------------------------------------------------------------------------------

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object message) throws Exception {
        if (!failed && message instanceof BedrockPacketWrapper wrapper && wrapper.getPacket() instanceof PlayerAuthInputPacket input) {
            try {
                input(ctx, input);
            } catch (Throwable failure) {
                fail(ctx, failure);
            }
        }
        ctx.fireChannelRead(message);
    }

    private void input(ChannelHandlerContext ctx, PlayerAuthInputPacket input) {
        long now = clock.getAsLong();
        quality.input(now);
        locate(input.getPosition());
        Vector3f rotation = input.getRotation();
        if (rotation != null && Float.isFinite(rotation.getX()) && Float.isFinite(rotation.getY())) {
            double pitch = Math.toRadians(Math.max(-90, Math.min(90, rotation.getX())));
            double yaw = Math.toRadians(rotation.getY() % 360);
            lookX = -Math.sin(yaw) * Math.cos(pitch);
            lookY = -Math.sin(pitch);
            lookZ = Math.cos(yaw) * Math.cos(pitch);
            looking = true;
        }
        Vector3f orientation = input.getCameraOrientation();
        if (orientation != null && Float.isFinite(orientation.getX()) && Float.isFinite(orientation.getY())
                && Float.isFinite(orientation.getZ())) {
            double length = Math.sqrt(orientation.getX() * orientation.getX() + orientation.getY() * orientation.getY()
                    + orientation.getZ() * orientation.getZ());
            if (length > 1e-3 && length < 1e3) {
                cameraX = orientation.getX() / length;
                cameraY = orientation.getY() / length;
                cameraZ = orientation.getZ() / length;
            } else {
                cameraX = cameraY = cameraZ = 0;
            }
        }
        if (!looking || now - lastReveal < REVEAL_NANOS && lastReveal != Long.MIN_VALUE) return;
        double turned = lookX * revealLookX + lookY * revealLookY + lookZ * revealLookZ;
        double movedX = eyeX - revealX, movedY = eyeY - revealY, movedZ = eyeZ - revealZ;
        // A turn of more than 15 degrees or a few blocks of movement can bring held entities into view.
        if (turned < 0.966 || movedX * movedX + movedY * movedY + movedZ * movedZ > 4) reveal(ctx, now);
    }

    /** Sends the held entities that are no longer hidden, nearest first. */
    private void reveal(ChannelHandlerContext ctx, long now) {
        lastReveal = now;
        revealX = eyeX;
        revealY = eyeY;
        revealZ = eyeZ;
        revealLookX = lookX;
        revealLookY = lookY;
        revealLookZ = lookZ;
        List<TrackedEntity> due = new ArrayList<>();
        for (TrackedEntity entity : entities.values()) {
            if (entity.holding() && classify(entity) != Visibility.HIDDEN) due.add(entity);
        }
        if (due.isEmpty()) return;
        due.sort((a, b) -> Double.compare(a.distanceSquared(eyeX, eyeY, eyeZ), b.distanceSquared(eyeX, eyeY, eyeZ)));
        int sent = 0;
        for (TrackedEntity entity : due) {
            if (sent++ >= MAX_REVEAL) break;
            send(ctx, entity, now);
        }
        ctx.flush();
    }

    // ---- periodic -------------------------------------------------------------------------------------------

    private void tickSafely() {
        if (failed || context == null) return;
        try {
            tick(clock.getAsLong());
        } catch (Throwable failure) {
            fail(context, failure);
        }
    }

    /** Every 50 ms: limits, budget, held entities that are due, line-of-sight tests and compression. */
    void tick(long now) {
        ChannelHandlerContext ctx = context;
        if (ctx == null || !ctx.channel().isActive()) return;
        NetworkSettings settings = host.settings();
        quality.update(host.ping(), ctx.channel().isWritable(), now);
        limits = ConnectionQuality.Limits.of(settings, quality.ping(), quality.strain());
        double elapsed = Math.max(0, Math.min(1, (now - lastTick) / 1e9));
        lastTick = now;
        double cap = Math.max(32, limits.packetsPerSecond() / 2.0);
        tokens = Math.min(cap, tokens + limits.packetsPerSecond() * elapsed);

        List<TrackedEntity> due = new ArrayList<>();
        int holding = 0, hidden = 0;
        for (TrackedEntity entity : entities.values()) {
            Visibility visibility = classify(entity);
            if (visibility == Visibility.HIDDEN) hidden++;
            if (!entity.holding()) continue;
            holding++;
            long interval = visibility == Visibility.HIDDEN ? hiddenInterval(entity)
                    : visibility == Visibility.NEAR ? 0 : Math.max(0, holdInterval(entity, now));
            if (now - entity.lastSent >= interval) due.add(entity);
        }
        if (!due.isEmpty()) {
            due.sort((a, b) -> Double.compare(a.distanceSquared(eyeX, eyeY, eyeZ), b.distanceSquared(eyeX, eyeY, eyeZ)));
            // Nothing waits longer than three refresh intervals, whatever the budget.
            long overdue = 3 * limits.hiddenIntervalNanos();
            boolean wrote = false;
            for (TrackedEntity entity : due) {
                boolean near = classify(entity) == Visibility.NEAR;
                if (!near && !take() && now - entity.heldSince < overdue) continue;
                send(ctx, entity, now);
                stats.refreshed++;
                wrote = true;
            }
            if (wrote) ctx.flush();
        }
        stats.tracked = entities.size();
        stats.holding = holding;
        stats.hidden = hidden;
        stats.strain = quality.strain();
        stats.inputRate = quality.inputRate();
        stats.ping = quality.ping();
        stats.view = !located ? null : looking ? new double[]{eyeX, eyeY, eyeZ, lookX, lookY, lookZ} : new double[]{eyeX, eyeY, eyeZ};

        if (settings.enabled() && settings.culling() && settings.wallCulling() && !occlusionRunning && now >= nextOcclusion) {
            occlusion(ctx, now);
        }
        if (settings.enabled() && settings.adaptiveCompression() && now >= nextCompression) {
            nextCompression = now + TimeUnit.SECONDS.toNanos(1);
            compress();
        }
    }

    private void send(ChannelHandlerContext ctx, TrackedEntity entity, long now) {
        int sender = entity.sender, target = entity.target;
        for (BedrockPacket packet : entity.flush()) {
            ctx.write(BedrockPacketWrapper.create(0, sender, target, packet, null), ctx.voidPromise());
            stats.flushed++;
        }
        entity.lastSent = now;
    }

    /** Snapshots the entities worth a line-of-sight test and tests them on a worker thread. */
    private void occlusion(ChannelHandlerContext ctx, long now) {
        nextOcclusion = now + (long) (TimeUnit.MILLISECONDS.toNanos(250) * (1 + quality.strain()));
        Occlusion.Blocks blocks;
        try {
            blocks = host.blocks();
        } catch (RuntimeException | LinkageError unavailable) {
            blocks = null;
        }
        if (blocks == null || !located || !looking || camera || wallsBroken) {
            for (TrackedEntity entity : entities.values()) {
                entity.occluded = false;
                entity.occludedVotes = 0;
            }
            return;
        }
        List<TrackedEntity> candidates = new ArrayList<>();
        for (TrackedEntity entity : entities.values()) {
            double distanceSquared = entity.distanceSquared(eyeX, eyeY, eyeZ);
            double wall = entity.player ? limits.playerWallDistance() : limits.wallDistance();
            if (distanceSquared < wall * wall || distanceSquared > MAX_OCCLUSION_RANGE * MAX_OCCLUSION_RANGE
                    || entity.named || entity.offset
                    || entity.player && !host.settings().cullPlayers() || !inView(entity, Math.sqrt(distanceSquared))) {
                entity.occluded = false;
                entity.occludedVotes = 0;
                continue;
            }
            candidates.add(entity);
        }
        if (candidates.isEmpty()) return;
        candidates.sort((a, b) -> Double.compare(a.distanceSquared(eyeX, eyeY, eyeZ), b.distanceSquared(eyeX, eyeY, eyeZ)));
        int count = Math.min(candidates.size(), MAX_OCCLUSION_CANDIDATES);
        long[] ids = new long[count];
        double[] positions = new double[count * 5];
        for (int i = 0; i < count; i++) {
            TrackedEntity entity = candidates.get(i);
            ids[i] = entity.id;
            positions[i * 5] = entity.x;
            positions[i * 5 + 1] = entity.player ? entity.y - PLAYER_EYE : entity.y;
            positions[i * 5 + 2] = entity.z;
            positions[i * 5 + 3] = entity.sizeWidth();
            positions[i * 5 + 4] = entity.sizeHeight();
        }
        for (int i = count; i < candidates.size(); i++) {
            candidates.get(i).occluded = false;
            candidates.get(i).occludedVotes = 0;
        }
        double x = eyeX, y = eyeY, z = eyeZ;
        int thickness = limits.wallThickness();
        Occlusion.Blocks world = blocks;
        occlusionRunning = true;
        try {
            host.workers().execute(() -> {
                boolean[] hidden = new boolean[count];
                Throwable failure = null;
                try {
                    Occlusion.Blocks cached = cached(world);
                    Occlusion.Budget budget = new Occlusion.Budget(OCCLUSION_LOOKUPS);
                    for (int i = 0; i < count && !budget.spent(); i++) {
                        hidden[i] = Occlusion.hidden(x, y, z, positions[i * 5], positions[i * 5 + 1], positions[i * 5 + 2],
                                positions[i * 5 + 3], positions[i * 5 + 4], thickness, cached, budget);
                    }
                } catch (Throwable error) {
                    failure = error;
                    java.util.Arrays.fill(hidden, false);
                }
                Throwable reported = failure;
                try {
                    ctx.executor().execute(() -> occluded(ids, hidden, reported));
                } catch (RejectedExecutionException closed) {
                    // The connection closed meanwhile.
                }
            });
        } catch (RejectedExecutionException busy) {
            // Every worker is busy with other players: this player is tested next round.
            occlusionRunning = false;
        }
    }

    /** Applies line-of-sight results: two hidden results in a row hide an entity, one clear result shows it. */
    private void occluded(long[] ids, boolean[] hidden, Throwable failure) {
        occlusionRunning = false;
        if (failure != null) {
            wallsBroken = true;
            for (TrackedEntity entity : entities.values()) entity.occluded = false;
            host.failed("Line-of-sight tests failed; walls no longer hide entities for this player", failure);
            if (context != null) reveal(context, clock.getAsLong());
            return;
        }
        for (int i = 0; i < ids.length; i++) {
            TrackedEntity entity = entities.get(ids[i]);
            if (entity == null) continue;
            if (hidden[i]) {
                if (entity.occludedVotes < 2) entity.occludedVotes++;
                entity.occluded = entity.occludedVotes >= 2;
            } else {
                entity.occludedVotes = 0;
                entity.occluded = false;
            }
        }
        if (context != null) reveal(context, clock.getAsLong());
    }

    /** One test round reads each block once. */
    private static Occlusion.Blocks cached(Occlusion.Blocks world) {
        Map<Long, Boolean> seen = new HashMap<>();
        return (x, y, z) -> {
            long key = ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | (y & 0xFFFL);
            Boolean known = seen.get(key);
            if (known != null) return known;
            boolean opaque = seen.size() < 200_000 && world.opaque(x, y, z);
            seen.put(key, opaque);
            return opaque;
        };
    }

    /** A struggling connection gets smaller packets (zlib works harder); a healthy one Geyser's own level. */
    private void compress() {
        IntConsumer setter = host.compression();
        int base = host.baseCompression();
        if (setter == null || base < 0) return;
        int level = Math.min(9, base + (int) Math.round(2 * quality.strain()));
        if (level == compressionLevel) return;
        setter.accept(level);
        compressionLevel = level;
        stats.compression = level;
    }

    // ---- failure and shutdown -------------------------------------------------------------------------------

    private void fail(ChannelHandlerContext ctx, Throwable failure) {
        if (failed) return;
        failed = true;
        try {
            flushAll(ctx);
        } catch (Throwable ignored) {
            // Nothing more can be done for this session.
        }
        entities.clear();
        if (ticker != null) ticker.cancel(false);
        host.failed("Bedrock network optimisation stopped for this player; packets pass unchanged", failure);
    }

    private void flushAll(ChannelHandlerContext ctx) {
        long now = clock.getAsLong();
        boolean wrote = false;
        for (TrackedEntity entity : entities.values()) {
            if (!entity.holding()) continue;
            send(ctx, entity, now);
            wrote = true;
        }
        if (wrote) ctx.flush();
    }

    /** Sends everything held and leaves the connection; call on the event loop. */
    public void detach() {
        ChannelHandlerContext ctx = context;
        if (ctx == null) return;
        if (!failed) {
            failed = true;
            try {
                flushAll(ctx);
            } catch (Throwable ignored) {
                // The connection is closing.
            }
        }
        entities.clear();
        if (ticker != null) ticker.cancel(false);
        try {
            ctx.pipeline().remove(this);
        } catch (RuntimeException alreadyGone) {
            // Removed already or the channel closed.
        }
    }
}
