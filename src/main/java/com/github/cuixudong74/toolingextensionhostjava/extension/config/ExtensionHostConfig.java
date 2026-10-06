package com.github.cuixudong74.toolingextensionhostjava.extension.config;

import com.github.cuixudong74.toolingextensionhostjava.extension.model.HostProfile;
import com.github.cuixudong74.toolingextensionhostjava.extension.registry.ExtensionHostService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashSet;

/**
 * 宿主装配：由配置项构建核心服务 Bean。
 */
@Configuration
@EnableConfigurationProperties(ExtensionHostProperties.class)
public class ExtensionHostConfig {

    @Bean
    public ExtensionHostService extensionHostService(ExtensionHostProperties properties) {
        HostProfile host = new HostProfile(properties.getApiVersion(),
                new LinkedHashSet<>(properties.getCapabilities()));
        return new ExtensionHostService(host, properties.getCompatibilityPolicy());
    }
}
