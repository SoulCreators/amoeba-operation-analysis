package com.sunwoda.evb.finance.analysis.domain.repository;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public interface AnalysisBatchRepository {
    AnalysisBatch save(AnalysisBatch batch);
    Optional<AnalysisBatch> findByBatchNo(String batchNo);

    /**
     * 列出同一视角下 period 在 [fromPeriod, toPeriod]（含边界，YYYY-MM 字典序即时间序）的批次，
     * 按 period 升序返回。用于累计口径按当年 1 月至目标月汇总。轻量/测试实现可保留空列表默认值。
     */
    default List<AnalysisBatch> findByPerspectiveAndPeriodRange(AnalysisPerspective perspective,
                                                                String fromPeriod,
                                                                String toPeriod) {
        return Collections.emptyList();
    }
}
