package com.siberanka.twilight.compiler;

import com.siberanka.twilight.config.TwilightConfig;
import com.siberanka.twilight.source.ContentSource;
import com.siberanka.twilight.source.ResourceIndex;
import com.siberanka.twilight.text.TextLayoutTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Boss bar sprites: transparent colours are hidden, redrawn colours keep the pack's sprites. */
class BossBarsTest {
    @TempDir Path root;

    @Test
    void transparentColoursAreHiddenAndRedrawnColoursAreStyled() throws Exception {
        sprite("pink_background", 0);
        sprite("pink_progress", 0);
        sprite("red_progress", 0xFFC81E1E);
        sprite("notched_6_progress", 0xFF202020);
        try (ResourceIndex index = index()) {
            assertEquals(Set.of("pink"), BossBars.hidden(index));
            Set<String> styled = BossBars.styled(index);
            assertEquals(Set.of("red"), styled, "a pack that redraws only the progress sprite styles the colour");
            Map<String, byte[]> files = new HashMap<>();
            Set<String> written = BossBars.writeSprites(index, null, styled, files);
            assertEquals(Set.of("red_progress", "notched_6_progress"), written,
                    "without the vanilla client only the pack's sprites can be drawn");
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(files.get(BossBars.BEDROCK_FOLDER + "red_progress.png")));
            assertEquals(182, image.getWidth());
            assertEquals(0xFFC81E1E, image.getRGB(90, 2));
            assertEquals(Set.of("red"), BossBars.drawn(styled, written));
            assertEquals(Set.of(), BossBars.drawn(Set.of("blue"), written), "no sprite, no styled bar");
        }
    }

    @Test
    void noStyledColourWritesNothing() throws Exception {
        sprite("notched_6_progress", 0xFF202020);
        try (ResourceIndex index = index()) {
            assertEquals(Set.of(), BossBars.styled(index), "notches alone do not replace Bedrock's bar");
            Map<String, byte[]> files = new HashMap<>();
            assertTrue(BossBars.writeSprites(index, null, Set.of(), files).isEmpty());
            assertTrue(files.isEmpty());
        }
    }

    @Test
    void layoutTableKeepsStyledColours() {
        TextLayoutTable table = new TextLayoutTable(Map.of(), 0xE000, 0).withHiddenBossBars(Set.of("pink"))
                .withStyledBossBars(Set.of("red", "white"));
        TextLayoutTable read = TextLayoutTable.fromJson(table.withLayers(Set.of(TextLayoutTable.BOSS_LAYERS)).toJson());
        assertTrue(read.bossBarHidden("pink"));
        assertTrue(read.bossBarStyled("red"));
        assertTrue(read.bossBarStyled("white"));
        assertFalse(read.bossBarStyled("pink"));
        assertFalse(read.withContainerOrigin(0).bossBarStyled("blue"));
        assertTrue(read.withContainerOrigin(0).bossBarStyled("red"), "copies keep the styled colours");
    }

    private void sprite(String name, int argb) throws Exception {
        BufferedImage image = new BufferedImage(182, 5, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 5; y++) for (int x = 0; x < 182; x++) image.setRGB(x, y, argb);
        Path file = root.resolve(BossBars.JAVA_FOLDER + name + ".png");
        Files.createDirectories(file.getParent());
        ImageIO.write(image, "PNG", file.toFile());
    }

    private ResourceIndex index() throws Exception {
        return ResourceIndex.build(List.of(new ContentSource("test", ContentSource.Kind.RESOURCE_PACK, root, 1)),
                new TwilightConfig(false, true, false, false, 40, 100, 10_000_000, 1000, false, false, List.of(),
                        "auto", false, false, 3));
    }
}
