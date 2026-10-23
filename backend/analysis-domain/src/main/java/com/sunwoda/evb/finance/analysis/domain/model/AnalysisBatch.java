package com.sunwoda.evb.finance.analysis.domain.model;

import java.time.LocalDateTime;

public class AnalysisBatch {
    private final Long id;
    private final String batchNo;
    private final String period;
    private final AnalysisPerspective perspective;
    private final String scopeCode;
    private BatchStatus status;
    private final String createdBy;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 兼容既有调用：未显式绑定范围时默认覆盖整个视角（ALL）。 */
    public AnalysisBatch(Long id, String batchNo, String period,
                         AnalysisPerspective perspective, BatchStatus status,
                         String createdBy, LocalDateTime createdAt) {
        this(id, batchNo, period, perspective, "ALL", status, createdBy, createdAt);
    }

    public AnalysisBatch(Long id, String batchNo, String period,
                         AnalysisPerspective perspective, String scopeCode,
                         BatchStatus status, String createdBy, LocalDateTime createdAt) {
        this.id = id;
        this.batchNo = batchNo;
        this.period = period;
        this.perspective = perspective;
        this.scopeCode = normalizeScope(scopeCode);
        this.status = status;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    private static String normalizeScope(String scopeCode) {
        return (scopeCode == null || scopeCode.trim().isEmpty()) ? "ALL" : scopeCode.trim();
    }

    public void moveTo(BatchStatus next) {
        BatchLifecyclePolicy.validate(status, next);
        if (status == next) return;
        this.status = next;
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getBatchNo() { return batchNo; }
    public String getPeriod() { return period; }
    public AnalysisPerspective getPerspective() { return perspective; }
    public String getScopeCode() { return scopeCode; }
    public BatchStatus getStatus() { return status; }
    public String getCreatedBy() { return createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
