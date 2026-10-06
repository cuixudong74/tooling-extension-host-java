package com.github.cuixudong74.exthost.validation;

/**
 * 清单校验错误，可定位到具体扩展与字段。
 *
 * @param extensionId 出错扩展标识（未知时为 "<unknown>"）
 * @param field       出错字段路径，如 "contributions[2].type"
 * @param reason      拒绝原因
 */
public record ManifestError(String extensionId, String field, String reason) {
    @Override
    public String toString() {
        return "[" + extensionId + "] " + field + ": " + reason;
    }
}
