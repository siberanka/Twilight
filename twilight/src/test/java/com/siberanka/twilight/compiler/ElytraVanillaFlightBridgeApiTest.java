package com.siberanka.twilight.compiler;

import org.geysermc.api.Geyser;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.connection.GeyserConnection;
import org.geysermc.geyser.api.entity.EntityData;
import org.geysermc.geyser.api.entity.data.GeyserEntityDataTypes;
import org.geysermc.geyser.api.entity.type.GeyserEntity;
import org.geysermc.geyser.api.entity.type.player.GeyserPlayerEntity;
import org.geysermc.geyser.api.util.Identifier;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Public Geyser API regression: remote viewers and the local Bedrock player both receive VARIANT. */
class ElytraVanillaFlightBridgeApiTest {
    @Test
    void variantLookupKeepsUnknownModelsAtZero() {
        var variants = Map.of("elytras:rainbow", 4);
        assertEquals(4, ElytraVanillaFlightBridge.variantForModel("elytras:rainbow", variants));
        assertEquals(0, ElytraVanillaFlightBridge.variantForModel("elytras:unknown", variants));
        assertEquals(0, ElytraVanillaFlightBridge.variantForModel(null, variants));
    }

    @Test
    void publishesVariantToRemoteViewerAndLocalPlayer() {
        // The API data-type registry needs a provider even in an isolated unit test.
        // The regular test suite does not register a running Geyser instance.
        assumeFalse(Geyser.isRegistered());
        UUID playerUuid = UUID.randomUUID();
        AtomicInteger remoteCalls = new AtomicInteger();
        AtomicInteger localCalls = new AtomicInteger();

        GeyserEntity observedPlayer = proxy(GeyserEntity.class, (self, method, args) -> {
            if (method.getName().equals("override") && args != null && args.length == 2) {
                assertSame(GeyserEntityDataTypes.VARIANT, args[0]);
                assertEquals(4, args[1]);
                remoteCalls.incrementAndGet();
            }
            return null;
        });
        GeyserPlayerEntity ownPlayer = proxy(GeyserPlayerEntity.class, (self, method, args) -> {
            if (method.getName().equals("override") && args != null && args.length == 2) {
                assertSame(GeyserEntityDataTypes.VARIANT, args[0]);
                assertEquals(4, args[1]);
                localCalls.incrementAndGet();
            }
            return null;
        });
        GeyserConnection observer = proxy(GeyserConnection.class, (self, method, args) -> switch (method.getName()) {
            case "entities" -> proxy(EntityData.class, (entity, lookup, values) ->
                    lookup.getName().equals("byUuid") ? observedPlayer : null);
            case "javaUuid" -> UUID.randomUUID();
            default -> null;
        });
        GeyserConnection localConnection = proxy(GeyserConnection.class, (self, method, args) -> switch (method.getName()) {
            case "entities" -> proxy(EntityData.class, (entity, lookup, values) -> null);
            case "javaUuid" -> playerUuid;
            case "playerEntity" -> ownPlayer;
            default -> null;
        });
        GeyserApi api = proxy(GeyserApi.class, (self, method, args) -> {
            if (method.getName().equals("onlineConnections")) {
                return List.of(observer, localConnection);
            }
            if (method.getName().equals("provider") && args[0] == Identifier.class) {
                Object[] values = (Object[]) args[1];
                String namespace = values.length == 2 ? (String) values[0] : "minecraft";
                String path = values.length == 2 ? (String) values[1] : (String) values[0];
                return proxy(Identifier.class, (id, property, ignored) -> switch (property.getName()) {
                    case "namespace" -> namespace;
                    case "path" -> path;
                    default -> null;
                });
            }
            return null;
        });
        Geyser.set(api);
        ElytraGeyserApiAdapter.sendVariantToObservers(api, playerUuid, 4);
        assertEquals(1, remoteCalls.get());
        assertEquals(1, localCalls.get());
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }
}
