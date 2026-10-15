package com.siberanka.twilight.geyser;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CustomBlocksTest {
    @TempDir Path root;

    private static final String RUBY = """
            {"name":"twilight_note_block_aaaaaaaaaaaa","state":"minecraft:note_block[instrument=basedrum,note=3,powered=false]",
             "geometry":"minecraft:geometry.full_block","materials":{"up":{"texture":"twilight_b_aaaaaaaaaaaa_up","render_method":"opaque"}},
             "selection":[-8,0,-8,16,16,16],"collision":[[-8,0,-8,16,16,16]]}
            """;

    /** A proxy merges every server's blocks; the first server wins where two draw the same state differently. */
    @Test
    void mergesServersAndCountsDifferentShapes() throws Exception {
        Path lobby = pack("lobby.zip", "{\"format\":1,\"blocks\":[" + RUBY + "],\"items\":{\"twilight:paper_5\":\"twilight_note_block_aaaaaaaaaaaa\"}}");
        String plant = RUBY.replace("minecraft:geometry.full_block", "geometry.twilight_b_aaaaaaaaaaaa");
        Path survival = pack("survival.zip", "{\"format\":1,\"blocks\":[" + plant + "],\"items\":{}}");
        CustomBlocks.Merged merged = CustomBlocks.read(List.of(lobby, survival, root.resolve("missing.zip")));
        assertEquals(1, merged.blocks().size());
        assertEquals("minecraft:geometry.full_block", merged.blocks().getFirst().get("geometry").getAsString());
        assertEquals(1, merged.conflicts());
        assertEquals("twilight_note_block_aaaaaaaaaaaa", merged.items().get("twilight:paper_5"));
        assertNotEquals(merged.hash(), CustomBlocks.read(List.of(survival)).hash(), "a different shape needs a Geyser restart");
        assertEquals(merged.hash(), CustomBlocks.read(List.of(lobby, survival)).hash());
        assertTrue(CustomBlocks.read(List.of()).blocks().isEmpty());
    }

    /** Pack content is untrusted on a proxy: malformed entries are skipped, never registered with Geyser. */
    @Test
    void rejectsMalformedEntries() {
        JsonObject valid = JsonParser.parseString(RUBY).getAsJsonObject();
        assertTrue(CustomBlocks.valid(valid));
        for (String[] change : new String[][]{{"name", "1digit"}, {"name", "Upper"}, {"state", "minecraft:note_block"},
                {"state", "minecraft:note_block[instrument=../../x]"}, {"geometry", "geometry with spaces"}}) {
            JsonObject copy = valid.deepCopy();
            copy.addProperty(change[0], change[1]);
            assertFalse(CustomBlocks.valid(copy), change[0] + "=" + change[1]);
        }
        JsonObject noMaterials = valid.deepCopy();
        noMaterials.add("materials", new JsonObject());
        assertFalse(CustomBlocks.valid(noMaterials));
    }

    @Test
    void turnsJavaRotationsTheBedrockWay() {
        assertEquals(0, CustomBlocks.bedrockRotation(0));
        assertEquals(270, CustomBlocks.bedrockRotation(90));
        assertEquals(180, CustomBlocks.bedrockRotation(180));
        assertEquals(90, CustomBlocks.bedrockRotation(270));
    }

    private Path pack(String name, String blocks) throws Exception {
        Path pack = root.resolve(name);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(pack))) {
            zip.putNextEntry(new ZipEntry(CustomBlocks.PATH));
            zip.write(blocks.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return pack;
    }
}
