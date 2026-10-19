package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.BatchReadiness;
import com.sunwoda.evb.finance.analysis.domain.model.BatchStatus;
import com.sunwoda.evb.finance.analysis.domain.model.DatasetReadiness;
import com.sunwoda.evb.finance.analysis.domain.repository.AnalysisBatchRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BatchServiceImplTest {
    @Test
    void shouldCreateAndMoveBatch() {
        FakeRepository repository = new FakeRepository();
        BatchService service = new BatchServiceImpl(repository, new FakeReadinessService(ready()));

        AnalysisBatch created = service.create("B20260801", "2026-08",
                AnalysisPerspective.BASE, "finance");
        assertEquals(BatchStatus.DRAFT, created.getStatus());

        service.move("B20260801", BatchStatus.CHECKING);
        AnalysisBatch moved = service.move("B20260801", BatchStatus.READY_FOR_CALCULATION);
        assertEquals(BatchStatus.READY_FOR_CALCULATION, moved.getStatus());
    }

    @Test
    void shouldRejectWhenRequiredDatasetIsMissing() {
        FakeRepository repository = repositoryWithStatus(BatchStatus.CHECKING);
        BatchService service = new BatchServiceImpl(repository,
                new FakeReadinessService(notReady(requiredDataset("MISSING", 0))));

        assertThrows(IllegalStateException.class, () ->
                service.move("B20260801", BatchStatus.READY_FOR_CALCULATION));

        assertEquals(BatchStatus.CHECKING, repository.findByBatchNo("B20260801").get().getStatus());
    }

    @Test
    void shouldRejectWhenRequiredDatasetIsInvalid() {
        FakeRepository repository = repositoryWithStatus(BatchStatus.CHECKING);
        BatchService service = new BatchServiceImpl(repository,
                new FakeReadinessService(notReady(requiredDataset("INVALID", 2))));

        assertThrows(IllegalStateException.class, () ->
                service.move("B20260801", BatchStatus.READY_FOR_CALCULATION));

        assertEquals(BatchStatus.CHECKING, repository.findByBatchNo("B20260801").get().getStatus());
    }

    @Test
    void shouldRejectWhenRequiredDatasetHasIssues() {
        FakeRepository repository = repositoryWithStatus(BatchStatus.CHECKING);
        BatchService service = new BatchServiceImpl(repository,
                new FakeReadinessService(notReady(requiredDataset("VALID", 1))));

        assertThrows(IllegalStateException.class, () ->
                service.move("B20260801", BatchStatus.READY_FOR_CALCULATION));

        assertEquals(BatchStatus.CHECKING, repository.findByBatchNo("B20260801").get().getStatus());
    }

    @Test
    void shouldNotCheckReadinessWhenCurrentStatusIsNotChecking() {
        FakeRepository repository = repositoryWithStatus(BatchStatus.DRAFT);
        FakeReadinessService readinessService = new FakeReadinessService(ready());
        BatchService service = new BatchServiceImpl(repository, readinessService);

        assertThrows(IllegalStateException.class, () ->
                service.move("B20260801", BatchStatus.READY_FOR_CALCULATION));

        assertEquals(0, readinessService.getCalls());
        assertEquals(BatchStatus.DRAFT, repository.findByBatchNo("B20260801").get().getStatus());
    }

    @Test
    void shouldNotPersistWhenReadinessFails() {
        FakeRepository repository = repositoryWithStatus(BatchStatus.CHECKING);
        int savesBeforeMove = repository.getSaveCount();
        BatchService service = new BatchServiceImpl(repository,
                new FakeReadinessService(notReady(requiredDataset("INVALID", 1))));

        assertThrows(IllegalStateException.class, () ->
                service.move("B20260801", BatchStatus.READY_FOR_CALCULATION));

        assertEquals(savesBeforeMove, repository.getSaveCount());
        assertEquals(BatchStatus.CHECKING, repository.findByBatchNo("B20260801").get().getStatus());
    }

    @Test
    void shouldRejectDuplicatedBatch() {
        BatchService service = new BatchServiceImpl(new FakeRepository(),
                new FakeReadinessService(ready()));
        service.create("B20260801", "2026-08", AnalysisPerspective.BASE, "finance");
        assertThrows(IllegalStateException.class, () ->
                service.create("B20260801", "2026-08", AnalysisPerspective.BASE, "finance"));
    }

    private FakeRepository repositoryWithStatus(BatchStatus status) {
        FakeRepository repository = new FakeRepository();
        repository.save(new AnalysisBatch(1L, "B20260801", "2026-08",
                AnalysisPerspective.BASE, status, "finance", LocalDateTime.now()));
        return repository;
    }

    private BatchReadiness ready() {
        return new BatchReadiness("B20260801", AnalysisPerspective.BASE, true,
                Collections.singletonList(requiredDataset("VALID", 0)));
    }

    private BatchReadiness notReady(DatasetReadiness dataset) {
        return new BatchReadiness("B20260801", AnalysisPerspective.BASE, false,
                Collections.singletonList(dataset));
    }

    private DatasetReadiness requiredDataset(String status, int issueCount) {
        boolean present = !"MISSING".equals(status);
        return new DatasetReadiness("BASE_DATA", "基础数据", true, present, status, issueCount,
                present ? "TASK-001" : null);
    }

    private static class FakeReadinessService implements BatchReadinessService {
        private final BatchReadiness readiness;
        private int calls;

        private FakeReadinessService(BatchReadiness readiness) {
            this.readiness = readiness;
        }

        @Override
        public BatchReadiness get(String batchNo) {
            calls++;
            return readiness;
        }

        private int getCalls() {
            return calls;
        }
    }

    private static class FakeRepository implements AnalysisBatchRepository {
        private final Map<String, AnalysisBatch> store = new HashMap<String, AnalysisBatch>();
        private long id = 1L;
        private int saveCount;

        @Override
        public AnalysisBatch save(AnalysisBatch batch) {
            saveCount++;
            if (batch.getId() == null) {
                batch = new AnalysisBatch(id++, batch.getBatchNo(), batch.getPeriod(),
                        batch.getPerspective(), batch.getStatus(), batch.getCreatedBy(), batch.getCreatedAt());
            }
            store.put(batch.getBatchNo(), batch);
            return batch;
        }

        @Override
        public Optional<AnalysisBatch> findByBatchNo(String batchNo) {
            return Optional.ofNullable(store.get(batchNo));
        }

        private int getSaveCount() {
            return saveCount;
        }
    }
}
