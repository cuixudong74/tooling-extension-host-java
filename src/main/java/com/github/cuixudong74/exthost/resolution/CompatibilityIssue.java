package com.github.cuixudong74.exthost.resolution;

/**
 * 兼容性检查结果。
 *
 * @param compatible 是否完全兼容
 * @param reason     不兼容原因（兼容时为 null），可解释
 */
public record CompatibilityIssue(boolean compatible, String reason) {
    public static CompatibilityIssue ok() {
        return new CompatibilityIssue(true, null);
    }

    public static CompatibilityIssue incompatible(String reason) {
        return new CompatibilityIssue(false, reason);
    }
}
