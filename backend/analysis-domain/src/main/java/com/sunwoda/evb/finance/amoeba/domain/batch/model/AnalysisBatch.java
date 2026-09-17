package com.sunwoda.evb.finance.amoeba.domain.batch.model;

import java.time.LocalDateTime;

/** 月度经营分析批次领域对象。 */
public class AnalysisBatch {
    private Long id;
    private String batchNo;
    private String period;
    private String status;
    private LocalDateTime createdAt;

    public AnalysisBatch(String batchNo, String period) {
        this.batchNo = batchNo;
        this.period = period;
        this.status = "DRAFT";
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getBatchNo() { return batchNo; }
    public String getPeriod() { return period; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
