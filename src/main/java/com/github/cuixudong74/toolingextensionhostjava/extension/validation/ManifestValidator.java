package com.github.cuixudong74.toolingextensionhostjava.extension.validation;

import com.github.cuixudong74.toolingextensionhostjava.extension.model.Contribution;
import com.github.cuixudong74.toolingextensionhostjava.extension.model.ExtensionManifest;
import com.github.cuixudong74.toolingextensionhostjava.extension.model.HostProfile;
import com.github.cuixudong74.toolingextensionhostjava.extension.version.VersionRange;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 清单合法性校验：必填字段、版本号格式、贡献类型与标识、能力标识引用。
 * 校验以整个扩展集合为单位，任一清单非法则整体拒绝（不得只加载一半）。
 */
public final class ManifestValidator {

    private static final Pattern EXTENSION_ID = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9._-]{0,127}$");
    private static final Pattern CONTRIBUTION_ID = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9._/-]{0,255}$");

    /**
     * 校验整个清单集合，发现任何错误即抛出携带全部定位信息的异常。
     *
     * @param manifests 清单集合（结构已解析）
     * @param host      宿主信息（用于能力标识存在性校验）
     * @throws ManifestValidationException 含 "扩展id.字段: 原因" 形式的全部错误
     */
    public void validateAll(List<ExtensionManifest> manifests, HostProfile host) {
        List<String> errors = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        for (ExtensionManifest m : manifests) {
            validateOne(m, host, seenIds, errors);
        }
        if (!errors.isEmpty()) {
            throw new ManifestValidationException(errors);
        }
    }

    private void validateOne(ExtensionManifest m, HostProfile host, Set<String> seenIds, List<String> errors) {
        String id = m.id();
        String prefix = (id == null || id.isBlank()) ? "<unknown>" : id;

        if (id == null || id.isBlank()) {
            errors.add("<unknown>.id: 扩展标识为必填字段");
        } else if (!EXTENSION_ID.matcher(id).matches()) {
            errors.add(prefix + ".id: 扩展标识含非法字符: " + id);
        } else if (!seenIds.add(id)) {
            errors.add(prefix + ".id: 扩展标识在集合中重复: " + id);
        }

        if (m.version() == null || m.version().isBlank()) {
            errors.add(prefix + ".version: 扩展版本为必填字段");
        } else if (!VersionRange.isValidVersion(m.version())) {
            errors.add(prefix + ".version: 非法版本号: " + m.version());
        }

        if (m.apiVersionRange() == null || m.apiVersionRange().isBlank()) {
            errors.add(prefix + ".apiVersionRange: 接口版本范围为必填字段");
        } else {
            try {
                VersionRange.parse(m.apiVersionRange());
            } catch (IllegalArgumentException e) {
                errors.add(prefix + ".apiVersionRange: " + e.getMessage());
            }
        }

        for (String cap : m.requiredCapabilities()) {
            if (cap == null || cap.isBlank()) {
                errors.add(prefix + ".requiredCapabilities: 能力标识不能为空");
            } else if (!host.capabilities().contains(cap)) {
                errors.add(prefix + ".requiredCapabilities: 引用了宿主不存在的能力标识: " + cap);
            }
        }

        if (m.contributions().isEmpty()) {
            errors.add(prefix + ".contributions: 至少声明一条贡献");
        }
        Set<String> seenContributionKeys = new HashSet<>();
        for (int i = 0; i < m.contributions().size(); i++) {
            Contribution c = m.contributions().get(i);
            String field = prefix + ".contributions[" + i + "]";
            if (c.getType() == null) {
                errors.add(field + ".type: 贡献类型为必填字段，合法值为 COMMAND/SETTING/KEYBINDING/LANGUAGE/PANEL");
            }
            if (c.getId() == null || c.getId().isBlank()) {
                errors.add(field + ".id: 贡献标识为必填字段");
            } else if (!CONTRIBUTION_ID.matcher(c.getId()).matches()) {
                errors.add(field + ".id: 贡献标识含非法字符: " + c.getId());
            } else if (c.getType() != null && !seenContributionKeys.add(c.key())) {
                errors.add(field + ".id: 同一扩展内贡献重复声明: " + c.key());
            }
        }
    }
}
