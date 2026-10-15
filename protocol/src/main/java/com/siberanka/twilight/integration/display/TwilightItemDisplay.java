/* Copyright (c) 2026 siberanka. Twilight License (PolyForm Noncommercial 1.0.0 + terms), see LICENSE. */
package com.siberanka.twilight.integration.display;

import org.cloudburstmc.math.imaginary.Quaternionf;
import org.cloudburstmc.math.vector.Vector3f;
import org.geysermc.geyser.entity.spawn.EntitySpawnContext;
import org.geysermc.geyser.entity.type.Entity;
import org.geysermc.geyser.translator.item.ItemTranslator;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;

import java.util.Arrays;

/** One Java display, owned by Geyser's normal per-session entity lifecycle. */
final class TwilightItemDisplay extends Entity {
    private static final float[] IDENTITY_CONTEXT = {0, 0, 0, 0, 0, 0, 1, 1, 1};
    /** Revisions are {@code sequence * 2 + 1} with a new pose, {@code sequence * 2} with only new timing. */
    private static final int SEQUENCES = 500_000;

    private final GeyserDisplayBridge bridge;
    private final float[] pending = DisplayPose.identity().values();
    private final DisplayTimeline timeline = new DisplayTimeline();
    private final DisplayAppearance appearance = new DisplayAppearance();
    private final float[] sentContext = IDENTITY_CONTEXT.clone();
    private float[] contexts;
    private Integer durationTicks, delayTicks;
    private int sequence;
    private boolean poseDirty;

    TwilightItemDisplay(EntitySpawnContext spawn, GeyserDisplayBridge bridge) {
        super(spawn);
        this.bridge = bridge;
        // Java displays have one entity rotation, not an independent living head.
        // A zero head yaw makes Bedrock turn mounted meshes toward that heading.
        setHeadYaw(getYaw());
    }

    @Override public void moveRelativeRaw(double x, double y, double z, float yaw, float pitch,
                                          float headYaw, boolean onGround) {
        super.moveRelativeRaw(x, y, z, yaw, pitch, yaw, onGround);
    }

    @Override public void moveAbsoluteRaw(Vector3f position, float yaw, float pitch, float headYaw,
                                          boolean onGround, boolean teleported) {
        super.moveAbsoluteRaw(position, yaw, pitch, yaw, onGround, teleported);
    }

    @Override public void updateHeadLookRotation(float headYaw) {
        // A display has no independently rotatable head.
    }

    @Override public void setRiderSeatPosition(Vector3f position) {
        Entity vehicle = getVehicle();
        if (vehicle != null && vehicle.getEntityType() == EntityType.AREA_EFFECT_CLOUD) {
            // Java clouds attach passengers at their full height; displays attach
            // at their feet. Geyser's generic 75% mount offset places a display
            // 0.125 blocks too low, potentially sampling light inside the floor.
            position = Vector3f.from(position.getX(), vehicle.getJavaDefinition().height(), position.getZ());
        }
        super.setRiderSeatPosition(position);
    }

    void delay(int ticks) { delayTicks = Math.clamp(ticks, -72_000, 72_000); }
    void duration(int ticks) { durationTicks = Math.clamp(ticks, 0, 72_000); }
    void vector(int offset, Vector3f value) {
        pending[offset] = value.getX(); pending[offset + 1] = value.getY(); pending[offset + 2] = value.getZ(); poseDirty = true;
    }
    void rotation(int offset, Quaternionf value) {
        pending[offset] = value.getX(); pending[offset + 1] = value.getY(); pending[offset + 2] = value.getZ(); pending[offset + 3] = value.getW(); poseDirty = true;
    }
    void context(byte value) { appearance.context(value); updateAppearance(); }
    void viewRange(float value) { appearance.viewRange(value); updateAppearance(); }

    void item(ItemStack stack) {
        DisplayIndex.Variant selected = null;
        if (stack != null && stack.getAmount() > 0) {
            DisplayIndex index = bridge.variants(session);
            // Model bones have no Bedrock item: their item_model names them.
            String model = index.models().isEmpty() ? null : DisplayItems.itemModel(stack);
            if (model != null) selected = index.models().get(model);
            if (selected == null && !index.items().isEmpty()) {
                var item = ItemTranslator.translateToBedrock(session, stack);
                selected = index.items().get(item.getDefinition().getIdentifier());
            }
            if (selected == null) bridge.unshown(session, DisplayItems.keys(stack));
        }
        appearance.variant(selected == null ? -1 : selected.ordinal());
        contexts = selected == null ? null : selected.contexts();
        updateAppearance();
    }

    private void updateAppearance() {
        float[] current = contexts == null ? IDENTITY_CONTEXT
                : Arrays.copyOfRange(contexts, appearance.context() * 9, appearance.context() * 9 + 9);
        boolean contextChanged = !Arrays.equals(current, sentContext);
        if (contextChanged) System.arraycopy(current, 0, sentContext, 0, 9);
        int packed = appearance.packed();
        if (bridge.appearance == null) return;
        if (!contextChanged) {
            updateProperty(bridge.appearance, packed);
            return;
        }
        // The transform and the item change together, so the client never shows one with the other's values.
        updatePropertiesBatched(batch -> {
            batch.update(bridge.appearance, packed);
            for (int i = 0; i < 9; i++) batch.update(bridge.context[i], current[i]);
        }, false);
    }

    @Override public void updateBedrockMetadata() {
        if ((poseDirty || durationTicks != null || delayTicks != null) && bridge != null && bridge.revision != null) {
            boolean posed = poseDirty;
            var state = timeline.update(posed ? new DisplayPose(pending) : null, durationTicks, delayTicks, System.nanoTime());
            poseDirty = false;
            durationTicks = null;
            delayTicks = null;
            sequence = (sequence + 1) % SEQUENCES;
            int revision = sequence * 2 + (posed ? 1 : 0);
            DisplayPose target = state.to();
            // The client starts a new pose from the one it shows, as Java's DisplayRenderer does.
            updatePropertiesBatched(batch -> {
                if (posed) for (int i = 0; i < DisplayPose.SIZE; i++) batch.update(bridge.to[i], target.value(i));
                batch.update(bridge.seconds, state.seconds());
                batch.update(bridge.delay, state.delay());
                batch.update(bridge.revision, revision);
            }, false);
        }
        super.updateBedrockMetadata();
    }
}
