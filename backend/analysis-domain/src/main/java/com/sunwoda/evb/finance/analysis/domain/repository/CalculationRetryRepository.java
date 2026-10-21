package com.sunwoda.evb.finance.analysis.domain.repository;

import com.sunwoda.evb.finance.analysis.domain.model.CalculationRetryState;

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
}
