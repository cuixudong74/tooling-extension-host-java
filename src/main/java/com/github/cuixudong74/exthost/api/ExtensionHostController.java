package com.github.cuixudong74.exthost.api;

import com.github.cuixudong74.exthost.host.ExtensionHostService;
import com.github.cuixudong74.exthost.host.ExtensionSetSnapshot;
import com.github.cuixudong74.exthost.host.UpdateResult;
import com.github.cuixudong74.exthost.inspect.ContributionExplanation;
import com.github.cuixudong74.exthost.merge.EffectiveContribution;
import com.github.cuixudong74.exthost.model.ContributionType;
import com.github.cuixudong74.exthost.model.ExtensionManifest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 本地 REST 接口：查询生效状态、解释取值、触发热更新。 */
@RestController
@RequestMapping("/api")
public class ExtensionHostController {

    private final ExtensionHostService service;

    public ExtensionHostController(ExtensionHostService service) {
        this.service = service;
    }

    /** 整体替换扩展集合（热更新，失败自动回退）。 */
    @PutMapping("/extensions")
    public ResponseEntity<UpdateResult> replaceAll(@RequestBody List<ExtensionManifest> manifests) {
        UpdateResult result = service.replaceAll(manifests);
        return result.committed() ? ResponseEntity.ok(result)
                : ResponseEntity.unprocessableEntity().body(result);
    }

    /** 启用/升级单个扩展。 */
    @PostMapping("/extensions")
    public ResponseEntity<UpdateResult> enable(@RequestBody ExtensionManifest manifest) {
        UpdateResult result = service.enable(manifest);
        return result.committed() ? ResponseEntity.ok(result)
                : ResponseEntity.unprocessableEntity().body(result);
    }

    /** 停用扩展。 */
    @DeleteMapping("/extensions/{id}")
    public UpdateResult disable(@PathVariable String id) {
        return service.disable(id);
    }

    /** 当前生效集合与代次。 */
    @GetMapping("/extensions")
    public Map<String, Object> current() {
        ExtensionSetSnapshot snap = service.snapshot();
        return Map.of(
                "generation", snap.getGeneration(),
                "extensions", snap.getExtensions());
    }

    /** 当前生效的贡献注册表。 */
    @GetMapping("/contributions")
    public Map<String, EffectiveContribution> contributions() {
        ExtensionSetSnapshot snap = service.snapshot();
        return snap.getRegistry() == null ? Map.of() : snap.getRegistry().all();
    }

    /** 检查接口：解释某条贡献为什么是该值。 */
    @GetMapping("/explain/{type}/{id}")
    public ContributionExplanation explain(@PathVariable ContributionType type, @PathVariable String id) {
        return service.explain(type, id);
    }
}
