package com.github.cuixudong74.exthost.model;

import java.util.Map;

/**
 * 一条贡献声明。
 *
 * @param type  贡献类型
 * @param id    贡献标识（同类型内唯一）
 * @param payload 贡献内容（键值对，如命令标题、设置默认值等）
 */
public record Contribution(ContributionType type, String id, Map<String, Object> payload) {
}
