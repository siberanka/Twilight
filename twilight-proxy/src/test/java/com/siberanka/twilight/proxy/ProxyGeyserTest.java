package com.siberanka.twilight.proxy;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/** Attaching to the proxy's Geyser, its item mappings and the login servers of login plugins. */
class ProxyGeyserTest {
    @TempDir Path root;

    // --- Attaching to Geyser ---------------------------------------------------------------

    @Test
    void attachesWhenGeyserFinishesStartingLater() throws Exception {
        RecordingPlatform platform = platform("late");
        FakeSessions sessions = new FakeSessions(null);
        int[] calls = {0};
        ProxyCore core = new ProxyCore(platform, this, ignored -> ++calls[0] < 4
                ? Sessions.Attach.notReady("Geyser's API is not available yet") : Sessions.Attach.attached(sessions));
        core.enable();
        assertEquals(1, calls[0]);
        assertTrue(platform.infos.stream().anyMatch(line -> line.contains("not started yet")), platform.infos.toString());
        assertTrue(core.statusText().contains("Geyser not attached (Geyser's API is not available yet)"), core.statusText());
        platform.runQueued();
        platform.runQueued();
        platform.runQueued();
        assertEquals(4, calls[0]);
        assertEquals("", core.geyserProblem());
        assertTrue(core.statusText().contains("Geyser attached"), core.statusText());
        assertTrue(platform.infos.stream().anyMatch(line -> line.startsWith("Attached to Geyser after it started")));
        assertTrue(platform.warns.isEmpty(), platform.warns.toString());
        platform.runQueued();
        assertEquals(4, calls[0], "no further attempts once attached");
        core.disable();
    }

    @Test
    void reportsAnIncompatibleGeyserOnceAndDoesNotRetry() throws Exception {
        RecordingPlatform platform = platform("broken");
        int[] calls = {0};
        ProxyCore core = new ProxyCore(platform, this, ignored -> {
            calls[0]++;
            throw new NoSuchMethodError("org.geysermc.event.bus.OwnedEventBus.subscribe");
        });
        core.enable();
        platform.runQueued();
        assertEquals(1, calls[0]);
        assertEquals(1, platform.warns.stream().filter(line -> line.startsWith("Could not attach to Geyser")).count(), platform.warns.toString());
        assertTrue(platform.warns.get(0).contains("NoSuchMethodError"), platform.warns.toString());
        assertTrue(core.statusText().contains("not attached (Geyser's API could not be used"), core.statusText());
        core.disable();
    }

    @Test
    void aProxyWithoutGeyserSaysSoOnce() throws Exception {
        RecordingPlatform platform = platform("none");
        ProxyCore core = new ProxyCore(platform, this, ignored -> Sessions.Attach.failed("Geyser is not installed on this proxy"));
        core.enable();
        platform.runQueued();
        assertEquals(List.of("Geyser is not installed on this proxy: packs are prepared but not sent."),
                platform.infos.stream().filter(line -> line.contains("Geyser")).toList());
        assertTrue(platform.warns.isEmpty());
        // Players joining do not retry an absent Geyser.
        assertTrue(core.initial(UUID.randomUUID(), "Steve", "lobby", "lobby").isEmpty());
        core.disable();
    }

    @Test
    void anInstalledGeyserWhoseClassesAreInvisibleIsNamedAndRetried() throws Exception {
        // Geyser's API is not on this test's class path, as on a proxy that isolates plugins from each other.
        RecordingPlatform platform = platform("isolated");
        platform.geyser = "Geyser-BungeeCord 2.11.3-SNAPSHOT";
        ProxyCore core = new ProxyCore(platform, this);
        core.enable();
        assertTrue(core.geyserProblem().startsWith("Geyser-BungeeCord 2.11.3-SNAPSHOT is installed, but its API is not visible"
                + " to twilight-proxy (ClassNotFoundException"), core.geyserProblem());
        assertTrue(platform.infos.contains("Geyser is installed but not started yet; attaching when it is ready."));
        for (int round = 0; round < ProxyCore.ATTACH_ATTEMPTS + 1; round++) platform.runQueued();
        assertTrue(platform.warns.stream().anyMatch(line -> line.startsWith("Geyser did not become ready")
                && line.contains("is not visible to twilight-proxy")), platform.warns.toString());
        core.disable();

        RecordingPlatform none = platform("absent");
        ProxyCore absent = new ProxyCore(none, this);
        absent.enable();
        assertEquals("Geyser is not installed on this proxy", absent.geyserProblem());
        absent.disable();
    }

    @Test
    void givesUpWaitingAfterTwoMinutesAndTriesAgainWhenAPlayerJoins() throws Exception {
        RecordingPlatform platform = platform("slow");
        int[] calls = {0};
        boolean[] ready = {false};
        FakeSessions sessions = new FakeSessions(null);
        ProxyCore core = new ProxyCore(platform, this, ignored -> {
            calls[0]++;
            return ready[0] ? Sessions.Attach.attached(sessions) : Sessions.Attach.notReady("not loaded");
        });
        long[] now = {1_000_000};
        core.clock(() -> now[0]);
        core.enable();
        for (int round = 0; round < ProxyCore.ATTACH_ATTEMPTS + 5; round++) platform.runQueued();
        assertEquals(ProxyCore.ATTACH_ATTEMPTS + 1, calls[0]);
        assertEquals(1, platform.warns.stream().filter(line -> line.startsWith("Geyser did not become ready")).count());
        ready[0] = true;
        core.initial(UUID.randomUUID(), "Steve", "lobby", "lobby");
        assertEquals("not loaded", core.geyserProblem(), "at most one late attempt every ten seconds");
        now[0] += 11_000;
        core.initial(UUID.randomUUID(), "Steve", "lobby", "lobby");
        assertEquals("", core.geyserProblem());
        core.disable();
    }

    // --- Item mappings ---------------------------------------------------------------------

    @Test
    void mergesTheItemMappingsOfEveryServerAndReportsConflicts() {
        JsonObject lobby = mappings(legacy("minecraft:paper", 7, "twilight:minecraft_paper_7_aaaaaaaaaaaa"),
                definition("minecraft:iron_pickaxe", "demo:hammer", "twilight:demo_hammer_bbbbbbbbbbbb"));
        JsonObject survival = mappings(legacy("minecraft:paper", 7, "twilight:minecraft_paper_7_aaaaaaaaaaaa"),
                legacy("minecraft:paper", 8, "twilight:minecraft_paper_8_cccccccccccc"),
                // The same selector as the lobby's hammer, but another Bedrock item: a conflict.
                definition("minecraft:iron_pickaxe", "demo:hammer", "twilight:other_dddddddddddd"),
                // Another selector reusing the lobby's Bedrock item.
                legacy("minecraft:stick", 1, "twilight:minecraft_paper_7_aaaaaaaaaaaa"));
        Map<String, JsonObject> servers = new LinkedHashMap<>();
        servers.put("survival", survival);
        servers.put("lobby", lobby);
        ItemMappings.Merge merge = ItemMappings.merge(servers);
        assertEquals(3, merge.count());
        assertEquals(2, merge.conflicts().size(), merge.conflicts().toString());
        assertTrue(merge.conflicts().get(0).contains("lobby -> twilight:demo_hammer_bbbbbbbbbbbb, survival -> twilight:other_dddddddddddd (kept lobby)"),
                merge.conflicts().toString());
        JsonObject items = merge.mappings().getAsJsonObject("items");
        assertEquals(2, items.getAsJsonArray("minecraft:paper").size());
        assertEquals("twilight:demo_hammer_bbbbbbbbbbbb",
                items.getAsJsonArray("minecraft:iron_pickaxe").get(0).getAsJsonObject().get("bedrock_identifier").getAsString());
        assertFalse(items.has("minecraft:stick"));
        assertEquals(2, merge.mappings().get("format_version").getAsInt());
    }

    /**
     * FlameCord starts Geyser before twilight-proxy enables: the custom blocks are read in the load phase from the
     * packs the last run cached and the pack files, never from the version store or other files.
     */
    @Test
    void earlyBlocksReadTheCachedServerPacks() throws Exception {
        java.nio.file.Files.createDirectories(root.resolve("cache/versions"));
        java.nio.file.Files.createDirectories(root.resolve("packs"));
        java.nio.file.Files.writeString(root.resolve("cache/survival.mcpack"), "x");
        java.nio.file.Files.writeString(root.resolve("cache/versions/ab.mcpack"), "x");
        java.nio.file.Files.writeString(root.resolve("cache/notes.txt"), "x");
        java.nio.file.Files.writeString(root.resolve("packs/lobby.zip"), "x");
        assertEquals(List.of(root.resolve("cache/survival.mcpack"), root.resolve("packs/lobby.zip")),
                ProxyCore.cachedPacks(root));
        assertEquals(List.of(), ProxyCore.cachedPacks(root.resolve("missing")));
    }

    /** BungeeCord has not read its config when plugins load; twilight-proxy reads the server names itself. */
    @Test
    void readsBungeeServerNamesBeforeTheProxyDoes() throws Exception {
        Path config = root.resolve("config.yml");
        java.nio.file.Files.writeString(config, """
                listeners:
                - query_port: 25577
                  priorities:
                  - lobby
                servers:
                  # the hub
                  lobby:
                    motd: '&1Lobby'
                    address: 127.0.0.1:25583
                  'survival':
                    address: 127.0.0.1:25582
                timeout: 30000
                """);
        assertEquals(List.of("lobby", "survival"), ProxyCore.bungeeServerNames(config));
        assertEquals(List.of(), ProxyCore.bungeeServerNames(root.resolve("missing.yml")));
    }

    @Test
    void sameBedrockItemWithOtherOptionsIsNotAConflict() {
        JsonObject lobby = mappings(legacy("minecraft:paper", 7, "twilight:minecraft_paper_7_aaaaaaaaaaaa"));
        JsonObject held = legacy("minecraft:paper", 7, "twilight:minecraft_paper_7_aaaaaaaaaaaa");
        held.getAsJsonObject("bedrock_options").addProperty("display_handheld", true);
        ItemMappings.Merge merge = ItemMappings.merge(Map.of("lobby", lobby, "survival", mappings(held)));
        assertEquals(1, merge.count());
        assertEquals(List.of(), merge.conflicts());
        assertEquals(1, merge.optionDifferences());
    }

    @Test
    void keepsOnlyWellFormedTwilightMappings() {
        JsonObject extra = legacy("minecraft:paper", 1, "twilight:ok_000000000000");
        extra.addProperty("unexpected", "dropped");
        extra.getAsJsonObject("bedrock_options").addProperty("render_offsets", "dropped");
        JsonObject root = mappings(extra,
                legacy("minecraft:paper", 2, "minecraft:diamond"),            // not a Twilight item
                legacy("minecraft:paper", 3, "twilight:BAD ID"),              // invalid characters
                definition("minecraft:paper", "../../evil", "twilight:x_1"),  // invalid model
                withPredicate(legacy("minecraft:paper", 4, "twilight:p_1"), "\"text\""), // predicate must be objects
                withType(legacy("minecraft:paper", 5, "twilight:t_1"), "custom"));
        root.getAsJsonObject("items").add("Minecraft:Paper", new JsonArray());   // invalid Java item
        root.getAsJsonObject("items").addProperty("minecraft:apple", "not a list");
        ItemMappings.Merge merge = ItemMappings.merge(Map.of("lobby", root));
        assertEquals(1, merge.count());
        assertEquals(7, merge.invalid());
        JsonObject kept = merge.mappings().getAsJsonObject("items").getAsJsonArray("minecraft:paper").get(0).getAsJsonObject();
        assertFalse(kept.has("unexpected"));
        assertFalse(kept.getAsJsonObject("bedrock_options").has("render_offsets"));
        assertEquals(1, ItemMappings.merge(Map.of("lobby", JsonParser.parseString("{\"format_version\":1,\"items\":{}}").getAsJsonObject())).invalid());
    }

    @Test
    void readsMappingsFromPacksAndRejectsBrokenOnes() throws Exception {
        JsonObject mappings = mappings(legacy("minecraft:paper", 7, "twilight:minecraft_paper_7_aaaaaaaaaaaa"));
        assertEquals(mappings, ItemMappings.read(zip("with.zip", ItemMappings.PACK_ENTRY, mappings.toString())).orElseThrow());
        assertTrue(ItemMappings.read(zip("without.zip", "manifest.json", "{}")).isEmpty());
        assertThrows(IOException.class, () -> ItemMappings.read(zip("broken.zip", ItemMappings.PACK_ENTRY, "{not json")));
        assertThrows(IOException.class, () -> ItemMappings.read(zip("array.zip", ItemMappings.PACK_ENTRY, "[]")));
    }

    @Test
    void writesMergedMappingsIntoGeyserAndAsksForARestartOnlyAfterGeyserRegisteredItems() throws Exception {
        RecordingPlatform platform = platform("mappings", "lobby", "survival", "auth");
        Path packs = platform.dataDirectory().resolve("packs");
        Files.createDirectories(packs);
        Files.writeString(platform.dataDirectory().resolve("config.yml"),
                "packs:\n  default: none\n  server:\n    lobby: lobby.zip\n    survival: survival.zip\n");
        Files.copy(zip("lobby-src.zip", "manifest.json", "{}", ItemMappings.PACK_ENTRY,
                mappings(legacy("minecraft:paper", 7, "twilight:minecraft_paper_7_aaaaaaaaaaaa")).toString()), packs.resolve("lobby.zip"));
        Files.copy(zip("survival-src.zip", "manifest.json", "{}", ItemMappings.PACK_ENTRY,
                mappings(legacy("minecraft:paper", 8, "twilight:minecraft_paper_8_cccccccccccc")).toString()), packs.resolve("survival.zip"));
        Path geyser = root.resolve("geyser-folder");
        Files.createDirectories(geyser.resolve("custom_mappings"));
        Files.writeString(geyser.resolve("custom_mappings/twilight_item_mappings.json"), "{}");
        FakeSessions sessions = new FakeSessions(geyser);
        ProxyCore core = new ProxyCore(platform, this, ignored -> Sessions.Attach.attached(sessions));
        core.enable();

        Path file = geyser.resolve("custom_mappings").resolve(ItemMappings.FILE);
        JsonObject written = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertEquals(2, written.getAsJsonObject("items").getAsJsonArray("minecraft:paper").size());
        assertTrue(platform.infos.contains("Wrote item mappings for Geyser: 2 item(s) from lobby, survival."), platform.infos.toString());
        // A copy from a backend is moved out of Geyser before Geyser reads it.
        assertTrue(platform.warns.stream().anyMatch(line -> line.startsWith("Moved custom_mappings/twilight_item_mappings.json out of Geyser")),
                platform.warns.toString());
        assertFalse(Files.exists(geyser.resolve("custom_mappings/twilight_item_mappings.json")));
        assertFalse(platform.warns.stream().anyMatch(line -> line.contains("restart the proxy")), platform.warns.toString());

        // Geyser registers its items; an unchanged file needs no restart.
        sessions.registered = true;
        core.beforeGeyserItems();
        assertFalse(platform.warns.stream().anyMatch(line -> line.contains("restart the proxy")));

        // A server's pack changes after Geyser started: the file changes and a restart is asked for.
        Files.copy(zip("survival-2.zip", "manifest.json", "{}", ItemMappings.PACK_ENTRY,
                        mappings(legacy("minecraft:paper", 9, "twilight:minecraft_paper_9_eeeeeeeeeeee")).toString()),
                packs.resolve("survival.zip"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        assertTrue(core.reload());
        platform.runQueued();
        written = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertEquals("twilight:minecraft_paper_9_eeeeeeeeeeee", written.getAsJsonObject("items").getAsJsonArray("minecraft:paper")
                .get(1).getAsJsonObject().get("bedrock_identifier").getAsString());
        assertEquals(1, platform.warns.stream().filter(line -> line.contains("restart the proxy")).count(), platform.warns.toString());
        assertTrue(core.statusText().contains("item mappings: 2 item(s) from lobby, survival; restart the proxy"), core.statusText());
        core.disable();
    }

    @Test
    void recognisesLocalesInWhichGeyserCannotReadItemMappings() {
        // Measured on a Turkish Windows proxy: Geyser skipped every "definition" mapping until the locale was English.
        assertFalse(ProxyCore.localeReadsMappings(java.util.Locale.forLanguageTag("tr-TR")));
        assertFalse(ProxyCore.localeReadsMappings(java.util.Locale.forLanguageTag("az")));
        assertTrue(ProxyCore.localeReadsMappings(java.util.Locale.US));
        assertTrue(ProxyCore.localeReadsMappings(java.util.Locale.forLanguageTag("de-DE")));
    }

    /**
     * Floodgate bundles its own copy of Geyser's event library. twilight-proxy's classes must not refer to that
     * library at all, or a proxy that loads Floodgate first fails with "loader constraint violation" (FlameCord).
     */
    @Test
    void noClassLinksAgainstGeyserEventLibrary() throws Exception {
        Path classes = Path.of(ProxyCore.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        List<String> offenders = new ArrayList<>();
        try (var files = Files.walk(classes)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".class")).toList()) {
                String content = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
                if (content.contains("org/geysermc/event/")) offenders.add(classes.relativize(file).toString());
            }
        }
        assertTrue(Files.isRegularFile(classes.resolve("com/siberanka/twilight/geyser/GeyserEvents.class")));
        assertEquals(List.of(), offenders);
    }

    @Test
    void reportsOlderMappingFilesThatMapTheSameItemsDifferently() throws Exception {
        Path folder = root.resolve("clash");
        Files.createDirectories(folder);
        Path old = folder.resolve("twilight_network_item_mappings.json");
        JsonObject legacyTool = mappings(legacy("minecraft:paper", 7, "twilight:n_0123456789abcdef0123456789abcdef"),
                legacy("minecraft:paper", 8, "twilight:minecraft_paper_8_cccccccccccc"),
                legacy("minecraft:stick", 3, "twilight:n_fedcba9876543210fedcba9876543210"));
        Files.writeString(old, legacyTool.toString());
        ItemMappings.Merge merge = ItemMappings.merge(Map.of("lobby", mappings(
                legacy("minecraft:paper", 7, "twilight:minecraft_paper_7_aaaaaaaaaaaa"),
                legacy("minecraft:paper", 8, "twilight:minecraft_paper_8_cccccccccccc"))));
        assertEquals(1, ItemMappings.clashes(old, merge), "paper 7 differs, paper 8 is the same, stick is not ours");
        Files.writeString(old, "{broken");
        assertEquals(0, ItemMappings.clashes(old, merge));
        assertEquals(List.of("twilight_network_item_mappings.json"), ItemMappings.copies(folder));
    }

    // --- Backends on this machine and stale files ----------------------------------------------

    @Test
    void findsBackendFoldersOnThisMachineByTheirPort() throws Exception {
        Path machine = root.resolve("machine");
        backend(machine.resolve("lobby"), 25583, true);
        Path backup = backend(machine.resolve("lobby-backup"), 25583, true);
        Files.setLastModifiedTime(backup.resolve("logs/latest.log"), java.nio.file.attribute.FileTime.fromMillis(1_000));
        backend(machine.resolve("survival"), 25582, false); // no Twilight installed
        backend(machine.resolve("events"), 25590, true);
        Map<String, java.net.InetSocketAddress> servers = new LinkedHashMap<>();
        servers.put("lobby", new java.net.InetSocketAddress("127.0.0.1", 25583));
        servers.put("survival", new java.net.InetSocketAddress("127.0.0.1", 25582));
        servers.put("remote", new java.net.InetSocketAddress(InetAddress.getByAddress(new byte[]{(byte) 203, 0, 113, 5}), 25590));
        servers.put("events", java.net.InetSocketAddress.createUnresolved("localhost", 25590));
        List<String> log = new ArrayList<>();
        Map<String, LocalBackends.Backend> found = LocalBackends.discover(servers, List.of(machine), Map.of(), log::add);
        assertEquals(List.of("lobby", "events"), List.copyOf(found.keySet()));
        assertEquals(machine.resolve("lobby"), found.get("lobby").folder(), "the most recently active of two folders with one port");
        assertTrue(log.getFirst().contains("2 server folders use port 25583"), log.toString());
        assertEquals(machine.resolve("lobby/" + LocalBackends.EXPORT), found.get("lobby").export());

        // An explicit folder wins, and one without Twilight is refused.
        Map<String, LocalBackends.Backend> pinned = LocalBackends.discover(servers, List.of(machine),
                Map.of("lobby", backup, "survival", machine.resolve("survival")), log::add);
        assertEquals(backup, pinned.get("lobby").folder());
        assertFalse(pinned.containsKey("survival"));
        assertEquals(-1, LocalBackends.port(machine.resolve("missing/server.properties")));
    }

    @Test
    void readsLocalBackendsBeforeGeyserStartsAndRetiresStaleFiles() throws Exception {
        Path machine = root.resolve("network");
        Path proxy = machine.resolve("proxy");
        Path data = proxy.resolve("plugins/twilight-proxy");
        Files.createDirectories(data);
        Path lobby = backend(machine.resolve("lobby"), 25583, true);
        Files.createDirectories(lobby.resolve("plugins/Twilight/export"));
        Files.copy(zip("lobby-export.zip", "manifest.json", "{}", ItemMappings.PACK_ENTRY,
                mappings(legacy("minecraft:paper", 7, "twilight:minecraft_paper_7_aaaaaaaaaaaa")).toString()), lobby.resolve(LocalBackends.EXPORT));
        Files.setLastModifiedTime(lobby.resolve(LocalBackends.EXPORT), java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() - 60_000));
        Path geyser = machine.resolve("proxy/plugins/Geyser-Velocity");
        Files.createDirectories(geyser.resolve("custom_mappings"));
        Files.writeString(geyser.resolve("custom_mappings/twilight_network_item_mappings.json"),
                mappings(legacy("minecraft:paper", 7, "twilight:n_0123456789abcdef0123456789abcdef")).toString());
        Files.writeString(data.resolve("config.yml"), "packs:\n  default: auto\nitem-mappings:\n  restart: when-empty\n");

        RecordingPlatform platform = new RecordingPlatform(proxy, data, List.of("lobby"));
        platform.addresses.put("lobby", new java.net.InetSocketAddress("127.0.0.1", 25583));
        FakeSessions sessions = new FakeSessions(geyser);
        ProxyCore core = new ProxyCore(platform, this, ignored -> Sessions.Attach.attached(sessions));
        long[] now = {5_000_000};
        core.clock(() -> now[0]);
        core.enable();

        assertTrue(platform.infos.stream().anyMatch(line -> line.startsWith("Reading the Twilight builds of lobby from")), platform.infos.toString());
        assertTrue(platform.infos.stream().anyMatch(line -> line.startsWith("Loaded the Bedrock pack of lobby from its folder")), platform.infos.toString());
        assertFalse(Files.exists(geyser.resolve("custom_mappings/twilight_network_item_mappings.json")), "retired before Geyser reads it");
        assertTrue(platform.warns.stream().anyMatch(line -> line.startsWith("Moved custom_mappings/twilight_network_item_mappings.json out of Geyser")),
                platform.warns.toString());
        try (var retired = Files.walk(data.resolve("retired"))) {
            assertEquals(1, retired.filter(Files::isRegularFile).count());
        }
        JsonObject written = JsonParser.parseString(Files.readString(geyser.resolve("custom_mappings/" + ItemMappings.FILE))).getAsJsonObject();
        assertEquals("twilight:minecraft_paper_7_aaaaaaaaaaaa", written.getAsJsonObject("items").getAsJsonArray("minecraft:paper")
                .get(0).getAsJsonObject().get("bedrock_identifier").getAsString());
        assertTrue(core.adminNotice().isEmpty(), "written before Geyser registered its items: no restart needed");
        assertTrue(core.statusText().contains("local backends: lobby (lobby)"), core.statusText());

        // Geyser registered its items; the backend builds new ones, which the proxy picks up within a sweep.
        sessions.registered = true;
        Files.copy(zip("lobby-export-2.zip", "manifest.json", "{}", ItemMappings.PACK_ENTRY,
                        mappings(legacy("minecraft:paper", 7, "twilight:minecraft_paper_7_aaaaaaaaaaaa"),
                                legacy("minecraft:paper", 9, "twilight:minecraft_paper_9_bbbbbbbbbbbb")).toString()),
                lobby.resolve(LocalBackends.EXPORT), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Files.setLastModifiedTime(lobby.resolve(LocalBackends.EXPORT), java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() - 10_000));
        core.sweep();
        platform.runQueued();
        assertTrue(core.adminNotice().orElse("").contains("Restart the proxy so Geyser shows them; it restarts by itself once nobody is online"),
                core.adminNotice().toString());
        assertTrue(platform.told.stream().anyMatch(line -> line.startsWith("[twilight-proxy] Bedrock custom items changed")), platform.told.toString());

        // when-empty: not before a minute, not while players are online.
        platform.online = 3;
        now[0] += 120_000;
        core.sweep();
        assertEquals(0, platform.stops.size());
        platform.online = 0;
        core.sweep();
        assertEquals(1, platform.stops.size());
        core.sweep();
        assertEquals(1, platform.stops.size(), "stopped once");
        core.disable();
    }

    @Test
    void parsesLocalBackendAndItemMappingSettings() {
        ProxyConfig defaults = ProxyConfig.parse("packs:\n  default: auto\n");
        assertTrue(defaults.local().enabled());
        assertTrue(defaults.retireStaleFiles());
        assertFalse(defaults.restartWhenEmpty());
        ProxyConfig custom = ProxyConfig.parse("packs:\n  default: auto\nlocal-backends:\n  mode: off\n  search: [/srv/mc, ../other]\n"
                + "  server:\n    Survival: ../survival\nitem-mappings:\n  retire-stale-files: false\n  restart: when-empty\n");
        assertFalse(custom.local().enabled());
        assertEquals(2, custom.local().search().size());
        assertEquals(Path.of("../survival").normalize(), custom.local().servers().get("survival"));
        assertFalse(custom.retireStaleFiles());
        assertTrue(custom.restartWhenEmpty());
        assertThrows(IllegalArgumentException.class, () -> ProxyConfig.parse("packs:\n  default: auto\nlocal-backends:\n  mode: sometimes\n"));
        assertThrows(IllegalArgumentException.class, () -> ProxyConfig.parse("packs:\n  default: auto\nitem-mappings:\n  restart: always\n"));
    }

    private static Path backend(Path folder, int port, boolean twilight) throws IOException {
        Files.createDirectories(folder.resolve("logs"));
        Files.writeString(folder.resolve("server.properties"), "motd=test\nserver-port=" + port + "\n");
        Files.writeString(folder.resolve("logs/latest.log"), "started");
        if (twilight) Files.createDirectories(folder.resolve("plugins/Twilight"));
        return folder;
    }

    // --- Login servers ---------------------------------------------------------------------

    @Test
    void findsLoginServersInLoginPluginConfigurations() throws Exception {
        Path proxy = root.resolve("login-proxy");
        write(proxy.resolve("plugins/LeaderOS-Auth/config.yml"), """
                settings:
                  # Players will be redirected to this server to login/register.
                  auth-server: auth_lobby
                  url: "https://example.com"
                """);
        write(proxy.resolve("plugins/authmevelocity/config.toml"), """
                [advanced]
                auth-servers = ["auth2", "lobby-auth"]
                """);
        write(proxy.resolve("plugins/AuthMeBungee/config.yml"), """
                authServers:
                - captcha
                - unknown_server
                """);
        write(proxy.resolve("plugins/librelogin/config.conf"), """
                limbo=[
                    limbo
                ]
                """);
        // Not a login plugin's folder: never read.
        write(proxy.resolve("plugins/Shop/config.yml"), "auth-server: survival\n");
        Map<String, String> found = LoginServers.detect(proxy,
                List.of("Auth_Lobby", "auth2", "lobby-auth", "captcha", "limbo", "survival", "lobby"));
        assertEquals(List.of("auth2", "auth_lobby", "captcha", "limbo", "lobby-auth"), List.copyOf(found.keySet()));
        assertEquals("plugins/LeaderOS-Auth/config.yml", found.get("auth_lobby"));
        assertEquals("plugins/librelogin/config.conf", found.get("limbo"));
        assertEquals(List.of("auth", "auth2"), LoginServers.servers("auth-servers: [auth, \"auth2\"] # comment\n"));
        assertTrue(LoginServers.detect(root.resolve("no-proxy"), List.of("auth")).isEmpty());
    }

    @Test
    void ignoresConfigurationsOfLoginPluginsThatAreNotInstalled() throws Exception {
        RecordingPlatform platform = platform("leftover", "auth", "lobi");
        write(platform.proxyRoot().resolve("plugins/LeaderOS-Auth/config.yml"), "settings:\n  auth-server: auth\n");
        // AuthMeBungee was removed, its folder stayed.
        write(platform.proxyRoot().resolve("plugins/AuthMeBungee/config.yml"), "authServers:\n- lobi\n");
        platform.folders = java.util.Set.of("LeaderOS-Auth", "twilight-proxy", "Geyser-BungeeCord");
        ProxyCore core = new ProxyCore(platform, this, ignored -> Sessions.Attach.failed("Geyser is not installed on this proxy"));
        core.enable();
        assertTrue(core.statusText().contains("login servers: auth\n"), core.statusText());
        assertTrue(platform.infos.contains("Not reading plugins/AuthMeBungee: that plugin is not installed (a folder left"
                + " behind); its login servers are not used."), platform.infos.toString());
        assertFalse(platform.infos.stream().anyMatch(line -> line.startsWith("Login server lobi")));
        core.disable();
    }

    @Test
    void detectedLoginServersNeverCauseAReconnect() throws Exception {
        RecordingPlatform platform = platform("login", "lobby", "auth_lobby");
        write(platform.proxyRoot().resolve("plugins/LeaderOS-Auth/config.yml"), "settings:\n  auth-server: auth_lobby\n");
        ProxyCore core = new ProxyCore(platform, this, ignored -> Sessions.Attach.failed("Geyser is not installed on this proxy"));
        core.enable();
        assertTrue(platform.infos.contains("Login server auth_lobby (found in plugins/LeaderOS-Auth/config.yml):"
                + " Bedrock players are never reconnected for it."), platform.infos.toString());
        assertTrue(core.statusText().contains("login servers: auth_lobby"), core.statusText());
        core.disable();
    }

    // --- Helpers ---------------------------------------------------------------------------

    private RecordingPlatform platform(String name, String... servers) throws IOException {
        Path proxy = root.resolve(name);
        Path data = proxy.resolve("plugins/twilight-proxy");
        Files.createDirectories(data);
        return new RecordingPlatform(proxy, data, servers.length == 0 ? List.of("lobby") : List.of(servers));
    }

    private static JsonObject mappings(JsonObject... entries) {
        JsonObject items = new JsonObject();
        for (JsonObject entry : entries) {
            String item = entry.remove("item").getAsString();
            if (!items.has(item)) items.add(item, new JsonArray());
            items.getAsJsonArray(item).add(entry);
        }
        JsonObject root = new JsonObject();
        root.addProperty("format_version", 2);
        root.add("items", items);
        return root;
    }

    private static JsonObject legacy(String item, int data, String identifier) {
        JsonObject entry = new JsonObject();
        entry.addProperty("item", item);
        entry.addProperty("type", "legacy");
        entry.addProperty("custom_model_data", data);
        entry.addProperty("bedrock_identifier", identifier);
        JsonObject options = new JsonObject();
        options.addProperty("icon", identifier.replace(':', '.'));
        entry.add("bedrock_options", options);
        return entry;
    }

    private static JsonObject definition(String item, String model, String identifier) {
        JsonObject entry = legacy(item, 0, identifier);
        entry.remove("custom_model_data");
        entry.addProperty("type", "definition");
        entry.addProperty("model", model);
        JsonObject predicate = new JsonObject();
        predicate.addProperty("type", "damaged");
        entry.add("predicate", predicate);
        return entry;
    }

    private static JsonObject withPredicate(JsonObject entry, String json) {
        entry.add("predicate", JsonParser.parseString(json));
        return entry;
    }

    private static JsonObject withType(JsonObject entry, String type) {
        entry.addProperty("type", type);
        return entry;
    }

    private Path zip(String name, String... entries) throws IOException {
        Path file = root.resolve(name);
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(file))) {
            for (int index = 0; index < entries.length; index += 2) {
                out.putNextEntry(new ZipEntry(entries[index]));
                out.write(entries[index + 1].getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return file;
    }

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }

    private static final class FakeSessions implements Sessions {
        private final Path folder;
        volatile boolean registered;

        FakeSessions(Path folder) { this.folder = folder; }

        @Override public Optional<String> xuid(UUID player, String name, InetAddress address) { return Optional.empty(); }
        @Override public Optional<InetAddress> address(UUID player, String name, InetAddress address) { return Optional.empty(); }
        @Override public Optional<String> transfer(UUID player, String name, InetAddress from, String address, int port) { return Optional.empty(); }
        @Override public void prepare(Path pack) { }
        @Override public Optional<Path> geyserFolder() { return Optional.ofNullable(folder); }
        @Override public boolean itemsRegistered() { return registered; }
        @Override public void close() { }
    }

    private static final class RecordingPlatform implements Platform {
        private final Path proxy;
        private final Path data;
        private final List<String> servers;
        final List<String> infos = new ArrayList<>();
        final List<String> warns = new ArrayList<>();
        private final List<Runnable> queued = new ArrayList<>();

        RecordingPlatform(Path proxy, Path data, List<String> servers) {
            this.proxy = proxy;
            this.data = data;
            this.servers = servers;
        }

        /** Runs what is due now (one round; tasks queued while running wait for the next round). */
        void runQueued() {
            List<Runnable> tasks = new ArrayList<>(queued);
            queued.clear();
            tasks.forEach(Runnable::run);
        }

        @Override public Path dataDirectory() { return data; }
        @Override public Path proxyRoot() { return proxy; }
        @Override public boolean velocity() { return true; }
        @Override public Collection<String> serverNames() { return servers; }
        @Override public String defaultServer() { return servers.get(0); }
        @Override public void info(String message) { infos.add(message); }
        @Override public void warn(String message, Throwable failure) { warns.add(message); }
        @Override public void async(Runnable task) { task.run(); }
        @Override public void repeat(Runnable task, long periodSeconds) { }
        @Override public void later(Runnable task, long millis) { queued.add(task); }
        @Override public Optional<InetAddress> address(UUID player) { return Optional.empty(); }
        @Override public Optional<String> currentServer(UUID player) { return Optional.empty(); }
        @Override public String version() { return "test"; }
        @Override public void tellAdmins(String text, String url) { told.add(text); }
        @Override public Optional<String> geyserPlugin() { return Optional.ofNullable(geyser); }
        @Override public Map<String, java.net.InetSocketAddress> serverAddresses() { return addresses; }
        @Override public int onlinePlayers() { return online; }
        @Override public void stopProxy(String reason) { stops.add(reason); }

        final List<String> told = new ArrayList<>();
        final List<String> stops = new ArrayList<>();
        final Map<String, java.net.InetSocketAddress> addresses = new LinkedHashMap<>();
        int online;
        @Override public Optional<java.util.Set<String>> pluginFolders() { return Optional.ofNullable(folders); }

        String geyser;
        java.util.Set<String> folders;
    }
}
