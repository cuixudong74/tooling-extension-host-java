package com.github.cuixudong74.exthost.host;

import com.github.cuixudong74.exthost.merge.EffectiveRegistry;
import com.github.cuixudong74.exthost.model.ExtensionManifest;

import java.util.List;

/**
 * 一次完整提交的扩展集合快照（不可变），查询永远落在某个完整快照上。
 */
public class ExtensionSetSnapshot {

    private final long generation;
    private final List<ExtensionManifest> extensions;
    private final EffectiveRegistry registry;

    public ExtensionSetSnapshot(long generation, List<ExtensionManifest> extensions, EffectiveRegistry registry) {
        this.generation = generation;
        this.extensions = List.copyOf(extensions);
        this.registry = registry;
    }

    public long getGeneration() {
        return generation;
    }

    public List<ExtensionManifest> getExtensions() {
        return extensions;
    }

    public EffectiveRegistry getRegistry() {
        return registry;
    }
}
