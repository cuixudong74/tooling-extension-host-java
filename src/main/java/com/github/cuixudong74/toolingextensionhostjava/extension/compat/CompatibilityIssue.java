package com.github.cuixudong74.toolingextensionhostjava.extension.compat;

/**
 * 一条兼容性问题，可定位到扩展与原因。
 */
public record CompatibilityIssue(String extensionId, String reason) {
}
