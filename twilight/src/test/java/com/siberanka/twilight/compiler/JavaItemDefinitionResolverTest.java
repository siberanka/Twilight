/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.compiler;

import com.siberanka.twilight.config.TwilightConfig;
import com.siberanka.twilight.source.ContentSource;
import com.siberanka.twilight.source.ResourceIndex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Item definitions as CraftEngine and other content plugins write them (1.21.4+), resolved into variants Geyser's
 * item mappings accept.
 */
class JavaItemDefinitionResolverTest {
    @TempDir Path root;

    private void write(String path, String text) throws Exception {
        Path file = root.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }

    private List<JavaItemDefinitionResolver.Variant> resolve(String item) throws Exception {
        try (ResourceIndex index = ResourceIndex.build(List.of(new ContentSource("test", ContentSource.Kind.RESOURCE_PACK, root, 1)),
                new TwilightConfig(false, true, false, false, 40, 100, 10_000_000, 1000, false, false, List.of(),
                        "auto", false, false, 3))) {
            return new JavaItemDefinitionResolver(index).resolve(item);
        }
    }

    /** Every property in the result is one Geyser's item mappings know; a definition naming another is refused whole. */
    private static void assertGeyserAccepts(List<JavaItemDefinitionResolver.Variant> variants) {
        for (var variant : variants) {
            for (var element : variant.predicates()) {
                var predicate = element.getAsJsonObject();
                String type = predicate.get("type").getAsString(), property = predicate.get("property").getAsString();
                Set<String> known = switch (type) {
                    case "condition" -> JavaItemDefinitionResolver.CONDITIONS;
                    case "match" -> JavaItemDefinitionResolver.MATCHES;
                    case "range_dispatch" -> JavaItemDefinitionResolver.RANGES;
                    default -> Set.of();
                };
                assertTrue(known.contains(property), type + " " + property + " is not a Geyser property");
            }
        }
    }

    /** CraftEngine's topaz crossbow: pulling while used, charge type otherwise. */
    @Test void crossbowsKeepTheChargeAndShowTheIdleModelForPulling() throws Exception {
        write("assets/default/items/topaz_crossbow.json", """
                {"model":{"type":"minecraft:condition","property":"minecraft:using_item",
                  "on_true":{"type":"minecraft:range_dispatch","property":"minecraft:crossbow/pull","entries":[
                    {"threshold":0.58,"model":{"type":"minecraft:model","model":"default:item/topaz_crossbow_pulling_1"}},
                    {"threshold":1.0,"model":{"type":"minecraft:model","model":"default:item/topaz_crossbow_pulling_2"}}],
                    "fallback":{"type":"minecraft:model","model":"default:item/topaz_crossbow_pulling_0"}},
                  "on_false":{"type":"minecraft:select","property":"minecraft:charge_type","cases":[
                    {"when":"arrow","model":{"type":"minecraft:model","model":"default:item/topaz_crossbow_arrow"}},
                    {"when":"rocket","model":{"type":"minecraft:model","model":"default:item/topaz_crossbow_firework"}}],
                    "fallback":{"type":"minecraft:model","model":"default:item/topaz_crossbow"}}}}
                """);
        var variants = resolve("default:topaz_crossbow");
        assertGeyserAccepts(variants);
        assertEquals(3, variants.size(), variants.toString());
        assertTrue(variants.stream().anyMatch(v -> v.visualModels().equals(List.of("default:item/topaz_crossbow"))
                && v.predicates().isEmpty()), "the idle model without a predicate");
        assertTrue(variants.stream().noneMatch(v -> v.visualModels().getFirst().contains("pulling")),
                "Bedrock cannot tell the pull: the idle state is used");
    }

    @Test void fishingRodsUseGeysersPropertyName() throws Exception {
        write("assets/default/items/topaz_rod.json", """
                {"model":{"type":"minecraft:condition","property":"minecraft:fishing_rod/cast",
                  "on_true":{"type":"minecraft:model","model":"default:item/topaz_rod_cast"},
                  "on_false":{"type":"minecraft:model","model":"default:item/topaz_rod"}}}
                """);
        var variants = resolve("default:topaz_rod");
        assertGeyserAccepts(variants);
        assertEquals(2, variants.size());
        assertTrue(variants.toString().contains("fishing_rod_cast"));
    }

    /** CraftEngine's spear shows a flat icon in menus and a 3D model in hand: Bedrock gets the hand model. */
    @Test void displayContextBecomesTheFallback() throws Exception {
        write("assets/default/items/topaz_spear.json", """
                {"model":{"type":"minecraft:select","property":"minecraft:display_context","cases":[
                  {"when":["gui","ground","fixed"],"model":{"type":"minecraft:model","model":"default:item/topaz_spear"}}],
                  "fallback":{"type":"minecraft:model","model":"default:item/topaz_spear_in_hand"}}}
                """);
        var variants = resolve("default:topaz_spear");
        assertGeyserAccepts(variants);
        assertEquals(List.of("default:item/topaz_spear_in_hand"), variants.getFirst().visualModels());
        assertEquals(1, variants.size());
    }

    @Test void bowsWithoutAFallbackUseTheLowestEntry() throws Exception {
        write("assets/default/items/topaz_bow.json", """
                {"model":{"type":"minecraft:condition","property":"minecraft:using_item",
                  "on_true":{"type":"minecraft:range_dispatch","property":"minecraft:use_duration","scale":0.05,"entries":[
                    {"threshold":0.65,"model":{"type":"minecraft:model","model":"default:item/topaz_bow_pulling_1"}},
                    {"threshold":0.0,"model":{"type":"minecraft:model","model":"default:item/topaz_bow_pulling_0"}}]},
                  "on_false":{"type":"minecraft:model","model":"default:item/topaz_bow"}}}
                """);
        var variants = resolve("default:topaz_bow");
        assertGeyserAccepts(variants);
        assertEquals(List.of("default:item/topaz_bow"), variants.getFirst().visualModels());
    }

    /** Java reads a model without a namespace as minecraft:, also in another namespace's item definition. */
    @Test void modelsWithoutANamespaceAreMinecraftsAsOnJava() throws Exception {
        write("assets/default/items/chinese_lantern.json", """
                {"oversized_in_gui":true,"model":{"type":"model","model":"block/custom/chinese_lantern"}}
                """);
        write("assets/minecraft/models/block/custom/chinese_lantern.json", "{\"parent\":\"block/cube_all\"}");
        assertEquals(List.of("minecraft:block/custom/chinese_lantern"), resolve("default:chinese_lantern").getFirst().visualModels());
        // A pack that relied on the old reading (the model only in the definition's namespace) keeps working.
        write("assets/demo/items/lamp.json", "{\"model\":{\"type\":\"model\",\"model\":\"item/lamp\"}}");
        write("assets/demo/models/item/lamp.json", "{\"parent\":\"item/generated\"}");
        assertEquals(List.of("demo:item/lamp"), resolve("demo:lamp").getFirst().visualModels());
        // Vanilla parents stay vanilla.
        write("assets/demo/items/plain.json", "{\"model\":{\"type\":\"model\",\"model\":\"item/stick\"}}");
        assertEquals(List.of("minecraft:item/stick"), resolve("demo:plain").getFirst().visualModels());
    }
}
