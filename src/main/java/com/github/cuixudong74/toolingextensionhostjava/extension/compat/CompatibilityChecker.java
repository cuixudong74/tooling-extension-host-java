package com.github.cuixudong74.toolingextensionhostjava.extension.compat;

import com.github.cuixudong74.toolingextensionhostjava.extension.model.ExtensionManifest;
import com.github.cuixudong74.toolingextensionhostjava.extension.model.HostProfile;
import com.github.cuixudong74.toolingextensionhostjava.extension.version.VersionRange;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * 宿主与扩展的版本 / 能力匹配检查。每个问题都可定位到扩展并给出可解释原因。
 */
public final class CompatibilityChecker {

    /**
     * 检查单个扩展与宿主的兼容性。
     *
     * @return 问题列表，空列表表示兼容
     */
    public List<CompatibilityIssue> check(ExtensionManifest manifest, HostProfile host) {
        List<CompatibilityIssue> issues = new ArrayList<>();
        String id = manifest.id();

        VersionRange range = VersionRange.parse(manifest.apiVersionRange());
        if (!range.contains(host.apiVersion())) {
            issues.add(new CompatibilityIssue(id,
                    "接口版本不匹配: 扩展要求 apiVersion 在范围 " + range
                            + " 内, 宿主实际为 " + host.apiVersion()));
        }

        var missing = new TreeSet<>(manifest.requiredCapabilities());
        missing.removeAll(host.capabilities());
        for (String cap : missing) {
            issues.add(new CompatibilityIssue(id,
                    "缺少所需能力: 扩展要求能力 " + cap + ", 宿主未提供 (宿主能力: "
                            + new TreeSet<>(host.capabilities()) + ")"));
        }
        return issues;
    }
}
