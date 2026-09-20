package com.sunwoda.evb.finance.analysis.domain.model;

import java.util.Collections;
import java.util.List;

/**
 * Derived quality summary for one batch. It deliberately reports control
 * information only; it never changes the imported business data.
 */
public class DataQualityReport {
    private final String batchNo;
    private final AnalysisPerspective perspective;
    private final String status;
    private final int totalDatasetCount;
    private final int requiredDatasetCount;
    private final int presentRequiredDatasetCount;
    private final int validRequiredDatasetCount;
    private final int issueCount;
    private final List<String> missingDatasetCodes;
    private final List<String> invalidDatasetCodes;
    private final List<DatasetReadiness> datasets;

    public DataQualityReport(String batchNo, AnalysisPerspective perspective, String status,
                             int totalDatasetCount, int requiredDatasetCount,
                             int presentRequiredDatasetCount, int validRequiredDatasetCount,
                             int issueCount, List<String> missingDatasetCodes,
                             List<String> invalidDatasetCodes, List<DatasetReadiness> datasets) {
        this.batchNo = batchNo;
        this.perspective = perspective;
        this.status = status;
        this.totalDatasetCount = totalDatasetCount;
        this.requiredDatasetCount = requiredDatasetCount;
        this.presentRequiredDatasetCount = presentRequiredDatasetCount;
        this.validRequiredDatasetCount = validRequiredDatasetCount;
        this.issueCount = issueCount;
        this.missingDatasetCodes = Collections.unmodifiableList(missingDatasetCodes);
        this.invalidDatasetCodes = Collections.unmodifiableList(invalidDatasetCodes);
        this.datasets = Collections.unmodifiableList(datasets);
    }

    public String getBatchNo() { return batchNo; }
    public AnalysisPerspective getPerspective() { return perspective; }
    public String getStatus() { return status; }
    public int getTotalDatasetCount() { return totalDatasetCount; }
    public int getRequiredDatasetCount() { return requiredDatasetCount; }
    public int getPresentRequiredDatasetCount() { return presentRequiredDatasetCount; }
    public int getValidRequiredDatasetCount() { return validRequiredDatasetCount; }
    public int getIssueCount() { return issueCount; }
    public List<String> getMissingDatasetCodes() { return missingDatasetCodes; }
    public List<String> getInvalidDatasetCodes() { return invalidDatasetCodes; }
    public List<DatasetReadiness> getDatasets() { return datasets; }
}
