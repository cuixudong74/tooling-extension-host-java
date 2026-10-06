package com.github.cuixudong74.toolingextensionhostjava.tests.extension;

import com.github.cuixudong74.toolingextensionhostjava.extension.compat.CompatibilityPolicy;
import com.github.cuixudong74.toolingextensionhostjava.extension.io.ManifestJsonParser;
import com.github.cuixudong74.toolingextensionhostjava.extension.model.ExtensionManifest;
import com.github.cuixudong74.toolingextensionhostjava.extension.model.HostProfile;
import com.github.cuixudong74.toolingextensionhostjava.extension.registry.ContributionExplanation;
import com.github.cuixudong74.toolingextensionhostjava.extension.registry.ExtensionHostService;
import com.github.cuixudong74.toolingextensionhostjava.extension.registry.UpdateResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtensionHostServiceTest {

    private final ManifestJsonParser parser = new ManifestJsonParser();
    private final HostProfile host = new HostProfile("1.5.0", Set.of("git", "docker"));

    private ExtensionHostService service(CompatibilityPolicy policy) {
        return new ExtensionHostService(host, policy);
    }

    private ExtensionManifest manifest(String id, String version, String apiRange,
                                       List<String> caps, String contributions) {
        String capsJson = caps.stream().map(c -> "\"" + c + "\"")
                .collect(java.util.stream.Collectors.joining(", ", "[", "]"));
        return parser.parse("""
                {
                  "id": "%s", "version": "%s", "apiVersionRange": "%s",
                  "requiredCapabilities": %s,
                  "contributions": %s
                }
                """.formatted(id, version, apiRange, capsJson, contributions));
    }

    private ExtensionManifest settingExt(String id, int priority, String value) {
        return manifest(id, "1.0.0", "[1.0,2.0)", List.of("git"),
                """
                [{"type": "SETTING", "id": "editor.tabSize", "priority": %d, "payload": {"default": "%s"}}]
                """.formatted(priority, value));
    }

    // ---------- 兼容性：版本不满足 ----------

    @Test
    void rejectPolicyRefusesIncompatibleVersion() {
        ExtensionHostService svc = service(CompatibilityPolicy.REJECT);
        ExtensionManifest bad = manifest("ext.newapi", "1.0.0", "[2.0,3.0)", List.of(),
                "[{\"type\": \"COMMAND\", \"id\": \"c1\"}]");
        UpdateResult result = svc.replaceAll(List.of(bad));
        assertFalse(result.committed());
        assertTrue(result.message().contains("接口版本不匹配"));
        assertTrue(result.message().contains("ext.newapi"));
        assertEquals(0, svc.snapshot().generation());
    }

    @Test
    void degradePolicyDropsIncompatibleExtension() {
        ExtensionHostService svc = service(CompatibilityPolicy.DEGRADE);
        ExtensionManifest good = settingExt("ext.good", 1, "4");
        ExtensionManifest bad = manifest("ext.newapi", "1.0.0", "[2.0,3.0)", List.of(),
                "[{\"type\": \"COMMAND\", \"id\": \"c1\"}]");
        UpdateResult result = svc.replaceAll(List.of(good, bad));
        assertTrue(result.committed());
        assertEquals(1, result.degraded().size());
        assertEquals("ext.newapi", result.degraded().get(0).extensionId());
        assertEquals(List.of("ext.good"),
                svc.snapshot().activeExtensions().stream().map(ExtensionManifest::id).toList());
    }

    // ---------- 冲突裁决 + 覆盖链解释 ----------

    @Test
    void explainTracesFullOverrideChain() {
        ExtensionHostService svc = service(CompatibilityPolicy.REJECT);
        svc.replaceAll(List.of(
                settingExt("ext.a", 1, "2"),
                settingExt("ext.b", 9, "8"),
                settingExt("ext.c", 5, "4")));

        // 实际生效值
        var resolved = svc.resolve("SETTING:editor.tabSize");
        assertEquals("ext.b", resolved.winner().extensionId());
        assertEquals("8", resolved.winner().contribution().getPayload().get("default"));

        // 解释与实际生效状态一致，且覆盖链完整
        ContributionExplanation ex = svc.explain("SETTING:editor.tabSize");
        assertTrue(ex.found());
        assertEquals("ext.b", ex.winner().extensionId());
        assertEquals(List.of("ext.c", "ext.a"),
                ex.overriddenBy().stream().map(s -> s.extensionId()).toList());
        assertEquals(List.of("ext.b", "ext.c", "ext.a"), ex.affectedBy());
        assertTrue(ex.reason().contains("ext.b"));
    }

    @Test
    void explainUnknownContribution() {
        ExtensionHostService svc = service(CompatibilityPolicy.REJECT);
        ContributionExplanation ex = svc.explain("COMMAND:nope");
        assertFalse(ex.found());
    }

    // ---------- 热更新与回退 ----------

    @Test
    void failedUpdateRollsBackToPreviousSet() {
        ExtensionHostService svc = service(CompatibilityPolicy.REJECT);
        assertTrue(svc.replaceAll(List.of(settingExt("ext.stable", 1, "4"))).committed());
        long genBefore = svc.snapshot().generation();

        // 提交一个非法集合（缺版本字段）
        ExtensionManifest illegal = parser.parse("""
                {"id": "ext.broken", "apiVersionRange": "^1.0",
                 "contributions": [{"type": "COMMAND", "id": "c"}]}
                """);
        UpdateResult failed = svc.replaceAll(List.of(illegal));
        assertFalse(failed.committed());

        // 旧集合继续服务，结果不变
        assertEquals(genBefore, svc.snapshot().generation());
        assertEquals("ext.stable", svc.resolve("SETTING:editor.tabSize").winner().extensionId());
    }

    @Test
    void disableAndEnableAreAtomic() {
        ExtensionHostService svc = service(CompatibilityPolicy.REJECT);
        svc.replaceAll(List.of(settingExt("ext.a", 1, "2"), settingExt("ext.b", 5, "4")));
        assertEquals("ext.b", svc.resolve("SETTING:editor.tabSize").winner().extensionId());

        assertTrue(svc.disable("ext.b").committed());
        assertEquals("ext.a", svc.resolve("SETTING:editor.tabSize").winner().extensionId());

        assertTrue(svc.enable(settingExt("ext.b", 5, "4")).committed());
        assertEquals("ext.b", svc.resolve("SETTING:editor.tabSize").winner().extensionId());
    }

    @Test
    void upgradeReplacesOldVersion() {
        ExtensionHostService svc = service(CompatibilityPolicy.REJECT);
        svc.replaceAll(List.of(settingExt("ext.a", 1, "2")));
        ExtensionManifest upgraded = parser.parse("""
                {
                  "id": "ext.a", "version": "2.0.0", "apiVersionRange": "[1.0,2.0)",
                  "requiredCapabilities": ["git"],
                  "contributions": [
                    {"type": "SETTING", "id": "editor.tabSize", "priority": 1, "payload": {"default": "8"}},
                    {"type": "PANEL", "id": "ext.a.panel", "payload": {"title": "A"}}
                  ]
                }
                """);
        assertTrue(svc.upgrade(upgraded).committed());
        assertEquals("8", svc.resolve("SETTING:editor.tabSize").winner().contribution().getPayload().get("default"));
        assertEquals("2.0.0", svc.resolve("SETTING:editor.tabSize").winner().extensionVersion());
        assertTrue(svc.contributions().containsKey("PANEL:ext.a.panel"));
        // 同 id 旧版本不应残留
        assertEquals(1, svc.snapshot().activeExtensions().size());
    }

    @Test
    void noMixedStateDuringUpdate() {
        ExtensionHostService svc = service(CompatibilityPolicy.REJECT);
        svc.replaceAll(List.of(settingExt("ext.a", 1, "2")));
        long gen = svc.snapshot().generation();
        svc.replaceAll(List.of(settingExt("ext.a", 1, "2"), settingExt("ext.b", 5, "4")));
        // 代际单调递增，且新快照完整包含两个扩展
        assertEquals(gen + 1, svc.snapshot().generation());
        assertEquals(2, svc.snapshot().activeExtensions().size());
    }

    @Test
    void unknownExtensionDisableRejected() {
        ExtensionHostService svc = service(CompatibilityPolicy.REJECT);
        UpdateResult result = svc.disable("ext.ghost");
        assertFalse(result.committed());
        assertNull(svc.resolve("SETTING:editor.tabSize"));
    }
}
