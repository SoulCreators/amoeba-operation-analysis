package com.sunwoda.evb.finance.analysis.application.port;

/**
 * 计算作业执行端口。
 *
 * <p>本次新增：Worker 只依赖执行端口，不改造现有 CalculationServiceImpl；生产接线可把已有计算服务适配到此端口。</p>
 */
public interface CalculationRetryExecutor {
    void execute(String batchNo);
}
