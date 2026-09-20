package com.sunwoda.evb.finance.analysis.domain.model;

import java.util.Collections;
import java.util.List;

public class CalculationResult {
    private final String batchNo;
    private final AnalysisPerspective perspective;
    private final List<PnlResult> results;

    public CalculationResult(String batchNo, AnalysisPerspective perspective, List<PnlResult> results) {
        this.batchNo = batchNo;
        this.perspective = perspective;
        this.results = Collections.unmodifiableList(results);
    }

    public String getBatchNo() { return batchNo; }
    public AnalysisPerspective getPerspective() { return perspective; }
    public List<PnlResult> getResults() { return results; }
}
