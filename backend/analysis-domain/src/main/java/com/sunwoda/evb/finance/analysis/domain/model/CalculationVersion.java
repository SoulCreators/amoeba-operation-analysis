package com.sunwoda.evb.finance.analysis.domain.model;

import java.time.LocalDateTime;

/**
 * 计算结果版本头的不可变领域模型。
 *
 * <p>本次修改：建立批次、计算作业、输入快照、版本状态和替代关系的纯领域表达，
 * 不改变现有 CalculationResult 的覆盖式实现，也不接入权限和运行时编排。</p>
 */
public final class CalculationVersion {
    private final String calculationVersionId;
    private final String batchNo;
    private final AnalysisPerspective perspective;
    private final String versionNo;
    private final String jobId;
    private final String snapshotId;
    private final String sourceVersionHash;
    private final String configHash;
    private final String inputFingerprint;
    private final CalculationVersionStatus status;
    private final boolean current;
    private final String supersedesVersionId;
    private final String traceId;
    private final String calculatedBy;
    private final LocalDateTime calculatedAt;
    private final String confirmedBy;
    private final LocalDateTime confirmedAt;
    private final String publishedBy;
    private final LocalDateTime publishedAt;

    public CalculationVersion(String calculationVersionId, String batchNo,
                              AnalysisPerspective perspective, String versionNo,
                              String jobId, String snapshotId, String sourceVersionHash,
                              String configHash, String inputFingerprint,
                              CalculationVersionStatus status, boolean current,
                              String supersedesVersionId, String traceId,
                              String calculatedBy, LocalDateTime calculatedAt,
                              String confirmedBy, LocalDateTime confirmedAt,
                              String publishedBy, LocalDateTime publishedAt) {
        this.calculationVersionId = requiredText(calculationVersionId, "calculationVersionId");
        this.batchNo = requiredText(batchNo, "batchNo");
        if (perspective == null) {
            throw new IllegalArgumentException("perspective 不能为空");
        }
        this.perspective = perspective;
        this.versionNo = requiredText(versionNo, "versionNo");
        this.jobId = requiredText(jobId, "jobId");
        this.snapshotId = requiredText(snapshotId, "snapshotId");
        this.sourceVersionHash = requiredText(sourceVersionHash, "sourceVersionHash");
        this.configHash = requiredText(configHash, "configHash");
        this.inputFingerprint = requiredText(inputFingerprint, "inputFingerprint");
        if (status == null) {
            throw new IllegalArgumentException("status 不能为空");
        }
        this.status = status;
        this.current = current;
        this.supersedesVersionId = optionalText(supersedesVersionId);
        this.traceId = optionalText(traceId);
        this.calculatedBy = requiredText(calculatedBy, "calculatedBy");
        if (calculatedAt == null) {
            throw new IllegalArgumentException("calculatedAt 不能为空");
        }
        this.calculatedAt = calculatedAt;
        this.confirmedBy = optionalText(confirmedBy);
        this.confirmedAt = confirmedAt;
        this.publishedBy = optionalText(publishedBy);
        this.publishedAt = publishedAt;
        validatePairedFields("confirmed", this.confirmedBy, this.confirmedAt);
        validatePairedFields("published", this.publishedBy, this.publishedAt);
    }

    public String getCalculationVersionId() {
        return calculationVersionId;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public AnalysisPerspective getPerspective() {
        return perspective;
    }

    public String getVersionNo() {
        return versionNo;
    }

    public String getJobId() {
        return jobId;
    }

    public String getSnapshotId() {
        return snapshotId;
    }

    public String getSourceVersionHash() {
        return sourceVersionHash;
    }

    public String getConfigHash() {
        return configHash;
    }

    public String getInputFingerprint() {
        return inputFingerprint;
    }

    public CalculationVersionStatus getStatus() {
        return status;
    }

    public boolean isCurrent() {
        return current;
    }

    public String getSupersedesVersionId() {
        return supersedesVersionId;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getCalculatedBy() {
        return calculatedBy;
    }

    public LocalDateTime getCalculatedAt() {
        return calculatedAt;
    }

    public String getConfirmedBy() {
        return confirmedBy;
    }

    public LocalDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public String getPublishedBy() {
        return publishedBy;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    private static void validatePairedFields(String name, String operator, LocalDateTime time) {
        if ((operator == null) != (time == null)) {
            throw new IllegalArgumentException(name + " 操作人与时间必须同时存在或同时为空");
        }
    }

    private static String requiredText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return value;
    }

    private static String optionalText(String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }
}
