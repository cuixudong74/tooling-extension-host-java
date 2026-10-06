package com.github.cuixudong74.exthost.validation;

import com.github.cuixudong74.exthost.model.Contribution;
import com.github.cuixudong74.exthost.model.ExtensionManifest;
import com.github.cuixudong74.exthost.model.HostProfile;
import com.github.cuixudong74.exthost.model.VersionRange;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 清单合法性校验器：必填字段、版本格式、贡献类型、能力标识引用。
 * 校验以扩展为单位收集全部错误，可定位到具体扩展与字段。
 */
public class ManifestValidator {

    private static final Pattern ID_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9._-]*$");
    private static final Pattern CAPABILITY_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9._-]*$");

    /** 校验单个清单，返回全部错误（空列表表示合法）。 */
    public List<ManifestError> validate(ExtensionManifest manifest, HostProfile host) {
        List<ManifestError> errors = new ArrayList<>();
        if (manifest == null) {
            errors.add(new ManifestError("<unknown>", "<manifest>", "manifest is null"));
            return errors;
        }
        String extId = manifest.id() == null || manifest.id().isBlank() ? "<unknown>" : manifest.id();

        // 必填字段：id
        if (manifest.id() == null || manifest.id().isBlank()) {
            errors.add(new ManifestError(extId, "id", "required field is missing or blank"));
        } else if (!ID_PATTERN.matcher(manifest.id()).matches()) {
            errors.add(new ManifestError(extId, "id",
                    "invalid extension id '" + manifest.id() + "', must match " + ID_PATTERN.pattern()));
        }

        // 必填字段：version
        if (manifest.version() == null || manifest.version().isBlank()) {
            errors.add(new ManifestError(extId, "version", "required field is missing or blank"));
        } else if (!VersionRange.isValidVersion(manifest.version())) {
            errors.add(new ManifestError(extId, "version",
                    "invalid semantic version '" + manifest.version() + "'"));
        }

        // 必填字段：apiVersionRange
        if (manifest.apiVersionRange() == null || manifest.apiVersionRange().isBlank()) {
            errors.add(new ManifestError(extId, "apiVersionRange", "required field is missing or blank"));
        } else {
            try {
                VersionRange.parse(manifest.apiVersionRange());
            } catch (IllegalArgumentException e) {
                errors.add(new ManifestError(extId, "apiVersionRange", e.getMessage()));
            }
        }

        // 能力标识：格式合法 + 必须引用宿主实际提供的能力
        if (manifest.requiredCapabilities() != null) {
            Set<String> provided = host == null ? Set.of() : host.providedCapabilities();
            for (int i = 0; i < manifest.requiredCapabilities().size(); i++) {
                String cap = manifest.requiredCapabilities().get(i);
                String field = "requiredCapabilities[" + i + "]";
                if (cap == null || cap.isBlank()) {
                    errors.add(new ManifestError(extId, field, "capability id is blank"));
                } else if (!CAPABILITY_PATTERN.matcher(cap).matches()) {
                    errors.add(new ManifestError(extId, field,
                            "invalid capability id '" + cap + "'"));
                } else if (provided != null && !provided.contains(cap)) {
                    errors.add(new ManifestError(extId, field,
                            "capability '" + cap + "' is not provided by host"));
                }
            }
        }

        // 贡献声明：类型必须合法（反序列化阶段已保证非 null 的枚举值），id 必填且同类型内唯一
        if (manifest.contributions() != null) {
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < manifest.contributions().size(); i++) {
                Contribution c = manifest.contributions().get(i);
                String field = "contributions[" + i + "]";
                if (c == null) {
                    errors.add(new ManifestError(extId, field, "contribution is null"));
                    continue;
                }
                if (c.type() == null) {
                    errors.add(new ManifestError(extId, field + ".type",
                            "contribution type is missing or not a legal type"));
                }
                if (c.id() == null || c.id().isBlank()) {
                    errors.add(new ManifestError(extId, field + ".id",
                            "contribution id is missing or blank"));
                } else if (c.type() != null) {
                    String key = c.type() + ":" + c.id();
                    if (!seen.add(key)) {
                        errors.add(new ManifestError(extId, field + ".id",
                                "duplicate contribution id '" + c.id() + "' within type " + c.type()));
                    }
                }
            }
        }
        return errors;
    }

    /** 校验一批清单，任一非法则整体抛 ManifestValidationException（不允许只加载一半）。 */
    public void validateAll(List<ExtensionManifest> manifests, HostProfile host) {
        List<ManifestError> all = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (ExtensionManifest m : manifests) {
            all.addAll(validate(m, host));
            if (m != null && m.id() != null && !m.id().isBlank() && !ids.add(m.id())) {
                all.add(new ManifestError(m.id(), "id",
                        "duplicate extension id '" + m.id() + "' in the submitted set"));
            }
        }
        if (!all.isEmpty()) {
            throw new ManifestValidationException(all);
        }
    }
}
