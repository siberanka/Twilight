/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.settings;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Keeps a configuration file current and working without touching what an administrator chose:
 *
 * <ul>
 *   <li><b>New settings</b> of a newer version are added with their default value and their explanation, in the
 *       place they have in the default file, indented like the file around them.</li>
 *   <li><b>Invalid values</b> (a word where a number belongs, a number out of range, an unknown option) are set
 *       back to their default, one setting at a time; every other value stays exactly as written.</li>
 *   <li><b>A file that cannot be read at all</b> (broken YAML) is replaced by the last version that worked, or by
 *       the defaults when there is none, with the new settings added.</li>
 * </ul>
 *
 * <p>Safety: nothing is written unless a copy of the old file is in {@code config-backups/} first (the newest ten
 * are kept); the new file is written to a temporary file and moved into place; the result must parse, pass every
 * check and come out of a second maintenance run unchanged, or nothing is written at all. A file this class wrote
 * is never changed again by it unless someone edits it (no repair loops). Files that are symbolic links, not
 * UTF-8, larger than 1 MiB or written in YAML this class cannot follow are never changed: they are only checked.
 * Comments, order, quoting, unknown keys and line endings stay as they were.
 */
public final class ConfigMaintenance {
    public static final String BACKUPS = "config-backups";
    static final long MAX_BYTES = 1_048_576;
    static final int KEEP_BACKUPS = 10;
    private static final Pattern NUMBER = Pattern.compile("[-+]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][-+]?\\d+)?");
    private static final Set<String> BOOLEANS = Set.of("true", "false", "yes", "no", "on", "off");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);

    /** Checks a whole configuration text the way the plugin reads it. */
    public interface Validator {
        /** Throws when the text is not readable YAML (for the platform's parser). */
        void syntax(String text) throws Exception;

        /** Throws when any value would be refused. */
        void validate(String text) throws Exception;
    }

    /** What a run did. */
    public record Report(boolean written, List<String> added, List<String> repaired, List<String> unknown,
                         Path backup, boolean restored, String skipped) {
        static Report skipped(String reason) {
            return new Report(false, List.of(), List.of(), List.of(), null, false, reason);
        }

        public boolean changed() {
            return written;
        }
    }

    /** The result of planning on a text: the new text and what changed. */
    record Plan(String text, List<String> added, List<String> repaired, List<String> unknown) {}

    private ConfigMaintenance() {}

    /**
     * Maintains {@code file} against the default file {@code defaults}. A missing file is left to the caller (it
     * writes the defaults). {@code freeForm} lists sections whose keys are the administrator's own (server names,
     * provider lists): their keys are never added, checked or reported.
     */
    public static Report run(Path file, String defaults, Validator validator, Set<String> freeForm,
                             Consumer<String> info, Consumer<String> warn, LocalDateTime now) {
        try {
            if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return Report.skipped("missing");
            if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                return Report.skipped("not a plain file (a link or a folder); it is only read");
            }
            if (Files.size(file) > MAX_BYTES) return Report.skipped("larger than 1 MiB; it is only read");
            byte[] bytes = Files.readAllBytes(file);
            String original;
            try {
                original = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            } catch (CharacterCodingException notUtf8) {
                return Report.skipped("not saved as UTF-8; it is only read");
            }
            boolean bom = original.startsWith("﻿");
            if (bom) original = original.substring(1);
            boolean crlf = original.contains("\r\n");
            String text = original.replace("\r\n", "\n");
            String defaultsText = defaults.replace("\r\n", "\n");
            Path backups = file.resolveSibling(BACKUPS);
            String fileName = file.getFileName().toString();
            Path lastGood = backups.resolve(baseName(fileName) + ".last-good.yml");

            if (!valid(validator, defaultsText)) return Report.skipped("the default file did not pass its own checks");

            boolean restored = false;
            String target;
            Plan plan;
            if (!readable(validator, text)) {
                // Broken YAML: the last file that worked (or the defaults), brought up to date.
                String fallback = defaultsText;
                if (Files.isRegularFile(lastGood, LinkOption.NOFOLLOW_LINKS) && Files.size(lastGood) <= MAX_BYTES) {
                    String good = Files.readString(lastGood, StandardCharsets.UTF_8).replace("\r\n", "\n");
                    if (valid(validator, good)) fallback = good;
                }
                plan = plan(fallback, defaultsText, validator, freeForm);
                if (!valid(validator, plan.text())) plan = new Plan(defaultsText, List.of(), List.of(), List.of());
                target = plan.text();
                restored = true;
            } else {
                YamlOutline outline;
                try {
                    outline = YamlOutline.parse(text);
                } catch (IllegalArgumentException unsupported) {
                    return Report.skipped("uses YAML that cannot be updated safely (" + unsupported.getMessage()
                            + "); it is only read");
                }
                plan = plan(text, defaultsText, validator, freeForm);
                target = plan.text();
                if (outline.entries.isEmpty() && !text.isBlank()) return Report.skipped("no settings found; it is only read");
            }

            Properties state = state(backups);
            String writtenHash = state.getProperty(fileName);
            String stamp = hash(text) + ":" + hash(defaultsText);
            if (target.equals(text)) {
                if (valid(validator, text)) remember(lastGood, text, backups);
                return new Report(false, List.of(), List.of(), plan.unknown(), null, false, null);
            }
            if (!restored && stamp.equals(writtenHash)) {
                // This file is exactly what an earlier run wrote with these defaults: never change it again on its own.
                return Report.skipped("already maintained; a further change was not applied (please report this)");
            }
            // The result must be readable, valid and final: a second run may not change it again.
            if (!valid(validator, target)) return Report.skipped("the repaired file would not pass the checks; nothing was changed");
            Plan again = plan(target, defaultsText, validator, freeForm);
            if (!again.text().equals(target)) return Report.skipped("the repair was not stable; nothing was changed");

            Files.createDirectories(backups);
            Path backup = backups.resolve(baseName(fileName) + "-" + now.format(STAMP) + (restored ? "-broken" : "") + ".yml");
            for (int suffix = 2; Files.exists(backup, LinkOption.NOFOLLOW_LINKS); suffix++) {
                backup = backups.resolve(baseName(fileName) + "-" + now.format(STAMP) + (restored ? "-broken" : "") + "-" + suffix + ".yml");
            }
            Files.write(backup, bytes);
            if (!java.util.Arrays.equals(Files.readAllBytes(backup), bytes)) {
                return Report.skipped("the backup could not be verified; nothing was changed");
            }
            String output = crlf ? target.replace("\n", "\r\n") : target;
            byte[] encoded = ((bom ? "﻿" : "") + output).getBytes(StandardCharsets.UTF_8);
            Path temporary = file.resolveSibling(fileName + ".twilight-tmp");
            Files.write(temporary, encoded);
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            state.setProperty(fileName, hash(target) + ":" + hash(defaultsText));
            saveState(backups, state);
            remember(lastGood, target, backups);
            prune(backups, baseName(fileName));
            Report report = new Report(true, plan.added(), plan.repaired(), plan.unknown(), backup, restored, null);
            describe(fileName, report, info, warn);
            return report;
        } catch (IOException | RuntimeException failure) {
            return Report.skipped("could not be maintained (" + failure.getClass().getSimpleName()
                    + (failure.getMessage() == null ? "" : ": " + failure.getMessage()) + "); it is only read");
        }
    }

    private static void describe(String file, Report report, Consumer<String> info, Consumer<String> warn) {
        String backup = BACKUPS + "/" + report.backup().getFileName();
        if (report.restored()) {
            warn.accept(file + " could not be read (broken YAML) and was restored from the last version that worked"
                    + " (or the defaults); the broken file is " + backup + ".");
        }
        if (!report.added().isEmpty()) {
            info.accept(file + ": added " + report.added().size() + " new setting(s) with their defaults: "
                    + summary(report.added()) + " (old file: " + backup + ").");
        }
        for (String repaired : report.repaired()) warn.accept(file + ": " + repaired + " (old file: " + backup + ").");
    }

    static String summary(List<String> paths) {
        if (paths.size() <= 8) return String.join(", ", paths);
        return String.join(", ", paths.subList(0, 8)) + " and " + (paths.size() - 8) + " more";
    }

    /** Unknown keys deserve a word: they are often typos, and they do nothing. */
    public static void warnUnknown(String file, Report report, Consumer<String> warn) {
        if (report.unknown().isEmpty()) return;
        warn.accept(file + ": unknown setting(s) " + summary(report.unknown()) + " have no effect (a typo, or a"
                + " setting of another version); they were left as they are.");
    }

    // ---- planning --------------------------------------------------------------------------------------------

    /** The maintained text for {@code text}: invalid values set back, missing settings added. */
    static Plan plan(String text, String defaults, Validator validator, Set<String> freeForm) {
        text = text.replace("\r\n", "\n");
        defaults = defaults.replace("\r\n", "\n");
        YamlOutline wanted = YamlOutline.parse(defaults);
        List<String> repaired = new ArrayList<>();
        String current = repair(text, defaults, wanted, validator, freeForm, repaired);
        List<String> added = new ArrayList<>();
        current = add(current, wanted, freeForm, added);
        List<String> unknown = new ArrayList<>();
        YamlOutline result = YamlOutline.parse(current);
        for (YamlOutline.Entry entry : result.entries) {
            if (wanted.get(entry.path()) != null || freeForm(entry.path(), freeForm)) continue;
            if (!entry.parent().isEmpty() && wanted.get(entry.parent()) == null) continue; // inside an unknown section
            unknown.add(entry.path());
        }
        return new Plan(current, List.copyOf(added), List.copyOf(repaired), List.copyOf(unknown));
    }

    private static boolean freeForm(String path, Set<String> freeForm) {
        for (String section : freeForm) {
            if (path.startsWith(section + ".")) return true;
        }
        return false;
    }

    /** Sets invalid values back to their defaults, one setting at a time. */
    private static String repair(String text, String defaults, YamlOutline wanted, Validator validator,
                                 Set<String> freeForm, List<String> repaired) {
        YamlOutline outline = YamlOutline.parse(text);
        List<String> lines = new ArrayList<>(outline.lines);
        // Values are tried one by one only when the file as a whole is refused: a value that is fine together
        // with the others is never taken for a broken one.
        boolean wholeValid = valid(validator, text);
        // From the bottom, so earlier line numbers stay valid.
        List<YamlOutline.Entry> entries = new ArrayList<>(outline.entries);
        entries.sort(Comparator.comparingInt(YamlOutline.Entry::line).reversed());
        for (YamlOutline.Entry entry : entries) {
            if (freeForm(entry.path(), freeForm)) continue;
            YamlOutline.Entry expected = wanted.get(entry.path());
            if (expected == null) continue;
            if (expected.kind() == YamlOutline.Kind.SECTION) {
                if (entry.kind() == YamlOutline.Kind.SECTION) continue;
                // A value where a section belongs: the default section takes its place.
                List<String> block = reindent(wanted.lines.subList(expected.line(), expected.end()), expected.indent(), entry.indent());
                replace(lines, entry.line(), entry.end(), block);
                repaired.add(entry.path() + " was a value, not a section; set back to the default section");
                continue;
            }
            if (expected.kind() == YamlOutline.Kind.OPAQUE || entry.kind() == YamlOutline.Kind.OPAQUE) {
                // A block list for a one-line list is fine; anything else is left to the checks.
                continue;
            }
            String reason = null;
            if (entry.kind() == YamlOutline.Kind.SECTION) {
                reason = "a section where a value belongs";
            } else {
                reason = typeProblem(expected, entry);
                if (reason == null && !wholeValid) reason = probe(defaults, wanted, expected, entry.value(), validator);
            }
            if (reason == null) continue;
            String keyLine = lines.get(entry.line());
            String fixed;
            if (entry.kind() == YamlOutline.Kind.SECTION) {
                fixed = keyLine.substring(0, entry.keyEnd() + 1) + " " + expected.value();
                replace(lines, entry.line(), entry.end(), List.of(fixed));
            } else {
                fixed = keyLine.substring(0, entry.valueStart()) + expected.value() + keyLine.substring(entry.valueEnd());
                lines.set(entry.line(), fixed);
            }
            repaired.add(entry.path() + ": '" + shorten(entry.kind() == YamlOutline.Kind.SECTION ? "(section)" : entry.value())
                    + "' " + reason + "; set back to " + expected.value());
        }
        return join(lines);
    }

    private static String shorten(String value) {
        String flat = value.replaceAll("[\\p{Cntrl}]", "?");
        return flat.length() > 80 ? flat.substring(0, 77) + "..." : flat;
    }

    /** Why a value has the wrong type for its default, or null. */
    private static String typeProblem(YamlOutline.Entry expected, YamlOutline.Entry actual) {
        String defaultValue = YamlOutline.unquote(expected.value());
        String value = actual.kind() == YamlOutline.Kind.SCALAR ? YamlOutline.unquote(actual.value()) : actual.value();
        boolean quotedDefault = expected.value().startsWith("\"") || expected.value().startsWith("'");
        if (expected.kind() == YamlOutline.Kind.INLINE_LIST) {
            return actual.kind() == YamlOutline.Kind.INLINE_LIST ? null : "is not a list";
        }
        if (actual.kind() == YamlOutline.Kind.INLINE_LIST) return "is a list";
        if (!quotedDefault && NUMBER.matcher(defaultValue).matches()) {
            return NUMBER.matcher(value.strip()).matches() ? null : "is not a number";
        }
        if (!quotedDefault && BOOLEANS.contains(defaultValue.toLowerCase(Locale.ROOT))) {
            return BOOLEANS.contains(value.strip().toLowerCase(Locale.ROOT)) ? null : "is not true or false";
        }
        return null;
    }

    /** Whether the defaults with only this value changed still pass the checks; the reason when not. */
    private static String probe(String defaults, YamlOutline wanted, YamlOutline.Entry expected, String value,
                                Validator validator) {
        List<String> lines = new ArrayList<>(wanted.lines);
        String line = lines.get(expected.line());
        lines.set(expected.line(), line.substring(0, expected.valueStart()) + value + line.substring(expected.valueEnd()));
        try {
            validator.validate(join(lines));
            return null;
        } catch (Exception | LinkageError refused) {
            String message = refused.getMessage();
            return message == null || message.isBlank() ? "is not accepted" : "is not accepted (" + shorten(message) + ")";
        }
    }

    /** Adds the settings of the defaults that the text lacks, with their comments, where the defaults have them. */
    private static String add(String text, YamlOutline wanted, Set<String> freeForm, List<String> added) {
        String current = text;
        for (int round = 0; round <= wanted.entries.size(); round++) {
            YamlOutline outline = YamlOutline.parse(current);
            YamlOutline.Entry missing = null;
            for (YamlOutline.Entry entry : wanted.entries) {
                if (outline.get(entry.path()) != null || freeForm(entry.path(), freeForm)) continue;
                if (!entry.parent().isEmpty() && outline.get(entry.parent()) == null) continue; // comes with its section
                if (!entry.parent().isEmpty() && outline.get(entry.parent()).kind() != YamlOutline.Kind.SECTION) continue;
                missing = entry;
                break;
            }
            if (missing == null) return current;
            current = insert(outline, wanted, missing);
            added.add(missing.kind() == YamlOutline.Kind.SECTION ? missing.path() + ".*" : missing.path());
        }
        throw new IllegalStateException("adding settings did not finish");
    }

    private static String insert(YamlOutline outline, YamlOutline wanted, YamlOutline.Entry missing) {
        List<String> lines = new ArrayList<>(outline.lines);
        List<YamlOutline.Entry> siblings = wanted.children(missing.parent());
        // After the nearest earlier sibling the file has, else before the nearest later one, else in the section.
        int position = -1;
        int index = siblings.indexOf(missing);
        for (int i = index - 1; i >= 0 && position < 0; i--) {
            YamlOutline.Entry present = outline.get(siblings.get(i).path());
            if (present != null) position = present.end();
        }
        if (position < 0) {
            for (int i = index + 1; i < siblings.size() && position < 0; i++) {
                YamlOutline.Entry present = outline.get(siblings.get(i).path());
                if (present != null) position = present.commentStart();
            }
        }
        YamlOutline.Entry parent = missing.parent().isEmpty() ? null : outline.get(missing.parent());
        if (position < 0) position = parent == null ? lines.size() : parent.end();
        // Indentation: the file's own for this section.
        int indent;
        List<YamlOutline.Entry> present = outline.children(missing.parent());
        if (!present.isEmpty()) {
            indent = present.getFirst().indent();
        } else if (parent != null) {
            YamlOutline.Entry wantedParent = wanted.get(missing.parent());
            indent = parent.indent() + Math.max(1, missing.indent() - wantedParent.indent());
        } else {
            indent = 0;
        }
        List<String> block = new ArrayList<>(reindent(wanted.lines.subList(missing.commentStart(), missing.end()),
                missing.indent(), indent));
        boolean topLevel = missing.parent().isEmpty();
        if (topLevel) {
            if (position > 0 && !lines.get(position - 1).isBlank()) block.addFirst("");
            if (position < lines.size() && !lines.get(position).isBlank()) block.add("");
        }
        lines.addAll(position, block);
        return join(lines);
    }

    /** Moves a block from one indentation to another, keeping its inner structure. */
    static List<String> reindent(List<String> block, int from, int to) {
        List<String> moved = new ArrayList<>(block.size());
        int shift = to - from;
        for (String line : block) {
            if (line.isBlank()) {
                moved.add("");
                continue;
            }
            int indent = YamlOutline.indent(line);
            moved.add(" ".repeat(Math.max(0, indent + shift)) + line.substring(indent));
        }
        return moved;
    }

    private static void replace(List<String> lines, int from, int to, List<String> block) {
        for (int i = to - 1; i >= from; i--) lines.remove(i);
        lines.addAll(from, block);
    }

    private static String join(List<String> lines) {
        return lines.isEmpty() ? "" : String.join("\n", lines) + "\n";
    }

    private static boolean readable(Validator validator, String text) {
        try {
            // Only the platform's own parser decides: YAML it reads but the outline cannot follow is only read.
            validator.syntax(text);
            return true;
        } catch (Exception | LinkageError broken) {
            return false;
        }
    }

    private static boolean valid(Validator validator, String text) {
        try {
            validator.syntax(text);
            validator.validate(text);
            YamlOutline.parse(text);
            return true;
        } catch (Exception | LinkageError invalid) {
            return false;
        }
    }

    // ---- files -----------------------------------------------------------------------------------------------

    private static String baseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    /** Keeps a copy of the last version that worked, for a file that later cannot be read. */
    private static void remember(Path lastGood, String text, Path backups) {
        try {
            if (Files.isRegularFile(lastGood, LinkOption.NOFOLLOW_LINKS)
                    && Files.readString(lastGood, StandardCharsets.UTF_8).equals(text)) return;
            Files.createDirectories(backups);
            Path temporary = lastGood.resolveSibling(lastGood.getFileName() + ".tmp");
            Files.writeString(temporary, text, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, lastGood, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, lastGood, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException ignored) {
            // Only a convenience for a later repair.
        }
    }

    private static Properties state(Path backups) {
        Properties state = new Properties();
        Path file = backups.resolve("maintenance.properties");
        try {
            if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && Files.size(file) < 65_536) {
                try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    state.load(reader);
                }
            }
        } catch (IOException | RuntimeException unreadable) {
            state.clear();
        }
        return state;
    }

    private static void saveState(Path backups, Properties state) throws IOException {
        Path file = backups.resolve("maintenance.properties");
        try (var writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            state.store(writer, "Hashes of the files Twilight last wrote (no repair loops)");
        }
    }

    /** Keeps the newest backups of a file. */
    private static void prune(Path backups, String base) {
        try (var files = Files.list(backups)) {
            List<Path> copies = files.filter(path -> {
                String name = path.getFileName().toString();
                return name.startsWith(base + "-") && name.endsWith(".yml") && name.length() > base.length() + 16;
            }).sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed()).toList();
            for (int i = KEEP_BACKUPS; i < copies.size(); i++) Files.deleteIfExists(copies.get(i));
        } catch (IOException | RuntimeException ignored) {
            // Old copies stay.
        }
    }

    static String hash(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
