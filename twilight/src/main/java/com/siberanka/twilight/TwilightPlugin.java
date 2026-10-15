/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight;

import com.siberanka.twilight.config.TwilightConfig;
import com.siberanka.twilight.compiler.BedrockPackCompiler;
import com.siberanka.twilight.compiler.BuildResult;
import com.siberanka.twilight.compiler.ConversionException;
import com.siberanka.twilight.api.TwilightApi;
import com.siberanka.twilight.api.event.TwilightBuildCompleteEvent;
import com.siberanka.twilight.api.event.TwilightDeployCompleteEvent;
import com.siberanka.twilight.api.event.TwilightOperationFailedEvent;
import com.siberanka.twilight.api.event.TwilightScanCompleteEvent;
import com.siberanka.twilight.deploy.DeploymentResult;
import com.siberanka.twilight.deploy.GeyserDeploymentService;
import com.siberanka.twilight.report.ContentReportWriter;
import com.siberanka.twilight.source.ContentInspector;
import com.siberanka.twilight.source.ContentReport;
import com.siberanka.twilight.source.ContentSource;
import com.siberanka.twilight.source.BukkitItemCollector;
import com.siberanka.twilight.source.CustomItemDescriptor;
import com.siberanka.twilight.source.SourceDiscovery;
import com.siberanka.twilight.source.SourceFingerprint;
import com.siberanka.twilight.source.WorldLayout;
import com.siberanka.twilight.logging.OperationLog;
import com.siberanka.twilight.integration.ProviderHookManager;
import com.siberanka.twilight.integration.ProviderCommandClassifier;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.event.server.ServerLoadEvent;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.stream.Collectors;

public final class TwilightPlugin extends JavaPlugin {
    private final AtomicBoolean operationRunning = new AtomicBoolean();
    private final AtomicReference<ContentReport> lastReport = new AtomicReference<>();
    private final AtomicReference<String> lastSuccessfulInputFingerprint = new AtomicReference<>();
    private final AtomicBoolean automaticBuildPending = new AtomicBoolean();
    private ExecutorService worker;
    private ServerScheduler scheduler;
    private Path serverRoot;
    private TwilightConfig config;
    private GeyserDeploymentService deployment;
    private ProviderHookManager providerHooks;
    private AutoCloseable displayBridge;
    private com.siberanka.twilight.integration.text.GeyserTextLayoutBridge textBridge;
    private com.siberanka.twilight.integration.world.GeyserBiomeBridge biomeBridge;
    private com.siberanka.twilight.integration.display.GeyserRiderNames riderNames;
    private com.siberanka.twilight.integration.proxy.ProxyPackChannel proxyChannel;
    /** The custom items this server shows in item displays; only they (and model bones) get display models. */
    private com.siberanka.twilight.integration.display.DisplayedItems displayedItems;
    private com.siberanka.twilight.integration.heads.PlayerHeads playerHeads;
    private com.siberanka.twilight.integration.menus.MenuAudit menuAudit;
    private volatile Object[] menuTable = {null, -1L};
    private com.siberanka.twilight.geyser.CustomSkulls customSkulls;
    /** What the twilight-proxy in front of this server shows (its item displays need the display models). */
    private volatile com.siberanka.twilight.integration.proxy.ProxyFeatures proxyFeatures;
    private com.siberanka.twilight.integration.text.GeyserLanguageBridge languageBridge;
    private com.siberanka.twilight.integration.pack.GeyserPackHosting packHosting;
    private com.siberanka.twilight.geyser.LoadingGuard loadingGuard;
    private com.siberanka.twilight.geyser.CustomBlocks customBlocks;
    private volatile com.siberanka.twilight.update.UpdateCheck updates;
    private volatile boolean notifyUpdatePlayers = true;

    /**
     * Before any plugin enables, so before Geyser loads its folder: moves Twilight files this server does not own
     * out of its Geyser (item mappings and packs from older versions, copies or sync tools), which would otherwise
     * register items twice or send a second Twilight pack.
     */
    @Override
    public void onLoad() {
        try {
            saveDefaultConfig();
            reloadConfig();
            startCustomBlocks();
            startPlayerHeads();
            if (!getConfig().getBoolean("geyser.retire-stale-files", true)) return;
            Path root = getServer().getWorldContainer().toPath().toAbsolutePath().normalize();
            var service = new GeyserDeploymentService(root, getDataFolder().toPath(), TwilightConfig.read(getConfig(), root));
            for (var file : service.retireStaleFiles()) {
                getLogger().warning("Moved " + file.file() + " out of Geyser to "
                        + getDataFolder().toPath().toAbsolutePath().normalize().relativize(file.to().toAbsolutePath().normalize())
                        + ": a Twilight file this server does not own (older version, copy or sync tool) that would register"
                        + " items twice or send a second Twilight pack.");
            }
        } catch (java.io.IOException noLocalGeyser) {
            // Geyser is not on this server (the usual proxy layout): nothing to clean up.
        } catch (RuntimeException failure) {
            getLogger().log(Level.WARNING, "Could not check Geyser for stale Twilight files", failure);
        }
    }

    /**
     * Geyser-Spigot registers custom blocks and items when it enables, right after this load phase: the blocks of
     * the deployed pack are handed to it then (a build that changes them needs a restart, like items).
     */
    /** {@code geyser.player-heads}: player heads show their skins on Bedrock (Geyser custom skulls). */
    private boolean playerHeadsEnabled() {
        return getConfig().getBoolean("geyser.player-heads", true);
    }

    /** Hands the deployed pack's player heads to Geyser-Spigot, which registers skulls when it enables. */
    private void startPlayerHeads() {
        if (!playerHeadsEnabled() || getServer().getPluginManager().getPlugin("Geyser-Spigot") == null) return;
        try {
            Path root = getServer().getWorldContainer().toPath().toAbsolutePath().normalize();
            TwilightConfig settings = TwilightConfig.read(getConfig(), root);
            Path pack;
            try {
                pack = settings.sendPackToBedrock()
                        ? new GeyserDeploymentService(root, getDataFolder().toPath(), settings).resolveGeyserDirectory().resolve("packs/twilight.zip")
                        : getDataFolder().toPath().resolve(com.siberanka.twilight.deploy.PackExport.PACK);
            } catch (java.io.IOException noFolder) {
                pack = getDataFolder().toPath().resolve(com.siberanka.twilight.deploy.PackExport.PACK);
            }
            Path served = pack;
            customSkulls = com.siberanka.twilight.geyser.CustomSkulls.start(this, () -> java.util.List.of(served),
                    getLogger()::info, (message, failure) -> getLogger().log(Level.WARNING, message, failure));
        } catch (RuntimeException | LinkageError failure) {
            getLogger().log(Level.WARNING, "Player head skins are unavailable for this Geyser build; Bedrock shows Steve's head.", failure);
        }
    }

    /**
     * Learns the player heads this server shows and reads the ones its plugins' configurations name (off the server
     * thread, a little after start so that HeadDatabase has loaded); new heads go into the next build.
     */
    private void startHeadLearning() {
        if (!playerHeadsEnabled()) return;
        try {
            playerHeads = com.siberanka.twilight.integration.heads.PlayerHeads.start(this, getDataFolder().toPath().resolve("player-heads.txt"));
            playerHeads.onNew(added -> {
                if (itemDisplayModels() || getServer().getPluginManager().getPlugin("Geyser-Spigot") != null
                        || proxyChannel != null) scheduleBuild(added.size() + " new player head(s)", 1200L);
            });
            var tracked = playerHeads;
            scheduler.delayed(() -> CompletableFuture.runAsync(() -> {
                try {
                    var found = com.siberanka.twilight.integration.heads.HeadSources.scan(getDataFolder().toPath().getParent(),
                            this::headDatabase);
                    tracked.report(com.siberanka.twilight.integration.heads.HeadSources.heads(found));
                    getLogger().info("Player heads in plugin configurations: " + found.skins().size() + " skin(s), "
                            + found.names().size() + " player name(s), " + found.headDatabase().size()
                            + " HeadDatabase id(s) in " + found.files() + " file(s).");
                } catch (java.io.IOException | RuntimeException failure) {
                    getLogger().log(Level.WARNING, "Could not read the plugins' configurations for player heads", failure);
                }
            }, worker), 200L);
        } catch (RuntimeException | LinkageError failure) {
            getLogger().log(Level.WARNING, "Could not watch player heads; Bedrock keeps Steve's head for heads it has not seen.", failure);
        }
    }

    /** The Base64 profile HeadDatabase stores for an id, or null without HeadDatabase. */
    private String headDatabase(String id) {
        try {
            Class<?> api = Class.forName("me.arcaniax.hdb.api.HeadDatabaseAPI");
            Object instance = api.getConstructor().newInstance();
            Object value = api.getMethod("getBase64", String.class).invoke(instance, id);
            return value instanceof String text ? text : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError absent) {
            return null;
        }
    }

    private void startCustomBlocks() {
        if (!getConfig().getBoolean("geyser.custom-blocks", true)) return;
        if (getServer().getPluginManager().getPlugin("Geyser-Spigot") == null) return;
        try {
            Path root = getServer().getWorldContainer().toPath().toAbsolutePath().normalize();
            TwilightConfig settings = TwilightConfig.read(getConfig(), root);
            var service = new GeyserDeploymentService(root, getDataFolder().toPath(), settings);
            Path pack;
            try {
                pack = settings.sendPackToBedrock() ? service.resolveGeyserDirectory().resolve("packs/twilight.zip")
                        : getDataFolder().toPath().resolve(com.siberanka.twilight.deploy.PackExport.PACK);
            } catch (java.io.IOException noFolder) {
                pack = getDataFolder().toPath().resolve(com.siberanka.twilight.deploy.PackExport.PACK);
            }
            Path served = pack;
            customBlocks = com.siberanka.twilight.geyser.CustomBlocks.start(this, () -> java.util.List.of(served),
                    getLogger()::info, (message, failure) -> getLogger().log(Level.WARNING, message, failure));
        } catch (RuntimeException | LinkageError failure) {
            getLogger().log(Level.WARNING, "Custom blocks are unavailable for this Geyser build; they keep the vanilla look on Bedrock.", failure);
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        serverRoot = getServer().getWorldContainer().toPath().toAbsolutePath().normalize();
        scheduler = new ServerScheduler(this);
        worker = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Twilight-Worker");
            thread.setDaemon(true);
            return thread;
        });
        loadServices();
        if (getServer().getPluginManager().getPlugin("Geyser-Spigot") != null) {
            try {
                displayBridge = com.siberanka.twilight.integration.display.GeyserDisplayBridge.forPack(this,
                        servedPack(), getLogger(), itemDisplayModels());
            } catch (Exception | LinkageError failure) {
                getLogger().log(Level.SEVERE, "Live item-display bridge is unavailable; model animation parity is not supported.", failure);
            }
            if (config.javaTranslations()) {
                try {
                    languageBridge = com.siberanka.twilight.integration.text.GeyserLanguageBridge.create(this,
                            servedPack(), getLogger());
                } catch (Exception | LinkageError failure) {
                    getLogger().log(Level.WARNING, "Resource-pack translations are unavailable for this Geyser build.", failure);
                }
            }
            try {
                riderNames = com.siberanka.twilight.integration.display.GeyserRiderNames.create(this, getLogger());
            } catch (Exception | LinkageError failure) {
                getLogger().log(Level.WARNING, "Java's rider name rule is unavailable for this Geyser build.", failure);
            }
            if (config.bedrockBiomeMatching()) {
                try {
                    var resender = new com.siberanka.twilight.integration.world.ChunkResender(this);
                    biomeBridge = com.siberanka.twilight.integration.world.GeyserBiomeBridge.create(this,
                            servedPack(), getLogger(), resender::request);
                } catch (Exception | LinkageError failure) {
                    getLogger().log(Level.WARNING, "Custom biome matching is unavailable for this Geyser build.", failure);
                }
            }
            startPackHost();
            try {
                loadingGuard = com.siberanka.twilight.geyser.LoadingGuard.start(this,
                        getConfig().getInt("geyser.loading-protection-seconds", 300), getLogger()::info);
            } catch (Exception | LinkageError failure) {
                getLogger().log(Level.WARNING, "Loading protection is unavailable for this Geyser build.", failure);
            }
            if (config.javaTextLayout()) {
                try {
                    textBridge = com.siberanka.twilight.integration.text.GeyserTextLayoutBridge.create(this,
                            servedPack(), getLogger(),
                            config.javaTextSurfaces());
                } catch (Exception | LinkageError failure) {
                    getLogger().log(Level.SEVERE, "Java text layout is unavailable; Bedrock chest titles of a pack "
                            + "built with the layout will be misplaced. Rebuild with ui.java-text-layout: false.", failure);
                }
            }
        }

        if (getConfig().getBoolean("proxy.share-pack", true)) {
            try {
                proxyChannel = com.siberanka.twilight.integration.proxy.ProxyPackChannel.start(this, scheduler::delayed,
                        getDataFolder().toPath().resolve(com.siberanka.twilight.deploy.PackExport.PACK),
                        getConfig().getString("proxy.secret", ""));
                if (proxyChannel != null) {
                    proxyChannel.onCapabilities(this::proxyCapabilities);
                    proxyChannel.onDisplayed(keys -> {
                        var items = displayedItems;
                        if (items != null) items.report(keys);
                    });
                }
            } catch (RuntimeException | LinkageError failure) {
                getLogger().log(Level.WARNING, "twilight-proxy pack sharing is unavailable.", failure);
            }
        }

        TwilightCommand command = new TwilightCommand(this);
        var registered = getCommand("twilight");
        if (registered == null) throw new IllegalStateException("Twilight command is missing from plugin.yml");
        registered.setExecutor(command);
        registered.setTabCompleter(command);
        getServer().getServicesManager().register(TwilightApi.class, new Api(), this, ServicePriority.Normal);
        providerHooks = new ProviderHookManager(this, reason -> scheduleProviderBuild(reason, config.startupDelayTicks()));
        getServer().getPluginManager().registerEvents(new AutomationListener(), this);
        for (org.bukkit.plugin.Plugin installed : getServer().getPluginManager().getPlugins()) providerHooks.register(installed);
        configureUpdates();

        getLogger().info("Twilight server-side content compiler enabled. Vanilla overrides: " + config.vanillaOverride());
        if (getServer().getPluginManager().getPlugin("Geyser-Spigot") == null) getLogger().info(geyserElsewhere());
        if (!"DEFINITION".toLowerCase(java.util.Locale.getDefault()).equals("definition")) {
            getLogger().warning("This JVM locale can break Geyser custom-item enum parsing. If Geyser rejects definition mappings, "
                    + "restart with -Duser.language=en -Duser.country=US; keep the generated mapping names unchanged.");
        }
        try {
            displayedItems = com.siberanka.twilight.integration.display.DisplayedItems.start(this,
                    getDataFolder().toPath().resolve("displayed-items.txt"));
            displayedItems.onNew(this::newDisplayedItems);
            startHeadLearning();
            menuAudit = com.siberanka.twilight.integration.menus.MenuAudit.start(this,
                    getDataFolder().toPath().resolve("reports/menus.json"), this::servedLayoutTable);
            var tracked = displayedItems;
            if (displayBridge instanceof com.siberanka.twilight.integration.display.GeyserDisplayBridge bridge) {
                bridge.onUnshown((session, keys) -> tracked.report(keys));
            }
            scheduler.delayed(displayedItems::scanLoaded, 20L);
        } catch (RuntimeException | LinkageError failure) {
            getLogger().log(Level.WARNING, "Could not watch item displays; every custom item gets a display model.", failure);
        }
        if (config.autoBuildOnStartup()) {
            scheduleBuild("startup", config.startupDelayTicks());
        }
    }

    /**
     * Items appeared in item displays for the first time: the next build gives them display models. A minute's
     * delay gathers the items of a busy start or a newly loaded area into one build.
     */
    private void newDisplayedItems(java.util.List<String> keys) {
        var heads = playerHeads;
        if (heads != null) {
            java.util.List<String> skins = keys.stream().filter(key -> key.startsWith("head=")).map(key -> key.substring(5)).toList();
            if (!skins.isEmpty()) heads.report(skins);
        }
        boolean seen = displayVariantsSeen();
        long count = keys.stream().filter(com.siberanka.twilight.integration.display.DisplayedItems::matters)
                .filter(key -> seen || com.siberanka.twilight.integration.display.DisplayedItems.own(key)).count();
        if (count == 0 || !itemDisplayModels()) return;
        scheduleBuild(count + " item(s) or block(s) newly seen in displays", 1200L);
    }

    /**
     * {@code geyser.item-display-variants}: {@code seen} (default) gives display models to the items this server
     * shows in item displays and to model bones; {@code all} to every custom item, at a much longer client
     * loading time.
     */
    private boolean displayVariantsSeen() {
        String setting = getConfig().getString("geyser.item-display-variants", "seen").strip().toLowerCase(java.util.Locale.ROOT);
        if (!setting.equals("seen") && !setting.equals("all")) {
            getLogger().warning("geyser.item-display-variants must be seen or all, not '" + setting + "'; using seen.");
        }
        return !setting.equals("all");
    }

    @Override
    public void onDisable() {
        if (updates != null) updates.close();
        if (languageBridge != null) {
            try { languageBridge.close(); }
            catch (Exception failure) { getLogger().log(Level.WARNING, "Could not close translations", failure); }
        }
        if (proxyChannel != null) proxyChannel.close();
        if (displayedItems != null) displayedItems.close();
        if (playerHeads != null) playerHeads.close();
        if (menuAudit != null) menuAudit.close();
        if (customSkulls != null) customSkulls.close();
        if (loadingGuard != null) loadingGuard.close();
        if (customBlocks != null) customBlocks.close();
        if (packHosting != null) packHosting.close();
        if (riderNames != null) {
            try { riderNames.close(); }
            catch (Exception failure) { getLogger().log(Level.WARNING, "Could not close the rider name rule", failure); }
        }
        if (biomeBridge != null) {
            try { biomeBridge.close(); }
            catch (Exception failure) { getLogger().log(Level.WARNING, "Could not close biome matching", failure); }
        }
        if (textBridge != null) {
            try { textBridge.close(); }
            catch (Exception failure) { getLogger().log(Level.WARNING, "Could not close text layout", failure); }
        }
        if (displayBridge != null) {
            try { displayBridge.close(); }
            catch (Exception failure) { getLogger().log(Level.WARNING, "Could not close display bridge", failure); }
        }
        getServer().getServicesManager().unregisterAll(this);
        if (worker != null) worker.shutdownNow();
    }

    private void loadServices() {
        reloadConfig();
        config = TwilightConfig.read(getConfig(), serverRoot);
        if (deployment == null) deployment = new GeyserDeploymentService(serverRoot, getDataFolder().toPath(), config);
        else deployment.reconfigure(config);
    }

    void scan(CommandSender sender, boolean buildRequested) {
        scan(sender, buildRequested, false);
    }

    private void scan(CommandSender sender, boolean buildRequested, boolean skipUnchanged) {
        if (!operationRunning.compareAndSet(false, true)) {
            send(sender, "Another Twilight operation is already running.");
            return;
        }
        String operation = buildRequested ? "convert" : "scan";
        OperationLog operationLog;
        try {
            operationLog = OperationLog.create(getDataFolder().toPath(), operation);
        } catch (Exception failure) {
            operationRunning.set(false);
            send(sender, "Could not create operation log: " + rootMessage(failure));
            return;
        }
        Set<Path> runtimeWorlds = Bukkit.getWorlds().stream().map(World::getWorldFolder).map(java.io.File::toPath)
                .collect(Collectors.toUnmodifiableSet());
        BukkitItemCollector.CollectionResult runtimeCollection = collectRuntimeItems();
        // Decided once per build: the proxy's statement can change while the build runs.
        boolean displays = itemDisplayModels();
        java.util.Set<String> shownItems = !displays || !displayVariantsSeen() ? null
                : displayedItems == null ? null : displayedItems.keys().stream()
                        .filter(com.siberanka.twilight.integration.display.DisplayedItems::matters)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
        var heads = playerHeads;
        java.util.Set<String> headSkins = heads == null || !playerHeadsEnabled() ? java.util.Set.of() : heads.skins();
        java.util.Set<String> headNames = heads == null || !playerHeadsEnabled() ? java.util.Set.of() : heads.names();
        java.util.Set<String> shownOwn = !displays || displayedItems == null ? java.util.Set.of()
                : displayedItems.keys().stream().filter(com.siberanka.twilight.integration.display.DisplayedItems::own)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
        List<CustomItemDescriptor> liveItems = modelBones(runtimeCollection.items(), displays);
        String minecraftVersion = Bukkit.getMinecraftVersion();
        operationLog.info("runtime-items", liveItems.size());
        for (String issue : runtimeCollection.issues()) operationLog.warn("provider-api", issue);
        for (String notice : runtimeCollection.notices()) {
            operationLog.warn("runtime-items", notice);
            if (reportedRuntimeNotices.add(notice)) getLogger().warning(notice);
        }
        operationLog.info("runtime-worlds", runtimeWorlds);
        send(sender, (buildRequested ? "Build" : "Scan") + " started off the server thread.");
        CompletableFuture.supplyAsync(() -> {
            try {
                // Strict publication keeps the last good pack; with none deployed yet, a first pack that leaves
                // out the reported content serves Bedrock players better than no pack at all.
                boolean firstPack = buildRequested && config.strict() && config.deployAfterBuild()
                        && !java.nio.file.Files.isRegularFile(servedPack());
                if (buildRequested && config.strict() && !runtimeCollection.issues().isEmpty()) {
                    if (!firstPack) throw new ConversionException("Strict conversion rejected incomplete provider item discovery: "
                            + runtimeCollection.issues().getFirst(), runtimeCollection.issues());
                    operationLog.warn("first-pack", "provider item discovery is incomplete: " + runtimeCollection.issues().getFirst());
                }
                Set<Path> worlds = WorldLayout.discover(serverRoot, runtimeWorlds);
                operationLog.info("world-discovery", worlds);
                List<ContentSource> sources = new SourceDiscovery(serverRoot, config).discover(worlds);
                operationLog.info("source-discovery", sources);
                WorldInputs world = buildRequested ? worldInputs(operationLog) : WorldInputs.NONE;
                // Build options that change the pack without changing its sources.
                String inputFingerprint = SourceFingerprint.compute(sources, liveItems, config) + world.fingerprint()
                        + "|displays=" + displays + "|shown=" + (shownItems == null ? "all"
                                : Integer.toHexString(new java.util.TreeSet<>(shownItems).hashCode()))
                        + "|own=" + Integer.toHexString(new java.util.TreeSet<>(shownOwn).hashCode())
                        + "|heads=" + Integer.toHexString(java.util.Objects.hash(headSkins, headNames)) + "|pocket=" + pocketContainerLayout()
                        + "|blocks=" + getConfig().getBoolean("geyser.custom-blocks", true)
                        + "|cell=" + getConfig().getInt("ui.max-glyph-cell", 512);
                operationLog.info("input-fingerprint", inputFingerprint);
                ContentReport report = new ContentInspector(config).inspect(sources);
                operationLog.info("content-report", report);
                try {
                    ContentReportWriter.write(getDataFolder().toPath().resolve("reports/content-report.json"), report);
                } catch (java.io.IOException | RuntimeException reportFailure) {
                    // The report is diagnostic only; it never stops a build.
                    operationLog.warn("content-report-file", rootMessage(reportFailure));
                    getLogger().warning("Could not write reports/content-report.json: " + rootMessage(reportFailure));
                }
                lastReport.set(report);
                boolean unchanged = buildRequested && skipUnchanged &&
                        inputFingerprint.equals(lastSuccessfulInputFingerprint.get());
                if (unchanged) operationLog.info("build-skip", "normalized inputs are unchanged");
                BuildResult build = null;
                if (buildRequested && !unchanged) {
                    try {
                        build = new BedrockPackCompiler(getDataFolder().toPath(), config, minecraftVersion)
                                .withServerBiomes(world.biomes(), world.current())
                                .withNameplatePlugin(world.nameplates()).withItemDisplays(displays).withDisplayedItems(shownItems).withDisplayedOwnModels(shownOwn)
                                .withPlayerHeads(headSkins, headNames, getDataFolder().toPath().resolve("cache/skins"), playerHeadsEnabled())
                                .withMaxGlyphCell(getConfig().getInt("ui.max-glyph-cell", 512))
                                .withPocketContainerLayout(pocketContainerLayout())
                                .withCustomBlocks(getConfig().getBoolean("geyser.custom-blocks", true))
                                .build(sources, liveItems);
                    } catch (ConversionException rejected) {
                        if (!firstPack) throw rejected;
                        operationLog.warn("first-pack", rejected.problems());
                        getLogger().warning("Strict conversion found " + rejected.problems().size() + " problem(s) and no "
                                + "Bedrock pack is deployed yet: deploying a first pack without that content. Later builds "
                                + "stay strict. Details: " + operationLog.path());
                        build = new BedrockPackCompiler(getDataFolder().toPath(), config.withStrict(false), minecraftVersion)
                                .withServerBiomes(world.biomes(), world.current())
                                .withNameplatePlugin(world.nameplates()).withItemDisplays(displays).withDisplayedItems(shownItems).withDisplayedOwnModels(shownOwn)
                                .withPlayerHeads(headSkins, headNames, getDataFolder().toPath().resolve("cache/skins"), playerHeadsEnabled())
                                .withMaxGlyphCell(getConfig().getInt("ui.max-glyph-cell", 512))
                                .withPocketContainerLayout(pocketContainerLayout())
                                .withCustomBlocks(getConfig().getBoolean("geyser.custom-blocks", true))
                                .build(sources, liveItems);
                    }
                }
                if (build != null) operationLog.info("build-result", build);
                DeploymentResult deployed = null;
                if (build != null) {
                    com.siberanka.twilight.deploy.PackExport.write(build.outputDirectory(), getDataFolder().toPath());
                    operationLog.info("export", getDataFolder().toPath().resolve(com.siberanka.twilight.deploy.PackExport.PACK));
                    if (proxyChannel != null) proxyChannel.announceToAnyPlayer();
                    if (config.deployAfterBuild()) {
                        if (localGeyser()) deployed = deployment.deploy(build.outputDirectory());
                        else operationLog.info("deploy-skip", "no Geyser on this server; use the exported pack and mappings");
                    }
                }
                if (deployed != null) {
                    operationLog.info("deploy-result", deployed);
                    reloadGeyser(operationLog, deployed);
                }
                return new ScanOutcome(report, build, deployed, inputFingerprint, unchanged);
            } catch (Exception exception) {
                throw new java.util.concurrent.CompletionException(exception);
            }
        }, worker).whenComplete((outcome, failure) -> {
            operationRunning.set(false);
            if (failure != null) {
                Throwable root = rootCause(failure);
                if (root instanceof ConversionException conversion) {
                    for (String problem : conversion.problems()) operationLog.warn("conversion-problem", problem);
                }
                operationLog.failure("operation-failed", root);
                getLogger().log(Level.SEVERE, "Twilight content scan failed", failure);
                send(sender, "Twilight " + operation + " failed: " + rootMessage(failure) + ". Log: " + operationLog.path());
                scheduler.execute(() -> Bukkit.getPluginManager().callEvent(
                        new TwilightOperationFailedEvent(operation, operationLog.path(), root)));
            } else {
                ContentReport report = outcome.report();
                operationLog.info("operation-complete", "success");
                send(sender, "Scan passed: " + report.sources() + " sources, " + report.entries() + " files, " +
                        report.itemDefinitions() + " current items, " + report.legacyItemModels() + " legacy item roots, " +
                        report.bbmodels() + " bbmodels, " + report.biomeDefinitions() + " custom biomes.");
                if (outcome.build() != null) {
                    BuildResult build = outcome.build();
                    lastSuccessfulInputFingerprint.set(outcome.inputFingerprint());
                    send(sender, "Build passed: " + build.converted() + "/" + build.candidates() + " custom items, " +
                            build.threeDimensional() + " volumetric models, " + build.glyphs() + " glyphs/" +
                            build.fontPages() + " font pages, " + build.vanillaFallbackTextures() + " vanilla texture fallbacks, " +
                            build.namedFonts() + " named fonts/" + build.namedGlyphs() + " globally safe glyphs, " +
                            build.soundDefinitions() + " sound events/" + build.soundFiles() + " OGG files/" +
                            build.vanillaFallbackSounds() + " vanilla sound fallbacks, " +
                            build.problems().size() + " isolated problems, SHA-256 " + build.packSha256() + '.');
                }
                if (outcome.unchanged()) send(sender, "Automatic build skipped: normalized provider inputs are unchanged.");
                if (outcome.deployment() != null) send(sender, outcome.deployment().message());
                scheduler.execute(() -> {
                    Bukkit.getPluginManager().callEvent(new TwilightScanCompleteEvent(report));
                    if (outcome.build() != null) Bukkit.getPluginManager().callEvent(new TwilightBuildCompleteEvent(outcome.build()));
                    if (outcome.deployment() != null) Bukkit.getPluginManager().callEvent(new TwilightDeployCompleteEvent(outcome.deployment()));
                });
            }
            try { operationLog.close(); } catch (Exception closeFailure) { getLogger().log(Level.WARNING, "Could not close operation log", closeFailure); }
        });
    }

    /** Bedrock players download Geyser's packs from Twilight's own host when {@code pack-host.enabled} is set. */
    private void startPackHost() {
        com.siberanka.twilight.host.HostSettings settings;
        try {
            settings = com.siberanka.twilight.host.HostSettings.parse(key -> getConfig().get("pack-host." + key));
        } catch (IllegalArgumentException invalid) {
            getLogger().severe("pack-host is disabled: " + invalid.getMessage());
            return;
        }
        if (!settings.enabled()) return;
        if (!config.sendPackToBedrock()) {
            getLogger().warning("pack-host is enabled but geyser.send-pack-to-bedrock is false; Geyser's own packs are still hosted.");
        }
        try {
            packHosting = com.siberanka.twilight.integration.pack.GeyserPackHosting.start(this, settings,
                    getDataFolder().toPath().resolve("pack-host"), getLogger());
        } catch (java.io.IOException | RuntimeException | LinkageError failure) {
            getLogger().log(Level.SEVERE, "pack-host could not start; Geyser sends the packs itself: " + failure.getMessage());
        }
    }

    /** The pack Bedrock players receive: Geyser's copy, or the exported one that another plugin sends. */
    private java.nio.file.Path servedPack() {
        java.nio.file.Path export = getDataFolder().toPath().resolve(com.siberanka.twilight.deploy.PackExport.PACK);
        if (!config.sendPackToBedrock()) return export;
        try {
            return deployment.resolveGeyserDirectory().resolve("packs/twilight.zip");
        } catch (java.io.IOException noLocalGeyser) {
            return export;
        }
    }

    /**
     * The usual network layout runs Geyser on the proxy only. Says where the pack goes instead of reporting a
     * missing Geyser as a problem.
     */
    private String geyserElsewhere() {
        String export = getDataFolder().toPath().resolve(com.siberanka.twilight.deploy.PackExport.PACK).toString();
        return "Geyser is not on this server (normal when it runs on the proxy): builds are exported to " + export
                + (proxyChannel != null ? " and shared with twilight-proxy." : "; pack sharing with twilight-proxy is off"
                + " (no proxy.secret, Velocity forwarding secret or BungeeGuard token found).");
    }

    /**
     * ModelEngine's and BetterModel's bone items are only ever shown by item displays, which Bedrock draws through
     * the display models; without them (Geyser on a proxy) the bones would only make the pack larger.
     */
    static List<CustomItemDescriptor> modelBones(List<CustomItemDescriptor> items, boolean displays) {
        if (displays) return items;
        return items.stream().filter(item -> !item.provider().equals("ModelEngine") && !item.provider().equals("BetterModel"))
                .toList();
    }

    /**
     * {@code ui.pocket-container-layout}: {@code java} (default) gives chest screens on Bedrock's pocket UI profile
     * (phones, tablets) the Java layout of desktop screens; {@code bedrock} keeps Bedrock's two-column screens.
     */
    private boolean pocketContainerLayout() {
        String setting = getConfig().getString("ui.pocket-container-layout", "java").strip().toLowerCase(java.util.Locale.ROOT);
        if (!setting.equals("java") && !setting.equals("bedrock")) {
            getLogger().warning("ui.pocket-container-layout must be java or bedrock, not '" + setting + "'; using java.");
        }
        return !setting.equals("bedrock");
    }

    /**
     * {@code geyser.item-display-models}: auto puts them in the pack only when Geyser runs on this server, where
     * Twilight's display bridge uses them; a proxy's Geyser does not.
     */
    private boolean itemDisplayModels() {
        String setting = getConfig().getString("geyser.item-display-models", "auto").strip().toLowerCase(java.util.Locale.ROOT);
        if (setting.equals("on") || setting.equals("true")) return true;
        if (setting.equals("off") || setting.equals("false")) return false;
        if (getServer().getPluginManager().getPlugin("Geyser-Spigot") != null && localGeyser()) return true;
        var features = proxyFeatures();
        return features != null && features.itemDisplays(System.currentTimeMillis());
    }

    private com.siberanka.twilight.integration.proxy.ProxyFeatures proxyFeatures() {
        var features = proxyFeatures;
        if (features == null) {
            features = com.siberanka.twilight.integration.proxy.ProxyFeatures.load(getDataFolder().toPath().resolve("proxy-features.properties"));
            proxyFeatures = features;
        }
        return features;
    }

    /**
     * twilight-proxy said what its Bedrock runtime shows. When it starts or stops showing item displays, the pack
     * is built again with or without their models (auto mode).
     */
    private void proxyCapabilities(com.siberanka.twilight.protocol.PackChannel.Capabilities capabilities) {
        var features = proxyFeatures();
        var update = features.accept(capabilities, System.currentTimeMillis());
        if (update.save()) {
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    features.save();
                } catch (java.io.IOException failure) {
                    getLogger().warning("Could not save what twilight-proxy shows: " + failure.getMessage());
                }
            });
        }
        if (!update.itemDisplaysChanged()) return;
        String setting = getConfig().getString("geyser.item-display-models", "auto").strip().toLowerCase(java.util.Locale.ROOT);
        if (!setting.equals("auto")) return;
        boolean shows = features.itemDisplays(System.currentTimeMillis());
        getLogger().info(shows ? "twilight-proxy shows Java item displays (furniture, model bones): the pack gets their models."
                : "twilight-proxy no longer shows Java item displays: the pack leaves their models out.");
        scheduleBuild("a change in what twilight-proxy shows", 20L);
    }

    private boolean localGeyser() {
        try {
            deployment.resolveGeyserDirectory();
            return true;
        } catch (java.io.IOException absent) {
            return false;
        }
    }

    /** CustomNameplates draws name tags from text displays with their own (transparent) backgrounds. */
    private boolean nameplatePlugin() {
        org.bukkit.plugin.Plugin plugin = getServer().getPluginManager().getPlugin("CustomNameplates");
        if (plugin == null) return false;
        java.io.File file = new java.io.File(plugin.getDataFolder(), "config.yml");
        return file.isFile() && org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file)
                .getBoolean("modules.nametags", false);
    }

    /**
     * Server state the pack depends on beyond its sources: the custom biomes of the registry (exact Bedrock
     * biome looks), the ones a seasons plugin shows now, and whether a nameplate plugin draws tag backgrounds.
     * Part of the input fingerprint, so a season change rebuilds the pack.
     */
    private WorldInputs worldInputs(OperationLog operationLog) {
        java.util.Map<String, java.util.Map<String, ?>> biomes = java.util.Map.of();
        java.util.Set<String> current = java.util.Set.of();
        if (config.bedrockBiomeMatching()) {
            try {
                biomes = com.siberanka.twilight.integration.world.ServerBiomes.read(getServer());
                operationLog.info("server-biomes", biomes.keySet());
            } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                operationLog.warn("server-biomes", "registry biomes are unavailable; datapack biomes are still used: " + failure);
            }
            try {
                current = com.siberanka.twilight.integration.world.SeasonalBiomes.current(getServer(), biomes);
                if (!current.isEmpty()) operationLog.info("season-biomes", current);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                operationLog.warn("season-biomes", "the current season's biomes are unknown; slots follow the looks only: " + failure);
            }
        }
        return new WorldInputs(biomes, current, nameplatePlugin());
    }

    private record WorldInputs(java.util.Map<String, java.util.Map<String, ?>> biomes, java.util.Set<String> current,
                               boolean nameplates) {
        static final WorldInputs NONE = new WorldInputs(java.util.Map.of(), java.util.Set.of(), false);

        String fingerprint() {
            if (biomes.isEmpty() && current.isEmpty() && !nameplates) return "";
            String state = new java.util.TreeMap<>(biomes) + "|" + new java.util.TreeSet<>(current) + "|" + nameplates;
            try {
                return "+" + java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                        .digest(state.getBytes(java.nio.charset.StandardCharsets.UTF_8))).substring(0, 16);
            } catch (java.security.NoSuchAlgorithmException impossible) {
                throw new IllegalStateException(impossible);
            }
        }
    }

    void deploy(CommandSender sender) {
        runExclusive(sender, "Geyser deployment", log -> {
            DeploymentResult result = deployment.deploy(getDataFolder().toPath().resolve("build/current"));
            reloadGeyser(log, result);
            return result;
        });
    }

    void rollback(CommandSender sender, int index) {
        runExclusive(sender, "Geyser rollback", log -> {
            DeploymentResult result = deployment.rollback(index);
            reloadGeyser(log, result);
            return result;
        });
    }

    private void runExclusive(CommandSender sender, String label, Operation operation) {
        if (!operationRunning.compareAndSet(false, true)) {
            send(sender, "Another Twilight operation is already running.");
            return;
        }
        OperationLog operationLog;
        try { operationLog = OperationLog.create(getDataFolder().toPath(), label); }
        catch (Exception failure) {
            operationRunning.set(false);
            send(sender, "Could not create operation log: " + rootMessage(failure));
            return;
        }
        send(sender, label + " started.");
        CompletableFuture.supplyAsync(() -> {
            try { return operation.run(operationLog); }
            catch (Exception exception) { throw new java.util.concurrent.CompletionException(exception); }
        }, worker).whenComplete((result, failure) -> {
            operationRunning.set(false);
            if (failure != null) {
                Throwable root = rootCause(failure);
                operationLog.failure("operation-failed", root);
                getLogger().log(Level.SEVERE, label + " failed", failure);
                send(sender, label + " failed: " + rootMessage(failure) + ". Log: " + operationLog.path());
                scheduler.execute(() -> Bukkit.getPluginManager().callEvent(
                        new TwilightOperationFailedEvent(label, operationLog.path(), root)));
            } else {
                operationLog.info("operation-complete", result);
                send(sender, result.message());
                if (label.toLowerCase(java.util.Locale.ROOT).contains("deploy")) {
                    scheduler.execute(() -> Bukkit.getPluginManager().callEvent(new TwilightDeployCompleteEvent(result)));
                }
            }
            try { operationLog.close(); } catch (Exception closeFailure) { getLogger().log(Level.WARNING, "Could not close operation log", closeFailure); }
        });
    }

    /** The layout table of the pack Bedrock players get, read again when the pack changes. */
    private com.siberanka.twilight.text.TextLayoutTable servedLayoutTable() {
        java.nio.file.Path pack = servedPack();
        try {
            long modified = java.nio.file.Files.isRegularFile(pack) ? java.nio.file.Files.getLastModifiedTime(pack).toMillis() : -1;
            Object[] cached = menuTable;
            if ((long) cached[1] == modified) return (com.siberanka.twilight.text.TextLayoutTable) cached[0];
            var layout = modified < 0 ? null : com.siberanka.twilight.integration.text.GeyserTextLayoutBridge.readLayout(pack);
            var table = layout == null ? null : layout.table();
            menuTable = new Object[]{table, modified};
            return table;
        } catch (java.io.IOException | RuntimeException unreadable) {
            return null;
        }
    }

    /** {@code /twilight menus [reset]}: the menus players opened and how Bedrock shows their titles. */
    void menus(CommandSender sender, boolean reset) {
        var audit = menuAudit;
        if (audit == null) {
            send(sender, "Menu analysis is unavailable on this server.");
            return;
        }
        if (reset) {
            audit.reset();
            send(sender, "Menu analysis restarted: open the menus to analyse them again.");
            return;
        }
        CompletableFuture.supplyAsync(() -> {
            try {
                return audit.report();
            } catch (java.io.IOException failure) {
                return java.util.List.of("Could not write the menu report: " + failure.getMessage());
            }
        }, worker).thenAccept(lines -> scheduler.execute(() -> lines.forEach(line -> send(sender, line))));
    }

    void reloadTwilight(CommandSender sender) {
        if (operationRunning.get()) {
            send(sender, "Wait for the active Twilight operation before reloading.");
            return;
        }
        try (OperationLog log = OperationLog.create(getDataFolder().toPath(), "reload")) {
            loadServices();
            configureUpdates();
            log.info("configuration", "reloaded; vanilla-override=" + config.vanillaOverride());
            send(sender, "Twilight configuration reloaded. Vanilla overrides: " + config.vanillaOverride());
        } catch (Exception failure) {
            getLogger().log(Level.SEVERE, "Twilight configuration reload failed", failure);
            send(sender, "Configuration reload failed: " + rootMessage(failure));
        }
    }

    void sendStatus(CommandSender sender) {
        ContentReport report = lastReport.get();
        send(sender, "Twilight " + getPluginMeta().getVersion() + " | operation=" +
                (operationRunning.get() ? "running" : "idle") + " | vanilla-override=" + config.vanillaOverride());
        if (report == null) send(sender, "No scan has completed since startup.");
        else send(sender, "Last scan " + report.scannedAt() + ": " + report.sources() + " sources, " +
                report.itemDefinitions() + " current items, " + report.bbmodels() + " bbmodels, " +
                report.biomeDefinitions() + " custom biomes.");
        String fingerprint = lastSuccessfulInputFingerprint.get();
        if (fingerprint != null) send(sender, "Last successful input fingerprint: " + fingerprint.substring(0, 16));
        var check = updates;
        send(sender, "Updates: " + (check == null ? "check disabled" : check.status()));
        try {
            send(sender, "Geyser=" + deployment.resolveGeyserDirectory() + " | snapshots=" + deployment.snapshots().size() + "/" + config.backupsToKeep());
        } catch (Exception unavailable) {
            send(sender, geyserElsewhere());
        }
    }

    void send(CommandSender sender, String message) {
        scheduler.execute(() -> sender.sendMessage("§5[Twilight] §f" + message));
    }

    /**
     * {@code update-check}: looks for newer releases on GitHub (GitLab when GitHub cannot be reached) and
     * tells the console and players with {@value #UPDATE_PERMISSION}. Applied on start and reload.
     */
    private void configureUpdates() {
        notifyUpdatePlayers = getConfig().getBoolean("update-check.notify-players", true);
        boolean enabled = getConfig().getBoolean("update-check.enabled", true);
        if (!enabled && updates != null) {
            updates.close();
            updates = null;
        } else if (enabled && updates == null) {
            updates = com.siberanka.twilight.update.UpdateCheck.start("Twilight", getPluginMeta().getVersion(),
                    getLogger()::info, release -> scheduler.execute(() -> {
                        if (!notifyUpdatePlayers) return;
                        for (org.bukkit.entity.Player online : Bukkit.getOnlinePlayers()) {
                            if (online.hasPermission(UPDATE_PERMISSION)) tellUpdate(online, release);
                        }
                    })).orElse(null);
        }
    }

    static final String UPDATE_PERMISSION = "twilight.update";
    private volatile long restartPendingSince;
    private volatile boolean restarting;

    /**
     * Geyser registers custom items only when the server starts. Tells the console and players with twilight.admin,
     * and with {@code geyser.restart-for-item-changes: when-empty} restarts the server once nobody is online.
     */
    private void pendRestart() {
        boolean first = restartPendingSince == 0;
        if (first) restartPendingSince = System.currentTimeMillis();
        boolean automatic = restartWhenEmpty();
        getLogger().warning(restartNotice());
        scheduler.execute(() -> {
            for (org.bukkit.entity.Player online : Bukkit.getOnlinePlayers()) {
                if (online.hasPermission("twilight.admin")) online.sendMessage("§5[Twilight] §f" + restartNotice());
            }
        });
        if (first && automatic) scheduler.delayed(this::restartIfEmpty, 600L);
    }

    private boolean restartWhenEmpty() {
        return "when-empty".equalsIgnoreCase(getConfig().getString("geyser.restart-for-item-changes", "notify").strip());
    }

    private String restartNotice() {
        return "Bedrock custom items changed. Restart the server so Geyser shows them"
                + (restartWhenEmpty() ? "; it restarts by itself once nobody is online." : ".");
    }

    /** With when-empty: restarts once the change has waited a minute and nobody is online; checks every 30 s. */
    private void restartIfEmpty() {
        if (restarting || restartPendingSince == 0 || !restartWhenEmpty() || !isEnabled()) return;
        if (System.currentTimeMillis() - restartPendingSince >= 60_000 && Bukkit.getOnlinePlayers().isEmpty()) {
            restarting = true;
            getLogger().warning("Restarting so Geyser registers the changed Bedrock items; nobody is online"
                    + " (geyser.restart-for-item-changes: when-empty). Without spigot.yml settings.restart-script the server stops.");
            Bukkit.restart();
            return;
        }
        scheduler.delayed(this::restartIfEmpty, 600L);
    }

    private void tellUpdate(CommandSender target, com.siberanka.twilight.update.UpdateCheck.Release release) {
        target.sendMessage(net.kyori.adventure.text.Component.text("[Twilight] ", net.kyori.adventure.text.format.NamedTextColor.DARK_PURPLE)
                .append(net.kyori.adventure.text.Component.text("Version " + release.version() + " is available (this server runs "
                        + getPluginMeta().getVersion() + "): ", net.kyori.adventure.text.format.NamedTextColor.WHITE))
                .append(net.kyori.adventure.text.Component.text(release.page(), net.kyori.adventure.text.format.NamedTextColor.AQUA)
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.openUrl(release.page()))));
    }

    private void reloadGeyser(OperationLog operationLog, DeploymentResult deploymentResult) throws Exception {
        if (deploymentResult.restartRequired()) {
            operationLog.warn("geyser-restart-required", deploymentResult.message());
            pendRestart();
            return;
        }
        if (!config.reloadAfterDeploy()) {
            operationLog.info("geyser-reload", "disabled by configuration");
            return;
        }
        operationLog.info("geyser-reload", "dispatching console command: geyser reload");
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        scheduler.execute(() -> {
            try {
                result.complete(Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "geyser reload"));
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            }
        });
        boolean accepted = result.get(15, TimeUnit.SECONDS);
        if (!accepted) throw new IllegalStateException("Geyser rejected the reload command after deployment");
        operationLog.info("geyser-reload", "command accepted");
    }

    private void scheduleProviderBuild(String reason, long delayTicks) {
        if (!config.syncProviderChanges()) return;
        scheduleBuild(reason, delayTicks);
    }

    private void scheduleBuild(String reason, long delayTicks) {
        if (!automaticBuildPending.compareAndSet(false, true)) return;
        getLogger().info("Scheduled automatic Twilight conversion after " + reason);
        scheduler.delayed(() -> {
            if (operationRunning.get()) {
                automaticBuildPending.set(false);
                scheduleBuild(reason + " (deferred)", config.startupDelayTicks());
                return;
            }
            automaticBuildPending.set(false);
            scan(Bukkit.getConsoleSender(), true, true);
        }, delayTicks);
    }

    /** Runtime items; another plugin's broken state never stops the build (the packs still describe the items). */
    private BukkitItemCollector.CollectionResult collectRuntimeItems() {
        try {
            return BukkitItemCollector.collect();
        } catch (RuntimeException | LinkageError failure) {
            getLogger().log(Level.WARNING, "Runtime items could not be read; this build uses the packs only", failure);
            return new BukkitItemCollector.CollectionResult(List.of(), List.of(),
                    List.of("runtime items could not be read: " + rootMessage(failure)));
        }
    }

    private final Set<String> reportedRuntimeNotices = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static String rootMessage(Throwable throwable) {
        Throwable current = rootCause(throwable);
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return current;
    }

    @FunctionalInterface
    private interface Operation { DeploymentResult run(OperationLog log) throws Exception; }

    private record ScanOutcome(ContentReport report, BuildResult build, DeploymentResult deployment,
                               String inputFingerprint, boolean unchanged) {}

    private final class AutomationListener implements Listener {
        @EventHandler
        public void onServerLoaded(ServerLoadEvent event) {
            if (config.autoBuildOnStartup()) scheduleBuild("server loaded", config.startupDelayTicks());
        }

        @EventHandler
        public void onPluginEnabled(PluginEnableEvent event) {
            int hooks = providerHooks.register(event.getPlugin());
            if (hooks > 0) scheduleProviderBuild(event.getPlugin().getName() + " enabled", config.startupDelayTicks());
        }

        @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR)
        public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
            org.bukkit.entity.Player joined = event.getPlayer();
            if (restartPendingSince != 0 && joined.hasPermission("twilight.admin")) {
                scheduler.delayed(() -> {
                    if (joined.isOnline()) joined.sendMessage("§5[Twilight] §f" + restartNotice());
                }, 70L);
            }
            var check = updates;
            if (check == null || !notifyUpdatePlayers) return;
            var release = check.latest();
            org.bukkit.entity.Player player = event.getPlayer();
            if (release.isEmpty() || !player.hasPermission(UPDATE_PERMISSION)) return;
            scheduler.delayed(() -> {
                if (player.isOnline()) tellUpdate(player, release.get());
            }, 60L);
        }

        @EventHandler(ignoreCancelled = true)
        public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
            if (event.getPlayer().hasPermission("twilight.admin")) {
                observeProviderCommand(event.getMessage(), event.getPlayer().getName());
            }
        }

        @EventHandler(ignoreCancelled = true)
        public void onServerCommand(ServerCommandEvent event) {
            observeProviderCommand(event.getCommand(), "console");
        }

        private void observeProviderCommand(String command, String actor) {
            ProviderCommandClassifier.classify(command).ifPresent(mutation -> {
                org.bukkit.plugin.Plugin provider = getServer().getPluginManager().getPlugin(mutation.provider());
                if (provider == null || !provider.isEnabled()) return;
                getLogger().info("Observed content-changing provider command " + mutation + " from " + actor);
                String reason = "provider command " + mutation;
                long delay = config.providerCommandDelayTicks();
                scheduleProviderBuild(reason, delay);
                scheduler.delayed(() -> scheduleProviderBuild(reason + " settle check", 1L), delay * 4L);
            });
        }
    }

    private final class Api implements TwilightApi {
        @Override public boolean isOperationRunning() { return operationRunning.get(); }
        @Override public boolean requestScan() { return request(false); }
        @Override public boolean requestConvert() { return request(true); }
        @Override public boolean requestDeploy() {
            if (operationRunning.get()) return false;
            scheduler.execute(() -> deploy(Bukkit.getConsoleSender()));
            return true;
        }
        private boolean request(boolean build) {
            if (operationRunning.get()) return false;
            scheduler.execute(() -> scan(Bukkit.getConsoleSender(), build));
            return true;
        }
        @Override public java.util.Optional<ContentReport> lastContentReport() { return java.util.Optional.ofNullable(lastReport.get()); }
        @Override public Path dataDirectory() { return getDataFolder().toPath().toAbsolutePath().normalize(); }
    }
}
