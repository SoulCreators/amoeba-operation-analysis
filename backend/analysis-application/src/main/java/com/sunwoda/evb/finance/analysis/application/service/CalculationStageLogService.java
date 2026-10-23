package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.application.model.CalculationJobDetail;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationStage;

import java.math.BigDecimal;

/**
 * 计算作业阶段日志与心跳应用服务。
 *
 * <p>负责在计算各阶段推进时记录独立阶段日志，并同步作业当前阶段、心跳、累计处理行数、
 * 异常数与错误码；阶段失败只标记该阶段，不暴露任何部分正式金额。</p>
 */
public interface CalculationStageLogService {
    void recordStageStarted(String jobId, CalculationStage stage, String traceId);

    void recordHeartbeat(String jobId, CalculationStage stage, long processedRows, int issueCount);

    void recordStageSucceeded(String jobId, CalculationStage stage, long processedRows,
                              BigDecimal controlAmount, int issueCount);

    void recordStageFailed(String jobId, CalculationStage stage, String errorCode, String message);

    CalculationJobDetail getJobDetail(String jobId);
}
