package com.sunwoda.evb.finance.analysis.domain.repository;

import com.sunwoda.evb.finance.analysis.domain.model.CalculationJob;

import java.util.Optional;

/**
 * 计算作业仓储契约。
 *
 * <p>本阶段只定义作业按标识和活动请求键查询的能力，不代表完整的生产级幂等机制。</p>
 */
public interface CalculationJobRepository {
    CalculationJob save(CalculationJob job);

    Optional<CalculationJob> findByJobId(String jobId);

    Optional<CalculationJob> findActiveByRequestKey(String requestKey);
}
