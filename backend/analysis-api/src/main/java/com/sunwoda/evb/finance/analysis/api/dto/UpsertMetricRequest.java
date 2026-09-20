package com.sunwoda.evb.finance.analysis.api.dto;

public class UpsertMetricRequest {
    private String metricCode;
    private String metricName;
    private String applicablePerspective = "ALL";
    private String unit;
    private String formulaExpression;
    private int displayOrder;
    private boolean enabled = true;
    private String aiSummaryTemplate;
    private String updatedBy;

    public String getMetricCode() { return metricCode; }
    public void setMetricCode(String metricCode) { this.metricCode = metricCode; }
    public String getMetricName() { return metricName; }
    public void setMetricName(String metricName) { this.metricName = metricName; }
    public String getApplicablePerspective() { return applicablePerspective; }
    public void setApplicablePerspective(String applicablePerspective) { this.applicablePerspective = applicablePerspective; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public String getFormulaExpression() { return formulaExpression; }
    public void setFormulaExpression(String formulaExpression) { this.formulaExpression = formulaExpression; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getAiSummaryTemplate() { return aiSummaryTemplate; }
    public void setAiSummaryTemplate(String aiSummaryTemplate) { this.aiSummaryTemplate = aiSummaryTemplate; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
