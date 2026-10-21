/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.host;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A small HTTP server that hands Bedrock packs to the players Geyser is connecting, and to nobody else.
 *
 * <p>Bedrock downloads a pack from a link it receives in-game (a CDN link in the resource pack info), much
 * faster than Geyser's chunked transfer. Every link is minted for one Bedrock session: a random 256-bit
 * token, valid for a few minutes, for a few downloads, and by default only from the IP address that session
 * connects from. Requests outside a link, from another address, after expiry or beyond the limits get the
 * same empty 404. The server reads at most one small request per connection with strict timeouts, limits
 * connections globally and per address, bans addresses that keep guessing, and serves immutable snapshots
 * so a rebuilt pack never changes under a running download. When a link cannot be used the client falls
 * back to Geyser's own transfer, so a refused download never stops a player from joining.
 */
public final class PackHost implements AutoCloseable {
    /** Bedrock and Geyser accept only this content type for packs. */
    static final String CONTENT_TYPE = "application/zip";
    static final String PATH_PREFIX = "/twilight/";
    private static final Pattern PATH = Pattern.compile("/twilight/([A-Za-z0-9_-]{43})/([0-9a-f]{16})\\.zip");
    private static final Pattern RANGE = Pattern.compile("bytes=(\\d{1,19})-(\\d{0,19})");
    private static final int MAX_HEADER_BYTES = 8 * 1024;
    private static final int HEADER_TIMEOUT_MILLIS = 5_000;
    /** Requests one connection may make, the wait for the next one, and the wait for the client's close. */
    private static final int REQUESTS_PER_CONNECTION = 8;
    private static final int IDLE_TIMEOUT_MILLIS = 5_000;
    private static final int LINGER_MILLIS = 2_000;
    /**
     * After a pack was sent the connection stays open at least this long plus the pack at {@link #DRAIN_RATE}:
     * security software that scans downloads (and other HTTP proxies on the player's side) can still be passing
     * the pack on to the client, and closing first makes them abort it.
     */
    private static final long DRAIN_BASE_MILLIS = 15_000;
    private static final long DRAIN_RATE = 128 * 1024;
    private static final long DRAIN_MAX_MILLIS = 600_000;
    private static final int MAX_LINKS = 20_000;
    /** Remote addresses (IPv6 by /64) tracked at once; new ones are refused beyond this until the sweep. */
    private static final int MAX_PEERS = 50_000;
    /** Requests per address per minute, failed requests per address per ten minutes before a ban. */
    private static final int REQUESTS_PER_MINUTE = 60;
    private static final int FAILURES_BEFORE_BAN = 20;
    private static final long BAN_MILLIS = TimeUnit.MINUTES.toMillis(15);
    /** Slowest accepted transfer before a download is cut (bytes per second), plus a grace period. */
    private static final long MINIMUM_RATE = 64 * 1024;
    private static final long TRANSFER_GRACE_MILLIS = 30_000;
    private static final DateTimeFormatter HTTP_DATE = DateTimeFormatter.RFC_1123_DATE_TIME;
    /** An address whose client could not use its link gets Geyser's transfer for this long. */
    private static final long NO_HTTP_MILLIS = TimeUnit.MINUTES.toMillis(30);
    /** Sessions that never reached their link before the host looks unreachable from outside. */
    private static final int UNREACHED_BEFORE_WARNING = 5;

    private final HostSettings settings;
    private final Path cache;
    private final Consumer<String> log;
    private final ServerSocket server;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Link> links = new ConcurrentHashMap<>();
    private final Map<String, Snapshot> snapshots = new ConcurrentHashMap<>();
    /** When a link to each snapshot was last made; snapshots unused for longer than a link lives are removed. */
    private final Map<String, Long> lastLinked = new ConcurrentHashMap<>();
    private final Map<InetAddress, Peer> peers = new ConcurrentHashMap<>();
    /** Player addresses whose client fell back to Geyser's transfer -> until when they get no links. */
    private final Map<InetAddress, Long> noHttp = new ConcurrentHashMap<>();
    private final AtomicInteger unreached = new AtomicInteger();
    private volatile long lastDownload;
    private volatile long lastUnreachableWarning;
    private final AtomicInteger connections = new AtomicInteger();
    private final java.util.concurrent.atomic.AtomicLong servedDownloads = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong servedBytes = new java.util.concurrent.atomic.AtomicLong();
    /** Snapshots whose first download was logged. */
    private final java.util.Set<String> announced = ConcurrentHashMap.newKeySet();
    private long lastSummary = System.currentTimeMillis();
    private final ScheduledExecutorService timer;
    private volatile boolean closed;

    /** A pack copy that never changes while it is served; named by its SHA-256. */
    public record Snapshot(Path file, String sha256, long size, long modified) {
        String tag() { return sha256.substring(0, 16); }
    }

    /** One player's link to one snapshot. */
    private static final class Link {
        final Snapshot snapshot;
        final InetAddress player;
        final long expires;
        final AtomicInteger downloads;
        /** A request with this link's token reached the host (from any address). */
        volatile boolean requested;
        /** The last address other than the player's that used the token. */
        volatile InetAddress otherAddress;
        final java.util.concurrent.atomic.AtomicBoolean fellBack = new java.util.concurrent.atomic.AtomicBoolean();

        Link(Snapshot snapshot, InetAddress player, long expires, int downloads) {
            this.snapshot = snapshot;
            this.player = player;
            this.expires = expires;
            this.downloads = new AtomicInteger(downloads);
        }
    }

    /** Connection and request accounting of one remote address. */
    private static final class Peer {
        final AtomicInteger open = new AtomicInteger();
        long windowStart;
        int requests;
        long failureWindowStart;
        int failures;
        long bannedUntil;
    }

    private PackHost(HostSettings settings, Path cache, Consumer<String> log, ServerSocket server) {
        this.settings = settings;
        this.cache = cache;
        this.log = log;
        this.server = server;
        this.timer = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "twilight-pack-host-timer");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Binds the port and starts serving.
     *
     * @param cache folder for pack snapshots (created; old snapshots are removed)
     * @param log   receives one line per notable event (start, bans); never per request
     */
    public static PackHost start(HostSettings settings, Path cache, Consumer<String> log) throws IOException {
        Files.createDirectories(cache);
        try (var old = Files.newDirectoryStream(cache, "*.zip")) {
            for (Path file : old) Files.deleteIfExists(file);
        }
        ServerSocket server = new ServerSocket();
        server.setReuseAddress(true);
        InetSocketAddress bind = settings.bindAddress().isEmpty() ? new InetSocketAddress(settings.port())
                : new InetSocketAddress(InetAddress.getByName(settings.bindAddress()), settings.port());
        try {
            server.bind(bind, 128);
        } catch (IOException failure) {
            server.close();
            throw new IOException("Cannot listen on " + bind + ": " + failure.getMessage(), failure);
        }
        PackHost host = new PackHost(settings, cache, log, server);
        Thread accept = new Thread(host::acceptLoop, "twilight-pack-host");
        accept.setDaemon(true);
        accept.start();
        host.timer.scheduleAtFixedRate(host::sweep, 1, 1, TimeUnit.MINUTES);
        log.accept("Bedrock pack host listening on " + server.getLocalSocketAddress()
                + (settings.requirePlayerAddress() ? " (links only for the player's own address)" : ""));
        return host;
    }

    public int port() {
        return server.getLocalPort();
    }

    public HostSettings settings() {
        return settings;
    }

    /**
     * A snapshot of the pack with this content. The copy is checked against {@code sha256} (the hash Geyser
     * computed for the pack it sends), so a file that changed since then is never offered.
     */
    public Optional<Snapshot> snapshot(Path pack, byte[] sha256) throws IOException {
        String hex = HexFormat.of().formatHex(sha256);
        Snapshot existing = snapshots.get(hex);
        if (existing == null || !Files.isRegularFile(existing.file())) {
            synchronized (snapshots) { // sessions joining together copy a pack once
                existing = snapshots.get(hex);
                if (existing == null || !Files.isRegularFile(existing.file())) existing = copy(pack, hex);
            }
        }
        if (existing == null) return Optional.empty();
        lastLinked.put(hex, System.currentTimeMillis()); // keeps it from the sweep until the link is made
        return Optional.of(existing);
    }

    private Snapshot copy(Path pack, String hex) throws IOException {
        Path target = cache.resolve(hex + ".zip");
        Path temporary = Files.createTempFile(cache, "copy-", ".tmp");
        try {
            Files.copy(pack, temporary, StandardCopyOption.REPLACE_EXISTING);
            if (!hex.equals(sha256(temporary))) return null;
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporary);
        }
        Snapshot snapshot = new Snapshot(target, hex, Files.size(target), System.currentTimeMillis());
        snapshots.put(hex, snapshot);
        return snapshot;
    }

    /**
     * A new link for one Bedrock session.
     *
     * @param player      the address the session connects from (required when links are bound to it)
     * @param joinAddress the host name the player used to join; the link's host when {@code public-address} is auto
     * @return the absolute URL, or empty when no usable host or address is known
     */
    public Optional<String> link(Snapshot snapshot, InetAddress player, String joinAddress) {
        if (closed) return Optional.empty();
        if (settings.requirePlayerAddress() && player == null) return Optional.empty();
        Optional<String> base = settings.baseUrl(joinAddress, port());
        if (base.isEmpty()) return Optional.empty();
        if (links.size() >= MAX_LINKS) sweep();
        if (links.size() >= MAX_LINKS) return Optional.empty();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        lastLinked.put(snapshot.sha256(), System.currentTimeMillis());
        links.put(token, new Link(snapshot, player == null ? null : normalise(player),
                System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(settings.linkMinutes()), settings.downloadsPerLink()));
        return Optional.of(base.get() + PATH_PREFIX + token + "/" + snapshot.tag() + ".zip");
    }

    private void acceptLoop() {
        while (!closed) {
            Socket socket;
            try {
                socket = server.accept();
            } catch (IOException failure) {
                if (closed) return;
                try {
                    Thread.sleep(50); // out of file handles or similar: do not spin
                } catch (InterruptedException interrupted) {
                    return;
                }
                continue;
            }
            InetAddress remote = normalise(socket.getInetAddress());
            InetAddress key = peerKey(remote);
            if (!peers.containsKey(key) && peers.size() >= MAX_PEERS) {
                closeQuietly(socket);
                continue;
            }
            Peer peer = peers.computeIfAbsent(key, ignored -> new Peer());
            if (banned(peer) || connections.get() >= settings.maxConnections()
                    || peer.open.get() >= settings.maxConnectionsPerAddress()) {
                closeQuietly(socket);
                continue;
            }
            connections.incrementAndGet();
            peer.open.incrementAndGet();
            Thread.ofVirtual().name("twilight-pack-host-request").start(() -> {
                try {
                    handle(socket, remote, peer);
                } finally {
                    peer.open.decrementAndGet();
                    connections.decrementAndGet();
                    closeQuietly(socket);
                }
            });
        }
    }

    /**
     * Serves the requests of one connection. The client closes it: a server that closes right after its
     * response lets some network stacks (and HTTP-inspecting security software) reset the connection before
     * the client has read the response, so every response is followed by a wait for the client's next request
     * or close, long enough for a scanner in between to hand a large pack on.
     */
    private void handle(Socket socket, InetAddress remote, Peer peer) {
        try {
            socket.setTcpNoDelay(true);
            InputStream in = new BufferedInputStream(socket.getInputStream(), 1024);
            OutputStream out = new BufferedOutputStream(socket.getOutputStream(), 64 * 1024);
            long sent = 0;
            for (int count = 0; count < REQUESTS_PER_CONNECTION; count++) {
                int wait = count == 0 ? HEADER_TIMEOUT_MILLIS : (int) drainMillis(sent);
                socket.setSoTimeout(wait);
                ScheduledFuture<?> deadline = timer.schedule(() -> closeQuietly(socket), wait, TimeUnit.MILLISECONDS);
                Request request;
                try {
                    request = Request.read(in);
                } catch (IOException silentOrSlow) {
                    // A connection that never sends a whole first request only holds a slot: it counts as a failure.
                    if (count == 0) fail(peer, remote);
                    return;
                } finally {
                    deadline.cancel(false);
                }
                if (request == Request.END) return;
                if (request == null || !rateAllowed(peer)) {
                    fail(peer, remote);
                    return;
                }
                boolean last = count == REQUESTS_PER_CONNECTION - 1 || !request.keepAlive();
                sent = serve(socket, out, request, remote, peer, last);
                if (sent < 0 || last) {
                    linger(socket, in, sent < 0 ? LINGER_MILLIS : drainMillis(sent));
                    return;
                }
            }
        } catch (IOException | RuntimeException failure) {
            // Timeouts, resets and malformed input end the connection; nothing is logged per request.
        }
    }

    /** How long a connection waits for the client after a body of {@code bytes}. */
    static long drainMillis(long bytes) {
        if (bytes <= 0) return IDLE_TIMEOUT_MILLIS;
        return Math.min(DRAIN_MAX_MILLIS, DRAIN_BASE_MILLIS + bytes * 1000 / DRAIN_RATE);
    }

    /**
     * Answers one request; returns the body bytes sent (0 for HEAD), or -1 when the connection must close
     * after it (every refusal closes it).
     */
    private long serve(Socket socket, OutputStream out, Request request, InetAddress remote, Peer peer, boolean last)
            throws IOException {
        InetAddress client = clientAddress(remote, request);
        if (!request.method.equals("GET") && !request.method.equals("HEAD")) {
            // Commit the failure (and any ban) before the client can read the refusal and reconnect.
            fail(peer, remote);
            respond(out, 405, "Allow: GET, HEAD\r\n");
            return -1;
        }
        Matcher path = PATH.matcher(request.target);
        Link link = path.matches() ? links.get(path.group(1)) : null;
        if (link != null) {
            link.requested = true;
            if (link.player != null && !link.player.equals(client)) link.otherAddress = client;
        }
        if (link == null || !path.group(2).equals(link.snapshot.tag()) || link.expires < System.currentTimeMillis()
                || link.player != null && !link.player.equals(client) || !Files.isRegularFile(link.snapshot.file())) {
            fail(peer, remote);
            respond(out, 404, "");
            return -1;
        }
        boolean get = request.method.equals("GET");
        long size = link.snapshot.size();
        long start = 0, end = size - 1;
        int status = 200;
        String range = request.headers.get("range");
        if (range != null) {
            Matcher bytes = RANGE.matcher(range.strip());
            if (bytes.matches() && size > 0) {
                start = Long.parseLong(bytes.group(1));
                end = bytes.group(2).isEmpty() ? size - 1 : Math.min(size - 1, Long.parseLong(bytes.group(2)));
            }
            if (!bytes.matches() || size == 0 || start > end) {
                respond(out, 416, "Content-Range: bytes */" + size + "\r\n");
                return -1;
            }
            status = 206;
        }
        // Only a download that is answered uses up the link; HEAD and refused ranges do not.
        if (get && link.downloads.getAndDecrement() <= 0) {
            fail(peer, remote);
            respond(out, 404, "");
            return -1;
        }
        long length = end - start + 1;
        StringBuilder headers = new StringBuilder();
        headers.append("HTTP/1.1 ").append(status).append(status == 206 ? " Partial Content" : " OK").append("\r\n")
                .append("Content-Type: ").append(CONTENT_TYPE).append("\r\n")
                .append("Content-Length: ").append(length).append("\r\n")
                .append("Accept-Ranges: bytes\r\n")
                .append("ETag: \"").append(link.snapshot.sha256()).append("\"\r\n")
                .append("Last-Modified: ").append(HTTP_DATE.format(ZonedDateTime.ofInstant(
                        java.time.Instant.ofEpochMilli(link.snapshot.modified()), ZoneOffset.UTC))).append("\r\n")
                .append("Cache-Control: private, no-store\r\n")
                .append("X-Content-Type-Options: nosniff\r\n");
        if (status == 206) headers.append("Content-Range: bytes ").append(start).append('-').append(end).append('/').append(size).append("\r\n");
        if (last) headers.append("Connection: close\r\n");
        headers.append("\r\n");
        long budget = TRANSFER_GRACE_MILLIS + (get ? length : 0) * 1000 / MINIMUM_RATE;
        ScheduledFuture<?> deadline = timer.schedule(() -> closeQuietly(socket), budget, TimeUnit.MILLISECONDS);
        try {
            out.write(headers.toString().getBytes(StandardCharsets.US_ASCII));
            if (get) {
                try (FileChannel file = FileChannel.open(link.snapshot.file(), StandardOpenOption.READ)) {
                    java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(64 * 1024);
                    long position = start, remaining = length;
                    while (remaining > 0) {
                        buffer.clear().limit((int) Math.min(buffer.capacity(), remaining));
                        int read = file.read(buffer, position);
                        if (read <= 0) throw new IOException("snapshot shrank");
                        out.write(buffer.array(), 0, read);
                        position += read;
                        remaining -= read;
                    }
                }
            }
            out.flush();
        } finally {
            deadline.cancel(false);
        }
        if (get) served(link.snapshot, length, client);
        return get ? length : 0;
    }

    /** After a final response: waits up to {@code millis} for the client to close (discarding a little input). */
    private static void linger(Socket socket, InputStream in, long millis) {
        long until = System.currentTimeMillis() + millis;
        try {
            byte[] sink = new byte[1024];
            for (int total = 0; total < MAX_HEADER_BYTES; ) {
                long left = until - System.currentTimeMillis();
                if (left <= 0) return;
                socket.setSoTimeout((int) left);
                int read = in.read(sink);
                if (read < 0) return;
                total += read;
            }
        } catch (IOException closedOrTimedOut) {
            // the connection ends either way
        }
    }

    /** The client's address: a trusted reverse proxy's forwarded address, otherwise the connection's. */
    private InetAddress clientAddress(InetAddress remote, Request request) {
        if (!settings.trusted(remote)) return remote;
        String forwarded = request.headers.get("x-forwarded-for");
        if (forwarded == null) return remote;
        String last = forwarded.substring(forwarded.lastIndexOf(',') + 1).strip();
        InetAddress parsed = HostSettings.literal(last);
        return parsed == null ? remote : normalise(parsed);
    }

    private boolean rateAllowed(Peer peer) {
        long now = System.currentTimeMillis();
        synchronized (peer) {
            if (now - peer.windowStart > 60_000) {
                peer.windowStart = now;
                peer.requests = 0;
            }
            return ++peer.requests <= REQUESTS_PER_MINUTE;
        }
    }

    private void fail(Peer peer, InetAddress remote) {
        long now = System.currentTimeMillis();
        synchronized (peer) {
            if (now - peer.failureWindowStart > TimeUnit.MINUTES.toMillis(10)) {
                peer.failureWindowStart = now;
                peer.failures = 0;
            }
            if (++peer.failures >= FAILURES_BEFORE_BAN && peer.bannedUntil < now) {
                peer.bannedUntil = now + BAN_MILLIS;
                log.accept("Bedrock pack host: blocked " + remote.getHostAddress()
                        + (remote instanceof java.net.Inet6Address ? " (its /64 network)" : "") + " for 15 minutes after repeated invalid requests");
            }
        }
    }

    private static boolean banned(Peer peer) {
        synchronized (peer) {
            return peer.bannedUntil > System.currentTimeMillis();
        }
    }

    private static void respond(OutputStream out, int status, String extraHeaders) throws IOException {
        String reason = switch (status) {
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 416 -> "Range Not Satisfiable";
            default -> "Error";
        };
        out.write(("HTTP/1.1 " + status + " " + reason + "\r\n" + extraHeaders
                + "Content-Length: 0\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }

    private void served(Snapshot snapshot, long bytes, InetAddress client) {
        servedBytes.addAndGet(bytes);
        servedDownloads.incrementAndGet();
        lastDownload = System.currentTimeMillis();
        unreached.set(0);
        if (announced.add(snapshot.sha256())) {
            log.accept("Bedrock pack host: first download of pack " + snapshot.tag() + " (" + snapshot.size() / 1024
                    + " KiB) served to " + client.getHostAddress());
        }
    }

    /** Removes expired links, idle peers, and snapshots that no link has used for longer than a link lives. */
    private synchronized void sweep() {
        long now = System.currentTimeMillis();
        if (now - lastSummary >= TimeUnit.MINUTES.toMillis(10)) {
            lastSummary = now;
            long downloads = servedDownloads.getAndSet(0), bytes = servedBytes.getAndSet(0);
            if (downloads > 0) log.accept("Bedrock pack host: " + downloads + " download(s), " + bytes / 1_048_576 + " MiB in the last 10 minutes");
        }
        // Used-up links stay until they expire, so a client that falls back late is still recognised.
        links.values().removeIf(link -> link.expires < now);
        noHttp.values().removeIf(until -> until < now);
        peers.entrySet().removeIf(entry -> {
            Peer peer = entry.getValue();
            synchronized (peer) {
                return peer.open.get() == 0 && peer.bannedUntil < now && now - peer.windowStart > 600_000
                        && now - peer.failureWindowStart > 600_000;
            }
        });
        long keep = TimeUnit.MINUTES.toMillis(settings.linkMinutes() + 1);
        for (Iterator<Snapshot> each = snapshots.values().iterator(); each.hasNext(); ) {
            Snapshot snapshot = each.next();
            long used = Math.max(snapshot.modified(), lastLinked.getOrDefault(snapshot.sha256(), 0L));
            if (now - used < keep || links.values().stream().anyMatch(link -> link.snapshot == snapshot)) continue;
            each.remove();
            lastLinked.remove(snapshot.sha256());
            announced.remove(snapshot.sha256());
            try {
                Files.deleteIfExists(snapshot.file());
            } catch (IOException inUse) {
                // removed on the next start
            }
        }
    }

    /** False while the client at {@code player} gets Geyser's transfer because its last link did not work. */
    public boolean usable(InetAddress player) {
        if (player == null) return true;
        Long until = noHttp.get(normalise(player));
        return until == null || until < System.currentTimeMillis();
    }

    /**
     * The client of {@code url} asked Geyser for the pack instead of downloading it (Geyser reads the pack to
     * send it in chunks). Its address gets no links for a while, so its next joins do not wait for a link it
     * cannot use, and the log says why.
     */
    public void fallback(String url) {
        int at = url.indexOf(PATH_PREFIX);
        if (at < 0) return;
        String rest = url.substring(at + PATH_PREFIX.length());
        int slash = rest.indexOf('/');
        Link link = slash < 0 ? null : links.get(rest.substring(0, slash));
        if (link == null || !link.fellBack.compareAndSet(false, true)) return;
        long now = System.currentTimeMillis();
        if (link.player == null) return;
        InetAddress player = link.player;
        if (noHttp.size() < 50_000) {
            Long previous = noHttp.put(player, now + NO_HTTP_MILLIS);
            if (previous != null && previous > now) return; // already reported
        }
        String reason;
        if (link.otherAddress != null) {
            reason = "its link was used from " + link.otherAddress.getHostAddress() + ", not from the address it plays from; "
                    + "players reach this port through a proxy or NAT that changes their address (set trusted-proxies, "
                    + "or require-player-address: false)";
        } else if (link.requested) {
            reason = "its download did not finish";
        } else {
            reason = "its link never reached the host (port " + port() + " closed or filtered, wrong public-address, "
                    + "or a client that refuses plain HTTP)";
            if (unreached.incrementAndGet() >= UNREACHED_BEFORE_WARNING && now - lastDownload > TimeUnit.MINUTES.toMillis(30)
                    && now - lastUnreachableWarning > TimeUnit.HOURS.toMillis(1)) {
                lastUnreachableWarning = now;
                log.accept("Bedrock pack host: the last " + unreached.get() + " players could not reach their links and none "
                        + "downloaded in 30 minutes. Check that TCP port " + port() + " is open from outside and that "
                        + "public-address is right; until then Geyser sends the packs itself.");
            }
        }
        log.accept("Bedrock pack host: " + player.getHostAddress() + " got its pack from Geyser instead: " + reason
                + ". It gets Geyser's transfer for " + NO_HTTP_MILLIS / 60_000 + " minutes.");
    }

    @Override
    public void close() {
        closed = true;
        closeQuietly(server);
        timer.shutdownNow();
        links.clear();
    }

    /** The address limits and bans apply to: IPv4 addresses as they are, IPv6 addresses by their /64 network. */
    static InetAddress peerKey(InetAddress address) {
        if (!(address instanceof java.net.Inet6Address)) return address;
        byte[] bytes = java.util.Arrays.copyOf(address.getAddress(), 16);
        java.util.Arrays.fill(bytes, 8, 16, (byte) 0);
        try {
            return InetAddress.getByAddress(bytes);
        } catch (IOException impossible) {
            return address;
        }
    }

    public static InetAddress normalise(InetAddress address) {
        if (address instanceof java.net.Inet6Address six) {
            byte[] bytes = six.getAddress();
            boolean mapped = true;
            for (int index = 0; index < 10; index++) mapped &= bytes[index] == 0;
            mapped &= bytes[10] == (byte) 0xff && bytes[11] == (byte) 0xff;
            if (mapped) {
                try {
                    return InetAddress.getByAddress(java.util.Arrays.copyOfRange(bytes, 12, 16));
                } catch (IOException impossible) {
                    return address;
                }
            }
        }
        return address;
    }

    static String sha256(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            for (int read; (read = in.read(buffer)) > 0; ) digest.update(buffer, 0, read);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void closeQuietly(AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (Exception ignored) {
            // already closed
        }
    }

    /** One HTTP/1.x request head: method, target, version and lower-case headers. */
    record Request(String method, String target, String version, Map<String, String> headers) {
        /** The client closed the connection before a new request. */
        static final Request END = new Request("", "", "", Map.of());

        boolean keepAlive() {
            String connection = headers.getOrDefault("connection", "").toLowerCase(Locale.ROOT);
            return version.equals("HTTP/1.1") ? !connection.contains("close") : connection.contains("keep-alive");
        }

        /** The next request head; {@link #END} at a clean end of stream, null when malformed or too long. */
        static Request read(InputStream in) throws IOException {
            byte[] head = new byte[MAX_HEADER_BYTES];
            int length = 0;
            while (true) {
                int next = in.read();
                if (next < 0) return length == 0 ? END : null;
                if (length == head.length) return null;
                head[length++] = (byte) next;
                if (length >= 4 && head[length - 4] == '\r' && head[length - 3] == '\n'
                        && head[length - 2] == '\r' && head[length - 1] == '\n') break;
            }
            String text = new String(head, 0, length - 4, StandardCharsets.ISO_8859_1);
            String[] lines = text.split("\r\n", -1);
            String[] start = lines[0].split(" ", -1);
            if (start.length != 3 || !start[2].matches("HTTP/1\\.[01]") || !start[0].matches("[A-Z]{1,10}")
                    || start[1].isEmpty() || start[1].length() > 512) return null;
            Map<String, String> headers = new java.util.HashMap<>();
            for (int index = 1; index < lines.length; index++) {
                int colon = lines[index].indexOf(':');
                if (colon <= 0) return null;
                String name = lines[index].substring(0, colon).strip().toLowerCase(Locale.ROOT);
                if (headers.size() > 64 || !name.matches("[a-z0-9-]{1,64}")) return null;
                headers.merge(name, lines[index].substring(colon + 1).strip(), (a, b) -> a + ", " + b);
            }
            return new Request(start[0], start[1], start[2], headers);
        }
    }

    /** For tests: the number of links that are still valid. */
    int liveLinks() {
        long now = System.currentTimeMillis();
        return (int) links.values().stream().filter(link -> link.expires >= now && link.downloads.get() > 0).count();
    }

}
