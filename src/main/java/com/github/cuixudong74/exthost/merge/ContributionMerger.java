package com.github.cuixudong74.exthost.merge;

import com.github.cuixudong74.exthost.model.Contribution;
import com.github.cuixudong74.exthost.model.ExtensionManifest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 贡献合并器。
 *
 * 裁决规则（确定性，与扩展读取顺序、并发度无关）：
 * <ol>
 *   <li>优先级 priority 高者胜（未声明按 MergeConfig.defaultPriority）；</li>
 *   <li>优先级相同按扩展 id 字典序，小者胜；</li>
 *   <li>再相同按贡献在清单中的声明下标，小者胜。</li>
 * </ol>
 * 每条生效贡献记录完整来源链，可追溯。
 */
public class ContributionMerger {

    /** 单条贡献声明的来源信息。 */
    private record Declaration(String extensionId, int priority, int index, Contribution contribution) {
    }

    /** 裁决顺序：优先级降序、扩展 id 升序、声明下标升序。 */
    private static final Comparator<Declaration> ORDER = Comparator
            .comparingInt(Declaration::priority).reversed()
            .thenComparing(Declaration::extensionId)
            .thenComparingInt(Declaration::index);

    public EffectiveRegistry merge(List<ExtensionManifest> extensions, MergeConfig config) {
        // 先按扩展 id 排序，保证分组收集顺序与输入顺序无关
        List<ExtensionManifest> sorted = new ArrayList<>(extensions);
        sorted.sort(Comparator.comparing(ExtensionManifest::id));

        Map<String, List<Declaration>> grouped = new LinkedHashMap<>();
        for (ExtensionManifest ext : sorted) {
            if (ext.contributions() == null) {
                continue;
            }
            int priority = ext.priority() == null ? config.defaultPriority() : ext.priority();
            for (int i = 0; i < ext.contributions().size(); i++) {
                Contribution c = ext.contributions().get(i);
                String key = EffectiveRegistry.key(c.type(), c.id());
                grouped.computeIfAbsent(key, k -> new ArrayList<>())
                        .add(new Declaration(ext.id(), priority, i, c));
            }
        }

        Map<String, EffectiveContribution> effective = new LinkedHashMap<>();
        for (Map.Entry<String, List<Declaration>> e : grouped.entrySet()) {
            List<Declaration> declarations = new ArrayList<>(e.getValue());
            declarations.sort(ORDER);
            Declaration winner = declarations.get(0);
            List<String> chain = declarations.stream().map(Declaration::extensionId).toList();
            effective.put(e.getKey(),
                    new EffectiveContribution(winner.contribution(), winner.extensionId(), chain));
        }
        return new EffectiveRegistry(effective);
    }
}
