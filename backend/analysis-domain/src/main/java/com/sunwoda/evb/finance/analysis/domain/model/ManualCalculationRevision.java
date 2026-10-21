package com.sunwoda.evb.finance.analysis.domain.model;

import java.time.LocalDateTime;

/**
 * 历史计算版本人工修订关系的不可变领域模型。
 *
 * <p>本次修改：显式保存来源版本、目标版本、修订人、修订原因和修订时间，
 * 让人工修订成为可审计的追加关系，而不是覆盖旧版本结果。</p>
 */
public final class ManualCalculationRevision {
    private final String revisionId;
    private final String sourceCalculationVersionId;
    private final String targetCalculationVersionId;
    private final String revisedBy;
    private final String revisionReason;
    private final LocalDateTime revisedAt;
    private final String traceId;

    public ManualCalculationRevision(String revisionId, String sourceCalculationVersionId,
                                     String targetCalculationVersionId, String revisedBy,
                                     String revisionReason, LocalDateTime revisedAt,
                                     String traceId) {
        this.revisionId = required(revisionId, "revisionId");
        this.sourceCalculationVersionId = required(sourceCalculationVersionId, "sourceCalculationVersionId");
        this.targetCalculationVersionId = required(targetCalculationVersionId, "targetCalculationVersionId");
        if (this.sourceCalculationVersionId.equals(this.targetCalculationVersionId)) {
            throw new IllegalArgumentException("来源版本和目标版本不能相同");
        }
        this.revisedBy = required(revisedBy, "revisedBy");
        this.revisionReason = required(revisionReason, "revisionReason");
        if (revisedAt == null) {
            throw new IllegalArgumentException("revisedAt 不能为空");
        }
        this.revisedAt = revisedAt;
        this.traceId = optional(traceId);
    }

    public String getRevisionId() { return revisionId; }
    public String getSourceCalculationVersionId() { return sourceCalculationVersionId; }
    public String getTargetCalculationVersionId() { return targetCalculationVersionId; }
    public String getRevisedBy() { return revisedBy; }
    public String getRevisionReason() { return revisionReason; }
    public LocalDateTime getRevisedAt() { return revisedAt; }
    public String getTraceId() { return traceId; }

    private static String required(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
