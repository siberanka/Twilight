package com.siberanka.twilight.integration.proxy;

import com.siberanka.twilight.protocol.PackChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ProxyFeaturesTest {
    @TempDir Path root;

    @Test void remembersWhatTheProxyShowsAcrossRestarts() throws Exception {
        Path file = root.resolve("proxy-features.properties");
        ProxyFeatures features = ProxyFeatures.load(file);
        long now = 1_000_000_000_000L;
        assertFalse(features.itemDisplays(now), "nothing said yet");
        ProxyFeatures.Update update = features.accept(new PackChannel.Capabilities(now, PackChannel.ITEM_DISPLAYS), now);
        assertTrue(update.itemDisplaysChanged());
        assertTrue(update.save());
        features.save();
        assertTrue(ProxyFeatures.load(file).itemDisplays(now + 1000));

        ProxyFeatures.Update repeated = features.accept(new PackChannel.Capabilities(now, PackChannel.ITEM_DISPLAYS), now + 1000);
        assertFalse(repeated.itemDisplaysChanged());
        assertFalse(repeated.save(), "an unchanged statement is not written on every join");

        ProxyFeatures.Update off = features.accept(new PackChannel.Capabilities(now, 0), now + 2000);
        assertTrue(off.itemDisplaysChanged());
        assertFalse(features.itemDisplays(now + 2000));
    }

    @Test void aStatementNobodyRepeatsExpiresAndBrokenFilesMeanNothing() throws Exception {
        Path file = root.resolve("proxy-features.properties");
        ProxyFeatures features = ProxyFeatures.load(file);
        long now = 1_000_000_000_000L;
        features.accept(new PackChannel.Capabilities(now, PackChannel.ITEM_DISPLAYS), now);
        assertTrue(features.itemDisplays(now + ProxyFeatures.MAX_AGE_MILLIS));
        assertFalse(features.itemDisplays(now + ProxyFeatures.MAX_AGE_MILLIS + 1));
        assertFalse(features.itemDisplays(now - 1), "a clock set back does not extend it");
        Files.writeString(file, "flags=x\nconfirmed=?");
        assertFalse(ProxyFeatures.load(file).itemDisplays(now));
    }
}
