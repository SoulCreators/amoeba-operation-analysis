package com.sunwoda.evb.finance.analysis.domain.repository;

import com.sunwoda.evb.finance.analysis.domain.model.CalculationResult;

import java.util.Optional;

public interface CalculationResultRepository {
    CalculationResult save(CalculationResult result);
    Optional<CalculationResult> findByBatchNo(String batchNo);
}
