package com.sunwoda.evb.finance.analysis.infrastructure.memory;

import com.sunwoda.evb.finance.analysis.domain.model.ImportStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ImportTask;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class InMemoryImportTaskRepositoryTest {
    private final InMemoryImportTaskRepository repository =
            new InMemoryImportTaskRepository();

    @Test
    void savesNewTaskAsCreatedAndAssignsId() {
        ImportTask task = task("IMP-001");

        ImportTask saved = repository.save(task);

        assertNotNull(saved.getId());
        assertEquals(1L, saved.getId().longValue());
        assertEquals(ImportStatus.CREATED, saved.getStatus());
        assertEquals(0, saved.getIssueCount());
        assertSame(saved, repository.findByTaskNo("IMP-001").get());
    }

    @Test
    void preservesLifecycleStatusesWhenSavingExistingTask() {
        ImportTask task = repository.save(task("IMP-002"));

        task.moveTo(ImportStatus.VALIDATING, 0);
        repository.save(task);
        assertEquals(ImportStatus.VALIDATING, repository.findByTaskNo("IMP-002").get().getStatus());

        task.moveTo(ImportStatus.INVALID, 2);
        repository.save(task);
        assertEquals(ImportStatus.INVALID, repository.findByTaskNo("IMP-002").get().getStatus());

        task.moveTo(ImportStatus.VALIDATING, 0);
        task.moveTo(ImportStatus.VALID, 0);
        repository.save(task);
        ImportTask reloaded = repository.findByTaskNo("IMP-002").get();
        assertEquals(ImportStatus.VALID, reloaded.getStatus());
        assertEquals(0, reloaded.getIssueCount());
    }

    @Test
    void savingExistingTaskDoesNotResetItsStatus() {
        ImportTask task = repository.save(task("IMP-003"));
        task.moveTo(ImportStatus.VALIDATING, 0);

        ImportTask saved = repository.save(task);

        assertSame(task, saved);
        assertEquals(ImportStatus.VALIDATING,
                repository.findByTaskNo("IMP-003").get().getStatus());
    }

    private static ImportTask task(String taskNo) {
        return new ImportTask(null, taskNo, "BATCH-001", "BASE_ACTUAL",
                "actual.xlsx", "checksum", "tester",
                LocalDateTime.of(2026, 10, 19, 10, 0));
    }
}
