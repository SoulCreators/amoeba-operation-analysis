package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.CalculationJob;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationJobLifecyclePolicy;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationJobStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CalculationJobLifecyclePolicyTest {
    @Test
    void shouldAllowQueuedRunningSucceededLifecycle() {
        CalculationJob job = job();

        job.moveTo(CalculationJobStatus.RUNNING);
        job.moveTo(CalculationJobStatus.SUCCEEDED);

        assertEquals(CalculationJobStatus.SUCCEEDED, job.getStatus());
        assertEquals(1, job.getAttemptNo());
        assertNotNull(job.getStartedAt());
        assertNotNull(job.getFinishedAt());
        assertNull(job.getLastError());
    }

    @Test
    void shouldRetainFailureAndIncrementAttemptOnRetry() {
        CalculationJob job = job();

        job.moveTo(CalculationJobStatus.RUNNING);
        job.fail("事实数据读取失败");
        assertEquals(CalculationJobStatus.FAILED, job.getStatus());
        assertEquals(1, job.getAttemptNo());
        assertEquals("事实数据读取失败", job.getLastError());
        assertNotNull(job.getFinishedAt());

        job.moveTo(CalculationJobStatus.QUEUED);
        assertEquals(CalculationJobStatus.QUEUED, job.getStatus());
        assertEquals(2, job.getAttemptNo());
        assertEquals("事实数据读取失败", job.getLastError());
        assertNull(job.getStartedAt());
        assertNull(job.getFinishedAt());
    }

    @Test
    void shouldRejectUndefinedTransitionsAndKeepSucceededTerminal() {
        CalculationJob job = job();

        assertIllegalTransition(job, CalculationJobStatus.SUCCEEDED);
        job.moveTo(CalculationJobStatus.RUNNING);
        assertIllegalTransition(job, CalculationJobStatus.QUEUED);
        job.moveTo(CalculationJobStatus.SUCCEEDED);
        assertIllegalTransition(job, CalculationJobStatus.RUNNING);
        assertIllegalTransition(job, CalculationJobStatus.SUCCEEDED);
    }

    @Test
    void shouldRejectNullAndBlankRequiredFields() {
        LocalDateTime now = LocalDateTime.now();

        assertThrows(IllegalArgumentException.class,
                () -> new CalculationJob(null, "request-1", "batch-1", now));
        assertThrows(IllegalArgumentException.class,
                () -> new CalculationJob("job-1", " ", "batch-1", now));
        assertThrows(IllegalArgumentException.class,
                () -> new CalculationJob("job-1", "request-1", null, now));
        assertThrows(IllegalArgumentException.class,
                () -> new CalculationJob("job-1", "request-1", "batch-1", null));
    }

    @Test
    void shouldRejectNullTargetAndBlankFailureReason() {
        CalculationJob job = job();

        assertThrows(IllegalArgumentException.class, () -> job.moveTo(null));
        job.moveTo(CalculationJobStatus.RUNNING);
        assertThrows(IllegalArgumentException.class, () -> job.fail(" "));
        assertEquals(CalculationJobStatus.RUNNING, job.getStatus());
    }

    @Test
    void policyShouldRejectNullCurrentStateAndTerminalTransitions() {
        assertThrows(IllegalStateException.class,
                () -> CalculationJobLifecyclePolicy.validate(null, CalculationJobStatus.RUNNING));
        assertThrows(IllegalArgumentException.class,
                () -> CalculationJobLifecyclePolicy.validate(CalculationJobStatus.QUEUED, null));
        assertThrows(IllegalStateException.class,
                () -> CalculationJobLifecyclePolicy.validate(
                        CalculationJobStatus.SUCCEEDED, CalculationJobStatus.QUEUED));
    }

    private void assertIllegalTransition(CalculationJob job, CalculationJobStatus next) {
        CalculationJobStatus current = job.getStatus();
        assertThrows(IllegalStateException.class, () -> job.moveTo(next));
        assertEquals(current, job.getStatus());
    }

    private CalculationJob job() {
        return new CalculationJob("JOB-001", "request-001", "B-001", LocalDateTime.now());
    }
}
