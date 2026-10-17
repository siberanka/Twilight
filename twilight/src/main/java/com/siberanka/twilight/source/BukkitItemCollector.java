package com.siberanka.twilight.source;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

public final class BukkitItemCollector {
    private static final int MAX_REPORTED_PROVIDER_ISSUES = 64;
    private static final List<ProviderProbe> PROVIDERS = List.of(
            new ProviderProbe("ItemsAdder", "dev.lone.itemsadder.api.CustomStack",
                    List.of("getNamespacedIdsInRegistry"), List.of("getInstance")),
            new ProviderProbe("CraftEngine", "net.momirealms.craftengine.bukkit.api.CraftEngineItems",
                    List.of("loadedItems"), List.of("byId")),
            new ProviderProbe("Oraxen", "io.th0rgal.oraxen.api.OraxenItems",
                    List.of("getItemNames"), List.of("getItemById")),
            new ProviderProbe("Nexo", "com.nexomc.nexo.api.NexoItems",
                    List.of("itemNames", "getItemNames"), List.of("itemFromId", "getItemById"))
    );

    private BukkitItemCollector() {}

    public static CollectionResult collect() {
        Map<String, CustomItemDescriptor> descriptors = new LinkedHashMap<>();
        Set<String> issues = new LinkedHashSet<>();
        Set<String> notices = new LinkedHashSet<>();
        collectRecipes(descriptors, notices);
        Bukkit.getOnlinePlayers().forEach(player -> {
            for (ItemStack stack : player.getInventory().getContents()) add("inventory", stack, descriptors);
        });
        for (ProviderProbe probe : PROVIDERS) probe.collect(descriptors, issues);
        collectBetterModel(descriptors, issues);
        collectModelEngine(descriptors, issues);
        return new CollectionResult(List.copyOf(descriptors.values()), List.copyOf(issues), List.copyOf(notices));
    }

    /**
     * Recipe results. The server converts each recipe while iterating, and a plugin's broken recipe (one with no
     * result) makes that throw; such recipes are skipped instead of stopping the whole build.
     */
    static void collectRecipes(Map<String, CustomItemDescriptor> descriptors, Set<String> notices) {
        int skipped = 0;
        java.util.Iterator<org.bukkit.inventory.Recipe> recipes;
        try {
            recipes = Bukkit.recipeIterator();
        } catch (RuntimeException | LinkageError unavailable) {
            notices.add("recipes could not be read: " + describe(unavailable));
            return;
        }
        // Bounded: an iterator that keeps failing without advancing cannot hold the build forever.
        for (int read = 0; read < 1_000_000; read++) {
            try {
                if (!recipes.hasNext()) break;
                add("recipe", recipes.next().getResult(), descriptors);
            } catch (RuntimeException | LinkageError broken) {
                skipped++;
                if (skipped > 10_000) break;
            }
        }
        if (skipped > 0) {
            notices.add(skipped + " recipe(s) could not be read (a plugin registered a recipe the server cannot convert,"
                    + " such as one without a result); their results are skipped");
        }
    }

    private static void collectModelEngine(Map<String, CustomItemDescriptor> output, Set<String> issues) {
        Plugin provider = Bukkit.getPluginManager().getPlugin("ModelEngine");
        if (provider == null || !provider.isEnabled()) return;
        int before = output.size();
        String registryFailure = null;
        try {
            collectModelEngineRegistry(provider, output);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            registryFailure = describe(failure);
        }
        if (output.size() > before) return;
        // The registry gave nothing (another API version, models still loading): the bone items are read from the
        // resource pack ModelEngine generated, so its models still reach Bedrock.
        try {
            for (CustomItemDescriptor bone : ModelEngineFiles.descriptors(provider.getDataFolder().toPath())) {
                String key = bone.baseItem() + '|' + bone.itemModel().orElse("") + '|';
                output.putIfAbsent(key, bone);
            }
        } catch (java.io.IOException | RuntimeException failure) {
            issue(issues, "ModelEngine's generated item definitions could not be read: " + describe(failure));
            return;
        }
        if (registryFailure != null && output.size() == before) {
            issue(issues, "ModelEngine model registry could not be read: " + registryFailure);
        }
    }

    private static void collectModelEngineRegistry(Plugin provider, Map<String, CustomItemDescriptor> output)
            throws ReflectiveOperationException {
        Class<?> api = Class.forName("com.ticxo.modelengine.api.ModelEngineAPI", false, provider.getClass().getClassLoader());
        Object instance = api.getMethod("getAPI").invoke(null);
        Object registry = api.getMethod("getModelRegistry").invoke(instance);
        Object values = registry.getClass().getMethod("getValues").invoke(registry);
        if (!(values instanceof Iterable<?> models)) throw new IllegalStateException("Unsupported model registry");
        for (Object model : models) {
            Object flatMap = model.getClass().getMethod("getFlatMap").invoke(model);
            if (!(flatMap instanceof Map<?, ?> bones)) throw new IllegalStateException("Unsupported bone registry");
            for (Object bone : bones.values()) {
                if (!Boolean.TRUE.equals(bone.getClass().getMethod("isRenderer").invoke(bone))) continue;
                Object modelData = bone.getClass().getMethod("getModelData").invoke(bone);
                if (modelData == null) continue;
                Object stacks = modelData.getClass().getMethod("createItemStack").invoke(modelData);
                if (!(stacks instanceof Iterable<?> items)) throw new IllegalStateException("Unsupported bone item collection");
                for (Object stack : items) add("ModelEngine", stack, output);
            }
        }
    }

    private static void collectBetterModel(Map<String, CustomItemDescriptor> output, Set<String> issues) {
        Plugin provider = Bukkit.getPluginManager().getPlugin("BetterModel");
        if (provider == null || !provider.isEnabled()) return;
        try {
            ClassLoader loader = provider.getClass().getClassLoader();
            Class<?> api = Class.forName("kr.toxicity.model.api.BetterModel", false, loader);
            Object registry = api.getMethod("models").invoke(null);
            if (!(registry instanceof Iterable<?> models)) throw new IllegalStateException("Unsupported model registry");
            for (Object model : models) {
                try (var groups = (java.util.stream.Stream<?>) model.getClass().getMethod("flatten").invoke(model)) {
                    for (Object group : groups.toList()) {
                        Object transformed = group.getClass().getMethod("getItemStack").invoke(group);
                        Object platformItem = transformed.getClass().getMethod("itemStack").invoke(transformed);
                        Object stack = platformItem.getClass().getMethod("source").invoke(platformItem);
                        add("BetterModel", stack, output);
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            issue(issues, "BetterModel model registry could not be read: " + describe(failure));
        }
    }

    private static void add(String provider, Object possibleStack, Map<String, CustomItemDescriptor> output) {
        ItemStack stack = unwrapStack(possibleStack);
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) return;
        ItemMeta meta = stack.getItemMeta();
        Optional<String> model = itemModel(meta);
        OptionalInt customModelData = meta.hasCustomModelData()
                ? OptionalInt.of(meta.getCustomModelData()) : OptionalInt.empty();
        if (model.isEmpty() && customModelData.isEmpty()) return;
        String base = stack.getType().getKey().toString();
        String display = legacyDisplayName(meta, base);
        String key = base + '|' + model.orElse("") + '|' + (customModelData.isPresent() ? customModelData.getAsInt() : "");
        output.putIfAbsent(key, new CustomItemDescriptor(provider, base, model, customModelData, display,
                equipment(stack, meta)));
    }

    /**
     * Where the item is worn and the equipment asset drawn there. Paper's data components include the base item's
     * own (a custom chainmail helmet keeps chainmail's asset unless the provider replaces it); Spigot's item meta only
     * knows a provider's own {@code equippable}. Servers before 1.21.2 have neither: their items stay without one.
     */
    static Optional<CustomItemDescriptor.Equipment> equipment(ItemStack stack, ItemMeta meta) {
        try {
            Object equippable = paperEquippable(stack);
            if (equippable == null && meta != null && Boolean.TRUE.equals(call(meta, "hasEquippable"))) {
                equippable = call(meta, "getEquippable");
            }
            if (equippable == null) return Optional.empty();
            Object slot = firstResult(equippable, "slot", "getSlot");
            if (!(slot instanceof Enum<?> value)) return Optional.empty();
            String name = switch (value.name()) {
                case "HEAD" -> "head";
                case "CHEST" -> "chest";
                case "LEGS" -> "legs";
                case "FEET" -> "feet";
                case "BODY" -> "body";
                default -> null;
            };
            if (name == null) return Optional.empty();
            Object asset = firstResult(equippable, "assetId", "getAssetId", "getModel");
            Optional<String> assetId = Optional.empty();
            if (asset != null) {
                Object text = asset instanceof NamespacedKey key ? key.toString() : firstResult(asset, "asString");
                String id = String.valueOf(text == null ? asset : text);
                if (id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") && id.length() <= 256) assetId = Optional.of(id);
            }
            return Optional.of(new CustomItemDescriptor.Equipment(name, assetId));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            return Optional.empty();
        }
    }

    private static volatile Object equippableType;
    private static volatile Method getData;

    private static Object paperEquippable(ItemStack stack) throws ReflectiveOperationException {
        if (equippableType == null) {
            Class<?> types;
            try {
                types = Class.forName("io.papermc.paper.datacomponent.DataComponentTypes", false, stack.getClass().getClassLoader());
            } catch (ClassNotFoundException notPaper) {
                return null;
            }
            Object type = types.getField("EQUIPPABLE").get(null);
            Method method = null;
            for (Method candidate : ItemStack.class.getMethods()) {
                if (candidate.getName().equals("getData") && candidate.getParameterCount() == 1
                        && candidate.getParameterTypes()[0].isInstance(type)) method = candidate;
            }
            if (method == null) return null;
            getData = method;
            equippableType = type;
        }
        return getData.invoke(stack, equippableType);
    }

    private static Object call(Object target, String name) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(name);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static Object firstResult(Object target, String... names) throws ReflectiveOperationException {
        for (String name : names) {
            try {
                return call(target, name);
            } catch (NoSuchMethodException next) {
                // The API names this differently on this server version.
            }
        }
        return null;
    }

    private static Optional<String> itemModel(ItemMeta meta) {
        try {
            Method method = meta.getClass().getMethod("getItemModel");
            Object value = method.invoke(meta);
            if (value instanceof NamespacedKey key) return Optional.of(key.toString());
            if (value != null) return Optional.of(value.toString());
        } catch (ReflectiveOperationException ignored) {
            // Older servers expose only legacy custom model data.
        }
        return Optional.empty();
    }

    @SuppressWarnings("deprecation") // Kept as the cross-platform Spigot/Paper string representation.
    private static String legacyDisplayName(ItemMeta meta, String fallback) {
        return meta.hasDisplayName() ? meta.getDisplayName() : fallback;
    }

    private static ItemStack unwrapStack(Object value) {
        if (value instanceof ItemStack stack) return stack;
        if (value == null) return null;
        for (String methodName : List.of("getItemStack", "build", "buildBukkitItem")) {
            try {
                Method method = value.getClass().getMethod(methodName);
                Object result = method.invoke(value);
                if (result instanceof ItemStack stack) return stack;
            } catch (ReflectiveOperationException ignored) {
                // Try the next stable provider shape.
            }
        }
        return null;
    }

    private static void issue(Set<String> issues, String message) {
        if (issues.size() < MAX_REPORTED_PROVIDER_ISSUES) {
            issues.add(message);
        } else if (issues.size() == MAX_REPORTED_PROVIDER_ISSUES) {
            issues.add("Additional provider API issues were suppressed after " + MAX_REPORTED_PROVIDER_ISSUES + " entries.");
        }
    }

    private static String describe(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) current = current.getCause();
        String message = current.getMessage();
        return current.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }

    /**
     * @param issues  incomplete provider discovery (strict conversion refuses to publish on these)
     * @param notices other plugins' broken state that only costs a few items (logged, never blocking)
     */
    public record CollectionResult(List<CustomItemDescriptor> items, List<String> issues, List<String> notices) {
        public CollectionResult {
            items = List.copyOf(items);
            issues = List.copyOf(issues);
            notices = List.copyOf(notices);
        }

        public CollectionResult(List<CustomItemDescriptor> items, List<String> issues) {
            this(items, issues, List.of());
        }
    }

    private record ProviderProbe(String name, String className, List<String> registryMethods, List<String> itemMethods) {
        void collect(Map<String, CustomItemDescriptor> output, Set<String> issues) {
            Plugin provider = Bukkit.getPluginManager().getPlugin(name);
            if (provider == null || !provider.isEnabled()) return;
            try {
                Class<?> api = Class.forName(className, false, provider.getClass().getClassLoader());
                Method registry = findStaticMethod(api, registryMethods);
                if (registry == null) {
                    issue(issues, name + " API mismatch: no supported item registry method on " + className);
                    return;
                }
                Object registryValue = registry.invoke(null);
                List<?> ids = ProviderRegistryEntries.ids(registryValue).orElse(null);
                if (ids == null) {
                    issue(issues, name + " API mismatch: " + registry.getName() +
                            " did not return a map, collection, iterable, iterator, stream, or array");
                    return;
                }
                Method itemMethod = findStaticMethod(api, itemMethods, String.class);
                if (itemMethod == null) {
                    issue(issues, name + " API mismatch: no supported item lookup method on " + className);
                    return;
                }
                for (Object id : ids) {
                    try {
                        Object providerItem = itemMethod.invoke(null, String.valueOf(id));
                        ItemStack stack = unwrapStack(providerItem);
                        if (providerItem != null && stack == null) {
                            issue(issues, name + " item " + id + " returned unsupported type " + providerItem.getClass().getName());
                        } else {
                            add(name, stack, output);
                        }
                    } catch (ReflectiveOperationException | LinkageError failure) {
                        issue(issues, name + " item " + id + " could not be read: " + describe(failure));
                    }
                }
            } catch (ClassNotFoundException failure) {
                issue(issues, name + " is enabled but its public API class is unavailable: " + className);
            } catch (ReflectiveOperationException | LinkageError failure) {
                issue(issues, name + " public API probe failed: " + describe(failure));
            }
        }

        private static Method findStaticMethod(Class<?> api, List<String> candidates, Class<?>... arguments) {
            for (String candidate : candidates) {
                try {
                    Method method = api.getMethod(candidate, arguments);
                    if (Modifier.isStatic(method.getModifiers())) return method;
                } catch (NoSuchMethodException ignored) { }
            }
            return null;
        }
    }
}
