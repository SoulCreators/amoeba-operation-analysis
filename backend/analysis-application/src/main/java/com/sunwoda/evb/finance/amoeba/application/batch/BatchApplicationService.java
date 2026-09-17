package com.sunwoda.evb.finance.amoeba.application.batch;

import com.sunwoda.evb.finance.amoeba.domain.batch.model.AnalysisBatch;

/** 批次用例编排入口，后续接入仓储和权限校验。 */
public class BatchApplicationService {
    public AnalysisBatch create(String batchNo, String period) {
        if (batchNo == null || batchNo.trim().isEmpty()) {
            throw new IllegalArgumentException("batchNo不能为空");
        }
        if (period == null || !period.matches("\\d{4}-\\d{2}")) {
            throw new IllegalArgumentException("period格式必须为YYYY-MM");
        }
        return new AnalysisBatch(batchNo, period);
    }
}
