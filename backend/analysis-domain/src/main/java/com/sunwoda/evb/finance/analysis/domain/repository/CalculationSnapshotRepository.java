package com.sunwoda.evb.finance.analysis.domain.repository;

import com.sunwoda.evb.finance.analysis.domain.model.CalculationSnapshot;

import java.util.Optional;

/**
 * 计算输入快照仓储契约。
 *
 * <p>本次修改：保存并读取不可变计算快照，不在领域端口中引入 JSON 库或数据库类型。</p>
 */
public interface CalculationSnapshotRepository {
    CalculationSnapshot save(CalculationSnapshot snapshot);

    Optional<CalculationSnapshot> findById(String snapshotId);
}
