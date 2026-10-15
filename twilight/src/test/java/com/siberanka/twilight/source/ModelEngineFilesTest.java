package com.siberanka.twilight.source;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ModelEngineFilesTest {
    @TempDir Path root;

    /** ModelEngine R4 on 1.21.4+: every bone is an item_model on the configured base item. */
    @Test
    void readsBoneItemsFromTheGeneratedPack() throws Exception {
        write("config.yml", """
                Model-Generator:
                  Force-Custom-Model-Data: false
                  Item-Model: LEATHER_HORSE_ARMOR
                  Item-Models: []
                """);
        write("resource pack/assets/modelengine/items/fallen_mauler3/right_arm.json", "{}");
        write("resource pack/assets/modelengine/items/crab/left_leg.json", "{}");
        write("resource pack/assets/modelengine/items/crab/notes.txt", "x");
        write("resource pack/assets/modelengine/items/Bad Name/x.json", "{}");
        List<CustomItemDescriptor> bones = ModelEngineFiles.descriptors(root);
        assertEquals(List.of("modelengine:crab/left_leg", "modelengine:fallen_mauler3/right_arm"),
                bones.stream().map(bone -> bone.itemModel().orElseThrow()).toList());
        assertTrue(bones.stream().allMatch(bone -> bone.baseItem().equals("minecraft:leather_horse_armor")
                && bone.customModelData().isEmpty() && bone.provider().equals("ModelEngine")));
    }

    @Test
    void usesTheConfiguredBaseItemAndSkipsCustomModelDataMode() throws Exception {
        write("resource pack/assets/modelengine/items/a/b.json", "{}");
        write("config.yml", "  Item-Model: 'paper'\n");
        assertEquals(Optional.of("modelengine:a/b"), ModelEngineFiles.descriptors(root).getFirst().itemModel());
        assertEquals("minecraft:paper", ModelEngineFiles.descriptors(root).getFirst().baseItem());
        write("config.yml", "  Force-Custom-Model-Data: true\n");
        assertEquals(List.of(), ModelEngineFiles.descriptors(root), "the packs' overrides describe those");
        assertEquals(List.of(), ModelEngineFiles.descriptors(root.resolve("missing")));
    }

    private void write(String relative, String text) throws Exception {
        Path path = root.resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, text);
    }
}
