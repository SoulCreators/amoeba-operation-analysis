package com.sunwoda.evb.finance.amoeba.worker;

import com.sunwoda.evb.finance.analysis.application.port.CalculationLifecycleEventDeliveryPort;
import com.sunwoda.evb.finance.analysis.application.port.CalculationLifecycleEventPublisher;
import com.sunwoda.evb.finance.analysis.application.service.CalculationLifecycleEventDeliveryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Outbox Worker 接线配置。
 *
 * <p>本次新增：只有部署同时提供状态端口和外部发布端口时才创建生产 Worker；
 * 未配置完整适配器时保持失败关闭，不创建一个看似运行但无法落状态的空壳任务。</p>
 */
@Configuration
@Profile("db")
public class CalculationLifecycleEventWorkerConfiguration {
    @Bean
    @ConditionalOnBean({CalculationLifecycleEventDeliveryPort.class,
            CalculationLifecycleEventPublisher.class})
    public CalculationLifecycleEventDeliveryService calculationLifecycleEventDeliveryService(
            CalculationLifecycleEventDeliveryPort deliveryPort,
            CalculationLifecycleEventPublisher publisher,
            @Value("${analysis.worker.calculation-event.worker-id:analysis-event-worker}") String workerId,
            @Value("${analysis.worker.calculation-event.lease-minutes:5}") int leaseMinutes) {
        return new CalculationLifecycleEventDeliveryService(deliveryPort, publisher, workerId, leaseMinutes);
    }

    @Bean
    @ConditionalOnBean(CalculationLifecycleEventDeliveryService.class)
    public CalculationLifecycleEventWorker calculationLifecycleEventWorker(
            CalculationLifecycleEventDeliveryService service,
            @Value("${analysis.worker.calculation-event.batch-size:100}") int batchSize) {
        return new CalculationLifecycleEventWorker(service, batchSize);
    }
}
