package com.sunwoda.evb.finance.amoeba.worker;

import com.sunwoda.evb.finance.analysis.application.model.CalculationEventManualCommand;
import com.sunwoda.evb.finance.analysis.application.port.CalculationLifecycleEventDeliveryPort;
import com.sunwoda.evb.finance.analysis.application.port.CalculationLifecycleEventPublisher;
import com.sunwoda.evb.finance.analysis.application.service.CalculationLifecycleEventDeliveryService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CalculationLifecycleEventWorkerTest {
    @Test
    void delegatesScheduledPassAndManualActionsToApplicationService() {
        // 本次新增测试：最终类使用真实应用服务，端口使用 Mock，验证 Worker 只做委托不改写状态机。
        CalculationLifecycleEventDeliveryPort deliveryPort = mock(CalculationLifecycleEventDeliveryPort.class);
        CalculationLifecycleEventPublisher publisher = mock(CalculationLifecycleEventPublisher.class);
        CalculationLifecycleEventDeliveryService service =
                new CalculationLifecycleEventDeliveryService(deliveryPort, publisher, "test-worker", 5);
        CalculationLifecycleEventWorker worker = new CalculationLifecycleEventWorker(service, 20);
        LocalDateTime now = LocalDateTime.of(2026, 10, 21, 12, 0);
        CalculationEventManualCommand command = new CalculationEventManualCommand(
                "event-1", "finance-user", "已人工核验");
        when(deliveryPort.claimReady("test-worker", 20, now, now.plusMinutes(5)))
                .thenReturn(Collections.emptyList());

        worker.runOnce(now);
        worker.manualRetry(command, now);
        worker.manualCompensate(command, now);

        verify(deliveryPort).claimReady("test-worker", 20, now, now.plusMinutes(5));
        // 本次新增测试：应用服务为 final，改为验证其下游端口收到人工动作参数。
        verify(deliveryPort).manualRetry("event-1", "finance-user", "已人工核验", now);
        verify(deliveryPort).manualCompensate("event-1", "finance-user", "已人工核验", now);
    }
}
