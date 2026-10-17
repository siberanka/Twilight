package com.siberanka.twilight.source;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * @param equipment where the item is worn and the equipment asset Java draws on the body there ({@code equippable}
 *                  component, 1.21.2+), when the item has one
 */
public record CustomItemDescriptor(
        String provider,
        String baseItem,
        Optional<String> itemModel,
        OptionalInt customModelData,
        String displayName,
        Optional<Equipment> equipment
) {
    public CustomItemDescriptor {
        equipment = equipment == null ? Optional.empty() : equipment;
    }

    public CustomItemDescriptor(String provider, String baseItem, Optional<String> itemModel,
                                OptionalInt customModelData, String displayName) {
        this(provider, baseItem, itemModel, customModelData, displayName, Optional.empty());
    }

    /**
     * @param slot    head, chest, legs, feet or body
     * @param assetId the equipment asset ({@code assets/<ns>/equipment/<path>.json}); empty draws nothing on the
     *                body (Java then shows the item's own model on the head for the head slot)
     */
    public record Equipment(String slot, Optional<String> assetId) {
        public static final Set<String> SLOTS = Set.of("head", "chest", "legs", "feet", "body");

        public Equipment {
            if (slot == null || !SLOTS.contains(slot)) throw new IllegalArgumentException("unknown equipment slot " + slot);
            assetId = assetId == null ? Optional.empty() : assetId;
        }
    }
}
