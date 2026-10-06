package com.github.cuixudong74.toolingextensionhostjava.extension.validation;

import java.util.List;

/**
 * 清单校验失败异常，携带可定位到扩展与字段的错误列表。
 */
public class ManifestValidationException extends RuntimeException {
    private final List<String> errors;

    public ManifestValidationException(List<String> errors) {
        super(String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> getErrors() { return errors; }
}
