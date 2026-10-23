package com.sunwoda.evb.finance.analysis.domain.repository;

import com.sunwoda.evb.finance.analysis.domain.model.Adjustment;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;

import java.util.List;
import java.util.Optional;

/** 调整单仓储端口：按期间、视角与状态查询，供普通计算叠加层消费。 */
public interface AdjustmentRepository {
    Adjustment save(Adjustment adjustment);

    Optional<Adjustment> findById(String adjustmentId);

    List<Adjustment> findByPeriod(String period);

    List<Adjustment> findActive(String period, AnalysisPerspective perspective);
}
