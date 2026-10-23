package com.sunwoda.evb.finance.analysis.domain.model;

import java.time.LocalDateTime;

/**
 * 异常工作台工作项：一条经分级（阻断/警告）并带处理状态（未处理/已忽略）的导入异常。
 *
 * <p>不变量：</p>
 * <ul>
 *   <li>阻断项不可忽略；</li>
 *   <li>忽略警告必须记录忽略人、时间与原因；</li>
 *   <li>复检把已忽略警告重新置为未处理，避免旧忽略掩盖新校验结果。</li>
 * </ul>
 */
public class ExceptionWorkItem {
    private final Long id;
    private final String taskNo;
    private final String objectCode;
    private final String issueCode;
    private final IssueSeverity severity;
    private final IssueStatus status;
    private final Integer rowNo;
    private final String fieldName;
    private final String issueMessage;
    private final String rawValue;
    private final String ignoredBy;
    private final LocalDateTime ignoredAt;
    private final String ignoreReason;

    private ExceptionWorkItem(Long id, String taskNo, String objectCode, String issueCode,
                              IssueSeverity severity, IssueStatus status, Integer rowNo,
                              String fieldName, String issueMessage, String rawValue,
                              String ignoredBy, LocalDateTime ignoredAt, String ignoreReason) {
        requireText(taskNo, "taskNo");
        requireText(objectCode, "objectCode");
        requireText(issueCode, "issueCode");
        if (severity == null) {
            throw new IllegalArgumentException("severity 不能为空");
        }
        if (status == null) {
            throw new IllegalArgumentException("status 不能为空");
        }
        if (rowNo != null && rowNo < 0) {
            throw new IllegalArgumentException("rowNo 不能为负");
        }
        this.id = id;
        this.taskNo = taskNo;
        this.objectCode = objectCode;
        this.issueCode = issueCode;
        this.severity = severity;
        this.status = status;
        this.rowNo = rowNo;
        this.fieldName = fieldName;
        this.issueMessage = issueMessage;
        this.rawValue = rawValue;
        this.ignoredBy = ignoredBy;
        this.ignoredAt = ignoredAt;
        this.ignoreReason = ignoreReason;
    }

    /** 新报异常：未处理状态。 */
    public static ExceptionWorkItem report(String taskNo, String objectCode, String issueCode,
                                          IssueSeverity severity, Integer rowNo, String fieldName,
                                          String issueMessage, String rawValue) {
        return new ExceptionWorkItem(null, taskNo, objectCode, issueCode, severity,
                IssueStatus.OPEN, rowNo, fieldName, issueMessage, rawValue, null, null, null);
    }

    /** 持久化读取重建：还原全部字段（含主键与忽略痕迹）。 */
    public static ExceptionWorkItem restore(Long id, String taskNo, String objectCode, String issueCode,
                                            IssueSeverity severity, IssueStatus status, Integer rowNo,
                                            String fieldName, String issueMessage, String rawValue,
                                            String ignoredBy, LocalDateTime ignoredAt, String ignoreReason) {
        return new ExceptionWorkItem(id, taskNo, objectCode, issueCode, severity, status, rowNo,
                fieldName, issueMessage, rawValue, ignoredBy, ignoredAt, ignoreReason);
    }

    /** 仓储分配主键后的副本。 */
    public ExceptionWorkItem withId(Long newId) {
        return new ExceptionWorkItem(newId, taskNo, objectCode, issueCode, severity, status,
                rowNo, fieldName, issueMessage, rawValue, ignoredBy, ignoredAt, ignoreReason);
    }

    /** 受控忽略：仅警告且未处理可忽略；记录忽略人、时间与原因。 */
    public ExceptionWorkItem ignore(String reason, String operator, LocalDateTime at) {
        if (severity == IssueSeverity.BLOCKING) {
            throw new IllegalStateException("阻断项不可忽略: " + issueCode);
        }
        if (status != IssueStatus.OPEN) {
            throw new IllegalStateException("仅未处理项可忽略: " + issueCode);
        }
        requireText(reason, "ignoreReason");
        requireText(operator, "operator");
        if (at == null) {
            throw new IllegalArgumentException("ignoredAt 不能为空");
        }
        return new ExceptionWorkItem(id, taskNo, objectCode, issueCode, severity,
                IssueStatus.IGNORED, rowNo, fieldName, issueMessage, rawValue,
                operator, at, reason);
    }

    /** 复检重置：清除忽略痕迹，重新置为未处理。 */
    public ExceptionWorkItem reopen() {
        return new ExceptionWorkItem(id, taskNo, objectCode, issueCode, severity,
                IssueStatus.OPEN, rowNo, fieldName, issueMessage, rawValue,
                null, null, null);
    }

    public boolean isBlocking() {
        return severity == IssueSeverity.BLOCKING;
    }

    public boolean isOpen() {
        return status == IssueStatus.OPEN;
    }

    public boolean isIgnored() {
        return status == IssueStatus.IGNORED;
    }

    public Long getId() { return id; }
    public String getTaskNo() { return taskNo; }
    public String getObjectCode() { return objectCode; }
    public String getIssueCode() { return issueCode; }
    public IssueSeverity getSeverity() { return severity; }
    public IssueStatus getStatus() { return status; }
    public Integer getRowNo() { return rowNo; }
    public String getFieldName() { return fieldName; }
    public String getIssueMessage() { return issueMessage; }
    public String getRawValue() { return rawValue; }
    public String getIgnoredBy() { return ignoredBy; }
    public LocalDateTime getIgnoredAt() { return ignoredAt; }
    public String getIgnoreReason() { return ignoreReason; }

    private static void requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
    }
}
