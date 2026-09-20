package com.sunwoda.evb.finance.analysis.domain.model;

import java.util.Collections;
import java.util.List;

public class BatchReadiness {
    private final String batchNo;
    private final AnalysisPerspective perspective;
    private final boolean readyForCalculation;
    private final List<DatasetReadiness> datasets;

    public BatchReadiness(String batchNo, AnalysisPerspective perspective,
                          boolean readyForCalculation, List<DatasetReadiness> datasets) {
        this.batchNo = batchNo;
        this.perspective = perspective;
        this.readyForCalculation = readyForCalculation;
        this.datasets = Collections.unmodifiableList(datasets);
    }

    public String getBatchNo() { return batchNo; }
    public AnalysisPerspective getPerspective() { return perspective; }
    public boolean isReadyForCalculation() { return readyForCalculation; }
    public List<DatasetReadiness> getDatasets() { return datasets; }
}
