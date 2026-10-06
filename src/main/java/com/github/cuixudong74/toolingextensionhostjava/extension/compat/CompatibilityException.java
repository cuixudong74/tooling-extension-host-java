package com.github.cuixudong74.toolingextensionhostjava.extension.compat;

import java.util.List;

/**
 * REJECT 策略下兼容性检查失败抛出的异常。
 */
public class CompatibilityException extends RuntimeException {
    private final List<CompatibilityIssue> issues;

    public CompatibilityException(List<CompatibilityIssue> issues) {
        super(issues.toString());
        this.issues = List.copyOf(issues);
    }

    public List<CompatibilityIssue> getIssues() { return issues; }
}
