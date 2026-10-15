/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.proxy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;

/**
 * Backends that run on the same machine as the proxy. Their Twilight writes every build to
 * {@code plugins/Twilight/export/Twilight.mcpack}; twilight-proxy reads it from there directly, without waiting
 * for a player to join that server, so the proxy's Geyser gets every server's current items at start and a new
 * build within seconds.
 *
 * <p>A backend is found by its folder: a server folder next to the proxy's (or under a configured search
 * folder) whose {@code server.properties} uses the port of a proxy server with a local address, and which has
 * Twilight installed. Only that one file path inside such a folder is ever read; symbolic links are refused.
 */
final class LocalBackends {
    static final String EXPORT = "plugins/Twilight/export/Twilight.mcpack";
    private static final int MAX_FOLDERS = 500;
    private static final long MAX_PROPERTIES = 64 * 1024;

    /** A backend's folder and its exported pack. */
    record Backend(String server, Path folder, Path export) {}

    private LocalBackends() {}

    /**
     * The backends of {@code servers} (name -> address) found on this machine. {@code explicit} maps servers to
     * their folders and wins over discovery; {@code roots} are searched one level deep.
     */
    static Map<String, Backend> discover(Map<String, InetSocketAddress> servers, List<Path> roots, Map<String, Path> explicit,
                                         Consumer<String> log) {
        Map<String, Backend> found = new LinkedHashMap<>();
        List<Candidate> candidates = null;
        for (var server : servers.entrySet()) {
            String name = server.getKey().toLowerCase(Locale.ROOT);
            Path folder = explicit.get(name);
            if (folder != null) {
                if (twilightInstalled(folder)) found.put(name, backend(name, folder));
                else log.accept("local-backends: " + folder + " (set for " + name + ") has no plugins/Twilight; not used.");
                continue;
            }
            if (!local(server.getValue())) continue;
            if (candidates == null) candidates = candidates(roots);
            int port = server.getValue().getPort();
            List<Candidate> matching = candidates.stream().filter(candidate -> candidate.port == port).toList();
            if (matching.isEmpty()) continue;
            Candidate chosen = matching.size() == 1 ? matching.getFirst()
                    : matching.stream().max(Comparator.comparing(Candidate::activity)).orElseThrow();
            if (matching.size() > 1) {
                log.accept("local-backends: " + matching.size() + " server folders use port " + port + " ("
                        + String.join(", ", matching.stream().map(candidate -> candidate.folder.getFileName().toString()).toList())
                        + "); using " + chosen.folder.getFileName() + ", the most recently active. Set it under local-backends.server"
                        + " if that is wrong.");
            }
            found.put(name, backend(name, chosen.folder));
        }
        return found;
    }

    private static Backend backend(String server, Path folder) {
        return new Backend(server, folder, folder.resolve(EXPORT));
    }

    private record Candidate(Path folder, int port, FileTime activity) {}

    /** Server folders with Twilight installed, one level under each root. */
    private static List<Candidate> candidates(List<Path> roots) {
        List<Candidate> candidates = new ArrayList<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) continue;
            try (var folders = Files.list(root)) {
                for (Path folder : folders.filter(Files::isDirectory).sorted().limit(MAX_FOLDERS).toList()) {
                    if (Files.isSymbolicLink(folder) || !twilightInstalled(folder)) continue;
                    int port = port(folder.resolve("server.properties"));
                    if (port > 0) candidates.add(new Candidate(folder, port, activity(folder)));
                }
            } catch (IOException | SecurityException unreadable) {
                // A folder this process may not list: nothing to find there.
            }
        }
        return candidates;
    }

    static boolean twilightInstalled(Path folder) {
        Path twilight = folder.resolve("plugins/Twilight");
        return Files.isDirectory(twilight) && !Files.isSymbolicLink(twilight);
    }

    /** The server's port, or -1 when the file is missing or unreadable. */
    static int port(Path properties) {
        try {
            if (!Files.isRegularFile(properties) || Files.isSymbolicLink(properties) || Files.size(properties) > MAX_PROPERTIES) return -1;
            Properties values = new Properties();
            try (var reader = Files.newBufferedReader(properties, StandardCharsets.ISO_8859_1)) {
                values.load(reader);
            }
            int port = Integer.parseInt(values.getProperty("server-port", "25565").strip());
            return port > 0 && port <= 65_535 ? port : -1;
        } catch (IOException | RuntimeException unreadable) {
            return -1;
        }
    }

    /** The newest of the server's log and Twilight export: a running server writes both. */
    private static FileTime activity(Path folder) {
        FileTime newest = FileTime.fromMillis(0);
        for (String file : List.of("logs/latest.log", EXPORT)) {
            try {
                FileTime time = Files.getLastModifiedTime(folder.resolve(file));
                if (time.compareTo(newest) > 0) newest = time;
            } catch (IOException | SecurityException missing) {
                // not there
            }
        }
        return newest;
    }

    /** True when {@code address} is this machine (loopback, any of its interfaces, or "localhost"). */
    static boolean local(InetSocketAddress address) {
        if (address == null) return false;
        if ("localhost".equalsIgnoreCase(address.getHostString())) return true;
        InetAddress ip = address.getAddress();
        if (ip == null) return false;
        if (ip.isLoopbackAddress() || ip.isAnyLocalAddress()) return true;
        try {
            return NetworkInterface.getByInetAddress(ip) != null;
        } catch (SocketException unknown) {
            return false;
        }
    }
}
