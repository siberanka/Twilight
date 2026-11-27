/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.conflicts;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Detects other Bedrock content tools that would show the same thing as Twilight, and decides who does.
 *
 * <p>Two kinds of content can be shown twice: ModelEngine and BetterModel models (bones) and Java display entities.
 * For each, {@link Owner#AUTO} lets Twilight yield to a specialised renderer that is installed (it was installed on
 * purpose) and render itself otherwise. Nothing here changes another tool's files or settings.
 */
public final class Conflicts {
    /** Geyser extension ids and names that render ModelEngine/BetterModel models as Bedrock custom entities. */
    /**
     * What {@code model-bones: auto} does when another extension renders the models. The single place to change
     * when Twilight gets its own entity render mode for them and should be preferred.
     */
    static final boolean AUTO_YIELDS_MODEL_BONES = true;
    static final List<String> MODEL_RENDERERS = List.of("geysermodelengineextension", "geysermodelengine");
    /** Geyser extensions that register Geyser's item and block display entities (name fragments). */
    static final List<String> DISPLAY_RENDERERS = List.of("geyserdisplayentity", "scaffolding");
    /** Plugins that also send resource packs or mappings to Geyser (name fragments). */
    static final List<String> PACK_SENDERS = List.of("bedrockbundler", "rainbow", "geyserpacksync");

    /** How serious a finding is: INFO is resolved or harmless, WARN needs the administrator's attention. */
    public enum Level { INFO, WARN }

    /** One thing the administrator should know: what was found, what Twilight did, and how to change it. */
    public record Finding(String id, Level level, String summary, String fix) {
        public String text() {
            return summary + (fix == null || fix.isBlank() ? "" : " " + fix);
        }
    }

    /**
     * The outcome: findings to log or show, and the two decisions the runtime applies.
     *
     * @param showModelBones   whether Twilight's display bridge shows ModelEngine/BetterModel bones
     * @param registerDisplays whether Twilight's display bridge registers Geyser's display entities at all
     */
    public record Assessment(List<Finding> findings, boolean showModelBones, boolean registerDisplays) {
        public static final Assessment NONE = new Assessment(List.of(), true, true);
    }

    private Conflicts() {}

    public static Assessment assess(Environment env, ConflictSettings settings) {
        if (!settings.enabled()) return Assessment.NONE;
        Map<String, Finding> findings = new LinkedHashMap<>();
        boolean modelRenderer = MODEL_RENDERERS.stream().anyMatch(env::hasExtension);
        boolean modelPlugin = env.hasPlugin("geysermodelengine");
        boolean displayRenderer = DISPLAY_RENDERERS.stream().anyMatch(part -> env.extensions().stream().anyMatch(id -> id.contains(part)));

        boolean showBones = true;
        if (modelRenderer) {
            boolean yield = settings.modelBones() == Owner.OTHER || (settings.modelBones() == Owner.AUTO && AUTO_YIELDS_MODEL_BONES);
            if (settings.modelBones() == Owner.TWILIGHT) {
                add(findings, new Finding("model-bones", Level.WARN,
                        "GeyserModelEngine renders ModelEngine/BetterModel models on Bedrock, and Twilight is set to show them too"
                                + " (integrations.conflicts.model-bones: twilight): Bedrock players see every model twice.",
                        "Remove GeyserModelEngine and its Geyser extension, or set integrations.conflicts.model-bones to auto."));
            } else {
                add(findings, new Finding("model-bones", Level.INFO,
                        "GeyserModelEngine renders ModelEngine/BetterModel models on Bedrock: Twilight's item-display bridge leaves"
                                + " their bones to it, so each model shows once.",
                        "To use Twilight's display models instead, remove GeyserModelEngine and set integrations.conflicts.model-bones: twilight."));
            }
            showBones = !yield;
        } else if (settings.modelBones() == Owner.OTHER) {
            showBones = false;
            add(findings, new Finding("model-bones", Level.WARN,
                    "integrations.conflicts.model-bones is other, but no GeyserModelEngine extension was found: ModelEngine and"
                            + " BetterModel models are not shown on Bedrock.",
                    "Install the extension, or set integrations.conflicts.model-bones to auto."));
        }
        if (modelPlugin && !modelRenderer && !env.proxy() && env.geyserHere()) {
            add(findings, new Finding("model-extension-missing", Level.WARN,
                    "The GeyserModelEngine plugin is installed but Geyser has no GeyserModelEngineExtension, so it shows no model.",
                    "Put GeyserModelEngineExtension (and geyserutils-geyser) into Geyser's extensions folder, or remove the plugin."));
        }
        if (modelPlugin && !env.proxy() && !env.hasPlugin("geyserutils")) {
            add(findings, new Finding("geyserutils-missing", Level.WARN,
                    "The GeyserModelEngine plugin needs the GeyserUtils plugin, which is not installed.",
                    "Install GeyserUtils or remove GeyserModelEngine."));
        }

        boolean register = true;
        if (displayRenderer) {
            if (settings.displayEntities() == Owner.TWILIGHT) {
                add(findings, new Finding("display-entities", Level.WARN,
                        "Another Geyser extension (GeyserDisplayEntity or ScaffoldingExtension) registers Java display entities too,"
                                + " and Twilight is set to register them (integrations.conflicts.display-entities: twilight):"
                                + " Geyser lets only one translator own item_display, so which one works depends on the load order.",
                        "Remove the other extension, or set integrations.conflicts.display-entities to auto."));
            } else {
                register = false;
                add(findings, new Finding("display-entities", Level.INFO,
                        "Another Geyser extension (GeyserDisplayEntity or ScaffoldingExtension) renders Java display entities:"
                                + " Twilight's display bridge stays off, so furniture and model bones are shown by one translator.",
                        "To use Twilight's display bridge instead, remove that extension and set integrations.conflicts.display-entities: twilight."));
            }
        } else if (settings.displayEntities() == Owner.OTHER) {
            register = false;
            add(findings, new Finding("display-entities", Level.WARN,
                    "integrations.conflicts.display-entities is other, but no other display extension was found: furniture and"
                            + " model bones are invisible to Bedrock players.",
                    "Set integrations.conflicts.display-entities to auto."));
        }

        for (String sender : PACK_SENDERS) {
            if (env.hasPlugin(sender)) {
                add(findings, new Finding("pack-sender-" + sender, Level.WARN,
                        "The " + sender + " tool also sends resource packs or mappings to Geyser: two pack senders give Bedrock players"
                                + " duplicate or conflicting content.",
                        "Use one pack sender. See docs/MIGRATION.md; Twilight's overlap check below lists clashing mappings."));
            }
        }

        if (env.geyserHere() && env.proxyShowsDisplays() && !env.proxy()) {
            add(findings, new Finding("two-geysers", Level.INFO,
                    "Geyser runs on this server and twilight-proxy reports a Geyser on the proxy that also shows item displays."
                            + " Bedrock players connect to only one of them; the other one's Twilight runtime stays idle.",
                    "Normal for a test setup; in production run Geyser in one place."));
        }
        return new Assessment(List.copyOf(findings.values()), showBones, register);
    }

    /** A finding when the two Twilight halves differ in major or minor version; null when they match or the peer is unknown. */
    public static Finding versionMismatch(int ownCode, int peerCode, boolean ownIsProxy) {
        if (peerCode <= 0 || ownCode <= 0) return null;
        if (ownCode / 100 == peerCode / 100) return null;
        String own = Environment.describe(ownCode);
        String peer = Environment.describe(peerCode);
        return new Finding("version-mismatch", Level.WARN,
                (ownIsProxy ? "twilight-proxy " + own + " and a Twilight backend " + peer : "Twilight " + own + " and twilight-proxy " + peer)
                        + " differ in minor version: their messages are compatible, but newer features may only work on one side.",
                "Update both to the same release.");
    }

    private static void add(Map<String, Finding> findings, Finding finding) {
        findings.putIfAbsent(finding.id(), finding);
    }

    /** The findings as a mutable list for callers that add more (overlaps, version checks). */
    public static List<Finding> merge(List<Finding> first, List<Finding> second) {
        List<Finding> all = new ArrayList<>(first);
        all.addAll(second);
        return all;
    }
}
