package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.application.port.DatasetCatalog;
import com.sunwoda.evb.finance.analysis.application.port.PnlFactSourceReader;
import com.sunwoda.evb.finance.analysis.application.port.PnlFactTaskReader;
import com.sunwoda.evb.finance.analysis.application.port.TemplateValidator;
import com.sunwoda.evb.finance.analysis.application.service.ImportTaskServiceImpl;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.BatchStatus;
import com.sunwoda.evb.finance.analysis.domain.model.DatasetDefinition;
import com.sunwoda.evb.finance.analysis.domain.model.ImportStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ImportTask;
import com.sunwoda.evb.finance.analysis.domain.model.ImportValidationResult;
import com.sunwoda.evb.finance.analysis.domain.model.PnlFact;
import com.sunwoda.evb.finance.analysis.domain.model.PnlFactSource;
import com.sunwoda.evb.finance.analysis.domain.repository.AnalysisBatchRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.ImportFileRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.PnlFactWriter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Real PostgreSQL contract for the current import replacement boundary.
 *
 * <p>This test deliberately checks the current implementation rather than
 * inventing version governance: one task is replaced in place, and the
 * current schema has no imported-fact version-history contract.</p>
 *
 * <p>It is opt-in through {@code AMOEBA_DB_URL}. Every test creates a unique
 * batch and removes all rows belonging to that batch in {@code @AfterEach}.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ImportReplacementPostgresContractTest {
    private static final String DATASET_CODE = "P_AND_L_FACTS";

    private DataSource dataSource;
    private JdbcTemplate jdbc;
    private JdbcImportTaskRepository tasks;
    private JdbcImportIssueRepository issues;
    private JdbcPnlFactWriter factWriter;
    private JdbcPnlFactSourceWriter sourceWriter;
    private String batchNo;
    private Long batchId;

    @BeforeAll
    void connectToConfiguredPostgresAndVerifySchema() {
        String url = System.getenv("AMOEBA_DB_URL");
        assumeTrue(url != null && !url.trim().isEmpty(),
                "BLOCKED: AMOEBA_DB_URL is not set; PostgreSQL replacement contract was skipped");

        dataSource = new DriverManagerDataSource(
                url, environment("AMOEBA_DB_USERNAME"), environment("AMOEBA_DB_PASSWORD"));
        jdbc = new JdbcTemplate(dataSource);
        try {
            jdbc.execute("SELECT 1 FROM meta.analysis_batch LIMIT 0");
            jdbc.execute("SELECT 1 FROM flow.import_task LIMIT 0");
            jdbc.execute("SELECT 1 FROM flow.import_issue LIMIT 0");
            jdbc.execute("SELECT 1 FROM raw.pnl_fact LIMIT 0");
            jdbc.execute("SELECT 1 FROM raw.pnl_fact_trace LIMIT 0");
        } catch (RuntimeException failure) {
            assumeTrue(false, "BLOCKED: import replacement PostgreSQL schema is not available: "
                    + failure.getMessage());
        }

        tasks = new JdbcImportTaskRepository(dataSource);
        issues = new JdbcImportIssueRepository(dataSource);
        factWriter = new JdbcPnlFactWriter(dataSource);
        sourceWriter = new JdbcPnlFactSourceWriter(dataSource);
    }

    @BeforeEach
    void createUniqueBatch() {
        batchNo = "REPLACE-DB-" + UUID.randomUUID().toString().replace("-", "");
        jdbc.update("INSERT INTO meta.analysis_batch(batch_no, period, perspective, status, created_by) "
                        + "VALUES (?, ?, ?, ?, ?)", batchNo, "2026-10", "BASE", "DRAFT",
                "IMPORT-REPLACE-DB-001");
        batchId = jdbc.queryForObject("SELECT id FROM meta.analysis_batch WHERE batch_no = ?",
                Long.class, batchNo);
    }

    @AfterEach
    void removeUniqueBatch() {
        if (jdbc == null || batchNo == null) return;
        jdbc.update("DELETE FROM raw.pnl_fact_trace WHERE batch_id IN "
                + "(SELECT id FROM meta.analysis_batch WHERE batch_no = ?)", batchNo);
        jdbc.update("DELETE FROM raw.pnl_fact WHERE batch_id IN "
                + "(SELECT id FROM meta.analysis_batch WHERE batch_no = ?)", batchNo);
        jdbc.update("DELETE FROM flow.import_issue WHERE task_id IN "
                + "(SELECT id FROM flow.import_task WHERE batch_id IN "
                + "(SELECT id FROM meta.analysis_batch WHERE batch_no = ?))", batchNo);
        jdbc.update("DELETE FROM flow.import_task WHERE batch_id IN "
                + "(SELECT id FROM meta.analysis_batch WHERE batch_no = ?)", batchNo);
        jdbc.update("DELETE FROM meta.analysis_batch WHERE batch_no = ?", batchNo);
        batchNo = null;
        batchId = null;
    }

    @AfterAll
    void closeResources() {
        // DriverManagerDataSource owns no pool or connection that needs closing.
        dataSource = null;
        jdbc = null;
    }

    @Test
    void sameBatchAndDatasetHasOneTaskAtApplicationAndDatabaseBoundaries() {
        ImportTaskServiceImpl service = service(new VersionedReaders(
                Collections.singletonList(fact("SCOPE-UNUSED", "0")),
                Collections.singletonList(source("UNUSED.xlsx", "unused", 2))));
        ImportTask first = service.create(batchNo, DATASET_CODE, "first.xlsx", "first-checksum",
                "replace-tester");

        assertEquals(ImportStatus.CREATED, first.getStatus());
        assertTrue(tasks.existsByBatchAndDataset(batchNo, DATASET_CODE));
        assertEquals(1, countTasks());

        IllegalStateException serviceDuplicate = assertThrows(IllegalStateException.class,
                () -> service.create(batchNo, DATASET_CODE, "second.xlsx", "second-checksum",
                        "replace-tester"));
        assertTrue(serviceDuplicate.getMessage().contains(DATASET_CODE));

        ImportTask repositoryDuplicate = new ImportTask(null,
                "IMP-DUP-" + UUID.randomUUID().toString().replace("-", ""), batchNo,
                DATASET_CODE, "second.xlsx", "second-checksum", "replace-tester",
                LocalDateTime.now());
        assertThrows(DataIntegrityViolationException.class, () -> tasks.save(repositoryDuplicate));
        assertEquals(1, countTasks());
        assertEquals(first.getTaskNo(), tasks.findByBatchNo(batchNo).get(0).getTaskNo());
    }

    @Test
    void twoValidUploadsReplaceFactsAndSourceTraceForTheSameTask() throws Exception {
        byte[] firstUpload = "first-upload-replace-db".getBytes(StandardCharsets.UTF_8);
        byte[] secondUpload = "second-upload-replace-db".getBytes(StandardCharsets.UTF_8);
        RecordingFileRepository files = new RecordingFileRepository();
        VersionedReaders readers = new VersionedReaders(
                Collections.singletonList(fact("SCOPE-OLD", "100")),
                Collections.singletonList(source("old-source.xlsx", "old-checksum", 2)),
                Collections.singletonList(fact("SCOPE-NEW", "250")),
                Collections.singletonList(source("new-source.xlsx", "new-checksum", 8)));
        ImportTaskServiceImpl service = service(readers, files);

        ImportTask task = service.create(batchNo, DATASET_CODE, "replace.xlsx", "initial-checksum",
                "replace-tester");
        assertEquals(ImportStatus.CREATED, tasks.findByTaskNo(task.getTaskNo())
                .orElseThrow(() -> new AssertionError("task was not persisted")).getStatus());

        ImportValidationResult firstResult = service.validate(task.getTaskNo(),
                new ByteArrayInputStream(firstUpload));
        assertEquals(ImportStatus.VALID, firstResult.getStatus());
        assertEquals(ImportStatus.VALID, tasks.findByTaskNo(task.getTaskNo())
                .orElseThrow(() -> new AssertionError("first task state was not persisted")).getStatus());
        assertEquals(1, countFacts(task.getTaskNo()));
        assertEquals(1, sourceWriter.findByBatchNoAndPerspectiveAndTaskNo(
                batchNo, AnalysisPerspective.BASE, task.getTaskNo()).size());

        ImportValidationResult secondResult = service.validate(task.getTaskNo(),
                new ByteArrayInputStream(secondUpload));
        assertEquals(ImportStatus.VALID, secondResult.getStatus());
        assertEquals(ImportStatus.VALID, tasks.findByTaskNo(task.getTaskNo())
                .orElseThrow(() -> new AssertionError("second task state was not persisted")).getStatus());
        assertEquals(0, tasks.findByTaskNo(task.getTaskNo())
                .orElseThrow(() -> new AssertionError("task disappeared")).getIssueCount());

        assertEquals(1, countFacts(task.getTaskNo()));
        assertEquals(0, countFactScope(task.getTaskNo(), "SCOPE-OLD"));
        assertEquals(1, countFactScope(task.getTaskNo(), "SCOPE-NEW"));
        BigDecimal latestRevenue = jdbc.queryForObject(
                "SELECT revenue FROM raw.pnl_fact WHERE batch_id = ? AND perspective = ? "
                        + "AND source_task_no = ? AND scope_code = ?", BigDecimal.class,
                batchId, AnalysisPerspective.BASE.getCode(), task.getTaskNo(), "SCOPE-NEW");
        assertEquals(0, latestRevenue.compareTo(new BigDecimal("250")));

        List<PnlFactSource> latestSources = sourceWriter.findByBatchNoAndPerspectiveAndTaskNo(
                batchNo, AnalysisPerspective.BASE, task.getTaskNo());
        assertEquals(1, latestSources.size());
        assertEquals("new-source.xlsx", latestSources.get(0).getFileName());
        assertEquals("new-checksum", latestSources.get(0).getChecksum());
        assertEquals("Sheet1", latestSources.get(0).getSheetName());
        assertEquals(8, latestSources.get(0).getRowNo());
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM raw.pnl_fact_trace WHERE source_task_no = ? "
                        + "AND source_file_name = ?", Integer.class, task.getTaskNo(),
                "old-source.xlsx").intValue());

        assertEquals(2, files.saveCount);
        assertArrayEquals(secondUpload, files.latestContent);
        assertEquals(2, readers.factReads.get());
        assertEquals(2, readers.sourceReads.get());
        assertTrue(issues.findByTaskNo(task.getTaskNo()).isEmpty());
    }

    @Test
    void twoValidUploadsUpdateTaskAndReplaceSourceTraceEvenBeforeFactWriterIsFixed() {
        RecordingFileRepository files = new RecordingFileRepository();
        VersionedReaders readers = new VersionedReaders(
                Collections.singletonList(fact("SCOPE-OLD", "100")),
                Collections.singletonList(source("old-source.xlsx", "old-checksum", 2)),
                Collections.singletonList(fact("SCOPE-NEW", "250")),
                Collections.singletonList(source("new-source.xlsx", "new-checksum", 8)));
        PnlFactWriter noOpFactWriter = (batch, task, facts) -> { };
        ImportTaskServiceImpl service = service(readers, files, noOpFactWriter);

        ImportTask task = service.create(batchNo, DATASET_CODE, "replace.xlsx", "initial-checksum",
                "replace-tester");
        service.validate(task.getTaskNo(), new ByteArrayInputStream(
                "first-upload-source-only".getBytes(StandardCharsets.UTF_8)));
        service.validate(task.getTaskNo(), new ByteArrayInputStream(
                "second-upload-source-only".getBytes(StandardCharsets.UTF_8)));

        assertEquals(ImportStatus.VALID, tasks.findByTaskNo(task.getTaskNo())
                .orElseThrow(() -> new AssertionError("task was not persisted")).getStatus());
        List<PnlFactSource> latestSources = sourceWriter.findByBatchNoAndPerspectiveAndTaskNo(
                batchNo, AnalysisPerspective.BASE, task.getTaskNo());
        assertEquals(1, latestSources.size());
        assertEquals("new-source.xlsx", latestSources.get(0).getFileName());
        assertEquals("new-checksum", latestSources.get(0).getChecksum());
        assertEquals(8, latestSources.get(0).getRowNo());
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM raw.pnl_fact_trace WHERE source_task_no = ? "
                        + "AND source_file_name = ?", Integer.class, task.getTaskNo(),
                "old-source.xlsx").intValue());
        assertEquals(2, files.saveCount);
        assertEquals(2, readers.factReads.get());
        assertEquals(2, readers.sourceReads.get());
        assertTrue(issues.findByTaskNo(task.getTaskNo()).isEmpty());
    }

    @Test
    void currentSchemaObservesReplacementNotHistoricalVersionRetention() {
        Integer versionColumnCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = 'raw' AND table_name = 'pnl_fact' "
                        + "AND column_name IN ('version_no', 'version_id', 'supersedes_fact_id')",
                Integer.class);
        assertEquals(0, versionColumnCount.intValue(),
                "current raw.pnl_fact has no explicit imported-fact version identity");

        Boolean historyTablePresent = jdbc.queryForObject(
                "SELECT to_regclass('raw.pnl_fact_history') IS NOT NULL", Boolean.class);
        assertFalse(historyTablePresent,
                "current schema has no named raw.pnl_fact_history relation");

        // This is a source observation only; it does not approve any future
        // rule about active versions, retention, or effective timestamps.
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM raw.pnl_fact WHERE batch_id = ?", Integer.class, batchId)
                .intValue());
    }

    private ImportTaskServiceImpl service(VersionedReaders readers) {
        return service(readers, new RecordingFileRepository());
    }

    private ImportTaskServiceImpl service(VersionedReaders readers,
                                          RecordingFileRepository files) {
        return service(readers, files, factWriter);
    }

    private ImportTaskServiceImpl service(VersionedReaders readers,
                                          RecordingFileRepository files,
                                          PnlFactWriter selectedFactWriter) {
        AnalysisBatch batch = new AnalysisBatch(batchId, batchNo, "2026-10",
                AnalysisPerspective.BASE, BatchStatus.DRAFT, "replace-tester", LocalDateTime.now());
        AnalysisBatchRepository batches = new AnalysisBatchRepository() {
            @Override
            public AnalysisBatch save(AnalysisBatch value) {
                return value;
            }

            @Override
            public Optional<AnalysisBatch> findByBatchNo(String requestedBatchNo) {
                return batch.getBatchNo().equals(requestedBatchNo)
                        ? Optional.of(batch) : Optional.<AnalysisBatch>empty();
            }
        };
        DatasetCatalog catalog = new DatasetCatalog() {
            private final DatasetDefinition definition = new DatasetDefinition(
                    DATASET_CODE, "测试损益事实", "MONTHLY", true,
                    Collections.singletonList(AnalysisPerspective.BASE));

            @Override
            public List<DatasetDefinition> list(AnalysisPerspective perspective) {
                return Collections.singletonList(definition);
            }

            @Override
            public DatasetDefinition require(String code, AnalysisPerspective perspective) {
                if (!DATASET_CODE.equals(code) || !definition.supports(perspective)) {
                    throw new IllegalArgumentException("unexpected dataset: " + code);
                }
                return definition;
            }
        };
        TemplateValidator validator = (taskNo, definition, input) -> {
            try {
                readAll(input);
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
            return new ImportValidationResult(taskNo, ImportStatus.VALID, 1,
                    Arrays.asList("scope_code", "revenue"), Collections.emptyList());
        };
        ImportTaskServiceImpl service = new ImportTaskServiceImpl(
                batches, tasks, catalog, validator, issues, files);
        setField(service, "factReader", (PnlFactTaskReader) readers::readFacts);
        setField(service, "sourceReader", (PnlFactSourceReader) readers::readSources);
        setField(service, "factWriter", selectedFactWriter);
        setField(service, "sourceWriter", sourceWriter);
        return service;
    }

    private int countTasks() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM flow.import_task WHERE batch_id = ?",
                Integer.class, batchId);
    }

    private int countFacts(String taskNo) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM raw.pnl_fact WHERE batch_id = ? "
                        + "AND perspective = ? AND source_task_no = ?", Integer.class, batchId,
                AnalysisPerspective.BASE.getCode(), taskNo);
    }

    private int countFactScope(String taskNo, String scopeCode) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM raw.pnl_fact WHERE batch_id = ? "
                        + "AND perspective = ? AND source_task_no = ? AND scope_code = ?",
                Integer.class, batchId, AnalysisPerspective.BASE.getCode(), taskNo, scopeCode);
    }

    private static PnlFact fact(String scopeCode, String revenue) {
        return new PnlFact(scopeCode, BigDecimal.ONE, new BigDecimal(revenue), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private PnlFactSource source(String fileName, String checksum, int rowNo) {
        return new PnlFactSource("PLACEHOLDER", fileName, checksum, "Sheet1", rowNo,
                "SCOPE", null, null, null);
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[256];
        int count;
        while ((count = input.read(buffer)) >= 0) {
            if (count > 0) output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static void setField(Object target, String name, Object value) {
        try {
            java.lang.reflect.Field field = ImportTaskServiceImpl.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("cannot wire test reader/writer: " + name, ex);
        }
    }

    private static String environment(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value;
    }

    private static final class VersionedReaders {
        private final List<PnlFact> firstFacts;
        private final List<PnlFactSource> firstSources;
        private final List<PnlFact> secondFacts;
        private final List<PnlFactSource> secondSources;
        private final AtomicInteger factReads = new AtomicInteger();
        private final AtomicInteger sourceReads = new AtomicInteger();

        private VersionedReaders(List<PnlFact> firstFacts, List<PnlFactSource> firstSources) {
            this(firstFacts, firstSources, firstFacts, firstSources);
        }

        private VersionedReaders(List<PnlFact> firstFacts, List<PnlFactSource> firstSources,
                                 List<PnlFact> secondFacts, List<PnlFactSource> secondSources) {
            this.firstFacts = firstFacts;
            this.firstSources = firstSources;
            this.secondFacts = secondFacts;
            this.secondSources = secondSources;
        }

        private List<PnlFact> readFacts(AnalysisBatch batch, ImportTask task) {
            return factReads.incrementAndGet() == 1 ? firstFacts : secondFacts;
        }

        private List<PnlFactSource> readSources(AnalysisBatch batch, ImportTask task) {
            sourceReads.incrementAndGet();
            List<PnlFactSource> selected = sourceReads.get() == 1 ? firstSources : secondSources;
            return copyWithTask(selected, task.getTaskNo());
        }

        private List<PnlFactSource> copyWithTask(List<PnlFactSource> sources, String taskNo) {
            java.util.ArrayList<PnlFactSource> result = new java.util.ArrayList<PnlFactSource>();
            for (PnlFactSource source : sources) {
                result.add(new PnlFactSource(taskNo, source.getFileName(), source.getChecksum(),
                        source.getSheetName(), source.getRowNo(), source.getScopeCode(),
                        source.getBaseCode(), source.getProductCode(), source.getLineCode()));
            }
            return result;
        }
    }

    private static final class RecordingFileRepository implements ImportFileRepository {
        private int saveCount;
        private byte[] latestContent;

        @Override
        public void save(String taskNo, String fileName, byte[] content) {
            saveCount++;
            latestContent = content == null ? null : Arrays.copyOf(content, content.length);
        }

        @Override
        public InputStream open(String taskNo) {
            return new ByteArrayInputStream(latestContent == null
                    ? new byte[0] : Arrays.copyOf(latestContent, latestContent.length));
        }
    }
}
