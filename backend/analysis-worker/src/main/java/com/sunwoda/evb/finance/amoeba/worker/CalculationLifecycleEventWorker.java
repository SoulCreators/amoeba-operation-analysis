package com.sunwoda.evb.finance.amoeba.worker;

import com.sunwoda.evb.finance.analysis.application.model.CalculationEventManualCommand;
import com.sunwoda.evb.finance.analysis.application.service.CalculationLifecycleEventDeliveryService;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;

/**
 * 计算生命周期 Outbox 投递 Worker。
 *
 * <p>本次新增：只负责定时触发应用服务和暴露人工动作入口；领取、投递、状态更新、
 * 重试策略及人工审计边界全部由应用层端口和服务负责。</p>
 */
public final class CalculationLifecycleEventWorker {
    static final long DEFAULT_FIXED_DELAY_MILLIS = 30 * 1000L;
    static final int DEFAULT_BATCH_SIZE = 100;

    private final CalculationLifecycleEventDeliveryService service;
    private final int batchSize;

    public CalculationLifecycleEventWorker(CalculationLifecycleEventDeliveryService service, int batchSize) {
        if (service == null) throw new IllegalArgumentException("service 不能为空");
        if (batchSize <= 0) throw new IllegalArgumentException("batchSize 必须大于 0");
        this.service = service;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${analysis.worker.calculation-event.fixed-delay-ms:30000}")
    public void scheduledDelivery() {
        // 本次新增：定时器只触发一批处理，不在 Worker 中直接操作数据库状态。
        runOnce(LocalDateTime.now());
    }

    public void runOnce(LocalDateTime now) {
        service.runOnce(now, batchSize);
    }

    public void manualRetry(CalculationEventManualCommand command, LocalDateTime operatedAt) {
        service.manualRetry(command, operatedAt);
    }

    public void manualCompensate(CalculationEventManualCommand command, LocalDateTime operatedAt) {
        service.manualCompensate(command, operatedAt);
    }
}
