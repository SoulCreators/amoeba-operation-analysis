package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.BatchLifecyclePolicy;
import com.sunwoda.evb.finance.analysis.domain.model.BatchStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BatchLifecycleStateMatrixTest {
    private static final Map<BatchStatus, Set<BatchStatus>> EXPECTED_TRANSITIONS =
            expectedTransitions();

    @Test
    void policyAcceptsOnlyTheDeclaredStateMatrix() {
        for (BatchStatus current : BatchStatus.values()) {
            for (BatchStatus next : BatchStatus.values()) {
                if (EXPECTED_TRANSITIONS.get(current).contains(next)) {
                    BatchLifecyclePolicy.validate(current, next);
                } else {
                    assertThrows(IllegalStateException.class,
                            () -> BatchLifecyclePolicy.validate(current, next),
                            current + " must not transition to " + next);
                }
            }
        }
    }

    @Test
    void analysisBatchMovesToEveryAllowedTargetInTheMatrix() {
        for (BatchStatus current : BatchStatus.values()) {
            for (BatchStatus next : EXPECTED_TRANSITIONS.get(current)) {
                AnalysisBatch batch = batch(current);

                batch.moveTo(next);

                assertEquals(next, batch.getStatus(),
                        current + " should transition to " + next);
            }
        }
    }

    @Test
    void analysisBatchRejectsEveryForbiddenTargetAndKeepsItsCurrentState() {
        for (BatchStatus current : BatchStatus.values()) {
            for (BatchStatus next : BatchStatus.values()) {
                if (EXPECTED_TRANSITIONS.get(current).contains(next)) {
                    continue;
                }

                AnalysisBatch batch = batch(current);

                assertThrows(IllegalStateException.class, () -> batch.moveTo(next),
                        current + " must not transition to " + next);
                assertEquals(current, batch.getStatus());
            }
        }
    }

    @Test
    void rejectsNullTargetsAndNullCurrentStateAccordingToThePolicyContract() {
        assertThrows(IllegalArgumentException.class,
                () -> BatchLifecyclePolicy.validate(BatchStatus.DRAFT, null));
        assertThrows(IllegalStateException.class,
                () -> BatchLifecyclePolicy.validate(null, BatchStatus.DRAFT));
        assertThrows(IllegalArgumentException.class,
                () -> batch(BatchStatus.DRAFT).moveTo(null));
        assertThrows(IllegalStateException.class,
                () -> batch(null).moveTo(BatchStatus.DRAFT));
    }

    private static Map<BatchStatus, Set<BatchStatus>> expectedTransitions() {
        EnumMap<BatchStatus, Set<BatchStatus>> transitions =
                new EnumMap<BatchStatus, Set<BatchStatus>>(BatchStatus.class);
        transitions.put(BatchStatus.DRAFT, EnumSet.of(BatchStatus.CHECKING));
        transitions.put(BatchStatus.CHECKING,
                EnumSet.of(BatchStatus.READY_FOR_CALCULATION, BatchStatus.FAILED));
        transitions.put(BatchStatus.READY_FOR_CALCULATION,
                EnumSet.of(BatchStatus.CALCULATING));
        transitions.put(BatchStatus.CALCULATING,
                EnumSet.of(BatchStatus.CALCULATED, BatchStatus.FAILED));
        transitions.put(BatchStatus.CALCULATED,
                EnumSet.of(BatchStatus.CALCULATING, BatchStatus.CONFIRMED));
        transitions.put(BatchStatus.CONFIRMED, EnumSet.of(BatchStatus.PUBLISHED));
        transitions.put(BatchStatus.PUBLISHED, EnumSet.of(BatchStatus.PUBLISHED));
        transitions.put(BatchStatus.FAILED, EnumSet.of(BatchStatus.CHECKING));
        return transitions;
    }

    private static AnalysisBatch batch(BatchStatus status) {
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 20, 9, 0);
        return new AnalysisBatch(1L, "BATCH-STATE-001", "2026-10",
                AnalysisPerspective.BASE, status, "test", createdAt);
    }
}
