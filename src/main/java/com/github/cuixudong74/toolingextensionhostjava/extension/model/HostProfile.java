package com.github.cuixudong74.toolingextensionhostjava.extension.model;

import java.util.Set;

/**
 * 宿主自身信息：接口版本与可提供的能力标识集合。
 */
public record HostProfile(String apiVersion, Set<String> capabilities) {
    public HostProfile {
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
    }
}
