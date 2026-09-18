package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.BatchStatus;

public interface BatchService {
    AnalysisBatch create(String batchNo, String period, AnalysisPerspective perspective, String createdBy);
    AnalysisBatch get(String batchNo);
    AnalysisBatch move(String batchNo, BatchStatus status);
}
