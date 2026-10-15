/* Copyright (c) 2026 siberanka. Twilight License (PolyForm Noncommercial 1.0.0 + terms), see LICENSE. */
package com.siberanka.twilight.integration.heads;

import com.siberanka.twilight.geyser.HeadTextures;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Player heads named in the configuration of the server's plugins: menus (DeluxeMenus, zMenu, ChestCommands,
 * CommandPanels, TrMenu, BossShopPro, AuraSkills, Lands and the rest), shops, crates and holograms write a head as a
 * Base64 profile ({@code basehead-}, {@code texture:}), a skin URL or hash ({@code texture-}), a user name
 * ({@code head-Notch}) or a HeadDatabase id ({@code hdb-123}). The forms differ between plugins, the encodings do
 * not, so the files are read for the encodings rather than per plugin.
 *
 * <p>Only text files of moderate size are read, placeholders ({@code %player%}) are skipped, and the scan is bounded,
 * so a large or odd plugin folder cannot hold the server up; it runs off the server thread.
 */
public final class HeadSources {
    static final long MAX_FILE = 2L * 1024 * 1024;
    static final long MAX_TOTAL = 64L * 1024 * 1024;
    static final int MAX_FILES = 20_000;
    private static final Pattern BASE64 = Pattern.compile("(?<![A-Za-z0-9+/])(?:eyJ|ewo|ewog|e3)[A-Za-z0-9+/]{40,}={0,2}");
    private static final Pattern URL = Pattern.compile("textures\\.minecraft\\.net/texture/([0-9a-fA-F]{16,64})");
    private static final Pattern HASH_KEY = Pattern.compile("(?i)(?:texture|skull|head)[-_:=\\s\"']{1,3}([0-9a-f]{56,64})\\b");
    /** {@code material: head-Notch} (DeluxeMenus and its kind) and {@code skull-owner: Notch} (ChestCommands and others). */
    private static final Pattern NAME = Pattern.compile("(?i)(?:material\\s*:\\s*['\"]?head-|skull-?owner\\s*:\\s*['\"]?|player-?head\\s*:\\s*['\"]?)([A-Za-z0-9_]{3,16})(?![\\w%{])");
    private static final Pattern HDB = Pattern.compile("(?i)(?:hdb|headdatabase|hdb-id)[-:]\\s*['\"]?(\\d{1,7})(?!\\d)");
    private static final Set<String> SKIPPED = Set.of("twilight", "geyser-spigot", "floodgate", "bstats", "spark",
            "luckperms", "worldedit", "fastasyncworldedit", "coreprotect", "dynmap", "bluemap", "squaremap", "plan");

    private HeadSources() {}

    /** What a scan found: skins (hashes), user names and HeadDatabase ids. */
    public record Found(Set<String> skins, Set<String> names, Set<String> headDatabase, int files) {}

    /** Reads the plugins folder; {@code headDatabase} resolves a HeadDatabase id to its Base64 profile, or null. */
    public static Found scan(Path plugins, Function<String, String> headDatabase) throws IOException {
        Set<String> skins = new LinkedHashSet<>(), names = new LinkedHashSet<>(), ids = new LinkedHashSet<>();
        long[] total = {0};
        int[] files = {0};
        if (Files.isDirectory(plugins)) Files.walkFileTree(plugins, Set.of(), 8, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) {
                if (dir.equals(plugins)) return FileVisitResult.CONTINUE;
                String name = dir.getFileName().toString().toLowerCase(Locale.ROOT);
                if (dir.getParent().equals(plugins) && SKIPPED.contains(name)) return FileVisitResult.SKIP_SUBTREE;
                if (name.startsWith(".") || name.equals("logs") || name.equals("cache") || name.equals("backups")
                        || name.equals("libs") || name.equals("libraries")) return FileVisitResult.SKIP_SUBTREE;
                return FileVisitResult.CONTINUE;
            }

            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                if (files[0] >= MAX_FILES || total[0] >= MAX_TOTAL) return FileVisitResult.TERMINATE;
                String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                if (!(name.endsWith(".yml") || name.endsWith(".yaml") || name.endsWith(".json") || name.endsWith(".conf")
                        || name.endsWith(".toml") || name.endsWith(".txt") || name.endsWith(".properties"))) return FileVisitResult.CONTINUE;
                if (attributes.size() > MAX_FILE || !attributes.isRegularFile()) return FileVisitResult.CONTINUE;
                files[0]++;
                total[0] += attributes.size();
                String text;
                try {
                    text = Files.readString(file, StandardCharsets.UTF_8);
                } catch (IOException | RuntimeException notText) {
                    return FileVisitResult.CONTINUE;
                }
                read(text, skins, names, ids);
                return FileVisitResult.CONTINUE;
            }

            @Override public FileVisitResult visitFileFailed(Path file, IOException failure) {
                return FileVisitResult.CONTINUE;
            }
        });
        if (headDatabase != null) for (String id : ids) {
            try {
                String hash = HeadTextures.fromValue(headDatabase.apply(id));
                if (hash != null) skins.add(hash);
            } catch (RuntimeException | LinkageError unavailable) {
                break;
            }
        }
        return new Found(Set.copyOf(skins), Set.copyOf(names), Set.copyOf(ids), files[0]);
    }

    /** The heads one text names. */
    static void read(String text, Set<String> skins, Set<String> names, Set<String> ids) {
        if (text.indexOf("eyJ") >= 0 || text.indexOf("ewo") >= 0 || text.indexOf("e3") >= 0) {
            Matcher base64 = BASE64.matcher(text);
            while (base64.find()) {
                String hash = HeadTextures.fromValue(base64.group());
                if (hash != null) skins.add(hash);
            }
        }
        if (text.contains("textures.minecraft.net")) {
            Matcher url = URL.matcher(text);
            while (url.find()) skins.add(url.group(1).toLowerCase(Locale.ROOT));
        }
        Matcher hash = HASH_KEY.matcher(text);
        while (hash.find()) {
            String value = hash.group(1).toLowerCase(Locale.ROOT);
            if (HeadTextures.valid(value)) skins.add(value);
        }
        Matcher name = NAME.matcher(text);
        while (name.find()) {
            String user = name.group(1);
            // Item ids and words a config often puts after "head:" are not players.
            if (HeadTextures.name(user) && !COMMON.contains(user.toLowerCase(Locale.ROOT))) names.add(user);
        }
        Matcher hdb = HDB.matcher(text);
        while (hdb.find()) ids.add(hdb.group(1));
    }

    private static final Set<String> COMMON = Set.of("true", "false", "none", "null", "player", "player_head", "skull",
            "head", "default", "self", "owner", "name", "texture", "base64", "material", "item", "slot", "custom",
            "enabled", "disabled", "yes", "no", "url", "value", "type", "hdb", "basehead", "playerhead", "steve", "alex");

    /** The heads of a scan, in the form {@link PlayerHeads#report} takes. */
    public static List<String> heads(Found found) {
        List<String> heads = new ArrayList<>(found.skins());
        for (String name : found.names()) heads.add("name:" + name);
        return heads;
    }
}
