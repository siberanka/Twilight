package com.siberanka.twilight.compiler;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Which items get display models: what this server shows in item displays, and model bones. */
class DisplayedItemSelectionTest {
    private static ItemCandidate legacy(int cmd) {
        return new ItemCandidate("minecraft:paper", "demo:item/chair", OptionalInt.of(cmd), "legacy-pack");
    }

    private static ItemCandidate definition(String model, String provider, String predicates) {
        JsonArray array = JsonParser.parseString(predicates).getAsJsonArray();
        return new ItemCandidate("minecraft:paper", model, List.of(model), OptionalInt.empty(), provider, array, 0, List.of());
    }

    @Test void legacyItemsMatchTheirCustomModelData() {
        assertEquals(List.of("minecraft:paper|cmd=7"), BedrockPackCompiler.displayKeys(legacy(7)));
        assertTrue(BedrockPackCompiler.displayed(legacy(7), Set.of("minecraft:paper|cmd=7")));
        assertFalse(BedrockPackCompiler.displayed(legacy(8), Set.of("minecraft:paper|cmd=7")));
        assertTrue(BedrockPackCompiler.displayed(legacy(8), null), "without a list every item is shown");
    }

    @Test void itemModelsAndDispatchedCustomModelDataMatch() {
        ItemCandidate nexo = definition("nexo:chair", "Nexo", "[]");
        assertTrue(BedrockPackCompiler.displayed(nexo, Set.of("model=nexo:chair")));
        assertFalse(BedrockPackCompiler.displayed(nexo, Set.of("model=nexo:table")));
        ItemCandidate dispatched = definition("minecraft:paper", "ItemsAdder",
                "[{\"type\":\"range_dispatch\",\"property\":\"minecraft:custom_model_data\",\"threshold\":10001.0}]");
        assertTrue(BedrockPackCompiler.displayed(dispatched, Set.of("minecraft:paper|cmd=10001")));
        ItemCandidate selected = definition("minecraft:paper", "Nexo",
                "[{\"type\":\"match\",\"property\":\"minecraft:custom_model_data\",\"value\":\"lamp\"}]");
        assertTrue(BedrockPackCompiler.displayed(selected, Set.of("minecraft:paper|str=lamp")));
    }

    @Test void itemsWithoutAKeyStayShownAndModelBonesAreRecognized() {
        ItemCandidate unknown = definition("minecraft:paper", "Plugin",
                "[{\"type\":\"match\",\"property\":\"minecraft:display_context\",\"value\":\"gui\"}]");
        assertTrue(BedrockPackCompiler.displayed(unknown, Set.of()), "no way to tell: shown rather than missing");
        assertTrue(BedrockPackCompiler.modelBone(definition("modelengine:crab/body", "ModelEngine", "[]")));
        assertTrue(BedrockPackCompiler.modelBone(definition("bettermodel:model/1", "BetterModel", "[]")));
        assertFalse(BedrockPackCompiler.modelBone(definition("nexo:chair", "Nexo", "[]")));
        assertFalse(BedrockPackCompiler.modelBone(legacy(5)));
    }
}
