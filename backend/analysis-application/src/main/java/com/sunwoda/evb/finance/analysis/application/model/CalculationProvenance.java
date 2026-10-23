package com.sunwoda.evb.finance.analysis.application.model;

import com.sunwoda.evb.finance.analysis.domain.model.CalculationSnapshot;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationVersion;

/**
 * 计算血源只读投影：一条计算版本与其冻结输入快照的成对回放结果。
 *
 * <p>历史上游配置重放（FR-AUD-001）的读取出口。版本头只保存快照引用，
 * 本对象把版本与它实际冻结的配置内容、来源版本集合拼在一起，供审计
 * 按追踪号/版本号还原「这条结果由哪些上游配置与来源计算而来」。快照
 * 内容是不可变的 JSON 原文，本对象不解析、不二次解释。</p>
 */
public final class CalculationProvenance {
    private final CalculationVersion version;
    private final CalculationSnapshot snapshot;

    public CalculationProvenance(CalculationVersion version, CalculationSnapshot snapshot) {
        if (version == null) {
            throw new IllegalArgumentException("version 不能为空");
        }
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot 不能为空");
        }
        if (!version.getSnapshotId().equals(snapshot.getSnapshotId())) {
            throw new IllegalArgumentException("计算版本与输入快照的 snapshotId 不一致");
        }
        this.version = version;
        this.snapshot = snapshot;
    }

    public CalculationVersion getVersion() {
        return version;
    }

    public CalculationSnapshot getSnapshot() {
        return snapshot;
    }
}
