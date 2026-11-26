/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.geyser;

/**
 * Which chest item other Bedrock viewers get for a custom elytra. Geyser sends every glider in the chest slot as
 * the vanilla elytra (the client only glides with it), so a custom elytra's worn attachable, bound to its own item,
 * never applies. For entities other than the viewer's own player nothing needs the vanilla item (they glide by
 * entity flag), so the custom item is sent instead and its worn attachable draws the wings.
 */
public final class WornElytra {
    /** Identifier prefix of the Bedrock items Twilight registers. */
    public static final String CUSTOM_PREFIX = "twilight:";

    private WornElytra() {}

    /**
     * Whether the chest of an entity shows as the custom item.
     *
     * @param ownPlayer the entity is the viewer's own player, which must keep the vanilla elytra to glide
     * @param glider the Java stack is a glider worn in the chest slot (the case Geyser rewrites)
     * @param bedrockIdentifier the identifier Geyser maps the Java stack to, or null when it is unmapped
     */
    public static boolean showsCustomItem(boolean ownPlayer, boolean glider, String bedrockIdentifier) {
        return !ownPlayer && glider && bedrockIdentifier != null && bedrockIdentifier.startsWith(CUSTOM_PREFIX);
    }
}
