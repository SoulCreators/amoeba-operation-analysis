package com.sunwoda.evb.finance.analysis.domain.model;

import java.math.BigDecimal;

public class MetricResult {
    private final String scopeCode;
    private final String metricCode;
    private final String metricName;
    private final String unit;
    private final BigDecimal actualValue;
    private final BigDecimal budgetValue;
    private final BigDecimal gapValue;
    private final String aiSummary;

    public MetricResult(String scopeCode, String metricCode, String metricName, String unit,
                        BigDecimal actualValue, BigDecimal budgetValue, BigDecimal gapValue,
                        String aiSummary) {
        this.scopeCode = scopeCode;
        this.metricCode = metricCode;
        this.metricName = metricName;
        this.unit = unit;
        this.actualValue = actualValue;
        this.budgetValue = budgetValue;
        this.gapValue = gapValue;
        this.aiSummary = aiSummary;
    }

    public String getScopeCode() { return scopeCode; }
    public String getMetricCode() { return metricCode; }
    public String getMetricName() { return metricName; }
    public String getUnit() { return unit; }
    public BigDecimal getActualValue() { return actualValue; }
    public BigDecimal getBudgetValue() { return budgetValue; }
    public BigDecimal getGapValue() { return gapValue; }
    public String getAiSummary() { return aiSummary; }
}
