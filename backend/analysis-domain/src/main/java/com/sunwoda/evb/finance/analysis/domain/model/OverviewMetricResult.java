package com.sunwoda.evb.finance.analysis.domain.model;

import java.math.BigDecimal;

public class OverviewMetricResult {
    private final String metricCode;
    private final String metricName;
    private final String unit;
    private final BigDecimal actual;
    private final BigDecimal budget;
    private final BigDecimal gap;
    private final BigDecimal previous;
    private final String aiSummary;

    public OverviewMetricResult(String metricCode, String metricName, String unit,
                                BigDecimal actual, BigDecimal budget,
                                BigDecimal gap, BigDecimal previous, String aiSummary) {
        this.metricCode = metricCode;
        this.metricName = metricName;
        this.unit = unit;
        this.actual = actual;
        this.budget = budget;
        this.gap = gap;
        this.previous = previous;
        this.aiSummary = aiSummary;
    }

    public String getMetricCode() { return metricCode; }
    public String getMetricName() { return metricName; }
    public String getUnit() { return unit; }
    public BigDecimal getActual() { return actual; }
    public BigDecimal getBudget() { return budget; }
    public BigDecimal getGap() { return gap; }
    public BigDecimal getPrevious() { return previous; }
    public String getAiSummary() { return aiSummary; }
}
