package com.github.cuixudong74.exthost;

import com.github.cuixudong74.exthost.resolution.CompatibilityChecker;
import com.github.cuixudong74.exthost.resolution.CompatibilityIssue;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CompatibilityCheckerTest {

    private final CompatibilityChecker checker = new CompatibilityChecker();

    @Test
    void compatibleExtensionHasNoIssues() {
        assertTrue(checker.check(TestFixtures.valid("ext.a"), TestFixtures.HOST).isEmpty());
    }

    @Test
    void apiVersionOutOfRangeIsReportedWithReason() {
        var m = TestFixtures.manifest("ext.a", "1.0.0", "[2.0,3.0)", List.of(), null, List.of());
        List<CompatibilityIssue> issues = checker.check(m, TestFixtures.HOST);
        assertEquals(1, issues.size());
        assertFalse(issues.get(0).compatible());
        assertTrue(issues.get(0).reason().contains("ext.a"));
        assertTrue(issues.get(0).reason().contains("1.2.0"));
    }

    @Test
    void boundaryVersionsAreInclusiveAsDeclared() {
        var lower = TestFixtures.manifest("e1", "1.0.0", "[1.2,2.0)", List.of(), null, List.of());
        var upper = TestFixtures.manifest("e2", "1.0.0", "(1.1,2.0]", List.of(), null, List.of());
        assertTrue(checker.check(lower, TestFixtures.HOST).isEmpty());
        assertTrue(checker.check(upper, TestFixtures.HOST).isEmpty());
        var exclusive = TestFixtures.manifest("e3", "1.0.0", "(1.2,2.0)", List.of(), null, List.of());
        assertEquals(1, checker.check(exclusive, TestFixtures.HOST).size());
    }

    @Test
    void missingCapabilitiesAreAllReported() {
        var m = TestFixtures.manifest("ext.a", "1.0.0", "[1.0,2.0)",
                List.of("commands", "ghost1", "ghost2"), null, List.of());
        List<CompatibilityIssue> issues = checker.check(m, TestFixtures.HOST);
        assertEquals(1, issues.size());
        assertTrue(issues.get(0).reason().contains("ghost1"));
        assertTrue(issues.get(0).reason().contains("ghost2"));
    }
}
