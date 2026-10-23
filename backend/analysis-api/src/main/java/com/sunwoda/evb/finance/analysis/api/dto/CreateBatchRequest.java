package com.sunwoda.evb.finance.analysis.api.dto;

public class CreateBatchRequest {
    private String batchNo;
    private String period;
    private String perspective;
    private String scopeCode;
    private String createdBy;

    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String batchNo) { this.batchNo = batchNo; }
    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }
    public String getPerspective() { return perspective; }
    public void setPerspective(String perspective) { this.perspective = perspective; }
    public String getScopeCode() { return scopeCode; }
    public void setScopeCode(String scopeCode) { this.scopeCode = scopeCode; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
