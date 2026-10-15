package com.siberanka.twilight.report;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.siberanka.twilight.source.ContentReport;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes {@code reports/content-report.json}. The JSON is built field by field instead of through Gson's
 * reflection: the Gson that servers bundle (2.11 on Paper) cannot reflect into {@code java.time} on Java 17
 * and later, which made every scan fail.
 */
public final class ContentReportWriter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private ContentReportWriter() {}

    public static void write(Path path, ContentReport report) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
            GSON.toJson(json(report), writer);
        }
        try {
            Files.move(temporary, path, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static JsonObject json(ContentReport report) {
        JsonObject root = new JsonObject();
        root.addProperty("scannedAt", report.scannedAt().toString());
        root.addProperty("sources", report.sources());
        root.addProperty("sourceBytes", report.sourceBytes());
        root.addProperty("entries", report.entries());
        root.addProperty("textures", report.textures());
        root.addProperty("itemDefinitions", report.itemDefinitions());
        root.addProperty("legacyItemModels", report.legacyItemModels());
        root.addProperty("modelJson", report.modelJson());
        root.addProperty("blockStates", report.blockStates());
        root.addProperty("fontDefinitions", report.fontDefinitions());
        root.addProperty("sounds", report.sounds());
        root.addProperty("bbmodels", report.bbmodels());
        root.addProperty("biomeDefinitions", report.biomeDefinitions());
        JsonArray paths = new JsonArray();
        report.sourcePaths().forEach(paths::add);
        root.add("sourcePaths", paths);
        JsonObject providers = new JsonObject();
        report.providers().forEach(providers::addProperty);
        root.add("providers", providers);
        return root;
    }
}
