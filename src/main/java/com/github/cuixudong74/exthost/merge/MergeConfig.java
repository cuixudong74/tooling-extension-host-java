package com.github.cuixudong74.exthost.merge;

/**
 * 合并配置。
 *
 * @param defaultPriority 清单未声明 priority 时的默认优先级
 */
public record MergeConfig(int defaultPriority) {
    public static MergeConfig defaults() {
        return new MergeConfig(0);
    }
}
