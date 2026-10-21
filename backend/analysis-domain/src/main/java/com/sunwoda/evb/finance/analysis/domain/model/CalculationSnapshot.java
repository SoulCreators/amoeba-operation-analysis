package com.sunwoda.evb.finance.analysis.domain.model;

import java.time.LocalDateTime;

/**
 * 计算输入快照的不可变领域模型。
 *
 * <p>本次修改：冻结计算实际读取的配置内容、来源版本集合及其摘要，
 * 不在领域层解析 JSON，也不引入数据库或 Spring 依赖。</p>
 */
public final class CalculationSnapshot {
    private final String snapshotId;
    private final String snapshotType;
    private final String schemaVersion;
    private final String contentJson;
    private final String sourceVersionSetJson;
    private final String contentHash;
    private final String sourceVersionHash;
    private final String ruleVersion;
    private final String createdBy;
    private final LocalDateTime createdAt;

    public CalculationSnapshot(String snapshotId, String snapshotType, String schemaVersion,
                               String contentJson, String sourceVersionSetJson,
                               String contentHash, String sourceVersionHash,
                               String ruleVersion, String createdBy, LocalDateTime createdAt) {
        this.snapshotId = requiredText(snapshotId, "snapshotId");
        this.snapshotType = requiredText(snapshotType, "snapshotType");
        this.schemaVersion = requiredText(schemaVersion, "schemaVersion");
        this.contentJson = requiredText(contentJson, "contentJson");
        this.sourceVersionSetJson = requiredText(sourceVersionSetJson, "sourceVersionSetJson");
        this.contentHash = requiredText(contentHash, "contentHash");
        this.sourceVersionHash = optionalText(sourceVersionHash);
        this.ruleVersion = optionalText(ruleVersion);
        this.createdBy = requiredText(createdBy, "createdBy");
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt 不能为空");
        }
        this.createdAt = createdAt;
    }

    public String getSnapshotId() {
        return snapshotId;
    }

    public String getSnapshotType() {
        return snapshotType;
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public String getContentJson() {
        return contentJson;
    }

    public String getSourceVersionSetJson() {
        return sourceVersionSetJson;
    }

    public String getContentHash() {
        return contentHash;
    }

    public String getSourceVersionHash() {
        return sourceVersionHash;
    }

    public String getRuleVersion() {
        return ruleVersion;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
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
