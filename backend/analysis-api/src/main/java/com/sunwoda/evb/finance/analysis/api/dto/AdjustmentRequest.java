package com.sunwoda.evb.finance.analysis.api.dto;

import java.math.BigDecimal;

public class AdjustmentRequest {
    private String period;
    private String perspective;
    private String scope;
    private String scenario;
    private String metricCode;
    private BigDecimal amount;
    private String direction;
    private String reason;
    private String evidenceFileId;
    private String expectedVersion;

    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }
    public String getPerspective() { return perspective; }
    public void setPerspective(String perspective) { this.perspective = perspective; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public String getScenario() { return scenario; }
    public void setScenario(String scenario) { this.scenario = scenario; }
    public String getMetricCode() { return metricCode; }
    public void setMetricCode(String metricCode) { this.metricCode = metricCode; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getEvidenceFileId() { return evidenceFileId; }
    public void setEvidenceFileId(String evidenceFileId) { this.evidenceFileId = evidenceFileId; }
    public String getExpectedVersion() { return expectedVersion; }
    public void setExpectedVersion(String expectedVersion) { this.expectedVersion = expectedVersion; }
}
