/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.network;

import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataMap;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.data.entity.FloatEntityProperty;
import org.cloudburstmc.protocol.bedrock.data.entity.IntEntityProperty;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.MoveEntityAbsolutePacket;
import org.cloudburstmc.protocol.bedrock.packet.MoveEntityDeltaPacket;
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetEntityDataPacket;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One entity a Bedrock client knows: where it is (from the packets the client got or was meant to get) and the
 * updates held back for it. Held updates are merged: the latest position, rotation and data win, a teleport stays
 * a teleport, and the entity is brought up to date with one packet per kind. The state is never older than what
 * the server sent, so whatever is held, the client ends with exactly the server's latest state.
 *
 * <p>Confined to the player's network thread.
 */
final class TrackedEntity {
    enum Move { NONE, ABSOLUTE, DELTA, PLAYER }

    final long id;
    final boolean player;
    /**
     * Drawn away from its position (display entities: their model may sit blocks away through its transformation):
     * a wide margin in the view and never hidden by walls.
     */
    final boolean offset;
    float width, height, scale = 1;
    /** Shows a name tag, which Bedrock draws through walls: never hidden by walls. */
    boolean named;
    /** The latest known position and rotation (Bedrock coordinates and degrees). */
    float x, y, z, pitch, yaw, headYaw;
    boolean onGround;

    long lastSent;
    long heldSince;
    boolean occluded;
    byte occludedVotes;

    // Held movement.
    Move held = Move.NONE;
    boolean teleport, forceMove;
    long ticks;
    int sender, target;
    MovePlayerPacket.Mode mode;
    MovePlayerPacket.TeleportationCause cause;
    long riding;
    int entityType;

    // Held data.
    EntityDataMap data;
    Map<Integer, Integer> intProperties;
    Map<Integer, Float> floatProperties;
    long dataTick;

    TrackedEntity(long id, boolean player, float width, float height, Vector3f position, float pitch, float yaw,
                  float headYaw) {
        this(id, player, false, width, height, position, pitch, yaw, headYaw);
    }

    TrackedEntity(long id, boolean player, boolean offset, float width, float height, Vector3f position, float pitch,
                  float yaw, float headYaw) {
        this.id = id;
        this.player = player;
        this.offset = offset;
        this.width = width;
        this.height = height;
        this.named = player;
        place(position);
        this.pitch = finite(pitch, 0);
        this.yaw = finite(yaw, 0);
        this.headYaw = finite(headYaw, this.yaw);
    }

    /** Size and name tag from entity data (spawn or update). */
    void describe(Map<? extends org.cloudburstmc.protocol.bedrock.data.entity.EntityDataType<?>, ?> metadata) {
        if (metadata == null || metadata.isEmpty()) return;
        if (metadata.get(EntityDataTypes.WIDTH) instanceof Float value && Float.isFinite(value) && value >= 0) width = Math.min(value, 64);
        if (metadata.get(EntityDataTypes.HEIGHT) instanceof Float value && Float.isFinite(value) && value >= 0) height = Math.min(value, 64);
        if (metadata.get(EntityDataTypes.SCALE) instanceof Float value && Float.isFinite(value) && value > 0) scale = Math.min(value, 64);
        if (metadata.get(EntityDataTypes.NAME) instanceof CharSequence name) named = player || name.length() > 0;
    }

    float sizeWidth() {
        return offset ? 8 : width * scale;
    }

    float sizeHeight() {
        return offset ? 8 : height * scale;
    }

    boolean holding() {
        return held != Move.NONE || data != null;
    }

    private void place(Vector3f position) {
        if (position == null) return;
        x = finite(position.getX(), x);
        y = finite(position.getY(), y);
        z = finite(position.getZ(), z);
    }

    /** Takes the state of a movement that is sent on. */
    void observe(BedrockPacket packet) {
        switch (packet) {
            case MoveEntityAbsolutePacket move -> {
                place(move.getPosition());
                rotate(move.getRotation());
                onGround = move.isOnGround();
            }
            case MoveEntityDeltaPacket move -> delta(move);
            case MovePlayerPacket move -> {
                place(move.getPosition());
                rotate(move.getRotation());
                onGround = move.isOnGround();
            }
            default -> { }
        }
    }

    private void rotate(Vector3f rotation) {
        if (rotation == null) return;
        pitch = finite(rotation.getX(), pitch);
        yaw = finite(rotation.getY(), yaw);
        headYaw = finite(rotation.getZ(), headYaw);
    }

    private void delta(MoveEntityDeltaPacket move) {
        var flags = move.getFlags();
        if (flags.contains(MoveEntityDeltaPacket.Flag.HAS_X)) x = finite(move.getX(), x);
        if (flags.contains(MoveEntityDeltaPacket.Flag.HAS_Y)) y = finite(move.getY(), y);
        if (flags.contains(MoveEntityDeltaPacket.Flag.HAS_Z)) z = finite(move.getZ(), z);
        if (flags.contains(MoveEntityDeltaPacket.Flag.HAS_PITCH)) pitch = finite(move.getPitch(), pitch);
        if (flags.contains(MoveEntityDeltaPacket.Flag.HAS_YAW)) yaw = finite(move.getYaw(), yaw);
        if (flags.contains(MoveEntityDeltaPacket.Flag.HAS_HEAD_YAW)) headYaw = finite(move.getHeadYaw(), headYaw);
        onGround = flags.contains(MoveEntityDeltaPacket.Flag.ON_GROUND);
    }

    /** Holds a movement back: the entity's state takes it, and the next flush sends the merged result. */
    void holdMove(BedrockPacket packet, int sender, int target, long now) {
        observe(packet);
        if (!holding()) heldSince = now;
        this.sender = sender;
        this.target = target;
        switch (packet) {
            case MoveEntityAbsolutePacket move -> {
                held = held == Move.DELTA || held == Move.PLAYER ? held : Move.ABSOLUTE;
                teleport |= move.isTeleported();
                forceMove |= move.isForceMove();
            }
            case MoveEntityDeltaPacket move -> {
                held = held == Move.PLAYER ? held : Move.DELTA;
                teleport |= move.getFlags().contains(MoveEntityDeltaPacket.Flag.TELEPORTING);
                forceMove |= move.isForceMove();
                ticks = move.getTicks();
            }
            case MovePlayerPacket move -> {
                held = Move.PLAYER;
                if (move.getMode() == MovePlayerPacket.Mode.TELEPORT || mode != MovePlayerPacket.Mode.TELEPORT) {
                    mode = move.getMode();
                    cause = move.getTeleportationCause();
                    entityType = move.getEntityType();
                }
                riding = move.getRidingRuntimeEntityId();
                ticks = move.getTick();
            }
            default -> throw new IllegalArgumentException("not a movement: " + packet.getClass().getSimpleName());
        }
    }

    /** Holds data back, merged with what is already held (newer values win). */
    void holdData(SetEntityDataPacket packet, int sender, int target, long now) {
        if (!holding()) heldSince = now;
        this.sender = sender;
        this.target = target;
        if (data == null) data = new EntityDataMap();
        for (var entry : packet.getMetadata().entrySet()) {
            Object value = entry.getValue();
            if (value == null) continue;
            if (entry.getKey() == EntityDataTypes.FLAGS || entry.getKey() == EntityDataTypes.FLAGS_2) {
                @SuppressWarnings("unchecked")
                EnumMap<EntityFlag, Boolean> flags = new EnumMap<>((Map<EntityFlag, Boolean>) value);
                data.putFlags(flags);
            } else {
                data.put(entry.getKey(), value);
            }
        }
        var properties = packet.getProperties();
        if (!properties.getIntProperties().isEmpty()) {
            if (intProperties == null) intProperties = new LinkedHashMap<>();
            for (IntEntityProperty property : properties.getIntProperties()) intProperties.put(property.getIndex(), property.getValue());
        }
        if (!properties.getFloatProperties().isEmpty()) {
            if (floatProperties == null) floatProperties = new LinkedHashMap<>();
            for (FloatEntityProperty property : properties.getFloatProperties()) floatProperties.put(property.getIndex(), property.getValue());
        }
        dataTick = packet.getTick();
    }

    /** The packets that bring the client up to date, data first; clears what was held. */
    List<BedrockPacket> flush() {
        List<BedrockPacket> packets = new ArrayList<>(2);
        if (data != null || intProperties != null || floatProperties != null) {
            SetEntityDataPacket packet = new SetEntityDataPacket();
            packet.setRuntimeEntityId(id);
            if (data != null) packet.getMetadata().putAll(dataOf(data));
            if (intProperties != null) intProperties.forEach((index, value) -> packet.getProperties().getIntProperties().add(new IntEntityProperty(index, value)));
            if (floatProperties != null) floatProperties.forEach((index, value) -> packet.getProperties().getFloatProperties().add(new FloatEntityProperty(index, value)));
            packet.setTick(dataTick);
            packets.add(packet);
        }
        switch (held) {
            case ABSOLUTE -> {
                MoveEntityAbsolutePacket packet = new MoveEntityAbsolutePacket();
                packet.setRuntimeEntityId(id);
                packet.setPosition(Vector3f.from(x, y, z));
                packet.setRotation(Vector3f.from(pitch, yaw, headYaw));
                packet.setOnGround(onGround);
                packet.setTeleported(teleport);
                packet.setForceMove(forceMove);
                packets.add(packet);
            }
            case DELTA -> {
                MoveEntityDeltaPacket packet = new MoveEntityDeltaPacket();
                packet.setRuntimeEntityId(id);
                var flags = packet.getFlags();
                flags.add(MoveEntityDeltaPacket.Flag.HAS_X);
                flags.add(MoveEntityDeltaPacket.Flag.HAS_Y);
                flags.add(MoveEntityDeltaPacket.Flag.HAS_Z);
                flags.add(MoveEntityDeltaPacket.Flag.HAS_PITCH);
                flags.add(MoveEntityDeltaPacket.Flag.HAS_YAW);
                flags.add(MoveEntityDeltaPacket.Flag.HAS_HEAD_YAW);
                if (onGround) flags.add(MoveEntityDeltaPacket.Flag.ON_GROUND);
                if (teleport) flags.add(MoveEntityDeltaPacket.Flag.TELEPORTING);
                packet.setX(x);
                packet.setY(y);
                packet.setZ(z);
                packet.setPitch(pitch);
                packet.setYaw(yaw);
                packet.setHeadYaw(headYaw);
                packet.setOnGround(onGround);
                packet.setForceMove(forceMove);
                packet.setTicks(ticks);
                packets.add(packet);
            }
            case PLAYER -> {
                MovePlayerPacket packet = new MovePlayerPacket();
                packet.setRuntimeEntityId(id);
                packet.setPosition(Vector3f.from(x, y, z));
                packet.setRotation(Vector3f.from(pitch, yaw, headYaw));
                packet.setMode(mode == null ? MovePlayerPacket.Mode.NORMAL : mode);
                if (packet.getMode() == MovePlayerPacket.Mode.TELEPORT) {
                    packet.setTeleportationCause(cause == null ? MovePlayerPacket.TeleportationCause.UNKNOWN : cause);
                    packet.setEntityType(entityType);
                }
                packet.setOnGround(onGround);
                packet.setRidingRuntimeEntityId(riding);
                packet.setTick(ticks);
                packets.add(packet);
            }
            case NONE -> { }
        }
        clear();
        return packets;
    }

    private static Map<org.cloudburstmc.protocol.bedrock.data.entity.EntityDataType<?>, Object> dataOf(EntityDataMap map) {
        Map<org.cloudburstmc.protocol.bedrock.data.entity.EntityDataType<?>, Object> copy = new LinkedHashMap<>();
        for (var entry : map.entrySet()) copy.put(entry.getKey(), entry.getValue());
        return copy;
    }

    void clear() {
        held = Move.NONE;
        teleport = false;
        forceMove = false;
        mode = null;
        cause = null;
        data = null;
        intProperties = null;
        floatProperties = null;
    }

    double distanceSquared(double ex, double ey, double ez) {
        double dx = x - ex, dy = y - ey, dz = z - ez;
        return dx * dx + dy * dy + dz * dz;
    }

    private static float finite(float value, float fallback) {
        return Float.isFinite(value) && Math.abs(value) < 3.0e7f ? value : fallback;
    }
}
