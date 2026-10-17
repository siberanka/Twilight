/* Copyright (c) 2026 siberanka. Twilight License (PolyForm Noncommercial 1.0.0 + terms), see LICENSE. */
package com.siberanka.twilight.integration.display;

import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.packet.AddEntityPacket;
import org.cloudburstmc.protocol.bedrock.packet.RemoveEntityPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateBlockPacket;
import org.geysermc.geyser.entity.spawn.EntitySpawnContext;
import org.geysermc.geyser.entity.type.ItemFrameEntity;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.EntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.IntEntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;

/**
 * A Java item frame. Bedrock has item frames only as blocks, always drawn with their frame and an item icon, while
 * Java draws an invisible frame's item alone with its model: ItemsAdder and other furniture plugins build furniture
 * from invisible frames, which on Bedrock lay as frames on the floor showing a small picture. An invisible frame
 * whose item the pack has a display model for is shown as that model in Java's pose instead; visible frames and
 * other items stay Geyser's frame block.
 *
 * <p>Java's ItemFrameRenderer draws the item at the frame's centre: it moves by the facing direction times 0.46875,
 * turns the frame to face that way, moves half a block out of an invisible frame, turns by the frame's rotation in
 * eighths of a turn, halves the size and draws the item's {@code fixed} display context. The display entity draws
 * an item display's transformation followed by a half turn (Java's ItemDisplayRenderer), so that half turn is undone
 * on the right.
 */
final class TwilightItemFrame extends ItemFrameEntity {
    private static final int FIXED = 8;

    private final GeyserDisplayBridge bridge;
    private ItemStack stack;
    private Direction facing = Direction.SOUTH;
    private int turns;
    private boolean invisible, shown, frameRemoved;
    private int sequence;
    private DisplayIndex.Variant model;

    TwilightItemFrame(EntitySpawnContext spawn, GeyserDisplayBridge bridge) {
        super(spawn);
        this.bridge = bridge;
    }

    @Override public void setDirection(Direction direction) {
        super.setDirection(direction);
        if (direction != null) facing = direction;
    }

    @Override public void setItemInFrame(EntityMetadata<ItemStack, ?> metadata) {
        super.setItemInFrame(metadata);
        stack = metadata.getValue();
    }

    @Override public void setItemRotation(IntEntityMetadata metadata) {
        super.setItemRotation(metadata);
        turns = Math.floorMod(metadata.getPrimitiveValue(), 8);
    }

    @Override protected void setInvisible(boolean value) {
        super.setInvisible(value);
        invisible = value;
    }

    @Override public void spawnEntity() {
        refresh(true);
        if (!shown) super.spawnEntity();
        valid = true;
    }

    @Override public void updateBedrockMetadata() {
        refresh(false);
        if (!shown) super.updateBedrockMetadata();
    }

    @Override public void despawnEntity() {
        hideModel();
        super.despawnEntity();
    }

    /** Chooses between Geyser's frame block and the display model, and sends what changed. */
    private void refresh(boolean spawning) {
        DisplayIndex.Variant chosen = null;
        if (invisible && stack != null && stack.getAmount() > 0 && bridge.appearance != null) {
            chosen = TwilightItemDisplay.select(bridge, session, stack, false);
            if (chosen == null) bridge.unshown(session, TwilightItemDisplay.unshownKeys(stack, false));
        }
        if (chosen == null) {
            if (shown || frameRemoved) {
                hideModel();
                frameRemoved = false;
                updateBlock(true);
            }
            model = null;
            return;
        }
        if (!frameRemoved) removeFrameBlock();
        if (shown && model != null && model.ordinal() == chosen.ordinal() && !spawning) {
            sendPose(chosen);
            return;
        }
        hideModel();
        model = chosen;
        sendPose(chosen);
        showModel();
    }

    private void sendPose(DisplayIndex.Variant variant) {
        float[] pose = pose(facing, turns);
        float[] contexts = variant.contexts();
        int appearance = variant.ordinal() * 9 + FIXED;
        updatePropertiesBatched(batch -> {
            batch.update(bridge.appearance, appearance);
            if (bridge.style != null) batch.update(bridge.style, TwilightItemDisplay.style(0, false, -1));
            for (int i = 0; i < 9; i++) {
                batch.update(bridge.context[i], contexts == null ? (i >= 6 ? 1f : 0f) : contexts[FIXED * 9 + i]);
            }
            if (bridge.revision != null) {
                for (int i = 0; i < DisplayPose.SIZE; i++) batch.update(bridge.to[i], pose[i]);
                batch.update(bridge.seconds, 0f);
                batch.update(bridge.delay, 0f);
                // A new revision makes the client take the pose at once (no interpolation: seconds 0).
                sequence = (sequence + 1) % 500_000;
                batch.update(bridge.revision, sequence * 2 + 1);
            }
        }, false);
    }

    private void showModel() {
        AddEntityPacket packet = new AddEntityPacket();
        packet.setIdentifier(bedrockDefinition.identifier().toString());
        packet.setRuntimeEntityId(geyserId);
        packet.setUniqueEntityId(geyserId);
        packet.setPosition(position);
        packet.setMotion(Vector3f.ZERO);
        packet.setRotation(Vector2f.ZERO);
        packet.setHeadRotation(0);
        packet.setBodyRotation(0);
        // The frame's own box, so Bedrock players can still hit and use the furniture.
        boolean flat = facing == Direction.UP || facing == Direction.DOWN;
        packet.getMetadata().put(EntityDataTypes.WIDTH, 0.75f);
        packet.getMetadata().put(EntityDataTypes.HEIGHT, flat ? 0.0625f : 0.75f);
        if (propertyManager != null) {
            propertyManager.applyIntProperties(packet.getProperties().getIntProperties());
            propertyManager.applyFloatProperties(packet.getProperties().getFloatProperties());
        }
        session.sendUpstreamPacket(packet);
        shown = true;
    }

    private void hideModel() {
        if (!shown) return;
        RemoveEntityPacket packet = new RemoveEntityPacket();
        packet.setUniqueEntityId(geyserId);
        session.sendUpstreamPacket(packet);
        shown = false;
    }

    private void removeFrameBlock() {
        Vector3i block = position.toInt();
        UpdateBlockPacket packet = new UpdateBlockPacket();
        packet.setDataLayer(0);
        packet.setBlockPosition(block);
        packet.setDefinition(session.getBlockMappings().getBedrockAir());
        packet.getFlags().add(UpdateBlockPacket.Flag.PRIORITY);
        packet.getFlags().add(UpdateBlockPacket.Flag.NETWORK);
        packet.getFlags().add(UpdateBlockPacket.Flag.NEIGHBORS);
        session.sendUpstreamPacket(packet);
        frameRemoved = true;
    }

    static float[] pose(Direction facing, int turns) {
        return FramePose.pose(step(facing), yRot(facing), turns);
    }

    private static int[] step(Direction direction) {
        return switch (direction) {
            case DOWN -> new int[]{0, -1, 0};
            case UP -> new int[]{0, 1, 0};
            case NORTH -> new int[]{0, 0, -1};
            case SOUTH -> new int[]{0, 0, 1};
            case WEST -> new int[]{-1, 0, 0};
            case EAST -> new int[]{1, 0, 0};
        };
    }

    private static float yRot(Direction direction) {
        return switch (direction) {
            case WEST -> 90;
            case NORTH -> 180;
            case EAST -> 270;
            default -> 0;
        };
    }

}
