package com.sunwoda.evb.finance.analysis.domain.repository;

import com.sunwoda.evb.finance.analysis.domain.model.CalculationStageLog;

import java.util.List;

/**
 * 计算作业阶段日志仓储契约。
 *
 * <p>阶段日志是独立的失败任务追溯载体，按作业标识查询按执行顺序返回；保存按
 * (jobId, stage) 幂等覆盖，保证同一作业同一阶段只有一条进度记录。</p>
 */
public interface CalculationStageLogRepository {
    CalculationStageLog save(CalculationStageLog log);

    List<CalculationStageLog> findByJobId(String jobId);
}
