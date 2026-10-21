/* Copyright (c) 2026 siberanka. Twilight License (PolyForm Noncommercial 1.0.0 + terms), see LICENSE. */
package com.siberanka.twilight.integration.display;

import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.event.EventRegistrar;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostInitializeEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostReloadEvent;
import org.geysermc.geyser.entity.type.Entity;
import org.geysermc.geyser.entity.type.LivingEntity;
import org.geysermc.geyser.entity.type.living.ArmorStandEntity;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundPlayerInfoUpdatePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundRemoveEntitiesPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEntityDataPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetPassengersPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.scoreboard.ClientboundSetPlayerTeamPacket;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Hides the name of a living entity while something rides it, as Java does.
 *
 * <p>Java never draws the name tag of a living entity (player, mob) that carries passengers;
 * armor stands follow their own rule and keep it. Nameplate plugins rely on it: CustomNameplates and similar ones mount text
 * displays on the player and leave the vanilla name to that rule. Bedrock draws the name
 * anyway, so Bedrock players saw a second, plain name under every nameplate. The bridge blanks
 * the Bedrock name of such vehicles after Geyser translates the packets that set passengers or
 * names, and restores it when the last passenger leaves. Packets themselves are never changed.
 */
public final class GeyserRiderNames implements AutoCloseable {
    private final Logger logger;
    private final EventRegistrar registrar;
    private final List<Hook<?>> hooks = new ArrayList<>();
    private final Map<GeyserSession, Set<Entity>> hidden = Collections.synchronizedMap(new WeakHashMap<>());
    private final AtomicBoolean failureLogged = new AtomicBoolean();

    private GeyserRiderNames(Object owner, Logger logger) {
        this.logger = logger;
        this.registrar = EventRegistrar.of(owner);
        hooks.add(new Hook<>(ClientboundSetPassengersPacket.class,
                (session, packet) -> update(session, session.getEntityCache().getEntityByJavaId(packet.getEntityId()))));
        hooks.add(new Hook<>(ClientboundSetEntityDataPacket.class, (session, packet) -> {
            Entity entity = session.getEntityCache().getEntityByJavaId(packet.getEntityId());
            if (entity != null && hiddenOf(session).contains(entity)) update(session, entity);
        }));
        hooks.add(new Hook<>(ClientboundRemoveEntitiesPacket.class, (session, packet) -> recheck(session)));
        hooks.add(new Hook<>(ClientboundSetPlayerTeamPacket.class, (session, packet) -> recheck(session)));
        hooks.add(new Hook<>(ClientboundPlayerInfoUpdatePacket.class, (session, packet) -> recheck(session)));
    }

    public static GeyserRiderNames create(Object owner, Logger logger) {
        GeyserRiderNames bridge = new GeyserRiderNames(owner, logger);
        com.siberanka.twilight.geyser.GeyserEvents.subscribe(bridge.registrar, GeyserPostInitializeEvent.class, event -> bridge.attach());
        com.siberanka.twilight.geyser.GeyserEvents.subscribe(bridge.registrar, GeyserPostReloadEvent.class, event -> bridge.attach());
        if (Registries.JAVA_PACKET_TRANSLATORS.get().get(ClientboundSetPassengersPacket.class) != null) bridge.attach();
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
        GeyserApi.api().eventBus().unregisterAll(registrar);
        var translators = Registries.JAVA_PACKET_TRANSLATORS.get();
        for (Hook<?> hook : hooks) if (hook.original != null) translators.replace(hook.type, hook, hook.original);
    }

    private Set<Entity> hiddenOf(GeyserSession session) {
        return hidden.computeIfAbsent(session, ignored -> ConcurrentHashMap.newKeySet());
    }

    private void recheck(GeyserSession session) {
        Set<Entity> entities = hidden.get(session);
        if (entities != null) for (Entity entity : List.copyOf(entities)) update(session, entity);
    }

    /**
     * Java's rule (LivingEntityRenderer#shouldShowName): no name while the entity is a vehicle. Armor stands
     * have their own rule without it (ArmorStandRenderer), so a ridden armor stand keeps its name.
     */
    static boolean hidesName(Entity entity) {
        return entity instanceof LivingEntity && !(entity instanceof ArmorStandEntity) && !entity.getPassengers().isEmpty();
    }

    private void update(GeyserSession session, Entity entity) {
        if (entity == null || entity == session.getPlayerEntity()) return;
        Set<Entity> entities = hiddenOf(session);
        boolean present = session.getEntityCache().getEntityByGeyserId(entity.geyserId()) == entity;
        if (present && hidesName(entity)) {
            entities.add(entity);
            entity.getMetadata().put(EntityDataTypes.NAME, "");
            entity.updateBedrockMetadata();
        } else if (entities.remove(entity) && present) {
            String name = entity.getNametag();
            entity.getMetadata().put(EntityDataTypes.NAME, name == null ? "" : name);
            entity.updateBedrockMetadata();
        }
    }

    @FunctionalInterface
    private interface After<P> {
        void run(GeyserSession session, P packet);
    }

    private final class Hook<P extends Packet> extends PacketTranslator<P> implements com.siberanka.twilight.geyser.TranslatorChain.Link {
        private final Class<P> type;
        private final After<P> after;
        private volatile PacketTranslator<? extends Packet> original;

        private Hook(Class<P> type, After<P> after) {
            this.type = type;
            this.after = after;
        }

        @SuppressWarnings("unchecked")
        @Override public void translate(GeyserSession session, P packet) {
            ((PacketTranslator<P>) original).translate(session, packet);
            try {
                after.run(session, packet);
            } catch (RuntimeException | LinkageError failure) {
                if (failureLogged.compareAndSet(false, true)) {
                    logger.log(Level.WARNING, "Could not apply Java's rider name rule; Bedrock keeps the names of "
                            + "ridden entities. Further failures are not logged.", failure);
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
