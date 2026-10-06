package com.github.cuixudong74.toolingextensionhostjava.extension.api;

import com.github.cuixudong74.toolingextensionhostjava.extension.io.ManifestJsonParser;
import com.github.cuixudong74.toolingextensionhostjava.extension.merge.ResolvedContribution;
import com.github.cuixudong74.toolingextensionhostjava.extension.model.ExtensionManifest;
import com.github.cuixudong74.toolingextensionhostjava.extension.registry.ContributionExplanation;
import com.github.cuixudong74.toolingextensionhostjava.extension.registry.ExtensionHostService;
import com.github.cuixudong74.toolingextensionhostjava.extension.registry.UpdateResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 对外 REST 接口：更新扩展集合、查询生效贡献、解释覆盖链。
 */
@RestController
@RequestMapping("/api/extension-host")
public class ExtensionHostController {

    private final ExtensionHostService service;
    private final ManifestJsonParser parser = new ManifestJsonParser();

    public ExtensionHostController(ExtensionHostService service) {
        this.service = service;
    }

    /** 整体替换扩展集合（请求体为清单 JSON 数组）。 */
    @PostMapping("/extensions")
    public ResponseEntity<UpdateResult> replaceAll(@RequestBody List<com.fasterxml.jackson.databind.JsonNode> manifestJsons) {
        List<ExtensionManifest> manifests = manifestJsons.stream()
                .map(node -> parser.parse(node.toString()))
                .toList();
        UpdateResult result = service.replaceAll(manifests);
        return result.committed() ? ResponseEntity.ok(result)
                : ResponseEntity.unprocessableEntity().body(result);
    }

    /** 启用 / 升级单个扩展（请求体为单个清单 JSON）。 */
    @PostMapping("/extensions/enable")
    public ResponseEntity<UpdateResult> enable(@RequestBody String manifestJson) {
        UpdateResult result = service.enable(parser.parse(manifestJson));
        return result.committed() ? ResponseEntity.ok(result)
                : ResponseEntity.unprocessableEntity().body(result);
    }

    /** 停用单个扩展。 */
    @PostMapping("/extensions/{id}/disable")
    public ResponseEntity<UpdateResult> disable(@PathVariable String id) {
        UpdateResult result = service.disable(id);
        return result.committed() ? ResponseEntity.ok(result)
                : ResponseEntity.unprocessableEntity().body(result);
    }

    /** 当前全部生效贡献及其裁决结果。 */
    @GetMapping("/contributions")
    public Map<String, ResolvedContribution> contributions() {
        return service.contributions();
    }

    /** 解释某贡献 key（格式 TYPE:id，如 SETTING:editor.tabSize）为何是当前值。 */
    @GetMapping("/explain/{type}/{id}")
    public ResponseEntity<ContributionExplanation> explain(@PathVariable String type,
                                                           @PathVariable String id) {
        ContributionExplanation explanation = service.explain(type.toUpperCase() + ":" + id);
        return explanation.found() ? ResponseEntity.ok(explanation)
                : ResponseEntity.notFound().build();
    }

    /** 当前快照代际与生效扩展。 */
    @GetMapping("/status")
    public Map<String, Object> status() {
        var snapshot = service.snapshot();
        return Map.of(
                "generation", snapshot.generation(),
                "activeExtensions", snapshot.activeExtensions().stream()
                        .map(m -> m.id() + "@" + m.version()).toList(),
                "degraded", snapshot.degradedIssues());
    }
}
