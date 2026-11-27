package com.siberanka.twilight.conflicts;

import com.siberanka.twilight.conflicts.Conflicts.Assessment;
import com.siberanka.twilight.conflicts.Conflicts.Finding;
import com.siberanka.twilight.conflicts.Conflicts.Level;
import com.siberanka.twilight.protocol.PackChannel;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConflictsTest {
    private static Environment backend(Set<String> plugins, Set<String> extensions) {
        return new Environment(false, "Paper 1.21.4", plugins, extensions, true, "Geyser-Spigot 2.11", false, 0, false);
    }

    private static Finding find(Assessment assessment, String id) {
        return assessment.findings().stream().filter(finding -> finding.id().equals(id)).findFirst().orElse(null);
    }

    @Test
    void nothingInstalledMeansTwilightShowsEverything() {
        Assessment assessment = Conflicts.assess(backend(Set.of("Geyser-Spigot", "floodgate"), Set.of()), ConflictSettings.DEFAULTS);
        assertTrue(assessment.findings().isEmpty());
        assertTrue(assessment.showModelBones());
        assertTrue(assessment.registerDisplays());
    }

    @Test
    void autoLeavesModelBonesToGeyserModelEngineAndSaysSo() {
        Assessment assessment = Conflicts.assess(backend(Set.of("GeyserModelEngine", "GeyserUtils", "ModelEngine"),
                Set.of("GeyserModelEngineExtension", "geysermodelengineextension", "geyserutils")), ConflictSettings.DEFAULTS);
        assertFalse(assessment.showModelBones(), "one owner per model: GeyserModelEngine");
        assertTrue(assessment.registerDisplays(), "furniture displays are not affected");
        Finding finding = find(assessment, "model-bones");
        assertNotNull(finding);
        assertEquals(Level.INFO, finding.level());
        assertTrue(finding.text().contains("integrations.conflicts.model-bones"), "names the setting that changes it");
    }

    @Test
    void forcingTwilightWithGeyserModelEngineWarnsAboutDoubleModels() {
        var settings = new ConflictSettings(true, Owner.TWILIGHT, Owner.AUTO, ConflictSettings.Foreign.REPORT, 10);
        Assessment assessment = Conflicts.assess(backend(Set.of("GeyserModelEngine", "GeyserUtils"),
                Set.of("geysermodelengineextension")), settings);
        assertTrue(assessment.showModelBones());
        Finding finding = find(assessment, "model-bones");
        assertEquals(Level.WARN, finding.level());
        assertTrue(finding.summary().contains("twice"));
    }

    @Test
    void otherWithoutAnyRendererWarnsThatModelsAreInvisible() {
        var settings = new ConflictSettings(true, Owner.OTHER, Owner.AUTO, ConflictSettings.Foreign.REPORT, 10);
        Assessment assessment = Conflicts.assess(backend(Set.of(), Set.of()), settings);
        assertFalse(assessment.showModelBones());
        assertEquals(Level.WARN, find(assessment, "model-bones").level());
    }

    @Test
    void pluginWithoutItsGeyserExtensionOrUtilsIsReported() {
        Assessment assessment = Conflicts.assess(backend(Set.of("GeyserModelEngine"), Set.of()), ConflictSettings.DEFAULTS);
        assertTrue(assessment.showModelBones(), "nothing renders the models, so Twilight does");
        assertNotNull(find(assessment, "model-extension-missing"));
        assertNotNull(find(assessment, "geyserutils-missing"));
    }

    @Test
    void anotherDisplayExtensionKeepsTwilightsBridgeOff() {
        for (String extension : new String[]{"geyserdisplayentity", "scaffoldingextension"}) {
            Assessment assessment = Conflicts.assess(backend(Set.of("Nexo"), Set.of(extension)), ConflictSettings.DEFAULTS);
            assertFalse(assessment.registerDisplays(), extension);
            assertEquals(Level.INFO, find(assessment, "display-entities").level());
        }
    }

    @Test
    void aScaffoldingPluginAloneDoesNotSwitchTheBridgeOff() {
        Assessment assessment = Conflicts.assess(backend(Set.of("Nexo", "Scaffolding"), Set.of()), ConflictSettings.DEFAULTS);
        assertTrue(assessment.registerDisplays(), "only a Geyser extension can own the display entities");
    }

    @Test
    void displayEntitiesTwilightWarnsAboutTheRace() {
        var settings = new ConflictSettings(true, Owner.AUTO, Owner.TWILIGHT, ConflictSettings.Foreign.REPORT, 10);
        Assessment assessment = Conflicts.assess(backend(Set.of(), Set.of("geyserdisplayentity")), settings);
        assertTrue(assessment.registerDisplays());
        assertEquals(Level.WARN, find(assessment, "display-entities").level());
    }

    @Test
    void modeOffDetectsNothing() {
        var settings = new ConflictSettings(false, Owner.AUTO, Owner.AUTO, ConflictSettings.Foreign.REPORT, 10);
        Assessment assessment = Conflicts.assess(backend(Set.of("GeyserModelEngine"), Set.of("geysermodelengineextension", "geyserdisplayentity")), settings);
        assertTrue(assessment.findings().isEmpty());
        assertTrue(assessment.showModelBones());
        assertTrue(assessment.registerDisplays());
    }

    @Test
    void otherPackSendersAreNamed() {
        Assessment assessment = Conflicts.assess(backend(Set.of("BedrockBundler"), Set.of()), ConflictSettings.DEFAULTS);
        assertEquals(Level.WARN, find(assessment, "pack-sender-bedrockbundler").level());
    }

    @Test
    void twoGeysersAreAnInformationNotAnError() {
        var env = new Environment(false, "Paper", Set.of(), Set.of(), true, "Geyser", false, 10002, true);
        assertEquals(Level.INFO, find(Conflicts.assess(env, ConflictSettings.DEFAULTS), "two-geysers").level());
    }

    @Test
    void proxyWithTheExtensionYieldsToo() {
        var env = new Environment(true, "Velocity", Set.of("geyserutils"), Set.of("geysermodelengineextension"), true, "Geyser", false, 0, false);
        assertFalse(Conflicts.assess(env, ConflictSettings.DEFAULTS).showModelBones());
    }

    @Test
    void versionsOfTheTwoHalvesAreComparedByMinor() {
        assertNull(Conflicts.versionMismatch(10002, 10005, false), "patch differences are fine");
        assertNull(Conflicts.versionMismatch(10002, 0, false), "an older proxy says nothing");
        Finding finding = Conflicts.versionMismatch(10002, 10100, false);
        assertNotNull(finding);
        assertTrue(finding.text().contains("1.0.2") && finding.text().contains("1.1.0"));
    }

    @Test
    void versionCodesRoundTrip() {
        assertEquals(10002, Environment.versionCode("1.0.2-pre.3"));
        assertEquals(10100, Environment.versionCode("1.1"));
        assertEquals(0, Environment.versionCode("garbage"));
        assertEquals("1.0.2", Environment.describe(10002));
        int flags = PackChannel.withVersion(PackChannel.ITEM_DISPLAYS, 10002);
        assertEquals(10002, PackChannel.versionOf(flags));
        assertTrue(new PackChannel.Capabilities(0, flags).has(PackChannel.ITEM_DISPLAYS), "old readers still see the feature bit");
        assertEquals(0, PackChannel.versionOf(PackChannel.ITEM_DISPLAYS), "a proxy without a version announces 0");
    }

    @Test
    void settingsParseAndRejectInvalidValues() {
        assertEquals(ConflictSettings.DEFAULTS, ConflictSettings.parse(key -> null));
        var parsed = ConflictSettings.parse(Map.<String, Object>of("mode", "off", "model-bones", "OTHER", "foreign-mappings", "retire",
                "max-notices", 3)::get);
        assertFalse(parsed.enabled());
        assertEquals(Owner.OTHER, parsed.modelBones());
        assertEquals(ConflictSettings.Foreign.RETIRE, parsed.foreignMappings());
        assertEquals(3, parsed.maxNotices());
        assertThrows(IllegalArgumentException.class, () -> ConflictSettings.parse(Map.<String, Object>of("model-bones", "both")::get));
        assertThrows(IllegalArgumentException.class, () -> ConflictSettings.parse(Map.<String, Object>of("max-notices", 0)::get));
        assertThrows(IllegalArgumentException.class, () -> ConflictSettings.parse(Map.<String, Object>of("foreign-mappings", "delete")::get));
    }
}
