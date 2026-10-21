package com.sunwoda.evb.finance.analysis.domain.model;

import java.time.LocalDateTime;

/**
 * 计算失败自动重试策略。
 *
 * <p>本次新增：首次失败后最多自动重试三次，退避窗口固定为 1、5、15 分钟；
 * 第四次失败不再自动排队，转为 MANUAL_REQUIRED。</p>
 */
public final class CalculationRetryPolicy {
    public static final int MAX_AUTOMATIC_RETRIES = 3;
    private static final int[] BACKOFF_MINUTES = new int[]{1, 5, 15};

    private CalculationRetryPolicy() {
    }

    public static CalculationRetryDecision decide(int originalAttemptNo, String error,
                                                  LocalDateTime failedAt) {
        if (originalAttemptNo < 1) {
            throw new IllegalArgumentException("originalAttemptNo 必须大于 0");
        }
        if (failedAt == null) {
            throw new IllegalArgumentException("failedAt 不能为空");
        }
        if (originalAttemptNo <= MAX_AUTOMATIC_RETRIES) {
            int retryNo = originalAttemptNo;
            return CalculationRetryDecision.schedule(retryNo, originalAttemptNo,
                    failedAt.plusMinutes(BACKOFF_MINUTES[retryNo - 1]), error);
        }
        return CalculationRetryDecision.manualRequired(originalAttemptNo, error);
    }

    public static int backoffMinutes(int retryNo) {
        if (retryNo < 1 || retryNo > MAX_AUTOMATIC_RETRIES) {
            throw new IllegalArgumentException("retryNo 超出自动重试上限: " + retryNo);
        }
        return BACKOFF_MINUTES[retryNo - 1];
    }
}
