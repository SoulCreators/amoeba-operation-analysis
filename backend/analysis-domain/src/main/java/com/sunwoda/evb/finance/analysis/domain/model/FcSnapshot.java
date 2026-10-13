package com.sunwoda.evb.finance.analysis.domain.model;

import java.math.BigDecimal;

/** FC 预测快照中的单个期间、范围和指标值。 */
public class FcSnapshot {
    private final String period;
    private final String scopeCode;
    private final String metricCode;
    private final BigDecimal value;

    public FcSnapshot(String period, String scopeCode, String metricCode, BigDecimal value) {
        this.period = period;
        this.scopeCode = scopeCode;
        this.metricCode = metricCode;
        this.value = value == null ? BigDecimal.ZERO : value;
    }

    public String getPeriod() { return period; }
    public String getScopeCode() { return scopeCode; }
    public String getMetricCode() { return metricCode; }
    public BigDecimal getValue() { return value; }
}
