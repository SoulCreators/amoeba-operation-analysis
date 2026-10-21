package com.sunwoda.evb.finance.analysis.domain.model;

/**
 * 计算作业失败处理状态。
 *
 * <p>本次新增：把重试编排状态独立于现有 CalculationJob 状态，避免改变既有作业模型；
 * 作业本身仍必须通过 CalculationJob 的生命周期状态机运行。</p>
 */
public enum CalculationRetryStatus {
    ACTIVE,
    PENDING_RETRY,
    PROCESSING,
    SUCCEEDED,
    MANUAL_REQUIRED,
    COMPENSATED
}
