package com.github.cuixudong74.toolingextensionhostjava.extension.registry;

import com.github.cuixudong74.toolingextensionhostjava.extension.merge.ContributionSource;

import java.util.List;

/**
 * 对某个贡献标识最终生效状态的完整解释。
 *
 * @param key             贡献 key（类型:标识）
 * @param found           是否存在该贡献
 * @param winner          生效来源
 * @param overriddenBy    覆盖链（不含生效者，按裁决顺序排列）
 * @param affectedBy      间接影响该 key 的扩展 id（含生效者所在扩展）
 * @param reason          人类可读的裁决原因
 */
public record ContributionExplanation(String key, boolean found,
                                      ContributionSource winner,
                                      List<ContributionSource> overriddenBy,
                                      List<String> affectedBy,
                                      String reason) {

    public ContributionExplanation {
        overriddenBy = overriddenBy == null ? List.of() : List.copyOf(overriddenBy);
        affectedBy = affectedBy == null ? List.of() : List.copyOf(affectedBy);
    }

    public static ContributionExplanation notFound(String key) {
        return new ContributionExplanation(key, false, null, List.of(), List.of(),
                "当前扩展集合中没有任何扩展声明该贡献");
    }
}
