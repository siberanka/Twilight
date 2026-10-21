/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.proxy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Checks and fingerprints Bedrock pack archives before Geyser ever sees them. */
final class PackFiles {
    private static final int MAX_ENTRIES = 200_000;
    private static final long MAX_MANIFEST = 1_048_576;

    private PackFiles() {}

    /** A checked pack: its file, SHA-256 and size. */
    record Pack(Path path, byte[] sha256, long size) {
        String hex() {
            return HexFormat.of().formatHex(sha256);
        }
    }

    /**
     * A readable ZIP below the size limit with a {@code manifest.json} at its root, and no entry that
     * could escape a folder when extracted. Anything else is refused.
     */
    static Pack inspect(Path file, long maxBytes) throws IOException {
        if (!Files.isRegularFile(file) || Files.isSymbolicLink(file)) throw new IOException("not a regular file: " + file.getFileName());
        long size = Files.size(file);
        if (size <= 0 || size > maxBytes) throw new IOException(file.getFileName() + " is " + size + " bytes (limit " + maxBytes + ")");
        try (ZipFile zip = new ZipFile(file.toFile())) {
            if (zip.size() > MAX_ENTRIES) throw new IOException(file.getFileName() + " has too many entries");
            ZipEntry manifest = zip.getEntry("manifest.json");
            if (manifest == null || manifest.isDirectory()) throw new IOException(file.getFileName() + " has no manifest.json at its root");
            if (manifest.getSize() > MAX_MANIFEST) throw new IOException(file.getFileName() + " has an oversized manifest.json");
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.startsWith("/") || name.contains("\\") || name.contains("../") || name.contains(":")) {
                    throw new IOException(file.getFileName() + " has an unsafe entry name");
                }
            }
        } catch (java.util.zip.ZipException invalid) {
            throw new IOException(file.getFileName() + " is not a valid ZIP archive", invalid);
        }
        return new Pack(file, sha256(file), size);
    }

    static byte[] sha256(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[65_536];
            for (int read; (read = input.read(buffer)) >= 0;) digest.update(buffer, 0, read);
            return digest.digest();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    static void moveInto(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** File names derived from server names: lowercase, no path characters. */
    static String safeName(String server) {
        String name = server.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        if (name.isEmpty() || name.startsWith(".")) name = "_" + name;
        return name.length() > 64 ? name.substring(0, 64) : name;
    }
}
