package com.siberanka.twilight.integration.proxy;

import com.siberanka.twilight.protocol.PackChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ProxyVersionTest {
    @TempDir Path folder;

    @Test
    void theProxysVersionRidesOnTheCapabilityFlagsWithoutChangingTheFeatureBit() {
        var features = ProxyFeatures.load(folder.resolve("features.properties"));
        long now = System.currentTimeMillis();
        assertEquals(0, features.proxyVersionCode(now));
        var update = features.accept(new PackChannel.Capabilities(now, PackChannel.withVersion(PackChannel.ITEM_DISPLAYS, 10002)), now);
        assertTrue(update.itemDisplaysChanged());
        assertEquals(10002, features.proxyVersionCode(now));
        assertTrue(features.itemDisplays(now));
        // An upgrade of the proxy only changes the version: no rebuild of the pack.
        var upgraded = features.accept(new PackChannel.Capabilities(now, PackChannel.withVersion(PackChannel.ITEM_DISPLAYS, 10100)), now + 1);
        assertFalse(upgraded.itemDisplaysChanged());
        assertTrue(upgraded.save());
        assertEquals(0, features.proxyVersionCode(now + 40L * 24 * 3600 * 1000), "a stale statement no longer counts");
    }
}
