package com.github.cuixudong74.exthost.inspect;

import com.github.cuixudong74.exthost.model.Contribution;

import java.util.List;

/**
 * 某条贡献/设置最终取值的完整解释。
 *
 * @param key                贡献键 "TYPE:id"
 * @param effective          最终生效的贡献（不存在时为 null）
 * @param winnerExtensionId  生效来源扩展
 * @param overrideChain      完整覆盖链：按裁决顺序从高到低，每项为 "扩展id@版本"
 * @param indirectInfluencers 间接影响的扩展（被覆盖但参与裁决、或拉高了优先级的扩展）
 * @param reason             人类可读的结论说明
 */
public record ContributionExplanation(
        String key,
        Contribution effective,
        String winnerExtensionId,
        List<String> overrideChain,
        List<String> indirectInfluencers,
        String reason) {
}
