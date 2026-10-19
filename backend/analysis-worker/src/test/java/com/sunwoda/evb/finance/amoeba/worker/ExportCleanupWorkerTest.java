package com.sunwoda.evb.finance.amoeba.worker;

import com.sunwoda.evb.finance.analysis.application.port.ExportCleanupAlertPort;
import com.sunwoda.evb.finance.analysis.application.port.ExportCleanupPort;
import com.sunwoda.evb.finance.analysis.application.model.ExportCleanupTask;
import com.sunwoda.evb.finance.analysis.application.port.ExportFileStoragePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExportCleanupWorkerTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 19, 12, 0);

    private ExportCleanupPort cleanupPort;
    private ExportFileStoragePort storage;
    private ExportCleanupAlertPort alert;

    @BeforeEach
    void setUp() {
        cleanupPort = mock(ExportCleanupPort.class);
        storage = mock(ExportFileStoragePort.class);
        alert = mock(ExportCleanupAlertPort.class);
    }

    @Test
    void expiresClaimsDeletesInUserScopeAndMarksSuccess() {
        ExportCleanupTask task = task(1);
        when(cleanupPort.claimExpired("test-worker", 100, NOW, NOW.plusMinutes(5)))
                .thenReturn(Collections.singletonList(task));

        new ExportCleanupWorker(cleanupPort, storage, alert).runOnce(NOW);

        verify(cleanupPort).expireDueTasks(NOW);
        verify(cleanupPort).claimExpired("test-worker", 100, NOW, NOW.plusMinutes(5));
        verify(storage).delete("file-001", "user-001");
        verify(cleanupPort).markCleanupSucceeded("EXP-001", "claim-001", NOW);
        verify(alert, never()).alert(any(ExportCleanupTask.class), any(String.class));
    }

    @Test
    void firstFailureRetriesAfterFiveMinutes() {
        ExportCleanupTask task = task(1);
        when(cleanupPort.claimExpired("test-worker", 100, NOW, NOW.plusMinutes(5)))
                .thenReturn(Collections.singletonList(task));
        doThrow(new IllegalStateException("storage unavailable"))
                .when(storage).delete("file-001", "user-001");

        new ExportCleanupWorker(cleanupPort, storage, alert).runOnce(NOW);

        verify(cleanupPort).markCleanupRetry("EXP-001", "claim-001",
                "storage unavailable", NOW.plusMinutes(5), NOW, false);
        verify(alert, never()).alert(any(ExportCleanupTask.class), any(String.class));
    }

    @Test
    void secondFailureRetriesAfterTenMinutes() {
        ExportCleanupTask task = task(2);
        when(cleanupPort.claimExpired("test-worker", 100, NOW, NOW.plusMinutes(5)))
                .thenReturn(Collections.singletonList(task));
        doThrow(new IllegalStateException("storage unavailable"))
                .when(storage).delete("file-001", "user-001");

        new ExportCleanupWorker(cleanupPort, storage, alert).runOnce(NOW);

        verify(cleanupPort).markCleanupRetry("EXP-001", "claim-001",
                "storage unavailable", NOW.plusMinutes(10), NOW, false);
    }

    @Test
    void thirdFailureAlertsWithoutChangingDownloadOrPermissionState() {
        ExportCleanupTask task = task(3);
        when(cleanupPort.claimExpired("test-worker", 100, NOW, NOW.plusMinutes(5)))
                .thenReturn(Collections.singletonList(task));
        doThrow(new IllegalStateException("storage unavailable"))
                .when(storage).delete("file-001", "user-001");

        new ExportCleanupWorker(cleanupPort, storage, alert).runOnce(NOW);

        verify(cleanupPort).markCleanupRetry("EXP-001", "claim-001",
                "storage unavailable", NOW, NOW, true);
        verify(alert).alert(task, "storage unavailable");
        verify(cleanupPort, never()).markCleanupSucceeded(any(String.class), any(String.class), any(LocalDateTime.class));
    }

    @Test
    void usesTheRequiredProfileExpressionAndTenMinuteSchedule() throws Exception {
        Profile profile = ExportCleanupWorker.class.getAnnotation(Profile.class);
        assertNotNull(profile);
        assertArrayEquals(new String[] {"db & filesystem"}, profile.value());

        Method method = ExportCleanupWorker.class.getDeclaredMethod("scheduledCleanup");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);
        assertNotNull(scheduled);
        assertEquals(ExportCleanupWorker.CLEANUP_DELAY_MILLIS, scheduled.fixedDelay());
    }

    private ExportCleanupTask task(int attempts) {
        return new ExportCleanupTask("EXP-001", "user-001", "file-001",
                attempts, "claim-001", NOW.minusMinutes(20));
    }
}
