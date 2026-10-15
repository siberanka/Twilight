package com.siberanka.twilight.integration.display;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class DisplayIndexTest {
    @TempDir Path root;

    private static String transform(double fixedYaw) {
        StringBuilder values = new StringBuilder("[");
        for (int i = 0; i < 81; i++) {
            if (i > 0) values.append(',');
            values.append(i == 8 * 9 + 4 ? Double.toString(fixedYaw) : i % 9 >= 6 ? "1" : "0");
        }
        return values.append(']').toString();
    }

    @Test void readsItemsModelsAndTheirTransforms() throws IOException {
        DisplayIndex index = DisplayIndex.parse("""
                {"format":2,"parts":64,"variants":{"twilight:chair":[0,0],"twilight:lamp":[1,-1]},
                 "models":{"modelengine:crab/left_leg":[64,-1]},"transforms":[%s]}
                """.formatted(transform(-90)));
        assertEquals(0, index.items().get("twilight:chair").ordinal());
        assertEquals(-90, index.items().get("twilight:chair").contexts()[8 * 9 + 4]);
        assertNull(index.items().get("twilight:lamp").contexts());
        assertEquals(64, index.models().get("modelengine:crab/left_leg").ordinal());
    }

    @Test void ignoresIndexesOfOtherFormats() throws IOException {
        assertTrue(DisplayIndex.parse("{\"twilight:x\":0}").isEmpty(), "format 1 had another variant layout");
        assertTrue(DisplayIndex.parse("{\"format\":3,\"variants\":{},\"transforms\":[]}").isEmpty());
    }

    @Test void rejectsHostileOrBrokenIndexes() {
        for (String broken : new String[]{
                "{\"format\":2,\"variants\":{\"twilight:a\":[0,-1],\"twilight:b\":[0,-1]},\"transforms\":[]}",
                "{\"format\":2,\"variants\":{\"twilight:a\":[111104,-1]},\"transforms\":[]}",
                "{\"format\":2,\"variants\":{\"twilight:a\":[-1,-1]},\"transforms\":[]}",
                "{\"format\":2,\"variants\":{\"Twilight:A B\":[0,-1]},\"transforms\":[]}",
                "{\"format\":2,\"variants\":{\"twilight:a\":[0,1]},\"transforms\":[]}",
                "{\"format\":2,\"variants\":{\"twilight:a\":[0,0,5]},\"transforms\":[" + transform(0) + "]}",
                "{\"format\":2,\"variants\":{},\"transforms\":[[1,2,3]]}",
                "{\"format\":2,\"variants\":{},\"transforms\":[" + transform(0).replaceFirst("^\\[0", "[81") + "]}",
                "{\"format\":2,\"variants\":{},\"transforms\":[" + transform(0).replace("[0,", "[\"NaN\",") + "]}",
                "not json"}) {
            assertThrows(IOException.class, () -> DisplayIndex.parse(broken), broken);
        }
    }

    @Test void readsThePackEntryAndBoundsItsSize() throws IOException {
        Path pack = root.resolve("pack.mcpack");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(pack))) {
            zip.putNextEntry(new ZipEntry(DisplayIndex.INDEX));
            zip.write("{\"format\":2,\"variants\":{\"twilight:a\":[3,-1]},\"transforms\":[]}".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        assertEquals(3, DisplayIndex.read(pack).items().get("twilight:a").ordinal());
        assertTrue(DisplayIndex.read(root.resolve("missing.zip")).isEmpty());

        Path bomb = root.resolve("bomb.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(bomb))) {
            zip.putNextEntry(new ZipEntry(DisplayIndex.INDEX));
            byte[] spaces = new byte[1 << 20];
            java.util.Arrays.fill(spaces, (byte) ' ');
            for (int i = 0; i < 17; i++) zip.write(spaces);
            zip.closeEntry();
        }
        assertThrows(IOException.class, () -> DisplayIndex.read(bomb), "an index above 16 MiB is refused");
    }
}
