package com.github.cuixudong74.toolingextensionhostjava.tests.extension;

import com.github.cuixudong74.toolingextensionhostjava.extension.io.ManifestJsonParser;
import com.github.cuixudong74.toolingextensionhostjava.extension.merge.ContributionMerger;
import com.github.cuixudong74.toolingextensionhostjava.extension.merge.ResolvedContribution;
import com.github.cuixudong74.toolingextensionhostjava.extension.model.ExtensionManifest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContributionMergerTest {

    private final ManifestJsonParser parser = new ManifestJsonParser();
    private final ContributionMerger merger = new ContributionMerger();

    private ExtensionManifest manifest(String id, int priority, String settingValue) {
        return parser.parse("""
                {
                  "id": "%s", "version": "1.0.0", "apiVersionRange": "^1.0",
                  "contributions": [
                    {"type": "SETTING", "id": "editor.tabSize", "priority": %d,
                     "payload": {"default": "%s"}}
                  ]
                }
                """.formatted(id, priority, settingValue));
    }

    @Test
    void higherPriorityWins() {
        ExtensionManifest low = manifest("ext.low", 1, "2");
        ExtensionManifest high = manifest("ext.high", 10, "4");
        Map<String, ResolvedContribution> merged = merger.merge(List.of(low, high));
        ResolvedContribution r = merged.get("SETTING:editor.tabSize");
        assertEquals("ext.high", r.winner().extensionId());
        assertEquals("4", r.winner().contribution().getPayload().get("default"));
        assertEquals(1, r.overridden().size());
        assertEquals("ext.low", r.overridden().get(0).extensionId());
    }

    @Test
    void samePriorityBreaksTieByExtensionId() {
        ExtensionManifest b = manifest("ext.b", 5, "b-value");
        ExtensionManifest a = manifest("ext.a", 5, "a-value");
        Map<String, ResolvedContribution> merged = merger.merge(List.of(b, a));
        assertEquals("ext.a", merged.get("SETTING:editor.tabSize").winner().extensionId());
    }

    @Test
    void resultIndependentOfInputOrder() {
        List<ExtensionManifest> base = List.of(
                manifest("ext.a", 3, "a"),
                manifest("ext.b", 7, "b"),
                manifest("ext.c", 7, "c"),
                manifest("ext.d", 1, "d"));
        Map<String, ResolvedContribution> expected = merger.merge(base);
        List<ExtensionManifest> shuffled = new ArrayList<>(base);
        for (int seed = 0; seed < 20; seed++) {
            Collections.shuffle(shuffled, new java.util.Random(seed));
            Map<String, ResolvedContribution> actual = merger.merge(shuffled);
            assertEquals(expected.keySet(), actual.keySet());
            for (String key : expected.keySet()) {
                assertEquals(expected.get(key).winner().extensionId(), actual.get(key).winner().extensionId());
                assertEquals(expected.get(key).chain().stream().map(s -> s.extensionId()).toList(),
                        actual.get(key).chain().stream().map(s -> s.extensionId()).toList());
            }
        }
    }

    @Test
    void differentTypesWithSameIdDoNotConflict() {
        ExtensionManifest m = parser.parse("""
                {
                  "id": "ext.multi", "version": "1.0.0", "apiVersionRange": "^1.0",
                  "contributions": [
                    {"type": "COMMAND", "id": "format", "payload": {}},
                    {"type": "KEYBINDING", "id": "format", "payload": {"key": "ctrl+alt+f"}}
                  ]
                }
                """);
        Map<String, ResolvedContribution> merged = merger.merge(List.of(m));
        assertEquals(2, merged.size());
        assertEquals("ext.multi", merged.get("COMMAND:format").winner().extensionId());
        assertEquals("ext.multi", merged.get("KEYBINDING:format").winner().extensionId());
    }
}
