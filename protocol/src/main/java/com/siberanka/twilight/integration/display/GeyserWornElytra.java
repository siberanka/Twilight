/* Copyright (c) 2026 siberanka. Twilight License (PolyForm Noncommercial 1.0.0 + terms), see LICENSE. */
package com.siberanka.twilight.integration.display;

import com.siberanka.twilight.geyser.WornElytra;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.ToIntFunction;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.packet.MobArmorEquipmentPacket;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.event.EventRegistrar;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostInitializeEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostReloadEvent;
import org.geysermc.geyser.entity.type.Entity;
import org.geysermc.geyser.entity.type.LivingEntity;
import org.geysermc.geyser.inventory.GeyserItemStack;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.entity.EquipmentSlot;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.Equippable;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEquipmentPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundAddEntityPacket;

/**
 * Shows a custom elytra on the body of other entities, which Geyser sends to Bedrock as the vanilla elytra (see
 * {@link WornElytra}). After Geyser translates a packet that sets an entity's equipment, the bridge sends the
 * armour again with the custom item in the chest slot. The viewer's own player is never touched: its client
 * needs the vanilla elytra to glide. Geyser builds without that rewrite (no {@code translateEquipment}) need
 * nothing, and the bridge stays idle.
 */
public final class GeyserWornElytra implements AutoCloseable {
    private final Logger logger;
    private final EventRegistrar registrar;
    private final List<Hook<?>> hooks = new ArrayList<>();
    private final AtomicBoolean failureLogged = new AtomicBoolean();
    private final Method translate;

    private GeyserWornElytra(Object owner, Logger logger) {
        this.logger = logger;
        this.registrar = EventRegistrar.of(owner);
        Method method;
        try {
            method = LivingEntity.class.getDeclaredMethod("translateEquipment", GeyserItemStack.class, EquipmentSlot.class);
            method.setAccessible(true);
        } catch (ReflectiveOperationException | RuntimeException absent) {
            method = null;
        }
        this.translate = method;
        hooks.add(new Hook<>(ClientboundSetEquipmentPacket.class, ClientboundSetEquipmentPacket::getEntityId));
        hooks.add(new Hook<>(ClientboundAddEntityPacket.class, ClientboundAddEntityPacket::getEntityId));
    }

    public static GeyserWornElytra create(Object owner, Logger logger) {
        GeyserWornElytra bridge = new GeyserWornElytra(owner, logger);
        if (bridge.translate == null) return bridge;
        com.siberanka.twilight.geyser.GeyserEvents.subscribe(bridge.registrar, GeyserPostInitializeEvent.class, event -> bridge.attach());
        com.siberanka.twilight.geyser.GeyserEvents.subscribe(bridge.registrar, GeyserPostReloadEvent.class, event -> bridge.attach());
        if (Registries.JAVA_PACKET_TRANSLATORS.get().get(ClientboundSetEquipmentPacket.class) != null) bridge.attach();
        return bridge;
    }

    private synchronized void attach() {
        var translators = Registries.JAVA_PACKET_TRANSLATORS.get();
        for (Hook<?> hook : hooks) {
            PacketTranslator<? extends Packet> current = translators.get(hook.type);
            if (current == null || com.siberanka.twilight.geyser.TranslatorChain.installed(current, hook)) continue;
            hook.original = current;
            translators.put(hook.type, hook);
        }
    }

    @Override public synchronized void close() {
        if (translate == null) return;
        GeyserApi.api().eventBus().unregisterAll(registrar);
        var translators = Registries.JAVA_PACKET_TRANSLATORS.get();
        for (Hook<?> hook : hooks) if (hook.original != null) translators.replace(hook.type, hook, hook.original);
    }

    /** Whether Geyser's glider rule applies to the stack: a glider that is worn in the chest slot. */
    private static boolean glider(GeyserItemStack stack) {
        if (stack.isEmpty() || stack.getComponent(DataComponentTypes.GLIDER) == null) return false;
        Equippable equippable = stack.getComponent(DataComponentTypes.EQUIPPABLE);
        return equippable != null && equippable.slot() == EquipmentSlot.CHESTPLATE;
    }

    private void correct(GeyserSession session, int javaId) throws ReflectiveOperationException {
        Entity found = session.getEntityCache().getEntityByJavaId(javaId);
        if (!(found instanceof LivingEntity entity) || !entity.isValid()) return;
        GeyserItemStack chest = entity.getItemInSlot(EquipmentSlot.CHESTPLATE);
        boolean glider = glider(chest);
        ItemData custom = glider ? chest.getItemData(session) : null;
        String identifier = custom == null || custom.getDefinition() == null ? null : custom.getDefinition().getIdentifier();
        if (!WornElytra.showsCustomItem(entity == session.getPlayerEntity(), glider, identifier)) return;
        MobArmorEquipmentPacket armor = new MobArmorEquipmentPacket();
        armor.setRuntimeEntityId(entity.geyserId());
        armor.setHelmet(piece(entity, EquipmentSlot.HELMET));
        armor.setChestplate(custom);
        armor.setLeggings(piece(entity, EquipmentSlot.LEGGINGS));
        armor.setBoots(piece(entity, EquipmentSlot.BOOTS));
        armor.setBody(piece(entity, EquipmentSlot.BODY));
        session.sendUpstreamPacket(armor);
    }

    private ItemData piece(LivingEntity entity, EquipmentSlot slot) throws ReflectiveOperationException {
        return (ItemData) translate.invoke(entity, entity.getItemInSlot(slot), slot);
    }

    private final class Hook<P extends Packet> extends PacketTranslator<P> implements com.siberanka.twilight.geyser.TranslatorChain.Link {
        private final Class<P> type;
        private final ToIntFunction<P> entityId;
        private volatile PacketTranslator<? extends Packet> original;

        private Hook(Class<P> type, ToIntFunction<P> entityId) {
            this.type = type;
            this.entityId = entityId;
        }

        @SuppressWarnings("unchecked")
        @Override public void translate(GeyserSession session, P packet) {
            ((PacketTranslator<P>) original).translate(session, packet);
            try {
                correct(session, entityId.applyAsInt(packet));
            } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                if (failureLogged.compareAndSet(false, true)) {
                    logger.log(Level.WARNING, "Could not show a custom elytra on other players; Bedrock viewers see "
                            + "the vanilla elytra. Further failures are not logged.", failure);
                }
            }
        }

        @Override public boolean shouldExecuteInEventLoop() {
            return original.shouldExecuteInEventLoop();
        }

        @Override public Object next() {
            return original;
        }
    }
}
