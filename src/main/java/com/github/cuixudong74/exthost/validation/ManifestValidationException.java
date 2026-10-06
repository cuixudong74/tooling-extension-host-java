package com.github.cuixudong74.exthost.validation;

import java.util.List;

/** 清单校验失败异常，携带全部错误（不允许只加载一半）。 */
public class ManifestValidationException extends RuntimeException {
    private final List<ManifestError> errors;

    public ManifestValidationException(List<ManifestError> errors) {
        super("manifest validation failed: " + errors);
        this.errors = List.copyOf(errors);
    }

    public List<ManifestError> getErrors() {
        return errors;
    }
}
