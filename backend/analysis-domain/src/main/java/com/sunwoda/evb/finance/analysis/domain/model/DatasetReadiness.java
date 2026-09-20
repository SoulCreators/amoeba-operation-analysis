package com.sunwoda.evb.finance.analysis.domain.model;

public class DatasetReadiness {
    private final String datasetCode;
    private final String datasetName;
    private final boolean required;
    private final boolean present;
    private final String status;
    private final int issueCount;
    private final String taskNo;

    public DatasetReadiness(String datasetCode, String datasetName, boolean required,
                            boolean present, String status, int issueCount, String taskNo) {
        this.datasetCode = datasetCode;
        this.datasetName = datasetName;
        this.required = required;
        this.present = present;
        this.status = status;
        this.issueCount = issueCount;
        this.taskNo = taskNo;
    }

    public String getDatasetCode() { return datasetCode; }
    public String getDatasetName() { return datasetName; }
    public boolean isRequired() { return required; }
    public boolean isPresent() { return present; }
    public String getStatus() { return status; }
    public int getIssueCount() { return issueCount; }
    public String getTaskNo() { return taskNo; }
}
