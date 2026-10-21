/* Copyright (c) 2026 siberanka. Twilight License (PolyForm Noncommercial 1.0.0 + terms), see LICENSE. */
package com.siberanka.twilight.integration.proxy;

import com.siberanka.twilight.protocol.PackChannel;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/**
 * What the Bedrock runtime of the twilight-proxy in front of this server shows, as it last said (signed) when this
 * server announced its pack. Kept across restarts, because the first build after a start runs before any player
 * (and so any proxy message) arrives. A statement older than {@link #MAX_AGE_MILLIS} no longer counts: the proxy
 * repeats it whenever a player joins, so only a proxy that stopped saying it gets there.
 */
public final class ProxyFeatures {
    static final long MAX_AGE_MILLIS = 30L * 24 * 60 * 60 * 1000;
    /** A confirmation of an unchanged statement is written at most this often. */
    static final long REFRESH_MILLIS = 24L * 60 * 60 * 1000;

    private final Path file;
    private volatile int flags;
    private volatile long confirmed;

    private ProxyFeatures(Path file, int flags, long confirmed) {
        this.file = file;
        this.flags = flags;
        this.confirmed = confirmed;
    }

    /** Reads the saved statement; a missing or unreadable file means none. */
    public static ProxyFeatures load(Path file) {
        int flags = 0;
        long confirmed = 0;
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                Properties properties = new Properties();
                properties.load(reader);
                flags = Integer.parseInt(properties.getProperty("flags", "0").strip());
                confirmed = Long.parseLong(properties.getProperty("confirmed", "0").strip());
            } catch (IOException | IllegalArgumentException unreadable) {
                flags = 0;
                confirmed = 0;
            }
        }
        return new ProxyFeatures(file, flags, confirmed);
    }

    /** Whether the proxy shows Java item displays with the pack's models (and so wants them built). */
    public boolean itemDisplays(long now) {
        return has(PackChannel.ITEM_DISPLAYS, now);
    }

    private boolean has(int flag, long now) {
        long age = now - confirmed;
        return (flags & flag) == flag && age >= 0 && age <= MAX_AGE_MILLIS;
    }

    /** What changed with a new statement. */
    public record Update(boolean itemDisplaysChanged, boolean save) {}

    /** Takes a verified statement from the proxy. */
    public synchronized Update accept(PackChannel.Capabilities capabilities, long now) {
        boolean before = itemDisplays(now);
        boolean changed = capabilities.flags() != flags;
        boolean save = changed || now - confirmed >= REFRESH_MILLIS;
        flags = capabilities.flags();
        confirmed = now;
        return new Update(before != itemDisplays(now), save);
    }

    /** Writes the statement (atomically, so a crash leaves the previous one). */
    public synchronized void save() throws IOException {
        Properties properties = new Properties();
        properties.setProperty("flags", Integer.toString(flags));
        properties.setProperty("confirmed", Long.toString(confirmed));
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
            properties.store(writer, "What the twilight-proxy in front of this server shows; written by Twilight");
        }
        try {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
