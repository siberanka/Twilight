/* Copyright (c) 2026 siberanka. Twilight License (PolyForm Noncommercial 1.0.0 + terms), see LICENSE. */
package com.siberanka.twilight.integration.display;

import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentType;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponents;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the {@code item_model} component of a Java item stack. Its value is an Adventure key, and Adventure is
 * relocated or provided differently on each platform, so the key is read without linking its class.
 */
final class DisplayItems {
    private static final DataComponentType<?> ITEM_MODEL = itemModelType();
    /** The key class is the same for every stack; its method is looked up once per class. */
    private static final ClassValue<Method> AS_STRING = new ClassValue<>() {
        @Override protected Method computeValue(Class<?> type) {
            try {
                return type.getMethod("asString");
            } catch (NoSuchMethodException | RuntimeException absent) {
                return null;
            }
        }
    };

    private DisplayItems() {}

    /** The stack's own {@code item_model} ("namespace:path"), or null when it has none. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static String itemModel(ItemStack stack) {
        if (ITEM_MODEL == null || stack == null) return null;
        DataComponents components = stack.getDataComponentsPatch();
        if (components == null) return null;
        Object key = components.get((DataComponentType) ITEM_MODEL);
        if (key == null) return null;
        Method method = AS_STRING.get(key.getClass());
        if (method != null) {
            try {
                return String.valueOf(method.invoke(key));
            } catch (ReflectiveOperationException | RuntimeException inaccessible) {
                // Adventure's keys print the same text.
            }
        }
        return key.toString();
    }

    /** The {@link DisplayKeys} of a stack: its item model and its custom model data on the base item. */
    static List<String> keys(ItemStack stack) {
        List<String> keys = new ArrayList<>(3);
        if (stack == null || stack.getAmount() <= 0) return keys;
        String model = itemModel(stack);
        if (model != null) keys.add(DisplayKeys.model(model));
        DataComponents components = stack.getDataComponentsPatch();
        if (components != null) {
            var data = components.get(DataComponentTypes.CUSTOM_MODEL_DATA);
            String base = baseItem(stack.getId());
            if (data != null && base != null) {
                if (!data.floats().isEmpty()) keys.add(DisplayKeys.customModelData(base, data.floats().getFirst()));
                if (!data.strings().isEmpty()) keys.add(DisplayKeys.customModelString(base, data.strings().getFirst()));
            }
        }
        keys.removeIf(key -> !DisplayKeys.valid(key));
        return keys;
    }

    /**
     * The skin hash of a player head stack (its profile's textures, as Geyser resolves them), or null for other items
     * and heads whose profile is not resolved yet.
     */
    static String skin(ItemStack stack) {
        if (stack == null || !"minecraft:player_head".equals(baseItem(stack.getId()))) return null;
        DataComponents components = stack.getDataComponentsPatch();
        if (components == null) return null;
        try {
            var profile = components.get(DataComponentTypes.PROFILE);
            if (profile == null) return null;
            var resolved = org.geysermc.geyser.skin.SkinManager.resolveProfile(profile).getNow(null);
            if (resolved == null) return null;
            var texture = org.geysermc.geyser.skin.SkinManager.getTextureDataFromProfile(resolved,
                    org.geysermc.mcprotocollib.auth.texture.TextureType.SKIN);
            String hash = texture == null ? null : texture.getHash();
            return com.siberanka.twilight.geyser.HeadTextures.valid(hash) ? hash : null;
        } catch (RuntimeException | LinkageError unresolved) {
            return null;
        }
    }

    static String baseItem(int javaId) {
        try {
            var item = org.geysermc.geyser.registry.Registries.JAVA_ITEMS.get(javaId);
            return item == null ? null : item.javaIdentifier();
        } catch (RuntimeException | LinkageError unknown) {
            return null;
        }
    }

    private static DataComponentType<?> itemModelType() {
        try {
            return (DataComponentType<?>) DataComponentTypes.class.getField("ITEM_MODEL").get(null);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError absent) {
            return null;
        }
    }
}
