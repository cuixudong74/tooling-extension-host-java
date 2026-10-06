package com.github.cuixudong74.exthost.merge;

import com.github.cuixudong74.exthost.model.Contribution;

import java.util.List;

/**
 * 一条最终生效的贡献及其完整来源链。
 *
 * @param contribution 生效的贡献内容
 * @param winnerExtensionId 生效来源扩展
 * @param overriddenBy  覆盖链：按优先级从高到低的所有声明来源扩展 id（含 winner）
 */
public record EffectiveContribution(
        Contribution contribution,
        String winnerExtensionId,
        List<String> overriddenBy) {
}
