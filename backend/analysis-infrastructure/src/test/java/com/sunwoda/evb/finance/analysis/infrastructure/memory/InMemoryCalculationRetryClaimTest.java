package com.sunwoda.evb.finance.analysis.infrastructure.memory;

import com.sunwoda.evb.finance.analysis.domain.model.CalculationRetryClaim;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationRetryClaimLostException;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationRetryDecision;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationRetryState;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 内存仓储租约所有权契约。
 *
 * <p>本次新增测试：验证错误 token、租约过期接管和旧 token 释放保护，保持与 JDBC 语义一致。</p>
 */
class InMemoryCalculationRetryClaimTest {
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 21, 10, 0);

    @Test
    void oldTokenCannotReleaseOrLockAClaimTakenAfterLeaseExpiry() {
        InMemoryCalculationRetryRepository repository = new InMemoryCalculationRetryRepository();
        CalculationRetryState state = CalculationRetryState.active("JOB-CLAIM-1", "REQ-CLAIM-1", T0);
        state.schedule(CalculationRetryDecision.schedule(1, 1, T0, "首次失败"), T0);
        repository.save(state);

        List<CalculationRetryClaim> first = repository.findDueClaims(T0, 1);
        CalculationRetryClaim oldClaim = first.get(0);
        List<CalculationRetryClaim> second = repository.findDueClaims(T0.plusMinutes(6), 1);
        CalculationRetryClaim newClaim = second.get(0);

        assertNotEquals(oldClaim.getClaimToken(), newClaim.getClaimToken());
        assertThrows(CalculationRetryClaimLostException.class,
                () -> repository.lockClaim(state.getJobId(), oldClaim.getClaimToken(), T0.plusMinutes(6)));
        repository.releaseClaim(state.getJobId(), oldClaim.getClaimToken());
        repository.lockClaim(state.getJobId(), newClaim.getClaimToken(), T0.plusMinutes(6));
        assertTrue(repository.findDueClaims(T0.plusMinutes(6), 1).isEmpty(),
                "旧 token 释放不得删除新 Worker 的租约");
    }
}
