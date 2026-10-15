/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.host;

import org.geysermc.geyser.api.pack.PackCodec;
import org.geysermc.geyser.api.pack.ResourcePack;
import org.geysermc.geyser.api.pack.ResourcePackManifest;
import org.geysermc.geyser.api.pack.UrlPackCodec;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.channels.SeekableByteChannel;

/**
 * A pack Geyser announces with a download link (Bedrock's CDN entry) and still sends itself when the client
 * asks for chunks instead. Hash, size and bytes come from the pack Geyser already loaded, so nothing is read
 * or hashed again per session. Only classes that touch Geyser load this one.
 */
public final class HostedPackCodec extends UrlPackCodec {
    private final String url;
    private final ResourcePack pack;
    private final Runnable fallback;
    private final java.util.concurrent.atomic.AtomicBoolean reported = new java.util.concurrent.atomic.AtomicBoolean();

    private HostedPackCodec(String url, ResourcePack pack, Runnable fallback) {
        this.url = url;
        this.pack = pack;
        this.fallback = fallback;
    }

    /**
     * {@code pack} offered through {@code url}. Built on Geyser's own pack record, so options, subpacks and
     * the content key stay as Geyser read them.
     *
     * @throws IllegalStateException when this Geyser cannot carry a different codec on its pack record
     */
    public static ResourcePack hosted(ResourcePack pack, String url) {
        return hosted(pack, url, () -> {});
    }

    /** As {@link #hosted(ResourcePack, String)}; {@code fallback} runs once if the client asks Geyser for the pack. */
    public static ResourcePack hosted(ResourcePack pack, String url, Runnable fallback) {
        HostedPackCodec codec = new HostedPackCodec(url, pack, fallback);
        return codec.create();
    }

    @Override
    public String url() {
        return url;
    }

    @Override
    public byte[] sha256() {
        return pack.codec().sha256();
    }

    @Override
    public long size() {
        return pack.codec().size();
    }

    @Override
    public SeekableByteChannel serialize() throws IOException {
        // Geyser only reads the bytes to send them in chunks: the client did not use the link.
        if (reported.compareAndSet(false, true)) {
            try {
                fallback.run();
            } catch (RuntimeException ignored) {
                // reporting never stops the transfer
            }
        }
        return pack.codec().serialize();
    }

    @Override
    protected ResourcePack create() {
        try {
            Method withCodec = pack.getClass().getMethod("withCodec", PackCodec.class);
            return (ResourcePack) withCodec.invoke(pack, this);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            throw new IllegalStateException("This Geyser version cannot announce a pack with a download link", failure);
        }
    }

    @Override
    protected ResourcePack.Builder createBuilder() {
        ResourcePack built = create();
        return new ResourcePack.Builder() {
            private String key = built.contentKey();

            @Override public ResourcePackManifest manifest() { return built.manifest(); }
            @Override public PackCodec codec() { return HostedPackCodec.this; }
            @Override public String contentKey() { return key; }

            @Override
            public ResourcePack.Builder contentKey(String contentKey) {
                key = contentKey;
                return this;
            }

            @Override
            public ResourcePack build() {
                try {
                    return (ResourcePack) built.getClass().getMethod("withContentKey", String.class).invoke(built, key);
                } catch (ReflectiveOperationException failure) {
                    throw new IllegalStateException(failure);
                }
            }
        };
    }
}
