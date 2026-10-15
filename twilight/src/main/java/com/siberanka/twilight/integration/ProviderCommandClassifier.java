package com.siberanka.twilight.integration;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Recognizes only provider commands that can replace generated content. */
public final class ProviderCommandClassifier {
    private static final Map<String, String> DIRECT = Map.of(
            "iareload", "ItemsAdder",
            "iazip", "ItemsAdder"
    );
    private static final Map<String, ProviderCommand> ROOTS = Map.ofEntries(
            Map.entry("itemsadder", command("ItemsAdder", "reload", "zip", "pack")),
            Map.entry("ia", command("ItemsAdder", "reload", "zip", "pack")),
            Map.entry("oraxen", command("Oraxen", "reload", "pack")),
            Map.entry("nexo", command("Nexo", "reload", "pack")),
            Map.entry("craftengine", command("CraftEngine", "reload", "pack")),
            Map.entry("ce", command("CraftEngine", "reload", "pack")),
            Map.entry("bettermodel", command("BetterModel", "reload", "pack")),
            Map.entry("bm", command("BetterModel", "reload", "pack")),
            Map.entry("modelengine", command("ModelEngine", "reload", "pack")),
            Map.entry("meg", command("ModelEngine", "reload", "pack")),
            Map.entry("realisticseasons", command("RealisticSeasons", "reload")),
            Map.entry("rs", command("RealisticSeasons", "reload"))
    );

    private ProviderCommandClassifier() {}

    public static Optional<ProviderMutation> classify(String rawCommand) {
        if (rawCommand == null) return Optional.empty();
        String normalized = rawCommand.strip().toLowerCase(Locale.ROOT);
        while (normalized.startsWith("/")) normalized = normalized.substring(1).stripLeading();
        if (normalized.isEmpty()) return Optional.empty();
        String[] parts = normalized.split("\\s+");
        String root = unqualified(parts[0]);
        String direct = DIRECT.get(root);
        if (direct != null) return Optional.of(new ProviderMutation(direct, root));
        if (parts.length < 2) return Optional.empty();
        ProviderCommand provider = ROOTS.get(root);
        String action = unqualified(parts[1]);
        if (provider == null || !provider.actions().contains(action)) return Optional.empty();
        return Optional.of(new ProviderMutation(provider.name(), root + ' ' + action));
    }

    private static String unqualified(String token) {
        int namespace = token.lastIndexOf(':');
        return namespace >= 0 ? token.substring(namespace + 1) : token;
    }

    private static ProviderCommand command(String name, String... actions) {
        return new ProviderCommand(name, Set.copyOf(Arrays.asList(actions)));
    }

    public record ProviderMutation(String provider, String command) {
        @Override public String toString() { return provider + ':' + command; }
    }

    private record ProviderCommand(String name, Set<String> actions) {}
}
