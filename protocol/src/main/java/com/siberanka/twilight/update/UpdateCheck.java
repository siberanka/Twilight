/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.update;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Looks for a newer Twilight release: on GitHub, where the project is published, and on its GitLab mirror
 * when GitHub cannot be reached or refuses (rate limit). Only the public list of releases is read over
 * HTTPS; nothing about the server is sent and nothing is downloaded or installed. A server running a
 * prerelease hears about newer prereleases too, one running a release only about releases.
 */
public final class UpdateCheck implements AutoCloseable {
    /** A place the release list is read from; {@code page} + tag is the release's page. */
    public record Host(String name, URI releases, String page) {}

    public static final List<Host> HOSTS = List.of(
            new Host("GitHub", URI.create("https://api.github.com/repos/siberanka/twilight/releases?per_page=10"),
                    "https://github.com/siberanka/twilight/releases/tag/"),
            new Host("GitLab", URI.create("https://gitlab.com/api/v4/projects/siberanka%2Ftwilight/releases?per_page=10"),
                    "https://gitlab.com/siberanka/twilight/-/releases/"));

    /** A release newer than the running version, with its page on the host it was found on. */
    public record Release(Version version, String page, String host) {}

    /** The result of one check. */
    public sealed interface Outcome {
        record Current(String host) implements Outcome {}

        record Newer(Release release) implements Outcome {}

        record Failed(String reason) implements Outcome {}
    }

    /** Reads a host's release list. */
    @FunctionalInterface
    public interface Fetcher {
        String get(Host host) throws IOException, InterruptedException;
    }

    static final long FIRST_DELAY_SECONDS = 20;
    static final long PERIOD_HOURS = 6;
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    /** Only tags in the project's version format are read; nothing else from the response is used. */
    private static final Pattern TAG = Pattern.compile("\"tag_name\"\\s*:\\s*\"(v?[0-9]{1,9}\\.[0-9]{1,9}\\.[0-9]{1,9}(?:-[0-9A-Za-z.-]{1,64})?)\"");

    private final String product;
    private final Version running;
    private final Fetcher fetcher;
    private final Consumer<String> log;
    private final Consumer<Release> newer;
    private final HttpClient client;
    private final ScheduledExecutorService timer;
    private final AtomicReference<Outcome> last = new AtomicReference<>();
    private final AtomicReference<Release> latest = new AtomicReference<>();

    private UpdateCheck(String product, Version running, Fetcher fetcher, HttpClient client,
                        Consumer<String> log, Consumer<Release> newer) {
        this.product = product;
        this.running = running;
        this.fetcher = fetcher;
        this.client = client;
        this.log = log;
        this.newer = newer;
        this.timer = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, product + "-UpdateCheck");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Checks shortly after start and then every six hours, off the server threads. {@code log} gets the
     * lines for the console, {@code newer} each newer release once. Returns empty when the running version
     * is not a release version (a local build), which is reported through {@code log}.
     */
    public static Optional<UpdateCheck> start(String product, String runningVersion, Consumer<String> log,
                                              Consumer<Release> newer) {
        Optional<Version> running = Version.parse(runningVersion);
        if (running.isEmpty()) {
            log.accept("Update check is off: version '" + runningVersion + "' is not a release version.");
            return Optional.empty();
        }
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        String agent = product + "/" + runningVersion + " update-check (+https://github.com/siberanka/twilight)";
        UpdateCheck check = new UpdateCheck(product, running.get(), host -> fetch(client, host, agent), client, log, newer);
        check.timer.scheduleWithFixedDelay(check::run, FIRST_DELAY_SECONDS, TimeUnit.HOURS.toSeconds(PERIOD_HOURS), TimeUnit.SECONDS);
        return Optional.of(check);
    }

    /** For tests: a check that reads release lists through {@code fetcher} and is never scheduled. */
    static UpdateCheck manual(String product, Version running, Fetcher fetcher, Consumer<String> log, Consumer<Release> newer) {
        return new UpdateCheck(product, running, fetcher, null, log, newer);
    }

    /** One check; reports a newer release once and a failure once until a check works again. */
    void run() {
        Outcome outcome;
        try {
            outcome = check(running, fetcher);
        } catch (RuntimeException failure) {
            outcome = new Outcome.Failed(failure.getClass().getSimpleName());
        }
        Outcome previous = last.getAndSet(outcome);
        if (outcome instanceof Outcome.Newer found) {
            Release known = latest.get();
            if (known == null || found.release().version().compareTo(known.version()) > 0) {
                latest.set(found.release());
                log.accept(announcement(found.release()));
                newer.accept(found.release());
            }
        } else if (outcome instanceof Outcome.Failed failed && !(previous instanceof Outcome.Failed)) {
            log.accept("Could not check for " + product + " updates (" + failed.reason() + "); trying again in "
                    + PERIOD_HOURS + " hours.");
        }
    }

    /** Asks GitHub first and GitLab when GitHub fails. */
    static Outcome check(Version running, Fetcher fetcher) {
        List<String> failures = new ArrayList<>();
        for (Host host : HOSTS) {
            try {
                String body = fetcher.get(host);
                Optional<Optional<Release>> found = newest(body, running, host);
                if (found.isEmpty()) {
                    failures.add(host.name() + ": no releases in the answer");
                    continue;
                }
                return found.get().<Outcome>map(Outcome.Newer::new).orElseGet(() -> new Outcome.Current(host.name()));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return new Outcome.Failed("interrupted");
            } catch (IOException failure) {
                failures.add(host.name() + ": " + reason(failure));
            }
        }
        return new Outcome.Failed(String.join(", ", failures));
    }

    /** A short reason for the console: blocked outgoing connections and TLS inspection are the usual causes. */
    static String reason(IOException failure) {
        if (failure instanceof java.net.http.HttpTimeoutException) return "timed out";
        if (failure instanceof java.net.ConnectException) return "no connection";
        if (failure instanceof javax.net.ssl.SSLException) {
            String message = String.valueOf(failure.getMessage());
            return message.contains("PKIX") ? "its certificate is not trusted by this Java (TLS inspection?)" : "TLS failed";
        }
        String message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        return message.length() > 120 ? message.substring(0, 120) + "..." : message;
    }

    /**
     * The newest release in a release list that is newer than {@code running} (inner value), or empty when
     * the list holds no release at all. Prereleases count only when a prerelease is running.
     */
    static Optional<Optional<Release>> newest(String body, Version running, Host host) {
        Matcher matcher = TAG.matcher(body);
        boolean any = false;
        Version best = null;
        String bestTag = null;
        while (matcher.find()) {
            Optional<Version> parsed = Version.parse(matcher.group(1));
            if (parsed.isEmpty()) continue;
            any = true;
            Version version = parsed.get();
            if (version.snapshot() || (version.prerelease() && !running.prerelease())) continue;
            if (best == null || version.compareTo(best) > 0) {
                best = version;
                bestTag = matcher.group(1);
            }
        }
        if (!any) return Optional.empty();
        if (best == null || best.compareTo(running) <= 0) return Optional.of(Optional.empty());
        return Optional.of(Optional.of(new Release(best, host.page() + bestTag, host.name())));
    }

    private static String fetch(HttpClient client, Host host, String agent) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(host.releases())
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .header("User-Agent", agent)
                .GET()
                .build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
            if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode());
            byte[] bytes = body.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) throw new IOException("the release list is larger than expected");
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    /** The console line for a newer release. */
    public String announcement(Release release) {
        return product + " " + release.version() + " is available (this server runs " + running + "): " + release.page();
    }

    /** The newest release found so far that is newer than the running version. */
    public Optional<Release> latest() {
        return Optional.ofNullable(latest.get());
    }

    public String running() {
        return running.toString();
    }

    /** One line for status commands. */
    public String status() {
        Release found = latest.get();
        if (found != null) return "update " + found.version() + " available: " + found.page();
        Outcome outcome = last.get();
        if (outcome == null) return "update check pending";
        if (outcome instanceof Outcome.Current current) return "up to date (checked on " + current.host() + ")";
        return "update check failed (" + ((Outcome.Failed) outcome).reason() + ")";
    }

    @Override
    public void close() {
        timer.shutdownNow();
        if (client != null) client.shutdownNow();
    }
}
