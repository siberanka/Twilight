package com.siberanka.twilight.integration.text;

import com.siberanka.twilight.text.TextLayoutTable;
import com.siberanka.twilight.text.TextLayout;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AdventureTextLayoutTest {
    // Shift -1 (U+4E03), a 176-wide menu image aliased to U+F700, and a named-font icon.
    private static final TextLayoutTable TABLE = new TextLayoutTable(Map.of(
            TextLayoutTable.DEFAULT_FONT, Map.of(
                    0x4E03, TextLayoutTable.Entry.advanceOnly(-1),
                    0x1F03, new TextLayoutTable.Entry(0xF700, -1, 0, 176, 177)),
            "demo:icons", Map.of(0xE121, new TextLayoutTable.Entry(0xF600, 0xF680, 0, 8, 9))),
            0xF800, 32);

    @Test
    void rewritesNestedTextKeepingStylesAndStructure() throws Exception {
        // DeluxeMenus '&f\u4E03\u4E03\u4E03\u4E03\u4E03\u4E03\u4E03\u4E03\u1F03' as Paper deserializes it: an empty root with a white child.
        Component title = Component.text().append(Component.text("\u4E03\u4E03\u4E03\u4E03\u4E03\u4E03\u4E03\u4E03\u1F03", NamedTextColor.WHITE)).build();
        Component laidOut = (Component) AdventureTextLayout.layoutTitle(TABLE, title);

        String expected = TextLayout.layout(TABLE, List.of(new TextLayout.Segment("", null),
                new TextLayout.Segment("\u4E03\u4E03\u4E03\u4E03\u4E03\u4E03\u4E03\u4E03\u1F03", null))).texts().get(1);
        TextComponent child = (TextComponent) laidOut.children().getFirst();
        assertEquals(expected, child.content());
        assertEquals(NamedTextColor.WHITE, child.color());
        assertEquals("", ((TextComponent) laidOut).content());
        assertTrue(child.content().contains("\uF700"));
        assertFalse(child.content().contains("\u1F03"));
    }

    @Test
    void onlyUncolouredContainerTitlesAreDarkened() throws Exception {
        TextLayoutTable shaded = new TextLayoutTable(TABLE.fonts(), 0xF800, 32, TextLayoutTable.ORIGIN, Map.of(),
                Map.of(0xF700, 0xF701));
        Component uncoloured = Component.text("\u4E03\u4E03\u1F03");
        assertTrue(text(AdventureTextLayout.layoutTitle(shaded, uncoloured)).contains("\uF701"));
        assertTrue(text(AdventureTextLayout.layoutTitle(shaded, uncoloured, true)).contains("\uF701"), "touch layout");
        // A colour anywhere up the tree replaces Java's default title colour.
        Component white = Component.text().color(NamedTextColor.WHITE).append(uncoloured).build();
        assertTrue(text(AdventureTextLayout.layoutTitle(shaded, white)).contains("\uF700"));
        // Other surfaces default to white text: nothing is darkened.
        assertTrue(text(AdventureTextLayout.layout(shaded, uncoloured, TextLayout.Mode.LEFT)).contains("\uF700"));
    }

    private static String text(Object component) {
        StringBuilder out = new StringBuilder();
        java.util.ArrayDeque<Component> pending = new java.util.ArrayDeque<>(List.of((Component) component));
        while (!pending.isEmpty()) {
            Component next = pending.pop();
            if (next instanceof TextComponent text) out.append(text.content());
            List<Component> children = new java.util.ArrayList<>(next.children());
            java.util.Collections.reverse(children);
            children.forEach(pending::push);
        }
        return out.toString();
    }

    @Test
    void originSpacersOfBoldTitlesStayUnstyled() throws Exception {
        // '&b&lShop' as Paper deserializes it: Bedrock draws bold spacers wider, which moved the whole title.
        Component title = Component.text("Shop", NamedTextColor.AQUA, net.kyori.adventure.text.format.TextDecoration.BOLD);
        Component laidOut = (Component) AdventureTextLayout.layoutTitle(TABLE, title);
        TextComponent lead = (TextComponent) laidOut.children().getFirst();
        TextComponent bold = (TextComponent) laidOut.children().get(1);
        assertEquals(net.kyori.adventure.text.format.TextDecoration.State.NOT_SET,
                laidOut.decoration(net.kyori.adventure.text.format.TextDecoration.BOLD));
        assertFalse(lead.content().isEmpty());
        assertTrue(lead.content().codePoints().allMatch(TABLE::isSpacer), lead.content());
        assertEquals("Shop", bold.content());
        assertTrue(bold.hasDecoration(net.kyori.adventure.text.format.TextDecoration.BOLD));
        assertEquals(TextLayout.layout(TABLE, "Shop").texts().getFirst(), lead.content() + bold.content());
    }

    @Test
    void textMovedBackOverAnImageBecomesStyledLayers() throws Exception {
        // ItemsAdder style: a 176-wide image, 100 one-unit steps back, bold aqua text inherited from a parent.
        Component title = Component.text().append(Component.text("\u1F03", NamedTextColor.WHITE))
                .append(Component.text("\u4E03".repeat(100)))
                .append(Component.text().color(NamedTextColor.AQUA).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD)
                        .append(Component.text("Shop")))
                .build();
        TextLayoutTable table = new TextLayoutTable(TABLE.fonts(), 0xF800, 32, TextLayoutTable.ORIGIN,
                Map.of((int) 'S', 6f, (int) 'h', 6f, (int) 'o', 6f, (int) 'p', 6f));
        List<Object> layers = AdventureTextLayout.layered(table, title, TextLayout.Mode.CONTAINER, null, true);
        assertNotNull(layers);
        assertEquals(2, layers.size());
        assertTrue(text(layers.get(0)).contains("\uF700"));
        TextComponent shop = ((Component) layers.get(1)).children().stream().map(TextComponent.class::cast)
                .filter(child -> child.content().contains("Shop")).findFirst().orElseThrow();
        assertEquals(NamedTextColor.AQUA, shop.color(), "inherited colour");
        assertTrue(shop.hasDecoration(net.kyori.adventure.text.format.TextDecoration.BOLD), "inherited bold");
        for (Component child : ((Component) layers.get(1)).children()) {
            String content = ((TextComponent) child).content();
            if (content.codePoints().anyMatch(table::isSpacer)) assertFalse(child.hasDecoration(
                    net.kyori.adventure.text.format.TextDecoration.BOLD), "spacers stay unstyled");
        }
        // Without a move back the line stays a single label.
        assertNull(AdventureTextLayout.layered(table, Component.text("\u1F03 Shop"), TextLayout.Mode.CONTAINER, null, true));
    }

    @Test
    void touchLayoutGetsGlyphSubstitutionWithoutSpacers() throws Exception {
        // Bedrock's pocket chest title is a centred label unrelated to Java geometry: no origin spacers.
        Component title = Component.text("\u4E03\u4E03\u1F03 Shop", NamedTextColor.WHITE);
        TextComponent laidOut = (TextComponent) AdventureTextLayout.layoutTitle(TABLE, title, true);
        assertEquals("\uF700 Shop", laidOut.content());
        assertEquals(NamedTextColor.WHITE, laidOut.color());
    }

    @Test
    void usesTheInheritedNamedFontOfEachComponent() throws Exception {
        Component title = Component.text().font(Key.key("demo:icons"))
                .append(Component.text("\uE121")).build();
        Component laidOut = (Component) AdventureTextLayout.layoutTitle(TABLE, title);
        String icon = ((TextComponent) laidOut.children().getFirst()).content();
        assertTrue(icon.endsWith("\uF600"), icon);
        assertEquals(Key.key("demo:icons"), laidOut.style().font());
    }

    @Test
    void precedesUnmeasurableComponentsWithOriginSpacers() throws Exception {
        TranslatableComponent chest = Component.translatable("container.chest");
        Component laidOut = (Component) AdventureTextLayout.layoutTitle(TABLE, chest);
        String spacers = ((TextComponent) laidOut).content();
        assertEquals(TextLayout.layout(TABLE, List.of(new TextLayout.Segment(null, null))).texts().getFirst(), spacers);
        assertSame(chest, laidOut.children().getFirst());
        int advance = spacers.codePoints().map(cp -> cp - 0xF800 + 2).sum();
        assertEquals(TextLayoutTable.ORIGIN, advance, "translations start at Java's title origin");
    }

    @Test
    void packTranslationsAreLaidOutLikeText() throws Exception {
        // A resource pack turns container.enderchest into a GUI image; Java players see that image.
        java.util.function.Function<String, String> translations =
                key -> key.equals("container.enderchest") ? "\u00a7f\u1F03" : null;
        Component title = Component.translatable("container.enderchest", NamedTextColor.DARK_GRAY);
        Component laidOut = (Component) AdventureTextLayout.layout(TABLE, title, TextLayout.Mode.CONTAINER, translations);
        assertTrue(laidOut instanceof TextComponent, laidOut.getClass().getName());
        String content = ((TextComponent) laidOut).content();
        // The colour code keeps its place; the origin spacers follow it, then the image.
        assertTrue(content.startsWith("\u00a7f") && content.endsWith("\uF700"), content);
        assertEquals(NamedTextColor.DARK_GRAY, laidOut.color());

        // Keys the packs do not define, and components with arguments, stay translatable for Geyser.
        Component vanilla = Component.translatable("container.chest");
        assertSame(vanilla, ((Component) AdventureTextLayout.layout(TABLE, vanilla, TextLayout.Mode.CONTAINER, translations))
                .children().getFirst());
        Component withArgument = Component.translatable("container.enderchest", Component.text("x"));
        assertSame(withArgument, ((Component) AdventureTextLayout.layout(TABLE, withArgument, TextLayout.Mode.CONTAINER,
                translations)).children().getFirst());
    }

    @Test
    void fallbackKeepsJavaTextAfterTheOriginSpacers() throws Exception {
        Component title = Component.text("Plain");
        Component fallback = (Component) AdventureTextLayout.originOnly(TABLE, title);
        assertSame(title, fallback.children().getFirst());
    }
}
