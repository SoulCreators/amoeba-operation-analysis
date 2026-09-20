package com.sunwoda.evb.finance.analysis.domain.repository;

import com.sunwoda.evb.finance.analysis.domain.model.AiAnalysisDraft;

import java.util.Optional;

public interface AiAnalysisDraftRepository {
    AiAnalysisDraft save(AiAnalysisDraft draft);
    Optional<AiAnalysisDraft> findByBatchNo(String batchNo);
}
