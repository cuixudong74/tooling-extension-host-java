package com.github.cuixudong74.exthost.host;

import com.github.cuixudong74.exthost.inspect.ContributionExplanation;
import com.github.cuixudong74.exthost.inspect.RegistryInspector;
import com.github.cuixudong74.exthost.merge.ContributionMerger;
import com.github.cuixudong74.exthost.merge.MergeConfig;
import com.github.cuixudong74.exthost.model.ContributionType;
import com.github.cuixudong74.exthost.model.ExtensionManifest;
import com.github.cuixudong74.exthost.model.HostProfile;
import com.github.cuixudong74.exthost.persistence.ExtensionSetStore;
import com.github.cuixudong74.exthost.resolution.CompatibilityChecker;
import com.github.cuixudong74.exthost.resolution.CompatibilityIssue;
import com.github.cuixudong74.exthost.resolution.MismatchPolicy;
import com.github.cuixudong74.exthost.validation.ManifestValidationException;
import com.github.cuixudong74.exthost.validation.ManifestValidator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 扩展宿主核心服务：装载、热更新、查询。
 *
 * 并发模型：
 * <ul>
 *   <li>查询走 AtomicReference 无锁读，永远读到某个已提交的完整快照；</li>
 *   <li>更新在 updateLock 上串行化，先完整构建并校验候选快照，再一次原子替换，
 *       更新之间不会互相覆盖造成状态漂移；</li>
 *   <li>校验失败不触碰当前快照，旧集合继续对外服务（回退语义）。</li>
 * </ul>
 */
public class ExtensionHostService {

    private final HostProfile hostProfile;
    private final ManifestValidator validator;
    private final CompatibilityChecker checker;
    private final ContributionMerger merger;
    private final MergeConfig mergeConfig;
    private final MismatchPolicy mismatchPolicy;
    private final RegistryInspector inspector = new RegistryInspector();
    private final ExtensionSetStore store; // 可空，空则不持久化

    private final AtomicReference<ExtensionSetSnapshot> current =
            new AtomicReference<>(new ExtensionSetSnapshot(0, List.of(), null));
    private final Object updateLock = new Object();

    public ExtensionHostService(HostProfile hostProfile,
                                ManifestValidator validator,
                                CompatibilityChecker checker,
                                ContributionMerger merger,
                                MergeConfig mergeConfig,
                                MismatchPolicy mismatchPolicy,
                                ExtensionSetStore store) {
        this.hostProfile = hostProfile;
        this.validator = validator;
        this.checker = checker;
        this.merger = merger;
        this.mergeConfig = mergeConfig;
        this.mismatchPolicy = mismatchPolicy;
        this.store = store;
    }

    /** 以全新集合替换当前集合；校验失败则整体回退，继续用旧集合。 */
    public UpdateResult replaceAll(List<ExtensionManifest> manifests) {
        synchronized (updateLock) {
            return commit(manifests);
        }
    }

    /** 启用/新增一个扩展（同 id 视为升级替换）。 */
    public UpdateResult enable(ExtensionManifest manifest) {
        synchronized (updateLock) {
            Map<String, ExtensionManifest> byId = currentById();
            byId.put(manifest.id(), manifest);
            return commit(new ArrayList<>(byId.values()));
        }
    }

    /** 停用一个扩展；不存在时视为无操作成功。 */
    public UpdateResult disable(String extensionId) {
        synchronized (updateLock) {
            Map<String, ExtensionManifest> byId = currentById();
            byId.remove(extensionId);
            return commit(new ArrayList<>(byId.values()));
        }
    }

    /** 当前生效快照。 */
    public ExtensionSetSnapshot snapshot() {
        return current.get();
    }

    /** 检查接口：解释某条贡献的最终取值，结果与当前生效快照一致。 */
    public ContributionExplanation explain(ContributionType type, String id) {
        ExtensionSetSnapshot snap = current.get();
        if (snap.getRegistry() == null) {
            return new ContributionExplanation(type.name() + ":" + id, null, null,
                    List.of(), List.of(), "no extension set has been committed yet");
        }
        return inspector.explain(snap.getRegistry(), type, id);
    }

    // ---- 内部实现 ----

    private Map<String, ExtensionManifest> currentById() {
        Map<String, ExtensionManifest> byId = new LinkedHashMap<>();
        for (ExtensionManifest m : current.get().getExtensions()) {
            byId.put(m.id(), m);
        }
        return byId;
    }

    /**
     * 构建并校验候选集合，全部通过才原子替换；失败返回拒绝结果，当前快照不变。
     * 调用方必须持有 updateLock。
     */
    private UpdateResult commit(List<ExtensionManifest> candidate) {
        long generation = current.get().getGeneration();

        // 1. 清单合法性校验：任一非法整体拒绝，不允许只加载一半
        try {
            validator.validateAll(candidate, hostProfile);
        } catch (ManifestValidationException e) {
            return UpdateResult.rejected(generation,
                    e.getErrors().stream().map(Object::toString).toList());
        }

        // 2. 版本与能力匹配：按策略拒绝或降级
        List<ExtensionManifest> accepted = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (ExtensionManifest m : candidate) {
            List<CompatibilityIssue> issues = checker.check(m, hostProfile);
            if (issues.isEmpty()) {
                accepted.add(m);
            } else if (mismatchPolicy == MismatchPolicy.REJECT) {
                issues.forEach(i -> errors.add(i.reason()));
            } else { // DEGRADE：剔除不兼容扩展，其余继续生效
                warnings.add("extension '" + m.id() + "' degraded out of the set: "
                        + issues.stream().map(CompatibilityIssue::reason).toList());
            }
        }
        if (!errors.isEmpty()) {
            return UpdateResult.rejected(generation, errors);
        }

        // 3. 合并为生效注册表，整体替换快照
        ExtensionSetSnapshot next = new ExtensionSetSnapshot(
                generation + 1, accepted, merger.merge(accepted, mergeConfig));
        current.set(next);

        // 4. 本地持久化（失败不影响已提交结果，仅作为警告暴露）
        if (store != null) {
            try {
                store.save(accepted);
            } catch (RuntimeException e) {
                warnings = new ArrayList<>(warnings);
                warnings.add("persistence failed: " + e.getMessage());
            }
        }
        return UpdateResult.ok(next.getGeneration(), warnings);
    }
}
