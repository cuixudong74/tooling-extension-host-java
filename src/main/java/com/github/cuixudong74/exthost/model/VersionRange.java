package com.github.cuixudong74.exthost.model;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 语义化版本范围，支持：
 * <ul>
 *   <li>"1.2.3" 精确版本</li>
 *   <li>"[1.0,2.0)" / "(1.0,2.0]" / "[1.0,]" / "(,2.0)" 区间</li>
 * </ul>
 * 版本号允许 1~3 段数字（缺省段按 0 计），可带预发布后缀（按字典序参与比较，低于无后缀版本）。
 */
public final class VersionRange {

    private static final Pattern VERSION =
            Pattern.compile("^(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?(?:-([0-9A-Za-z.-]+))?$");
    private static final Pattern RANGE =
            Pattern.compile("^([\\[(])\\s*([^,]*)\\s*,\\s*([^)\\]]*)\\s*([)\\]])$");

    private final Version lower;
    private final boolean lowerInclusive;
    private final Version upper;
    private final boolean upperInclusive;

    private VersionRange(Version lower, boolean lowerInclusive, Version upper, boolean upperInclusive) {
        this.lower = lower;
        this.lowerInclusive = lowerInclusive;
        this.upper = upper;
        this.upperInclusive = upperInclusive;
    }

    /** 解析范围表达式，非法时抛 IllegalArgumentException。 */
    public static VersionRange parse(String expr) {
        if (expr == null || expr.isBlank()) {
            throw new IllegalArgumentException("version range expression is blank");
        }
        String trimmed = expr.trim();
        Matcher rm = RANGE.matcher(trimmed);
        if (rm.matches()) {
            Version lower = rm.group(2).isBlank() ? null : Version.parse(rm.group(2).trim());
            Version upper = rm.group(3).isBlank() ? null : Version.parse(rm.group(3).trim());
            if (lower == null && upper == null) {
                throw new IllegalArgumentException("range must have at least one bound: " + expr);
            }
            if (lower != null && upper != null && lower.compareTo(upper) > 0) {
                throw new IllegalArgumentException("lower bound exceeds upper bound: " + expr);
            }
            return new VersionRange(lower, "[".equals(rm.group(1)), upper, "]".equals(rm.group(4)));
        }
        // 精确版本
        Version exact = Version.parse(trimmed);
        return new VersionRange(exact, true, exact, true);
    }

    /** 判断给定版本是否落在范围内。 */
    public boolean contains(String version) {
        Objects.requireNonNull(version, "version");
        Version v = Version.parse(version.trim());
        if (lower != null) {
            int c = v.compareTo(lower);
            if (c < 0 || (c == 0 && !lowerInclusive)) {
                return false;
            }
        }
        if (upper != null) {
            int c = v.compareTo(upper);
            if (c > 0 || (c == 0 && !upperInclusive)) {
                return false;
            }
        }
        return true;
    }

    /** 校验版本号字符串是否合法。 */
    public static boolean isValidVersion(String version) {
        return version != null && VERSION.matcher(version.trim()).matches();
    }

    /** 语义化版本（可比较）。 */
    static final class Version implements Comparable<Version> {
        private final long major;
        private final long minor;
        private final long patch;
        private final String preRelease;

        private Version(long major, long minor, long patch, String preRelease) {
            this.major = major;
            this.minor = minor;
            this.patch = patch;
            this.preRelease = preRelease;
        }

        static Version parse(String s) {
            Matcher m = VERSION.matcher(s);
            if (!m.matches()) {
                throw new IllegalArgumentException("invalid version: " + s);
            }
            return new Version(
                    Long.parseLong(m.group(1)),
                    m.group(2) == null ? 0 : Long.parseLong(m.group(2)),
                    m.group(3) == null ? 0 : Long.parseLong(m.group(3)),
                    m.group(4));
        }

        @Override
        public int compareTo(Version o) {
            int c = Long.compare(major, o.major);
            if (c != 0) return c;
            c = Long.compare(minor, o.minor);
            if (c != 0) return c;
            c = Long.compare(patch, o.patch);
            if (c != 0) return c;
            // 无预发布后缀 > 有预发布后缀
            if (preRelease == null && o.preRelease == null) return 0;
            if (preRelease == null) return 1;
            if (o.preRelease == null) return -1;
            return preRelease.compareTo(o.preRelease);
        }

        @Override
        public String toString() {
            return major + "." + minor + "." + patch + (preRelease == null ? "" : "-" + preRelease);
        }
    }
}
