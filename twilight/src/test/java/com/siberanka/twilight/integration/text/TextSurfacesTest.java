package com.siberanka.twilight.integration.text;

import com.siberanka.twilight.text.TextLayout;
import com.siberanka.twilight.text.TextLayoutTable;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.EntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.MetadataType;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.ObjectEntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerType;
import org.geysermc.mcprotocollib.protocol.data.game.scoreboard.ObjectiveAction;
import org.geysermc.mcprotocollib.protocol.data.game.scoreboard.ScoreType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundBossEventPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundPlayerChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSystemChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEntityDataPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundOpenScreenPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.scoreboard.ClientboundSetObjectivePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.scoreboard.ClientboundSetScorePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.title.ClientboundSetActionBarTextPacket;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Packet-level rewrites with real protocol packets and unrelocated Adventure components. */
class TextSurfacesTest {
    private static final int SPACER_FIRST = 0xF800;
    // Shift -1 (U+4E03), a 176-wide menu image Java remaps from U+1F03, an 8-wide icon and a named-font icon.
    private static final TextLayoutTable TABLE = new TextLayoutTable(Map.of(
            TextLayoutTable.DEFAULT_FONT, Map.of(
                    0x4E03, TextLayoutTable.Entry.advanceOnly(-1),
                    0x1F03, new TextLayoutTable.Entry(0xF700, -1, 0, 176, 177),
                    0xE010, new TextLayoutTable.Entry(0xE010, 0xF010, 0, 8, 9)),
            "demo:icons", Map.of(0x0061, new TextLayoutTable.Entry(0xF600, 0xF680, 0, 8, 9))),
            SPACER_FIRST, 32);

    @Test
    void chatMessagesUseBedrockGlyphsAndKeepStyles() throws Exception {
        Component message = Component.text("[", NamedTextColor.GRAY)
                .append(Component.text("a", NamedTextColor.WHITE).font(Key.key("demo:icons")))
                .append(Component.text("] Steve: hi \u1F03", NamedTextColor.YELLOW));
        var packet = new ClientboundSystemChatPacket(message, false);
        var rewritten = TextSurfaces.rewrite(packet, TABLE, TextLayout.Mode.LEFT, "Content");
        String plain = plain(rewritten.getContent());
        assertTrue(plain.contains("\uF600"), escape(plain));
        assertTrue(plain.contains("\uF700"), escape(plain));
        assertFalse(plain.contains("\u1F03"));
        assertTrue(plain.contains("] Steve: hi"), "ordinary words and spaces stay literal for wrapping");
        assertEquals(NamedTextColor.YELLOW, ((TextComponent) rewritten.getContent().children().get(1)).color());
        assertFalse(rewritten.isOverlay());
    }

    @Test
    void untouchedPacketsAreForwardedAsTheSameInstance() throws Exception {
        var chat = new ClientboundSystemChatPacket(Component.text("Hello world", NamedTextColor.GREEN), false);
        assertSame(chat, TextSurfaces.rewrite(chat, TABLE, TextLayout.Mode.LEFT, "Content"));
        var bar = new ClientboundSetActionBarTextPacket(Component.translatable("item.minecraft.stone"));
        assertSame(bar, TextSurfaces.rewrite(bar, TABLE, TextLayout.Mode.CENTERED, "Text"));
        var health = new ClientboundBossEventPacket(UUID.randomUUID(), 0.5f);
        assertSame(health, TextSurfaces.rewrite(health, TABLE, TextLayout.Mode.CENTERED, "Title"));
        var data = new ClientboundSetEntityDataPacket(7, new EntityMetadata<?, ?>[]{
                new ObjectEntityMetadata<>(2, OPTIONAL_COMPONENT, Optional.of(Component.text("Zombie"))),
                new ObjectEntityMetadata<>(3, OPTIONAL_COMPONENT, Optional.empty())});
        assertSame(data, TextSurfaces.entityData(data, TABLE));
    }

    @Test
    void actionBarsAreCentredLikeJavaWithUnstyledPadding() throws Exception {
        // HUD idiom: a negative shift moves the centred image left on Java.
        Component hud = Component.text("\u4E03".repeat(40) + "\uE010", NamedTextColor.WHITE).decorate(TextDecoration.BOLD);
        var packet = TextSurfaces.rewrite(new ClientboundSetActionBarTextPacket(hud), TABLE,
                TextLayout.Mode.CENTERED, "Text");
        Component text = packet.getText();
        TextComponent wrapper = (TextComponent) text;
        assertEquals("", wrapper.content());
        assertNotEquals(TextDecoration.State.TRUE, wrapper.decoration(TextDecoration.BOLD), "padding must not inherit bold");
        TextComponent padding = (TextComponent) text.children().get(1);
        int trailing = padding.content().codePoints().map(cp -> cp - SPACER_FIRST + 2).sum();
        // Java: width 9 - 40 = -31, icon ink at +15.5 - 40 = -24.5 from the centre. Bedrock: width
        // 9 + trailing, icon ink at -(9 + trailing) / 2 + 1, hence trailing = 42.
        assertEquals(42, trailing);
        assertEquals(TextDecoration.State.TRUE, ((TextComponent) text.children().getFirst()).decoration(TextDecoration.BOLD));
    }

    @Test
    void playerChatRewritesSignedContentAndSenderName() throws Exception {
        var packet = new ClientboundPlayerChatPacket(0, UUID.randomUUID(), 0, null, "gg \uE010", 0L, 0L, List.of(),
                null, null, null, Component.text("\uE010 Steve"), null);
        var rewritten = TextSurfaces.rewrite(packet, TABLE, TextLayout.Mode.LEFT,
                "Content", "UnsignedContent", "Name", "TargetName");
        assertTrue(rewritten.getContent().startsWith("gg"), escape(rewritten.getContent()));
        assertTrue(rewritten.getContent().endsWith("\uE010"));
        String name = ((TextComponent) rewritten.getName()).content();
        // The literal space is kept for wrapping; the icon's one-unit-wider variant absorbs the gap.
        assertEquals("\uF010 Steve", name, escape(name));
        assertNull(rewritten.getUnsignedContent());
        assertNull(rewritten.getTargetName());
    }

    @Test
    void entityNamesAndTextDisplaysAreCentredAndOtherDataIsKept() throws Exception {
        var custom = new ObjectEntityMetadata<>(2, OPTIONAL_COMPONENT, Optional.of(Component.text("\u1F03")));
        var display = new ObjectEntityMetadata<>(23, COMPONENT, (Component) Component.text("Top\n\u4E03\u4E03\uE010"));
        var other = new ObjectEntityMetadata<>(5, STRING, "\u1F03");
        var packet = new ClientboundSetEntityDataPacket(9, new EntityMetadata<?, ?>[]{custom, display, other});
        var rewritten = TextSurfaces.entityData(packet, TABLE);
        EntityMetadata<?, ?>[] metadata = rewritten.getMetadata();
        assertEquals(9, rewritten.getEntityId());
        assertEquals(3, metadata.length);
        Component name = (Component) ((Optional<?>) metadata[0].getValue()).orElseThrow();
        assertTrue(plain(name).contains("\uF700") && !plain(name).contains("\u1F03"),
                "centred names keep their glyph, padded for centring");
        assertEquals(2, metadata[0].getId());
        assertSame(OPTIONAL_COMPONENT, metadata[0].getType());
        String lines = plain((Component) metadata[1].getValue());
        assertTrue(lines.startsWith("Top\n"), escape(lines));
        assertTrue(lines.contains("\uE010"));
        assertSame(other, metadata[2], "non-text metadata is untouched");
        assertSame(custom, packet.getMetadata()[0], "the original packet is not modified");
    }

    @Test
    void containerTitlesFollowTheirScreen() throws Exception {
        Component title = Component.text("\u4E03\u4E03\u1F03");
        var chest = TextSurfaces.openScreen(new ClientboundOpenScreenPacket(1, ContainerType.GENERIC_9X3, title), TABLE, false);
        String chestText = plain(chest.getTitle());
        int origin = chestText.codePoints().filter(cp -> cp >= SPACER_FIRST && cp < SPACER_FIRST + 32)
                .map(cp -> cp - SPACER_FIRST + 2).sum();
        assertEquals(TextLayoutTable.ORIGIN - 3, origin, "chest titles start at the shifted label origin");

        var hopper = TextSurfaces.openScreen(new ClientboundOpenScreenPacket(2, ContainerType.HOPPER, title), TABLE, false);
        String hopperText = plain(hopper.getTitle());
        assertTrue(hopperText.contains("\uF700") && !hopperText.contains("\u1F03"), escape(hopperText));
        assertTrue(hopperText.codePoints().count() < 4, "no origin spacers outside chest screens: " + escape(hopperText));

        var plainHopper = new ClientboundOpenScreenPacket(3, ContainerType.HOPPER, Component.text("Hopper"));
        assertSame(plainHopper, TextSurfaces.openScreen(plainHopper, TABLE, false));

        var touch = TextSurfaces.openScreen(new ClientboundOpenScreenPacket(4, ContainerType.GENERIC_9X3, title), TABLE, true);
        assertEquals("\uF700", plain(touch.getTitle()));
    }

    @Test
    void scoreboardAndBossBarTextIsLaidOut() throws Exception {
        var objective = new ClientboundSetObjectivePacket("side", ObjectiveAction.ADD,
                Component.text("\uE010 Stats"), ScoreType.INTEGER, null);
        var laidOut = TextSurfaces.rewrite(objective, TABLE, TextLayout.Mode.CENTERED, "DisplayName");
        assertNotSame(objective, laidOut);
        assertEquals("side", laidOut.getName());

        var score = new ClientboundSetScorePacket("line1", "side", 3).withDisplay(Component.text("Coins \uE010"));
        String line = ((TextComponent) TextSurfaces.rewrite(score, TABLE, TextLayout.Mode.LEFT, "Display")
                .getDisplay()).content();
        assertTrue(line.startsWith("Coins") && line.endsWith("\uE010"), escape(line));

        var boss = new ClientboundBossEventPacket(UUID.randomUUID(), Component.text("\u1F03"));
        assertNotSame(boss, TextSurfaces.rewrite(boss, TABLE, TextLayout.Mode.CENTERED, "Title"));
    }

    private static final MetadataType<Optional<Component>> OPTIONAL_COMPONENT = new MetadataType<>(6, null, null, null) {};
    private static final MetadataType<Component> COMPONENT = new MetadataType<>(5, null, null, null) {};
    private static final MetadataType<String> STRING = new MetadataType<>(4, null, null, null) {};

    private static String plain(Component component) {
        StringBuilder out = new StringBuilder();
        flatten(component, out);
        return out.toString();
    }

    private static void flatten(Component component, StringBuilder out) {
        if (component instanceof TextComponent text) out.append(text.content());
        component.children().forEach(child -> flatten(child, out));
    }

    private static String escape(String value) {
        StringBuilder out = new StringBuilder();
        value.codePoints().forEach(cp -> out.append(cp < 128 && cp != '\n' ? Character.toString(cp)
                : "\\u%04X".formatted(cp)));
        return out.toString();
    }
}
