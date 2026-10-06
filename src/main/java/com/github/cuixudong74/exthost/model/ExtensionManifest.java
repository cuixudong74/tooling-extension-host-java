package com.github.cuixudong74.exthost.model;

import java.util.List;

/**
 * 扩展声明式清单。
 *
 * @param id                   扩展唯一标识（必填）
 * @param version              扩展版本（必填，语义化版本）
 * @param apiVersionRange      依赖的宿主接口版本范围，如 "[1.0,2.0)"（必填）
 * @param requiredCapabilities 所需能力标识列表（可空）
 * @param priority             冲突裁决优先级，数值大者胜（可空，默认 0）
 * @param contributions        贡献声明列表（可空）
 */
public record ExtensionManifest(
        String id,
        String version,
        String apiVersionRange,
        List<String> requiredCapabilities,
        Integer priority,
        List<Contribution> contributions) {
}
