package com.sunwoda.evb.finance.analysis.domain.model;

import java.time.LocalDateTime;

/**
 * 失败后的重试决策。
 *
 * <p>本次新增：把退避计算结果作为不可变值对象返回，应用层不自行推导重试次数或等待时间。</p>
 */
public final class CalculationRetryDecision {
    private final boolean manualRequired;
    private final int retryNo;
    private final int originalAttemptNo;
    private final LocalDateTime nextAttemptAt;
    private final String error;

    private CalculationRetryDecision(boolean manualRequired, int retryNo, int originalAttemptNo,
                                     LocalDateTime nextAttemptAt, String error) {
        this.manualRequired = manualRequired;
        this.retryNo = retryNo;
        this.originalAttemptNo = originalAttemptNo;
        this.nextAttemptAt = nextAttemptAt;
        this.error = required(error, "error");
    }

    public static CalculationRetryDecision schedule(int retryNo, int originalAttemptNo,
                                                    LocalDateTime nextAttemptAt, String error) {
        if (retryNo < 1) throw new IllegalArgumentException("retryNo 必须大于 0");
        if (originalAttemptNo < 1) throw new IllegalArgumentException("originalAttemptNo 必须大于 0");
        if (nextAttemptAt == null) throw new IllegalArgumentException("nextAttemptAt 不能为空");
        return new CalculationRetryDecision(false, retryNo, originalAttemptNo, nextAttemptAt, error);
    }

    public static CalculationRetryDecision manualRequired(int originalAttemptNo, String error) {
        if (originalAttemptNo < 1) throw new IllegalArgumentException("originalAttemptNo 必须大于 0");
        return new CalculationRetryDecision(true, 0, originalAttemptNo, null, error);
    }

    public boolean isManualRequired() {
        return manualRequired;
    }

    public int getRetryNo() {
        return retryNo;
    }

    public int getOriginalAttemptNo() {
        return originalAttemptNo;
    }

    public LocalDateTime getNextAttemptAt() {
        return nextAttemptAt;
    }

    public String getError() {
        return error;
    }

    private static String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value.trim();
    }
}
