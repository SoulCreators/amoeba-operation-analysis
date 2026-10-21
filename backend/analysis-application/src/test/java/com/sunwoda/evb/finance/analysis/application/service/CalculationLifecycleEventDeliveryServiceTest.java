package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.application.model.CalculationEventDeliveryTask;
import com.sunwoda.evb.finance.analysis.application.model.CalculationEventManualCommand;
import com.sunwoda.evb.finance.analysis.application.port.CalculationLifecycleEventDeliveryPort;
import com.sunwoda.evb.finance.analysis.application.port.CalculationLifecycleEventPublisher;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationEventDeliveryStatus;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationLifecycleEvent;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CalculationLifecycleEventDeliveryServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 12, 0);

    @Test
    void publishesAndMarksDeliveredWithClaimToken() {
        CalculationLifecycleEventDeliveryPort port = mock(CalculationLifecycleEventDeliveryPort.class);
        CalculationLifecycleEventPublisher publisher = mock(CalculationLifecycleEventPublisher.class);
        CalculationLifecycleEvent event = event(1);
        CalculationEventDeliveryTask task = new CalculationEventDeliveryTask(event, "claim-1", NOW,
                NOW.plusMinutes(5));
        when(port.claimReady("worker-1", 10, NOW, NOW.plusMinutes(5)))
                .thenReturn(Collections.singletonList(task));

        new CalculationLifecycleEventDeliveryService(port, publisher, "worker-1", 5)
                .runOnce(NOW, 10);

        verify(publisher).publish(event);
        verify(port).markDelivered("event-1", "claim-1", NOW);
    }

    @Test
    void firstFailureUsesOneMinuteBackoff() {
        CalculationLifecycleEventDeliveryPort port = mock(CalculationLifecycleEventDeliveryPort.class);
        CalculationLifecycleEventPublisher publisher = mock(CalculationLifecycleEventPublisher.class);
        CalculationLifecycleEvent event = event(1);
        CalculationEventDeliveryTask task = new CalculationEventDeliveryTask(event, "claim-1", NOW,
                NOW.plusMinutes(5));
        when(port.claimReady("worker-1", 10, NOW, NOW.plusMinutes(5)))
                .thenReturn(Collections.singletonList(task));
        doThrow(new IllegalStateException("下游不可用")).when(publisher).publish(event);

        new CalculationLifecycleEventDeliveryService(port, publisher, "worker-1", 5)
                .runOnce(NOW, 10);

        verify(port).markFailed("event-1", "claim-1", "下游不可用",
                NOW.plusMinutes(1), NOW, false);
    }

    @Test
    void fourthFailureMovesToManualRequiredWithoutAnotherAutomaticDelay() {
        CalculationLifecycleEventDeliveryPort port = mock(CalculationLifecycleEventDeliveryPort.class);
        CalculationLifecycleEventPublisher publisher = mock(CalculationLifecycleEventPublisher.class);
        CalculationLifecycleEvent event = event(4);
        CalculationEventDeliveryTask task = new CalculationEventDeliveryTask(event, "claim-4", NOW,
                NOW.plusMinutes(5));
        when(port.claimReady("worker-1", 10, NOW, NOW.plusMinutes(5)))
                .thenReturn(Collections.singletonList(task));
        doThrow(new IllegalStateException("持续失败")).when(publisher).publish(event);

        new CalculationLifecycleEventDeliveryService(port, publisher, "worker-1", 5)
                .runOnce(NOW, 10);

        verify(port).markFailed("event-1", "claim-4", "持续失败", NOW, NOW, true);
    }

    @Test
    void manualActionRequiresOperatorAndReasonAndDelegatesAuditBoundaryToPort() {
        CalculationLifecycleEventDeliveryPort port = mock(CalculationLifecycleEventDeliveryPort.class);
        CalculationLifecycleEventPublisher publisher = mock(CalculationLifecycleEventPublisher.class);
        CalculationLifecycleEventDeliveryService service =
                new CalculationLifecycleEventDeliveryService(port, publisher, "worker-1", 5);

        service.manualRetry(new CalculationEventManualCommand("event-1", "finance-user", "确认下游已恢复"), NOW);
        verify(port).manualRetry("event-1", "finance-user", "确认下游已恢复", NOW);
        assertThrows(IllegalArgumentException.class,
                () -> new CalculationEventManualCommand("event-1", "finance-user", " "));
    }

    private CalculationLifecycleEvent event(int attemptNo) {
        return new CalculationLifecycleEvent("event-1", "VERSION", "version-1", "CALCULATION_PUBLISHED",
                "version-1", "trace-1", "{}", CalculationEventDeliveryStatus.IN_PROGRESS,
                attemptNo, NOW, null, NOW, null);
    }
}
