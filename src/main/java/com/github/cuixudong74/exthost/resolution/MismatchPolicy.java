package com.github.cuixudong74.exthost.resolution;

/** 能力/版本不满足时的处理策略。 */
public enum MismatchPolicy {
    /** 直接拒绝该扩展。 */
    REJECT,
    /** 降级：剔除该扩展中依赖缺失能力的贡献，保留其余贡献。 */
    DEGRADE
}
