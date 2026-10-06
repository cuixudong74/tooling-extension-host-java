package com.github.cuixudong74.toolingextensionhostjava.tests.extension;

import com.github.cuixudong74.toolingextensionhostjava.extension.io.ManifestJsonParser;
import com.github.cuixudong74.toolingextensionhostjava.extension.model.ExtensionManifest;
import com.github.cuixudong74.toolingextensionhostjava.extension.model.HostProfile;
import com.github.cuixudong74.toolingextensionhostjava.extension.validation.ManifestValidationException;
import com.github.cuixudong74.toolingextensionhostjava.extension.validation.ManifestValidator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManifestValidatorTest {

    private final ManifestJsonParser parser = new ManifestJsonParser();
    private final ManifestValidator validator = new ManifestValidator();
    private final HostProfile host = new HostProfile("1.5.0", Set.of("git", "docker"));

    private String validManifest(String id) {
        return """
                {
                  "id": "%s",
                  "version": "1.0.0",
                  "apiVersionRange": "[1.0,2.0)",
                  "requiredCapabilities": ["git"],
                  "contributions": [
                    {"type": "COMMAND", "id": "hello", "priority": 1, "payload": {"title": "Hi"}}
                  ]
                }
                """.formatted(id);
    }

    @Test
    void validManifestPasses() {
        ExtensionManifest m = parser.parse(validManifest("ext.a"));
        assertDoesNotThrow(() -> validator.validateAll(List.of(m), host));
    }

    @Test
    void missingRequiredFieldsRejectedWithLocation() {
        ExtensionManifest m = parser.parse("""
                {"id": "ext.bad", "contributions": [{"type": "COMMAND", "id": "x"}]}
                """);
        ManifestValidationException e = assertThrows(ManifestValidationException.class,
                () -> validator.validateAll(List.of(m), host));
        assertTrue(e.getErrors().stream().anyMatch(s -> s.contains("ext.bad.version")));
        assertTrue(e.getErrors().stream().anyMatch(s -> s.contains("ext.bad.apiVersionRange")));
    }

    @Test
    void illegalContributionTypeRejected() {
        ExtensionManifest m = parser.parse("""
                {
                  "id": "ext.badtype", "version": "1.0.0", "apiVersionRange": "^1.0",
                  "contributions": [{"type": "WIDGET", "id": "w1"}]
                }
                """);
        ManifestValidationException e = assertThrows(ManifestValidationException.class,
                () -> validator.validateAll(List.of(m), host));
        assertTrue(e.getErrors().stream().anyMatch(s -> s.contains("ext.badtype.contributions[0].type")));
    }

    @Test
    void unknownCapabilityReferenceRejected() {
        ExtensionManifest m = parser.parse("""
                {
                  "id": "ext.badcap", "version": "1.0.0", "apiVersionRange": "^1.0",
                  "requiredCapabilities": ["nonexistent-cap"],
                  "contributions": [{"type": "PANEL", "id": "p1"}]
                }
                """);
        ManifestValidationException e = assertThrows(ManifestValidationException.class,
                () -> validator.validateAll(List.of(m), host));
        assertTrue(e.getErrors().stream().anyMatch(s -> s.contains("ext.badcap.requiredCapabilities")
                && s.contains("nonexistent-cap")));
    }

    @Test
    void duplicateExtensionIdRejected() {
        ExtensionManifest a = parser.parse(validManifest("ext.dup"));
        ExtensionManifest b = parser.parse(validManifest("ext.dup"));
        ManifestValidationException e = assertThrows(ManifestValidationException.class,
                () -> validator.validateAll(List.of(a, b), host));
        assertTrue(e.getErrors().stream().anyMatch(s -> s.contains("重复")));
    }

    @Test
    void malformedJsonRejected() {
        assertThrows(Exception.class, () -> parser.parse("{not json"));
    }
}
