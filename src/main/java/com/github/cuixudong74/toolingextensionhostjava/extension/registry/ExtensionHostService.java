package com.github.cuixudong74.toolingextensionhostjava.extension.registry;

import com.github.cuixudong74.toolingextensionhostjava.extension.compat.CompatibilityChecker;
import com.github.cuixudong74.toolingextensionhostjava.extension.compat.CompatibilityIssue;
import com.github.cuixudong74.toolingextensionhostjava.extension.compat.CompatibilityPolicy;
import com.github.cuixudong74.toolingextensionhostjava.extension.merge.ContributionMerger;
import com.github.cuixudong74.toolingextensionhostjava.extension.merge.ResolvedContribution;
import com.github.cuixudong74.toolingextensionhostjava.extension.model.ExtensionManifest;
import com.github.cuixudong74.toolingextensionhostjava.extension.model.HostProfile;
import com.github.cuixudong74.toolingextensionhostjava.extension.validation.ManifestValidationException;
import com.github.cuixudong74.toolingextensionhostjava.extension.validation.ManifestValidator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 扩展宿主核心服务：装载、热更新、查询入口。
 *
 * <p>并发模型：</p>
 * <ul>
 *   <li>读：通过 {@link AtomicReference} 无锁读取不可变快照，慢查询不阻塞更新；</li>
 *   <li>写：所有更新在内部锁上串行执行，先完整构建并校验新快照，
 *       校验失败则丢弃新快照、继续以旧快照服务（回退语义），
 *       成功才一次性原子切换，杜绝新旧混合与状态漂移。</li>
 * </ul>
 */
public class ExtensionHostService {

    private final HostProfile hostProfile;
    private final CompatibilityPolicy policy;
    private final ManifestValidator validator = new ManifestValidator();
    private final CompatibilityChecker compatibilityChecker = new CompatibilityChecker();
    private final ContributionMerger merger = new ContributionMerger();

    private final AtomicReference<ExtensionSetSnapshot> current = new AtomicReference<>(ExtensionSetSnapshot.empty());
    private final Object updateLock = new Object();

    /** 最近一次成功提交的“请求态”清单集合（含被降级的扩展），供增量操作计算新集合。 */
    private List<ExtensionManifest> lastRequested = List.of();

    public ExtensionHostService(HostProfile hostProfile, CompatibilityPolicy policy) {
        this.hostProfile = hostProfile;
        this.policy = policy;
    }

    public HostProfile hostProfile() { return hostProfile; }
    public CompatibilityPolicy policy() { return policy; }

    // ---------- 查询（无锁，落在某个完整快照上） ----------

    /** 当前生效快照。 */
    public ExtensionSetSnapshot snapshot() {
        return current.get();
    }

    /** 查询某贡献 key 的生效裁决结果，不存在返回 null。 */
    public ResolvedContribution resolve(String key) {
        return current.get().resolve(key);
    }

    /** 解释某贡献 key 为什么是当前值。 */
    public ContributionExplanation explain(String key) {
        return current.get().explain(key);
    }

    /** 当前全部生效贡献。 */
    public Map<String, ResolvedContribution> contributions() {
        return current.get().contributions();
    }

    // ---------- 更新（串行，失败回退） ----------

    /**
     * 以全新扩展集合整体替换当前集合（热更新）。
     * 校验失败时当前服务状态不变，返回携带原因的拒绝结果。
     */
    public UpdateResult replaceAll(List<ExtensionManifest> manifests) {
        synchronized (updateLock) {
            List<ExtensionManifest> requested = List.copyOf(manifests);
            BuildOutcome outcome = build(requested);
            if (outcome.error != null) {
                return UpdateResult.rejected(current.get().generation(), outcome.error);
            }
            publish(requested, outcome);
            return UpdateResult.committed(current.get().generation(), outcome.degraded);
        }
    }

    /** 启用（或替换）一个扩展。 */
    public UpdateResult enable(ExtensionManifest manifest) {
        synchronized (updateLock) {
            List<ExtensionManifest> requested = new ArrayList<>(lastRequested);
            requested.removeIf(m -> m.id().equals(manifest.id()));
            requested.add(manifest);
            BuildOutcome outcome = build(requested);
            if (outcome.error != null) {
                return UpdateResult.rejected(current.get().generation(), outcome.error);
            }
            publish(requested, outcome);
            return UpdateResult.committed(current.get().generation(), outcome.degraded);
        }
    }

    /** 停用一个扩展。 */
    public UpdateResult disable(String extensionId) {
        synchronized (updateLock) {
            List<ExtensionManifest> requested = new ArrayList<>(lastRequested);
            boolean removed = requested.removeIf(m -> m.id().equals(extensionId));
            if (!removed) {
                return UpdateResult.rejected(current.get().generation(),
                        "扩展不存在，无法停用: " + extensionId);
            }
            BuildOutcome outcome = build(requested);
            if (outcome.error != null) {
                return UpdateResult.rejected(current.get().generation(), outcome.error);
            }
            publish(requested, outcome);
            return UpdateResult.committed(current.get().generation(), outcome.degraded);
        }
    }

    /** 升级一个扩展（等价于以新清单替换同 id 扩展）。 */
    public UpdateResult upgrade(ExtensionManifest newManifest) {
        return enable(newManifest);
    }

    // ---------- 内部 ----------

    private record BuildOutcome(List<ExtensionManifest> active, List<CompatibilityIssue> degraded, String error) {
        static BuildOutcome error(String message) { return new BuildOutcome(null, null, message); }
        static BuildOutcome ok(List<ExtensionManifest> active, List<CompatibilityIssue> degraded) {
            return new BuildOutcome(active, degraded, null);
        }
    }

    /** 构建新快照所需的全部内容；任何失败都只返回错误，不触碰当前状态。 */
    private BuildOutcome build(List<ExtensionManifest> requested) {
        try {
            validator.validateAll(requested, hostProfile);
        } catch (ManifestValidationException e) {
            return BuildOutcome.error("清单校验失败: " + e.getMessage());
        }

        List<ExtensionManifest> active = new ArrayList<>();
        List<CompatibilityIssue> degraded = new ArrayList<>();
        for (ExtensionManifest m : requested) {
            List<CompatibilityIssue> issues = compatibilityChecker.check(m, hostProfile);
            if (issues.isEmpty()) {
                active.add(m);
            } else if (policy == CompatibilityPolicy.REJECT) {
                return BuildOutcome.error("兼容性检查失败(REJECT): " + issues);
            } else {
                degraded.addAll(issues);
            }
        }
        return BuildOutcome.ok(active, degraded);
    }

    private void publish(List<ExtensionManifest> requested, BuildOutcome outcome) {
        long generation = current.get().generation() + 1;
        ExtensionSetSnapshot snapshot = new ExtensionSetSnapshot(
                generation,
                outcome.active,
                outcome.degraded,
                merger.merge(outcome.active));
        current.set(snapshot);
        lastRequested = requested;
    }
}
