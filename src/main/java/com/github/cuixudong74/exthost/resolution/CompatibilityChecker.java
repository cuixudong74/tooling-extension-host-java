package com.github.cuixudong74.exthost.resolution;

import com.github.cuixudong74.exthost.model.ExtensionManifest;
import com.github.cuixudong74.exthost.model.HostProfile;
import com.github.cuixudong74.exthost.model.VersionRange;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** 宿主与扩展的版本/能力匹配检查，结论可解释。 */
public class CompatibilityChecker {

    /** 检查接口版本范围与所需能力，返回全部问题（空列表表示完全兼容）。 */
    public List<CompatibilityIssue> check(ExtensionManifest manifest, HostProfile host) {
        List<CompatibilityIssue> issues = new ArrayList<>();

        // 接口版本范围匹配
        VersionRange range = VersionRange.parse(manifest.apiVersionRange());
        if (!range.contains(host.apiVersion())) {
            issues.add(CompatibilityIssue.incompatible(
                    "extension '" + manifest.id() + "' requires host api in range "
                            + manifest.apiVersionRange() + " but host provides " + host.apiVersion()));
        }

        // 能力匹配
        Set<String> provided = host.providedCapabilities() == null
                ? Set.of() : host.providedCapabilities();
        if (manifest.requiredCapabilities() != null) {
            Set<String> missing = new TreeSet<>();
            for (String cap : manifest.requiredCapabilities()) {
                if (!provided.contains(cap)) {
                    missing.add(cap);
                }
            }
            if (!missing.isEmpty()) {
                issues.add(CompatibilityIssue.incompatible(
                        "extension '" + manifest.id() + "' requires capabilities " + missing
                                + " but host only provides " + new TreeSet<>(provided)));
            }
        }
        return issues;
    }
}
