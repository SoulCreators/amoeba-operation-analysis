package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.application.port.DatasetCatalog;
import com.sunwoda.evb.finance.analysis.application.port.PnlFactSourceReader;
import com.sunwoda.evb.finance.analysis.application.port.PnlFactTaskReader;
import com.sunwoda.evb.finance.analysis.application.port.TemplateValidator;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.BatchStatus;
import com.sunwoda.evb.finance.analysis.domain.model.DatasetDefinition;
import com.sunwoda.evb.finance.analysis.domain.model.ImportIssue;
import com.sunwoda.evb.finance.analysis.domain.model.ImportStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ImportTask;
import com.sunwoda.evb.finance.analysis.domain.model.ImportValidationResult;
import com.sunwoda.evb.finance.analysis.domain.model.PnlFact;
import com.sunwoda.evb.finance.analysis.domain.model.PnlFactSource;
import com.sunwoda.evb.finance.analysis.domain.repository.AnalysisBatchRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.ImportFileRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.ImportIssueRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.ImportTaskRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.PnlFactSourceWriter;
import com.sunwoda.evb.finance.analysis.domain.repository.PnlFactWriter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Locks the application-service replacement boundary for one import task.
 *
 * <p>The recording writers intentionally do not model database deletion. This
 * test therefore proves only that every validation reads and delegates the
 * current facts and sources with the same batch/task identity; old-row deletion
 * remains an infrastructure/database contract to be verified separately.</p>
 */
class ImportTaskServiceReplacementContractTest {
    @Test
    void validatesTwiceAndReplacesLatestFactsAndSourcesForSameBatchAndTask() throws Exception {
        AnalysisBatch batch = batch("B-REPLACE-001");
        ImportTask task = task("IMP-REPLACE-001", batch.getBatchNo());
        FakeBatchRepository batches = new FakeBatchRepository();
        batches.save(batch);
        FakeImportTaskRepository tasks = new FakeImportTaskRepository();
        tasks.save(task);
        tasks.save(task("IMP-REPLACE-DECOY", "B-REPLACE-DECOY"));
        batches.save(batch("B-REPLACE-DECOY"));

        List<PnlFact> firstFacts = Collections.singletonList(fact("FACT-FIRST"));
        List<PnlFact> secondFacts = Collections.singletonList(fact("FACT-SECOND"));
        List<PnlFactSource> firstSources = Collections.singletonList(source(task.getTaskNo(), 2));
        List<PnlFactSource> secondSources = Collections.singletonList(source(task.getTaskNo(), 3));

        RecordingFactReader factReader = new RecordingFactReader(firstFacts, secondFacts);
        RecordingSourceReader sourceReader = new RecordingSourceReader(firstSources, secondSources);
        RecordingFactWriter factWriter = new RecordingFactWriter();
        RecordingSourceWriter sourceWriter = new RecordingSourceWriter();
        ImportTaskServiceImpl service = service(batches, tasks, factReader, sourceReader,
                factWriter, sourceWriter);

        service.validate(task.getTaskNo(), new ByteArrayInputStream(new byte[]{1}));
        service.validate(task.getTaskNo(), new ByteArrayInputStream(new byte[]{2}));

        assertEquals(2, factReader.calls.size());
        assertEquals(2, sourceReader.calls.size());
        assertEquals(2, factWriter.calls.size());
        assertEquals(2, sourceWriter.calls.size());

        assertSame(batch, factReader.calls.get(0).batch);
        assertSame(task, factReader.calls.get(0).task);
        assertSame(batch, factReader.calls.get(1).batch);
        assertSame(task, factReader.calls.get(1).task);
        assertSame(firstFacts, factReader.calls.get(0).returnedFacts);
        assertSame(secondFacts, factReader.calls.get(1).returnedFacts);

        assertSame(batch, sourceReader.calls.get(0).batch);
        assertSame(task, sourceReader.calls.get(0).task);
        assertSame(batch, sourceReader.calls.get(1).batch);
        assertSame(task, sourceReader.calls.get(1).task);
        assertSame(firstSources, sourceReader.calls.get(0).returnedSources);
        assertSame(secondSources, sourceReader.calls.get(1).returnedSources);

        assertReplaceCall(factWriter.calls.get(0), batch, task, firstFacts);
        assertReplaceCall(factWriter.calls.get(1), batch, task, secondFacts);
        assertReplaceCall(sourceWriter.calls.get(0), batch, task, firstSources);
        assertReplaceCall(sourceWriter.calls.get(1), batch, task, secondSources);
        assertEquals(batch.getBatchNo(), task.getBatchNo());
    }

    @Test
    void replacementNeverCrossesTaskOrBatchWhenAnotherImportExists() throws Exception {
        AnalysisBatch expectedBatch = batch("B-REPLACE-002");
        ImportTask expectedTask = task("IMP-REPLACE-002", expectedBatch.getBatchNo());
        AnalysisBatch otherBatch = batch("B-REPLACE-OTHER");
        ImportTask otherTask = task("IMP-REPLACE-OTHER", otherBatch.getBatchNo());
        FakeBatchRepository batches = new FakeBatchRepository();
        batches.save(expectedBatch);
        batches.save(otherBatch);
        FakeImportTaskRepository tasks = new FakeImportTaskRepository();
        tasks.save(expectedTask);
        tasks.save(otherTask);

        List<PnlFact> facts = Collections.singletonList(fact("FACT-EXPECTED"));
        List<PnlFactSource> sources = Collections.singletonList(source(expectedTask.getTaskNo(), 4));
        RecordingFactWriter factWriter = new RecordingFactWriter();
        RecordingSourceWriter sourceWriter = new RecordingSourceWriter();
        ImportTaskServiceImpl service = service(batches, tasks,
                new RecordingFactReader(facts), new RecordingSourceReader(sources),
                factWriter, sourceWriter);

        service.validate(expectedTask.getTaskNo(), new ByteArrayInputStream(new byte[]{9}));

        assertEquals(1, factWriter.calls.size());
        assertEquals(1, sourceWriter.calls.size());
        assertReplaceCall(factWriter.calls.get(0), expectedBatch, expectedTask, facts);
        assertReplaceCall(sourceWriter.calls.get(0), expectedBatch, expectedTask, sources);
        assertEquals(expectedBatch.getBatchNo(), expectedTask.getBatchNo());
        assertEquals(otherBatch.getBatchNo(), otherTask.getBatchNo());
    }

    private ImportTaskServiceImpl service(FakeBatchRepository batches,
                                          FakeImportTaskRepository tasks,
                                          RecordingFactReader factReader,
                                          RecordingSourceReader sourceReader,
                                          RecordingFactWriter factWriter,
                                          RecordingSourceWriter sourceWriter) throws Exception {
        ImportTaskServiceImpl service = new ImportTaskServiceImpl(batches, tasks,
                new FakeDatasetCatalog(), new FakeTemplateValidator(),
                new FakeImportIssueRepository(), new FakeImportFileRepository());
        setField(service, "factReader", factReader);
        setField(service, "sourceReader", sourceReader);
        setField(service, "factWriter", factWriter);
        setField(service, "sourceWriter", sourceWriter);
        return service;
    }

    private void assertReplaceCall(FactReplaceCall actual, AnalysisBatch batch,
                                   ImportTask task, List<PnlFact> facts) {
        assertSame(batch, actual.batch);
        assertSame(task, actual.task);
        assertSame(facts, actual.facts);
        assertEquals(batch.getBatchNo(), actual.batch.getBatchNo());
        assertEquals(task.getTaskNo(), actual.task.getTaskNo());
        assertEquals(batch.getBatchNo(), task.getBatchNo());
    }

    private void assertReplaceCall(SourceReplaceCall actual, AnalysisBatch batch,
                                   ImportTask task, List<PnlFactSource> sources) {
        assertSame(batch, actual.batch);
        assertSame(task, actual.task);
        assertSame(sources, actual.sources);
        assertEquals(batch.getBatchNo(), actual.batch.getBatchNo());
        assertEquals(task.getTaskNo(), actual.task.getTaskNo());
        assertEquals(batch.getBatchNo(), task.getBatchNo());
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = ImportTaskServiceImpl.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private AnalysisBatch batch(String batchNo) {
        return new AnalysisBatch(1L, batchNo, "2026-09", AnalysisPerspective.BASE,
                BatchStatus.DRAFT, "tester", LocalDateTime.now());
    }

    private ImportTask task(String taskNo, String batchNo) {
        return new ImportTask(1L, taskNo, batchNo, "BASE_ACT_INC_COST",
                "actual.xlsx", "checksum", "tester", LocalDateTime.now());
    }

    private PnlFact fact(String scopeCode) {
        return new PnlFact(scopeCode, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private PnlFactSource source(String taskNo, int rowNo) {
        return new PnlFactSource(taskNo, "actual.xlsx", "checksum", "Sheet1", rowNo,
                "SCOPE-" + rowNo, "BASE-1", "PRODUCT-1", "LINE-1");
    }

    private static class RecordingFactReader implements PnlFactTaskReader {
        private final Deque<List<PnlFact>> results = new ArrayDeque<List<PnlFact>>();
        private final List<ReaderCall> calls = new ArrayList<ReaderCall>();

        private RecordingFactReader(List<PnlFact>... results) {
            Collections.addAll(this.results, results);
        }

        @Override
        public List<PnlFact> read(AnalysisBatch batch, ImportTask task) {
            List<PnlFact> returnedFacts = this.results.removeFirst();
            calls.add(new ReaderCall(batch, task, returnedFacts));
            return returnedFacts;
        }
    }

    private static class RecordingSourceReader implements PnlFactSourceReader {
        private final Deque<List<PnlFactSource>> results = new ArrayDeque<List<PnlFactSource>>();
        private final List<SourceReaderCall> calls = new ArrayList<SourceReaderCall>();

        private RecordingSourceReader(List<PnlFactSource>... results) {
            Collections.addAll(this.results, results);
        }

        @Override
        public List<PnlFactSource> readSources(AnalysisBatch batch, ImportTask task) {
            List<PnlFactSource> returnedSources = this.results.removeFirst();
            calls.add(new SourceReaderCall(batch, task, returnedSources));
            return returnedSources;
        }
    }

    private static class RecordingFactWriter implements PnlFactWriter {
        private final List<FactReplaceCall> calls = new ArrayList<FactReplaceCall>();

        @Override
        public void replace(AnalysisBatch batch, ImportTask task, List<PnlFact> facts) {
            calls.add(new FactReplaceCall(batch, task, facts));
        }
    }

    private static class RecordingSourceWriter implements PnlFactSourceWriter {
        private final List<SourceReplaceCall> calls = new ArrayList<SourceReplaceCall>();

        @Override
        public void replace(AnalysisBatch batch, ImportTask task, List<PnlFactSource> sources) {
            calls.add(new SourceReplaceCall(batch, task, sources));
        }

        @Override
        public List<PnlFactSource> findByBatchNoAndPerspectiveAndTaskNo(
                String batchNo, AnalysisPerspective perspective, String taskNo) {
            return Collections.emptyList();
        }
    }

    private static class ReaderCall {
        private final AnalysisBatch batch;
        private final ImportTask task;
        private final List<PnlFact> returnedFacts;

        private ReaderCall(AnalysisBatch batch, ImportTask task, List<PnlFact> returnedFacts) {
            this.batch = batch;
            this.task = task;
            this.returnedFacts = returnedFacts;
        }
    }

    private static class SourceReaderCall {
        private final AnalysisBatch batch;
        private final ImportTask task;
        private final List<PnlFactSource> returnedSources;

        private SourceReaderCall(AnalysisBatch batch, ImportTask task,
                                 List<PnlFactSource> returnedSources) {
            this.batch = batch;
            this.task = task;
            this.returnedSources = returnedSources;
        }
    }

    private static class FactReplaceCall {
        private final AnalysisBatch batch;
        private final ImportTask task;
        private final List<PnlFact> facts;

        private FactReplaceCall(AnalysisBatch batch, ImportTask task, List<PnlFact> facts) {
            this.batch = batch;
            this.task = task;
            this.facts = facts;
        }
    }

    private static class SourceReplaceCall {
        private final AnalysisBatch batch;
        private final ImportTask task;
        private final List<PnlFactSource> sources;

        private SourceReplaceCall(AnalysisBatch batch, ImportTask task,
                                  List<PnlFactSource> sources) {
            this.batch = batch;
            this.task = task;
            this.sources = sources;
        }
    }

    private static class FakeDatasetCatalog implements DatasetCatalog {
        @Override
        public List<DatasetDefinition> list(AnalysisPerspective perspective) {
            return Collections.emptyList();
        }

        @Override
        public DatasetDefinition require(String datasetCode, AnalysisPerspective perspective) {
            return new DatasetDefinition(datasetCode, "测试数据集", "MONTHLY", true,
                    Collections.singletonList(perspective));
        }
    }

    private static class FakeTemplateValidator implements TemplateValidator {
        @Override
        public ImportValidationResult validate(String taskNo, DatasetDefinition definition,
                                               InputStream inputStream) {
            return new ImportValidationResult(taskNo, ImportStatus.VALID, 0,
                    Collections.<String>emptyList(), Collections.<ImportIssue>emptyList());
        }
    }

    private static class FakeImportIssueRepository implements ImportIssueRepository {
        @Override
        public void replace(String taskNo, List<ImportIssue> issues) {
        }

        @Override
        public List<ImportIssue> findByTaskNo(String taskNo) {
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
            return false;
        }

        @Override
        public List<ImportTask> findByBatchNo(String batchNo) {
            return Collections.emptyList();
        }
    }
}
