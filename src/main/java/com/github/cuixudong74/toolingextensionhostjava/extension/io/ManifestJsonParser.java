package com.github.cuixudong74.toolingextensionhostjava.extension.io;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.cuixudong74.toolingextensionhostjava.extension.model.Contribution;
import com.github.cuixudong74.toolingextensionhostjava.extension.model.ContributionType;
import com.github.cuixudong74.toolingextensionhostjava.extension.model.ExtensionManifest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 将 JSON 文本解析为清单对象（仅结构层面解析；合法性由
 * {@link com.github.cuixudong74.toolingextensionhostjava.extension.validation.ManifestValidator} 把关）。
 *
 * <p>非法的贡献类型不会被丢弃，而是以 {@code type=null} 保留并交由校验器
 * 给出可定位到扩展与字段的拒绝原因。</p>
 */
public final class ManifestJsonParser {

    private final ObjectMapper mapper = new ObjectMapper();

    public ExtensionManifest parse(String json) {
        JsonNode root;
        try {
            root = mapper.readTree(json);
        } catch (Exception e) {
            throw new ManifestParseException("清单不是合法 JSON: " + e.getMessage(), e);
        }
        if (root == null || !root.isObject()) {
            throw new ManifestParseException("清单必须是 JSON 对象", null);
        }

        String id = textOrNull(root.get("id"));
        String version = textOrNull(root.get("version"));
        String apiVersionRange = textOrNull(root.get("apiVersionRange"));

        Set<String> capabilities = new LinkedHashSet<>();
        JsonNode caps = root.get("requiredCapabilities");
        if (caps != null && caps.isArray()) {
            for (JsonNode c : caps) {
                capabilities.add(c.isTextual() ? c.asText() : null);
            }
        }

        List<Contribution> contributions = new ArrayList<>();
        JsonNode contribs = root.get("contributions");
        if (contribs != null && contribs.isArray()) {
            for (JsonNode c : contribs) {
                contributions.add(parseContribution(c));
            }
        }
        return new ExtensionManifest(id, version, apiVersionRange, capabilities, contributions);
    }

    private Contribution parseContribution(JsonNode node) {
        String typeText = textOrNull(node.get("type"));
        ContributionType type = null;
        if (typeText != null) {
            try {
                type = ContributionType.valueOf(typeText.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                type = null; // 非法类型保留为 null，由校验器报告
            }
        }
        String id = textOrNull(node.get("id"));
        int priority = node.hasNonNull("priority") ? node.get("priority").asInt() : 0;

        Map<String, Object> payload = new LinkedHashMap<>();
        JsonNode p = node.get("payload");
        if (p != null && p.isObject()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> converted = mapper.convertValue(p, Map.class);
            payload.putAll(converted);
        }
        return new Contribution(type, id, payload, priority);
    }

    private static String textOrNull(JsonNode node) {
        return node != null && node.isTextual() ? node.asText() : null;
    }
}
