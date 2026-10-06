package com.github.cuixudong74.toolingextensionhostjava.extension.merge;

import java.util.List;

/**
 * 某个贡献标识的最终裁决结果：生效者 + 完整覆盖链（按裁决顺序，首个为生效者）。
 */
public record ResolvedContribution(String key, ContributionSource winner,
                                   List<ContributionSource> overridden) {

    public ResolvedContribution {
        overridden = overridden == null ? List.of() : List.copyOf(overridden);
    }

    /** 完整声明链，含生效者在内。 */
    public List<ContributionSource> chain() {
        var all = new java.util.ArrayList<ContributionSource>(overridden.size() + 1);
        all.add(winner);
        all.addAll(overridden);
        return List.copyOf(all);
    }
}
