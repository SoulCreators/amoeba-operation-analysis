package com.sunwoda.evb.finance.analysis.domain.repository;

import com.sunwoda.evb.finance.analysis.domain.model.CalculationRetryState;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationRetryClaim;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 计算失败处理状态仓储。
 *
 * <p>本次新增：为 request_key 幂等查询、到期领取和状态机结果持久化提供最小端口。</p>
 */
public interface CalculationRetryRepository {
    CalculationRetryState save(CalculationRetryState state);
    Optional<CalculationRetryState> findByJobId(String jobId);
    Optional<CalculationRetryState> findByRequestKey(String requestKey);
    List<CalculationRetryState> findDue(LocalDateTime now, int limit);

    /**
     * 按租约凭证领取到期记录。
     *
     * <p>本次新增：默认实现保留既有内存/测试仓储的兼容性；数据库实现必须覆盖并返回真实 token。</p>
     */
    default List<CalculationRetryClaim> findDueClaims(LocalDateTime now, int limit) {
        List<CalculationRetryState> states = findDue(now, limit);
        java.util.List<CalculationRetryClaim> claims = new java.util.ArrayList<CalculationRetryClaim>();
        LocalDateTime claimedUntil = (now == null ? LocalDateTime.now() : now).plusMinutes(5L);
        for (CalculationRetryState state : states) {
            claims.add(new CalculationRetryClaim(state, "legacy-" + state.getJobId(), claimedUntil));
        }
        return claims;
    }

    /** 本次新增：在同一事务内确认 token 仍属于当前 Worker 并锁定租约。 */
    default void lockClaim(String jobId, String claimToken, LocalDateTime now) {
        // 本次兼容：旧内存/测试仓储没有数据库租约表，由具体实现自行补齐所有权校验。
    }

    /** 本次新增：只释放 jobId 与 claimToken 同时匹配的当前租约。 */
    default void releaseClaim(String jobId, String claimToken) {
        // 本次兼容：旧内存/测试仓储没有数据库租约表，无需执行释放动作。
    }
}
