package com.github.cuixudong74.toolingextensionhostjava.extension.config;

import com.github.cuixudong74.toolingextensionhostjava.extension.compat.CompatibilityPolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 宿主可配置项。
 */
@ConfigurationProperties(prefix = "extension-host")
public class ExtensionHostProperties {
    /** 宿主接口版本。 */
    private String apiVersion = "1.0.0";
    /** 兼容性策略。 */
    private CompatibilityPolicy compatibilityPolicy = CompatibilityPolicy.REJECT;
    /** 宿主可提供的能力标识。 */
    private List<String> capabilities = List.of();

    public String getApiVersion() { return apiVersion; }
    public void setApiVersion(String apiVersion) { this.apiVersion = apiVersion; }
    public CompatibilityPolicy getCompatibilityPolicy() { return compatibilityPolicy; }
    public void setCompatibilityPolicy(CompatibilityPolicy compatibilityPolicy) { this.compatibilityPolicy = compatibilityPolicy; }
    public List<String> getCapabilities() { return capabilities; }
    public void setCapabilities(List<String> capabilities) { this.capabilities = capabilities; }
}
