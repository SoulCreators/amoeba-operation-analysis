package com.sunwoda.evb.finance.amoeba.worker;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;

/**
 * 计算失败重试定时触发器。
 *
 * <p>本次新增：只负责周期性调用独立 Worker；领取、退避、状态机和人工审计仍由应用层负责。</p>
 */
public class CalculationRetryScheduledWorker {
    private final CalculationJobRetryWorker worker;
    private final int batchSize;

    public CalculationRetryScheduledWorker(
            CalculationJobRetryWorker worker,
            @Value("${analysis.worker.calculation-retry.batch-size:100}") int batchSize) {
        if (worker == null) throw new IllegalArgumentException("worker 不能为空");
        if (batchSize <= 0) throw new IllegalArgumentException("batchSize 必须大于 0");
        this.worker = worker;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${analysis.worker.calculation-retry.fixed-delay-ms:30000}")
    public void scheduledRetry() {
        // 本次新增：使用当前时间扫描到期重试，不在定时器中直接修改作业状态。
        worker.runOnce(LocalDateTime.now(), batchSize);
    }
}
