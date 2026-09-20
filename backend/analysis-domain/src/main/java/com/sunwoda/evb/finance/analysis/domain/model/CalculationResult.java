package com.sunwoda.evb.finance.analysis.domain.model;

import java.util.Collections;
import java.util.List;
import java.time.LocalDateTime;

public class CalculationResult {
    private final String batchNo;
    private final AnalysisPerspective perspective;
    private final List<PnlResult> results;
    private final String versionNo;
    private final CalculationResultStatus status;
    private final String confirmedBy;
    private final String publishedBy;
    private final LocalDateTime calculatedAt;
    private final LocalDateTime confirmedAt;
    private final LocalDateTime publishedAt;

    public CalculationResult(String batchNo, AnalysisPerspective perspective, List<PnlResult> results) {
        this(batchNo, perspective, results, batchNo + "-R1", CalculationResultStatus.CALCULATED,
                null, null, LocalDateTime.now(), null, null);
    }

    public CalculationResult(String batchNo, AnalysisPerspective perspective, List<PnlResult> results,
                              String versionNo, CalculationResultStatus status,
                              String confirmedBy, String publishedBy,
                              LocalDateTime calculatedAt, LocalDateTime confirmedAt,
                              LocalDateTime publishedAt) {
        this.batchNo = batchNo;
        this.perspective = perspective;
        this.results = Collections.unmodifiableList(results);
        this.versionNo = versionNo;
        this.status = status;
        this.confirmedBy = confirmedBy;
        this.publishedBy = publishedBy;
        this.calculatedAt = calculatedAt;
        this.confirmedAt = confirmedAt;
        this.publishedAt = publishedAt;
    }

    public String getBatchNo() { return batchNo; }
    public AnalysisPerspective getPerspective() { return perspective; }
    public List<PnlResult> getResults() { return results; }
    public String getVersionNo() { return versionNo; }
    public CalculationResultStatus getStatus() { return status; }
    public String getConfirmedBy() { return confirmedBy; }
    public String getPublishedBy() { return publishedBy; }
    public LocalDateTime getCalculatedAt() { return calculatedAt; }
    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public LocalDateTime getPublishedAt() { return publishedAt; }

    public CalculationResult confirm(String operator) {
        if (status != CalculationResultStatus.CALCULATED) {
            throw new IllegalStateException("只有已计算结果可以确认: " + status);
        }
        return new CalculationResult(batchNo, perspective, results, versionNo,
                CalculationResultStatus.CONFIRMED, operator, publishedBy,
                calculatedAt, LocalDateTime.now(), publishedAt);
    }

    public CalculationResult publish(String operator) {
        if (status != CalculationResultStatus.CONFIRMED) {
            throw new IllegalStateException("只有已确认结果可以发布: " + status);
        }
        return new CalculationResult(batchNo, perspective, results, versionNo,
                CalculationResultStatus.PUBLISHED, confirmedBy, operator,
                calculatedAt, confirmedAt, LocalDateTime.now());
    }
}
