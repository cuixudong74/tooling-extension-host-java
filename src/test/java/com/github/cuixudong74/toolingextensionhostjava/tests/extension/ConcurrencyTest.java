package com.github.cuixudong74.toolingextensionhostjava.tests.extension;

import com.github.cuixudong74.toolingextensionhostjava.extension.compat.CompatibilityPolicy;
import com.github.cuixudong74.toolingextensionhostjava.extension.io.ManifestJsonParser;
import com.github.cuixudong74.toolingextensionhostjava.extension.merge.ResolvedContribution;
import com.github.cuixudong74.toolingextensionhostjava.extension.model.ExtensionManifest;
import com.github.cuixudong74.toolingextensionhostjava.extension.model.HostProfile;
import com.github.cuixudong74.toolingextensionhostjava.extension.registry.ExtensionHostService;
import com.github.cuixudong74.toolingextensionhostjava.extension.registry.ExtensionSetSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 并发读写：任一时刻查询都必须落在某个已提交的完整快照上；
 * 并发更新串行提交，代际连续无漂移。
 */
class ConcurrencyTest {

    private final ManifestJsonParser parser = new ManifestJsonParser();
    private final HostProfile host = new HostProfile("1.5.0", Set.of("git"));

    private ExtensionManifest ext(String id, int priority) {
        return parser.parse("""
                {
                  "id": "%s", "version": "1.0.0", "apiVersionRange": "^1.0",
                  "requiredCapabilities": ["git"],
                  "contributions": [
                    {"type": "SETTING", "id": "editor.tabSize", "priority": %d, "payload": {"default": "%d"}}
                  ]
                }
                """.formatted(id, priority, priority));
    }

    @Test
    void concurrentReadsAlwaysSeeConsistentSnapshot() throws Exception {
        ExtensionHostService svc = new ExtensionHostService(host, CompatibilityPolicy.REJECT);
        svc.replaceAll(List.of(ext("ext.0", 0)));

        int writers = 4;
        int readers = 8;
        int writesPerThread = 50;
        ExecutorService writerPool = Executors.newFixedThreadPool(writers);
        ExecutorService readerPool = Executors.newFixedThreadPool(readers);
        CountDownLatch start = new CountDownLatch(1);
        AtomicBoolean stop = new AtomicBoolean(false);
        AtomicLong commits = new AtomicLong();
        ConcurrentLinkedQueue<String> violations = new ConcurrentLinkedQueue<>();

        for (int w = 0; w < writers; w++) {
            int finalW = w;
            writerPool.submit(() -> {
                await(start);
                for (int i = 0; i < writesPerThread; i++) {
                    var result = svc.replaceAll(List.of(
                            ext("ext." + finalW, finalW * 100 + i)));
                    if (result.committed()) {
                        commits.incrementAndGet();
                    }
                }
            });
        }
        for (int r = 0; r < readers; r++) {
            readerPool.submit(() -> {
                await(start);
                long lastGeneration = -1;
                while (!stop.get()) {
                    ExtensionSetSnapshot snap = svc.snapshot();
                    // 代际只能单调递增，不允许回退漂移
                    if (snap.generation() < lastGeneration) {
                        violations.add("generation 回退: " + lastGeneration + " -> " + snap.generation());
                    }
                    lastGeneration = snap.generation();
                    // 快照内部一致性：生效贡献的赢家必须属于该快照的活跃扩展
                    ResolvedContribution rc = snap.resolve("SETTING:editor.tabSize");
                    if (rc != null) {
                        boolean winnerActive = snap.activeExtensions().stream()
                                .anyMatch(m -> m.id().equals(rc.winner().extensionId()));
                        if (!winnerActive) {
                            violations.add("赢家不在活跃集合中: " + rc.winner().extensionId()
                                    + " @gen " + snap.generation());
                        }
                        // 覆盖链中的扩展也必须都在同一快照的活跃集合中
                        for (var s : rc.chain()) {
                            boolean active = snap.activeExtensions().stream()
                                    .anyMatch(m -> m.id().equals(s.extensionId()));
                            if (!active) {
                                violations.add("覆盖链成员不在活跃集合中: " + s.extensionId());
                            }
                        }
                    }
                }
            });
        }

        start.countDown();
        writerPool.shutdown();
        assertTrue(writerPool.awaitTermination(60, TimeUnit.SECONDS));
        stop.set(true);
        readerPool.shutdown();
        assertTrue(readerPool.awaitTermination(60, TimeUnit.SECONDS));

        assertTrue(violations.isEmpty(), () -> "一致性违例: " + violations);
        // 更新串行提交：最终代际 == 初始提交(1) + 成功提交数，无漂移
        assertEquals(1 + commits.get(), svc.snapshot().generation());
    }

    @Test
    void concurrentEnableDisableDoNotLoseUpdates() throws Exception {
        ExtensionHostService svc = new ExtensionHostService(host, CompatibilityPolicy.REJECT);
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicLong commits = new AtomicLong();

        for (int t = 0; t < threads; t++) {
            int finalT = t;
            pool.submit(() -> {
                await(start);
                for (int i = 0; i < 25; i++) {
                    if (svc.enable(ext("ext.t" + finalT, finalT)).committed()) {
                        commits.incrementAndGet();
                    }
                    if (svc.disable("ext.t" + finalT).committed()) {
                        commits.incrementAndGet();
                    }
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS));

        // 每次成功提交代际恰好 +1，最终代际与提交总数严格一致
        assertEquals(commits.get(), svc.snapshot().generation());
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
