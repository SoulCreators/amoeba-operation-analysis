package com.sunwoda.evb.finance.analysis.domain.repository;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationVersion;

import java.util.List;
import java.util.Optional;

/**
 * 计算结果版本头仓储契约。
 *
 * <p>本次修改：提供版本追加、当前版本和历史版本读取的纯端口，
 * 不规定 JDBC、事务或权限实现。</p>
 */
public interface CalculationVersionRepository {
    CalculationVersion save(CalculationVersion version);

    Optional<CalculationVersion> findById(String calculationVersionId);

    Optional<CalculationVersion> findCurrentByBatchNo(String batchNo, AnalysisPerspective perspective);

    List<CalculationVersion> findByBatchNo(String batchNo, AnalysisPerspective perspective);
}
