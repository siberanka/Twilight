/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.proxy;

import com.siberanka.twilight.protocol.PackChannel;
import com.siberanka.twilight.proxy.api.TwilightProxyApi;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-server Bedrock packs on a proxy, shared by the Velocity and BungeeCord entry points.
 *
 * <p>Bedrock loads resource packs once, when it connects. A Bedrock session therefore gets the pack
 * of the server it is about to join; when the player later moves to a server with another pack,
 * the client is transferred back to Geyser, loads that server's pack and is sent to the server it
 * asked for. Login plugins keep the last word: when one sends the reconnected player to a login or
 * limbo server first, the player goes on to its server at its next server change. A session's first
 * server never causes a reconnect, transfers are limited per player and every reconnect has a
 * deadline that grows with the pack, so nothing loops or waits forever.
 */
public final class ProxyCore implements TwilightProxyApi {
    private static final long TRANSFER_WINDOW_MILLIS = 300_000;
    private static final int TRANSFERS_PER_WINDOW = 4;
    private static final int MAX_TRACKED = 100_000;
    /** Packs at least this large get a pack-host hint when Geyser has to send them. */
    private static final long LARGE_PACK_BYTES = 32L * 1_048_576;
    private static final long GEYSER_BYTES_PER_SECOND = 1_250_000;
    /** The shortest time between a transfer and the client's next login seen in tests (cached pack). */
    private static final long RECONNECT_LOGIN_MILLIS = 4_000;
    /** A client that just joined its first server ignores a transfer until it is in game. */
    private static final long FIRST_SERVER_RECONNECT_DELAY_MILLIS = 5_000;

    private final Platform platform;
    private final Object owner;
    private final PackStore store;
    private final AutoTransfers transfers;
    /** Bedrock session (XUID) -> SHA-256 hex of the per-server pack it loaded ("" for none). */
    private final Map<String, String> loaded = new ConcurrentHashMap<>();
    /** Reconnects in progress, from the transfer until the player reaches its server. */
    private final Switches switches = new Switches();
    /** Proxy players already connected to a backend server; a session's first server is checked once. */
    private final java.util.Set<UUID> seated = ConcurrentHashMap.newKeySet();
    /** Servers whose large pack already got the pack-host hint. */
    private final java.util.Set<String> hinted = ConcurrentHashMap.newKeySet();
    private final Map<String, Deque<Long>> recentTransfers = new ConcurrentHashMap<>();
    private volatile ProxyConfig config;
    private volatile Sessions geyser;
    /** Twilight's runtime on the proxy's Geyser (text layout, translations, loading protection), or null. */
    private volatile ProxyRuntime runtime;
    /** True with the proxy's real Geyser (not a test attacher): the runtime may start. */
    private final boolean realGeyser;
    /** Why twilight-proxy is not attached to Geyser ("" once attached). */
    private volatile String geyserProblem = "not attached yet";
    private final Sessions.Attacher attacher;
    private volatile boolean enabled;
    private volatile long lastAttachAttempt;
    private java.util.function.LongSupplier clock = System::currentTimeMillis;
    /** The proxy's login rate limit, checked again when Geyser attaches late. */
    private volatile String loginLimitSetting;
    private volatile long loginLimitMillis;
    /** Attached after Geyser had probably started (a late attempt): its items are already registered. */
    private volatile boolean attachedLate;
    private final java.util.concurrent.atomic.AtomicBoolean mappingsQueued = new java.util.concurrent.atomic.AtomicBoolean();
    private volatile String mappingsState = "not written";
    private volatile String reportedConflicts = "";
    private volatile boolean reportedCopies;
    private volatile List<String> mappingsCopies = List.of();
    private volatile boolean reportedLocale;
    private volatile int reportedOptionDifferences;
    /** Login servers found in login plugins' configuration files (lower case -> file). */
    private volatile Map<String, String> detectedLogin = Map.of();
    private volatile java.util.Set<String> reportedSkippedLogin = java.util.Set.of();
    /** Backends on this machine whose Twilight exports are read directly (server -> backend). */
    private volatile Map<String, LocalBackends.Backend> localBackends = Map.of();
    /** Export state already handled per server: size and modification time. */
    private final Map<String, String> localSeen = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicBoolean localBusy = new java.util.concurrent.atomic.AtomicBoolean();
    /** Item mappings changed after Geyser registered its items: what the restart would bring, or null. */
    private volatile String restartPending;
    private volatile long restartPendingSince;
    private volatile boolean restarting;
    private volatile com.siberanka.twilight.host.PackHost host;
    private volatile com.siberanka.twilight.update.UpdateCheck updates;

    /** Players told about new versions; holders of {@link #ADMIN_PERMISSION} are told as well. */
    public static final String UPDATE_PERMISSION = "twilight.proxy.update";
    public static final String ADMIN_PERMISSION = "twilight.proxy.admin";

    public ProxyCore(Platform platform, Object owner) {
        this(platform, owner, null);
    }

    /** {@code attacher} null: Geyser on this proxy. */
    ProxyCore(Platform platform, Object owner, Sessions.Attacher attacher) {
        this.platform = platform;
        this.owner = owner;
        this.attacher = attacher != null ? attacher : core -> geyserAttach(owner, core);
        this.realGeyser = attacher == null;
        this.store = new PackStore(platform);
        this.transfers = new AutoTransfers(platform, store);
        store.onChange(pack -> {
            prewarm(pack);
            queueItemMappings();
            ProxyRuntime running = runtime;
            String blocks = running == null ? null : running.packsChanged();
            if (blocks != null && !blocks.equals(reportedBlocks)) {
                reportedBlocks = blocks;
                platform.warn("Custom blocks for Geyser changed (" + blocks + "). Geyser registers custom blocks when it starts:"
                        + " restart the proxy; until then Bedrock players see new or changed blocks as before.", null);
                pendRestart(blocks);
            }
        });
    }

    /**
     * Hashes a new pack for Geyser and copies it for the pack host now, so the first Bedrock session that
     * needs it does not wait for a large pack to be read during its login.
     */
    private void prewarm(PackFiles.Pack pack) {
        long started = System.nanoTime();
        try {
            Sessions bridge = geyser;
            if (bridge != null) bridge.prepare(pack.path());
            ProxyRuntime bedrock = runtime;
            if (bedrock != null) bedrock.prepare(pack.path());
            var running = host;
            if (running != null) running.snapshot(pack.path(), pack.sha256());
        } catch (IOException | RuntimeException | LinkageError failure) {
            platform.warn("Could not prepare a Bedrock pack for Geyser: " + failure.getMessage(), null);
            return;
        }
        long millis = (System.nanoTime() - started) / 1_000_000;
        if (millis >= 1000) platform.info("Prepared a " + mib(pack.size()) + " pack for Bedrock sessions in " + millis / 1000 + " s.");
    }

    public void enable() throws IOException {
        if (!reload()) throw new IOException("twilight-proxy configuration is invalid; see the log");
        enabled = true;
        attachGeyser(0);
        platform.repeat(this::sweep, 5);
        platform.repeat(this::reportSightings, 5);
        TwilightProxyApi.Holder.set(this);
    }

    /** Attempts while Geyser starts: every two seconds for two minutes, then whenever a player joins. */
    static final int ATTACH_ATTEMPTS = 60;
    static final long ATTACH_RETRY_MILLIS = 2_000;
    private static final String NOT_INSTALLED = "Geyser is not installed on this proxy";

    /** Geyser on this proxy: absent when its API classes are not visible, otherwise {@link GeyserBridge}. */
    private static Sessions.Attach geyserAttach(Object owner, ProxyCore core) {
        try {
            Class.forName("org.geysermc.geyser.api.GeyserApi", false, ProxyCore.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError absent) {
            // Installed but invisible: a proxy fork that isolates plugins, or Geyser loaded later than expected.
            Optional<String> plugin = core.platform.geyserPlugin();
            if (plugin.isPresent()) return Sessions.Attach.notReady(plugin.get() + " is installed, but its API is not visible"
                    + " to twilight-proxy (" + Sessions.describe(absent) + ")");
            return Sessions.Attach.failed(NOT_INSTALLED);
        }
        return GeyserBridge.attach(owner, core);
    }

    /**
     * Attaches to Geyser. Geyser may finish loading after twilight-proxy (Velocity starts both in the same
     * event), so a Geyser that is not ready yet is tried again instead of being reported as absent.
     */
    private synchronized void attachGeyser(int attempt) {
        if (geyser != null || !enabled) return;
        lastAttachAttempt = clock.getAsLong();
        Sessions.Attach result;
        try {
            result = attacher.attach(this);
        } catch (RuntimeException | LinkageError failure) {
            result = Sessions.Attach.failed("Geyser's API could not be used (" + Sessions.describe(failure) + ")", failure);
        }
        if (result.sessions() != null) {
            attachedLate = attempt > ATTACH_ATTEMPTS;
            geyser = result.sessions();
            geyserProblem = "";
            attached(attempt);
            return;
        }
        boolean changed = !result.problem().equals(geyserProblem);
        geyserProblem = result.problem();
        if (!result.retry()) {
            if (result.problem().equals(NOT_INSTALLED)) {
                if (attempt == 0) platform.info(NOT_INSTALLED + ": packs are prepared but not sent.");
            } else if (changed) {
                platform.warn("Could not attach to Geyser: " + result.problem() + ". Bedrock players get no per-server packs;"
                        + " check that Geyser and twilight-proxy are up to date. " + geyserLibraries(), result.cause());
            }
            return;
        }
        if (attempt == 0) platform.info("Geyser is installed but not started yet; attaching when it is ready.");
        if (attempt < ATTACH_ATTEMPTS) {
            platform.later(() -> attachGeyser(attempt + 1), ATTACH_RETRY_MILLIS);
        } else if (attempt == ATTACH_ATTEMPTS) {
            platform.warn("Geyser did not become ready within " + ATTACH_ATTEMPTS * ATTACH_RETRY_MILLIS / 1000 + " s ("
                    + result.problem() + "); twilight-proxy tries again when a player joins.", null);
        }
    }

    /**
     * Where Geyser's event library comes from, for twilight-proxy and for Geyser: two different files mean another
     * plugin (Floodgate) bundles its own copy, which is what a "loader constraint violation" points to.
     */
    static String geyserLibraries() {
        return "Geyser's event library for twilight-proxy: " + origin("org.geysermc.event.PostOrder", ProxyCore.class.getClassLoader())
                + "; Geyser's API: " + origin("org.geysermc.geyser.api.event.EventBus", ProxyCore.class.getClassLoader()) + ".";
    }

    private static String origin(String name, ClassLoader loader) {
        try {
            Class<?> type = Class.forName(name, false, loader);
            var source = type.getProtectionDomain().getCodeSource();
            String file = source == null || source.getLocation() == null ? "unknown" : source.getLocation().getPath();
            return file.substring(file.lastIndexOf('/') + 1);
        } catch (ClassNotFoundException | LinkageError | SecurityException missing) {
            return "not found";
        }
    }

    /** A late attempt when a player joins while Geyser was not ready; at most every ten seconds. */
    private void attachLate() {
        if (geyser != null || !enabled || NOT_INSTALLED.equals(geyserProblem)) return;
        if (clock.getAsLong() - lastAttachAttempt < 10_000) return;
        attachGeyser(ATTACH_ATTEMPTS + 1);
    }

    private void attached(int attempt) {
        retireStaleFiles();
        platform.info(attempt == 0 ? "Attached to Geyser: Bedrock players get each server's pack."
                : "Attached to Geyser after it started (attempt " + (attempt + 1) + "): Bedrock players get each server's pack.");
        startHost();
        platform.async(() -> platform.serverNames().forEach(server -> store.pack(server).ifPresent(this::prewarm)));
        if (loginLimitSetting != null) checkLoginLimit(loginLimitSetting, loginLimitMillis);
        writeItemMappings();
        startRuntime();
    }

    /** Twilight's runtime on the proxy's Geyser; settings in {@code bedrock} take effect on restart. */
    private volatile String reportedBlocks;

    /**
     * Custom blocks registered before the proxy enabled this plugin: BungeeCord forks such as FlameCord enable
     * Geyser, and Geyser defines its blocks, before twilight-proxy's own start. Their pack list follows the
     * servers' packs once this core runs.
     */
    private static volatile com.siberanka.twilight.geyser.CustomBlocks earlyBlocks;
    private static volatile java.util.function.Supplier<java.util.Collection<Path>> earlyPacks;
    /** The item-display bridge subscribed in the load phase, for the same reason as {@link #earlyBlocks}. */
    private static volatile ProxyRuntime.EarlyDisplays earlyDisplays;

    /**
     * Subscribes to Geyser's custom block and item definition from the proxy's plugin load phase, when Geyser is
     * loaded but not started. Until the core runs, the packs are the ones the last run cached and the files in
     * {@code packs/}. Does nothing when Geyser's API is not available yet or {@code bedrock.custom-blocks} is off.
     */
    public static void registerBlocksEarly(Object owner, Path dataDirectory, java.util.function.Consumer<String> info,
                                           java.util.function.BiConsumer<String, Throwable> warn) {
        registerBlocksEarly(owner, dataDirectory, java.util.List.of(), info, warn);
    }

    /**
     * Prepares the proxy's Geyser from the plugin load phase, before Geyser reads its folders: moves stale Twilight
     * files out, writes the merged item mappings of the servers' cached packs, and subscribes to custom blocks.
     * BungeeCord forks such as FlameCord start Geyser before twilight-proxy enables, so this is the last moment.
     *
     * @param servers the proxy's server names (their {@code auto} and file packs are merged, as the core does)
     */
    public static void registerBlocksEarly(Object owner, Path dataDirectory, java.util.Collection<String> servers,
                                           java.util.function.Consumer<String> info,
                                           java.util.function.BiConsumer<String, Throwable> warn) {
        ProxyConfig settings = null;
        try {
            Path configFile = dataDirectory.resolve("config.yml");
            if (Files.isRegularFile(configFile)) settings = ProxyConfig.load(configFile);
            if (org.geysermc.geyser.api.GeyserApi.api() == null) return;
        } catch (IOException | RuntimeException | LinkageError unavailable) {
            // Geyser is not loaded yet (or is hidden): the core prepares it when it attaches, as on Velocity.
            return;
        }
        if (settings != null && !servers.isEmpty()) prepareMappingsEarly(dataDirectory, settings, servers, info, warn);
        if (settings == null || settings.itemDisplays()) {
            try {
                ProxyRuntime.EarlyDisplays displays = new ProxyRuntime.EarlyDisplays();
                displays.bridge = new com.siberanka.twilight.integration.display.GeyserDisplayBridge(owner, displays,
                        ProxyRuntime.logger(info, warn), true);
                earlyDisplays = displays;
            } catch (RuntimeException | LinkageError unavailable) {
                // The runtime subscribes when it starts.
            }
        }
        if (settings != null && !settings.customBlocks()) return;
        try {
            earlyPacks = () -> cachedPacks(dataDirectory);
            earlyBlocks = com.siberanka.twilight.geyser.CustomBlocks.start(owner, () -> earlyPacks.get(), info, warn);
        } catch (RuntimeException | LinkageError unavailable) {
            // The core subscribes when it attaches.
        }
    }

    /**
     * The server names in a BungeeCord {@code config.yml} (the keys under the top-level {@code servers:}), read
     * before the proxy has loaded its configuration; empty when the file cannot be read.
     */
    public static java.util.List<String> bungeeServerNames(Path configFile) {
        java.util.List<String> names = new java.util.ArrayList<>();
        try {
            if (!Files.isRegularFile(configFile)) return names;
            boolean inServers = false;
            int indent = -1;
            for (String line : Files.readAllLines(configFile)) {
                if (line.isBlank() || line.stripLeading().startsWith("#")) continue;
                int leading = line.length() - line.stripLeading().length();
                if (leading == 0) {
                    inServers = line.startsWith("servers:");
                    indent = -1;
                    continue;
                }
                if (!inServers) continue;
                if (indent < 0) indent = leading;
                if (leading != indent) continue;
                String key = line.strip();
                int colon = key.indexOf(':');
                if (colon <= 0) continue;
                key = key.substring(0, colon).replace("'", "").replace("\"", "").strip();
                if (key.matches("[A-Za-z0-9._-]{1,64}")) names.add(key);
            }
        } catch (IOException | RuntimeException unreadable) {
            names.clear();
        }
        return names;
    }

    /** Stale Twilight files out of Geyser and the merged item mappings in, before Geyser reads custom_mappings. */
    private static void prepareMappingsEarly(Path dataDirectory, ProxyConfig settings, java.util.Collection<String> servers,
                                             java.util.function.Consumer<String> info,
                                             java.util.function.BiConsumer<String, Throwable> warn) {
        try {
            var api = org.geysermc.geyser.api.GeyserApi.api();
            Path folder = api.configDirectory();
            if (settings.retireStaleFiles()) {
                var retired = com.siberanka.twilight.geyser.StaleFiles.retire(folder, api.packDirectory(),
                        java.util.Set.of("custom_mappings/" + ItemMappings.FILE), dataDirectory.resolve("retired"));
                for (var file : retired) warn.accept("Moved " + file.file() + " out of Geyser before it started: a Twilight"
                        + " file twilight-proxy does not own (copied by hand, an older sync tool or an older Twilight).", null);
            }
            Map<String, com.google.gson.JsonObject> packs = new java.util.TreeMap<>();
            for (String server : servers) {
                ProxyConfig.PackSource source = settings.source(server);
                Path pack;
                if (source instanceof ProxyConfig.PackSource.Auto) pack = dataDirectory.resolve("cache").resolve(PackFiles.safeName(server) + ".mcpack");
                else if (source instanceof ProxyConfig.PackSource.File file) pack = dataDirectory.resolve("packs").resolve(file.name());
                else continue;
                if (!Files.isRegularFile(pack)) continue;
                ItemMappings.read(pack).ifPresent(mappings -> packs.put(server, mappings));
            }
            if (packs.isEmpty()) return;
            ItemMappings.Merge merge = ItemMappings.merge(packs);
            if (ItemMappings.write(folder.resolve("custom_mappings"), merge)) {
                info.accept("Wrote item mappings for Geyser before it started: " + merge.count() + " item(s) from "
                        + String.join(", ", packs.keySet()) + ".");
            }
        } catch (IOException | RuntimeException | LinkageError failure) {
            warn.accept("Could not prepare Geyser's item mappings before it started: " + failure.getMessage(), null);
        }
    }

    /** The packs a previous run cached for each server, and the pack files in {@code packs/}. */
    static java.util.List<Path> cachedPacks(Path dataDirectory) {
        java.util.List<Path> packs = new java.util.ArrayList<>();
        for (Path folder : java.util.List.of(dataDirectory.resolve("cache"), dataDirectory.resolve("packs"))) {
            if (!Files.isDirectory(folder)) continue;
            try (var files = Files.list(folder)) {
                files.filter(Files::isRegularFile).filter(file -> {
                    String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                    return name.endsWith(".mcpack") || name.endsWith(".zip");
                }).sorted().forEach(packs::add);
            } catch (IOException unreadable) {
                // Skipped: the core's own pack store reports unreadable folders.
            }
        }
        return packs;
    }

    private void startRuntime() {
        ProxyConfig current = config;
        if (!realGeyser || runtime != null || current == null) return;
        try {
            com.siberanka.twilight.geyser.CustomBlocks early = earlyBlocks;
            if (early != null) earlyPacks = store::currentPacks;
            ProxyRuntime started = ProxyRuntime.start(owner, current, platform, this::sessionPack, store::currentPacks,
                    early, earlyDisplays);
            runtime = started;
            // The display variants of the packs already known are read off the proxy's threads.
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                for (String server : platform.serverNames()) store.pack(server).ifPresent(pack -> started.prepare(pack.path()));
            });
        } catch (RuntimeException | LinkageError failure) {
            platform.warn("Twilight's text layout and loading protection are unavailable for this Geyser build: "
                    + Sessions.describe(failure), failure);
        }
    }

    /** The pack file a Bedrock session (by XUID) loaded, while that version is kept. */
    Optional<Path> sessionPack(String xuid) {
        String hex = loaded.get(xuid);
        return hex == null || hex.isEmpty() ? Optional.empty() : store.versionPath(hex);
    }

    // --- Item mappings for the proxy's Geyser ------------------------------------------------

    /** Geyser is about to read custom_mappings (it does once, when it starts). */
    void beforeGeyserItems() {
        retireStaleFiles();
        writeItemMappings();
    }

    /**
     * Moves Twilight item mappings and packs that twilight-proxy does not own out of the proxy's Geyser
     * ({@code item-mappings.retire-stale-files}). Done before Geyser reads them; afterwards Geyser keeps what it
     * loaded until it restarts, which is asked for.
     */
    private synchronized void retireStaleFiles() {
        Sessions bridge = geyser;
        ProxyConfig current = config;
        if (bridge == null || current == null || !current.retireStaleFiles()) return;
        Optional<Path> folder = bridge.geyserFolder();
        if (folder.isEmpty()) return;
        try {
            var retired = com.siberanka.twilight.geyser.StaleFiles.retire(folder.get(), bridge.packFolder().orElse(null),
                    java.util.Set.of("custom_mappings/" + ItemMappings.FILE), platform.dataDirectory().resolve("retired"));
            for (var file : retired) {
                platform.warn("Moved " + file.file() + " out of Geyser to "
                        + platform.dataDirectory().toAbsolutePath().normalize().relativize(file.to().toAbsolutePath().normalize())
                        + ": it is a Twilight file twilight-proxy does not own (copied by hand, an older sync tool or an older"
                        + " Twilight) and would register items twice or send a second Twilight pack.", null);
            }
            if (!retired.isEmpty() && bridge.itemsRegistered()) {
                pendRestart(retired.size() + " stale Twilight file(s) removed from Geyser");
            }
        } catch (IOException | RuntimeException failure) {
            platform.warn("Could not move stale Twilight files out of Geyser: " + failure.getMessage(), failure);
        }
    }

    // --- Backends on this machine -----------------------------------------------------------

    private void discoverLocalBackends(ProxyConfig next) {
        ProxyConfig.LocalSettings settings = next.local();
        if (!settings.enabled() && settings.servers().isEmpty()) {
            localBackends = Map.of();
            return;
        }
        Path root = platform.proxyRoot();
        java.util.List<Path> roots = new java.util.ArrayList<>();
        if (settings.enabled()) {
            if (root.getParent() != null) roots.add(root.getParent());
            settings.search().forEach(folder -> roots.add(root.resolve(folder).normalize()));
        }
        Map<String, Path> explicit = new java.util.HashMap<>();
        settings.servers().forEach((server, folder) -> explicit.put(server, root.resolve(folder).normalize()));
        Map<String, java.net.InetSocketAddress> servers = new java.util.LinkedHashMap<>();
        platform.serverAddresses().forEach((server, address) -> {
            if (next.source(server) instanceof ProxyConfig.PackSource.Auto) servers.put(server, address);
        });
        Map<String, LocalBackends.Backend> found = LocalBackends.discover(servers, java.util.List.copyOf(roots), explicit,
                line -> platform.info(line));
        if (!found.keySet().equals(localBackends.keySet())) {
            found.values().forEach(backend -> platform.info("Reading the Twilight builds of " + backend.server() + " from "
                    + backend.folder() + " (same machine): no player has to join it first."));
        }
        localBackends = Map.copyOf(found);
        localSeen.keySet().retainAll(found.keySet());
    }

    /**
     * Loads the exports of local backends that changed. {@code now}: at start and reload, on this thread, so the
     * proxy's Geyser reads current item mappings; otherwise from the five-second sweep, off the network threads.
     */
    private void syncLocalBackends(boolean now) {
        if (localBackends.isEmpty() || !localBusy.compareAndSet(false, true)) return;
        Runnable task = () -> {
            try {
                for (LocalBackends.Backend backend : localBackends.values()) {
                    try {
                        if (!Files.isRegularFile(backend.export())) continue;
                        var modified = Files.getLastModifiedTime(backend.export()).toMillis();
                        String state = Files.size(backend.export()) + "@" + modified;
                        if (state.equals(localSeen.get(backend.server()))) continue;
                        // Twilight replaces the export atomically; still wait until it has been quiet for a moment.
                        if (!now && System.currentTimeMillis() - modified < 2_000) continue;
                        localSeen.put(backend.server(), state);
                        if (store.acceptLocal(backend.server(), backend.export())) {
                            platform.info("Loaded the Bedrock pack of " + backend.server() + " from its folder ("
                                    + store.pack(backend.server()).map(pack -> pack.size() / 1024 + " KiB, " + pack.hex().substring(0, 12)).orElse("?")
                                    + ").");
                        }
                    } catch (IOException | RuntimeException failure) {
                        platform.warn("Could not read the Twilight build of " + backend.server() + " from " + backend.export()
                                + ": " + failure.getMessage(), null);
                    }
                }
            } finally {
                localBusy.set(false);
            }
        };
        if (now) task.run();
        else platform.async(task);
    }

    /** Local backends for status output. */
    Map<String, LocalBackends.Backend> localBackends() {
        return localBackends;
    }

    // --- Restarts for changed items ------------------------------------------------------------

    /** Geyser must restart to show {@code what}; tells the admins and, with {@code restart: when-empty}, waits for an empty proxy. */
    private void pendRestart(String what) {
        boolean first = restartPending == null;
        restartPending = what;
        if (first) restartPendingSince = clock.getAsLong();
        ProxyConfig current = config;
        boolean automatic = current != null && current.restartWhenEmpty();
        platform.tellAdmins(restartNotice(), null);
        if (automatic) platform.info("The proxy stops by itself once nobody is online (item-mappings.restart: when-empty).");
    }

    /** The notice for players with the admin permission, or empty when no restart is pending. */
    public Optional<String> adminNotice() {
        return restartPending == null ? Optional.empty() : Optional.of(restartNotice());
    }

    private String restartNotice() {
        ProxyConfig current = config;
        return "[twilight-proxy] Bedrock custom items changed (" + restartPending + "). Restart the proxy so Geyser shows them"
                + (current != null && current.restartWhenEmpty() ? "; it restarts by itself once nobody is online." : ".");
    }

    /** With {@code restart: when-empty}: stops the proxy once a pending restart has waited a minute and nobody is online. */
    private void restartIfEmpty() {
        ProxyConfig current = config;
        if (restartPending == null || restarting || current == null || !current.restartWhenEmpty()) return;
        if (clock.getAsLong() - restartPendingSince < 60_000 || platform.onlinePlayers() != 0) return;
        restarting = true;
        platform.warn("Stopping the proxy so Geyser registers the changed Bedrock items (" + restartPending
                + "); nobody is online and item-mappings.restart is when-empty.", null);
        platform.stopProxy("Updating Bedrock custom items; back in a moment.");
    }

    /** A pack changed: merge the item mappings again shortly (several packs often change together). */
    private void queueItemMappings() {
        if (geyser == null || !mappingsQueued.compareAndSet(false, true)) return;
        platform.later(() -> {
            mappingsQueued.set(false);
            writeItemMappings();
        }, 3_000);
    }

    /**
     * Merges the Geyser item mappings in the packs of the servers ({@code auto} and files in {@code packs/}) into
     * {@link ItemMappings#FILE} in Geyser's {@code custom_mappings}. Geyser registers custom items when it starts;
     * a file that changes afterwards needs a proxy restart, which is logged.
     */
    private synchronized void writeItemMappings() {
        Sessions bridge = geyser;
        ProxyConfig current = config;
        if (bridge == null || current == null) return;
        Optional<Path> folder = bridge.geyserFolder();
        if (folder.isEmpty()) {
            mappingsState = "Geyser's folder is unknown";
            return;
        }
        Path custom = folder.get().resolve("custom_mappings");
        Map<String, com.google.gson.JsonObject> servers = new java.util.TreeMap<>();
        for (String server : platform.serverNames()) {
            ProxyConfig.PackSource source = current.source(server);
            if (!(source instanceof ProxyConfig.PackSource.Auto) && !(source instanceof ProxyConfig.PackSource.File)) continue;
            Optional<PackFiles.Pack> pack = store.pack(server);
            if (pack.isEmpty()) continue;
            try {
                ItemMappings.read(pack.get().path()).ifPresent(mappings -> servers.put(server, mappings));
            } catch (IOException | RuntimeException unreadable) {
                platform.warn("Could not read the item mappings in " + server + "'s pack: " + unreadable.getMessage(), null);
            }
        }
        ItemMappings.Merge merge = ItemMappings.merge(servers);
        String conflicts = String.join("\n", merge.conflicts());
        if (!conflicts.isEmpty() && !conflicts.equals(reportedConflicts)) {
            platform.warn(merge.conflicts().size() + " Java item selector(s) are mapped differently by two servers; Geyser keeps"
                    + " one per selector, so the other server shows that item wrongly. Give the item the same model on"
                    + " both servers or a different custom model data / item model:", null);
            merge.conflicts().stream().limit(20).forEach(line -> platform.warn("  " + line, null));
            if (merge.conflicts().size() > 20) platform.warn("  ... and " + (merge.conflicts().size() - 20) + " more", null);
        }
        reportedConflicts = conflicts;
        if (merge.optionDifferences() > 0 && merge.optionDifferences() != reportedOptionDifferences) {
            platform.info(merge.optionDifferences() + " Java item selector(s) are the same Bedrock item on several servers with"
                    + " other display options (held like a tool, icon); the first server's options are used.");
        }
        reportedOptionDifferences = merge.optionDifferences();
        if (merge.count() > 0 && !reportedLocale && !localeReadsMappings(Locale.getDefault())) {
            reportedLocale = true;
            platform.warn("This Java runs with the " + Locale.getDefault() + " locale, in which Geyser cannot read item mappings"
                    + " (\"definition\" is upper-cased with a dotted I) and skips them: Bedrock players then see these items as"
                    + " their base item. Start the proxy with -Duser.language=en -Duser.country=US.", null);
        }
        if (merge.invalid() > 0) {
            platform.warn(merge.invalid() + " item mapping(s) in the servers' packs were malformed and left out.", null);
        }
        try {
            List<String> copies = ItemMappings.copies(custom);
            if (!copies.isEmpty() && !reportedCopies) {
                reportedCopies = true;
                platform.warn("Geyser's custom_mappings holds other Twilight item mappings " + copies + " (copied from a"
                        + " backend or written by an older sync tool): stop that tool and remove them. twilight-proxy writes "
                        + ItemMappings.FILE + " with every server's items; Geyser would register both files.", null);
                for (String copy : copies) {
                    int clashes = ItemMappings.clashes(custom.resolve(copy), merge);
                    if (clashes > 0) platform.warn("  " + copy + " maps " + clashes + " Java item selector(s) to other Bedrock"
                            + " items than the servers' packs: those items would look wrong for Bedrock players.", null);
                }
            }
            mappingsCopies = copies;
            if (merge.count() == 0 && !Files.isRegularFile(custom.resolve(ItemMappings.FILE))) {
                mappingsState = "no Twilight items in the servers' packs yet";
                return;
            }
            boolean changed = ItemMappings.write(custom, merge);
            String summary = merge.count() + " item(s) from " + (servers.isEmpty() ? "no server" : String.join(", ", servers.keySet()));
            boolean registered = bridge.itemsRegistered() || attachedLate;
            if (changed && registered) {
                mappingsState = summary + "; restart the proxy to register the changes";
                platform.warn("Item mappings for Geyser changed (" + summary + "). Geyser registers custom items when it starts:"
                        + " restart the proxy; until then Bedrock players see new or changed items as their base item.", null);
                pendRestart(summary);
            } else if (changed) {
                mappingsState = summary;
                platform.info("Wrote item mappings for Geyser: " + summary + ".");
            } else {
                mappingsState = summary + (registered ? ", registered" : "");
            }
        } catch (IOException | RuntimeException failure) {
            mappingsState = "could not be written";
            platform.warn("Could not write " + ItemMappings.FILE + " in " + custom + ": " + failure.getMessage(), null);
        }
    }

    /** Geyser upper-cases mapping types with the default locale; Turkish and Azerbaijani turn "i" into a dotted "I". */
    static boolean localeReadsMappings(Locale locale) {
        return "definition".toUpperCase(locale).equals("DEFINITION");
    }

    /** The item mapping state for status output. */
    String itemMappingsState() {
        return mappingsState;
    }

    /** A login, captcha or limbo server: listed in {@code login-servers} or found in a login plugin's configuration. */
    private boolean loginServer(ProxyConfig current, String server) {
        return current.loginServer(server) || detectedLogin.containsKey(server.toLowerCase(Locale.ROOT));
    }

    /** For tests: the clock of the late attach attempts. */
    void clock(java.util.function.LongSupplier clock) {
        this.clock = clock;
    }

    /** Why twilight-proxy is not attached to Geyser, or empty when it is. */
    String geyserProblem() {
        return geyserProblem;
    }

    /** The pack host Bedrock players download from ({@code pack-host.enabled}); changes take effect on restart. */
    private void startHost() {
        var settings = config.host();
        if (!settings.enabled() || host != null) return;
        try {
            host = com.siberanka.twilight.host.PackHost.start(settings, platform.dataDirectory().resolve("pack-host"), platform::info);
        } catch (IOException | RuntimeException failure) {
            platform.warn("pack-host could not start; Geyser sends the packs itself: " + failure.getMessage(), null);
        }
    }

    com.siberanka.twilight.host.PackHost host() {
        return host;
    }

    void warn(String message) {
        platform.warn(message, null);
    }

    /**
     * A transferred Bedrock client logs in again a few seconds after it left (four or more, measured). A proxy
     * that refuses logins from the same address within a longer time turns the pack reconnect into a refusal.
     */
    public void checkLoginLimit(String setting, long millis) {
        loginLimitSetting = setting;
        loginLimitMillis = millis;
        if (geyser != null && millis > RECONNECT_LOGIN_MILLIS && config != null && config.transferOnSwitch()) {
            platform.warn(setting + " is " + millis + " ms: Bedrock players who reconnect for a server's pack log in again"
                    + " about " + RECONNECT_LOGIN_MILLIS / 1000 + " s after leaving and may be refused. Keep it at "
                    + RECONNECT_LOGIN_MILLIS + " ms or less.", null);
        }
    }

    public void disable() {
        enabled = false;
        ProxyRuntime running = runtime;
        if (running != null) running.close();
        TwilightProxyApi.Holder.set(null);
        var check = updates;
        if (check != null) check.close();
        if (host != null) host.close();
        if (geyser != null) geyser.close();
        transfers.close();
    }

    @Override public synchronized boolean reload() {
        try {
            ProxyConfig next = ProxyConfig.load(platform.dataDirectory().resolve("config.yml"));
            config = next;
            store.reload(next, platform.serverNames());
            List<PackChannel.Key> keys = new ArrayList<>();
            for (String secret : ProxySecrets.discover(platform.proxyRoot(), platform.velocity(), next.secret())) {
                try { keys.add(PackChannel.Key.derive(secret)); }
                catch (IllegalArgumentException tooShort) { platform.warn("Ignoring a shared secret shorter than 16 characters.", null); }
            }
            transfers.keys(keys);
            platform.info(keys.isEmpty()
                    ? "No secret is shared with the backends (secret, Velocity forwarding or BungeeGuard): 'auto' packs are off."
                    : "Packs from Twilight on the backends are on (" + keys.size() + " shared secret(s)).");
            if (next.urlRefreshMinutes() > 0) store.refreshLinks();
            java.util.Set<String> skippedLogin = new java.util.TreeSet<>();
            Map<String, String> login = LoginServers.detect(platform.proxyRoot(), platform.serverNames(),
                    platform.pluginFolders().orElse(null), skippedLogin::add);
            if (!skippedLogin.equals(reportedSkippedLogin)) {
                skippedLogin.forEach(folder -> platform.info("Not reading " + folder + ": that plugin is not installed"
                        + " (a folder left behind); its login servers are not used."));
                reportedSkippedLogin = java.util.Set.copyOf(skippedLogin);
            }
            if (!login.equals(detectedLogin)) {
                login.forEach((server, file) -> platform.info("Login server " + server + " (found in " + file
                        + "): Bedrock players are never reconnected for it."));
            }
            detectedLogin = Map.copyOf(login);
            discoverLocalBackends(next);
            syncLocalBackends(true);
            configureUpdates(next);
            queueItemMappings();
            return true;
        } catch (IOException | IllegalArgumentException failure) {
            platform.warn("Could not load twilight-proxy's configuration: " + failure.getMessage(), null);
            return false;
        }
    }

    /**
     * {@code update-check}: looks for newer releases on GitHub (GitLab when GitHub cannot be reached), writes
     * them to the console and tells players with the update permission.
     */
    private void configureUpdates(ProxyConfig next) {
        var check = updates;
        if (!next.updateCheck() && check != null) {
            check.close();
            updates = null;
        } else if (next.updateCheck() && check == null) {
            updates = com.siberanka.twilight.update.UpdateCheck.start("twilight-proxy", platform.version(), platform::info,
                    release -> {
                        ProxyConfig current = config;
                        if (current != null && current.updateNotify()) platform.tellAdmins(updateText(release), release.page());
                    }).orElse(null);
        }
    }

    /** A newer release to tell a joining player with the update permission about, when notices are on. */
    public Optional<com.siberanka.twilight.update.UpdateCheck.Release> updateNotice() {
        var check = updates;
        ProxyConfig current = config;
        if (check == null || current == null || !current.updateNotify()) return Optional.empty();
        return check.latest();
    }

    /** The text before the release link in a player notice. */
    public String updateText(com.siberanka.twilight.update.UpdateCheck.Release release) {
        return "[twilight-proxy] Version " + release.version() + " is available (this proxy runs " + platform.version() + "): ";
    }

    // --- Plugin messages from backends -------------------------------------------------------

    /**
     * A message on {@link PackChannel#CHANNEL} that came from backend {@code server}; returns the replies to send
     * back to it (a pack request, the features of this proxy's Bedrock runtime), possibly none.
     */
    public java.util.List<byte[]> serverMessage(String server, byte[] message) {
        if (message == null || message.length > PackChannel.MAX_MESSAGE) return java.util.List.of();
        ProxyConfig current = config;
        if (current == null) return java.util.List.of();
        java.util.List<byte[]> replies = new ArrayList<>(2);
        byte[] request = transfers.onMessage(server, message, current.maxPackBytes());
        if (request != null) replies.add(request);
        byte[] features = transfers.capabilities(message, capabilities());
        if (features != null) replies.add(features);
        return replies;
    }

    /**
     * The signed statement of what this proxy's runtime shows, sent to a server a few seconds after a player
     * reaches it: a backend that has not built a pack yet has nothing to announce, and would not learn it otherwise.
     * Null without a shared secret.
     */
    public byte[] capabilitiesStatement() {
        return transfers.capabilities(capabilities());
    }

    /** Tells the backends which items their players saw in item displays without a model (signed). */
    private void reportSightings() {
        ProxyRuntime running = runtime;
        if (running == null) return;
        try {
            running.sightings().flush(transfers.keys(), platform::sendToServer);
        } catch (RuntimeException failure) {
            platform.warn("Could not report item display sightings to the servers: " + failure.getMessage(), null);
        }
    }

    /** What this proxy's Bedrock runtime shows, so the backends build only what it uses. */
    int capabilities() {
        ProxyRuntime running = runtime;
        return running != null && running.showsItemDisplays() ? PackChannel.ITEM_DISPLAYS : 0;
    }

    // --- Bedrock sessions --------------------------------------------------------------------

    /** What the proxy does with a server connection of a Bedrock player. */
    public sealed interface Route {
        /** Let it happen. */
        record Allow() implements Route {}

        /** Cancel it: the client was transferred to load the server's pack. */
        record Transferred() implements Route {}

        /** Cancel it and connect the player to {@code server} instead (a reconnect waiting for its destination). */
        record Redirect(String server) implements Route {}
    }

    private static final Route ALLOW = new Route.Allow();

    /** The pack a new Bedrock session loads: that of the server it is going to join. */
    Optional<Path> packFor(String xuid) {
        if (xuid == null || xuid.isEmpty()) return Optional.empty();
        long now = now();
        Optional<Switches.Switch> reconnect = switches.get(xuid, now);
        String server = reconnect.map(entry -> entry.to).orElseGet(this::initialServer);
        Optional<PackFiles.Pack> pack = server.isEmpty() ? Optional.empty() : store.pack(server);
        if (loaded.size() < MAX_TRACKED) loaded.put(xuid, pack.map(PackFiles.Pack::hex).orElse(""));
        switches.reconnected(xuid, now).ifPresent(entry -> platform.info(entry.name + " reconnected after "
                + entry.seconds(now) + " s; loading the pack of " + entry.to
                + pack.map(found -> " (" + mib(found.size()) + ")").orElse("") + "."));
        return pack.map(PackFiles.Pack::path);
    }

    /**
     * The first server of a session. {@code chosen} is the server picked after every other plugin ran,
     * {@code original} the one the proxy picked; returns the server to use instead, if any.
     */
    public Optional<String> initial(UUID player, String name, String chosen, String original) {
        attachLate();
        Sessions bridge = geyser;
        if (bridge == null) return Optional.empty();
        long now = now();
        Optional<String> xuid = xuidOf(bridge, player, name, now);
        if (xuid.isEmpty()) return Optional.empty();
        Switches.Initial decision = switches.initial(xuid.get(), chosen, original, now);
        if (decision instanceof Switches.Initial.Route route) {
            Switches.Switch entry = route.entry();
            platform.info(name + " is back " + entry.seconds(now) + " s after the transfer"
                    + (entry.reconnected > 0 ? " (pack and login " + Math.max(0, (now - entry.reconnected + 500) / 1000) + " s)" : "")
                    + "; sending it to " + entry.to + ".");
            return Optional.of(entry.to);
        }
        if (decision instanceof Switches.Initial.Defer defer) {
            platform.info(name + " was sent to " + defer.heldOn() + " by another plugin (login or limbo server); it goes on to "
                    + defer.entry().to + " when it next changes servers.");
        }
        return Optional.empty();
    }

    /**
     * Called before {@code player} connects to {@code server} with every other plugin's checks passed.
     * {@code first} is true for the first server of a session.
     */
    public Route beforeConnect(UUID player, String name, String server, boolean first) {
        Sessions bridge = geyser;
        ProxyConfig current = config;
        if (bridge == null || current == null) return ALLOW;
        long now = now();
        Optional<String> xuid = xuidOf(bridge, player, name, now);
        if (xuid.isEmpty()) return ALLOW;
        Switches.Connect step = switches.connect(xuid.get(), server, first || loginServer(current, server), now);
        if (step instanceof Switches.Connect.Redirect redirect) {
            platform.info("Sending " + name + " on to " + redirect.entry().to + " instead of " + server
                    + " (the server it reconnected for, " + redirect.entry().seconds(now) + " s ago).");
            return new Route.Redirect(redirect.entry().to);
        }
        // The pack of a session was chosen for the server the proxy expected; when a plugin sends the player
        // somewhere else first (a login or limbo server), reconnecting there would only loop.
        if (first || !current.transferOnSwitch() || loginServer(current, server)) return ALLOW;
        if (!needsReconnect(xuid.get(), server)) return ALLOW;
        return reconnect(bridge, current, player, name, xuid.get(), server, now) ? new Route.Transferred() : ALLOW;
    }

    /** True when the session's loaded pack is not the one {@code server} needs (and that one is known). */
    private boolean needsReconnect(String xuid, String server) {
        String have = loaded.get(xuid);
        String want = wanted(server);
        return have != null && want != null && !want.equals(have);
    }

    /** Transfers the client to load the pack of {@code server}; false when it was not (limits, no address). */
    private boolean reconnect(Sessions bridge, ProxyConfig current, UUID player, String name, String xuidValue,
                              String server, long now) {
        Optional<String> xuid = Optional.of(xuidValue);
        Deque<Long> recent = recentTransfers.computeIfAbsent(xuid.get(), ignored -> new ArrayDeque<>());
        synchronized (recent) {
            while (!recent.isEmpty() && now - recent.peekFirst() > TRANSFER_WINDOW_MILLIS) recent.pollFirst();
            if (recent.size() >= TRANSFERS_PER_WINDOW) {
                platform.warn(name + " was reconnected " + TRANSFERS_PER_WINDOW + " times in "
                        + TRANSFER_WINDOW_MILLIS / 60_000 + " minutes; it joins " + server + " with the pack it has.", null);
                return false;
            }
            recent.addLast(now);
        }
        long bytes = store.pack(server).map(PackFiles.Pack::size).orElse(0L);
        long deadline = current.transferDeadlineMillis(bytes);
        String from = platform.currentServer(player).orElse("");
        java.net.InetAddress client = platform.address(player).orElse(null);
        java.net.InetAddress playing = bridge.address(player, name, client).orElse(null);
        Optional<String> address = bridge.transfer(player, name, client, current.transferAddress(), current.transferPort());
        if (address.isEmpty()) return false;
        switches.start(new Switches.Switch(xuid.get(), name, from, server.toLowerCase(Locale.ROOT), bytes, now,
                now + deadline, address.get(), playing == null ? null : com.siberanka.twilight.host.PackHost.normalise(playing)));
        platform.info("Reconnecting " + name + " to load the Bedrock pack of " + server + " (" + mib(bytes)
                + "); waiting up to " + Math.max(1, deadline / 60_000) + " min for it to come back.");
        if (bytes >= LARGE_PACK_BYTES && host == null && hinted.add(server.toLowerCase(Locale.ROOT))) {
            platform.info("The pack of " + server + " is " + mib(bytes) + ": Geyser sends at most about 1.2 MiB/s, so it takes "
                    + Math.max(1, bytes / GEYSER_BYTES_PER_SECOND) + " s or longer. Enable pack-host to let Bedrock download it over HTTP.");
        }
        return true;
    }

    /**
     * {@code player} is now connected to {@code server}. A reconnect ends here when that is its server; on any
     * other server (a login server, or where a plugin sent the player) it waits for the next server change.
     */
    public void connected(UUID player, String name, String server) {
        Sessions bridge = geyser;
        ProxyConfig current = config;
        if (bridge == null || current == null || server == null) return;
        long now = now();
        Optional<String> xuid = xuidOf(bridge, player, name, now);
        if (xuid.isEmpty()) return;
        boolean first = seated.size() < MAX_TRACKED && seated.add(player);
        Switches.Connected state = switches.connected(xuid.get(), server, now);
        // A fresh session whose first server is not the one its pack was chosen for (a proxy that reconnects
        // players to their last server, forced hosts): reconnect once for this server's pack. Cancelling the
        // first connection instead is not possible on every proxy, so this happens once the player is on it.
        if (first && state instanceof Switches.Connected.None && current.transferOnSwitch() && !loginServer(current, server)
                && needsReconnect(xuid.get(), server)) {
            platform.info(name + " joined " + server + ", not the server its pack was chosen for; reconnecting it once for "
                    + server + "'s pack (set initial-server to the server players join first to avoid this).");
            // Bedrock ignores a transfer while it is still loading the world: wait until it is in game.
            platform.later(() -> {
                if (!platform.currentServer(player).map(server::equalsIgnoreCase).orElse(false)) return;
                if (!needsReconnect(xuid.get(), server)) return;
                reconnect(bridge, current, player, name, xuid.get(), server, now());
            }, FIRST_SERVER_RECONNECT_DELAY_MILLIS);
            return;
        }
        if (state instanceof Switches.Connected.Arrived arrived) {
            Switches.Switch entry = arrived.entry();
            platform.info(name + " reached " + entry.to + " " + entry.seconds(now) + " s after the transfer"
                    + (entry.reconnected > 0 ? " (reconnect " + Math.max(0, (entry.reconnected - entry.started + 500) / 1000)
                    + " s, pack and login " + Math.max(0, (now - entry.reconnected + 500) / 1000) + " s)" : "") + ".");
        } else if (state instanceof Switches.Connected.Held held && held.first()) {
            platform.info(name + " joined " + server + " first (a login or limbo server, or another plugin's choice); it goes on to "
                    + held.entry().to + " when it next changes servers.");
        }
    }

    /** The connection of a {@link Route.Redirect} failed; the player goes where it asked to instead. */
    public void redirectFailed(String name, String server, String asked) {
        platform.info(name + " could not be sent on to " + server + "; it goes to " + asked + " as it asked.");
    }

    /**
     * Another plugin refused the first connection of a reconnected player to the server it reconnected for
     * (a login plugin that blocks every other server). Returns true when the caller should connect the player
     * to the server the proxy picked first; the reconnect then waits for the player's next server change.
     */
    public boolean refusedFirst(UUID player, String name, String server) {
        Sessions bridge = geyser;
        if (bridge == null) return false;
        long now = now();
        Optional<String> xuid = xuidOf(bridge, player, name, now);
        Optional<Switches.Switch> entry = xuid.flatMap(found -> switches.refused(found, server, now));
        entry.ifPresent(found -> platform.info("Another plugin refused " + server + " as " + name
                + "'s first server (login required?); it joins the proxy's first server and goes on to " + server
                + " when it next changes servers."));
        return entry.isPresent();
    }

    public void disconnect(UUID player, String name) {
        seated.remove(player);
        Sessions bridge = geyser;
        if (bridge == null) return;
        // A transferred player keeps its switch; its new session records its pack again.
        bridge.xuid(player, name, platform.address(player).orElse(null)).ifPresent(xuid -> {
            if (switches.get(xuid, now()).isEmpty()) loaded.remove(xuid);
        });
    }

    /**
     * The Bedrock session (XUID) of a proxy player: through Geyser, or for a transferred player whose Java UUID
     * Geyser does not know yet, by its name from the address its client plays from.
     */
    private Optional<String> xuidOf(Sessions bridge, UUID player, String name, long now) {
        java.net.InetAddress address = platform.address(player).map(com.siberanka.twilight.host.PackHost::normalise).orElse(null);
        return bridge.xuid(player, name, address).or(() -> switches.xuidByName(name, address, now));
    }

    private static String mib(long bytes) {
        return bytes >= 10 * 1_048_576 ? bytes / 1_048_576 + " MiB" : String.format(Locale.ROOT, "%.1f MiB", bytes / 1_048_576.0);
    }

    /** SHA-256 hex of the pack Bedrock players need on {@code server}; null when unknown (keep theirs). */
    private String wanted(String server) {
        ProxyConfig current = config;
        if (current.source(server) instanceof ProxyConfig.PackSource.None) return "";
        return store.pack(server).map(PackFiles.Pack::hex).orElse(null);
    }

    private String initialServer() {
        ProxyConfig current = config;
        String configured = current == null ? "" : current.initialServer();
        return configured.isEmpty() ? platform.defaultServer().toLowerCase(Locale.ROOT) : configured;
    }

    /** Every five seconds (package-private for tests). */
    void sweep() {
        transfers.sweep();
        syncLocalBackends(false);
        restartIfEmpty();
        long now = now();
        ProxyConfig settings = config;
        if (settings != null) store.sweepVersions(settings.transferDeadlineMillis(settings.maxPackBytes()) + 600_000);
        for (Switches.Note note : switches.sweep(now)) {
            if (note.warning()) platform.warn(note.text(), null);
            else platform.info(note.text());
        }
        recentTransfers.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                return entry.getValue().isEmpty() || now - entry.getValue().peekLast() > TRANSFER_WINDOW_MILLIS;
            }
        });
        ProxyConfig current = config;
        if (current != null && current.urlRefreshMinutes() > 0 && now - lastRefresh > current.urlRefreshMinutes() * 60_000L) {
            lastRefresh = now;
            store.refreshLinks();
        }
    }

    private volatile long lastRefresh = System.currentTimeMillis();

    public String statusText() {
        StringBuilder out = new StringBuilder("twilight-proxy: ")
                .append(transfers.enabled() ? "auto packs on" : "auto packs off (no shared secret)")
                .append(", Geyser ").append(geyser == null ? "not attached (" + geyserProblem + ")" : "attached")
                .append(", pack host ").append(host == null ? "off" : "on");
        if (geyser != null) out.append("\n  item mappings: ").append(mappingsState)
                .append(mappingsCopies.isEmpty() ? "" : " (remove " + String.join(", ", mappingsCopies) + ")");
        java.util.Set<String> login = new java.util.TreeSet<>(detectedLogin.keySet());
        ProxyConfig current = config;
        if (current != null) login.addAll(current.loginServers());
        out.append("\n  login servers: ").append(login.isEmpty() ? "none" : String.join(", ", login));
        Map<String, LocalBackends.Backend> local = localBackends;
        if (!local.isEmpty()) out.append("\n  local backends: ").append(String.join(", ", local.values().stream()
                .map(backend -> backend.server() + " (" + backend.folder().getFileName() + ")").toList()));
        if (restartPending != null) out.append("\n  restart pending: ").append(restartPending);
        var check = updates;
        out.append("\n  ").append(check == null ? "update check disabled" : check.status());
        for (String server : platform.serverNames()) {
            out.append("\n  ").append(server).append(": ").append(store.pack(server)
                    .map(pack -> pack.size() / 1024 + " KiB " + pack.hex().substring(0, 12)).orElse("no pack"));
        }
        return out.toString();
    }

    @Override public Optional<Path> pack(String server) {
        return store.pack(server).map(PackFiles.Pack::path);
    }

    @Override public Optional<String> packSha256(String server) {
        return store.pack(server).map(PackFiles.Pack::hex);
    }

    private static long now() {
        return System.currentTimeMillis();
    }
}
