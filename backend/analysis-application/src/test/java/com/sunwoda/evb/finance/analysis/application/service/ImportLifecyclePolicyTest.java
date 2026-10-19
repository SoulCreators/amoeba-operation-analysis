package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.ImportStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ImportTask;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ImportLifecyclePolicyTest {
    @Test
    void shouldAllowDefinedLifecycleTransitions() {
        ImportTask task = task();

        task.moveTo(ImportStatus.VALIDATING, 0);
        task.moveTo(ImportStatus.INVALID, 2);
        task.moveTo(ImportStatus.VALIDATING, 0);
        task.moveTo(ImportStatus.VALID, 0);
        task.moveTo(ImportStatus.VALID, 0);
        task.moveTo(ImportStatus.VALIDATING, 0);
        task.moveTo(ImportStatus.VALID, 0);

        assertEquals(ImportStatus.VALID, task.getStatus());
        assertEquals(0, task.getIssueCount());
    }

    @Test
    void shouldAllowCancellationOnlyFromCreatedAndKeepItTerminal() {
        ImportTask task = task();

        task.moveTo(ImportStatus.CANCELLED, 0);
        task.moveTo(ImportStatus.CANCELLED, 3);

        assertEquals(ImportStatus.CANCELLED, task.getStatus());
        assertEquals(0, task.getIssueCount());
        assertThrows(IllegalStateException.class,
                () -> task.moveTo(ImportStatus.VALIDATING, 0));
    }

    @Test
    void shouldRejectUndefinedTransitions() {
        assertIllegalTransition(ImportStatus.CREATED, ImportStatus.VALID);
        assertIllegalTransition(ImportStatus.CREATED, ImportStatus.INVALID);
        assertIllegalTransition(ImportStatus.VALIDATING, ImportStatus.CREATED);
        assertIllegalTransition(ImportStatus.VALIDATING, ImportStatus.VALIDATING);
        assertIllegalTransition(ImportStatus.INVALID, ImportStatus.VALID);
        assertIllegalTransition(ImportStatus.VALID, ImportStatus.INVALID);
        assertIllegalTransition(ImportStatus.VALID, ImportStatus.CANCELLED);
        assertIllegalTransition(ImportStatus.CANCELLED, ImportStatus.CREATED);
    }

    @Test
    void shouldRejectPositiveIssueCountWhenMarkingValid() {
        ImportTask task = task();
        task.moveTo(ImportStatus.VALIDATING, 0);

        assertThrows(IllegalStateException.class,
                () -> task.moveTo(ImportStatus.VALID, 1));
        assertEquals(ImportStatus.VALIDATING, task.getStatus());
        assertEquals(0, task.getIssueCount());
    }

    @Test
    void shouldRejectNullStatusAndNegativeIssueCount() {
        ImportTask task = task();

        assertThrows(IllegalArgumentException.class,
                () -> task.moveTo(null, 0));
        assertThrows(IllegalArgumentException.class,
                () -> task.moveTo(ImportStatus.VALIDATING, -1));
    }

    private void assertIllegalTransition(ImportStatus current, ImportStatus next) {
        ImportTask task = task();
        if (current != ImportStatus.CREATED) {
            task.moveTo(ImportStatus.VALIDATING, 0);
            if (current == ImportStatus.INVALID) {
                task.moveTo(ImportStatus.INVALID, 1);
            } else if (current == ImportStatus.VALID) {
                task.moveTo(ImportStatus.VALID, 0);
            } else if (current == ImportStatus.CANCELLED) {
                task = task();
                task.moveTo(ImportStatus.CANCELLED, 0);
            }
        }

        ImportTask target = task;
        assertThrows(IllegalStateException.class, () -> target.moveTo(next, 0));
        assertEquals(current, target.getStatus());
    }

    private ImportTask task() {
        return new ImportTask(1L, "IMP-001", "B-001", "BASE_ACT_INC_COST",
                "actual.xlsx", "sha256", "tester", LocalDateTime.now());
    }
}
