package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.BatchStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BatchLifecyclePolicyTest {
    @Test
    void shouldAllowDefinedLifecycleTransitions() {
        assertTransition(BatchStatus.DRAFT, BatchStatus.CHECKING);
        assertTransition(BatchStatus.CHECKING, BatchStatus.READY_FOR_CALCULATION);
        assertTransition(BatchStatus.CHECKING, BatchStatus.FAILED);
        assertTransition(BatchStatus.READY_FOR_CALCULATION, BatchStatus.CALCULATING);
        assertTransition(BatchStatus.CALCULATING, BatchStatus.CALCULATED);
        assertTransition(BatchStatus.CALCULATING, BatchStatus.FAILED);
        assertTransition(BatchStatus.CALCULATED, BatchStatus.CALCULATING);
        assertTransition(BatchStatus.CALCULATED, BatchStatus.CONFIRMED);
        assertTransition(BatchStatus.CONFIRMED, BatchStatus.PUBLISHED);
        assertTransition(BatchStatus.FAILED, BatchStatus.CHECKING);
        assertTransition(BatchStatus.PUBLISHED, BatchStatus.PUBLISHED);
    }

    @Test
    void shouldRejectUndefinedLifecycleTransitions() {
        assertIllegalTransition(BatchStatus.DRAFT, BatchStatus.DRAFT);
        assertIllegalTransition(BatchStatus.DRAFT, BatchStatus.READY_FOR_CALCULATION);
        assertIllegalTransition(BatchStatus.CHECKING, BatchStatus.CALCULATING);
        assertIllegalTransition(BatchStatus.READY_FOR_CALCULATION, BatchStatus.CALCULATED);
        assertIllegalTransition(BatchStatus.CALCULATING, BatchStatus.CONFIRMED);
        assertIllegalTransition(BatchStatus.CALCULATED, BatchStatus.PUBLISHED);
        assertIllegalTransition(BatchStatus.CONFIRMED, BatchStatus.CALCULATING);
        assertIllegalTransition(BatchStatus.FAILED, BatchStatus.READY_FOR_CALCULATION);
        assertIllegalTransition(BatchStatus.PUBLISHED, BatchStatus.CALCULATED);
    }

    @Test
    void shouldRejectNullTargetStatus() {
        AnalysisBatch batch = batch(BatchStatus.DRAFT);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> batch.moveTo(null));

        assertEquals("批次状态不能为空", exception.getMessage());
    }

    private void assertTransition(BatchStatus current, BatchStatus next) {
        AnalysisBatch batch = batch(current);
        batch.moveTo(next);
        assertEquals(next, batch.getStatus());
    }

    private void assertIllegalTransition(BatchStatus current, BatchStatus next) {
        AnalysisBatch batch = batch(current);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> batch.moveTo(next));

        assertEquals("批次状态不允许从 " + current + " 转移到 " + next, exception.getMessage());
        assertEquals(current, batch.getStatus());
    }

    private AnalysisBatch batch(BatchStatus status) {
        return new AnalysisBatch(1L, "B20260801", "2026-08",
                AnalysisPerspective.BASE, status, "tester", LocalDateTime.now());
    }
}
