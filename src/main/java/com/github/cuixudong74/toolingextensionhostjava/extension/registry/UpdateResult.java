package com.github.cuixudong74.toolingextensionhostjava.extension.registry;

import com.github.cuixudong74.toolingextensionhostjava.extension.compat.CompatibilityIssue;

import java.util.List;

/**
 * 一次热更新的结果：是否提交、当前快照代际、降级/拒绝信息。
 */
public record UpdateResult(boolean committed, long generation, String message,
                           List<CompatibilityIssue> degraded) {

    public UpdateResult {
        degraded = degraded == null ? List.of() : List.copyOf(degraded);
    }

    public static UpdateResult committed(long generation, List<CompatibilityIssue> degraded) {
        return new UpdateResult(true, generation, "committed", degraded);
    }

    public static UpdateResult rejected(long generation, String message) {
        return new UpdateResult(false, generation, message, List.of());
    }
}
