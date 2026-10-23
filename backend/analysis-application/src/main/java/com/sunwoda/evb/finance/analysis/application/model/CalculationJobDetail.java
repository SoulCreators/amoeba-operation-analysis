package com.sunwoda.evb.finance.analysis.application.model;

import com.sunwoda.evb.finance.analysis.domain.model.CalculationJob;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationStageLog;

import java.util.Collections;
import java.util.List;

/**
 * 计算作业详情读模型：作业当前状态 + 阶段日志 + 可重试性。
 *
 * <p>失败作业仅返回阶段进度与错误码，不返回任何部分正式金额；可重试性当前按终态
 * {@code FAILED} 判定，系统故障与业务错误的细分分类不在此切片内。</p>
 */
public final class CalculationJobDetail {
    private final CalculationJob job;
    private final List<CalculationStageLog> stageLogs;
    private final boolean retryable;

    public CalculationJobDetail(CalculationJob job, List<CalculationStageLog> stageLogs, boolean retryable) {
        if (job == null) {
            throw new IllegalArgumentException("job 不能为空");
        }
        this.job = job;
        this.stageLogs = stageLogs == null ? Collections.<CalculationStageLog>emptyList() : stageLogs;
        this.retryable = retryable;
    }

    public CalculationJob getJob() {
        return job;
    }

    public List<CalculationStageLog> getStageLogs() {
        return stageLogs;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
