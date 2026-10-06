package com.github.cuixudong74.toolingextensionhostjava.extension.version;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 语义化版本范围。支持表达式：
 * <ul>
 *   <li>精确版本："1.2.3"（缺省段补 0）</li>
 *   <li>区间："[1.0,2.0)"、"(1.0,2.0]"、"[1.0,]"</li>
 *   <li>脱字符："^1.2" 等价于 [1.2.0, 2.0.0)</li>
 *   <li>波浪号："~1.2" 等价于 [1.2.0, 1.3.0)</li>
 *   <li>比较运算组合："&gt;=1.0 &lt;2.0"</li>
 * </ul>
 */
public final class VersionRange {

    private static final Pattern EXACT = Pattern.compile("^\\d+(\\.\\d+){0,2}$");
    private static final Pattern INTERVAL = Pattern.compile(
            "^([\\[(])\\s*(\\d+(?:\\.\\d+){0,2})?\\s*,\\s*(\\d+(?:\\.\\d+){0,2})?\\s*([\\])])$");
    private static final Pattern CARET = Pattern.compile("^\\^(\\d+(?:\\.\\d+){0,2})$");
    private static final Pattern TILDE = Pattern.compile("^~(\\d+(?:\\.\\d+){0,2})$");
    private static final Pattern CMP = Pattern.compile("(>=|<=|>|<|=)\\s*(\\d+(?:\\.\\d+){0,2})");

    private final String expression;
    private final List<long[]> lowerBounds = new ArrayList<>(); // [ver..., inclusive]
    private final List<long[]> upperBounds = new ArrayList<>();

    private VersionRange(String expression) {
        this.expression = expression;
    }

    public static VersionRange parse(String expr) {
        if (expr == null || expr.isBlank()) {
            throw new IllegalArgumentException("版本范围表达式为空");
        }
        String e = expr.trim();
        VersionRange range = new VersionRange(e);

        Matcher m;
        if (EXACT.matcher(e).matches()) {
            long[] v = parseVersion(e);
            range.lowerBounds.add(bound(v, true));
            range.upperBounds.add(bound(v, true));
        } else if ((m = INTERVAL.matcher(e)).matches()) {
            boolean lowerInc = m.group(1).equals("[");
            boolean upperInc = m.group(4).equals("]");
            if (m.group(2) != null) {
                range.lowerBounds.add(bound(parseVersion(m.group(2)), lowerInc));
            }
            if (m.group(3) != null) {
                range.upperBounds.add(bound(parseVersion(m.group(3)), upperInc));
            }
        } else if ((m = CARET.matcher(e)).matches()) {
            long[] v = parseVersion(m.group(1));
            range.lowerBounds.add(bound(v, true));
            long[] upper = {v[0] + 1, 0, 0};
            range.upperBounds.add(bound(upper, false));
        } else if ((m = TILDE.matcher(e)).matches()) {
            long[] v = parseVersion(m.group(1));
            range.lowerBounds.add(bound(v, true));
            long[] upper = {v[0], v[1] + 1, 0};
            range.upperBounds.add(bound(upper, false));
        } else {
            Matcher cm = CMP.matcher(e);
            boolean any = false;
            while (cm.find()) {
                any = true;
                long[] v = parseVersion(cm.group(2));
                switch (cm.group(1)) {
                    case ">=" -> range.lowerBounds.add(bound(v, true));
                    case ">" -> range.lowerBounds.add(bound(v, false));
                    case "<=" -> range.upperBounds.add(bound(v, true));
                    case "<" -> range.upperBounds.add(bound(v, false));
                    case "=" -> {
                        range.lowerBounds.add(bound(v, true));
                        range.upperBounds.add(bound(v, true));
                    }
                    default -> throw new IllegalArgumentException("不支持的比较符: " + cm.group(1));
                }
            }
            if (!any) {
                throw new IllegalArgumentException("无法解析的版本范围表达式: " + expr);
            }
        }
        return range;
    }

    /** 判断给定版本字符串是否落在范围内。 */
    public boolean contains(String version) {
        Objects.requireNonNull(version, "version");
        long[] v;
        try {
            v = parseVersion(version.trim());
        } catch (IllegalArgumentException ex) {
            return false;
        }
        for (long[] lb : lowerBounds) {
            int cmp = compare(v, lb);
            if (cmp < 0 || (cmp == 0 && lb[3] == 0)) {
                return false;
            }
        }
        for (long[] ub : upperBounds) {
            int cmp = compare(v, ub);
            if (cmp > 0 || (cmp == 0 && ub[3] == 0)) {
                return false;
            }
        }
        return true;
    }

    /** 校验版本号字符串本身是否合法。 */
    public static boolean isValidVersion(String version) {
        return version != null && EXACT.matcher(version.trim()).matches();
    }

    private static long[] bound(long[] v, boolean inclusive) {
        return new long[]{v[0], v[1], v[2], inclusive ? 1 : 0};
    }

    private static long[] parseVersion(String s) {
        String t = s.trim();
        if (!EXACT.matcher(t).matches()) {
            throw new IllegalArgumentException("非法版本号: " + s);
        }
        String[] parts = t.split("\\.");
        long[] v = new long[3];
        for (int i = 0; i < parts.length; i++) {
            v[i] = Long.parseLong(parts[i]);
        }
        return v;
    }

    private static int compare(long[] a, long[] b) {
        for (int i = 0; i < 3; i++) {
            if (a[i] != b[i]) {
                return a[i] < b[i] ? -1 : 1;
            }
        }
        return 0;
    }

    @Override
    public String toString() {
        return expression;
    }
}
