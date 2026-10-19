package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.application.port.DatasetCatalog;
import com.sunwoda.evb.finance.analysis.application.port.PnlFactSourceReader;
import com.sunwoda.evb.finance.analysis.application.port.PnlFactTaskReader;
import com.sunwoda.evb.finance.analysis.application.port.TemplateValidator;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.BatchStatus;
import com.sunwoda.evb.finance.analysis.domain.model.DatasetDefinition;
import com.sunwoda.evb.finance.analysis.domain.model.ImportStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ImportTask;
import com.sunwoda.evb.finance.analysis.domain.model.PnlFact;
import com.sunwoda.evb.finance.analysis.domain.model.PnlFactSource;
import com.sunwoda.evb.finance.analysis.domain.repository.AnalysisBatchRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.ImportIssueRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.ImportFileRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.ImportTaskRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.PnlFactSourceWriter;
import com.sunwoda.evb.finance.analysis.domain.repository.PnlFactWriter;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ImportTaskServiceImplTest {
    @Test
    void createsMonthlyImportTaskOnlyForSupportedPerspectiveDataset() {
        FakeBatchRepository batches = new FakeBatchRepository();
        batches.save(new AnalysisBatch(1L, "B-001", "2026-09", AnalysisPerspective.BASE,
                BatchStatus.DRAFT, "tester", LocalDateTime.now()));
        FakeImportTaskRepository tasks = new FakeImportTaskRepository();
        DatasetCatalog catalog = new FakeDatasetCatalog();
        ImportTaskService service = new ImportTaskServiceImpl(batches, tasks, catalog,
                new FakeTemplateValidator(), new FakeImportIssueRepository(), new FakeImportFileRepository());

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
                new FakeImportTaskRepository(), new FakeDatasetCatalog(), new FakeTemplateValidator(),
                new FakeImportIssueRepository(), new FakeImportFileRepository());

        assertThrows(IllegalArgumentException.class,
                () -> service.create("B-002", "PLBU_ACT_INC_COST", "actual.xlsx", null, "tester"));
    }

    @Test
    void validatesFactsAndSourcesWithTheSameBatchAndTask() throws Exception {
        FakeBatchRepository batches = new FakeBatchRepository();
        AnalysisBatch batch = batch("B-003");
        batches.save(batch);
        FakeImportTaskRepository tasks = new FakeImportTaskRepository();
        ImportTask task = task("IMP-003", "B-003");
        tasks.save(task);
        ImportTaskServiceImpl service = newService(batches, tasks);
        RecordingFactWriter factWriter = new RecordingFactWriter();
        RecordingSourceWriter sourceWriter = new RecordingSourceWriter();
        setField(service, "factReader", new EmptyFactReader());
        setField(service, "sourceReader", new EmptySourceReader());
        setField(service, "factWriter", factWriter);
        setField(service, "sourceWriter", sourceWriter);

        service.validate(task.getTaskNo(), new ByteArrayInputStream(new byte[]{1}));

        assertSame(batch, factWriter.batch);
        assertSame(task, factWriter.task);
        assertSame(batch, sourceWriter.batch);
        assertSame(task, sourceWriter.task);
    }

    @Test
    void rejectsConfiguredFactWriterWithoutSourceWriter() throws Exception {
        ImportTaskServiceImpl service = serviceWithPersistence("IMP-004", "B-004");
        setField(service, "sourceWriter", null);

        assertThrows(IllegalStateException.class,
                () -> service.validate("IMP-004", new ByteArrayInputStream(new byte[]{1})));
    }

    @Test
    void rejectsConfiguredFactWriterWithoutSourceReader() throws Exception {
        ImportTaskServiceImpl service = serviceWithPersistence("IMP-005", "B-005");
        setField(service, "sourceReader", null);

        assertThrows(IllegalStateException.class,
                () -> service.validate("IMP-005", new ByteArrayInputStream(new byte[]{1})));
    }

    @Test
    void propagatesSourceWriterFailure() throws Exception {
        ImportTaskServiceImpl service = serviceWithPersistence("IMP-006", "B-006");
        setField(service, "sourceWriter", new ThrowingSourceWriter());

        assertThrows(IllegalStateException.class,
                () -> service.validate("IMP-006", new ByteArrayInputStream(new byte[]{1})));
    }

    private ImportTaskServiceImpl serviceWithPersistence(String taskNo, String batchNo)
            throws Exception {
        FakeBatchRepository batches = new FakeBatchRepository();
        batches.save(batch(batchNo));
        FakeImportTaskRepository tasks = new FakeImportTaskRepository();
        tasks.save(task(taskNo, batchNo));
        ImportTaskServiceImpl service = newService(batches, tasks);
        setField(service, "factReader", new EmptyFactReader());
        setField(service, "sourceReader", new EmptySourceReader());
        setField(service, "factWriter", new RecordingFactWriter());
        setField(service, "sourceWriter", new RecordingSourceWriter());
        return service;
    }

    private ImportTaskServiceImpl newService(FakeBatchRepository batches,
                                              FakeImportTaskRepository tasks) {
        return new ImportTaskServiceImpl(batches, tasks, new FakeDatasetCatalog(),
                new FakeTemplateValidator(), new FakeImportIssueRepository(),
                new FakeImportFileRepository());
    }

    private AnalysisBatch batch(String batchNo) {
        return new AnalysisBatch(1L, batchNo, "2026-09", AnalysisPerspective.BASE,
                BatchStatus.DRAFT, "tester", LocalDateTime.now());
    }

    private ImportTask task(String taskNo, String batchNo) {
        return new ImportTask(1L, taskNo, batchNo, "BASE_ACT_INC_COST",
                "actual.xlsx", "sha256", "tester", LocalDateTime.now());
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = ImportTaskServiceImpl.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static class EmptyFactReader implements PnlFactTaskReader {
        @Override
        public List<PnlFact> read(AnalysisBatch batch, ImportTask task) {
            return Collections.emptyList();
        }
    }

    private static class EmptySourceReader implements PnlFactSourceReader {
        @Override
        public List<PnlFactSource> readSources(AnalysisBatch batch, ImportTask task) {
            return Collections.emptyList();
        }
    }

    private static class RecordingFactWriter implements PnlFactWriter {
        private AnalysisBatch batch;
        private ImportTask task;

        @Override
        public void replace(AnalysisBatch batch, ImportTask task, List<PnlFact> facts) {
            this.batch = batch;
            this.task = task;
        }
    }

    private static class RecordingSourceWriter implements PnlFactSourceWriter {
        private AnalysisBatch batch;
        private ImportTask task;

        @Override
        public void replace(AnalysisBatch batch, ImportTask task, List<PnlFactSource> sources) {
            this.batch = batch;
            this.task = task;
        }

        @Override
        public List<PnlFactSource> findByBatchNoAndPerspectiveAndTaskNo(
                String batchNo, AnalysisPerspective perspective, String taskNo) {
            return Collections.emptyList();
        }
    }

    private static class ThrowingSourceWriter extends RecordingSourceWriter {
        @Override
        public void replace(AnalysisBatch batch, ImportTask task, List<PnlFactSource> sources) {
            throw new IllegalStateException("source write failed");
        }
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

    private static class FakeImportIssueRepository implements ImportIssueRepository {
        @Override
        public void replace(String taskNo, List<com.sunwoda.evb.finance.analysis.domain.model.ImportIssue> issues) {
        }

        @Override
        public List<com.sunwoda.evb.finance.analysis.domain.model.ImportIssue> findByTaskNo(String taskNo) {
            return Collections.emptyList();
        }
    }

    private static class FakeImportFileRepository implements ImportFileRepository {
        @Override
        public void save(String taskNo, String fileName, byte[] content) {
        }

        @Override
        public InputStream open(String taskNo) {
            return new ByteArrayInputStream(new byte[0]);
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

        @Override
        public List<ImportTask> findByBatchNo(String batchNo) {
            return Collections.emptyList();
        }
    }
}
