package com.github.cuixudong74.toolingextensionhostjava.extension.registry;

import com.github.cuixudong74.toolingextensionhostjava.extension.compat.CompatibilityIssue;
import com.github.cuixudong74.toolingextensionhostjava.extension.merge.ContributionSource;
import com.github.cuixudong74.toolingextensionhostjava.extension.merge.ResolvedContribution;
import com.github.cuixudong74.toolingextensionhostjava.extension.model.ExtensionManifest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 一次成功提交的不可变扩展集合快照。查询永远落在某个完整快照上，
 * 因此任一时刻读到的都是某个已提交集合的完整结果，不会出现新旧混合。
 */
public final class ExtensionSetSnapshot {

    private final long generation;
    private final List<ExtensionManifest> activeExtensions;
    private final List<CompatibilityIssue> degradedIssues;
    private final Map<String, ResolvedContribution> contributions;

    public ExtensionSetSnapshot(long generation,
                                List<ExtensionManifest> activeExtensions,
                                List<CompatibilityIssue> degradedIssues,
                                Map<String, ResolvedContribution> contributions) {
        this.generation = generation;
        this.activeExtensions = List.copyOf(activeExtensions);
        this.degradedIssues = List.copyOf(degradedIssues);
        this.contributions = Map.copyOf(contributions);
    }

    public static ExtensionSetSnapshot empty() {
        return new ExtensionSetSnapshot(0, List.of(), List.of(), Map.of());
    }

    public long generation() { return generation; }
    public List<ExtensionManifest> activeExtensions() { return activeExtensions; }
    public List<CompatibilityIssue> degradedIssues() { return degradedIssues; }
    public Map<String, ResolvedContribution> contributions() { return contributions; }

    /** 查询某个贡献 key 的生效结果，可能为 null（不存在）。 */
    public ResolvedContribution resolve(String key) {
        return contributions.get(key);
    }

    /**
     * 解释某个贡献 key 为什么是当前值：生效者、完整覆盖链、间接影响者。
     * 解释直接来自本快照的裁决结果，保证与实际生效状态一致。
     */
    public ContributionExplanation explain(String key) {
        ResolvedContribution resolved = contributions.get(key);
        if (resolved == null) {
            return ContributionExplanation.notFound(key);
        }
        ContributionSource winner = resolved.winner();
        List<ContributionSource> overridden = resolved.overridden();

        List<String> affected = new ArrayList<>();
        affected.add(winner.extensionId());
        for (ContributionSource s : overridden) {
            if (!affected.contains(s.extensionId())) {
                affected.add(s.extensionId());
            }
        }

        String reason;
        if (overridden.isEmpty()) {
            reason = "仅扩展 " + winner.extensionId() + " 声明了该贡献，直接生效";
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("扩展 ").append(winner.extensionId())
                    .append(" 以 priority=").append(winner.contribution().getPriority())
                    .append(" 胜出；被覆盖者: ");
            for (int i = 0; i < overridden.size(); i++) {
                ContributionSource s = overridden.get(i);
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(s.extensionId())
                        .append("(priority=").append(s.contribution().getPriority()).append(")");
            }
            reason = sb.toString();
        }
        return new ContributionExplanation(key, true, winner, overridden, affected, reason);
    }
}
