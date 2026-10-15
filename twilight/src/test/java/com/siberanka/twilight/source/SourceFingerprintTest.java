package com.siberanka.twilight.source;

import com.siberanka.twilight.config.TwilightConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SourceFingerprintTest {
    @TempDir Path root;

    @Test
    void isDeterministicAndChangesWithBytesRatherThanOnlyMetadata() throws Exception {
        Path pack = Files.createDirectories(root.resolve("pack/assets/example/models/item"));
        Path model = pack.resolve("tool.json");
        Files.writeString(model, "alpha");
        ContentSource source = new ContentSource("test", ContentSource.Kind.RESOURCE_PACK,
                root.resolve("pack"), 600);

        String first = SourceFingerprint.compute(List.of(source), List.of(), config());
        String repeated = SourceFingerprint.compute(List.of(source), List.of(), config());
        Files.writeString(model, "bravo"); // Same byte length; content hash must still change.
        String changed = SourceFingerprint.compute(List.of(source), List.of(), config());

        assertEquals(first, repeated);
        assertNotEquals(first, changed);
    }

    private TwilightConfig config() {
        return new TwilightConfig(false, true, false, true, 40, 100, 10_000_000, 1000,
                true, true, List.of(), "auto", false, false, 3);
    }
}
