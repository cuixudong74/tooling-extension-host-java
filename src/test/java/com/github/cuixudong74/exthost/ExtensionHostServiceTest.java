package com.github.cuixudong74.exthost;

import com.github.cuixudong74.exthost.host.ExtensionHostService;
import com.github.cuixudong74.exthost.host.UpdateResult;
import com.github.cuixudong74.exthost.inspect.ContributionExplanation;
import com.github.cuixudong74.exthost.merge.ContributionMerger;
import com.github.cuixudong74.exthost.merge.MergeConfig;
import com.github.cuixudong74.exthost.model.ContributionType;
import com.github.cuixudong74.exthost.model.ExtensionManifest;
import com.github.cuixudong74.exthost.resolution.CompatibilityChecker;
import com.github.cuixudong74.exthost.resolution.MismatchPolicy;
import com.github.cuixudong74.exthost.validation.ManifestValidator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExtensionHostServiceTest {

    private ExtensionHostService service(MismatchPolicy policy) {
        return new ExtensionHostService(TestFixtures.HOST, new ManifestValidator(),
                new CompatibilityChecker(), new ContributionMerger(),
                MergeConfig.defaults(), policy, null);
    }

    private ExtensionManifest withSetting(String extId, int priority, String value) {
        return TestFixtures.manifest(extId, "1.0.0", "[1.0,2.0)", List.of(), priority,
                List.of(TestFixtures.contribution(ContributionType.SETTING, "editor.fontSize", value)));
    }

    @Test
    void invalidSetIsRejectedAndOldSetKeepsServing() {
        ExtensionHostService svc = service(MismatchPolicy.REJECT);
        assertTrue(svc.replaceAll(List.of(withSetting("ext.a", 1, "old"))).committed());
        long gen = svc.snapshot().getGeneration();

        ExtensionManifest bad = TestFixtures.manifest("ext.bad", "1.0.0", "[1.0,2.0)",
                List.of("ghost-capability"), null, List.of());
        UpdateResult result = svc.replaceAll(List.of(withSetting("ext.b", 1, "new"), bad));

        assertFalse(result.committed());
        assertFalse(result.errors().isEmpty());
        assertTrue(result.errors().get(0).contains("ext.bad"));
        // 回退：旧集合继续服务，代次不变
        assertEquals(gen, svc.snapshot().getGeneration());
        assertEquals("old", svc.snapshot().getRegistry()
                .lookup(ContributionType.SETTING, "editor.fontSize").orElseThrow()
                .contribution().payload().get("value"));
    }

    @Test
    void incompatibleVersionRejectsWholeUpdateUnderRejectPolicy() {
        ExtensionHostService svc = service(MismatchPolicy.REJECT);
        assertTrue(svc.replaceAll(List.of(withSetting("ext.a", 1, "v1"))).committed());
        ExtensionManifest wrongApi = TestFixtures.manifest("ext.b", "1.0.0", "[9.0,10.0)",
                List.of(), null, List.of());
        UpdateResult r = svc.enable(wrongApi);
        assertFalse(r.committed());
        assertEquals(1, svc.snapshot().getExtensions().size());
        assertEquals("ext.a", svc.snapshot().getExtensions().get(0).id());
    }

    @Test
    void degradePolicyDropsIncompatibleExtensionButCommitsRest() {
        ExtensionHostService svc = service(MismatchPolicy.DEGRADE);
        ExtensionManifest wrongApi = TestFixtures.manifest("ext.b", "1.0.0", "[9.0,10.0)",
                List.of(), null, List.of());
        UpdateResult r = svc.replaceAll(List.of(withSetting("ext.a", 1, "v1"), wrongApi));
        assertTrue(r.committed());
        assertEquals(1, r.warnings().size());
        assertTrue(r.warnings().get(0).contains("ext.b"));
        assertEquals(1, svc.snapshot().getExtensions().size());
    }

    @Test
    void enableDisableUpgradeHotUpdate() {
        ExtensionHostService svc = service(MismatchPolicy.REJECT);
        assertTrue(svc.enable(withSetting("ext.a", 1, "a")).committed());
        assertTrue(svc.enable(withSetting("ext.b", 5, "b")).committed());
        assertEquals("b", svc.snapshot().getRegistry()
                .lookup(ContributionType.SETTING, "editor.fontSize").orElseThrow()
                .contribution().payload().get("value"));

        // 停用高优先级扩展后，低优先级的浮现
        assertTrue(svc.disable("ext.b").committed());
        assertEquals("a", svc.snapshot().getRegistry()
                .lookup(ContributionType.SETTING, "editor.fontSize").orElseThrow()
                .contribution().payload().get("value"));

        // 同 id 升级替换
        ExtensionManifest upgraded = TestFixtures.manifest("ext.a", "2.0.0", "[1.0,2.0)",
                List.of(), 1, List.of(TestFixtures.contribution(
                        ContributionType.SETTING, "editor.fontSize", "a2")));
        assertTrue(svc.enable(upgraded).committed());
        assertEquals("a2", svc.snapshot().getRegistry()
                .lookup(ContributionType.SETTING, "editor.fontSize").orElseThrow()
                .contribution().payload().get("value"));
        assertEquals(1, svc.snapshot().getExtensions().size());
        assertEquals("2.0.0", svc.snapshot().getExtensions().get(0).version());
    }

    @Test
    void explainMatchesEffectiveStateAndTracesFullChain() {
        ExtensionHostService svc = service(MismatchPolicy.REJECT);
        svc.replaceAll(List.of(
                withSetting("ext.low", 1, "small"),
                withSetting("ext.mid", 5, "medium"),
                withSetting("ext.high", 9, "large")));

        ContributionExplanation ex = svc.explain(ContributionType.SETTING, "editor.fontSize");
        // 与实际生效状态一致
        assertEquals("large", ex.effective().payload().get("value"));
        assertEquals("ext.high", ex.winnerExtensionId());
        // 完整覆盖链，不只一部分
        assertEquals(List.of("ext.high", "ext.mid", "ext.low"), ex.overrideChain());
        assertEquals(List.of("ext.mid", "ext.low"), ex.indirectInfluencers());
        assertFalse(ex.reason().isBlank());
    }

    @Test
    void explainUnknownContribution() {
        ExtensionHostService svc = service(MismatchPolicy.REJECT);
        svc.replaceAll(List.of(withSetting("ext.a", 1, "a")));
        ContributionExplanation ex = svc.explain(ContributionType.COMMAND, "missing");
        assertNull(ex.effective());
        assertTrue(ex.overrideChain().isEmpty());
    }

    @Test
    void generationIncreasesOnEachCommit() {
        ExtensionHostService svc = service(MismatchPolicy.REJECT);
        svc.replaceAll(List.of(withSetting("ext.a", 1, "a")));
        long g1 = svc.snapshot().getGeneration();
        svc.enable(withSetting("ext.b", 1, "b"));
        long g2 = svc.snapshot().getGeneration();
        svc.disable("ext.b");
        assertTrue(g1 < g2);
        assertTrue(g2 < svc.snapshot().getGeneration());
    }
}
