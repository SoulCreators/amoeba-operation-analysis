package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.AiAnalysisDraft;

public interface AiAnalysisService {
    AiAnalysisDraft generate(String batchNo, String userId);
    AiAnalysisDraft get(String batchNo, String userId);
    AiAnalysisDraft publish(String batchNo, String userId);
}
