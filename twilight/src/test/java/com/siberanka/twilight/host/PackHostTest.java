package com.siberanka.twilight.host;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The Bedrock pack host: links, limits, strict requests and the settings that shape the links. */
class PackHostTest {
    private static final InetAddress LOCAL = InetAddress.getLoopbackAddress();
    @TempDir Path root;
    private PackHost host;

    @AfterEach
    void close() {
        if (host != null) host.close();
    }

    @Test
    void servesALinkOnlyToItsPlayer() throws Exception {
        Path pack = pack(70_000);
        host = start(true, 3);
        String url = link(pack, LOCAL);
        assertTrue(url.matches("http://localhost:\\d+/twilight/[A-Za-z0-9_-]{43}/[0-9a-f]{16}\\.zip"), url);
        Response ok = get(path(url), "");
        assertEquals(200, ok.status);
        assertEquals("application/zip", ok.headers.get("content-type"));
        assertEquals(String.valueOf(Files.size(pack)), ok.headers.get("content-length"));
        assertArrayEquals(Files.readAllBytes(pack), ok.body);
        assertEquals("\"" + sha(pack) + "\"", ok.headers.get("etag"));

        String other = host.link(snapshot(pack), InetAddress.getByName("203.0.113.9"), "localhost").orElseThrow();
        assertEquals(404, get(path(other), "").status);
        assertEquals(0, get(path(other), "").body.length);
    }

    @Test
    void refusesEverythingOutsideALink() throws Exception {
        Path pack = pack(1000);
        host = start(true, 3);
        String url = path(link(pack, LOCAL));
        String token = url.split("/")[2];
        for (String target : List.of("/", "/twilight/", url.replace(".zip", ".mcpack"), url.substring(0, url.length() - 6) + "0.zip",
                "/twilight/" + "A".repeat(43) + "/" + sha(pack).substring(0, 16) + ".zip", url + "?x=1", "/../" + url,
                "/twilight/" + token + "/../" + sha(pack).substring(0, 16) + ".zip")) {
            assertEquals(404, get(target, "").status, target);
        }
        assertEquals(405, request("POST " + url + " HTTP/1.1\r\nHost: x\r\n\r\n").status);
        assertNull(request("GET " + url + "\r\n\r\n"));                       // HTTP/0.9 style
        assertNull(request("GET " + url + " HTTP/1.1\r\nBad Header\r\n\r\n")); // no colon
        assertNull(request("GET " + url + " HTTP/1.1\r\nX: " + "a".repeat(9000) + "\r\n\r\n"));
        assertEquals(200, get(url, "").status); // the link itself still works
    }

    @Test
    void limitsDownloadsAndSupportsHeadAndRanges() throws Exception {
        Path pack = pack(5000);
        host = start(true, 2);
        String url = path(link(pack, LOCAL));
        Response head = request("HEAD " + url + " HTTP/1.1\r\nHost: x\r\n\r\n");
        assertEquals(200, head.status);
        assertEquals(0, head.body.length);
        Response part = get(url, "Range: bytes=100-199\r\n");
        assertEquals(206, part.status);
        assertEquals("bytes 100-199/5000", part.headers.get("content-range"));
        assertArrayEquals(java.util.Arrays.copyOfRange(Files.readAllBytes(pack), 100, 200), part.body);
        assertEquals(416, get(url, "Range: bytes=6000-\r\n").status);
        assertEquals(200, get(url, "").status);
        assertEquals(404, get(url, "").status); // two downloads used (HEAD and 416 are free)
        assertEquals(0, host.liveLinks());
    }

    @Test
    void servesSeveralRequestsOnOneConnection() throws Exception {
        Path pack = pack(3000);
        host = start(true, 3);
        String url = path(link(pack, LOCAL));
        try (Socket socket = new Socket(LOCAL, host.port())) {
            socket.setSoTimeout(10_000);
            var out = socket.getOutputStream();
            out.write(("GET " + url + " HTTP/1.1\r\nHost: x\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
            Response first = read(socket.getInputStream(), false);
            assertEquals(200, first.status);
            assertNull(first.headers.get("connection"));
            out.write(("GET " + url + " HTTP/1.1\r\nHost: x\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
            Response second = read(socket.getInputStream(), false);
            assertEquals(200, second.status);
            assertEquals("close", second.headers.get("connection"));
            assertArrayEquals(Files.readAllBytes(pack), second.body);
        }
    }

    @Test
    void blocksAddressesThatKeepGuessing() throws Exception {
        Path pack = pack(1000);
        host = start(true, 3);
        String url = path(link(pack, LOCAL));
        for (int attempt = 0; attempt < 20; attempt++) get("/twilight/" + "B".repeat(43) + "/0000000000000000.zip", "");
        assertNull(get(url, "").statusOrNull()); // the connection is closed without an answer
    }

    @Test
    void trustsForwardedAddressesOnlyFromListedProxies() throws Exception {
        Path pack = pack(1000);
        InetAddress player = InetAddress.getByName("198.51.100.7");
        host = start(true, 3);
        String url = path(host.link(snapshot(pack), player, "localhost").orElseThrow());
        assertEquals(404, get(url, "X-Forwarded-For: 198.51.100.7\r\n").status);
        host.close();

        host = PackHost.start(settings(true, 3, List.of(LOCAL)), root.resolve("cache2"), line -> {});
        url = path(host.link(host.snapshot(pack, digest(pack)).orElseThrow(), player, "localhost").orElseThrow());
        assertEquals(200, get(url, "X-Forwarded-For: 10.0.0.1, 198.51.100.7\r\n").status);
    }

    @Test
    void neverOffersAPackThatChangedSinceGeyserHashedIt() throws Exception {
        Path pack = pack(1000);
        host = start(true, 3);
        byte[] old = digest(pack);
        Files.write(pack, new byte[]{1, 2, 3}, java.nio.file.StandardOpenOption.APPEND);
        assertTrue(host.snapshot(pack, old).isEmpty());
        assertTrue(host.snapshot(pack, digest(pack)).isPresent());
    }

    @Test
    void clientsThatCannotUseTheirLinkGetGeyserForAWhile() throws Exception {
        Path pack = pack(2000);
        List<String> log = new java.util.ArrayList<>();
        host = PackHost.start(settings(true, 3, List.of()), root.resolve("cache"), log::add);
        String url = link(pack, LOCAL);
        assertTrue(host.usable(LOCAL));
        host.fallback(url); // Geyser had to send the pack: the link was never requested
        assertFalse(host.usable(LOCAL));
        assertTrue(host.usable(addr("198.51.100.1")), "other players keep their links");
        assertTrue(log.getLast().contains("never reached the host"), log.toString());
        int lines = log.size();
        host.fallback(url);
        assertEquals(lines, log.size(), "reported once");

        // A link used from another address: the player's address changes on the way (NAT, proxy).
        InetAddress player = addr("198.51.100.7");
        String other = host.link(snapshot(pack), player, "localhost").orElseThrow();
        assertEquals(404, get(path(other), "").status);
        host.fallback(other);
        assertTrue(log.getLast().contains("used from 127.0.0.1"), log.toString());
        assertFalse(host.usable(player));
    }

    @Test
    void parsesAndValidatesSettings() {
        HostSettings parsed = HostSettings.parse(Map.<String, Object>of("enabled", "true", "port", 9000,
                "trusted-proxies", "[127.0.0.1, '::ffff:10.0.0.2']", "public-address", "https://packs.example.com")::get);
        assertTrue(parsed.enabled());
        assertEquals(9000, parsed.port());
        assertEquals(List.of(LOCAL, addr("10.0.0.2")), parsed.trustedProxies());
        assertEquals("https://packs.example.com", parsed.baseUrl("ignored", 9000).orElseThrow());
        assertEquals(HostSettings.DISABLED, HostSettings.parse(key -> null));
        assertEquals(List.of(), HostSettings.parse(Map.<String, Object>of("trusted-proxies", "[]")::get).trustedProxies());

        HostSettings auto = HostSettings.DISABLED;
        assertEquals("http://play.example.com:8163", auto.baseUrl("Play.Example.com.", 8163).orElseThrow());
        assertEquals("http://[2001:db8::1]:8163", auto.baseUrl("2001:db8::1", 8163).orElseThrow());
        assertTrue(auto.baseUrl("evil.com/x?y", 8163).isEmpty());
        assertTrue(auto.baseUrl("a@b", 8163).isEmpty());
        assertTrue(auto.baseUrl(null, 8163).isEmpty());
        HostSettings nat = new HostSettings(true, "", 8163, "203.0.113.4", 25_000, true, 10, 3, 64, 2, List.of());
        assertEquals("http://203.0.113.4:25000", nat.baseUrl("anything", 8163).orElseThrow());

        for (Map<String, Object> bad : List.<Map<String, Object>>of(Map.of("port", 0), Map.of("port", "x"), Map.of("link-minutes", 500),
                Map.of("downloads-per-link", 0), Map.of("enabled", "yes"), Map.of("bind-address", "example.com"),
                Map.of("public-address", "https://user:pw@host"), Map.of("public-address", "http://host/path"),
                Map.of("public-address", "ftp://host"), Map.of("trusted-proxies", "[proxy.example.com]"),
                Map.of("trusted-proxies", "127.0.0.1"), Map.of("max-connections-per-address", 100))) {
            assertThrows(IllegalArgumentException.class, () -> HostSettings.parse(bad::get), bad.toString());
        }
    }

    @Test
    void keepsConnectionsOpenLongEnoughForScannersToHandOnLargePacks() {
        assertEquals(5_000, PackHost.drainMillis(0));
        assertEquals(15_000 + 1_000, PackHost.drainMillis(128 * 1024));
        assertEquals(15_000 + 320_000, PackHost.drainMillis(40L * 1_048_576));
        assertEquals(600_000, PackHost.drainMillis(500L * 1_048_576), "capped at ten minutes");
    }

    @Test
    void mapsIpv4InIpv6ToIpv4() throws Exception {
        assertEquals(addr("10.1.2.3"), PackHost.normalise(InetAddress.getByAddress(
                new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xff, (byte) 0xff, 10, 1, 2, 3})));
        assertEquals(addr("2001:db8::1"), PackHost.normalise(addr("2001:db8::1")));
        assertEquals(addr("2001:db8:1:2::"), PackHost.peerKey(addr("2001:db8:1:2:aaaa::5")));
        assertEquals(addr("10.1.2.3"), PackHost.peerKey(addr("10.1.2.3")));
    }

    private PackHost start(boolean requireAddress, int downloads) throws IOException {
        return PackHost.start(settings(requireAddress, downloads, List.of()), root.resolve("cache"), line -> {});
    }

    private static HostSettings settings(boolean requireAddress, int downloads, List<InetAddress> proxies) throws IOException {
        int port;
        try (ServerSocket free = new ServerSocket(0)) {
            port = free.getLocalPort();
        }
        return new HostSettings(true, "127.0.0.1", port, "auto", 0, requireAddress, 10, downloads, 64, 16, proxies);
    }

    private String link(Path pack, InetAddress player) throws Exception {
        return host.link(snapshot(pack), player, "localhost").orElseThrow();
    }

    private PackHost.Snapshot snapshot(Path pack) throws Exception {
        return host.snapshot(pack, digest(pack)).orElseThrow();
    }

    private Path pack(int size) throws IOException {
        byte[] bytes = new byte[size];
        new java.util.Random(size).nextBytes(bytes);
        Path file = root.resolve("pack-" + size + ".zip");
        Files.write(file, bytes);
        return file;
    }

    private static String path(String url) {
        return url.substring(url.indexOf('/', "http://".length()));
    }

    private Response get(String target, String extraHeaders) throws IOException {
        Response response = request("GET " + target + " HTTP/1.1\r\nHost: localhost\r\n" + extraHeaders + "\r\n");
        return response == null ? new Response(-1, Map.of(), new byte[0]) : response;
    }

    /** Sends raw bytes; null when the server closes without an answer. */
    private Response request(String raw) throws IOException {
        try (Socket socket = new Socket(LOCAL, host.port())) {
            socket.setSoTimeout(10_000);
            socket.getOutputStream().write(raw.getBytes(StandardCharsets.ISO_8859_1));
            socket.getOutputStream().flush();
            return read(socket.getInputStream(), raw.startsWith("HEAD"));
        }
    }

    /** One response: the head, then Content-Length bytes (none for HEAD). Null when the connection ends first. */
    private static Response read(InputStream in, boolean head) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            while (true) {
                int next = in.read();
                if (next < 0) return null;
                bytes.write(next);
                String text = bytes.toString(StandardCharsets.ISO_8859_1);
                if (!text.endsWith("\r\n\r\n")) continue;
                String[] lines = text.substring(0, text.length() - 4).split("\r\n");
                Map<String, String> headers = new HashMap<>();
                for (int index = 1; index < lines.length; index++) {
                    int colon = lines[index].indexOf(':');
                    headers.put(lines[index].substring(0, colon).toLowerCase(), lines[index].substring(colon + 1).strip());
                }
                int length = head ? 0 : Integer.parseInt(headers.getOrDefault("content-length", "0"));
                return new Response(Integer.parseInt(lines[0].split(" ")[1]), headers, in.readNBytes(length));
            }
        } catch (IOException closed) {
            return null;
        }
    }

    private record Response(int status, Map<String, String> headers, byte[] body) {
        Integer statusOrNull() {
            return status < 0 ? null : status;
        }
    }

    private static byte[] digest(Path file) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file));
    }

    private static String sha(Path file) throws Exception {
        return java.util.HexFormat.of().formatHex(digest(file));
    }

    private static InetAddress addr(String text) {
        return HostSettings.literal(text);
    }
}
