package com.sunwoda.evb.finance.analysis.domain.repository;

import com.sunwoda.evb.finance.analysis.domain.model.CalculationRetryAction;

import java.util.List;

/**
 * 人工失败处理动作仓储。
 *
 * <p>本次新增：保留人工重试和补偿的审计明细，不用状态表的最后一次写入覆盖历史。</p>
 */
public interface CalculationRetryActionRepository {
    CalculationRetryAction save(CalculationRetryAction action);
    List<CalculationRetryAction> findByJobId(String jobId);
}
