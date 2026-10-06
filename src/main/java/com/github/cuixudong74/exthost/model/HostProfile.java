package com.github.cuixudong74.exthost.model;

import java.util.Set;

/**
 * 宿主自身信息。
 *
 * @param apiVersion          宿主当前接口版本
 * @param providedCapabilities 宿主实际提供的能力标识集合
 */
public record HostProfile(String apiVersion, Set<String> providedCapabilities) {
}
