package com.sunwoda.evb.finance.analysis.api.dto;

import java.util.ArrayList;
import java.util.List;

/** HTTP request for submitting a permission-scoped result export task. */
public class CreateExportRequest {
    private String requestKey;
    private String batchNo;
    private String resultVersion;
    private String perspective;
    private String format;
    private String resultStatus;
    private List<String> requestedScopes = new ArrayList<String>();

    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String requestKey) { this.requestKey = requestKey; }
    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String batchNo) { this.batchNo = batchNo; }
    public String getResultVersion() { return resultVersion; }
    public void setResultVersion(String resultVersion) { this.resultVersion = resultVersion; }
    public String getPerspective() { return perspective; }
    public void setPerspective(String perspective) { this.perspective = perspective; }
    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
    public String getResultStatus() { return resultStatus; }
    public void setResultStatus(String resultStatus) { this.resultStatus = resultStatus; }
    public List<String> getRequestedScopes() { return requestedScopes; }
    public void setRequestedScopes(List<String> requestedScopes) { this.requestedScopes = requestedScopes; }
}
