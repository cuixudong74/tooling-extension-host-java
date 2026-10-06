package com.github.cuixudong74.toolingextensionhostjava.extension.merge;

import com.github.cuixudong74.toolingextensionhostjava.extension.model.Contribution;
import com.github.cuixudong74.toolingextensionhostjava.extension.model.ExtensionManifest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多扩展贡献合并与冲突裁决。
 *
 * <p>裁决规则（同一贡献 key 下，靠前者生效）：</p>
 * <ol>
 *   <li>贡献声明的 priority 高者优先；</li>
 *   <li>priority 相同则扩展 id 字典序小者优先（稳定、与读取顺序无关）；</li>
 *   <li>仍相同则扩展内声明序号小者优先。</li>
 * </ol>
 *
 * <p>合并前会先按扩展 id 对输入排序，因此结果不随扩展读取顺序或并发度改变。</p>
 */
public final class ContributionMerger {

    private static final Comparator<ContributionSource> RESOLUTION_ORDER = Comparator
            .comparingInt((ContributionSource s) -> s.contribution().getPriority()).reversed()
            .thenComparing(ContributionSource::extensionId)
            .thenComparingInt(ContributionSource::declarationIndex);

    /**
     * 合并一组扩展的贡献，返回 key -> 裁决结果（含完整覆盖链）。
     * 返回 Map 按 key 字典序排列，保证序列化输出稳定。
     */
    public Map<String, ResolvedContribution> merge(List<ExtensionManifest> manifests) {
        List<ExtensionManifest> sorted = new ArrayList<>(manifests);
        sorted.sort(Comparator.comparing(ExtensionManifest::id));

        Map<String, List<ContributionSource>> byKey = new LinkedHashMap<>();
        for (ExtensionManifest m : sorted) {
            List<Contribution> contributions = m.contributions();
            for (int i = 0; i < contributions.size(); i++) {
                Contribution c = contributions.get(i);
                byKey.computeIfAbsent(c.key(), k -> new ArrayList<>())
                        .add(new ContributionSource(m.id(), m.version(), c, i));
            }
        }

        Map<String, ResolvedContribution> resolved = new java.util.TreeMap<>();
        for (var entry : byKey.entrySet()) {
            List<ContributionSource> chain = new ArrayList<>(entry.getValue());
            chain.sort(RESOLUTION_ORDER);
            ContributionSource winner = chain.get(0);
            List<ContributionSource> overridden = List.copyOf(chain.subList(1, chain.size()));
            resolved.put(entry.getKey(), new ResolvedContribution(entry.getKey(), winner, overridden));
        }
        return java.util.Collections.unmodifiableMap(resolved);
    }
}
