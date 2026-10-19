package com.sunwoda.evb.finance.analysis.domain.model;

/**
 * 计算作业生命周期状态。
 */
public enum CalculationJobStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED
}
