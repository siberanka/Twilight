/* Copyright (c) 2026 siberanka. Twilight License (PolyForm Noncommercial 1.0.0 + terms), see LICENSE. */
package com.siberanka.twilight.integration.text;

import com.siberanka.twilight.text.LayeredTextLayout;
import com.siberanka.twilight.text.TextLayout;
import com.siberanka.twilight.text.TextLayoutTable;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.EntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.MetadataType;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.ObjectEntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEntityDataPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundOpenScreenPacket;

import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Packet-level text rewrites for {@link GeyserTextLayoutBridge}, independent of Geyser's
 * runtime so they can be exercised with real protocol packets. Packet fields are read and
 * replaced through their Lombok getters and withers by name, because Geyser relocates
 * Adventure on some platforms.
 */
final class TextSurfaces {
    /** Container types Geyser shows with Bedrock's chest screens, whose label the pack moves. */
    static final Set<ContainerType> CHEST_SCREENS = EnumSet.of(ContainerType.GENERIC_9X1, ContainerType.GENERIC_9X2,
            ContainerType.GENERIC_9X3, ContainerType.GENERIC_9X4, ContainerType.GENERIC_9X5,
            ContainerType.GENERIC_9X6, ContainerType.SHULKER_BOX);

    private static final Map<Class<?>, Map<String, Accessor>> ACCESSORS = new ConcurrentHashMap<>();

    private record Accessor(Method getter, Method wither) {}

    /**
     * @param translations pack strings for argument-free translation keys (null: none)
     * @param title        container titles: Java darkens their uncoloured images
     * @param layers       turns the layers of a line that moves back over itself into the component to send,
     *                     or null when the surface's UI cannot show layers (see {@link LayeredTextLayout})
     */
    record Options(Function<String, String> translations, boolean title, Function<List<Object>, Object> layers,
                   int linesPerUnit, boolean shadows) {
        static final Options NONE = new Options(null, false, null, 1, false);

        Options(Function<String, String> translations, boolean title, Function<List<Object>, Object> layers) {
            this(translations, title, layers, 1, false);
        }
    }

    private TextSurfaces() {}

    static ClientboundOpenScreenPacket openScreen(ClientboundOpenScreenPacket packet, TextLayoutTable table,
                                                  boolean pocket) throws ReflectiveOperationException {
        return openScreen(packet, table, pocket, null);
    }

    static ClientboundOpenScreenPacket openScreen(ClientboundOpenScreenPacket packet, TextLayoutTable table,
                                                  boolean pocket, Function<String, String> translations)
            throws ReflectiveOperationException {
        return openScreen(packet, table, pocket, translations, null);
    }

    /** @param layers see {@link Options#layers()}; chest screens only (the generated chest UI shows them) */
    static ClientboundOpenScreenPacket openScreen(ClientboundOpenScreenPacket packet, TextLayoutTable table,
                                                  boolean pocket, Function<String, String> translations,
                                                  Function<List<Object>, Object> layers)
            throws ReflectiveOperationException {
        if (pocket) return substitute(packet, table, "Title", true);
        if (!CHEST_SCREENS.contains(packet.getType())) {
            // Bedrock draws these screens with its own layout; relative positions still follow Java.
            return rewrite(packet, table, translations, true, TextLayout.Mode.LEFT, "Title");
        }
        try {
            return rewrite(packet, table, new Options(translations, true, layers), TextLayout.Mode.CONTAINER, "Title");
        } catch (ReflectiveOperationException | RuntimeException layoutFailure) {
            return originOnly(packet, table, "Title");
        }
    }

    /** Entity custom names and text-display text, both shown by Bedrock as centred name tags. */
    static ClientboundSetEntityDataPacket entityData(ClientboundSetEntityDataPacket packet, TextLayoutTable table)
            throws ReflectiveOperationException {
        return entityData(packet, table, null);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static ClientboundSetEntityDataPacket entityData(ClientboundSetEntityDataPacket packet, TextLayoutTable table,
                                                     Function<String, String> translations)
            throws ReflectiveOperationException {
        EntityMetadata<?, ?>[] metadata = packet.getMetadata();
        EntityMetadata<?, ?>[] replaced = null;
        for (int index = 0; index < metadata.length; index++) {
            if (!(metadata[index] instanceof ObjectEntityMetadata<?> entry)) continue;
            Object value = entry.getValue();
            if (value instanceof String) continue; // only component values are display text
            Object laidOut = layoutValue(value, table, TextLayout.Mode.CENTERED, translations);
            if (laidOut == value) continue;
            if (replaced == null) replaced = metadata.clone();
            replaced[index] = new ObjectEntityMetadata(entry.getId(), (MetadataType) entry.getType(), laidOut);
        }
        return replaced == null ? packet : packet.withMetadata(replaced);
    }

    static <P> P rewrite(P packet, TextLayoutTable table, TextLayout.Mode mode, String... properties)
            throws ReflectiveOperationException {
        return rewrite(packet, table, Options.NONE, mode, properties);
    }

    /** @param translations pack strings for argument-free translation keys, laid out like text (null: none) */
    static <P> P rewrite(P packet, TextLayoutTable table, Function<String, String> translations,
                         TextLayout.Mode mode, String... properties) throws ReflectiveOperationException {
        return rewrite(packet, table, translations, false, mode, properties);
    }

    /** @param title container titles: Java darkens their uncoloured images */
    static <P> P rewrite(P packet, TextLayoutTable table, Function<String, String> translations, boolean title,
                         TextLayout.Mode mode, String... properties) throws ReflectiveOperationException {
        return rewrite(packet, table, new Options(translations, title, null), mode, properties);
    }

    @SuppressWarnings("unchecked")
    static <P> P rewrite(P packet, TextLayoutTable table, Options options, TextLayout.Mode mode, String... properties)
            throws ReflectiveOperationException {
        P current = packet;
        for (String property : properties) {
            Accessor accessor = accessor(packet.getClass(), property);
            Object value = accessor.getter.invoke(current);
            Object laidOut = layoutValue(value, table, mode, options);
            if (laidOut != value) current = (P) accessor.wither.invoke(current, laidOut);
        }
        return current;
    }

    static <P> P substitute(P packet, TextLayoutTable table, String property) throws ReflectiveOperationException {
        return substitute(packet, table, property, false);
    }

    @SuppressWarnings("unchecked")
    static <P> P substitute(P packet, TextLayoutTable table, String property, boolean title)
            throws ReflectiveOperationException {
        Accessor accessor = accessor(packet.getClass(), property);
        Object value = accessor.getter.invoke(packet);
        if (value == null) return packet;
        Object substituted = AdventureTextLayout.substitute(table, value, title);
        return substituted == value ? packet : (P) accessor.wither.invoke(packet, substituted);
    }

    /** Puts plain (zero-width) text in front of a component property, if the packet carries it. */
    @SuppressWarnings("unchecked")
    static <P> P prepend(P packet, String property, String text) throws ReflectiveOperationException {
        Accessor accessor = accessor(packet.getClass(), property);
        Object value = accessor.getter.invoke(packet);
        if (!AdventureTextLayout.isComponent(value)) return packet;
        return (P) accessor.wither.invoke(packet, AdventureTextLayout.prepend(value, text));
    }

    /** One property of the packet by name (Geyser may relocate the property's type). */
    static Object get(Object packet, String property) throws ReflectiveOperationException {
        return accessor(packet.getClass(), property).getter.invoke(packet);
    }

    /** The packet with one property replaced by name (Geyser may relocate the property's type). */
    @SuppressWarnings("unchecked")
    static <P> P with(P packet, String property, Object value) throws ReflectiveOperationException {
        return (P) accessor(packet.getClass(), property).wither.invoke(packet, value);
    }

    @SuppressWarnings("unchecked")
    static <P> P originOnly(P packet, TextLayoutTable table, String property) throws ReflectiveOperationException {
        Accessor accessor = accessor(packet.getClass(), property);
        Object value = accessor.getter.invoke(packet);
        return value == null ? packet : (P) accessor.wither.invoke(packet, AdventureTextLayout.originOnly(table, value));
    }

    /** Lays out a component, a plain string or an optional component; returns the same instance if unchanged. */
    static Object layoutValue(Object value, TextLayoutTable table, TextLayout.Mode mode)
            throws ReflectiveOperationException {
        return layoutValue(value, table, mode, Options.NONE);
    }

    static Object layoutValue(Object value, TextLayoutTable table, TextLayout.Mode mode,
                              Function<String, String> translations) throws ReflectiveOperationException {
        return layoutValue(value, table, mode, translations, false);
    }

    static Object layoutValue(Object value, TextLayoutTable table, TextLayout.Mode mode,
                              Function<String, String> translations, boolean title) throws ReflectiveOperationException {
        return layoutValue(value, table, mode, new Options(translations, title, null));
    }

    static Object layoutValue(Object value, TextLayoutTable table, TextLayout.Mode mode, Options options)
            throws ReflectiveOperationException {
        if (value == null) return null;
        if (value instanceof String text) return AdventureTextLayout.layout(table, text, mode);
        if (value instanceof Optional<?> optional) {
            if (optional.isEmpty()) return value;
            Object inner = optional.get();
            if (!AdventureTextLayout.isComponent(inner)) return value;
            Object laidOut = layoutComponent(inner, table, mode, options);
            return laidOut == inner ? value : Optional.of(laidOut);
        }
        return AdventureTextLayout.isComponent(value) ? layoutComponent(value, table, mode, options) : value;
    }

    private static Object layoutComponent(Object component, TextLayoutTable table, TextLayout.Mode mode, Options options)
            throws ReflectiveOperationException {
        if (options.layers() != null && mode != TextLayout.Mode.LEFT) {
            List<Object> layers = AdventureTextLayout.layered(table, component, mode, options.translations(), options.title(),
                    options.linesPerUnit(), options.shadows());
            Object encoded = layers == null ? null : options.layers().apply(layers);
            if (encoded != null) return encoded;
        }
        return AdventureTextLayout.layout(table, component, mode, options.translations(), options.title());
    }

    private static Accessor accessor(Class<?> type, String property) throws NoSuchMethodException {
        Map<String, Accessor> byName = ACCESSORS.computeIfAbsent(type, ignored -> new ConcurrentHashMap<>());
        Accessor accessor = byName.get(property);
        if (accessor != null) return accessor;
        Method getter = type.getMethod("get" + property);
        Method wither = type.getMethod("with" + property, getter.getReturnType());
        accessor = new Accessor(getter, wither);
        byName.put(property, accessor);
        return accessor;
    }
}
