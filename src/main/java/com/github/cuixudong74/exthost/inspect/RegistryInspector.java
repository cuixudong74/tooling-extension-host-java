package com.github.cuixudong74.exthost.inspect;

import com.github.cuixudong74.exthost.merge.EffectiveContribution;
import com.github.cuixudong74.exthost.merge.EffectiveRegistry;
import com.github.cuixudong74.exthost.model.ContributionType;

import java.util.List;
import java.util.Optional;

/** 生效状态检查器：解释某条贡献为什么是该值，覆盖链完整可追溯。 */
public class RegistryInspector {

    public ContributionExplanation explain(EffectiveRegistry registry, ContributionType type, String id) {
        String key = type.name() + ":" + id;
        Optional<EffectiveContribution> found = registry.lookup(type, id);
        if (found.isEmpty()) {
            return new ContributionExplanation(key, null, null, List.of(), List.of(),
                    "no extension contributes '" + key + "'; it is not in effect");
        }
        EffectiveContribution ec = found.get();
        List<String> chain = ec.overriddenBy();
        List<String> overridden = chain.size() > 1 ? chain.subList(1, chain.size()) : List.of();
        String reason;
        if (overridden.isEmpty()) {
            reason = "'" + key + "' is contributed solely by extension '" + ec.winnerExtensionId()
                    + "' with no competing declarations";
        } else {
            reason = "'" + key + "' is contributed by extension '" + ec.winnerExtensionId()
                    + "', which wins the conflict over " + overridden
                    + " by (priority desc, extension id asc, declaration index asc)";
        }
        return new ContributionExplanation(key, ec.contribution(), ec.winnerExtensionId(),
                chain, overridden, reason);
    }
}
