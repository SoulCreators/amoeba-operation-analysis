package com.sunwoda.evb.finance.analysis.domain.model;

import java.time.LocalDateTime;

public class MetricDefinition {
    private final Long id;
    private final String metricCode;
    private String metricName;
    private String applicablePerspective;
    private String unit;
    private String formulaExpression;
    private int displayOrder;
    private boolean enabled;
    private String aiSummaryTemplate;
    private String updatedBy;
    private LocalDateTime updatedAt;

    public MetricDefinition(Long id, String metricCode, String metricName,
                            String applicablePerspective, String unit,
                            String formulaExpression, int displayOrder,
                            boolean enabled, String aiSummaryTemplate,
                            String updatedBy, LocalDateTime updatedAt) {
        this.id = id;
        this.metricCode = metricCode;
        this.metricName = metricName;
        this.applicablePerspective = applicablePerspective;
        this.unit = unit;
        this.formulaExpression = formulaExpression;
        this.displayOrder = displayOrder;
        this.enabled = enabled;
        this.aiSummaryTemplate = aiSummaryTemplate;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
    }

    public void update(String metricName, String applicablePerspective, String unit,
                       String formulaExpression, int displayOrder, boolean enabled,
                       String aiSummaryTemplate, String updatedBy) {
        if (metricName == null || metricName.trim().isEmpty()) throw new IllegalArgumentException("指标名称不能为空");
        if (formulaExpression == null || formulaExpression.trim().isEmpty()) throw new IllegalArgumentException("指标公式不能为空");
        if (updatedBy == null || updatedBy.trim().isEmpty()) throw new IllegalArgumentException("更新人不能为空");
        this.metricName = metricName;
        this.applicablePerspective = applicablePerspective;
        this.unit = unit;
        this.formulaExpression = formulaExpression;
        this.displayOrder = displayOrder;
        this.enabled = enabled;
        this.aiSummaryTemplate = aiSummaryTemplate;
        this.updatedBy = updatedBy;
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getMetricCode() { return metricCode; }
    public String getMetricName() { return metricName; }
    public String getApplicablePerspective() { return applicablePerspective; }
    public String getUnit() { return unit; }
    public String getFormulaExpression() { return formulaExpression; }
    public int getDisplayOrder() { return displayOrder; }
    public boolean isEnabled() { return enabled; }
    public String getAiSummaryTemplate() { return aiSummaryTemplate; }
    public String getUpdatedBy() { return updatedBy; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
