/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.conflicts;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Reads what other tools put in a Geyser folder: extensions (ids), and the options of Geyser's own config. */
public final class GeyserFolder {
    private static final int MAX_EXTENSIONS = 128;
    private static final long MAX_YAML = 64 * 1024;
    private static final Pattern ID = Pattern.compile("(?m)^id:[ \t]*['\"]?([A-Za-z0-9_.-]+)");
    private static final Pattern NAME = Pattern.compile("(?m)^name:[ \t]*['\"]?([A-Za-z0-9_. -]+?)['\"]?[ \t]*$");

    private GeyserFolder() {}

    /**
     * The ids and names of the extension jars in {@code geyser/extensions} (from each jar's {@code extension.yml};
     * the file name when a jar has none). Unreadable jars are skipped; never throws.
     */
    public static Set<String> extensions(Path geyserFolder) {
        Set<String> found = new TreeSet<>();
        Path folder = geyserFolder == null ? null : geyserFolder.resolve("extensions");
        if (folder == null || !Files.isDirectory(folder) || Files.isSymbolicLink(folder)) return found;
        try (var jars = Files.list(folder)) {
            for (Path jar : jars.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .sorted().limit(MAX_EXTENSIONS).toList()) {
                found.add(jar.getFileName().toString().toLowerCase(Locale.ROOT).replaceAll("[.]jar$", ""));
                try (ZipFile zip = new ZipFile(jar.toFile())) {
                    ZipEntry entry = zip.getEntry("extension.yml");
                    if (entry == null || entry.getSize() > MAX_YAML) continue;
                    try (InputStream in = zip.getInputStream(entry)) {
                        String yaml = new String(in.readNBytes((int) MAX_YAML), StandardCharsets.UTF_8);
                        Matcher id = ID.matcher(yaml);
                        if (id.find()) found.add(id.group(1).toLowerCase(Locale.ROOT));
                        Matcher name = NAME.matcher(yaml);
                        if (name.find()) found.add(name.group(1).strip().toLowerCase(Locale.ROOT));
                    }
                } catch (IOException | RuntimeException unreadable) {
                    // A broken jar is Geyser's to report.
                }
            }
        } catch (IOException unreadable) {
            // The listing is a hint.
        }
        return found;
    }

    /** A boolean option of Geyser's {@code config.yml} (top level or nested), if it is set there. */
    public static Optional<Boolean> option(Path geyserFolder, String key) {
        if (geyserFolder == null) return Optional.empty();
        Path file = geyserFolder.resolve("config.yml");
        try {
            if (!Files.isRegularFile(file) || Files.size(file) > 1_048_576) return Optional.empty();
            Matcher matcher = Pattern.compile("(?m)^[ \t]*" + Pattern.quote(key) + ":[ \t]*(true|false)(?![A-Za-z0-9])").matcher(Files.readString(file));
            return matcher.find() ? Optional.of(Boolean.parseBoolean(matcher.group(1))) : Optional.empty();
        } catch (IOException | RuntimeException unreadable) {
            return Optional.empty();
        }
    }
}
