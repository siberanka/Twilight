/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.conflicts;

import com.siberanka.twilight.conflicts.Conflicts.Finding;
import com.siberanka.twilight.conflicts.Conflicts.Level;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The startup capability probe: which Twilight layers run and why the others do not, plus the conflict findings. */
public final class Diagnostics {
    /** One runtime layer: running, or off with the reason. */
    public record Layer(String name, boolean active, String reason) {
        public static Layer on(String name, String detail) { return new Layer(name, true, detail); }
        public static Layer off(String name, String reason) { return new Layer(name, false, reason); }
    }

    private Diagnostics() {}

    /**
     * The facts about Geyser that switch layers off: {@code enable-custom-content: false} (no custom items or
     * blocks), and a Floodgate-less Geyser. Reads Geyser's own config; unknown values are not reported.
     */
    public static List<Finding> geyserFacts(Environment env, Path geyserFolder) {
        List<Finding> facts = new ArrayList<>();
        if (!env.geyserHere()) return facts;
        if (GeyserFolder.option(geyserFolder, "enable-custom-content").orElse(true) == Boolean.FALSE) {
            facts.add(new Finding("geyser-custom-content", Level.WARN,
                    "Geyser's enable-custom-content is false: custom items, blocks and Twilight's pack do not reach Bedrock players.",
                    "Set enable-custom-content: true in Geyser's config.yml and restart."));
        }
        if (GeyserFolder.option(geyserFolder, "add-non-bedrock-items").orElse(true) == Boolean.FALSE) {
            facts.add(new Finding("geyser-non-bedrock-items", Level.INFO,
                    "Geyser's add-non-bedrock-items is false: Java-only items have no Bedrock counterpart, so Twilight's"
                            + " custom items that rely on one show as the base item.",
                    "Set add-non-bedrock-items: true in Geyser's config.yml if items look wrong."));
        }
        return facts;
    }

    /** Platform notes: Folia, Via, Floodgate, forks. Not conflicts, but they explain behaviour. */
    public static List<String> platformNotes(Environment env) {
        List<String> notes = new ArrayList<>();
        notes.add("platform: " + env.platform() + (env.folia() ? " (regionised threading: Twilight schedules per region)" : ""));
        notes.add("Geyser: " + (env.geyserHere() ? (env.geyserVersion().isEmpty() ? "present" : env.geyserVersion()) : "not in this process"));
        boolean floodgate = env.hasPlugin("floodgate");
        notes.add("Floodgate: " + (floodgate ? "installed" : "not installed (Bedrock players are matched through Geyser only)"));
        if (env.hasPlugin("viaversion") || env.hasPlugin("viabackwards") || env.hasPlugin("viarewind") || env.hasPlugin("viaproxy")) {
            notes.add("Via: installed (older Java clients see translated items; Bedrock content is not affected)");
        }
        String lower = env.platform().toLowerCase(Locale.ROOT);
        for (String fork : List.of("purpur", "leaf", "pufferfish", "folia")) {
            if (lower.contains(fork)) notes.add("fork: " + fork + " (supported)");
        }
        return notes;
    }

    /** The full report, one line each (for the console, a command, or a file). */
    public static List<String> render(String title, Environment env, Conflicts.Assessment assessment, List<Layer> layers,
                                      List<Finding> extra, ConflictSettings settings) {
        List<String> lines = new ArrayList<>();
        lines.add(title);
        platformNotes(env).forEach(note -> lines.add("  " + note));
        lines.add("Layers:");
        for (Layer layer : layers) lines.add("  [" + (layer.active() ? "on " : "off") + "] " + layer.name() + " - " + layer.reason());
        lines.add("Conflict handling: " + (settings.enabled() ? "auto" : "off") + " (model-bones=" + name(settings.modelBones())
                + ", display-entities=" + name(settings.displayEntities()) + ", foreign-mappings=" + settings.foreignMappings().name().toLowerCase(Locale.ROOT)
                + "); Twilight shows model bones: " + assessment.showModelBones() + ", registers displays: " + assessment.registerDisplays());
        List<Finding> all = Conflicts.merge(assessment.findings(), extra);
        if (all.isEmpty()) lines.add("Findings: none.");
        else {
            lines.add("Findings:");
            for (Finding finding : all) lines.add("  [" + finding.level().name() + "] " + finding.text());
        }
        return lines;
    }

    private static String name(Owner owner) {
        return owner.name().toLowerCase(Locale.ROOT);
    }
}
