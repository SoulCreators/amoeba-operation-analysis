package com.sunwoda.evb.finance.analysis.application.model;

/** 导出任务生命周期状态。 */
public enum ExportJobStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    EXPIRED
}
