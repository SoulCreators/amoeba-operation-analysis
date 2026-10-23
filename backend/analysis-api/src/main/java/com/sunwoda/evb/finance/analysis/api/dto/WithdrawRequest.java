package com.sunwoda.evb.finance.analysis.api.dto;

import java.util.List;

public class WithdrawRequest {
    private String batchNo;
    private String reason;
    private List<String> notificationTargets;

    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String batchNo) { this.batchNo = batchNo; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public List<String> getNotificationTargets() { return notificationTargets; }
    public void setNotificationTargets(List<String> notificationTargets) { this.notificationTargets = notificationTargets; }
}
