package com.sunwoda.evb.finance.analysis.api.dto;

public class ResultLifecycleRequest {
    private String batchNo;
    private String operator;

    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String batchNo) { this.batchNo = batchNo; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
}
