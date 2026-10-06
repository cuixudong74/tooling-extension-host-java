package com.github.cuixudong74.toolingextensionhostjava.extension.io;

/**
 * 清单 JSON 解析失败异常。
 */
public class ManifestParseException extends RuntimeException {
    public ManifestParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
