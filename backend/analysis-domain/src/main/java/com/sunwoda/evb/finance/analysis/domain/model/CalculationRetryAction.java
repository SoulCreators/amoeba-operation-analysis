package com.sunwoda.evb.finance.analysis.domain.model;

import java.time.LocalDateTime;

/**
 * 人工重试/补偿动作审计记录。
 *
 * <p>本次新增：不可变保存操作者、原因、原尝试号和处理结果，避免人工入口只留下状态而丢失依据。</p>
 */
public final class CalculationRetryAction {
    public enum Type { MANUAL_RETRY, MANUAL_COMPENSATE }

    private final long actionId;
    private final String jobId;
    private final String requestKey;
    private final Type type;
    private final String operator;
    private final String reason;
    private final int originalAttemptNo;
    private final String result;
    private final String resultDetail;
    private final LocalDateTime occurredAt;

    public CalculationRetryAction(long actionId, String jobId, String requestKey, Type type,
                                  String operator, String reason, int originalAttemptNo,
                                  String result, String resultDetail, LocalDateTime occurredAt) {
        if (actionId < 0) throw new IllegalArgumentException("actionId 不能小于 0");
        this.actionId = actionId;
        this.jobId = required(jobId, "jobId");
        this.requestKey = required(requestKey, "requestKey");
        if (type == null) throw new IllegalArgumentException("type 不能为空");
        this.type = type;
        this.operator = required(operator, "operator");
        this.reason = required(reason, "reason");
        if (originalAttemptNo < 1) throw new IllegalArgumentException("originalAttemptNo 必须大于 0");
        this.originalAttemptNo = originalAttemptNo;
        this.result = required(result, "result");
        this.resultDetail = resultDetail == null || resultDetail.trim().isEmpty() ? null : resultDetail.trim();
        if (occurredAt == null) throw new IllegalArgumentException("occurredAt 不能为空");
        this.occurredAt = occurredAt;
    }

    public long getActionId() { return actionId; }
    public String getJobId() { return jobId; }
    public String getRequestKey() { return requestKey; }
    public Type getType() { return type; }
    public String getOperator() { return operator; }
    public String getReason() { return reason; }
    public int getOriginalAttemptNo() { return originalAttemptNo; }
    public String getResult() { return result; }
    public String getResultDetail() { return resultDetail; }
    public LocalDateTime getOccurredAt() { return occurredAt; }

    private static String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(field + " 不能为空");
        return value.trim();
    }
}
