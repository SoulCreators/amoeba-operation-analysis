package com.sunwoda.evb.finance.analysis.domain.model;

import java.time.LocalDateTime;

/**
 * 失败处理命令。
 *
 * <p>本次新增：统一自动失败、人工重试和人工补偿的输入，确保人工命令带有操作者、原因和原尝试号。</p>
 */
public final class CalculationRetryCommand {
    public enum Type {
        AUTOMATIC_FAILURE,
        MANUAL_RETRY,
        MANUAL_COMPENSATE
    }

    private final Type type;
    private final String jobId;
    private final String requestKey;
    private final int originalAttemptNo;
    private final String operator;
    private final String reason;
    private final String error;
    private final LocalDateTime occurredAt;

    private CalculationRetryCommand(Type type, String jobId, String requestKey, int originalAttemptNo,
                                    String operator, String reason, String error, LocalDateTime occurredAt) {
        this.type = type;
        this.jobId = required(jobId, "jobId");
        this.requestKey = required(requestKey, "requestKey");
        if (originalAttemptNo < 1) throw new IllegalArgumentException("originalAttemptNo 必须大于 0");
        this.originalAttemptNo = originalAttemptNo;
        this.operator = optional(operator);
        this.reason = optional(reason);
        this.error = optional(error);
        this.occurredAt = occurredAt == null ? LocalDateTime.now() : occurredAt;
    }

    public static CalculationRetryCommand automaticFailure(String jobId, String requestKey,
                                                           int originalAttemptNo, String error,
                                                           LocalDateTime occurredAt) {
        return new CalculationRetryCommand(Type.AUTOMATIC_FAILURE, jobId, requestKey,
                originalAttemptNo, null, null, required(error, "error"), occurredAt);
    }

    public static CalculationRetryCommand manualRetry(String jobId, String requestKey,
                                                      int originalAttemptNo, String operator,
                                                      String reason, LocalDateTime occurredAt) {
        return new CalculationRetryCommand(Type.MANUAL_RETRY, jobId, requestKey, originalAttemptNo,
                required(operator, "operator"), required(reason, "reason"), null, occurredAt);
    }

    public static CalculationRetryCommand manualCompensate(String jobId, String requestKey,
                                                           int originalAttemptNo, String operator,
                                                           String reason, LocalDateTime occurredAt) {
        return new CalculationRetryCommand(Type.MANUAL_COMPENSATE, jobId, requestKey, originalAttemptNo,
                required(operator, "operator"), required(reason, "reason"), null, occurredAt);
    }

    public Type getType() { return type; }
    public String getJobId() { return jobId; }
    public String getRequestKey() { return requestKey; }
    public int getOriginalAttemptNo() { return originalAttemptNo; }
    public String getOperator() { return operator; }
    public String getReason() { return reason; }
    public String getError() { return error; }
    public LocalDateTime getOccurredAt() { return occurredAt; }

    private static String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(field + " 不能为空");
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
