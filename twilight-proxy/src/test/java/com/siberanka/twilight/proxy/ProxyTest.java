package com.siberanka.twilight.proxy;

import com.siberanka.twilight.protocol.PackChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/** Configuration, pack checks and the signed pack transfer between a backend and the proxy. */
class ProxyTest {
    private static final PackChannel.Key KEY = PackChannel.Key.derive("a-shared-secret-of-enough-length");
    private static final java.net.InetAddress LOCAL = java.net.InetAddress.getLoopbackAddress();
    private static final java.net.InetAddress OTHER = address(new byte[]{(byte) 198, 51, 100, 7});

    private static java.net.InetAddress address(byte[] bytes) {
        try {
            return java.net.InetAddress.getByAddress(bytes);
        } catch (java.net.UnknownHostException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
    @TempDir Path root;

    @Test
    void parsesTheDocumentedConfiguration() throws Exception {
        String defaults;
        try (var in = ProxyConfig.class.getResourceAsStream("/proxy-config.yml")) {
            defaults = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        ProxyConfig config = ProxyConfig.parse(defaults.replace("#   smp: https://example.com/packs/smp.mcpack", "    smp: https://example.com/packs/smp.mcpack")
                .replace("#   survival: survival.zip", "    survival: survival.zip"));
        assertInstanceOf(ProxyConfig.PackSource.Auto.class, config.source("lobby"));
        assertInstanceOf(ProxyConfig.PackSource.Url.class, config.source("SMP"));
        assertEquals(new ProxyConfig.PackSource.File("survival.zip"), config.source("survival"));
        assertInstanceOf(ProxyConfig.PackSource.Auto.class, config.source("unlisted"));
        assertTrue(config.transferOnSwitch());
        assertEquals(256L * 1024 * 1024, config.maxPackBytes());
        assertEquals(com.siberanka.twilight.host.HostSettings.DISABLED, config.host());
        var hosted = ProxyConfig.parse(defaults.replace("  enabled: false", "  enabled: true").replace("trusted-proxies: []", "trusted-proxies: [127.0.0.1]"));
        assertTrue(hosted.host().enabled());
        assertEquals(1, hosted.host().trustedProxies().size());
        assertEquals(com.siberanka.twilight.host.HostSettings.DISABLED, ProxyConfig.parse("packs:\n  default: auto\n").host());
        assertTrue(config.updateCheck());
        assertTrue(config.updateNotify());
        var quiet = ProxyConfig.parse("packs:\n  default: auto\nupdate-check:\n  enabled: false\n  notify-players: false\n");
        assertFalse(quiet.updateCheck());
        assertFalse(quiet.updateNotify());
        assertTrue(ProxyConfig.parse("packs:\n  default: auto\n").updateCheck());
    }

    @Test
    void refusesUnsafeOrMalformedSettings() {
        for (String value : List.of("../../secret.zip", "/etc/passwd", "C:\\x.zip", "ftp://host/x.mcpack", "file:///x", "https://user:pw@host/x")) {
            assertThrows(IllegalArgumentException.class, () -> ProxyConfig.parse("packs:\n  server:\n    a: \"" + value + "\"\n"), value);
        }
        assertThrows(IllegalArgumentException.class, () -> ProxyConfig.parse("packs:\n  server:\n    a: auto\n    a: none\n"));
        assertThrows(IllegalArgumentException.class, () -> ProxyConfig.parse("packs:\n  - list\n"));
        assertThrows(IllegalArgumentException.class, () -> ProxyConfig.parse("packs:\n\tserver: x\n"));
        assertThrows(IllegalArgumentException.class, () -> ProxyConfig.parse("packs:\n  default: auto\nmax-pack-size-mb: 99999\n"));
        assertThrows(IllegalArgumentException.class, () -> ProxyConfig.parse("packs:\n  default: auto\npack-host:\n  port: 70000\n"));
        assertThrows(IllegalArgumentException.class, () -> ProxyConfig.parse("packs:\n  default: auto\nupdate-check:\n  enabled: maybe\n"));
    }

    @Test
    void aReconnectWaitsOnLoginServersAndGoesOnAfterwards() {
        long now = 1_000_000;
        Switches switches = new Switches();
        // A login plugin chose "auth" for the reconnected session: it keeps the last word.
        switches.start(new Switches.Switch("x1", "Steve", "lobby", "survival", 1 << 20, now, now + 600_000, "play.example.com:19132", LOCAL));
        assertTrue(switches.xuidByName("steve", LOCAL, now).isEmpty(), "a name counts only once the client is back");
        assertTrue(switches.reconnected("x1", now + 4_000).isPresent());
        assertTrue(switches.reconnected("x1", now + 5_000).isEmpty(), "reported once");
        assertEquals(java.util.Optional.of("x1"), switches.xuidByName("STEVE", LOCAL, now + 5_000));
        // A Java player who takes the name from another address (offline-mode networks) never matches.
        assertTrue(switches.xuidByName("Steve", OTHER, now + 5_000).isEmpty());
        assertTrue(switches.xuidByName("Steve", null, now + 5_000).isEmpty());
        assertInstanceOf(Switches.Initial.Defer.class, switches.initial("x1", "auth", "lobby", now + 6_000));
        assertInstanceOf(Switches.Connect.None.class, switches.connect("x1", "auth", true, now + 6_000));
        var held = assertInstanceOf(Switches.Connected.Held.class, switches.connected("x1", "auth", now + 6_500));
        assertFalse(held.first(), "the deferral was already reported when the first server was chosen");
        // After the login, the plugin sends the player to the lobby: it goes to survival instead, once.
        var redirect = assertInstanceOf(Switches.Connect.Redirect.class, switches.connect("x1", "lobby", false, now + 40_000));
        assertEquals("survival", redirect.entry().to);
        assertInstanceOf(Switches.Connect.None.class, switches.connect("x1", "survival", false, now + 40_100));
        var arrived = assertInstanceOf(Switches.Connected.Arrived.class, switches.connected("x1", "survival", now + 41_000));
        assertEquals(41, arrived.entry().seconds(now + 41_000));
        assertInstanceOf(Switches.Connect.None.class, switches.connect("x1", "lobby", false, now + 50_000));

        // Nobody else changed the first server: straight to the destination; arrival counts once connected.
        switches.start(new Switches.Switch("x2", "Alex", "lobby", "survival", 0, now, now + 600_000, "a:1"));
        switches.reconnected("x2", now + 1_000);
        var route = assertInstanceOf(Switches.Initial.Route.class, switches.initial("x2", "lobby", "lobby", now + 2_000));
        assertEquals("survival", route.entry().to);
        assertInstanceOf(Switches.Connect.None.class, switches.connect("x2", "survival", true, now + 2_000));
        assertTrue(switches.get("x2", now + 2_000).isPresent(), "a request is not an arrival");
        assertInstanceOf(Switches.Connected.Arrived.class, switches.connected("x2", "survival", now + 2_500));
        assertTrue(switches.get("x2", now + 2_500).isEmpty());

        // A login plugin that runs after everyone (BungeeCord priority 127) turned the routed request into its
        // auth server: the player is held there and goes on at its next server change.
        switches.start(new Switches.Switch("x5", "Ray", "lobby", "survival", 0, now, now + 600_000, "a:1"));
        switches.reconnected("x5", now + 1_000);
        switches.initial("x5", "lobby", "lobby", now + 2_000);
        assertInstanceOf(Switches.Connect.None.class, switches.connect("x5", "survival", true, now + 2_000));
        assertInstanceOf(Switches.Connected.Held.class, switches.connected("x5", "auth_lobby", now + 2_400));
        // That plugin returns the player to survival itself: no redirect, arrival.
        assertInstanceOf(Switches.Connect.None.class, switches.connect("x5", "survival", false, now + 20_000));
        assertInstanceOf(Switches.Connected.Arrived.class, switches.connected("x5", "survival", now + 20_500));

        // A login plugin refused the destination as the first server: join the lobby, go on later.
        switches.start(new Switches.Switch("x3", "Kai", "lobby", "survival", 0, now, now + 600_000, "a:1"));
        switches.reconnected("x3", now + 1_000);
        switches.initial("x3", "lobby", "lobby", now + 2_000);
        assertTrue(switches.refused("x3", "survival", now + 2_000).isPresent());
        assertInstanceOf(Switches.Connect.None.class, switches.connect("x3", "lobby", true, now + 2_100));
        assertInstanceOf(Switches.Connected.Held.class, switches.connected("x3", "lobby", now + 2_200));
        assertInstanceOf(Switches.Connect.Redirect.class, switches.connect("x3", "hub", false, now + 30_000));
        // The redirect was refused as well: the player goes where it asked and the switch ends.
        assertInstanceOf(Switches.Connect.None.class, switches.connect("x3", "hub", false, now + 30_500));
        assertTrue(switches.get("x3", now + 30_500).isEmpty());
        assertInstanceOf(Switches.Connected.None.class, switches.connected("x3", "hub", now + 31_000));

        // A client that never comes back: one warning, then the switch expires.
        switches.start(new Switches.Switch("x4", "Lee", "lobby", "survival", 0, now, now + 300_000, "play.example.com:19132"));
        assertInstanceOf(Switches.Connect.None.class, switches.connect("x4", "lobby", false, now + 1_000));
        assertTrue(switches.sweep(now + 30_000).isEmpty());
        var warning = switches.sweep(now + 61_000);
        assertEquals(1, warning.size());
        assertTrue(warning.getFirst().warning() && warning.getFirst().text().contains("play.example.com:19132"), warning.toString());
        assertTrue(switches.sweep(now + 62_000).isEmpty(), "warned once");
        assertEquals(1, switches.sweep(now + 300_000).size());
        assertEquals(0, switches.size());
    }

    @Test
    void transfersOnlyNamePlainHosts() {
        for (String host : List.of("play.example.com", "127.0.0.1", "2001:db8::1", "[2001:db8::1]", "mc-01.example.net")) {
            assertTrue(TransferHosts.valid(host, 19132), host);
        }
        // A join address is chosen by the client: anything else is never echoed back in a transfer.
        for (String host : List.of("", "evil.com/path", "a b", "x\nINFO fake", "host:19132", "\u00e7ok.com", "a".repeat(254))) {
            assertFalse(TransferHosts.valid(host, 19132), host);
        }
        assertFalse(TransferHosts.valid(null, 19132));
        assertFalse(TransferHosts.valid("play.example.com", 0));
        assertFalse(TransferHosts.valid("play.example.com", 70_000));
    }

    @Test
    void reconnectDeadlinesGrowWithThePack() {
        ProxyConfig auto = ProxyConfig.parse("packs:\n  default: auto\n");
        assertEquals(180_000, auto.transferDeadlineMillis(0));
        assertEquals(180_000 + 1_200_000, auto.transferDeadlineMillis(150L * 1_048_576));
        assertEquals(3_600_000, auto.transferDeadlineMillis(2048L * 1_048_576));
        ProxyConfig fixed = ProxyConfig.parse("packs:\n  default: auto\ntransfer-timeout-seconds: 900\nlogin-servers: [Auth, limbo_1]\n");
        assertEquals(900_000, fixed.transferDeadlineMillis(150L * 1_048_576));
        assertTrue(fixed.loginServer("auth") && fixed.loginServer("LIMBO_1") && !fixed.loginServer("lobby"));
        assertTrue(auto.loginServers().isEmpty());
        for (String bad : List.of("transfer-timeout-seconds: 30", "transfer-timeout-seconds: soon", "login-servers: auth",
                "login-servers: [../x]")) {
            assertThrows(IllegalArgumentException.class, () -> ProxyConfig.parse("packs:\n  default: auto\n" + bad + "\n"), bad);
        }
    }

    @Test
    void geyserReadsAnImmutableCopyOfEveryPackVersion() throws Exception {
        TestPlatform platform = new TestPlatform(root.resolve("proxy-versions"));
        Path packs = platform.dataDirectory().resolve("packs");
        Files.createDirectories(packs);
        Files.writeString(platform.dataDirectory().resolve("config.yml"), "packs:\n  default: big.zip\n");
        Path source = zip("big-source.zip", "manifest.json", "{}", "a.bin", randomText(50_000));
        Files.copy(source, packs.resolve("big.zip"));
        PackStore store = new PackStore(platform);
        store.reload(ProxyConfig.load(platform.dataDirectory().resolve("config.yml")), List.of("lobby"));
        PackFiles.Pack first = store.pack("lobby").orElseThrow();
        assertTrue(first.path().startsWith(platform.dataDirectory().resolve("cache/versions")), first.path().toString());
        // The admin overwrites the pack in place while a download may be running: the copy does not change.
        Files.copy(zip("big-2.zip", "manifest.json", "{}", "b.bin", randomText(60_000)), packs.resolve("big.zip"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        assertArrayEquals(Files.readAllBytes(source), Files.readAllBytes(first.path()));
        store.reload(ProxyConfig.load(platform.dataDirectory().resolve("config.yml")), List.of("lobby"));
        PackFiles.Pack second = store.pack("lobby").orElseThrow();
        assertNotEquals(first.path(), second.path());
        store.sweepVersions(60_000);
        assertTrue(Files.isRegularFile(first.path()), "kept while a reconnect may still load it");
        store.sweepVersions(0);
        assertFalse(Files.exists(first.path()));
        assertTrue(Files.isRegularFile(second.path()));
    }

    @Test
    void checksPackArchivesBeforeUse() throws Exception {
        Path good = zip("good.mcpack", "manifest.json", "{}");
        assertEquals(Files.size(good), PackFiles.inspect(good, 1 << 20).size());
        assertThrows(IOException.class, () -> PackFiles.inspect(zip("nomanifest.zip", "pack/manifest.json", "{}"), 1 << 20));
        assertThrows(IOException.class, () -> PackFiles.inspect(zip("escape.zip", "manifest.json", "{}", "../evil.txt", "x"), 1 << 20));
        assertThrows(IOException.class, () -> PackFiles.inspect(good, 10));
        Path notZip = root.resolve("text.mcpack");
        Files.writeString(notZip, "not a zip");
        assertThrows(IOException.class, () -> PackFiles.inspect(notZip, 1 << 20));
        assertEquals("lobby_1", PackFiles.safeName("Lobby 1"));
        assertEquals("_..", PackFiles.safeName(".."));
    }

    @Test
    void signedMessagesRejectTamperingWrongKeysAndOldTimestamps() {
        long now = System.currentTimeMillis();
        byte[] sha = new byte[32];
        byte[] announce = PackChannel.announce(KEY, now, sha, 1234);
        assertInstanceOf(PackChannel.Announce.class, PackChannel.read(announce, List.of(KEY), now));
        byte[] tampered = announce.clone();
        tampered[10] ^= 1;
        assertNull(PackChannel.read(tampered, List.of(KEY), now));
        assertNull(PackChannel.read(announce, List.of(PackChannel.Key.derive("another-secret-entirely")), now));
        assertNull(PackChannel.read(announce, List.of(KEY), now + PackChannel.MAX_AGE_MILLIS + 1));
        assertNull(PackChannel.read(new byte[PackChannel.MAX_MESSAGE + 1], List.of(KEY), now));
        assertNull(PackChannel.read(new byte[3], List.of(KEY), now));
        assertThrows(IllegalArgumentException.class, () -> PackChannel.Key.derive("short"));
    }

    @Test
    void answersEveryValidAnnouncementWithTheSignedFeaturesOfItsRuntime() {
        long now = System.currentTimeMillis();
        TestPlatform platform = new TestPlatform(root.resolve("features"));
        AutoTransfers transfers = new AutoTransfers(platform, new PackStore(platform));
        byte[] announce = PackChannel.announce(KEY, now, new byte[32], 1234);
        assertNull(transfers.capabilities(announce, PackChannel.ITEM_DISPLAYS), "nothing without a shared secret");
        transfers.keys(List.of(KEY));
        byte[] reply = transfers.capabilities(announce, PackChannel.ITEM_DISPLAYS);
        PackChannel.Capabilities features = assertInstanceOf(PackChannel.Capabilities.class, PackChannel.read(reply, List.of(KEY), now));
        assertTrue(features.has(PackChannel.ITEM_DISPLAYS));
        assertNull(transfers.capabilities(reply, PackChannel.ITEM_DISPLAYS), "only announcements are answered");
        byte[] forged = PackChannel.announce(PackChannel.Key.derive("another-secret-entirely"), now, new byte[32], 1234);
        assertNull(transfers.capabilities(forged, PackChannel.ITEM_DISPLAYS));
        assertNull(PackChannel.read(reply, List.of(KEY), now + PackChannel.MAX_AGE_MILLIS + 60_000), "a replayed statement expires");
        byte[] tampered = reply.clone();
        tampered[12] ^= 1;
        assertNull(PackChannel.read(tampered, List.of(KEY), now));
    }

    @Test
    void receivesAnnouncedPacksOnlyWhenEveryChunkMatches() throws Exception {
        Path pack = zip("source.mcpack", "manifest.json", "{}", "big.bin", randomText(140_000));
        byte[] bytes = Files.readAllBytes(pack);
        byte[] sha = MessageDigest.getInstance("SHA-256").digest(bytes);
        TestPlatform platform = new TestPlatform(root.resolve("proxy"));
        Files.createDirectories(platform.dataDirectory());
        Files.writeString(platform.dataDirectory().resolve("config.yml"), "packs:\n  default: auto\n");
        PackStore store = new PackStore(platform);
        ProxyConfig config = ProxyConfig.load(platform.dataDirectory().resolve("config.yml"));
        store.reload(config, List.of("lobby"));
        AutoTransfers transfers = new AutoTransfers(platform, store);
        long max = config.maxPackBytes();
        // Without a shared secret nothing is accepted.
        assertNull(transfers.onMessage("lobby", PackChannel.announce(KEY, System.currentTimeMillis(), sha, bytes.length), max));
        transfers.keys(List.of(KEY));

        byte[] request = transfers.onMessage("lobby", PackChannel.announce(KEY, System.currentTimeMillis(), sha, bytes.length), max);
        PackChannel.Request decoded = (PackChannel.Request) PackChannel.read(request, List.of(KEY), System.currentTimeMillis());
        assertArrayEquals(sha, decoded.sha256());
        // A second announce while the transfer runs asks for nothing.
        assertNull(transfers.onMessage("lobby", PackChannel.announce(KEY, System.currentTimeMillis(), sha, bytes.length), max));

        int total = (bytes.length + PackChannel.CHUNK_BYTES - 1) / PackChannel.CHUNK_BYTES;
        byte[] wrongNonce = new byte[16];
        transfers.onMessage("lobby", PackChannel.chunk(KEY, wrongNonce, 0, total, bytes, PackChannel.CHUNK_BYTES), max);
        for (int index = 0; index < total; index++) {
            int length = Math.min(PackChannel.CHUNK_BYTES, bytes.length - index * PackChannel.CHUNK_BYTES);
            byte[] data = Arrays.copyOfRange(bytes, index * PackChannel.CHUNK_BYTES, index * PackChannel.CHUNK_BYTES + length);
            assertNull(transfers.onMessage("lobby", PackChannel.chunk(KEY, decoded.nonce(), index, total, data, length), max));
        }
        platform.runAsync();
        assertArrayEquals(sha, store.pack("LOBBY").orElseThrow().sha256());
        // The same pack is not requested again.
        assertNull(transfers.onMessage("lobby", PackChannel.announce(KEY, System.currentTimeMillis(), sha, bytes.length), max));
        try (var files = Files.list(platform.dataDirectory().resolve("cache"))) {
            assertTrue(files.noneMatch(file -> file.getFileName().toString().endsWith(".part")));
        }
    }

    @Test
    void outOfOrderChunksAbortTheTransfer() throws Exception {
        TestPlatform platform = new TestPlatform(root.resolve("proxy2"));
        Files.createDirectories(platform.dataDirectory());
        Files.writeString(platform.dataDirectory().resolve("config.yml"), "packs:\n  default: auto\n");
        PackStore store = new PackStore(platform);
        ProxyConfig config = ProxyConfig.load(platform.dataDirectory().resolve("config.yml"));
        store.reload(config, List.of("lobby"));
        AutoTransfers transfers = new AutoTransfers(platform, store);
        transfers.keys(List.of(KEY));
        byte[] sha = new byte[32];
        byte[] request = transfers.onMessage("lobby", PackChannel.announce(KEY, System.currentTimeMillis(), sha, 70_000), config.maxPackBytes());
        PackChannel.Request decoded = (PackChannel.Request) PackChannel.read(request, List.of(KEY), System.currentTimeMillis());
        byte[] data = new byte[PackChannel.CHUNK_BYTES];
        transfers.onMessage("lobby", PackChannel.chunk(KEY, decoded.nonce(), 1, 3, data, data.length), config.maxPackBytes());
        try (var files = Files.list(platform.dataDirectory().resolve("cache")).filter(Files::isRegularFile)) {
            assertEquals(0, files.count(), "the partial file is deleted");
        }
        assertTrue(store.pack("lobby").isEmpty());
    }

    @Test
    void findsTheVelocityForwardingSecret() throws Exception {
        Path proxy = Files.createDirectories(root.resolve("velocity"));
        Files.writeString(proxy.resolve("velocity.toml"), "player-info-forwarding-mode = \"modern\"\nforwarding-secret-file = \"forwarding.secret\"\n[servers]\nlobby = \"127.0.0.1:30066\"\n");
        Files.writeString(proxy.resolve("forwarding.secret"), "  abcdefghijklmnop1234  \n");
        assertEquals(List.of("abcdefghijklmnop1234"), ProxySecrets.discover(proxy, true, ""));
        Files.writeString(proxy.resolve("velocity.toml"), "player-info-forwarding-mode = \"legacy\"\n");
        assertTrue(ProxySecrets.discover(proxy, true, "").isEmpty());
        Files.writeString(proxy.resolve("velocity.toml"), "player-info-forwarding-mode = \"modern\"\nforwarding-secret-file = \"../outside.secret\"\n");
        Files.writeString(root.resolve("outside.secret"), "should-never-be-read-1234");
        assertTrue(ProxySecrets.discover(proxy, true, "").isEmpty(), "secret files outside the proxy are ignored");
    }

    private static String randomText(int length) {
        byte[] bytes = new byte[length / 2];
        new java.util.Random(7).nextBytes(bytes);
        return java.util.HexFormat.of().formatHex(bytes);
    }

    private Path zip(String name, String... entries) throws IOException {
        Path file = root.resolve(name);
        try (OutputStream out = Files.newOutputStream(file); ZipOutputStream zip = new ZipOutputStream(out)) {
            for (int index = 0; index < entries.length; index += 2) {
                zip.putNextEntry(new ZipEntry(entries[index]));
                zip.write(entries[index + 1].getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return file;
    }

    private static final class TestPlatform implements Platform {
        private final Path data;
        private final List<Runnable> queued = new ArrayList<>();

        TestPlatform(Path data) { this.data = data; }

        void runAsync() {
            List<Runnable> tasks = new ArrayList<>(queued);
            queued.clear();
            tasks.forEach(Runnable::run);
        }

        @Override public Path dataDirectory() { return data; }
        @Override public Path proxyRoot() { return data.getParent(); }
        @Override public boolean velocity() { return true; }
        @Override public Collection<String> serverNames() { return List.of("lobby"); }
        @Override public String defaultServer() { return "lobby"; }
        @Override public void info(String message) { }
        @Override public void warn(String message, Throwable failure) { }
        @Override public void async(Runnable task) { queued.add(task); }
        @Override public void repeat(Runnable task, long periodSeconds) { }
        @Override public void later(Runnable task, long millis) { }
        @Override public java.util.Optional<String> currentServer(java.util.UUID player) { return java.util.Optional.empty(); }
        @Override public java.util.Optional<java.net.InetAddress> address(java.util.UUID player) { return java.util.Optional.empty(); }
        @Override public String version() { return "test"; }
        @Override public void tellAdmins(String text, String url) { }
    }
}
