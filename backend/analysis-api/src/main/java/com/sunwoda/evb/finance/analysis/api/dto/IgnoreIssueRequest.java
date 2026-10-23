package com.sunwoda.evb.finance.analysis.api.dto;

/** 异常工作台受控忽略请求：忽略原因。 */
public class IgnoreIssueRequest {
    private String reason;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
