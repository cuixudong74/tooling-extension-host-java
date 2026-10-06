package com.github.cuixudong74.exthost;

import com.github.cuixudong74.exthost.merge.ContributionMerger;
import com.github.cuixudong74.exthost.merge.EffectiveContribution;
import com.github.cuixudong74.exthost.merge.EffectiveRegistry;
import com.github.cuixudong74.exthost.merge.MergeConfig;
import com.github.cuixudong74.exthost.model.ContributionType;
import com.github.cuixudong74.exthost.model.ExtensionManifest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ContributionMergerTest {

    private final ContributionMerger merger = new ContributionMerger();
    private final MergeConfig config = MergeConfig.defaults();

    private ExtensionManifest withSetting(String extId, int priority, String value) {
        return TestFixtures.manifest(extId, "1.0.0", "[1.0,2.0)", List.of(), priority,
                List.of(TestFixtures.contribution(ContributionType.SETTING, "editor.fontSize", value)));
    }

    @Test
    void higherPriorityWins() {
        EffectiveRegistry r = merger.merge(List.of(withSetting("ext.low", 1, "small"),
                withSetting("ext.high", 10, "large")), config);
        EffectiveContribution ec = r.lookup(ContributionType.SETTING, "editor.fontSize").orElseThrow();
        assertEquals("ext.high", ec.winnerExtensionId());
        assertEquals("large", ec.contribution().payload().get("value"));
        assertEquals(List.of("ext.high", "ext.low"), ec.overriddenBy());
    }

    @Test
    void samePriorityFallsBackToExtensionIdOrder() {
        EffectiveRegistry r = merger.merge(List.of(withSetting("ext.b", 5, "b"),
                withSetting("ext.a", 5, "a")), config);
        assertEquals("ext.a", r.lookup(ContributionType.SETTING, "editor.fontSize")
                .orElseThrow().winnerExtensionId());
    }

    @Test
    void resultIsIndependentOfInputOrder() {
        List<ExtensionManifest> base = List.of(
                withSetting("ext.a", 3, "a"),
                withSetting("ext.b", 7, "b"),
                withSetting("ext.c", 7, "c"));
        EffectiveRegistry reference = merger.merge(base, config);
        List<ExtensionManifest> shuffled = new ArrayList<>(base);
        for (int seed = 1; seed <= 20; seed++) {
            Collections.shuffle(shuffled, new java.util.Random(seed));
            EffectiveRegistry r = merger.merge(shuffled, config);
            assertEquals(reference.all().keySet(), r.all().keySet());
            for (var key : reference.all().keySet()) {
                assertEquals(reference.all().get(key).winnerExtensionId(),
                        r.all().get(key).winnerExtensionId(), "key=" + key);
                assertEquals(reference.all().get(key).overriddenBy(),
                        r.all().get(key).overriddenBy(), "key=" + key);
            }
        }
    }

    @Test
    void differentTypesDoNotConflict() {
        ExtensionManifest m = TestFixtures.manifest("ext.a", "1.0.0", "[1.0,2.0)", List.of(), null,
                List.of(TestFixtures.contribution(ContributionType.SETTING, "x", "s"),
                        TestFixtures.contribution(ContributionType.COMMAND, "x", "c")));
        EffectiveRegistry r = merger.merge(List.of(m), config);
        assertEquals(2, r.all().size());
    }

    @Test
    void nullPriorityUsesDefault() {
        ExtensionManifest noPrio = TestFixtures.manifest("ext.a", "1.0.0", "[1.0,2.0)",
                List.of(), null,
                List.of(TestFixtures.contribution(ContributionType.SETTING, "k", "v")));
        EffectiveRegistry r = merger.merge(List.of(noPrio, withSetting("ext.b", -1, "neg")), config);
        assertEquals("ext.a", r.lookup(ContributionType.SETTING, "k").orElseThrow().winnerExtensionId());
    }
}
