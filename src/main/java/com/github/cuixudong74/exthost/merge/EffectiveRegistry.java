package com.github.cuixudong74.exthost.merge;

import com.github.cuixudong74.exthost.model.ContributionType;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** 合并后的生效注册表（不可变）。key 为 "TYPE:id"。 */
public class EffectiveRegistry {

    private final Map<String, EffectiveContribution> entries;

    EffectiveRegistry(Map<String, EffectiveContribution> entries) {
        this.entries = Collections.unmodifiableMap(new TreeMap<>(entries));
    }

    public Optional<EffectiveContribution> lookup(ContributionType type, String id) {
        return Optional.ofNullable(entries.get(key(type, id)));
    }

    public Map<String, EffectiveContribution> all() {
        return entries;
    }

    public List<EffectiveContribution> byType(ContributionType type) {
        String prefix = type.name() + ":";
        return entries.entrySet().stream()
                .filter(e -> e.getKey().startsWith(prefix))
                .map(Map.Entry::getValue)
                .toList();
    }

    static String key(ContributionType type, String id) {
        return type.name() + ":" + id;
    }
}
