/* Copyright (c) 2026 siberanka. Twilight License (PolyForm Noncommercial 1.0.0 + terms), see LICENSE. */
package com.siberanka.twilight.integration.display;

import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataMap;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataType;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityLinkData;
import org.cloudburstmc.protocol.bedrock.packet.AddEntityPacket;
import org.cloudburstmc.protocol.bedrock.packet.RemoveEntityPacket;
import org.geysermc.geyser.entity.BedrockEntityDefinition;
import org.geysermc.geyser.entity.VanillaEntities;
import org.geysermc.geyser.entity.spawn.EntitySpawnContext;
import org.geysermc.geyser.entity.type.Entity;
import org.geysermc.geyser.entity.type.AreaEffectCloudEntity;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.FloatEntityMetadata;

/** Preserves Geyser's cloud translation while giving invisible point anchors an inert actor. */
final class TwilightAreaEffectCloud extends AreaEffectCloudEntity {
    private final BedrockEntityDefinition originalDefinition;
    private final EntityDataMap snapshot = new EntityDataMap();
    private float javaRadius = 3f;
    private boolean javaInvisible;

    TwilightAreaEffectCloud(EntitySpawnContext context) {
        super(context);
        originalDefinition = bedrockDefinition;
    }

    @Override public void setRadius(FloatEntityMetadata data) {
        javaRadius = data.getPrimitiveValue();
        super.setRadius(data);
    }

    @Override protected void setInvisible(boolean value) {
        javaInvisible = value;
        // Runs from the superclass constructor too, before this class's fields exist.
        super.setInvisible(ours() ? CloudAnchorPolicy.invisible(javaRadius, javaInvisible) : value);
    }

    /** A spawn-event override belongs to another integration; leave it alone. */
    private boolean ours() {
        return originalDefinition != null && originalDefinition == VanillaEntities.AREA_EFFECT_CLOUD.defaultBedrockDefinition();
    }

    @Override public void updateBedrockMetadata() {
        // Geyser retains only API-exposed values after sending metadata. Keep a
        // complete snapshot so a representation change cannot lose color, scale,
        // particle type, custom name, or seat metadata sent in earlier packets.
        EntityDataMap pending = new EntityDataMap();
        metadata.apply(pending);
        snapshot.putAll(pending);
        pending.forEach(this::restorePending);

        boolean ours = ours();
        BedrockEntityDefinition desired = ours && CloudAnchorPolicy.useAnchor(javaRadius)
                ? VanillaEntities.ARMOR_STAND.defaultBedrockDefinition() : originalDefinition;
        // Java draws nothing for a radius-0 cloud, flagged invisible or not; the stand that stands in for it must not show.
        if (ours) super.setInvisible(CloudAnchorPolicy.invisible(javaRadius, javaInvisible));
        if (bedrockDefinition != desired) {
            bedrockDefinition = desired;
            if (isValid()) {
                // Replace only the client actor. Entity.despawnEntity() would
                // detach Geyser's passengers and corrupt the Java mount graph.
                RemoveEntityPacket remove = new RemoveEntityPacket();
                remove.setUniqueEntityId(geyserId());
                session.sendUpstreamPacket(remove);
                spawnEntity();
            }
        }
        super.updateBedrockMetadata();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void restorePending(EntityDataType key, Object value) {
        metadata.put(key, value);
    }

    @Override public void addAdditionalSpawnData(AddEntityPacket packet) {
        super.addAdditionalSpawnData(packet);
        snapshot.putAll(packet.getMetadata());
        packet.getMetadata().putAll(snapshot);
        packet.getMetadata().putFlags(flags);
        Entity vehicle = getVehicle();
        if (vehicle != null && vehicle.isValid()) {
            int index = vehicle.getPassengers().indexOf(this);
            packet.getEntityLinks().add(link(vehicle, this, index));
        }
        for (int i = 0; i < getPassengers().size(); i++) {
            Entity passenger = getPassengers().get(i);
            if (passenger != null && passenger.isValid()) packet.getEntityLinks().add(link(this, passenger, i));
        }
    }

    private static EntityLinkData link(Entity vehicle, Entity passenger, int index) {
        return new EntityLinkData(vehicle.geyserId(), passenger.geyserId(),
                index > 0 ? EntityLinkData.Type.PASSENGER : EntityLinkData.Type.RIDER, false, false, 0f);
    }
}
