package com.github.cuixudong74.toolingextensionhostjava.extension.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 一条贡献声明：类型 + 标识 + 负载 + 优先级。
 */
public final class Contribution {
    private final ContributionType type;
    private final String id;
    private final Map<String, Object> payload;
    private final int priority;

    public Contribution(ContributionType type, String id, Map<String, Object> payload, int priority) {
        // 注意：type/id 允许为 null，合法性由 ManifestValidator 统一把关并给出可定位错误。
        this.type = type;
        this.id = id;
        this.payload = payload == null ? Map.of() : new LinkedHashMap<>(payload);
        this.priority = priority;
    }

    public ContributionType getType() { return type; }
    public String getId() { return id; }
    public Map<String, Object> getPayload() { return payload; }
    public int getPriority() { return priority; }

    /** 贡献全局唯一键：类型 + 标识。 */
    public String key() { return (type == null ? "<null>" : type.name()) + ":" + id; }
}
