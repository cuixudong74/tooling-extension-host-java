package com.github.cuixudong74.exthost.api;

import com.github.cuixudong74.exthost.host.ExtensionHostService;
import com.github.cuixudong74.exthost.merge.ContributionMerger;
import com.github.cuixudong74.exthost.merge.MergeConfig;
import com.github.cuixudong74.exthost.model.HostProfile;
import com.github.cuixudong74.exthost.persistence.ExtensionSetStore;
import com.github.cuixudong74.exthost.resolution.CompatibilityChecker;
import com.github.cuixudong74.exthost.resolution.MismatchPolicy;
import com.github.cuixudong74.exthost.validation.ManifestValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.util.Set;

/** 宿主装配：全部配置来自本地 application.properties，可复现。 */
@Configuration
public class HostConfig {

    @Bean
    public HostProfile hostProfile(
            @Value("${host.api-version:1.0.0}") String apiVersion,
            @Value("${host.capabilities:}") String capabilities) {
        Set<String> caps = capabilities.isBlank()
                ? Set.of()
                : Set.of(capabilities.split(",")).stream()
                        .map(String::trim).filter(s -> !s.isEmpty())
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new HostProfile(apiVersion, caps);
    }

    @Bean
    public ExtensionHostService extensionHostService(
            HostProfile hostProfile,
            @Value("${host.mismatch-policy:REJECT}") MismatchPolicy mismatchPolicy,
            @Value("${host.default-priority:0}") int defaultPriority,
            @Value("${host.store-file:}") String storeFile) {
        ExtensionSetStore store = storeFile.isBlank() ? null : new ExtensionSetStore(Path.of(storeFile));
        ExtensionHostService service = new ExtensionHostService(
                hostProfile, new ManifestValidator(), new CompatibilityChecker(),
                new ContributionMerger(), new MergeConfig(defaultPriority), mismatchPolicy, store);
        if (store != null) {
            var persisted = store.load();
            if (!persisted.isEmpty()) {
                service.replaceAll(persisted);
            }
        }
        return service;
    }
}
