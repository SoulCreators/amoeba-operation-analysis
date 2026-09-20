package com.sunwoda.evb.finance.analysis.api.dto;

public class MoveImportTaskRequest {
    private String status;
    private int issueCount;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getIssueCount() { return issueCount; }
    public void setIssueCount(int issueCount) { this.issueCount = issueCount; }
}
