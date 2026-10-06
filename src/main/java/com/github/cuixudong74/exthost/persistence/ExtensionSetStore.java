package com.github.cuixudong74.exthost.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.cuixudong74.exthost.model.ExtensionManifest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/** 扩展集合的本地 JSON 持久化（仅本地文件，无外部服务）。写入先落临时文件再原子改名。 */
public class ExtensionSetStore {

    private final Path file;
    private final ObjectMapper mapper = new ObjectMapper();

    public ExtensionSetStore(Path file) {
        this.file = file;
    }

    public synchronized void save(List<ExtensionManifest> manifests) {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            mapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), manifests);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to persist extension set to " + file, e);
        }
    }

    public synchronized List<ExtensionManifest> load() {
        if (!Files.exists(file)) {
            return List.of();
        }
        try {
            return mapper.readValue(file.toFile(), new TypeReference<>() {
            });
        } catch (IOException e) {
            throw new UncheckedIOException("failed to load extension set from " + file, e);
        }
    }
}
