package com.siberanka.twilight.conflicts;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MappingOverlapsTest {
    @TempDir Path root;

    private static final String TWILIGHT = """
            {"format_version":2,"items":{"minecraft:paper":[
              {"type":"legacy","custom_model_data":10000,"bedrock_identifier":"twilight:paper_10000"},
              {"type":"legacy","custom_model_data":10001,"bedrock_identifier":"twilight:paper_10001"}],
             "minecraft:stick":[{"type":"definition","model":"shop:wand","bedrock_identifier":"twilight:wand"}]}}""";

    private Path geyser() throws IOException {
        Path geyser = root.resolve("plugins/Geyser-Spigot");
        write(geyser.resolve("custom_mappings/twilight_item_mappings.json"), TWILIGHT);
        return geyser;
    }

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    private static void pack(Path file, String uuid) throws IOException {
        Files.createDirectories(file.getParent());
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write(("{\"header\":{\"name\":\"x\",\"uuid\":\"" + uuid + "\"}}").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }

    @Test
    void java2BedrockStyleMappingOnTheSameCustomModelDataIsFound() throws Exception {
        Path geyser = geyser();
        write(geyser.resolve("custom_mappings/geyser_mappings.json"),
                "{\"format_version\":1,\"items\":{\"minecraft:paper\":[{\"name\":\"a\",\"custom_model_data\":10000},{\"name\":\"b\",\"custom_model_data\":77}]}}");
        write(geyser.resolve("custom_mappings/unrelated.json"),
                "{\"format_version\":1,\"items\":{\"minecraft:paper\":[{\"name\":\"c\",\"custom_model_data\":5}]}}");
        var result = MappingOverlaps.scan(geyser, ConflictSettings.DEFAULTS);
        assertEquals(1, result.overlaps().size());
        assertEquals("custom_mappings/geyser_mappings.json", result.overlaps().get(0).file());
        assertEquals(List.of("minecraft:paper|cmd=10000"), result.overlaps().get(0).examples());
        assertEquals(1, result.findings().size());
        assertTrue(result.findings().get(0).text().contains("foreign-mappings: retire"));
    }

    @Test
    void definitionMappingsClashByItemModel() throws Exception {
        Path geyser = geyser();
        write(geyser.resolve("custom_mappings/nexo.json"),
                "{\"format_version\":2,\"items\":{\"minecraft:stick\":[{\"type\":\"definition\",\"model\":\"shop:wand\",\"bedrock_identifier\":\"geyser_custom:wand\"}]}}");
        assertEquals(1, MappingOverlaps.scan(geyser, ConflictSettings.DEFAULTS).overlaps().size());
    }

    @Test
    void noticesAreBoundedAndTheRestIsSummedUp() throws Exception {
        Path geyser = geyser();
        for (int i = 0; i < 7; i++) {
            write(geyser.resolve("custom_mappings/other" + i + ".json"),
                    "{\"items\":{\"minecraft:paper\":[{\"custom_model_data\":10001}]}}");
        }
        var settings = new ConflictSettings(true, Owner.AUTO, Owner.AUTO, ConflictSettings.Foreign.REPORT, 3);
        var result = MappingOverlaps.scan(geyser, settings);
        assertEquals(7, result.overlaps().size());
        assertEquals(4, result.findings().size(), "three notices and one summary");
        assertTrue(result.findings().get(3).summary().startsWith("4 more"));
    }

    @Test
    void malformedOversizedAndMissingFilesNeverThrow() throws Exception {
        Path geyser = geyser();
        write(geyser.resolve("custom_mappings/broken.json"), "{ not json");
        write(geyser.resolve("custom_mappings/array.json"), "[1,2,3]");
        write(geyser.resolve("custom_mappings/odd.json"), "{\"items\":{\"minecraft:paper\":[1,\"x\",null,{\"custom_model_data\":{}}]}}");
        assertTrue(MappingOverlaps.scan(geyser, ConflictSettings.DEFAULTS).overlaps().isEmpty());
        assertTrue(MappingOverlaps.scan(root.resolve("missing"), ConflictSettings.DEFAULTS).findings().isEmpty());
        assertTrue(MappingOverlaps.scan(null, ConflictSettings.DEFAULTS).findings().isEmpty());
    }

    @Test
    void withoutTwilightMappingsYetNothingOverlaps() throws Exception {
        Path geyser = root.resolve("g");
        write(geyser.resolve("custom_mappings/a.json"), "{\"items\":{\"minecraft:paper\":[{\"custom_model_data\":10000}]}}");
        assertTrue(MappingOverlaps.scan(geyser, ConflictSettings.DEFAULTS).overlaps().isEmpty());
    }

    @Test
    void offAndModeOffSkipTheScan() throws Exception {
        Path geyser = geyser();
        write(geyser.resolve("custom_mappings/a.json"), "{\"items\":{\"minecraft:paper\":[{\"custom_model_data\":10000}]}}");
        assertTrue(MappingOverlaps.scan(geyser, new ConflictSettings(true, Owner.AUTO, Owner.AUTO, ConflictSettings.Foreign.OFF, 10)).overlaps().isEmpty());
        assertTrue(MappingOverlaps.scan(geyser, new ConflictSettings(false, Owner.AUTO, Owner.AUTO, ConflictSettings.Foreign.REPORT, 10)).overlaps().isEmpty());
    }

    @Test
    void retireMovesOnlyOverlappingFilesAndKeepsThemRecoverable() throws Exception {
        Path geyser = geyser();
        write(geyser.resolve("custom_mappings/clash.json"), "{\"items\":{\"minecraft:paper\":[{\"custom_model_data\":10000}]}}");
        write(geyser.resolve("custom_mappings/fine.json"), "{\"items\":{\"minecraft:paper\":[{\"custom_model_data\":1}]}}");
        var settings = new ConflictSettings(true, Owner.AUTO, Owner.AUTO, ConflictSettings.Foreign.RETIRE, 10);
        var result = MappingOverlaps.scan(geyser, settings);
        Path retired = root.resolve("plugins/Twilight/retired");
        List<String> moved = MappingOverlaps.retire(geyser, result.overlaps(), retired);
        assertEquals(1, moved.size());
        assertFalse(Files.exists(geyser.resolve("custom_mappings/clash.json")));
        assertTrue(Files.exists(geyser.resolve("custom_mappings/fine.json")));
        assertTrue(Files.exists(geyser.resolve("custom_mappings/twilight_item_mappings.json")), "Twilight's own file stays");
        try (var found = Files.walk(retired)) {
            assertEquals(1, found.filter(path -> path.getFileName().toString().equals("clash.json")).count());
        }
        assertTrue(MappingOverlaps.scan(geyser, settings).overlaps().isEmpty(), "a second start finds nothing");
    }

    @Test
    void retireRefusesPathsOutsideCustomMappings() throws Exception {
        Path geyser = geyser();
        write(geyser.resolve("config.yml"), "x: 1");
        var overlap = new MappingOverlaps.Overlap("custom_mappings/../config.yml", 1, List.of());
        assertTrue(MappingOverlaps.retire(geyser, List.of(overlap), root.resolve("plugins/Twilight/retired")).isEmpty());
        assertTrue(Files.exists(geyser.resolve("config.yml")));
    }

    @Test
    void aCopyOfTwilightsPackUnderAnotherNameClashes() throws Exception {
        Path geyser = geyser();
        pack(geyser.resolve("packs/twilight.zip"), "AAAA-1");
        pack(geyser.resolve("packs/backup-copy.zip"), "aaaa-1");
        pack(geyser.resolve("packs/other.zip"), "BBBB-2");
        var findings = MappingOverlaps.scan(geyser, ConflictSettings.DEFAULTS).findings();
        assertEquals(1, findings.size());
        assertTrue(findings.get(0).summary().contains("backup-copy.zip"));
    }

    @Test
    void geyserFolderReadsExtensionsAndOptions() throws Exception {
        Path geyser = root.resolve("Geyser");
        Files.createDirectories(geyser.resolve("extensions"));
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(geyser.resolve("extensions/gme-1.0.9.jar")))) {
            zip.putNextEntry(new ZipEntry("extension.yml"));
            zip.write("name: GeyserModelEngineExtension\nid: geysermodelengineextension\nmain: x\n".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        Files.writeString(geyser.resolve("extensions/broken.jar"), "not a zip");
        Files.writeString(geyser.resolve("config.yml"), "bedrock:\n  port: 1\nenable-custom-content: false\nadd-non-bedrock-items: true\n");
        Set<String> found = GeyserFolder.extensions(geyser);
        assertTrue(found.contains("geysermodelengineextension"));
        assertTrue(found.contains("broken"), "falls back to the file name");
        assertEquals(java.util.Optional.of(false), GeyserFolder.option(geyser, "enable-custom-content"));
        assertEquals(java.util.Optional.of(true), GeyserFolder.option(geyser, "add-non-bedrock-items"));
        assertEquals(java.util.Optional.empty(), GeyserFolder.option(geyser, "missing"));
        var env = new Environment(false, "Paper", Set.of("floodgate"), found, true, "Geyser", false, 0, false);
        assertTrue(Diagnostics.geyserFacts(env, geyser).stream().anyMatch(f -> f.id().equals("geyser-custom-content")));
        assertEquals(Set.of(), GeyserFolder.extensions(root.resolve("nowhere")));
    }

    @Test
    void theReportListsLayersWithReasonsAndFindings() {
        var env = new Environment(false, "Paper 1.21.4 (Leaf)", Set.of("ViaVersion"), Set.of("geysermodelengineextension"), true, "Geyser 2.11", true, 0, false);
        var settings = ConflictSettings.DEFAULTS;
        var assessment = Conflicts.assess(env, settings);
        var lines = Diagnostics.render("Twilight test", env, assessment,
                List.of(Diagnostics.Layer.on("compiler", "builds"), Diagnostics.Layer.off("text", "off (ui.java-text-layout)")), List.of(), settings);
        String report = String.join("\n", lines);
        assertTrue(report.contains("[on ] compiler"));
        assertTrue(report.contains("[off] text - off (ui.java-text-layout)"));
        assertTrue(report.contains("regionised"));
        assertTrue(report.contains("Floodgate: not installed"));
        assertTrue(report.contains("Via: installed"));
        assertTrue(report.contains("fork: leaf"));
        assertTrue(report.contains("[INFO] GeyserModelEngine renders"));
    }
}
