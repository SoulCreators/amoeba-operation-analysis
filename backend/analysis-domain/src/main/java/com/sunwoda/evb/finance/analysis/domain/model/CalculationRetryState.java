package com.sunwoda.evb.finance.analysis.domain.model;

import java.time.LocalDateTime;

/**
 * 计算作业重试状态。
 *
 * <p>本次新增：状态对象只通过显式方法改变状态，持久化层只能保存状态机已经接受的结果。</p>
 */
public final class CalculationRetryState {
    private final String jobId;
    private final String requestKey;
    private CalculationRetryStatus status;
    private int retryNo;
    private Integer lastFailedAttemptNo;
    private LocalDateTime nextRetryAt;
    private String lastError;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private CalculationRetryState(String jobId, String requestKey, CalculationRetryStatus status, int retryNo,
                                  Integer lastFailedAttemptNo, LocalDateTime nextRetryAt, String lastError,
                                  LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.jobId = required(jobId, "jobId");
        this.requestKey = required(requestKey, "requestKey");
        if (status == null) throw new IllegalArgumentException("status 不能为空");
        if (retryNo < 0 || retryNo > CalculationRetryPolicy.MAX_AUTOMATIC_RETRIES) {
            throw new IllegalArgumentException("retryNo 超出范围: " + retryNo);
        }
        if (createdAt == null || updatedAt == null) throw new IllegalArgumentException("时间不能为空");
        this.status = status;
        this.retryNo = retryNo;
        this.lastFailedAttemptNo = lastFailedAttemptNo;
        this.nextRetryAt = nextRetryAt;
        this.lastError = optional(lastError);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        validateFields();
    }

    public static CalculationRetryState active(String jobId, String requestKey, LocalDateTime now) {
        return new CalculationRetryState(jobId, requestKey, CalculationRetryStatus.ACTIVE, 0,
                null, null, null, now, now);
    }

    public static CalculationRetryState restore(String jobId, String requestKey, CalculationRetryStatus status,
                                                int retryNo, Integer lastFailedAttemptNo,
                                                LocalDateTime nextRetryAt, String lastError,
                                                LocalDateTime createdAt, LocalDateTime updatedAt) {
        return new CalculationRetryState(jobId, requestKey, status, retryNo, lastFailedAttemptNo,
                nextRetryAt, lastError, createdAt, updatedAt);
    }

    public void schedule(CalculationRetryDecision decision, LocalDateTime now) {
        if (decision == null || decision.isManualRequired()) throw new IllegalArgumentException("必须是自动重试决策");
        transition(CalculationRetryStatus.PENDING_RETRY);
        retryNo = decision.getRetryNo();
        lastFailedAttemptNo = decision.getOriginalAttemptNo();
        nextRetryAt = decision.getNextAttemptAt();
        lastError = decision.getError();
        touch(now);
    }

    public void requireManual(CalculationRetryDecision decision, LocalDateTime now) {
        if (decision == null || !decision.isManualRequired()) throw new IllegalArgumentException("必须是人工接管决策");
        transition(CalculationRetryStatus.MANUAL_REQUIRED);
        lastFailedAttemptNo = decision.getOriginalAttemptNo();
        nextRetryAt = null;
        lastError = decision.getError();
        touch(now);
    }

    public void claim(LocalDateTime now) {
        if (status == CalculationRetryStatus.PENDING_RETRY
                && (nextRetryAt == null || nextRetryAt.isAfter(now))) {
            throw new IllegalStateException("重试尚未到期");
        }
        transition(CalculationRetryStatus.PROCESSING);
        nextRetryAt = null;
        touch(now);
    }

    public void succeed(LocalDateTime now) {
        transition(CalculationRetryStatus.SUCCEEDED);
        nextRetryAt = null;
        lastError = null;
        touch(now);
    }

    public void compensate(LocalDateTime now) {
        transition(CalculationRetryStatus.COMPENSATED);
        nextRetryAt = null;
        touch(now);
    }

    private void transition(CalculationRetryStatus next) {
        CalculationRetryLifecyclePolicy.validate(status, next);
        status = next;
    }

    private void touch(LocalDateTime now) {
        if (now == null) throw new IllegalArgumentException("updatedAt 不能为空");
        updatedAt = now;
    }

    private void validateFields() {
        if ((status == CalculationRetryStatus.PENDING_RETRY && nextRetryAt == null)
                || (status != CalculationRetryStatus.PENDING_RETRY && nextRetryAt != null)) {
            throw new IllegalArgumentException("只有 PENDING_RETRY 必须带 nextRetryAt");
        }
        if ((status == CalculationRetryStatus.PENDING_RETRY || status == CalculationRetryStatus.MANUAL_REQUIRED)
                && (lastFailedAttemptNo == null || lastError == null)) {
            throw new IllegalArgumentException("失败处理状态必须保留原尝试号和错误");
        }
    }

    public String getJobId() { return jobId; }
    public String getRequestKey() { return requestKey; }
    public CalculationRetryStatus getStatus() { return status; }
    public int getRetryNo() { return retryNo; }
    public Integer getLastFailedAttemptNo() { return lastFailedAttemptNo; }
    public LocalDateTime getNextRetryAt() { return nextRetryAt; }
    public String getLastError() { return lastError; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    private static String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(field + " 不能为空");
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
