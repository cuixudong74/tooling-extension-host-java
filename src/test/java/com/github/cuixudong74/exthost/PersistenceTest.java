package com.github.cuixudong74.exthost;

import com.github.cuixudong74.exthost.host.ExtensionHostService;
import com.github.cuixudong74.exthost.merge.ContributionMerger;
import com.github.cuixudong74.exthost.merge.MergeConfig;
import com.github.cuixudong74.exthost.model.ContributionType;
import com.github.cuixudong74.exthost.model.ExtensionManifest;
import com.github.cuixudong74.exthost.persistence.ExtensionSetStore;
import com.github.cuixudong74.exthost.resolution.CompatibilityChecker;
import com.github.cuixudong74.exthost.resolution.MismatchPolicy;
import com.github.cuixudong74.exthost.validation.ManifestValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PersistenceTest {

    @TempDir
    Path dir;

    private ExtensionHostService service(ExtensionSetStore store) {
        return new ExtensionHostService(TestFixtures.HOST, new ManifestValidator(),
                new CompatibilityChecker(), new ContributionMerger(),
                MergeConfig.defaults(), MismatchPolicy.REJECT, store);
    }

    @Test
    void committedSetIsPersistedAndReloadable() {
        ExtensionSetStore store = new ExtensionSetStore(dir.resolve("set.json"));
        ExtensionHostService svc = service(store);
        ExtensionManifest m = TestFixtures.manifest("ext.a", "1.0.0", "[1.0,2.0)",
                List.of("commands"), 3,
                List.of(TestFixtures.contribution(ContributionType.COMMAND, "c1", "run")));
        assertTrue(svc.replaceAll(List.of(m)).committed());

        List<ExtensionManifest> loaded = store.load();
        assertEquals(1, loaded.size());
        assertEquals("ext.a", loaded.get(0).id());
        assertEquals(3, loaded.get(0).priority());
        assertEquals("run", loaded.get(0).contributions().get(0).payload().get("value"));

        // 用加载结果重建宿主，结论可复现
        ExtensionHostService restored = service(null);
        assertTrue(restored.replaceAll(loaded).committed());
        assertEquals("ext.a", restored.snapshot().getRegistry()
                .lookup(ContributionType.COMMAND, "c1").orElseThrow().winnerExtensionId());
    }

    @Test
    void loadFromMissingFileReturnsEmpty() {
        assertTrue(new ExtensionSetStore(dir.resolve("nope.json")).load().isEmpty());
    }
}
