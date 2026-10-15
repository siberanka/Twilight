/* Copyright (c) 2026 siberanka. Twilight License (PolyForm Noncommercial 1.0.0 + terms), see LICENSE. */
package com.siberanka.twilight.integration.display;

import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.entity.custom.CustomEntityDefinition;
import org.geysermc.geyser.api.entity.property.type.GeyserFloatEntityProperty;
import org.geysermc.geyser.api.entity.property.type.GeyserIntEntityProperty;
import org.geysermc.geyser.api.event.EventRegistrar;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineEntitiesEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineEntityPropertiesEvent;
import org.geysermc.geyser.api.util.Identifier;
import org.geysermc.geyser.entity.BedrockEntityDefinition;
import org.geysermc.geyser.entity.GeyserEntityType;
import org.geysermc.geyser.entity.VanillaEntityType;
import org.geysermc.geyser.entity.VanillaEntities;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.MetadataTypes;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.zip.ZipFile;

/**
 * Isolated adapter for the pinned Geyser 2.11.3 item-display metadata schema.
 *
 * <p>Runs on a backend's own Geyser (one pack) and on a proxy's Geyser (each session has the pack of its server).
 * The pack's {@link DisplayIndex} names the variant of every item; on the client the variant selects a sheet
 * geometry and the part inside it. Property ranges are fixed, so the entity registered when
 * Geyser starts serves every pack a session loads later.
 */
public final class GeyserDisplayBridge implements AutoCloseable {
    static final Identifier ID = Identifier.of("twilight:item_display");

    /** The variants of the pack a session loaded. */
    @FunctionalInterface
    public interface Variants {
        DisplayIndex of(org.geysermc.geyser.session.GeyserSession session);
    }

    /** Receives the items a session's pack has no display variant for (see {@link DisplayKeys}). */
    @FunctionalInterface
    public interface Sightings {
        void unshown(org.geysermc.geyser.session.GeyserSession session, List<String> keys);
    }

    private final Variants variants;
    private volatile Sightings sightings = (session, keys) -> { };
    /** The target pose; the client interpolates from the pose it shows (Java's own interpolation). */
    final GeyserFloatEntityProperty[] to = new GeyserFloatEntityProperty[DisplayPose.SIZE];
    /** The item's display transform for the current context. */
    final GeyserFloatEntityProperty[] context = new GeyserFloatEntityProperty[9];
    GeyserFloatEntityProperty seconds, delay;
    GeyserIntEntityProperty revision, appearance;
    /** Billboard, dropped-item mode and brightness override, see {@link TwilightItemDisplay#style}. */
    GeyserIntEntityProperty style;
    private final EventRegistrar registrar;
    private VanillaEntityType<TwilightItemDisplay> definition, blockDefinition;
    private VanillaEntityType<TwilightAreaEffectCloud> cloudDefinition;
    private VanillaEntityType<TwilightDroppedItem> droppedDefinition;

    /**
     * A backend's own Geyser: the variants of the deployed pack. With {@code learn} the display entity is
     * registered even before the pack has variants, so that displays sent only as packets are seen and reported.
     */
    public static GeyserDisplayBridge forPack(Object owner, Path deployedPack, Logger logger, boolean learn) throws IOException {
        DisplayIndex variants = DisplayIndex.read(deployedPack);
        return new GeyserDisplayBridge(owner, session -> variants, logger, learn || !variants.isEmpty());
    }

    /**
     * @param register whether to take over item displays at all; a proxy does whenever the feature is on, since its
     *                 servers' packs arrive after Geyser started
     */
    public GeyserDisplayBridge(Object owner, Variants variants, Logger logger, boolean register) {
        this.variants = variants;
        registrar = EventRegistrar.of(owner);
        if (!register) return;
        com.siberanka.twilight.geyser.GeyserEvents.subscribe(registrar, GeyserDefineEntitiesEvent.class, event -> {
            if (Registries.JAVA_ENTITY_TYPES.get(GeyserEntityType.ofVanilla(EntityType.ITEM_DISPLAY)) != null) {
                logger.severe("Item-display bridge cannot start: another translator already owns item_display.");
                return;
            }
            CustomEntityDefinition custom = CustomEntityDefinition.of(ID);
            event.register(custom);
            var builder = displayBase(EntityType.ITEM_DISPLAY, (BedrockEntityDefinition) custom);
            builder.addTranslator(MetadataTypes.ITEM_STACK, (entity, data) -> entity.item(data.getValue()));
            builder.addTranslator(MetadataTypes.BYTE, (entity, data) -> entity.context(data.getValue()));
            definition = builder.build();
            if (Registries.JAVA_ENTITY_TYPES.get(GeyserEntityType.ofVanilla(EntityType.BLOCK_DISPLAY)) == null) {
                var blocks = displayBase(EntityType.BLOCK_DISPLAY, (BedrockEntityDefinition) custom);
                blocks.addTranslator(MetadataTypes.BLOCK_STATE, (entity, data) -> entity.block(data.getValue()));
                blockDefinition = blocks.build();
            } else {
                logger.warning("Block displays keep another translator: something else already owns block_display.");
            }
            registerCloudAnchors(logger);
            registerDroppedItems(logger, (BedrockEntityDefinition) custom);
            logger.info("Registered the display bridge: item and block displays, furniture and model bones show with the pack's models.");
        });
        com.siberanka.twilight.geyser.GeyserEvents.subscribe(registrar, GeyserDefineEntityPropertiesEvent.class, event -> {
            if (definition == null) return;
            DisplayPose identity = DisplayPose.identity();
            for (int i = 0; i < DisplayPose.SIZE; i++) {
                to[i] = event.registerFloatProperty(ID, Identifier.of("twilight:b" + i), -1_000_000, 1_000_000, identity.value(i));
            }
            for (int i = 0; i < 9; i++) {
                context[i] = event.registerFloatProperty(ID, Identifier.of("twilight:c" + i), -1_000_000, 1_000_000, i >= 6 ? 1f : 0f);
            }
            seconds = event.registerFloatProperty(ID, Identifier.of("twilight:seconds"), 0, 3600, 0f);
            revision = event.registerIntegerProperty(ID, Identifier.of("twilight:revision"), 0, 1_000_000, 0);
            appearance = event.registerIntegerProperty(ID, Identifier.of("twilight:appearance"), -9, DisplayIndex.MAX_VARIANTS * 9 - 1, -9);
            delay = event.registerFloatProperty(ID, Identifier.of("twilight:delay"), -3600, 3600, 0f);
            style = event.registerIntegerProperty(ID, Identifier.of("twilight:style"), 0, 4095, 0);
        });
    }

    /**
     * The metadata shared by item and block displays (Display: indices 0..22). Java DisplayRenderer submits its mesh
     * even with the base entity's invisible flag set, and model providers use that flag on visible bones, so living
     * entity flags (invisibility, swimming, fire) are not forwarded to the mesh.
     */
    private VanillaEntityType.Builder<TwilightItemDisplay> displayBase(EntityType type, BedrockEntityDefinition custom) {
        var builder = VanillaEntityType.<TwilightItemDisplay>builder(spawn -> new TwilightItemDisplay(spawn, this))
                .type(type).bedrockDefinition(custom).heightAndWidth(0);
        builder.addTranslator(null);
        // Base entity metadata 1..7 does not affect the display mesh.
        for (int i = 1; i < 8; i++) builder.addTranslator(null);
        builder.addTranslator(MetadataTypes.INT, (entity, data) -> entity.delay(data.getValue()));
        builder.addTranslator(MetadataTypes.INT, (entity, data) -> entity.duration(data.getValue()));
        builder.addTranslator(null); // Position interpolation is handled by Geyser movement packets.
        builder.addTranslator(MetadataTypes.VECTOR3, (entity, data) -> entity.vector(0, data.getValue()));
        builder.addTranslator(MetadataTypes.VECTOR3, (entity, data) -> entity.vector(3, data.getValue()));
        builder.addTranslator(MetadataTypes.QUATERNION, (entity, data) -> entity.rotation(6, data.getValue()));
        builder.addTranslator(MetadataTypes.QUATERNION, (entity, data) -> entity.rotation(10, data.getValue()));
        builder.addTranslator(MetadataTypes.BYTE, (entity, data) -> entity.billboard(data.getValue()));
        builder.addTranslator(MetadataTypes.INT, (entity, data) -> entity.brightness(data.getValue()));
        builder.addTranslator(MetadataTypes.FLOAT, (entity, data) -> entity.viewRange(data.getValue()));
        // Shadow radius and strength, culling width and height, glow colour: no Bedrock counterpart on a mesh.
        for (int i = 18; i <= 22; i++) builder.addTranslator(null);
        return builder;
    }

    /** Java item entities: custom items with 3D models lie on the ground with their model, as on Java. */
    private void registerDroppedItems(Logger logger, BedrockEntityDefinition custom) {
        var original = VanillaEntities.ITEM;
        var type = GeyserEntityType.ofVanilla(EntityType.ITEM);
        if (Registries.JAVA_ENTITY_TYPES.get(type) != original
                || Registries.JAVA_ENTITY_IDENTIFIERS.get("minecraft:item") != original) {
            logger.warning("Dropped items keep another translator: something else already owns minecraft:item.");
            return;
        }
        var replacement = VanillaEntityType.<TwilightDroppedItem>inherited(spawn -> new TwilightDroppedItem(spawn, this), original)
                .type(EntityType.ITEM).bedrockDefinition(custom).build(false);
        if (!Registries.JAVA_ENTITY_TYPES.get().replace(type, original, replacement)) return;
        if (!Registries.JAVA_ENTITY_IDENTIFIERS.get().replace("minecraft:item", original, replacement)) {
            Registries.JAVA_ENTITY_TYPES.get().replace(type, replacement, original);
            return;
        }
        droppedDefinition = replacement;
    }

    private void registerCloudAnchors(Logger logger) {
        var original = VanillaEntities.AREA_EFFECT_CLOUD;
        var type = GeyserEntityType.ofVanilla(EntityType.AREA_EFFECT_CLOUD);
        // Adapt only Geyser's own translator; never replace another plugin's.
        if (Registries.JAVA_ENTITY_TYPES.get(type) != original
                || Registries.JAVA_ENTITY_IDENTIFIERS.get("minecraft:area_effect_cloud") != original) {
            logger.warning("Point-cloud adaptation skipped: another translator owns area_effect_cloud.");
            return;
        }
        var replacement = VanillaEntityType.<TwilightAreaEffectCloud>inherited(TwilightAreaEffectCloud::new, original)
                .type(EntityType.AREA_EFFECT_CLOUD).bedrockDefinition(original.defaultBedrockDefinition()).build(false);
        if (!Registries.JAVA_ENTITY_TYPES.get().replace(type, original, replacement)) return;
        if (!Registries.JAVA_ENTITY_IDENTIFIERS.get().replace("minecraft:area_effect_cloud", original, replacement)) {
            Registries.JAVA_ENTITY_TYPES.get().replace(type, replacement, original);
            return;
        }
        cloudDefinition = replacement;
        logger.info("Registered automatic invisible point-cloud adaptation.");
    }

    /**
     * Reports items shown in Java item displays that the pack cannot show yet, so that the server builds their
     * models: displays a plugin sends as packets only (model bones, client-side furniture, holograms) reach Twilight
     * only this way.
     */
    public void onUnshown(Sightings listener) {
        sightings = listener == null ? (session, keys) -> { } : listener;
    }

    void unshown(org.geysermc.geyser.session.GeyserSession session, List<String> keys) {
        if (keys.isEmpty()) return;
        try {
            sightings.unshown(session, keys);
        } catch (RuntimeException ignored) {
            // A report is an optimisation; showing the display never depends on it.
        }
    }

    /** Whether Geyser registered the display entity with this bridge (it fires its definition event once, at start). */
    public boolean registered() {
        return definition != null;
    }

    DisplayIndex variants(org.geysermc.geyser.session.GeyserSession session) {
        try {
            DisplayIndex found = variants.of(session);
            return found == null ? DisplayIndex.EMPTY : found;
        } catch (RuntimeException failure) {
            return DisplayIndex.EMPTY;
        }
    }

    @Override public void close() {
        GeyserApi.api().eventBus().unregisterAll(registrar);
        if (cloudDefinition != null) {
            Registries.JAVA_ENTITY_TYPES.get().replace(cloudDefinition.type(), cloudDefinition, VanillaEntities.AREA_EFFECT_CLOUD);
            Registries.JAVA_ENTITY_IDENTIFIERS.get().replace("minecraft:area_effect_cloud", cloudDefinition, VanillaEntities.AREA_EFFECT_CLOUD);
        }
        if (definition != null) {
            Registries.JAVA_ENTITY_TYPES.get().remove(definition.type(), definition);
            Registries.JAVA_ENTITY_IDENTIFIERS.get().remove("minecraft:item_display", definition);
        }
        if (blockDefinition != null) {
            Registries.JAVA_ENTITY_TYPES.get().remove(blockDefinition.type(), blockDefinition);
            Registries.JAVA_ENTITY_IDENTIFIERS.get().remove("minecraft:block_display", blockDefinition);
        }
        if (droppedDefinition != null) {
            Registries.JAVA_ENTITY_TYPES.get().replace(droppedDefinition.type(), droppedDefinition, VanillaEntities.ITEM);
            Registries.JAVA_ENTITY_IDENTIFIERS.get().replace("minecraft:item", droppedDefinition, VanillaEntities.ITEM);
        }
    }
}
