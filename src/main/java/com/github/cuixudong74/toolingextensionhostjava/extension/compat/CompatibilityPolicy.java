package com.github.cuixudong74.toolingextensionhostjava.extension.compat;

/**
 * 兼容性不满足时的处理策略。
 */
public enum CompatibilityPolicy {
    /** 不满足即拒绝整个扩展集合更新。 */
    REJECT,
    /** 不满足的扩展被降级（剔除），其余继续生效。 */
    DEGRADE
}
