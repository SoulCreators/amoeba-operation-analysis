package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.CalculationResult;

public interface CalculationService {
    CalculationResult calculate(String batchNo);
    CalculationResult get(String batchNo);
    CalculationResult getForUser(String batchNo, String userId);
    CalculationResult confirm(String batchNo, String operator);
    CalculationResult publish(String batchNo, String operator);
}
