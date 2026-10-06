package com.github.cuixudong74.toolingextensionhostjava.tests.extension;

import com.github.cuixudong74.toolingextensionhostjava.extension.version.VersionRange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionRangeTest {

    @Test
    void exactVersion() {
        VersionRange r = VersionRange.parse("1.2.3");
        assertTrue(r.contains("1.2.3"));
        assertFalse(r.contains("1.2.4"));
        assertFalse(r.contains("1.2.2"));
    }

    @Test
    void interval() {
        VersionRange r = VersionRange.parse("[1.0,2.0)");
        assertTrue(r.contains("1.0"));
        assertTrue(r.contains("1.9.9"));
        assertFalse(r.contains("2.0"));
        assertFalse(r.contains("0.9"));
    }

    @Test
    void caretAndTilde() {
        assertTrue(VersionRange.parse("^1.2").contains("1.9.0"));
        assertFalse(VersionRange.parse("^1.2").contains("2.0.0"));
        assertTrue(VersionRange.parse("~1.2").contains("1.2.9"));
        assertFalse(VersionRange.parse("~1.2").contains("1.3.0"));
    }

    @Test
    void comparisonCombo() {
        VersionRange r = VersionRange.parse(">=1.0 <2.0");
        assertTrue(r.contains("1.5"));
        assertFalse(r.contains("2.0"));
        assertFalse(r.contains("0.9"));
    }

    @Test
    void invalidExpressionRejected() {
        assertThrows(IllegalArgumentException.class, () -> VersionRange.parse("abc"));
        assertThrows(IllegalArgumentException.class, () -> VersionRange.parse(""));
    }
}
