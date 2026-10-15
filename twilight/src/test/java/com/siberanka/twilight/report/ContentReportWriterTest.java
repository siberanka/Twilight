package com.siberanka.twilight.report;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.siberanka.twilight.source.ContentReport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ContentReportWriterTest {
    /**
     * Gson 2.11 (Paper's bundled version) cannot reach into java.time on Java 17+; the report must not depend
     * on reflection into JDK classes, or every scan fails with "module java.base does not opens java.time".
     */
    @Test
    void writesTheReportWithoutReflectingIntoTheJdk(@TempDir Path folder) throws Exception {
        Map<String, Integer> providers = new LinkedHashMap<>();
        providers.put("itemsadder", 3);
        providers.put("nexo", 1);
        ContentReport report = new ContentReport(Instant.parse("2026-10-09T10:15:30.123456789Z"), 4, 1234L, 50, 20,
                7, 2, 30, 1, 3, 5, 6, 2, List.of("plugins/ItemsAdder/output/generated.zip", "world/datapacks/x"),
                providers);
        Path file = folder.resolve("reports/content-report.json");
        ContentReportWriter.write(file, report);

        JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("2026-10-09T10:15:30.123456789Z", json.get("scannedAt").getAsString());
        assertEquals(4, json.get("sources").getAsInt());
        assertEquals(1234L, json.get("sourceBytes").getAsLong());
        assertEquals(6, json.get("bbmodels").getAsInt());
        assertEquals(2, json.get("biomeDefinitions").getAsInt());
        assertEquals("world/datapacks/x", json.getAsJsonArray("sourcePaths").get(1).getAsString());
        assertEquals(3, json.getAsJsonObject("providers").get("itemsadder").getAsInt());
        assertFalse(Files.exists(folder.resolve("reports/content-report.json.tmp")));

        // Rewriting replaces the previous report.
        ContentReportWriter.write(file, new ContentReport(Instant.EPOCH, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, List.of(), Map.of()));
        json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("1970-01-01T00:00:00Z", json.get("scannedAt").getAsString());
        assertEquals(0, json.getAsJsonArray("sourcePaths").size());
    }

    /**
     * Floodgate bundles its own copy of Geyser's event library; Twilight's classes must not refer to it, or a
     * server that loads Floodgate first fails with "loader constraint violation" when subscribing to Geyser.
     */
    @Test
    void noClassLinksAgainstGeyserEventLibrary() throws Exception {
        Path classes = Path.of(ContentReportWriter.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        List<String> offenders = new java.util.ArrayList<>();
        try (var files = Files.walk(classes)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".class")).toList()) {
                String content = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
                if (content.contains("org/geysermc/event/")) offenders.add(classes.relativize(file).toString());
            }
        }
        assertTrue(Files.isRegularFile(classes.resolve("com/siberanka/twilight/geyser/GeyserEvents.class")));
        assertEquals(List.of(), offenders);
    }
}
