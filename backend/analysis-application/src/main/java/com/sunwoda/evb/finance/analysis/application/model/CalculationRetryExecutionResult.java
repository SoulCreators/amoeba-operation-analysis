package com.sunwoda.evb.finance.analysis.application.model;

import com.sunwoda.evb.finance.analysis.domain.model.CalculationJob;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationRetryDecision;

/**
 * 自动重试一次执行的原子结果。
 *
 * <p>本次新增：Worker 只在应用事务完成后读取该结果；成功时 decision 为 null，失败时携带已经落库的决策。</p>
 */
public final class CalculationRetryExecutionResult {
    private final CalculationJob job;
    private final CalculationRetryDecision decision;

    public CalculationRetryExecutionResult(CalculationJob job, CalculationRetryDecision decision) {
        if (job == null) throw new IllegalArgumentException("job 不能为空");
        this.job = job;
        this.decision = decision;
    }

    public CalculationJob getJob() { return job; }
    public CalculationRetryDecision getDecision() { return decision; }
}
