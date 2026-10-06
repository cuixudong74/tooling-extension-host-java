package com.github.cuixudong74.exthost;

import com.github.cuixudong74.exthost.host.ExtensionHostService;
import com.github.cuixudong74.exthost.host.ExtensionSetSnapshot;
import com.github.cuixudong74.exthost.merge.ContributionMerger;
import com.github.cuixudong74.exthost.merge.MergeConfig;
import com.github.cuixudong74.exthost.model.ContributionType;
import com.github.cuixudong74.exthost.model.ExtensionManifest;
import com.github.cuixudong74.exthost.resolution.CompatibilityChecker;
import com.github.cuixudong74.exthost.resolution.MismatchPolicy;
import com.github.cuixudong74.exthost.validation.ManifestValidator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/** 并发读写：查询永远落在某个完整快照上，更新之间不互相覆盖。 */
class ConcurrencyTest {

    private ExtensionHostService newService() {
        return new ExtensionHostService(TestFixtures.HOST, new ManifestValidator(),
                new CompatibilityChecker(), new ContributionMerger(),
                MergeConfig.defaults(), MismatchPolicy.REJECT, null);
    }

    private ExtensionManifest ext(String id, int priority) {
        return TestFixtures.manifest(id, "1.0.0", "[1.0,2.0)", List.of(), priority,
                List.of(TestFixtures.contribution(ContributionType.SETTING, "k", id)));
    }

    @Test
    void readersAlwaysSeeAConsistentCommittedSnapshot() throws Exception {
        ExtensionHostService svc = newService();
        svc.replaceAll(List.of(ext("ext.0", 0)));
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        AtomicBoolean inconsistent = new AtomicBoolean(false);

        // 4 个写线程不断整体替换集合
        List<Future<?>> writers = new ArrayList<>();
        for (int w = 0; w < 4; w++) {
            int base = w * 100;
            writers.add(pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 1; i <= 50; i++) {
                        svc.replaceAll(List.of(ext("ext." + (base + i), base + i)));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        // 4 个读线程持续校验快照内部一致性：代次、扩展集合、注册表必须互相对应
        List<Future<?>> readers = new ArrayList<>();
        for (int r = 0; r < 4; r++) {
            readers.add(pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < 2000; i++) {
                        ExtensionSetSnapshot snap = svc.snapshot();
                        if (snap.getExtensions().size() != 1) {
                            inconsistent.set(true);
                            return;
                        }
                        String expectedId = snap.getExtensions().get(0).id();
                        var ec = snap.getRegistry().lookup(ContributionType.SETTING, "k");
                        if (ec.isEmpty()
                                || !ec.get().winnerExtensionId().equals(expectedId)
                                || !ec.get().contribution().payload().get("value").equals(expectedId)) {
                            inconsistent.set(true); // 新旧混合
                            return;
                        }
                        // explain 与快照一致
                        var ex = svc.explain(ContributionType.SETTING, "k");
                        if (ex.effective() == null) {
                            inconsistent.set(true);
                            return;
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }
        start.countDown();
        for (Future<?> f : writers) f.get(60, TimeUnit.SECONDS);
        for (Future<?> f : readers) f.get(60, TimeUnit.SECONDS);
        pool.shutdownNow();
        assertFalse(inconsistent.get(), "reader observed a mixed snapshot");
    }

    @Test
    void concurrentUpdatesDoNotLoseCommits() throws Exception {
        ExtensionHostService svc = newService();
        int threads = 8, perThread = 25;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            int base = t * 1000;
            futures.add(pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        assertTrue(svc.enable(ext("ext." + (base + i), 1)).committed());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }
        start.countDown();
        for (Future<?> f : futures) f.get(60, TimeUnit.SECONDS);
        pool.shutdownNow();
        // 所有 enable 都成功且互不覆盖：最终集合包含全部扩展，代次等于提交次数
        assertEquals(threads * perThread, svc.snapshot().getExtensions().size());
        assertEquals(threads * perThread, svc.snapshot().getGeneration());
    }
}
