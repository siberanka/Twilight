/* Copyright (c) 2026 siberanka. Twilight License (PolyForm Noncommercial 1.0.0 + terms), see LICENSE. */
package com.siberanka.twilight.integration.text;

import com.siberanka.twilight.text.LayeredTextLayout;
import com.siberanka.twilight.text.TextLayout;
import com.siberanka.twilight.text.TextLayoutTable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Applies {@link TextLayout} to an Adventure component tree without linking against
 * Adventure: Geyser relocates it on Spigot, so methods are resolved by name from the
 * component's own classes. Styles, colours and the tree structure are preserved; only
 * text contents change, and unmeasurable components are preceded by spacers.
 */
final class AdventureTextLayout {
    private static final Map<Class<?>, Adventure> ADVENTURE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Boolean> COMPONENT_TYPES = new ConcurrentHashMap<>();

    private AdventureTextLayout() {}

    /** Whether a value is an Adventure component, relocated or not. */
    static boolean isComponent(Object value) {
        return value != null && COMPONENT_TYPES.computeIfAbsent(value.getClass(), AdventureTextLayout::implementsComponent);
    }

    private static boolean implementsComponent(Class<?> type) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Class<?> contract : current.getInterfaces()) {
                if (contract.getName().endsWith("kyori.adventure.text.Component") || implementsComponent(contract)) return true;
            }
        }
        return false;
    }

    /** Lays out a chest title (relocated or not) and returns the rewritten component. */
    static Object layoutTitle(TextLayoutTable table, Object title) throws ReflectiveOperationException {
        return layoutTitle(table, title, false);
    }

    /** @param substituteOnly touch layout: replace glyphs without Java positioning (see {@link TextLayout#substitute}) */
    static Object layoutTitle(TextLayoutTable table, Object title, boolean substituteOnly)
            throws ReflectiveOperationException {
        return substituteOnly ? substitute(table, title, true) : layout(table, title, TextLayout.Mode.CONTAINER, null, true);
    }

    /**
     * Lays out text for a surface. Returns the same instance when nothing changes, which is
     * the common case for chat and entity names without custom font characters.
     */
    static Object layout(TextLayoutTable table, Object component, TextLayout.Mode mode)
            throws ReflectiveOperationException {
        return layout(table, component, mode, null);
    }

    /**
     * @param translations the string Java players see for a translation key that resource packs define,
     *                     or null (such argument-free translatable components are laid out as text)
     */
    static Object layout(TextLayoutTable table, Object component, TextLayout.Mode mode,
                         Function<String, String> translations) throws ReflectiveOperationException {
        return layout(table, component, mode, translations, false);
    }

    /** @param title a container title, whose uncoloured images Java darkens (see {@link TextLayoutTable#shade}) */
    static Object layout(TextLayoutTable table, Object component, TextLayout.Mode mode,
                         Function<String, String> translations, boolean title) throws ReflectiveOperationException {
        Adventure adventure = Adventure.of(component);
        List<TextLayout.Segment> segments = segments(adventure, component, translations, title);
        TextLayout.Result result = TextLayout.layout(table, segments, mode);
        boolean origin = mode == TextLayout.Mode.CONTAINER && table.containerOrigin() > 0;
        if (result.customCharacters() == 0 && !origin) return component;
        List<String> texts = new ArrayList<>(result.texts());
        String lead = boldLead(table, segments, texts);
        Object rebuilt = rebuild(adventure, component, texts, new int[]{0}, translations);
        if (result.suffix().isEmpty() && lead.isEmpty()) return rebuilt;
        // An unstyled wrapper keeps the padding free of the text's own style: Bedrock widens bold spacers.
        Object wrapper = adventure.text("");
        if (!lead.isEmpty()) wrapper = adventure.append(wrapper, adventure.text(lead));
        wrapper = adventure.append(wrapper, rebuilt);
        return result.suffix().isEmpty() ? wrapper : adventure.append(wrapper, adventure.text(result.suffix()));
    }

    /**
     * Removes the spacers that start a line inside bold text and returns them, so they can precede the
     * component unstyled (Bedrock draws bold spacers wider, moving the whole line).
     */
    private static String boldLead(TextLayoutTable table, List<TextLayout.Segment> segments, List<String> texts) {
        for (int index = 0; index < texts.size(); index++) {
            String text = texts.get(index);
            if (text.isEmpty()) continue;
            if (!segments.get(index).bold() || segments.get(index).text() == null) return "";
            int end = 0;
            while (end < text.length() && table.isSpacer(text.codePointAt(end))) {
                end += Character.charCount(text.codePointAt(end));
            }
            texts.set(index, text.substring(end));
            return text.substring(0, end);
        }
        return "";
    }

    /**
     * The line as Bedrock layers when Java moves back over earlier characters (see {@link LayeredTextLayout}):
     * one component per layer, each a flat list of runs carrying their source component's full style
     * (spacer runs are unstyled). Null when the line fits one layer or cannot be layered.
     */
    static List<Object> layered(TextLayoutTable table, Object component, TextLayout.Mode mode,
                                Function<String, String> translations, boolean title) throws ReflectiveOperationException {
        return layered(table, component, mode, translations, title, 1);
    }

    /** @param linesPerUnit label lines per unit of vertical shift, see {@link LayeredTextLayout#layout} */
    static List<Object> layered(TextLayoutTable table, Object component, TextLayout.Mode mode,
                                Function<String, String> translations, boolean title, int linesPerUnit)
            throws ReflectiveOperationException {
        return layered(table, component, mode, translations, title, linesPerUnit, false);
    }

    /** @param shadows see {@link LayeredTextLayout#layout(TextLayoutTable, List, TextLayout.Mode, int, boolean)} */
    static List<Object> layered(TextLayoutTable table, Object component, TextLayout.Mode mode,
                                Function<String, String> translations, boolean title, int linesPerUnit,
                                boolean shadows) throws ReflectiveOperationException {
        Adventure adventure = Adventure.of(component);
        List<TextLayout.Segment> segments = new ArrayList<>(segments(adventure, component, translations, title));
        List<Object> styles = new ArrayList<>();
        effectiveStyles(adventure, component, null, styles, translations);
        if (shadows) {
            for (int index = 0; index < segments.size(); index++) {
                segments.set(index, segments.get(index).withShadowless(adventure.shadowless(styles.get(index))));
            }
        }
        LayeredTextLayout.Result result = LayeredTextLayout.layout(table, segments, mode, linesPerUnit, shadows);
        if (result == null) return null;
        List<Object> layers = new ArrayList<>();
        for (int index = 0; index < result.count(); index++) {
            List<LayeredTextLayout.Run> runs = result.layers().get(index);
            // Leading newlines move the layer down; unstyled, before any formatting code of the layer.
            Object root = adventure.text("\n".repeat(result.lines().get(index)));
            for (LayeredTextLayout.Run run : runs) {
                Object text = adventure.text(run.text());
                root = adventure.append(root, run.segment() < 0 ? text : adventure.withStyle(text, styles.get(run.segment())));
            }
            layers.add(root);
        }
        return layers;
    }

    /**
     * A chest title that needs no layout (no custom character, nothing the layout would move): the component with
     * pack translations resolved, or null when the title needs the layout.
     */
    static Object plainTitle(TextLayoutTable table, Object component, Function<String, String> translations)
            throws ReflectiveOperationException {
        Adventure adventure = Adventure.of(component);
        List<TextLayout.Segment> segments = segments(adventure, component, translations, true);
        TextLayout.Result result = TextLayout.layout(table, segments, TextLayout.Mode.CONTAINER);
        if (result.customCharacters() > 0 || result.approximations() > 0) return null;
        List<String> texts = new ArrayList<>();
        for (TextLayout.Segment segment : segments) texts.add(segment.text() == null ? "" : segment.text());
        return rebuild(adventure, component, texts, new int[]{0}, translations);
    }

    /** {@code text} followed by the component (an unstyled parent, so the component keeps its style). */
    static Object prepend(Object component, String text) throws ReflectiveOperationException {
        Adventure adventure = Adventure.of(component);
        return adventure.append(adventure.text(text), component);
    }

    /** Plain text in the component family of {@code sample} (relocated Adventure or not). */
    static Object plainText(Object sample, String text) throws ReflectiveOperationException {
        return Adventure.of(sample).text(text);
    }

    /** Pre-order like {@link #collect}: each segment's style with everything it inherits. */
    private static void effectiveStyles(Adventure adventure, Object component, Object inherited, List<Object> styles,
                                        Function<String, String> translations) throws ReflectiveOperationException {
        Object style = adventure.inherit(adventure.style.invoke(component), inherited);
        styles.add(style);
        if (!adventure.isText(component) && adventure.translated(component, translations) == null) return;
        for (Object child : adventure.children(component)) effectiveStyles(adventure, child, style, styles, translations);
    }

    /** Glyph substitution only (touch layouts). */
    static Object substitute(TextLayoutTable table, Object component) throws ReflectiveOperationException {
        return substitute(table, component, false);
    }

    static Object substitute(TextLayoutTable table, Object component, boolean title) throws ReflectiveOperationException {
        Adventure adventure = Adventure.of(component);
        TextLayout.Result result = TextLayout.substitute(table, segments(adventure, component, null, title));
        if (result.customCharacters() == 0) return component;
        return rebuild(adventure, component, result.texts(), new int[]{0}, null);
    }

    /** Lays out plain default-font text, such as the signed content of a player chat message. */
    static String layout(TextLayoutTable table, String text, TextLayout.Mode mode) {
        TextLayout.Result result = TextLayout.layout(table, List.of(new TextLayout.Segment(text, null)), mode);
        return result.customCharacters() == 0 ? text : result.texts().getFirst() + result.suffix();
    }

    /** Fallback: Java text unchanged, preceded only by the spacers that reach Java's origin. */
    static Object originOnly(TextLayoutTable table, Object title) throws ReflectiveOperationException {
        Adventure adventure = Adventure.of(title);
        String origin = TextLayout.layout(table, List.of(new TextLayout.Segment(null, null))).texts().getFirst();
        return adventure.append(adventure.text(origin), title);
    }

    private static List<TextLayout.Segment> segments(Adventure adventure, Object component,
                                                     Function<String, String> translations, boolean title)
            throws ReflectiveOperationException {
        List<TextLayout.Segment> segments = new ArrayList<>();
        // A title's text is uncoloured (shaded) until a component sets a colour.
        collect(adventure, component, null, false, !title, segments, translations);
        return segments;
    }

    /** Pre-order: a text component's content precedes its children; other components are opaque. */
    private static void collect(Adventure adventure, Object component, String inheritedFont, boolean inheritedBold,
                                boolean inheritedColour, List<TextLayout.Segment> segments,
                                Function<String, String> translations) throws ReflectiveOperationException {
        String font = adventure.font(component, inheritedFont);
        boolean bold = adventure.bold(component, inheritedBold);
        boolean coloured = inheritedColour || adventure.coloured(component);
        String translated = adventure.translated(component, translations);
        if (!adventure.isText(component) && translated == null) {
            segments.add(new TextLayout.Segment(null, font, bold, !coloured));
            return;
        }
        segments.add(new TextLayout.Segment(translated != null ? translated : adventure.content(component), font, bold,
                !coloured));
        for (Object child : adventure.children(component)) {
            collect(adventure, child, font, bold, coloured, segments, translations);
        }
    }

    private static Object rebuild(Adventure adventure, Object component, List<String> texts, int[] cursor,
                                  Function<String, String> translations) throws ReflectiveOperationException {
        String replacement = texts.get(cursor[0]++);
        boolean translated = adventure.translated(component, translations) != null;
        if (!adventure.isText(component) && !translated) {
            return replacement.isEmpty() ? component : adventure.append(adventure.text(replacement), component);
        }
        Object rebuilt = translated ? adventure.styled(adventure.text(replacement), component)
                : replacement.equals(adventure.content(component)) ? component
                : adventure.withContent(component, replacement);
        List<?> children = adventure.children(component);
        if (children.isEmpty()) return rebuilt;
        List<Object> replaced = new ArrayList<>(children.size());
        boolean changed = false;
        for (Object child : children) {
            Object next = rebuild(adventure, child, texts, cursor, translations);
            changed |= next != child;
            replaced.add(next);
        }
        return changed || translated ? adventure.withChildren(rebuilt, replaced) : rebuilt;
    }

    /** Adventure access through the (possibly relocated) runtime classes of a component. */
    record Adventure(Class<?> component, Class<?> textComponent, Method children, Method withChildren,
                     Method style, Method font, Method keyString, Method content, Method withContent,
                     Method text, Method append, Method decoration, Object boldDecoration, Method color) {
        static Adventure of(Object sample) throws ReflectiveOperationException {
            Adventure cached = ADVENTURE.get(sample.getClass());
            if (cached != null) return cached;
            String name = sample.getClass().getName();
            int split = name.indexOf(".text.");
            if (split < 0) throw new ClassNotFoundException("Not an Adventure component: " + name);
            String base = name.substring(0, split);
            ClassLoader loader = sample.getClass().getClassLoader();
            Class<?> component = Class.forName(base + ".text.Component", false, loader);
            Class<?> textComponent = Class.forName(base + ".text.TextComponent", false, loader);
            Class<?> style = Class.forName(base + ".text.format.Style", false, loader);
            Class<?> key = Class.forName(base + ".key.Key", false, loader);
            Class<?> decorations = Class.forName(base + ".text.format.TextDecoration", false, loader);
            Object bold = null;
            for (Object constant : decorations.getEnumConstants()) {
                if (((Enum<?>) constant).name().equals("BOLD")) bold = constant;
            }
            Adventure adventure = new Adventure(component, textComponent,
                    component.getMethod("children"), component.getMethod("children", List.class),
                    component.getMethod("style"), style.getMethod("font"), key.getMethod("asString"),
                    textComponent.getMethod("content"), textComponent.getMethod("content", String.class),
                    component.getMethod("text", String.class), component.getMethod("append", component),
                    style.getMethod("decoration", decorations), bold, style.getMethod("color"));
            ADVENTURE.put(sample.getClass(), adventure);
            return adventure;
        }

        boolean isText(Object value) { return textComponent.isInstance(value); }

        /** The pack string of an argument-free translatable component, or null. */
        String translated(Object value, Function<String, String> translations) throws ReflectiveOperationException {
            if (translations == null || isText(value)) return null;
            Class<?> type = value.getClass();
            Method key = null, arguments = null;
            for (Class<?> contract : type.getInterfaces()) {
                if (!contract.getName().endsWith(".text.TranslatableComponent")) continue;
                key = contract.getMethod("key");
                arguments = contract.getMethod("arguments");
            }
            if (key == null || !((List<?>) arguments.invoke(value)).isEmpty()) return null;
            return translations.apply((String) key.invoke(value));
        }

        /** A text component carrying {@code source}'s style. */
        Object styled(Object text, Object source) throws ReflectiveOperationException {
            return component.getMethod("style", this.style.getReturnType()).invoke(text, this.style.invoke(source));
        }

        String font(Object value, String inherited) throws ReflectiveOperationException {
            Object key = font.invoke(style.invoke(value));
            return key == null ? inherited : (String) keyString.invoke(key);
        }

        /** {@code own} with every property it leaves unset taken from {@code parent} (null: none). */
        Object inherit(Object own, Object parent) throws ReflectiveOperationException {
            if (parent == null) return own;
            Class<?> styleType = this.style.getReturnType();
            Class<?> strategy = Class.forName(styleType.getName() + "$Merge$Strategy", false, styleType.getClassLoader());
            Object ifAbsent = null;
            for (Object constant : strategy.getEnumConstants()) {
                if (((Enum<?>) constant).name().equals("IF_ABSENT_ON_TARGET")) ifAbsent = constant;
            }
            return styleType.getMethod("merge", styleType, strategy).invoke(own, parent, ifAbsent);
        }

        /** A text component with exactly {@code style}. */
        Object withStyle(Object text, Object style) throws ReflectiveOperationException {
            return component.getMethod("style", this.style.getReturnType()).invoke(text, style);
        }

        /** Whether a (merged) style draws no shadow: a transparent shadow colour (Adventure 4.18+). */
        boolean shadowless(Object style) throws ReflectiveOperationException {
            Method method;
            try {
                method = style.getClass().getMethod("shadowColor");
            } catch (NoSuchMethodException older) {
                return false;
            }
            method.setAccessible(true);
            Object colour = method.invoke(style);
            if (colour == null) return false;
            Method value = colour.getClass().getMethod("value");
            value.setAccessible(true);
            return (((Number) value.invoke(colour)).intValue() >>> 24) == 0;
        }

        /** Whether the component sets its own text colour. */
        boolean coloured(Object value) throws ReflectiveOperationException {
            return color.invoke(style.invoke(value)) != null;
        }

        /** Whether Java renders the component bold: TRUE/FALSE set it, NOT_SET inherits. */
        boolean bold(Object value, boolean inherited) throws ReflectiveOperationException {
            String state = ((Enum<?>) decoration.invoke(style.invoke(value), boldDecoration)).name();
            return state.equals("NOT_SET") ? inherited : state.equals("TRUE");
        }

        String content(Object value) throws ReflectiveOperationException { return (String) content.invoke(value); }
        List<?> children(Object value) throws ReflectiveOperationException { return (List<?>) children.invoke(value); }
        Object withContent(Object value, String text) throws ReflectiveOperationException { return withContent.invoke(value, text); }
        Object withChildren(Object value, List<?> list) throws ReflectiveOperationException { return withChildren.invoke(value, list); }
        Object text(String value) throws ReflectiveOperationException { return text.invoke(null, value); }
        Object append(Object parent, Object child) throws ReflectiveOperationException { return append.invoke(parent, child); }
    }
}
