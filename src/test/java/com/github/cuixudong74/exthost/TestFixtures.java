package com.github.cuixudong74.exthost;

import com.github.cuixudong74.exthost.model.Contribution;
import com.github.cuixudong74.exthost.model.ContributionType;
import com.github.cuixudong74.exthost.model.ExtensionManifest;
import com.github.cuixudong74.exthost.model.HostProfile;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** 测试公共构造工具。 */
final class TestFixtures {

    static final HostProfile HOST = new HostProfile("1.2.0",
            Set.of("commands", "settings", "keybindings", "languages", "panels"));

    private TestFixtures() {
    }

    static ExtensionManifest manifest(String id, String version, String apiRange,
                                      List<String> capabilities, Integer priority,
                                      List<Contribution> contributions) {
        return new ExtensionManifest(id, version, apiRange, capabilities, priority, contributions);
    }

    /** 一个默认合法的清单。 */
    static ExtensionManifest valid(String id) {
        return manifest(id, "1.0.0", "[1.0,2.0)", List.of("commands"), null,
                List.of(new Contribution(ContributionType.COMMAND, id + ".cmd",
                        Map.of("title", "cmd from " + id))));
    }

    static Contribution contribution(ContributionType type, String id, String value) {
        return new Contribution(type, id, Map.of("value", value));
    }
}
