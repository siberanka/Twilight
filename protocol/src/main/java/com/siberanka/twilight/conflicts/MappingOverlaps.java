/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.conflicts;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.siberanka.twilight.conflicts.Conflicts.Finding;
import com.siberanka.twilight.conflicts.Conflicts.Level;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Finds what other tools in a Geyser folder map or send that Twilight does too: Geyser custom item mappings
 * (java2bedrock.sh, Rainbow, Scaffolding, hand-written files in {@code custom_mappings}) that name the same Java
 * item selector, and resource packs in {@code packs} that carry the same pack id as Twilight's.
 *
 * <p>Precedence is documented rather than guessed: Twilight never edits another tool's file. It reports each
 * overlapping file once (the first {@code maxNotices} files, then one summary line); with
 * {@code foreign-mappings: retire} it moves the overlapping files out of Geyser to a dated folder, the way it
 * retires its own stale files. Nothing here throws into Geyser's start: unreadable or oversized files are skipped.
 */
public final class MappingOverlaps {
    private static final long MAX_MAPPING_BYTES = 16L * 1024 * 1024;
    private static final long MAX_MANIFEST_BYTES = 64 * 1024;
    private static final int MAX_FILES = 512;
    private static final int MAX_DEPTH = 4;
    private static final DateTimeFormatter FOLDER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /** An overlapping foreign file: path relative to the Geyser folder, how many selectors clash, and some examples. */
    public record Overlap(String file, int count, List<String> examples) {}

    /** The overlaps found, plus findings ready to log. */
    public record Result(List<Overlap> overlaps, List<Finding> findings) {
        public static final Result NONE = new Result(List.of(), List.of());
    }

    private MappingOverlaps() {}

    /**
     * @param twilightMapping the selectors Twilight maps are read from every {@code twilight*.json} in
     *                        {@code custom_mappings}; none yet (first start) means no overlap can be known
     */
    public static Result scan(Path geyserFolder, ConflictSettings settings) {
        if (!settings.enabled() || settings.foreignMappings() == ConflictSettings.Foreign.OFF || geyserFolder == null) return Result.NONE;
        List<Overlap> overlaps = new ArrayList<>();
        List<Finding> findings = new ArrayList<>();
        Path mappings = geyserFolder.resolve("custom_mappings");
        if (Files.isDirectory(mappings) && !Files.isSymbolicLink(mappings)) {
            List<Path> own = new ArrayList<>();
            List<Path> foreign = new ArrayList<>();
            try (var walk = Files.walk(mappings, MAX_DEPTH)) {
                for (Path file : walk.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                        .sorted().limit(MAX_FILES).toList()) {
                    (file.getFileName().toString().toLowerCase(Locale.ROOT).startsWith("twilight") ? own : foreign).add(file);
                }
            } catch (IOException unreadable) {
                return Result.NONE;
            }
            Set<String> twilight = new LinkedHashSet<>();
            own.forEach(file -> twilight.addAll(selectors(file)));
            if (!twilight.isEmpty()) {
                for (Path file : foreign) {
                    List<String> clashing = selectors(file).stream().filter(twilight::contains).sorted().toList();
                    if (clashing.isEmpty()) continue;
                    String relative = geyserFolder.relativize(file).toString().replace('\\', '/');
                    overlaps.add(new Overlap(relative, clashing.size(), clashing.stream().limit(3).toList()));
                }
            }
        }
        int shown = 0;
        for (Overlap overlap : overlaps) {
            if (shown++ >= settings.maxNotices()) break;
            boolean retire = settings.foreignMappings() == ConflictSettings.Foreign.RETIRE;
            findings.add(new Finding("mapping-overlap:" + overlap.file(), Level.WARN,
                    overlap.file() + " maps " + overlap.count() + " Java item selector(s) that Twilight maps too (for example "
                            + String.join(", ", overlap.examples()) + "): Geyser keeps the one it reads first (files are read in name order) and logs"
                            + " \"Not registering custom item definition\" for the other, so Bedrock players may get the other tool's item instead of Twilight's.",
                    retire ? "Twilight moves it out of Geyser (integrations.conflicts.foreign-mappings: retire)."
                            : "Remove the file, or set integrations.conflicts.foreign-mappings: retire to let Twilight move it out."));
        }
        if (overlaps.size() > settings.maxNotices()) {
            findings.add(new Finding("mapping-overlap-more", Level.WARN,
                    (overlaps.size() - settings.maxNotices()) + " more mapping file(s) in custom_mappings overlap Twilight's mappings.",
                    "See docs/MIGRATION.md."));
        }
        findings.addAll(packClashes(geyserFolder, settings));
        return new Result(List.copyOf(overlaps), List.copyOf(findings));
    }

    /** Moves overlapping foreign files out of Geyser (never deletes); returns where each went, relative to {@code retiredRoot}. */
    public static List<String> retire(Path geyserFolder, List<Overlap> overlaps, Path retiredRoot) throws IOException {
        List<String> moved = new ArrayList<>();
        if (overlaps.isEmpty()) return moved;
        Path target = retiredRoot.resolve(LocalDateTime.now().format(FOLDER));
        for (Overlap overlap : overlaps) {
            Path source = geyserFolder.toAbsolutePath().normalize().resolve(overlap.file()).normalize();
            if (!source.startsWith(geyserFolder.toAbsolutePath().normalize().resolve("custom_mappings")) || !Files.isRegularFile(source)) continue;
            Path destination = target.resolve(overlap.file().replace('/', java.io.File.separatorChar));
            Files.createDirectories(destination.getParent());
            for (int index = 2; Files.exists(destination); index++) destination = destination.resolveSibling(source.getFileName() + "." + index);
            try {
                Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException otherDisk) {
                Files.move(source, destination);
            }
            moved.add(overlap.file() + " -> " + retiredRoot.getParent().relativize(destination).toString().replace('\\', '/'));
        }
        return moved;
    }

    /** Selectors of a mapping file: {@code <java item>|cmd=<n>}, {@code model=<item model>} and {@code block=<state>}. */
    static Set<String> selectors(Path file) {
        Set<String> selectors = new TreeSet<>();
        try {
            if (Files.size(file) > MAX_MAPPING_BYTES) return selectors;
            JsonElement root;
            try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                root = JsonParser.parseReader(reader);
            }
            if (!root.isJsonObject()) return selectors;
            JsonObject object = root.getAsJsonObject();
            JsonElement items = object.get("items");
            if (items != null && items.isJsonObject()) {
                for (var base : items.getAsJsonObject().entrySet()) {
                    if (!base.getValue().isJsonArray()) continue;
                    for (JsonElement entry : base.getValue().getAsJsonArray()) {
                        if (!entry.isJsonObject()) continue;
                        JsonObject item = entry.getAsJsonObject();
                        if (item.has("model") && item.get("model").isJsonPrimitive()) {
                            selectors.add("model=" + item.get("model").getAsString());
                        } else if (item.has("custom_model_data") && item.get("custom_model_data").isJsonPrimitive()) {
                            selectors.add(base.getKey() + "|cmd=" + item.get("custom_model_data").getAsString());
                        }
                    }
                }
            } else if (items != null && items.isJsonArray()) {
                for (JsonElement entry : items.getAsJsonArray()) {
                    if (entry.isJsonObject() && entry.getAsJsonObject().has("model") && entry.getAsJsonObject().get("model").isJsonPrimitive()) {
                        selectors.add("model=" + entry.getAsJsonObject().get("model").getAsString());
                    }
                }
            }
            JsonElement blocks = object.get("blocks");
            if (blocks != null && blocks.isJsonObject()) blocks.getAsJsonObject().keySet().forEach(key -> selectors.add("block=" + key));
        } catch (IOException | RuntimeException | StackOverflowError unreadable) {
            // Malformed files are Geyser's to report.
        }
        return selectors;
    }

    /** Packs in {@code packs} (other than Twilight's) that carry Twilight's pack id: Bedrock then treats them as one pack. */
    private static List<Finding> packClashes(Path geyserFolder, ConflictSettings settings) {
        List<Finding> findings = new ArrayList<>();
        Path folder = geyserFolder.resolve("packs");
        if (!Files.isDirectory(folder) || Files.isSymbolicLink(folder)) return findings;
        try (var files = Files.list(folder)) {
            List<Path> packs = files.filter(Files::isRegularFile).filter(p -> {
                String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                return name.endsWith(".zip") || name.endsWith(".mcpack");
            }).sorted().limit(MAX_FILES).toList();
            String twilightId = null;
            for (Path pack : packs) if (pack.getFileName().toString().toLowerCase(Locale.ROOT).startsWith("twilight")) twilightId = packId(pack);
            if (twilightId == null) return findings;
            int notices = 0;
            for (Path pack : packs) {
                if (pack.getFileName().toString().toLowerCase(Locale.ROOT).startsWith("twilight")) continue;
                if (twilightId.equals(packId(pack)) && notices++ < settings.maxNotices()) {
                    findings.add(new Finding("pack-id:" + pack.getFileName(), Level.WARN,
                            "packs/" + pack.getFileName() + " has the same pack id as Twilight's pack: Bedrock treats them as one pack,"
                                    + " so one replaces the other.",
                            "Remove the copy (Twilight deploys packs/twilight.zip itself)."));
                }
            }
        } catch (IOException unreadable) {
            // A hint only.
        }
        return findings;
    }

    private static String packId(Path pack) {
        try (ZipFile zip = new ZipFile(pack.toFile())) {
            ZipEntry manifest = zip.getEntry("manifest.json");
            if (manifest == null || manifest.getSize() > MAX_MANIFEST_BYTES) return null;
            try (InputStream in = zip.getInputStream(manifest)) {
                JsonObject root = JsonParser.parseString(new String(in.readNBytes((int) MAX_MANIFEST_BYTES), StandardCharsets.UTF_8)).getAsJsonObject();
                return root.getAsJsonObject("header").get("uuid").getAsString().toLowerCase(Locale.ROOT);
            }
        } catch (IOException | RuntimeException notAPack) {
            return null;
        }
    }
}
