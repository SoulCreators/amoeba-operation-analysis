package com.sunwoda.evb.finance.analysis.domain.model;

/**
 * 计算作业单个阶段的执行状态。
 *
 * <p>阶段从 {@link #RUNNING} 进入，以 {@link #SUCCEEDED} 或 {@link #FAILED} 终态结束；
 * 终态阶段日志保留错误码与追踪号，供失败任务独立追溯。</p>
 */
public enum CalculationStageStatus {
    RUNNING,
    SUCCEEDED,
    FAILED
}
