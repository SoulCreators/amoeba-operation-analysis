package com.sunwoda.evb.finance.analysis.domain.repository;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;

import java.util.Optional;

public interface AnalysisBatchRepository {
    AnalysisBatch save(AnalysisBatch batch);
    Optional<AnalysisBatch> findByBatchNo(String batchNo);
}
