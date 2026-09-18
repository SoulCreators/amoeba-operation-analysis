package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.BatchStatus;
import com.sunwoda.evb.finance.analysis.domain.repository.AnalysisBatchRepository;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BatchServiceImplTest {
    @Test
    void shouldCreateAndMoveBatch() {
        BatchService service = new BatchServiceImpl(new FakeRepository());

        AnalysisBatch created = service.create("B20260801", "2026-08",
                AnalysisPerspective.BASE, "finance");
        assertEquals(BatchStatus.DRAFT, created.getStatus());

        AnalysisBatch moved = service.move("B20260801", BatchStatus.READY_FOR_CALCULATION);
        assertEquals(BatchStatus.READY_FOR_CALCULATION, moved.getStatus());
    }

    @Test
    void shouldRejectDuplicatedBatch() {
        BatchService service = new BatchServiceImpl(new FakeRepository());
        service.create("B20260801", "2026-08", AnalysisPerspective.BASE, "finance");
        assertThrows(IllegalStateException.class, () ->
                service.create("B20260801", "2026-08", AnalysisPerspective.BASE, "finance"));
    }

    private static class FakeRepository implements AnalysisBatchRepository {
        private final Map<String, AnalysisBatch> store = new HashMap<String, AnalysisBatch>();
        private long id = 1L;

        @Override
        public AnalysisBatch save(AnalysisBatch batch) {
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
    }
}
