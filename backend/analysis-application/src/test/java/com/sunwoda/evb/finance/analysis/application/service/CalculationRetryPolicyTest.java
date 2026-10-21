package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.CalculationRetryDecision;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationRetryPolicy;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V028 重试策略独立测试。
 *
 * <p>本次新增测试：固化 1/5/15 分钟退避、最多三次自动重试以及第四次失败人工接管。</p>
 */
class CalculationRetryPolicyTest {
    private final LocalDateTime failedAt = LocalDateTime.of(2026, 10, 21, 10, 0);

    @Test
    void shouldUseOneFiveAndFifteenMinutesForAutomaticRetries() {
        assertEquals(failedAt.plusMinutes(1), CalculationRetryPolicy.decide(1, "e1", failedAt).getNextAttemptAt());
        assertEquals(failedAt.plusMinutes(5), CalculationRetryPolicy.decide(2, "e2", failedAt).getNextAttemptAt());
        assertEquals(failedAt.plusMinutes(15), CalculationRetryPolicy.decide(3, "e3", failedAt).getNextAttemptAt());
    }

    @Test
    void shouldRequireManualHandlingAfterThreeRetries() {
        CalculationRetryDecision decision = CalculationRetryPolicy.decide(4, "e4", failedAt);

        assertTrue(decision.isManualRequired());
        assertEquals(4, decision.getOriginalAttemptNo());
        assertEquals("e4", decision.getError());
    }
}
