package com.siberanka.twilight.compiler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.siberanka.twilight.config.TwilightConfig;
import com.siberanka.twilight.source.ContentSource;
import com.siberanka.twilight.source.ResourceIndex;
import com.siberanka.twilight.source.SourceDiscovery;
import com.siberanka.twilight.source.WorldLayout;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/** Focused read-only sound audit for repeatable server-pack compatibility checks. */
public final class SoundAuditMain {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private SoundAuditMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 2) throw new IllegalArgumentException("Usage: <report.json> <server-root>...");
        Path output = Path.of(args[0]).toAbsolutePath().normalize();
        String minecraftVersion = System.getProperty("twilight.audit.minecraftVersion", "").trim();
        Path cacheDirectory = output.getParent().resolve("sound-audit-cache");
        JsonArray servers = new JsonArray();
        boolean failed = false;
        for (int index = 1; index < args.length; index++) {
            JsonObject result = audit(Path.of(args[index]), cacheDirectory, minecraftVersion);
            servers.add(result);
            failed |= !result.getAsJsonArray("problems").isEmpty();
        }
        JsonObject report = new JsonObject();
        report.addProperty("schema", 1);
        report.addProperty("generated_at", Instant.now().toString());
        report.add("servers", servers);
        report.addProperty("passed", !failed);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(report) + System.lineSeparator(), StandardCharsets.UTF_8);
        System.out.println("Twilight sound audit: " + (failed ? "FAILED" : "PASSED") + " -> " + output);
        if (failed) System.exit(2);
    }

    private static JsonObject audit(Path rootInput, Path cacheDirectory, String minecraftVersion) throws Exception {
        Path root = rootInput.toRealPath();
        TwilightConfig config = new TwilightConfig(false, false, false, false, 40, 100,
                2_147_483_648L, 200_000, true, true, List.of(), "auto", false, false, 3);
        Set<Path> worlds = WorldLayout.discover(root, Set.of());
        List<ContentSource> sources = new SourceDiscovery(root, config).discover(worlds);
        try (VanillaAssetCache vanilla = minecraftVersion.isBlank() ? null : new VanillaAssetCache(cacheDirectory, minecraftVersion, true);
             ResourceIndex resources = vanilla == null ? ResourceIndex.build(sources, config)
                     : ResourceIndex.build(sources, config, vanilla::packFormat)) {
            SoundCompiler.Result sounds = new SoundCompiler(resources, false, vanilla).compile(new LinkedHashMap<>());
            JsonObject result = new JsonObject();
            result.addProperty("root", root.toString());
            result.addProperty("sources", sources.size());
            result.addProperty("indexed_assets", resources.paths().size());
            result.addProperty("java_sound_registries", resources.paths().stream()
                    .filter(path -> path.matches("assets/[a-z0-9_.-]+/sounds\\.json")).count());
            result.addProperty("java_ogg_files", resources.paths().stream()
                    .filter(path -> path.matches("assets/[a-z0-9_.-]+/sounds/.+\\.ogg")).count());
            result.addProperty("bedrock_sound_definitions", sounds.definitions());
            result.addProperty("bedrock_ogg_files", sounds.files());
            result.addProperty("vanilla_fallback_sounds", sounds.vanillaFallbackFiles());
            result.add("problems", GSON.toJsonTree(sounds.problems()));
            result.add("notices", GSON.toJsonTree(sounds.notices()));
            return result;
        }
    }
}
