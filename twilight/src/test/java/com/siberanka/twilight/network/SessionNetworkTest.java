/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.network;

import io.netty.channel.embedded.EmbeddedChannel;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.data.entity.IntEntityProperty;
import org.cloudburstmc.protocol.bedrock.netty.BedrockPacketWrapper;
import org.cloudburstmc.protocol.bedrock.packet.AddEntityPacket;
import org.cloudburstmc.protocol.bedrock.packet.AddPlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.TextPacket;
import org.cloudburstmc.protocol.bedrock.packet.MoveEntityAbsolutePacket;
import org.cloudburstmc.protocol.bedrock.packet.MoveEntityDeltaPacket;
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.cloudburstmc.protocol.bedrock.packet.RemoveEntityPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetEntityDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetEntityMotionPacket;
import org.cloudburstmc.protocol.bedrock.packet.StartGamePacket;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntConsumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The network layer in a real Netty pipeline with Geyser's Bedrock packets: what it holds back, that the client
 * always ends with the server's latest state, and that nothing it does can break a connection.
 */
class SessionNetworkTest {
    private static final long SELF = 1;
    private static final long MS = 1_000_000L;

    /** A connection with a clock, a world and a client that applies what it receives. */
    static final class Rig {
        long now = 10_000 * MS;
        int ping = 30;
        NetworkSettings settings = NetworkSettings.DEFAULTS;
        Occlusion.Blocks blocks = (x, y, z) -> false;
        final List<String> failures = new ArrayList<>();
        final AtomicReference<Integer> compression = new AtomicReference<>();
        final SessionNetwork handler;
        final EmbeddedChannel channel;
        /** What the client believes: entity id to x, y, z, pitch, yaw, head yaw. */
        final Map<Long, float[]> client = new HashMap<>();
        /** What the server sent last per entity. */
        final Map<Long, float[]> server = new HashMap<>();
        final Map<Long, Map<Integer, Integer>> clientProperties = new HashMap<>();
        int received, sent;

        Rig() {
            handler = new SessionNetwork(new SessionNetwork.Host() {
                @Override public NetworkSettings settings() { return settings; }
                @Override public int ping() { return ping; }
                @Override public Occlusion.Blocks blocks() { return blocks; }
                @Override public Executor workers() { return Runnable::run; }
                @Override public IntConsumer compression() { return compression::set; }
                @Override public int baseCompression() { return 6; }
                @Override public void failed(String message, Throwable failure) { failures.add(message); }
            }, -1, () -> now);
            channel = new EmbeddedChannel(handler);
            send(start());
        }

        static StartGamePacket start() {
            StartGamePacket start = new StartGamePacket();
            start.setRuntimeEntityId(SELF);
            start.setPlayerPosition(Vector3f.from(0, 65.62f, 0));
            return start;
        }

        void send(BedrockPacket packet) {
            sent++;
            channel.writeOutbound(BedrockPacketWrapper.create(0, 0, 0, packet, null));
            drain();
        }

        void drain() {
            channel.runPendingTasks();
            Object message;
            while ((message = channel.readOutbound()) != null) {
                BedrockPacketWrapper wrapper = (BedrockPacketWrapper) message;
                apply(wrapper.getPacket());
                received++;
                wrapper.release();
            }
        }

        void apply(BedrockPacket packet) {
            switch (packet) {
                case AddEntityPacket add -> client.put(add.getRuntimeEntityId(), new float[]{add.getPosition().getX(),
                        add.getPosition().getY(), add.getPosition().getZ(), 0, 0, 0});
                case AddPlayerPacket add -> client.put(add.getRuntimeEntityId(), new float[]{add.getPosition().getX(),
                        add.getPosition().getY(), add.getPosition().getZ(), 0, 0, 0});
                case RemoveEntityPacket remove -> client.remove(remove.getUniqueEntityId());
                case MoveEntityAbsolutePacket move -> {
                    float[] state = client.get(move.getRuntimeEntityId());
                    if (state == null) return;
                    state[0] = move.getPosition().getX();
                    state[1] = move.getPosition().getY();
                    state[2] = move.getPosition().getZ();
                    state[3] = move.getRotation().getX();
                    state[4] = move.getRotation().getY();
                    state[5] = move.getRotation().getZ();
                }
                case MoveEntityDeltaPacket move -> {
                    float[] state = client.get(move.getRuntimeEntityId());
                    if (state == null) return;
                    var flags = move.getFlags();
                    if (flags.contains(MoveEntityDeltaPacket.Flag.HAS_X)) state[0] = move.getX();
                    if (flags.contains(MoveEntityDeltaPacket.Flag.HAS_Y)) state[1] = move.getY();
                    if (flags.contains(MoveEntityDeltaPacket.Flag.HAS_Z)) state[2] = move.getZ();
                    if (flags.contains(MoveEntityDeltaPacket.Flag.HAS_PITCH)) state[3] = move.getPitch();
                    if (flags.contains(MoveEntityDeltaPacket.Flag.HAS_YAW)) state[4] = move.getYaw();
                    if (flags.contains(MoveEntityDeltaPacket.Flag.HAS_HEAD_YAW)) state[5] = move.getHeadYaw();
                }
                case MovePlayerPacket move -> {
                    float[] state = client.get(move.getRuntimeEntityId());
                    if (state == null) return;
                    state[0] = move.getPosition().getX();
                    state[1] = move.getPosition().getY();
                    state[2] = move.getPosition().getZ();
                }
                case SetEntityDataPacket data -> {
                    var properties = clientProperties.computeIfAbsent(data.getRuntimeEntityId(), id -> new HashMap<>());
                    for (IntEntityProperty property : data.getProperties().getIntProperties()) {
                        properties.put(property.getIndex(), property.getValue());
                    }
                }
                default -> { }
            }
        }

        void spawn(long id, float x, float y, float z) {
            AddEntityPacket add = new AddEntityPacket();
            add.setRuntimeEntityId(id);
            add.setUniqueEntityId(id);
            add.setIdentifier("minecraft:zombie");
            add.setPosition(Vector3f.from(x, y, z));
            add.setMotion(Vector3f.ZERO);
            add.setRotation(Vector2f.ZERO);
            server.put(id, new float[]{x, y, z, 0, 0, 0});
            send(add);
        }

        void spawnPlayer(long id, float x, float y, float z) {
            AddPlayerPacket add = new AddPlayerPacket();
            add.setRuntimeEntityId(id);
            add.setUniqueEntityId(id);
            add.setUsername("p" + id);
            add.setPosition(Vector3f.from(x, y, z));
            add.setMotion(Vector3f.ZERO);
            add.setRotation(Vector3f.ZERO);
            server.put(id, new float[]{x, y, z, 0, 0, 0});
            send(add);
        }

        /** The way Geyser moves a living entity: a delta packet that carries absolute values for what changed. */
        void move(long id, float x, float y, float z, float yaw) {
            MoveEntityDeltaPacket move = new MoveEntityDeltaPacket();
            move.setRuntimeEntityId(id);
            move.getFlags().add(MoveEntityDeltaPacket.Flag.HAS_X);
            move.getFlags().add(MoveEntityDeltaPacket.Flag.HAS_Y);
            move.getFlags().add(MoveEntityDeltaPacket.Flag.HAS_Z);
            move.getFlags().add(MoveEntityDeltaPacket.Flag.HAS_YAW);
            move.setX(x);
            move.setY(y);
            move.setZ(z);
            move.setYaw(yaw);
            float[] state = server.get(id);
            if (state != null) {
                state[0] = x;
                state[1] = y;
                state[2] = z;
                state[4] = yaw;
            }
            send(move);
        }

        void movePlayer(long id, float x, float y, float z) {
            MovePlayerPacket move = new MovePlayerPacket();
            move.setRuntimeEntityId(id);
            move.setPosition(Vector3f.from(x, y, z));
            move.setRotation(Vector3f.ZERO);
            move.setMode(MovePlayerPacket.Mode.NORMAL);
            float[] state = server.get(id);
            if (state != null) {
                state[0] = x;
                state[1] = y;
                state[2] = z;
            }
            send(move);
        }

        /** The player's input: where it stands and where it looks (yaw 0 looks along +Z). */
        void look(float x, float y, float z, float pitch, float yaw) {
            PlayerAuthInputPacket input = new PlayerAuthInputPacket();
            input.setPosition(Vector3f.from(x, y, z));
            input.setRotation(Vector3f.from(pitch, yaw, yaw));
            input.setMotion(Vector2f.ZERO);
            input.setDelta(Vector3f.ZERO);
            channel.writeInbound(BedrockPacketWrapper.create(0, 0, 0, input, null));
            Object message;
            while ((message = channel.readInbound()) != null) ((BedrockPacketWrapper) message).release();
            drain();
        }

        void tick() {
            now += SessionNetwork.TICK_NANOS;
            handler.tick(now);
            drain();
        }

        void ticks(int count) {
            for (int i = 0; i < count; i++) tick();
        }

        /** Whether the client shows every entity where the server put it. */
        void assertConsistent() {
            server.forEach((id, state) -> {
                float[] seen = client.get(id);
                assertNotNull(seen, "client lost entity " + id);
                assertEquals(state[0], seen[0], 1e-4, "x of " + id);
                assertEquals(state[1], seen[1], 1e-4, "y of " + id);
                assertEquals(state[2], seen[2], 1e-4, "z of " + id);
            });
        }
    }

    @Test void passesEverythingUntilThePlayerIsKnown() {
        Rig rig = new Rig();
        rig.spawn(10, 0, 65, -30);
        int before = rig.received;
        for (int i = 0; i < 20; i++) {
            rig.move(10, i, 65, -30, 0);
            rig.now += 50 * MS;
        }
        assertEquals(before + 20, rig.received, "no culling before the first input");
    }

    @Test void holdsEntitiesBehindThePlayerAndRevealsTheirLatestStateOnTurning() {
        Rig rig = new Rig();
        rig.look(0, 65.62f, 0, 0, 0);
        rig.spawn(10, 0, 65, -40);
        rig.tick();
        int before = rig.received;
        for (int i = 0; i < 40; i++) {
            rig.move(10, i * 0.1f, 65, -40, i);
            rig.now += 5 * MS;
        }
        int passed = rig.received - before;
        assertTrue(passed <= 2, "an entity 40 blocks behind gets at most a refresh, got " + passed);
        assertNotEquals(rig.server.get(10L)[0], rig.client.get(10L)[0], 1e-4);
        rig.look(0, 65.62f, 0, 0, 180);
        rig.assertConsistent();
        assertEquals(39, rig.client.get(10L)[4], 1e-4, "the latest yaw arrives with the reveal");
    }

    @Test void entitiesCloseBehindAlwaysGetEveryUpdate() {
        Rig rig = new Rig();
        rig.look(0, 65.62f, 0, 0, 0);
        rig.spawn(10, 0, 65, -5);
        int before = rig.received;
        for (int i = 0; i < 20; i++) rig.move(10, i * 0.05f, 65, -5, 0);
        assertEquals(before + 20, rig.received);
    }

    @Test void hiddenEntitiesAreRefreshedNowAndThen() {
        Rig rig = new Rig();
        rig.look(0, 65.62f, 0, 0, 0);
        rig.spawn(10, 0, 65, -40);
        for (int second = 0; second < 5; second++) {
            for (int i = 0; i < 20; i++) {
                rig.move(10, second + i * 0.01f, 65, -40, 0);
                rig.tick();
            }
        }
        float[] seen = rig.client.get(10L);
        assertEquals(rig.server.get(10L)[0], seen[0], 1.0, "a hidden entity is at most about a second behind");
    }

    @Test void thickWallsHideEntitiesButOneBlockDoesNot() {
        Rig rig = new Rig();
        // A wall across the view at z = 20..(20 + thickness - 1), from y 60 to 80.
        int[] thickness = {1};
        rig.blocks = (x, y, z) -> z >= 20 && z < 20 + thickness[0] && y >= 60 && y <= 80 && Math.abs(x) <= 10;
        rig.look(0, 65.62f, 0, 0, 0);
        rig.spawn(10, 0, 65, 36);
        rig.ticks(40);
        int before = rig.received;
        for (int i = 0; i < 10; i++) rig.move(10, i * 0.1f, 65, 36, 0);
        assertEquals(before + 10, rig.received, "one block of wall hides nothing");

        thickness[0] = 3;
        rig.ticks(40);
        before = rig.received;
        for (int i = 0; i < 10; i++) rig.move(10, 1 + i * 0.1f, 65, 36, 0);
        assertTrue(rig.received - before <= 1, "behind three blocks of wall it is held, got " + (rig.received - before));

        thickness[0] = 0;
        rig.ticks(20);
        rig.assertConsistent();
    }

    /** Bedrock draws name tags through walls, and display models may sit away from their entity. */
    @Test void namedAndDisplayEntitiesAreNeverHiddenByWalls() {
        Rig rig = new Rig();
        rig.blocks = (x, y, z) -> z >= 20 && z < 26 && y >= 50 && y <= 90 && Math.abs(x) <= 20;
        rig.look(0, 65.62f, 0, 0, 0);
        AddEntityPacket named = new AddEntityPacket();
        named.setRuntimeEntityId(30);
        named.setUniqueEntityId(30);
        named.setIdentifier("minecraft:zombie");
        named.setPosition(Vector3f.from(0, 65, 36));
        named.setMotion(Vector3f.ZERO);
        named.setRotation(Vector2f.ZERO);
        named.getMetadata().put(EntityDataTypes.NAME, "Boss");
        rig.server.put(30L, new float[]{0, 65, 36, 0, 0, 0});
        rig.send(named);
        AddEntityPacket display = new AddEntityPacket();
        display.setRuntimeEntityId(31);
        display.setUniqueEntityId(31);
        display.setIdentifier("twilight:item_display");
        display.setPosition(Vector3f.from(2, 65, 36));
        display.setMotion(Vector3f.ZERO);
        display.setRotation(Vector2f.ZERO);
        rig.server.put(31L, new float[]{2, 65, 36, 0, 0, 0});
        rig.send(display);
        rig.spawn(32, -2, 65, 36);
        rig.ticks(40);
        int before = rig.received;
        for (int i = 0; i < 10; i++) {
            rig.move(30, i * 0.1f, 65, 36, 0);
            rig.move(31, 2 + i * 0.1f, 65, 36, 0);
        }
        assertEquals(before + 20, rig.received, "named and display entities pass");
        before = rig.received;
        for (int i = 0; i < 10; i++) rig.move(32, -2 + i * 0.1f, 65, 36, 0);
        assertTrue(rig.received - before <= 1, "an unnamed mob behind the same wall is held");
    }

    @Test void glassOrUnknownBlocksNeverHide() {
        assertFalse(Occlusion.hidden(0, 65, 0, 0, 65, 40, 0.6, 1.8, 3, (x, y, z) -> false, new Occlusion.Budget(10_000)));
        // An exhausted budget answers "visible".
        assertFalse(Occlusion.hidden(0, 65, 0, 0, 65, 40, 0.6, 1.8, 3, (x, y, z) -> true, new Occlusion.Budget(5)));
        assertTrue(Occlusion.hidden(0, 65, 0, 0, 65, 40, 0.6, 1.8, 3, (x, y, z) -> z > 10 && z < 30, new Occlusion.Budget(10_000)));
        assertFalse(Occlusion.hidden(Double.NaN, 65, 0, 0, 65, 40, 0.6, 1.8, 1, (x, y, z) -> true, new Occlusion.Budget(10_000)));
    }

    @Test void removedEntitiesForgetWhatWasHeld() {
        Rig rig = new Rig();
        rig.look(0, 65.62f, 0, 0, 0);
        rig.spawn(10, 0, 65, -40);
        rig.tick();
        for (int i = 0; i < 5; i++) rig.move(10, i, 65, -40, 0);
        RemoveEntityPacket remove = new RemoveEntityPacket();
        remove.setUniqueEntityId(10);
        rig.send(remove);
        rig.server.remove(10L);
        int before = rig.received;
        rig.look(0, 65.62f, 0, 0, 180);
        rig.ticks(100);
        assertEquals(before, rig.received, "nothing is sent for a removed entity");
        assertNull(rig.client.get(10L));
    }

    @Test void heldDataIsMergedNewestFirst() {
        Rig rig = new Rig();
        rig.look(0, 65.62f, 0, 0, 0);
        rig.spawn(10, 0, 65, -40);
        rig.tick();
        for (int value = 1; value <= 5; value++) {
            SetEntityDataPacket data = new SetEntityDataPacket();
            data.setRuntimeEntityId(10);
            data.getProperties().getIntProperties().add(new IntEntityProperty(0, value));
            data.getProperties().getIntProperties().add(new IntEntityProperty(value, value));
            data.getMetadata().putFlags(EntityDataMapFlags.of(value % 2 == 0));
            rig.send(data);
        }
        rig.look(0, 65.62f, 0, 0, 180);
        Map<Integer, Integer> properties = rig.clientProperties.get(10L);
        assertNotNull(properties);
        assertEquals(5, properties.get(0), "the latest value of a property wins");
        for (int index = 1; index <= 5; index++) assertEquals(index, properties.get(index), "every property arrives");
    }

    /** A flag set, as Geyser sends it. */
    static final class EntityDataMapFlags {
        static java.util.EnumMap<EntityFlag, Boolean> of(boolean onFire) {
            java.util.EnumMap<EntityFlag, Boolean> flags = new java.util.EnumMap<>(EntityFlag.class);
            flags.put(EntityFlag.ON_FIRE, onFire);
            return flags;
        }
    }

    @Test void otherPacketsAlwaysPassInOrder() {
        Rig rig = new Rig();
        rig.look(0, 65.62f, 0, 0, 0);
        rig.spawn(10, 0, 65, -40);
        rig.tick();
        List<BedrockPacket> order = new ArrayList<>();
        rig.channel.pipeline().addFirst(new io.netty.channel.ChannelOutboundHandlerAdapter() {
            @Override public void write(io.netty.channel.ChannelHandlerContext ctx, Object msg, io.netty.channel.ChannelPromise promise) {
                order.add(((BedrockPacketWrapper) msg).getPacket());
                ctx.write(msg, promise);
            }
        });
        TextPacket chunk = new TextPacket();
        SetEntityMotionPacket motion = new SetEntityMotionPacket();
        motion.setRuntimeEntityId(10);
        motion.setMotion(Vector3f.from(0, 1, 0));
        rig.move(10, 1, 65, -40, 0);
        rig.send(chunk);
        rig.send(motion);
        assertTrue(order.contains(chunk));
        assertTrue(order.contains(motion), "motion always passes");
    }

    @Test void theBudgetLimitsVisibleUpdatesAndNothingStarves() {
        Rig rig = new Rig();
        rig.settings = new NetworkSettings(true, NetworkSettings.AUTO, true, true, NetworkSettings.AUTO, 0, true,
                NetworkSettings.AUTO, NetworkSettings.AUTO, NetworkSettings.AUTO, NetworkSettings.AUTO, 200, true);
        rig.look(0, 65.62f, 0, 0, 0);
        for (int id = 100; id < 400; id++) rig.spawn(id, (id % 20) - 10, 65, 15 + id % 10);
        rig.ticks(40);
        int before = rig.received;
        for (int round = 0; round < 20; round++) {
            for (int id = 100; id < 400; id++) rig.move(id, (id % 20) - 10 + round * 0.01f, 65, 15 + id % 10, round);
            rig.tick();
        }
        int passed = rig.received - before;
        // 6000 updates in one second against a budget of 200 per second (plus the initial burst).
        assertTrue(passed < 1500, "the budget holds, got " + passed);
        rig.ticks(80);
        rig.assertConsistent();
    }

    @Test void otherPlayersUseWiderMargins() {
        Rig rig = new Rig();
        rig.look(0, 65.62f, 0, 0, 0);
        // 13 blocks behind: beyond an entity's margin (10 + ping), within a player's (1.5 times).
        rig.spawnPlayer(20, 0, 66.62f, -13);
        rig.spawn(21, 1, 65, -13);
        rig.tick();
        int before = rig.received;
        for (int i = 0; i < 10; i++) {
            rig.movePlayer(20, i * 0.1f, 66.62f, -13);
            rig.now += 5 * MS;
        }
        assertEquals(before + 10, rig.received, "a player close behind gets every update");
    }

    @Test void aFailureTurnsTheLayerIntoAPassThroughAfterSendingWhatItHeld() {
        Rig rig = new Rig();
        rig.look(0, 65.62f, 0, 0, 0);
        rig.spawn(10, 0, 65, -40);
        rig.tick();
        for (int i = 0; i < 5; i++) rig.move(10, i, 65, -40, 0);
        rig.settings = null; // the next decision fails
        rig.move(10, 9, 65, -40, 0);
        assertFalse(rig.failures.isEmpty());
        rig.settings = NetworkSettings.DEFAULTS;
        int before = rig.received;
        for (int i = 0; i < 10; i++) rig.move(10, 10 + i, 65, -40, 0);
        assertEquals(before + 10, rig.received, "after a failure everything passes");
        rig.assertConsistent();
    }

    @Test void cameraInstructionsPauseCulling() {
        Rig rig = new Rig();
        rig.look(0, 65.62f, 0, 0, 0);
        rig.spawn(10, 0, 65, -40);
        var camera = new org.cloudburstmc.protocol.bedrock.packet.CameraInstructionPacket();
        camera.setSetInstruction(new org.cloudburstmc.protocol.bedrock.data.camera.CameraSetInstruction());
        rig.send(camera);
        int before = rig.received;
        for (int i = 0; i < 10; i++) {
            rig.move(10, i, 65, -40, 0);
            rig.now += 300 * MS;
        }
        assertEquals(before + 10, rig.received, "a camera elsewhere sees from another place");
    }

    @Test void adaptsCompressionToTheConnection() {
        Rig rig = new Rig();
        rig.look(0, 65.62f, 0, 0, 0);
        rig.ticks(30);
        assertEquals(6, rig.compression.get(), "a good connection keeps Geyser's level");
        rig.ping = 500;
        rig.ticks(30);
        assertEquals(8, rig.compression.get(), "a slow one gets stronger compression");
    }

    /**
     * Random traffic from a hostile or broken server side and client: random ids, orders, removals, NaN and huge
     * coordinates, random looks. Nothing may escape, held packets are released, and in the end the client agrees
     * with the server about every entity.
     */
    @Test void survivesRandomTrafficAndStaysConsistent() {
        Random random = new Random(42);
        for (int run = 0; run < 5; run++) {
            Rig rig = new Rig();
            List<Long> alive = new ArrayList<>();
            for (int step = 0; step < 5000; step++) {
                int action = random.nextInt(100);
                if (action < 5 || alive.isEmpty()) {
                    long id = 2 + random.nextInt(400);
                    if (!alive.contains(id)) {
                        rig.spawn(id, random.nextFloat() * 200 - 100, 60 + random.nextFloat() * 20, random.nextFloat() * 200 - 100);
                        alive.add(id);
                    }
                } else if (action < 7) {
                    long id = alive.remove(random.nextInt(alive.size()));
                    RemoveEntityPacket remove = new RemoveEntityPacket();
                    remove.setUniqueEntityId(id);
                    rig.send(remove);
                    rig.server.remove(id);
                } else if (action < 80) {
                    long id = alive.get(random.nextInt(alive.size()));
                    float[] state = rig.server.get(id);
                    rig.move(id, state[0] + random.nextFloat() - 0.5f, state[1], state[2] + random.nextFloat() - 0.5f, random.nextFloat() * 360);
                } else if (action < 85) {
                    // Garbage: unknown entities, NaN, infinities.
                    MoveEntityAbsolutePacket move = new MoveEntityAbsolutePacket();
                    move.setRuntimeEntityId(random.nextBoolean() ? 9_999 : SELF);
                    move.setPosition(Vector3f.from(Float.NaN, Float.POSITIVE_INFINITY, 1e30f));
                    move.setRotation(Vector3f.from(Float.NaN, 0, 0));
                    rig.send(move);
                } else if (action < 95) {
                    float x = random.nextFloat() * 200 - 100, z = random.nextFloat() * 200 - 100;
                    if (random.nextInt(10) == 0) rig.look(Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN);
                    else rig.look(x, 65.62f, z, random.nextFloat() * 180 - 90, random.nextFloat() * 720 - 360);
                } else {
                    rig.tick();
                }
            }
            assertTrue(rig.failures.isEmpty(), "no failures: " + rig.failures);
            // Bring everything into view: the client must agree with the server.
            rig.settings = new NetworkSettings(true, NetworkSettings.AUTO, false, true, NetworkSettings.AUTO, 128, false,
                    NetworkSettings.AUTO, NetworkSettings.AUTO, NetworkSettings.AUTO, NetworkSettings.AUTO, 100_000, true);
            rig.ticks(5);
            rig.assertConsistent();
        }
    }

    /** Held wrappers go back to their pool: holding never leaks Netty buffers. */
    @Test void releasesHeldPackets() {
        Rig rig = new Rig();
        rig.look(0, 65.62f, 0, 0, 0);
        rig.spawn(10, 0, 65, -40);
        rig.tick();
        rig.move(10, 0, 65, -40, 0);
        MoveEntityDeltaPacket move = new MoveEntityDeltaPacket();
        move.setRuntimeEntityId(10);
        move.getFlags().add(MoveEntityDeltaPacket.Flag.HAS_X);
        move.setX(1);
        BedrockPacketWrapper wrapper = BedrockPacketWrapper.create(0, 0, 0, move, null);
        wrapper.retain();
        rig.channel.writeOutbound(wrapper);
        assertEquals(1, wrapper.refCnt(), "the layer released its reference of the held packet");
        wrapper.release();
    }

    @Test void entityDataTypesStayIntact() {
        // Flags are kept as one set under both keys, as Bedrock expects.
        TrackedEntity entity = new TrackedEntity(5, false, 1, 2, Vector3f.ZERO, 0, 0, 0);
        SetEntityDataPacket data = new SetEntityDataPacket();
        data.setRuntimeEntityId(5);
        data.getMetadata().putFlags(EntityDataMapFlags.of(true));
        data.getMetadata().put(EntityDataTypes.NAME, "Holo");
        entity.holdData(data, 0, 0, 0);
        SetEntityDataPacket merged = (SetEntityDataPacket) entity.flush().get(0);
        assertEquals("Holo", merged.getMetadata().get(EntityDataTypes.NAME));
        assertTrue(merged.getMetadata().getFlag(EntityFlag.ON_FIRE));
        assertNotNull(merged.getMetadata().get(EntityDataTypes.FLAGS_2));
    }
}
