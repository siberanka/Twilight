package com.siberanka.twilight.update;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class UpdateCheckTest {
    private static final String GITHUB_LIST = """
            [{"url":"https://api.github.com/repos/siberanka/twilight/releases/1","tag_name":"v1.0.0-pre.14","draft":false,
              "prerelease":true,"body":"Notes with \\"tag_name\\": \\"v9.9.9\\" inside."},
             {"tag_name":"v1.0.0-pre.13","prerelease":true},
             {"tag_name": "v1.0.0-pre.9", "prerelease": true},
             {"tag_name":"nightly-<script>","prerelease":true}]
            """;

    private static Version version(String text) {
        return Version.parse(text).orElseThrow();
    }

    @Test
    void ordersVersionsLikeSemanticVersioning() {
        List<String> ordered = List.of("0.9.9", "1.0.0-alpha", "1.0.0-pre.2", "1.0.0-pre.9", "1.0.0-pre.10",
                "1.0.0-pre.14-SNAPSHOT", "1.0.0-pre.14", "1.0.0-pre.19", "1.0.0-beta.1-SNAPSHOT", "1.0.0-beta.1",
                "1.0.0-beta.2", "1.0.0-beta.10", "1.0.0-rc.1", "1.0.0-SNAPSHOT", "1.0.0", "1.0.1", "1.10.0", "2.0.0");
        for (int index = 1; index < ordered.size(); index++) {
            Version lower = version(ordered.get(index - 1));
            Version higher = version(ordered.get(index));
            assertTrue(lower.compareTo(higher) < 0, lower + " < " + higher);
            assertTrue(higher.compareTo(lower) > 0, higher + " > " + lower);
        }
        assertEquals(0, version("v1.0.0-pre.13").compareTo(version("1.0.0-pre.13")));
        assertEquals("1.0.0-pre.14-SNAPSHOT", version("1.0.0-pre.14-SNAPSHOT").toString());
        assertTrue(version("1.0.0-pre.1").prerelease());
        assertFalse(version("1.0.0").prerelease());
        for (String invalid : List.of("", "1.0", "1.0.0-", "1.0.0-pre..1", "1.0.0 beta", "x1.0.0", "1.0.0-§c", "unknown")) {
            assertTrue(Version.parse(invalid).isEmpty(), invalid);
        }
    }

    @Test
    void findsTheNewestReleaseOnGitHub() {
        var outcome = UpdateCheck.check(version("1.0.0-pre.13"), host -> {
            assertEquals("GitHub", host.name());
            return GITHUB_LIST;
        });
        var release = assertInstanceOf(UpdateCheck.Outcome.Newer.class, outcome).release();
        assertEquals("1.0.0-pre.14", release.version().toString());
        assertEquals("https://github.com/siberanka/twilight/releases/tag/v1.0.0-pre.14", release.page());

        assertInstanceOf(UpdateCheck.Outcome.Current.class, UpdateCheck.check(version("1.0.0-pre.14"), host -> GITHUB_LIST));
        assertInstanceOf(UpdateCheck.Outcome.Current.class, UpdateCheck.check(version("1.0.0-pre.15-SNAPSHOT"), host -> GITHUB_LIST));
    }

    /** Servers on the last preview hear about the first beta, although "beta" sorts before "pre" alphabetically. */
    @Test
    void previewsHearAboutTheFirstBeta() {
        String list = "[{\"tag_name\":\"v1.0.0-beta.1\",\"prerelease\":true},{\"tag_name\":\"v1.0.0-pre.19\",\"prerelease\":true}]";
        var outcome = UpdateCheck.check(version("1.0.0-pre.19"), host -> list);
        assertEquals("1.0.0-beta.1", assertInstanceOf(UpdateCheck.Outcome.Newer.class, outcome).release().version().toString());
        assertInstanceOf(UpdateCheck.Outcome.Current.class, UpdateCheck.check(version("1.0.0-beta.1"), host -> list));
    }

    @Test
    void releasesHearOnlyAboutReleases() {
        String list = "[{\"tag_name\":\"v1.1.0-pre.1\"},{\"tag_name\":\"v1.0.1\"},{\"tag_name\":\"v1.0.0\"}]";
        var stable = UpdateCheck.check(version("1.0.0"), host -> list);
        assertEquals("1.0.1", assertInstanceOf(UpdateCheck.Outcome.Newer.class, stable).release().version().toString());
        var testing = UpdateCheck.check(version("1.0.1-pre.3"), host -> list);
        assertEquals("1.1.0-pre.1", assertInstanceOf(UpdateCheck.Outcome.Newer.class, testing).release().version().toString());
        assertInstanceOf(UpdateCheck.Outcome.Current.class, UpdateCheck.check(version("1.0.1"), host -> list));
    }

    @Test
    void fallsBackToGitLabWhenGitHubFails() {
        List<String> asked = new ArrayList<>();
        var outcome = UpdateCheck.check(version("1.0.0-pre.13"), host -> {
            asked.add(host.name());
            if (host.name().equals("GitHub")) throw new IOException("HTTP 403");
            return "[{\"name\":\"1.0.0-pre.14\",\"tag_name\":\"v1.0.0-pre.14\",\"upcoming_release\":false}]";
        });
        assertEquals(List.of("GitHub", "GitLab"), asked);
        var release = assertInstanceOf(UpdateCheck.Outcome.Newer.class, outcome).release();
        assertEquals("GitLab", release.host());
        assertEquals("https://gitlab.com/siberanka/twilight/-/releases/v1.0.0-pre.14", release.page());

        // An answer without releases (a captive portal, an error page) is not trusted either.
        var portal = UpdateCheck.check(version("1.0.0-pre.13"), host -> host.name().equals("GitHub") ? "<html>login</html>" : GITHUB_LIST);
        assertEquals("GitLab", assertInstanceOf(UpdateCheck.Outcome.Newer.class, portal).release().host());

        var failed = UpdateCheck.check(version("1.0.0-pre.13"), host -> {
            throw host.name().equals("GitHub") ? new java.net.http.HttpConnectTimeoutException("HTTP connect timed out")
                    : new javax.net.ssl.SSLHandshakeException("PKIX path building failed: " + "x".repeat(300));
        });
        assertEquals("GitHub: timed out, GitLab: its certificate is not trusted by this Java (TLS inspection?)",
                assertInstanceOf(UpdateCheck.Outcome.Failed.class, failed).reason());
        assertEquals("no connection", UpdateCheck.reason(new java.net.ConnectException()));
        assertEquals(123, UpdateCheck.reason(new IOException("y".repeat(500))).length());
    }

    @Test
    void announcesEachNewVersionOnceAndFailuresOnce() {
        List<String> log = new ArrayList<>();
        List<UpdateCheck.Release> told = new ArrayList<>();
        String[] answer = {GITHUB_LIST};
        boolean[] down = {false};
        UpdateCheck check = UpdateCheck.manual("Twilight", version("1.0.0-pre.13"), host -> {
            if (down[0]) throw new IOException("unreachable");
            return answer[0];
        }, log::add, told::add);
        assertEquals("update check pending", check.status());

        check.run();
        check.run();
        assertEquals(1, told.size());
        assertEquals(List.of("Twilight 1.0.0-pre.14 is available (this server runs 1.0.0-pre.13): "
                + "https://github.com/siberanka/twilight/releases/tag/v1.0.0-pre.14"), log);

        down[0] = true;
        check.run();
        check.run();
        assertEquals(2, log.size());
        assertTrue(log.get(1).startsWith("Could not check for Twilight updates (GitHub: unreachable, GitLab: unreachable)"));
        assertEquals(Optional.of("1.0.0-pre.14"), check.latest().map(release -> release.version().toString()));

        down[0] = false;
        answer[0] = "[{\"tag_name\":\"v1.0.0-pre.15\"}," + GITHUB_LIST.substring(1);
        check.run();
        assertEquals(2, told.size());
        assertEquals("update 1.0.0-pre.15 available: https://github.com/siberanka/twilight/releases/tag/v1.0.0-pre.15", check.status());
        check.close();
    }

    @Test
    void localBuildsDoNotCheck() {
        List<String> log = new ArrayList<>();
        assertTrue(UpdateCheck.start("Twilight", "dev", log::add, release -> { }).isEmpty());
        assertEquals(List.of("Update check is off: version 'dev' is not a release version."), log);
    }
}
