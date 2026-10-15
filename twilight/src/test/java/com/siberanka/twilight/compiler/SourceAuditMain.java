package com.siberanka.twilight.compiler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.siberanka.twilight.config.TwilightConfig;
import com.siberanka.twilight.source.ContentInspector;
import com.siberanka.twilight.source.ContentReport;
import com.siberanka.twilight.source.ContentSource;
import com.siberanka.twilight.source.ResourceIndex;
import com.siberanka.twilight.source.SourceDiscovery;
import com.siberanka.twilight.source.WorldLayout;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Set;

/** Read-only, deterministic source audit used by local production fixtures. */
public final class SourceAuditMain {
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Instant.class, (com.google.gson.JsonSerializer<Instant>)
                    (value, type, context) -> new com.google.gson.JsonPrimitive(value.toString()))
            .setPrettyPrinting().disableHtmlEscaping().create();
    private static final int MAX_EXAMPLES = 50;

    private SourceAuditMain() { }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) throw new IllegalArgumentException("Usage: <report.json> <server-root>...");
        Path output = Path.of(args[0]).toAbsolutePath().normalize();
        JsonObject report = new JsonObject();
        report.addProperty("schema", 1);
        report.addProperty("generated_at", Instant.now().toString());
        var servers = new com.google.gson.JsonArray();
        boolean failed = false;
        for (int index = 1; index < args.length; index++) {
            JsonObject server = audit(Path.of(args[index]));
            servers.add(server);
            failed |= server.get("invalid_json").getAsInt() > 0 || server.get("unresolved_models").getAsInt() > 0 ||
                    server.get("font_problems").getAsInt() > 0;
        }
        report.add("servers", servers);
        report.addProperty("passed", !failed);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(report) + System.lineSeparator(), StandardCharsets.UTF_8);
        System.out.println("Twilight source audit: " + (failed ? "FAILED" : "PASSED") + " -> " + output);
        if (failed) System.exit(2);
    }

    private static JsonObject audit(Path rootInput) throws Exception {
        Path root = rootInput.toRealPath();
        TwilightConfig config = new TwilightConfig(false, false, false, false, 40, 100,
                2_147_483_648L, 200_000, false, true, List.of(), "auto", false, false, 3);
        Set<Path> worlds = WorldLayout.discover(root, Set.of());
        List<ContentSource> sources = new SourceDiscovery(root, config).discover(worlds);
        ContentReport content = new ContentInspector(config).inspect(sources);
        try (ResourceIndex resources = ResourceIndex.build(sources, config)) {
        JavaModelResolver models = new JavaModelResolver(resources);
        JavaItemDefinitionResolver definitions = new JavaItemDefinitionResolver(resources);
        List<String> problems = new ArrayList<>();
        int invalidJson = 0;
        for (String path : resources.paths().stream().filter(value -> value.endsWith(".json")).sorted().toList()) {
            try { JsonParser.parseString(resources.find(path).orElseThrow().readUtf8()); }
            catch (Exception failure) { invalidJson++; example(problems, path + ": " + failure.getMessage()); }
        }

        int candidates = 0, resolved = 0, unresolved = 0, modernVariants = 0;
        for (ItemCandidate candidate : new ItemCandidateCollector(resources).collect(List.of())) {
            candidates++;
            try { models.resolveAll(candidate.visualModels()); resolved++; }
            catch (Exception failure) { unresolved++; example(problems, candidate.visualModels() + ": " + failure.getMessage()); }
        }
        String itemPrefix = "assets/";
        for (String path : resources.paths().stream().filter(value -> value.startsWith(itemPrefix) && value.contains("/items/") && value.endsWith(".json")).sorted().toList()) {
            int items = path.indexOf("/items/");
            String identifier = path.substring("assets/".length(), items) + ':' +
                    path.substring(items + "/items/".length(), path.length() - ".json".length());
            try {
                for (JavaItemDefinitionResolver.Variant variant : definitions.resolve(identifier)) {
                    modernVariants++;
                    try { models.resolveAll(variant.visualModels()); resolved++; }
                    catch (Exception failure) { unresolved++; example(problems, identifier + " -> " + variant.visualModels() + ": " + failure.getMessage()); }
                }
            } catch (Exception failure) {
                unresolved++;
                example(problems, identifier + ": " + failure.getMessage());
            }
        }
        BitmapFontCompiler.Result fonts = new BitmapFontCompiler(resources, null).compile(new LinkedHashMap<>());
        fonts.problems().forEach(problem -> example(problems, "font: " + problem));

        JsonObject result = new JsonObject();
        result.addProperty("root", root.toString());
        result.add("worlds", GSON.toJsonTree(worlds.stream().map(Path::toString).sorted().toList()));
        result.add("content", GSON.toJsonTree(content));
        result.addProperty("indexed_assets", resources.paths().size());
        result.addProperty("legacy_candidates", candidates);
        result.addProperty("modern_variants", modernVariants);
        result.addProperty("resolved_models", resolved);
        result.addProperty("unresolved_models", unresolved);
        result.addProperty("invalid_json", invalidJson);
        result.addProperty("font_glyphs", fonts.glyphs());
        result.addProperty("font_pages", fonts.pages());
        result.addProperty("named_fonts", fonts.namedFonts());
        result.addProperty("named_font_glyphs", fonts.namedGlyphs());
        result.addProperty("font_problems", fonts.problems().size());
        result.add("font_problem_examples", GSON.toJsonTree(fonts.problems().stream().limit(MAX_EXAMPLES).toList()));
        result.add("problem_examples", GSON.toJsonTree(problems));
        return result;
        }
    }

    private static void example(List<String> problems, String value) {
        if (problems.size() < MAX_EXAMPLES) problems.add(value);
    }
}
