/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.update;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A Twilight version such as {@code 1.0.0}, {@code 1.0.0-beta.1} or a development build
 * {@code 1.0.0-beta.1-SNAPSHOT}, ordered like Semantic Versioning: a prerelease comes before its release
 * and numeric parts compare as numbers. A development build comes before the version it leads to.
 * Release stages follow Twilight's order (alpha, pre, beta, rc) instead of the alphabet, so
 * {@code 1.0.0-beta.1} follows {@code 1.0.0-pre.19}; other labels compare alphabetically after them.
 */
public record Version(int major, int minor, int patch, List<String> pre, boolean snapshot) implements Comparable<Version> {
    private static final Pattern FORMAT = Pattern.compile("v?(\\d{1,9})\\.(\\d{1,9})\\.(\\d{1,9})(?:-([0-9A-Za-z.-]{1,64}))?");
    private static final Pattern NUMBER = Pattern.compile("\\d{1,9}");
    private static final String SNAPSHOT = "SNAPSHOT";
    private static final List<String> STAGES = List.of("alpha", "pre", "beta", "rc");

    public Version {
        pre = List.copyOf(pre);
    }

    public static Optional<Version> parse(String text) {
        if (text == null) return Optional.empty();
        Matcher matcher = FORMAT.matcher(text.strip());
        if (!matcher.matches()) return Optional.empty();
        String suffix = matcher.group(4);
        boolean snapshot = false;
        if (suffix != null && (suffix.equals(SNAPSHOT) || suffix.endsWith("-" + SNAPSHOT))) {
            snapshot = true;
            suffix = suffix.equals(SNAPSHOT) ? null : suffix.substring(0, suffix.length() - SNAPSHOT.length() - 1);
        }
        List<String> pre = suffix == null ? List.of() : List.of(suffix.split("\\.", -1));
        for (String part : pre) {
            if (part.isEmpty()) return Optional.empty();
        }
        return Optional.of(new Version(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3)), pre, snapshot));
    }

    /** A prerelease or development build. */
    public boolean prerelease() {
        return snapshot || !pre.isEmpty();
    }

    @Override
    public int compareTo(Version other) {
        int result = Integer.compare(major, other.major);
        if (result == 0) result = Integer.compare(minor, other.minor);
        if (result == 0) result = Integer.compare(patch, other.patch);
        if (result == 0) result = comparePre(pre, other.pre);
        if (result == 0) result = Boolean.compare(!snapshot, !other.snapshot);
        return result;
    }

    private static int comparePre(List<String> left, List<String> right) {
        if (left.isEmpty() || right.isEmpty()) return Boolean.compare(left.isEmpty(), right.isEmpty());
        for (int index = 0; index < Math.min(left.size(), right.size()); index++) {
            String a = left.get(index);
            String b = right.get(index);
            boolean numericA = NUMBER.matcher(a).matches();
            boolean numericB = NUMBER.matcher(b).matches();
            int result = numericA && numericB ? Integer.compare(Integer.parseInt(a), Integer.parseInt(b))
                    : numericA != numericB ? (numericA ? -1 : 1)
                    : compareLabels(a, b);
            if (result != 0) return result;
        }
        return Integer.compare(left.size(), right.size());
    }

    private static int compareLabels(String a, String b) {
        int stageA = STAGES.indexOf(a.toLowerCase(java.util.Locale.ROOT));
        int stageB = STAGES.indexOf(b.toLowerCase(java.util.Locale.ROOT));
        if (stageA >= 0 && stageB >= 0) return Integer.compare(stageA, stageB);
        if (stageA >= 0 || stageB >= 0) return stageA >= 0 ? -1 : 1;
        return a.compareTo(b);
    }

    @Override
    public String toString() {
        StringBuilder text = new StringBuilder().append(major).append('.').append(minor).append('.').append(patch);
        if (!pre.isEmpty()) text.append('-').append(String.join(".", pre));
        if (snapshot) text.append('-').append(SNAPSHOT);
        return text.toString();
    }
}
