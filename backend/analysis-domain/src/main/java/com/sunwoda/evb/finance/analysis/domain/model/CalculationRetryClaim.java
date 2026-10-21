package com.sunwoda.evb.finance.analysis.domain.model;

import java.time.LocalDateTime;

/**
 * 计算重试租约及其所有权凭证。
 *
 * <p>本次新增：把 claim token 与到期时间绑定到本次领取结果，后续状态写入和释放只能使用同一凭证。</p>
 */
public final class CalculationRetryClaim {
    private final CalculationRetryState state;
    private final String claimToken;
    private final LocalDateTime claimedUntil;

    public CalculationRetryClaim(CalculationRetryState state, String claimToken,
                                  LocalDateTime claimedUntil) {
        if (state == null) throw new IllegalArgumentException("state 不能为空");
        if (claimToken == null || claimToken.trim().isEmpty()) {
            throw new IllegalArgumentException("claimToken 不能为空");
        }
        if (claimedUntil == null) throw new IllegalArgumentException("claimedUntil 不能为空");
        this.state = state;
        this.claimToken = claimToken.trim();
        this.claimedUntil = claimedUntil;
    }

    public CalculationRetryState getState() { return state; }
    public String getJobId() { return state.getJobId(); }
    public String getClaimToken() { return claimToken; }
    public LocalDateTime getClaimedUntil() { return claimedUntil; }
}
