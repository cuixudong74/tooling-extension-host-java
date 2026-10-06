package com.github.cuixudong74.toolingextensionhostjava.extension.merge;

import com.github.cuixudong74.toolingextensionhostjava.extension.model.Contribution;

/**
 * 一条贡献的来源记录：来自哪个扩展、优先级、声明序号。
 */
public record ContributionSource(String extensionId, String extensionVersion,
                                 Contribution contribution, int declarationIndex) {
}
