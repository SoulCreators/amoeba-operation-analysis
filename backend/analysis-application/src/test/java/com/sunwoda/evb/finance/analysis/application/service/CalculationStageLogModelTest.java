package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.CalculationStage;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationStageLog;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationStageStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 计算作业阶段日志值对象的边界与不变量契约。 */
class CalculationStageLogModelTest {
    @Test
    void startCreatesRunningLogWithZeroMetricsAndPreservedTrace() {
        CalculationStageLog log = CalculationStageLog.start("JOB-1", CalculationStage.NORMALIZE, "trace-9");
        assertEquals(CalculationStageStatus.RUNNING, log.getStatus());
        assertEquals(0L, log.getProcessedRows());
        assertEquals(0, log.getIssueCount());
        assertEquals("trace-9", log.getTraceId());
        assertNotNull(log.getStartedAt());
    }

    @Test
    void succeedPreservesStartedAtAndTraceIdWhileFinalizing() {
        CalculationStageLog running = CalculationStageLog.start("JOB-1", CalculationStage.PNL, "trace-9");
        LocalDateTime startedAt = running.getStartedAt();
        CalculationStageLog done = running.succeed(42L, null, 1);
        assertEquals(startedAt, done.getStartedAt());
        assertEquals("trace-9", done.getTraceId());
        assertEquals(CalculationStageStatus.SUCCEEDED, done.getStatus());
        assertEquals(42L, done.getProcessedRows());
        assertEquals(1, done.getIssueCount());
    }

    @Test
    void failRequiresNonBlankErrorCode() {
        CalculationStageLog running = CalculationStageLog.start("JOB-1", CalculationStage.RECONCILE, null);
        assertThrows(IllegalArgumentException.class, () -> running.fail(" ", "blank code"));
        assertThrows(IllegalArgumentException.class, () -> running.fail(null, "null code"));
    }

    @Test
    void negativeMetricsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> CalculationStageLog.start("JOB-1", CalculationStage.MAP, null).succeed(-1L, null, 0));
        assertThrows(IllegalArgumentException.class,
                () -> CalculationStageLog.restore(1L, "JOB-1", CalculationStage.MAP,
                        CalculationStageStatus.RUNNING, LocalDateTime.now(), null, -1L, null, -1, null, null, null));
    }

    @Test
    void nullIdentityOrStageRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> CalculationStageLog.start(null, CalculationStage.MAP, null));
        assertThrows(IllegalArgumentException.class,
                () -> CalculationStageLog.start("JOB-1", null, null));
    }
}
