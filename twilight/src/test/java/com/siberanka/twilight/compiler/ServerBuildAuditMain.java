package com.siberanka.twilight.compiler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.siberanka.twilight.config.TwilightConfig;
import com.siberanka.twilight.source.ContentSource;
import com.siberanka.twilight.source.SourceDiscovery;
import com.siberanka.twilight.source.WorldLayout;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipFile;

/**
 * Production-shaped build audit: discovers every server's sources read-only and runs the complete
 * Bedrock pack build (items, fonts with the text layout, sounds, UI) into a separate data directory,
 * as the plugin would on that server. Reports conversion counts, problems, notices and the pack.
 *
 * <p>Usage: {@code <report.json> <data-root> <server-root>...}; the vanilla client cache is read from
 * {@code <data-root>/cache/vanilla/<version>} (system property {@code twilight.audit.minecraftVersion}).
 */
public final class ServerBuildAuditMain {
    private static final Gson GSON = new GsonBuilder().registerTypeAdapter(java.time.Instant.class,
            (com.google.gson.JsonSerializer<java.time.Instant>) (value, type, context) -> new com.google.gson.JsonPrimitive(value.toString()))
            .setPrettyPrinting().disableHtmlEscaping().create();

    private ServerBuildAuditMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 3) throw new IllegalArgumentException("Usage: <report.json> <data-root> <server-root>...");
        Path output = Path.of(args[0]).toAbsolutePath().normalize();
        Path dataRoot = Path.of(args[1]).toAbsolutePath().normalize();
        String version = System.getProperty("twilight.audit.minecraftVersion", "").trim();
        JsonArray servers = new JsonArray();
        boolean failed = false;
        for (int index = 2; index < args.length; index++) {
            Path root = Path.of(args[index]).toRealPath();
            JsonObject result = new JsonObject();
            result.addProperty("root", root.toString());
            // Non-strict, like a first production build: every problem is listed instead of the first.
            TwilightConfig config = new TwilightConfig(false, false, false, false, 40, 100,
                    2_147_483_648L, 200_000, !version.isEmpty(), true, List.of(), "auto", false, false, 3);
            try {
                Set<Path> worlds = WorldLayout.discover(root, Set.of());
                List<ContentSource> sources = new SourceDiscovery(root, config).discover(worlds);
                result.addProperty("sources", sources.size());
                java.util.Map<String, Integer> providers = new java.util.TreeMap<>();
                for (ContentSource source : sources) providers.merge(source.provider() + " " + source.kind(), 1, Integer::sum);
                result.add("source_kinds", GSON.toJsonTree(providers));
                result.add("content", GSON.toJsonTree(new com.siberanka.twilight.source.ContentInspector(config).inspect(sources)));
                // One data directory: the shared vanilla cache is reused, each build replaces the previous output.
                Path data = dataRoot;
                long started = System.nanoTime();
                // -Dtwilight.audit.itemDisplays=false builds the pack a proxy's Geyser gets (no display models).
                boolean displays = Boolean.parseBoolean(System.getProperty("twilight.audit.itemDisplays", "true"));
                int maxCell = Integer.getInteger("twilight.audit.maxGlyphCell", 512);
                BuildResult build = (version.isEmpty() ? new BedrockPackCompiler(data, config) : new BedrockPackCompiler(data, config, version))
                        .withItemDisplays(displays).withMaxGlyphCell(maxCell)
                        // -Dtwilight.audit.displayedItems=<file>: only the items in that list (as in displayed-items.txt)
                        // and model bones get display models; "none" for an empty list.
                        .withDisplayedItems(displayedItems())
                        .build(sources, runtimeItems(root, displays));
                result.addProperty("seconds", Math.round((System.nanoTime() - started) / 1e7) / 100.0);
                result.addProperty("candidates", build.candidates());
                result.addProperty("converted", build.converted());
                result.addProperty("three_dimensional", build.threeDimensional());
                result.addProperty("glyphs", build.glyphs());
                result.addProperty("font_pages", build.fontPages());
                result.addProperty("sound_definitions", build.soundDefinitions());
                result.addProperty("pack_sha256", build.packSha256());
                Path pack = build.outputDirectory().resolve("pack.zip");
                result.addProperty("pack_bytes", Files.size(pack));
                try (ZipFile zip = new ZipFile(pack.toFile())) { result.addProperty("pack_entries", zip.size()); }
                JsonObject report = JsonParser.parseString(Files.readString(build.outputDirectory().resolve("build-report.json")))
                        .getAsJsonObject();
                result.add("text_layout_entries", report.get("text_layout_entries"));
                result.add("aliased_glyphs", report.get("aliased_glyphs"));
                result.add("custom_blocks", report.get("custom_blocks"));
                result.add("block_items", report.get("block_items"));
                result.add("problems", GSON.toJsonTree(build.problems()));
                result.add("notices", report.get("notices"));
                failed |= !build.problems().isEmpty();
            } catch (Exception failure) {
                result.addProperty("error", failure.toString());
                failed = true;
            }
            servers.add(result);
            System.out.println(GSON.toJson(result));
        }
        JsonObject report = new JsonObject();
        report.addProperty("schema", 1);
        report.addProperty("minecraft_version", version);
        report.add("servers", servers);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(report) + System.lineSeparator(), StandardCharsets.UTF_8);
        System.out.println("Twilight server build audit: " + (failed ? "PROBLEMS" : "CLEAN") + " -> " + output);
    }

    /**
     * The items a running server adds through provider APIs that can be read from files: ModelEngine's bones, as
     * Twilight reads them when its registry is unavailable (left out without display models, as in the plugin).
     */
    private static Set<String> displayedItems() throws java.io.IOException {
        String file = System.getProperty("twilight.audit.displayedItems");
        if (file == null || file.isBlank()) return null;
        if (file.equals("none")) return Set.of();
        return Set.copyOf(Files.readAllLines(Path.of(file)));
    }

    private static List<com.siberanka.twilight.source.CustomItemDescriptor> runtimeItems(Path root, boolean displays) throws java.io.IOException {
        if (!displays) return List.of();
        return com.siberanka.twilight.source.ModelEngineFiles.descriptors(root.resolve("plugins/ModelEngine"));
    }
}
