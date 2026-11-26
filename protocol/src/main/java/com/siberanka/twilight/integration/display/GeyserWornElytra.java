/* Copyright (c) 2026 siberanka. Twilight License (PolyForm Noncommercial 1.0.0 + terms), see LICENSE. */
package com.siberanka.twilight.integration.display;

import com.siberanka.twilight.geyser.ElytraWings;
import com.siberanka.twilight.geyser.WornElytra;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.ToIntFunction;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.packet.MobArmorEquipmentPacket;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.util.Identifier;
import org.geysermc.geyser.api.event.EventRegistrar;
import org.geysermc.geyser.api.entity.property.type.GeyserIntEntityProperty;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineEntityPropertiesEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostInitializeEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostReloadEvent;
import org.geysermc.geyser.entity.type.Entity;
import org.geysermc.geyser.entity.type.LivingEntity;
import org.geysermc.geyser.entity.type.player.PlayerEntity;
import org.geysermc.geyser.inventory.GeyserItemStack;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.entity.EquipmentSlot;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.Equippable;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEquipmentPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetContentPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetSlotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundAddEntityPacket;

/**
 * Shows a custom elytra on the body of other entities, which Geyser sends to Bedrock as the vanilla elytra (see
 * {@link WornElytra}). After Geyser translates a packet that sets an entity's equipment, the bridge sends the
 * armour again with the custom item in the chest slot. The viewer's own player is never touched: its client
 * needs the vanilla elytra to glide. Geyser builds without that rewrite (no {@code translateEquipment}) need
 * nothing, and the bridge stays idle.
 *
 * <p>With wings from the pack ({@link ElytraWings}) it also sets the {@code twilight:elytra} property of every
 * player entity from the glider they wear, the viewer's own player included, so the vanilla elytra's attachable
 * can draw the wearer's wings (a player's own client has no other way to see its custom elytra).
 */
public final class GeyserWornElytra implements AutoCloseable {
    private final Logger logger;
    private final EventRegistrar registrar;
    private final List<Hook<?>> hooks = new ArrayList<>();
    private final AtomicBoolean failureLogged = new AtomicBoolean();
    private final Method translate;
    private final java.util.function.Function<GeyserSession, ElytraWings> wingsOf;
    /** The player property is registered. */
    private final boolean wingsEnabled;
    private volatile GeyserIntEntityProperty property;

    private GeyserWornElytra(Object owner, Logger logger, java.util.function.Function<GeyserSession, ElytraWings> wingsOf, boolean wingsEnabled) {
        this.logger = logger;
        this.wingsOf = wingsOf;
        this.wingsEnabled = wingsEnabled;
        this.registrar = EventRegistrar.of(owner);
        Method method;
        try {
            method = LivingEntity.class.getDeclaredMethod("translateEquipment", GeyserItemStack.class, EquipmentSlot.class);
            method.setAccessible(true);
        } catch (ReflectiveOperationException | RuntimeException absent) {
            method = null;
        }
        this.translate = method;
        hooks.add(new Hook<>(ClientboundSetEquipmentPacket.class, (session, packet) -> correct(session, packet.getEntityId())));
        hooks.add(new Hook<>(ClientboundAddEntityPacket.class, (session, packet) -> correct(session, packet.getEntityId())));
        if (wingsEnabled) {
            // The player's own chest slot arrives as inventory packets, never as an equipment packet.
            hooks.add(new Hook<>(ClientboundContainerSetSlotPacket.class, (session, packet) -> {
                if (packet.getContainerId() == 0) own(session);
            }));
            hooks.add(new Hook<>(ClientboundContainerSetContentPacket.class, (session, packet) -> {
                if (packet.getContainerId() == 0) own(session);
            }));
        }
    }

    public static GeyserWornElytra create(Object owner, Logger logger) {
        return create(owner, logger, ElytraWings.EMPTY);
    }

    /**
     * @param wings the wings of the served pack; when not empty, the {@code twilight:elytra} player property is
     *              registered and kept up to date. Geyser registers properties once, when it starts.
     */
    public static GeyserWornElytra create(Object owner, Logger logger, ElytraWings wings) {
        return create(owner, logger, session -> wings, !wings.isEmpty());
    }

    /**
     * The bridge of a Geyser on a proxy: every session uses the wings of the pack it loaded ({@code packOf} gives
     * that pack file by the session's XUID; sessions whose pack lists no wings keep Geyser's elytra untouched).
     * Geyser registers entity properties once, when it starts, and the proxy cannot know yet which backend packs
     * will list wings, so the property is always registered; at 0 (vanilla) it changes nothing, and sessions
     * without wings never get it set.
     */
    public static GeyserWornElytra createForSessions(Object owner, Logger logger,
                                                     java.util.function.Function<String, java.util.Optional<java.nio.file.Path>> packOf) {
        com.siberanka.twilight.geyser.SessionElytraWings sessions =
                new com.siberanka.twilight.geyser.SessionElytraWings(packOf, logger::warning);
        return create(owner, logger, session -> sessions.of(session.xuid()), true);
    }

    private static GeyserWornElytra create(Object owner, Logger logger,
                                           java.util.function.Function<GeyserSession, ElytraWings> wingsOf, boolean wingsEnabled) {
        GeyserWornElytra bridge = new GeyserWornElytra(owner, logger, wingsOf, wingsEnabled);
        if (wingsEnabled) {
            com.siberanka.twilight.geyser.GeyserEvents.subscribe(bridge.registrar, GeyserDefineEntityPropertiesEvent.class,
                    event -> bridge.property = event.registerIntegerProperty(Identifier.of("player"),
                            Identifier.of(ElytraWings.PROPERTY), 0, ElytraWings.MAX, 0));
        }
        if (bridge.translate == null && !wingsEnabled) return bridge;
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
        if (translate == null && !wingsEnabled) return;
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
        wings(session, entity, identifier);
        if (translate == null || !WornElytra.showsCustomItem(entity == session.getPlayerEntity(), glider, identifier)) return;
        MobArmorEquipmentPacket armor = new MobArmorEquipmentPacket();
        armor.setRuntimeEntityId(entity.geyserId());
        armor.setHelmet(piece(entity, EquipmentSlot.HELMET));
        armor.setChestplate(custom);
        armor.setLeggings(piece(entity, EquipmentSlot.LEGGINGS));
        armor.setBoots(piece(entity, EquipmentSlot.BOOTS));
        armor.setBody(piece(entity, EquipmentSlot.BODY));
        session.sendUpstreamPacket(armor);
    }

    /** The viewer's own chest, which Java sends as inventory contents. */
    private void own(GeyserSession session) {
        var player = session.getPlayerEntity();
        if (player == null) return;
        GeyserItemStack chest = session.getPlayerInventory().getEquipment().get(EquipmentSlot.CHESTPLATE);
        ItemData item = chest != null && glider(chest) ? chest.getItemData(session) : null;
        wings(session, player, item == null || item.getDefinition() == null ? null : item.getDefinition().getIdentifier());
    }

    /** Sets the wings a player entity wears: those of the Bedrock item, or 0 (vanilla) for anything else. */
    private void wings(GeyserSession session, Entity entity, String bedrockIdentifier) {
        GeyserIntEntityProperty registered = property;
        if (registered == null || !(entity instanceof PlayerEntity)) return;
        ElytraWings wings = wingsOf.apply(session);
        if (wings.isEmpty()) return;
        entity.updateProperty(registered, wings.indexOf(bedrockIdentifier));
    }

    private ItemData piece(LivingEntity entity, EquipmentSlot slot) throws ReflectiveOperationException {
        return (ItemData) translate.invoke(entity, entity.getItemInSlot(slot), slot);
    }

    private interface Action<P> {
        void run(GeyserSession session, P packet) throws ReflectiveOperationException;
    }

    private final class Hook<P extends Packet> extends PacketTranslator<P> implements com.siberanka.twilight.geyser.TranslatorChain.Link {
        private final Class<P> type;
        private final Action<P> action;
        private volatile PacketTranslator<? extends Packet> original;

        private Hook(Class<P> type, Action<P> action) {
            this.type = type;
            this.action = action;
        }

        @SuppressWarnings("unchecked")
        @Override public void translate(GeyserSession session, P packet) {
            ((PacketTranslator<P>) original).translate(session, packet);
            try {
                action.run(session, packet);
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
