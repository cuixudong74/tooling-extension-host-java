package com.github.cuixudong74.toolingextensionhostjava.extension.model;

import java.util.List;
import java.util.Set;

/**
 * 扩展声明式清单（已从 JSON 解析为结构化对象）。
 */
public record ExtensionManifest(
        String id,
        String version,
        String apiVersionRange,
        Set<String> requiredCapabilities,
        List<Contribution> contributions) {

    public ExtensionManifest {
        requiredCapabilities = requiredCapabilities == null ? Set.of() : Set.copyOf(requiredCapabilities);
        contributions = contributions == null ? List.of() : List.copyOf(contributions);
    }
}
