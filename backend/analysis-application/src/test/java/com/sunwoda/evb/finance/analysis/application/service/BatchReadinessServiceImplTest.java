package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.application.port.DatasetCatalog;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.BatchReadiness;
import com.sunwoda.evb.finance.analysis.domain.model.BatchStatus;
import com.sunwoda.evb.finance.analysis.domain.model.DatasetDefinition;
import com.sunwoda.evb.finance.analysis.domain.model.ImportStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ImportTask;
import com.sunwoda.evb.finance.analysis.domain.repository.AnalysisBatchRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.ImportTaskRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** READINESS-API-001 business-state contract; it is not an authorization or formula test. */
class BatchReadinessServiceImplTest {
    private static final String BATCH_NO = "BATCH-READINESS-001";

    @Test
    void readyForCalculationDependsOnRequiredDatasetStatusOnly() {
        FakeBatchRepository batches = new FakeBatchRepository();
        batches.batch = batch();
        FakeTaskRepository tasks = new FakeTaskRepository();
        tasks.tasks = Arrays.asList(task("BASE_REQUIRED", ImportStatus.VALID, 0),
                task("OPTIONAL_REFERENCE", ImportStatus.INVALID, 3));

        BatchReadiness readiness = service(batches, tasks).get(BATCH_NO);

        assertTrue(readiness.isReadyForCalculation());
        assertEquals(2, readiness.getDatasets().size());
        assertEquals("VALID", readiness.getDatasets().get(0).getStatus());
        assertEquals(3, readiness.getDatasets().get(1).getIssueCount());
    }

    @Test
    void missingOrInvalidRequiredDatasetBlocksWithoutApplyingPermissionOrFormulaRules() {
        FakeBatchRepository batches = new FakeBatchRepository();
        batches.batch = batch();
        FakeTaskRepository tasks = new FakeTaskRepository();
        tasks.tasks = Collections.singletonList(task("BASE_REQUIRED", ImportStatus.INVALID, 2));

        BatchReadiness readiness = service(batches, tasks).get(BATCH_NO);

        assertFalse(readiness.isReadyForCalculation());
        assertEquals("INVALID", readiness.getDatasets().get(0).getStatus());
        assertEquals(2, readiness.getDatasets().get(0).getIssueCount());
        assertNotNull(readiness.getDatasets().get(0).getTaskNo());
    }

    private BatchReadinessService service(FakeBatchRepository batches, FakeTaskRepository tasks) {
        return new BatchReadinessServiceImpl(batches, tasks, new FakeDatasetCatalog());
    }

    private AnalysisBatch batch() {
        return new AnalysisBatch(1L, BATCH_NO, "2026-01", AnalysisPerspective.BASE,
                BatchStatus.CHECKING, "owner", LocalDateTime.now());
    }

    private ImportTask task(String datasetCode, ImportStatus status, int issueCount) {
        return ImportTask.restore(1L, "TASK-" + datasetCode, BATCH_NO, datasetCode,
                datasetCode + ".xlsx", "checksum", "owner", LocalDateTime.now(),
                status, issueCount, LocalDateTime.now());
    }

    private static class FakeBatchRepository implements AnalysisBatchRepository {
        private AnalysisBatch batch;

        @Override
        public AnalysisBatch save(AnalysisBatch value) { return value; }

        @Override
        public Optional<AnalysisBatch> findByBatchNo(String batchNo) {
            return batch != null && batchNo.equals(batch.getBatchNo())
                    ? Optional.of(batch) : Optional.<AnalysisBatch>empty();
        }
    }

    private static class FakeTaskRepository implements ImportTaskRepository {
        private List<ImportTask> tasks = Collections.emptyList();

        @Override
        public ImportTask save(ImportTask task) { return task; }

        @Override
        public Optional<ImportTask> findByTaskNo(String taskNo) {
            for (ImportTask task : tasks) if (taskNo.equals(task.getTaskNo())) return Optional.of(task);
            return Optional.empty();
        }

        @Override
        public boolean existsByBatchAndDataset(String batchNo, String datasetCode) { return false; }

        @Override
        public List<ImportTask> findByBatchNo(String batchNo) { return tasks; }
    }

    private static class FakeDatasetCatalog implements DatasetCatalog {
        @Override
        public List<DatasetDefinition> list(AnalysisPerspective perspective) {
            return Arrays.asList(
                    new DatasetDefinition("BASE_REQUIRED", "Required", "FINANCE", true,
                            Collections.singletonList(AnalysisPerspective.BASE)),
                    new DatasetDefinition("OPTIONAL_REFERENCE", "Optional", "REFERENCE", false,
                            Collections.singletonList(AnalysisPerspective.BASE)));
        }

        @Override
        public DatasetDefinition require(String datasetCode, AnalysisPerspective perspective) {
            throw new UnsupportedOperationException();
        }
    }
}
