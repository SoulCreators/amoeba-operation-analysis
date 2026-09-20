package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.BatchReadiness;

public interface BatchReadinessService {
    BatchReadiness get(String batchNo);
}
