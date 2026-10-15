/* Copyright (c) 2026 siberanka. Twilight License (PolyForm Noncommercial 1.0.0 + terms), see LICENSE. */
package com.siberanka.twilight.integration.display;

import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.protocol.bedrock.packet.AddEntityPacket;
import org.geysermc.geyser.entity.spawn.EntitySpawnContext;
import org.geysermc.geyser.entity.type.ItemEntity;
import org.geysermc.geyser.translator.item.ItemTranslator;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.EntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;

/**
 * A Java item entity (a dropped item, or the floating item of a hologram). Bedrock draws dropped items itself, and
 * for items Java draws flat or as a block it looks the same; a custom item with a 3D model, however, would lie on the
 * ground as a flat icon. Such items are shown with their display model in Java's ground pose instead, bobbing and
 * spinning like Java's ItemEntityRenderer (on the client, with its own random phase, as on Java). Everything else
 * stays Geyser's item entity.
 */
final class TwilightDroppedItem extends ItemEntity {
    private static final int GROUND = 7;

    private final GeyserDisplayBridge bridge;
    private DisplayIndex.Variant model;

    TwilightDroppedItem(EntitySpawnContext spawn, GeyserDisplayBridge bridge) {
        super(spawn);
        this.bridge = bridge;
    }

    @Override public void setItem(EntityMetadata<ItemStack, ?> metadata) {
        ItemStack stack = metadata.getValue();
        DisplayIndex.Variant chosen = null;
        if (stack != null && stack.getAmount() > 0 && bridge.style != null) {
            chosen = TwilightItemDisplay.select(bridge, session, stack, true);
            if (chosen == null) bridge.unshown(session, TwilightItemDisplay.unshownKeys(stack, true));
        }
        if (chosen == null && model == null) {
            super.setItem(metadata);
            return;
        }
        var translated = ItemTranslator.translateToBedrock(session, stack);
        if (chosen != null && model != null && chosen.ordinal() == model.ordinal()) {
            // Only the count changed; Java's extra copies of a stack are not drawn.
            item = translated;
            return;
        }
        if (valid) despawnEntity();
        model = chosen;
        item = translated;
        if (model == null) {
            setOffsetOverride(null);
        } else {
            // Java draws the item at the entity's position; Bedrock's item entity offset does not apply.
            setOffsetOverride(0f);
            float[] contexts = model.contexts();
            int appearance = model.ordinal() * 9 + GROUND;
            int style = TwilightItemDisplay.style(0, true, -1);
            updatePropertiesBatched(batch -> {
                batch.update(bridge.appearance, appearance);
                batch.update(bridge.style, style);
                for (int i = 0; i < 9; i++) batch.update(bridge.context[i], contexts == null ? (i >= 6 ? 1f : 0f) : contexts[GROUND * 9 + i]);
            }, false);
        }
        spawnEntity();
    }

    @Override public void spawnEntity() {
        if (model == null) {
            super.spawnEntity();
            return;
        }
        if (item == null) return;
        AddEntityPacket packet = new AddEntityPacket();
        packet.setIdentifier(bedrockDefinition.identifier().toString());
        packet.setRuntimeEntityId(geyserId);
        packet.setUniqueEntityId(geyserId);
        packet.setPosition(spawnPosition(bedrockPosition()));
        packet.setMotion(motion);
        packet.setRotation(Vector2f.from(0, getYaw()));
        packet.setHeadRotation(getYaw());
        packet.setBodyRotation(getYaw());
        packet.getMetadata().putFlags(flags);
        metadata.apply(packet.getMetadata());
        if (propertyManager != null) {
            propertyManager.applyIntProperties(packet.getProperties().getIntProperties());
            propertyManager.applyFloatProperties(packet.getProperties().getFloatProperties());
        }
        valid = true;
        session.sendUpstreamPacket(packet);
        setFlagsDirty(false);
    }
}
