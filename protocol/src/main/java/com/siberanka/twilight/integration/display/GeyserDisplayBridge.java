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
    private final EntityTypeRegistry entities = EntityTypeRegistry.detect();
    private VanillaEntityType<TwilightItemDisplay> definition, blockDefinition;
    private VanillaEntityType<TwilightAreaEffectCloud> cloudDefinition;
    private VanillaEntityType<TwilightDroppedItem> droppedDefinition;
    private VanillaEntityType<TwilightItemFrame> frameDefinition, glowFrameDefinition;

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
    private volatile boolean modelBones = true;

    /**
     * Whether ModelEngine and BetterModel bones are shown (default). Off when another Geyser extension renders those
     * models (GeyserModelEngine): the bones are then left out so each model shows once.
     */
    public GeyserDisplayBridge modelBones(boolean shown) {
        modelBones = shown;
        return this;
    }

    boolean modelBones() { return modelBones; }

    public GeyserDisplayBridge(Object owner, Variants variants, Logger logger, boolean register) {
        this.variants = variants;
        registrar = EventRegistrar.of(owner);
        if (!register) return;
        com.siberanka.twilight.geyser.GeyserEvents.subscribe(registrar, GeyserDefineEntitiesEvent.class, event -> {
            if (!entities.available()) {
                logger.warning("This Geyser build keeps its entity types where Twilight cannot reach them; item displays are "
                        + "registered without an ownership check and furniture frames, dropped 3D items and clouds stay Geyser's.");
            }
            if (registered(EntityType.ITEM_DISPLAY) != null) {
                logger.severe("Item-display bridge cannot start: another translator already owns item_display.");
                return;
            }
            CustomEntityDefinition custom = CustomEntityDefinition.of(ID);
            event.register(custom);
            var builder = displayBase(EntityType.ITEM_DISPLAY, (BedrockEntityDefinition) custom);
            builder.addTranslator(MetadataTypes.ITEM_STACK, (entity, data) -> entity.item(data.getValue()));
            builder.addTranslator(MetadataTypes.BYTE, (entity, data) -> entity.context(data.getValue()));
            definition = builder.build();
            if (registered(EntityType.BLOCK_DISPLAY) == null) {
                var blocks = displayBase(EntityType.BLOCK_DISPLAY, (BedrockEntityDefinition) custom);
                blocks.addTranslator(MetadataTypes.BLOCK_STATE, (entity, data) -> entity.block(data.getValue()));
                blockDefinition = blocks.build();
            } else {
                logger.warning("Block displays keep another translator: something else already owns block_display.");
            }
            List<String> degraded = new ArrayList<>();
            guarded(degraded, "point-cloud adaptation", () -> registerCloudAnchors(logger));
            guarded(degraded, "3D dropped items", () -> registerDroppedItems(logger, (BedrockEntityDefinition) custom));
            guarded(degraded, "furniture item frames", () -> registerItemFrames(logger, (BedrockEntityDefinition) custom));
            if (!degraded.isEmpty()) {
                logger.warning("Geyser's entity registry (" + entities.describe() + ") could not be changed for: "
                        + String.join(", ", degraded) + "; these stay Geyser's own translators.");
            }
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
        if (!entities.available()) throw new IllegalStateException("no entity registry");
        if (registered(EntityType.ITEM) != original
                || GeyserRegistries.entity("minecraft:item", original) != original) {
            logger.warning("Dropped items keep another translator: something else already owns minecraft:item.");
            return;
        }
        var replacement = VanillaEntityType.<TwilightDroppedItem>inherited(spawn -> new TwilightDroppedItem(spawn, this), original)
                .type(EntityType.ITEM).bedrockDefinition(custom).build(false);
        if (!swap(EntityType.ITEM, original, replacement)) return;
        if (!GeyserRegistries.replaceEntity("minecraft:item", original, replacement)) {
            swap(EntityType.ITEM, replacement, original);
            return;
        }
        droppedDefinition = replacement;
    }

    /** Invisible item frames (furniture): their item shows with its display model, as on Java. */
    private void registerItemFrames(Logger logger, BedrockEntityDefinition custom) {
        frameDefinition = replaceFrame(logger, custom, EntityType.ITEM_FRAME, "minecraft:item_frame", VanillaEntities.ITEM_FRAME);
        glowFrameDefinition = replaceFrame(logger, custom, EntityType.GLOW_ITEM_FRAME, "minecraft:glow_item_frame",
                VanillaEntities.GLOW_ITEM_FRAME);
    }

    private VanillaEntityType<TwilightItemFrame> replaceFrame(Logger logger, BedrockEntityDefinition custom, EntityType javaType,
                                                              String identifier, VanillaEntityType<?> original) {
        if (!entities.available()) throw new IllegalStateException("no entity registry");
        if (registered(javaType) != original || GeyserRegistries.entity(identifier, original) != original) {
            logger.warning("Item frames keep another translator: something else already owns " + identifier + ".");
            return null;
        }
        @SuppressWarnings("unchecked") var frames = (VanillaEntityType<org.geysermc.geyser.entity.type.ItemFrameEntity>) original;
        var replacement = VanillaEntityType.<TwilightItemFrame>inherited(spawn -> new TwilightItemFrame(spawn, this), frames)
                .type(javaType).bedrockDefinition(custom).build(false);
        if (!swap(javaType, original, replacement)) return null;
        if (!GeyserRegistries.replaceEntity(identifier, original, replacement)) {
            swap(javaType, replacement, original);
            return null;
        }
        return replacement;
    }

    private void registerCloudAnchors(Logger logger) {
        var original = VanillaEntities.AREA_EFFECT_CLOUD;
        if (!entities.available()) throw new IllegalStateException("no entity registry");
        // Adapt only Geyser's own translator; never replace another plugin's.
        if (registered(EntityType.AREA_EFFECT_CLOUD) != original
                || GeyserRegistries.entity("minecraft:area_effect_cloud", original) != original) {
            logger.warning("Point-cloud adaptation skipped: another translator owns area_effect_cloud.");
            return;
        }
        var replacement = VanillaEntityType.<TwilightAreaEffectCloud>inherited(TwilightAreaEffectCloud::new, original)
                .type(EntityType.AREA_EFFECT_CLOUD).bedrockDefinition(original.defaultBedrockDefinition()).build(false);
        if (!swap(EntityType.AREA_EFFECT_CLOUD, original, replacement)) return;
        if (!GeyserRegistries.replaceEntity("minecraft:area_effect_cloud", original, replacement)) {
            swap(EntityType.AREA_EFFECT_CLOUD, replacement, original);
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

    /** Geyser's definition currently registered for a Java entity type, or null. */
    private Object registered(EntityType type) {
        return entities.current(GeyserEntityType.ofVanilla(type), type.ordinal());
    }

    private boolean swap(EntityType type, Object expected, Object replacement) {
        return entities.replace(GeyserEntityType.ofVanilla(type), type.ordinal(), expected, replacement);
    }

    private void unregister(EntityType type, Object definition) {
        entities.remove(GeyserEntityType.ofVanilla(type), type.ordinal(), definition);
    }

    /** Runs one registry-changing feature; a failure leaves that feature to Geyser and is reported once with the others. */
    private static void guarded(List<String> degraded, String feature, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException | LinkageError failure) {
            degraded.add(feature);
        }
    }

    @Override public void close() {
        GeyserApi.api().eventBus().unregisterAll(registrar);
        if (cloudDefinition != null) {
            swap(EntityType.AREA_EFFECT_CLOUD, cloudDefinition, VanillaEntities.AREA_EFFECT_CLOUD);
            GeyserRegistries.replaceEntity("minecraft:area_effect_cloud", cloudDefinition, VanillaEntities.AREA_EFFECT_CLOUD);
        }
        if (definition != null) {
            unregister(EntityType.ITEM_DISPLAY, definition);
            GeyserRegistries.removeEntity("minecraft:item_display", definition);
        }
        if (blockDefinition != null) {
            unregister(EntityType.BLOCK_DISPLAY, blockDefinition);
            GeyserRegistries.removeEntity("minecraft:block_display", blockDefinition);
        }
        if (frameDefinition != null) {
            swap(EntityType.ITEM_FRAME, frameDefinition, VanillaEntities.ITEM_FRAME);
            GeyserRegistries.replaceEntity("minecraft:item_frame", frameDefinition, VanillaEntities.ITEM_FRAME);
        }
        if (glowFrameDefinition != null) {
            swap(EntityType.GLOW_ITEM_FRAME, glowFrameDefinition, VanillaEntities.GLOW_ITEM_FRAME);
            GeyserRegistries.replaceEntity("minecraft:glow_item_frame", glowFrameDefinition, VanillaEntities.GLOW_ITEM_FRAME);
        }
        if (droppedDefinition != null) {
            swap(EntityType.ITEM, droppedDefinition, VanillaEntities.ITEM);
            GeyserRegistries.replaceEntity("minecraft:item", droppedDefinition, VanillaEntities.ITEM);
        }
    }
}
