package com.siberanka.twilight.compiler;

import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.connection.GeyserConnection;
import org.geysermc.geyser.api.entity.data.GeyserEntityDataTypes;
import org.geysermc.geyser.api.entity.type.GeyserEntity;

import java.util.UUID;

/**
 * Public Geyser API boundary for vanilla Elytra flight and worn-wing variants.
 * Never reference this class until Geyser-Spigot is available: Geyser is an optional plugin.
 * No Geyser implementation classes and no direct packet or event-loop access are used.
 */
final class ElytraGeyserApiAdapter {
    private ElytraGeyserApiAdapter() {
    }

    static boolean hasConnection(UUID playerUuid) {
        return GeyserApi.api().connectionByUuid(playerUuid) != null;
    }

    static void sendVariant(UUID playerUuid, int variant) {
        sendVariantToObservers(GeyserApi.api(), playerUuid, variant);
    }

    /**
     * Entity.override handles event-loop scheduling in Geyser's implementation.
     * Publish to every observer as well as the local player's Bedrock entity.
     */
    static void sendVariantToObservers(GeyserApi geyser, UUID playerUuid, int variant) {
        for (GeyserConnection connection : geyser.onlineConnections()) {
            GeyserEntity entity = connection.entities().byUuid(playerUuid);
            if (entity == null && playerUuid.equals(connection.javaUuid())) {
                entity = connection.playerEntity();
            }
            if (entity != null) {
                entity.override(GeyserEntityDataTypes.VARIANT, variant);
            }
        }
    }
}
