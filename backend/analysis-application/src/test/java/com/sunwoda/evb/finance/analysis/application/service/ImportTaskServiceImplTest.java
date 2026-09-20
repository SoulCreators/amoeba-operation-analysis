package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.application.port.DatasetCatalog;
import com.sunwoda.evb.finance.analysis.application.port.TemplateValidator;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.BatchStatus;
import com.sunwoda.evb.finance.analysis.domain.model.DatasetDefinition;
import com.sunwoda.evb.finance.analysis.domain.model.ImportStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ImportTask;
import com.sunwoda.evb.finance.analysis.domain.repository.AnalysisBatchRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.ImportTaskRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ImportTaskServiceImplTest {
    @Test
    void createsMonthlyImportTaskOnlyForSupportedPerspectiveDataset() {
        FakeBatchRepository batches = new FakeBatchRepository();
        batches.save(new AnalysisBatch(1L, "B-001", "2026-09", AnalysisPerspective.BASE,
                BatchStatus.DRAFT, "tester", LocalDateTime.now()));
        FakeImportTaskRepository tasks = new FakeImportTaskRepository();
        DatasetCatalog catalog = new FakeDatasetCatalog();
        ImportTaskService service = new ImportTaskServiceImpl(batches, tasks, catalog, new FakeTemplateValidator());

        ImportTask created = service.create("B-001", "BASE_ACT_INC_COST", "actual.xlsx", "sha256", "tester");

        assertEquals("BASE_ACT_INC_COST", created.getDatasetCode());
        assertEquals(ImportStatus.CREATED, created.getStatus());
        assertThrows(IllegalStateException.class,
                () -> service.create("B-001", "BASE_ACT_INC_COST", "actual-2.xlsx", null, "tester"));
    }

    @Test
    void rejectsDatasetFromAnotherPerspective() {
        FakeBatchRepository batches = new FakeBatchRepository();
        batches.save(new AnalysisBatch(1L, "B-002", "2026-09", AnalysisPerspective.BASE,
                BatchStatus.DRAFT, "tester", LocalDateTime.now()));
        ImportTaskService service = new ImportTaskServiceImpl(batches,
                new FakeImportTaskRepository(), new FakeDatasetCatalog(), new FakeTemplateValidator());

        assertThrows(IllegalArgumentException.class,
                () -> service.create("B-002", "PLBU_ACT_INC_COST", "actual.xlsx", null, "tester"));
    }

    private static class FakeDatasetCatalog implements DatasetCatalog {
        @Override
        public List<DatasetDefinition> list(AnalysisPerspective perspective) {
            return Collections.emptyList();
        }

        @Override
        public DatasetDefinition require(String datasetCode, AnalysisPerspective perspective) {
            if ("BASE_ACT_INC_COST".equals(datasetCode) && perspective == AnalysisPerspective.BASE) {
                return new DatasetDefinition(datasetCode, "基地实际收入成本明细", "MONTHLY", true,
                        Collections.singletonList(AnalysisPerspective.BASE));
            }
            throw new IllegalArgumentException("数据集不适用");
        }
    }

    private static class FakeTemplateValidator implements TemplateValidator {
        @Override
        public com.sunwoda.evb.finance.analysis.domain.model.ImportValidationResult validate(
                String taskNo, DatasetDefinition definition, InputStream inputStream) {
            return new com.sunwoda.evb.finance.analysis.domain.model.ImportValidationResult(
                    taskNo, ImportStatus.VALID, 0, Collections.<String>emptyList(),
                    Collections.<com.sunwoda.evb.finance.analysis.domain.model.ImportIssue>emptyList());
        }
    }

    private static class FakeBatchRepository implements AnalysisBatchRepository {
        private final Map<String, AnalysisBatch> data = new HashMap<String, AnalysisBatch>();

        @Override
        public AnalysisBatch save(AnalysisBatch batch) {
            data.put(batch.getBatchNo(), batch);
            return batch;
        }

        @Override
        public Optional<AnalysisBatch> findByBatchNo(String batchNo) {
            return Optional.ofNullable(data.get(batchNo));
        }
    }

    private static class FakeImportTaskRepository implements ImportTaskRepository {
        private final Map<String, ImportTask> data = new HashMap<String, ImportTask>();

        @Override
        public ImportTask save(ImportTask task) {
            data.put(task.getTaskNo(), task);
            return task;
        }

        @Override
        public Optional<ImportTask> findByTaskNo(String taskNo) {
            return Optional.ofNullable(data.get(taskNo));
        }

        @Override
        public boolean existsByBatchAndDataset(String batchNo, String datasetCode) {
            for (ImportTask task : data.values()) {
                if (batchNo.equals(task.getBatchNo()) && datasetCode.equals(task.getDatasetCode())) return true;
            }
            return false;
        }
    }
}
