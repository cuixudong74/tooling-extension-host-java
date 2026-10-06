package com.github.cuixudong74.exthost.host;

import java.util.List;

/**
 * 一次集合更新的结果。
 *
 * @param committed  是否提交成功（失败时宿主继续用旧集合服务）
 * @param generation 当前生效集合代次
 * @param errors     失败原因列表（成功时为空）
 * @param warnings   降级等非致命提示（如某些扩展被降级剔除）
 */
public record UpdateResult(boolean committed, long generation, List<String> errors, List<String> warnings) {
    public static UpdateResult ok(long generation, List<String> warnings) {
        return new UpdateResult(true, generation, List.of(), List.copyOf(warnings));
    }

    public static UpdateResult rejected(long generation, List<String> errors) {
        return new UpdateResult(false, generation, List.copyOf(errors), List.of());
    }
}
