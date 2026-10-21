package com.sunwoda.evb.finance.analysis.domain.repository;

import com.sunwoda.evb.finance.analysis.domain.model.CalculationVersion;
import com.sunwoda.evb.finance.analysis.domain.model.ManualCalculationRevision;

import java.util.Optional;

/**
 * 历史版本人工修订追加仓储契约。
 *
 * <p>本次修改：要求实现以一个事务追加目标 calculation_version 和修订关系，
 * 同时把来源版本转为非当前/已替代状态；来源版本的结果业务数据不得被覆盖。
 * 具体 SQL、锁和事务实现由基础设施层负责。</p>
 */
public interface ManualCalculationRevisionRepository {
    CalculationVersion append(CalculationVersion sourceVersion,
                              CalculationVersion targetVersion,
                              ManualCalculationRevision revision);

    Optional<ManualCalculationRevision> findByTargetVersionId(String targetVersionId);
}
