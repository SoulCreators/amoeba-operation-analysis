package com.sunwoda.evb.finance.analysis.application.port;

import com.sunwoda.evb.finance.analysis.domain.model.CalculationResult;
import java.util.function.Function;

/** Durable ordinary calculation boundary: lock, capture inputs, append version and commit together. */
public interface CalculationRunStore {
    CalculationResult execute(String batchNo, String operator, Function<String, CalculationResult> calculation);
    void lockBatch(String batchNo);
    void assertPublishable(CalculationResult result);
}
