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

/** One Java item or block display, owned by Geyser's normal per-session entity lifecycle. */
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
    private int billboard, light = -1;

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

    /** Java's billboard constraint: 0 fixed, 1 vertical, 2 horizontal, 3 center. */
    void billboard(byte value) {
        billboard = value >= 0 && value <= 3 ? value : 0;
        updateStyle();
    }

    /** Java's brightness override ({@code block << 4 | sky << 20}), or -1 for the light at the display. */
    void brightness(int packed) {
        light = packed < 0 ? -1 : ((packed >> 4) & 15) * 16 + ((packed >> 20) & 15);
        updateStyle();
    }

    private void updateStyle() {
        if (bridge.style != null) updateProperty(bridge.style, style(billboard, false, light));
    }

    /** The style property: billboard (2 bits), dropped item (1 bit) and the light override + 1 (block * 16 + sky). */
    static int style(int billboard, boolean dropped, int light) {
        return billboard | (dropped ? 4 : 0) | (light + 1) << 3;
    }

    void item(ItemStack stack) {
        DisplayIndex.Variant selected = null;
        if (stack != null && stack.getAmount() > 0) {
            selected = select(bridge, session, stack, false);
            if (selected == null) bridge.unshown(session, unshownKeys(stack, false));
        }
        show(selected);
    }

    /** A block display's block state (Java's block state id). */
    void block(int state) {
        DisplayIndex.Variant selected = null;
        String key = blockKey(state);
        if (key != null) {
            selected = bridge.variants(session).blocks().get(key);
            if (selected == null) bridge.unshown(session, java.util.List.of(key));
        }
        show(selected);
    }

    private void show(DisplayIndex.Variant selected) {
        appearance.variant(selected == null ? -1 : selected.ordinal());
        contexts = selected == null ? null : selected.contexts();
        updateAppearance();
    }

    /** The key of a block state, or null for air and states Geyser does not know. */
    static String blockKey(int state) {
        try {
            var blockState = org.geysermc.geyser.level.block.type.BlockState.of(state);
            String text = blockState.toString();
            if (text.equals("minecraft:air") || text.equals("minecraft:cave_air") || text.equals("minecraft:void_air")) return null;
            String key = DisplayKeys.block(text);
            return DisplayKeys.valid(key) ? key : null;
        } catch (RuntimeException | LinkageError unknown) {
            return null;
        }
    }

    /**
     * The variant Java's model of a stack corresponds to: a model bone by its item model, a custom item by the
     * Bedrock item Geyser maps it to, otherwise the item's own model. With {@code customOnly} (dropped items, which
     * Bedrock draws itself) only custom items Java draws in 3D qualify.
     */
    static DisplayIndex.Variant select(GeyserDisplayBridge bridge, org.geysermc.geyser.session.GeyserSession session,
                                       ItemStack stack, boolean customOnly) {
        DisplayIndex index = bridge.variants(session);
        if (index.isEmpty()) return null;
        // Model bones have no Bedrock item: their item_model names them.
        String model = DisplayItems.itemModel(stack);
        DisplayIndex.Variant selected = model == null ? null : index.models().get(model);
        if (selected == null && !index.items().isEmpty()) {
            var item = ItemTranslator.translateToBedrock(session, stack);
            selected = index.items().get(item.getDefinition().getIdentifier());
        }
        if (customOnly) return selected == null || index.flat().contains(selected.ordinal()) ? null : selected;
        if (selected == null && model == null) {
            // A player head draws its skin; its variant is keyed by the skin.
            String skin = DisplayItems.skin(stack);
            if (skin != null) return index.heads().get(skin);
        }
        if (selected == null && model == null) {
            String base = DisplayItems.baseItem(stack.getId());
            if (base != null) selected = index.vanilla().get(base);
        }
        return selected;
    }

    /** What to report for a stack the pack cannot show: its custom keys, else its own item. */
    static java.util.List<String> unshownKeys(ItemStack stack, boolean customOnly) {
        java.util.List<String> keys = DisplayItems.keys(stack);
        if (customOnly || !keys.isEmpty()) return keys;
        String skin = DisplayItems.itemModel(stack) == null ? DisplayItems.skin(stack) : null;
        if (skin != null) {
            keys.add(DisplayKeys.head(skin));
            return keys;
        }
        String base = DisplayItems.baseItem(stack.getId());
        if (base != null && DisplayItems.itemModel(stack) == null) {
            String key = DisplayKeys.item(base);
            if (DisplayKeys.valid(key)) keys.add(key);
        }
        return keys;
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
