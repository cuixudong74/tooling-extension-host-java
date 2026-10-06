package com.github.cuixudong74.exthost;

import com.github.cuixudong74.exthost.model.Contribution;
import com.github.cuixudong74.exthost.model.ContributionType;
import com.github.cuixudong74.exthost.model.ExtensionManifest;
import com.github.cuixudong74.exthost.validation.ManifestError;
import com.github.cuixudong74.exthost.validation.ManifestValidationException;
import com.github.cuixudong74.exthost.validation.ManifestValidator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ManifestValidatorTest {

    private final ManifestValidator validator = new ManifestValidator();

    @Test
    void validManifestPasses() {
        assertTrue(validator.validate(TestFixtures.valid("ext.a"), TestFixtures.HOST).isEmpty());
    }

    @Test
    void missingRequiredFieldsAreLocated() {
        ExtensionManifest m = new ExtensionManifest(null, null, null, null, null, null);
        List<ManifestError> errors = validator.validate(m, TestFixtures.HOST);
        assertEquals(3, errors.size());
        assertTrue(errors.stream().anyMatch(e -> e.field().equals("id")));
        assertTrue(errors.stream().anyMatch(e -> e.field().equals("version")));
        assertTrue(errors.stream().anyMatch(e -> e.field().equals("apiVersionRange")));
    }

    @Test
    void illegalContributionTypeIsRejected() {
        ExtensionManifest m = TestFixtures.manifest("ext.a", "1.0.0", "[1.0,2.0)",
                List.of(), null,
                List.of(new Contribution(null, "x", Map.of())));
        List<ManifestError> errors = validator.validate(m, TestFixtures.HOST);
        assertTrue(errors.stream().anyMatch(e -> e.field().equals("contributions[0].type")));
    }

    @Test
    void unknownCapabilityReferenceIsRejected() {
        ExtensionManifest m = TestFixtures.manifest("ext.a", "1.0.0", "[1.0,2.0)",
                List.of("no-such-capability"), null, List.of());
        List<ManifestError> errors = validator.validate(m, TestFixtures.HOST);
        assertEquals(1, errors.size());
        assertEquals("ext.a", errors.get(0).extensionId());
        assertEquals("requiredCapabilities[0]", errors.get(0).field());
        assertTrue(errors.get(0).reason().contains("no-such-capability"));
    }

    @Test
    void malformedVersionAndRangeAreRejected() {
        ExtensionManifest m = TestFixtures.manifest("ext.a", "1.x", "[2.0,1.0)",
                List.of(), null, List.of());
        List<ManifestError> errors = validator.validate(m, TestFixtures.HOST);
        assertTrue(errors.stream().anyMatch(e -> e.field().equals("version")));
        assertTrue(errors.stream().anyMatch(e -> e.field().equals("apiVersionRange")));
    }

    @Test
    void duplicateContributionIdWithinExtensionIsRejected() {
        ExtensionManifest m = TestFixtures.manifest("ext.a", "1.0.0", "[1.0,2.0)",
                List.of(), null,
                List.of(TestFixtures.contribution(ContributionType.SETTING, "s", "1"),
                        TestFixtures.contribution(ContributionType.SETTING, "s", "2")));
        assertFalse(validator.validate(m, TestFixtures.HOST).isEmpty());
    }

    @Test
    void validateAllRejectsWholeSetWhenAnyManifestIsInvalid() {
        ExtensionManifest bad = TestFixtures.manifest("ext.bad", "1.0.0", "[1.0,2.0)",
                List.of("ghost"), null, List.of());
        ManifestValidationException ex = assertThrows(ManifestValidationException.class,
                () -> validator.validateAll(List.of(TestFixtures.valid("ext.good"), bad),
                        TestFixtures.HOST));
        assertEquals(1, ex.getErrors().size());
        assertEquals("ext.bad", ex.getErrors().get(0).extensionId());
    }

    @Test
    void duplicateExtensionIdInSetIsRejected() {
        assertThrows(ManifestValidationException.class, () -> validator.validateAll(
                List.of(TestFixtures.valid("ext.a"), TestFixtures.valid("ext.a")),
                TestFixtures.HOST));
    }
}
