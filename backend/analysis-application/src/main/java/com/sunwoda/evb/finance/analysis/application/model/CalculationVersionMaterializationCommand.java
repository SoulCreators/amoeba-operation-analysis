package com.sunwoda.evb.finance.analysis.application.model;

import com.sunwoda.evb.finance.analysis.domain.model.CalculationSnapshot;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationVersion;

/**
 * 计算输入快照与计算版本头物化命令。
 *
 * <p>本次新增：命令只携带调用方已经明确构造好的快照和版本对象，
 * 不生成或推测 snapshotId、versionId、jobId、hash、traceId 等标识及摘要。</p>
 */
public final class CalculationVersionMaterializationCommand {
    private final CalculationSnapshot snapshot;
    private final CalculationVersion version;

    public CalculationVersionMaterializationCommand(CalculationSnapshot snapshot,
                                                    CalculationVersion version) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot 不能为空");
        }
        if (version == null) {
            throw new IllegalArgumentException("version 不能为空");
        }
        this.snapshot = snapshot;
        this.version = version;
    }

    public CalculationSnapshot getSnapshot() {
        return snapshot;
    }

    public CalculationVersion getVersion() {
        return version;
    }
}
