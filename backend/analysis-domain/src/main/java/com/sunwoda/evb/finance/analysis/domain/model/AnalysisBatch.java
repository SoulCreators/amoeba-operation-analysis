package com.sunwoda.evb.finance.analysis.domain.model;

import java.time.LocalDateTime;

public class AnalysisBatch {
    private final Long id;
    private final String batchNo;
    private final String period;
    private final AnalysisPerspective perspective;
    private BatchStatus status;
    private final String createdBy;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public AnalysisBatch(Long id, String batchNo, String period,
                         AnalysisPerspective perspective, BatchStatus status,
                         String createdBy, LocalDateTime createdAt) {
        this.id = id;
        this.batchNo = batchNo;
        this.period = period;
        this.perspective = perspective;
        this.status = status;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public void moveTo(BatchStatus next) {
        if (next == null) throw new IllegalArgumentException("批次状态不能为空");
        if (status == BatchStatus.PUBLISHED && next != BatchStatus.PUBLISHED) {
            throw new IllegalStateException("已发布批次不可直接修改状态");
        }
        this.status = next;
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getBatchNo() { return batchNo; }
    public String getPeriod() { return period; }
    public AnalysisPerspective getPerspective() { return perspective; }
    public BatchStatus getStatus() { return status; }
    public String getCreatedBy() { return createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
