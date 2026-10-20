package com.sunwoda.evb.finance.analysis.infrastructure.excel;

import com.sunwoda.evb.finance.analysis.application.port.DatasetCatalog;
import com.sunwoda.evb.finance.analysis.application.port.PnlFactSourceReader;
import com.sunwoda.evb.finance.analysis.application.port.PnlFactTaskReader;
import com.sunwoda.evb.finance.analysis.application.port.TemplateValidator;
import com.sunwoda.evb.finance.analysis.application.service.ImportTaskServiceImpl;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.BatchStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ImportIssue;
import com.sunwoda.evb.finance.analysis.domain.model.ImportStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ImportTask;
import com.sunwoda.evb.finance.analysis.domain.model.ImportValidationResult;
import com.sunwoda.evb.finance.analysis.domain.repository.AnalysisBatchRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.ImportFileRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.ImportIssueRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.ImportTaskRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.PnlFactSourceWriter;
import com.sunwoda.evb.finance.analysis.domain.repository.PnlFactWriter;
import com.sunwoda.evb.finance.analysis.infrastructure.filesystem.FileSystemImportFileRepository;
import com.sunwoda.evb.finance.analysis.infrastructure.jdbc.JdbcAnalysisBatchRepository;
import com.sunwoda.evb.finance.analysis.infrastructure.jdbc.JdbcImportIssueRepository;
import com.sunwoda.evb.finance.analysis.infrastructure.jdbc.JdbcImportTaskRepository;
import com.sunwoda.evb.finance.analysis.infrastructure.jdbc.JdbcPnlFactSourceWriter;
import com.sunwoda.evb.finance.analysis.infrastructure.jdbc.JdbcPnlFactWriter;
import com.sunwoda.evb.finance.analysis.infrastructure.memory.InMemoryDatasetCatalog;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * TEST-DATA-002-B-INVALID-001: invalid/corrupt XLSX PostgreSQL contract.
 *
 * <p>The invocation must go through the Spring transaction proxy around
 * {@link ImportTaskServiceImpl}. A real PostgreSQL task is validated with
 * malformed XLSX input, and the persisted task must become INVALID with an
 * issue row while no fact or provenance row is created for the random batch.</p>
 *
 * <p>Set {@code AMOEBA_DB_URL} (and optionally
 * {@code AMOEBA_DB_USERNAME}/{@code AMOEBA_DB_PASSWORD}) to run this opt-in
 * contract test. Each test owns and removes its own random batch.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PnlFactImportPostgresInvalidXlsxContractTest {
    private DataSource dataSource;
    private JdbcTemplate jdbc;
    private AnnotationConfigApplicationContext context;
    private ImportTaskServiceImpl service;
    private AnalysisBatchRepository batches;
    private ImportTaskRepository tasks;
    private ImportIssueRepository issues;
    private static Path storageRoot;
    private String batchNo;

    @BeforeAll
    void startRealSpringContext() throws Exception {
        String url = System.getenv("AMOEBA_DB_URL");
        assumeTrue(url != null && !url.trim().isEmpty(),
                "BLOCKED: AMOEBA_DB_URL is not set; invalid XLSX PostgreSQL contract was skipped");

        dataSource = new DriverManagerDataSource(
                url, environment("AMOEBA_DB_USERNAME"), environment("AMOEBA_DB_PASSWORD"));
        jdbc = new JdbcTemplate(dataSource);
        verifySchema();

        storageRoot = Files.createTempDirectory("amoeba-invalid-xlsx-");
        context = new AnnotationConfigApplicationContext();
        context.register(TestConfig.class);
        context.refresh();

        service = context.getBean(ImportTaskServiceImpl.class);
        batches = context.getBean(AnalysisBatchRepository.class);
        tasks = context.getBean(ImportTaskRepository.class);
        issues = context.getBean(ImportIssueRepository.class);

        assertTrue(AopUtils.isAopProxy(service),
                "ImportTaskServiceImpl must be invoked through a Spring transaction proxy");
        assertTrue(AopUtils.isCglibProxy(service),
                "ImportTaskServiceImpl must use the concrete-service CGLIB proxy");
    }

    @AfterEach
    void cleanupRandomBatch() {
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
    }

    @AfterAll
    void closeSpringContextAndStorage() throws Exception {
        if (context != null) context.close();
        if (storageRoot == null) return;
        try (java.util.stream.Stream<Path> paths = Files.walk(storageRoot)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (java.io.IOException ex) {
                    throw new IllegalStateException("非法XLSX测试目录清理失败: " + path, ex);
                }
            });
        }
    }

    @Test
    void truncatedXlsxIsInvalidWithIssueAndCreatesNoFactOrTraceRows() throws Exception {
        assertInvalidUploadDoesNotWriteFactsOrTrace(truncatedXlsxBytes());
    }

    @Test
    void nonXlsxPayloadIsInvalidWithIssueAndCreatesNoFactOrTraceRows() {
        assertInvalidUploadDoesNotWriteFactsOrTrace(
                "not-an-xlsx-TEST-DATA-002-B-INVALID-001".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private void assertInvalidUploadDoesNotWriteFactsOrTrace(byte[] invalidXlsx) {
        batchNo = "INVALID-XLSX-DB-" + UUID.randomUUID().toString().replace("-", "");
        AnalysisBatch batch = batches.save(new AnalysisBatch(
                null, batchNo, "2026-10", AnalysisPerspective.BASE,
                BatchStatus.DRAFT, "TEST-DATA-002-B-INVALID-001", LocalDateTime.now()));
        ImportTask task = service.create(batchNo, "BASE_ACT_INC_COST", "damaged.xlsx",
                "invalid-xlsx-checksum-" + UUID.randomUUID(),
                "TEST-DATA-002-B-INVALID-001");

        int factCountBefore = count("SELECT COUNT(*) FROM raw.pnl_fact WHERE batch_id = ?", batch.getId());
        int traceCountBefore = count("SELECT COUNT(*) FROM raw.pnl_fact_trace WHERE batch_id = ?", batch.getId());
        ImportValidationResult result = service.validate(
                task.getTaskNo(), new ByteArrayInputStream(invalidXlsx));

        assertEquals(ImportStatus.INVALID, result.getStatus());
        assertFalse(result.getIssues().isEmpty(), "invalid XLSX must produce validation issues");
        assertTrue(result.getIssues().stream().anyMatch(issue -> "FILE_INVALID".equals(issue.getIssueCode())),
                "malformed XLSX must be recorded as FILE_INVALID");

        ImportTask persistedTask = tasks.findByTaskNo(task.getTaskNo())
                .orElseThrow(() -> new AssertionError("import task was not persisted"));
        assertEquals(ImportStatus.INVALID, persistedTask.getStatus());
        assertEquals(result.getIssues().size(), persistedTask.getIssueCount());

        List<ImportIssue> persistedIssues = issues.findByTaskNo(task.getTaskNo());
        assertFalse(persistedIssues.isEmpty(), "invalid XLSX must persist issue rows");
        assertEquals(result.getIssues().size(), persistedIssues.size());
        assertTrue(persistedIssues.stream().anyMatch(issue -> "FILE_INVALID".equals(issue.getIssueCode())));

        assertEquals(factCountBefore,
                count("SELECT COUNT(*) FROM raw.pnl_fact WHERE batch_id = ?", batch.getId()),
                "invalid XLSX must not create raw.pnl_fact rows");
        assertEquals(traceCountBefore,
                count("SELECT COUNT(*) FROM raw.pnl_fact_trace WHERE batch_id = ?", batch.getId()),
                "invalid XLSX must not create raw.pnl_fact_trace rows");
    }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TestConfig {
        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource(
                    System.getenv("AMOEBA_DB_URL"),
                    environment("AMOEBA_DB_USERNAME"), environment("AMOEBA_DB_PASSWORD"));
        }

        @Bean
        DataSourceTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        JdbcAnalysisBatchRepository analysisBatchRepository(DataSource dataSource) {
            return new JdbcAnalysisBatchRepository(dataSource);
        }

        @Bean
        JdbcImportTaskRepository importTaskRepository(DataSource dataSource) {
            return new JdbcImportTaskRepository(dataSource);
        }

        @Bean
        JdbcImportIssueRepository importIssueRepository(DataSource dataSource) {
            return new JdbcImportIssueRepository(dataSource);
        }

        @Bean
        ImportFileRepository importFileRepository() {
            return new FileSystemImportFileRepository(storageRoot.toString());
        }

        @Bean
        DatasetCatalog datasetCatalog() {
            return new InMemoryDatasetCatalog();
        }

        @Bean
        TemplateValidator templateValidator() {
            return new PoiTemplateValidator();
        }

        @Bean
        PnlFactTaskReader factReader(ImportTaskRepository tasks, ImportFileRepository files) {
            return new PoiPnlFactProvider(tasks, files);
        }

        @Bean
        PnlFactSourceReader sourceReader(ImportFileRepository files) {
            return new PoiPnlFactSourceReader(files);
        }

        @Bean
        @Primary
        PnlFactWriter factWriter(DataSource dataSource) {
            return new JdbcPnlFactWriter(dataSource);
        }

        @Bean
        @Primary
        PnlFactSourceWriter sourceWriter(DataSource dataSource) {
            return new JdbcPnlFactSourceWriter(dataSource);
        }

        @Bean
        ImportTaskServiceImpl importTaskService(AnalysisBatchRepository batches,
                                                ImportTaskRepository tasks,
                                                DatasetCatalog catalog,
                                                TemplateValidator validator,
                                                ImportIssueRepository issues,
                                                ImportFileRepository files) {
            return new ImportTaskServiceImpl(batches, tasks, catalog, validator, issues, files);
        }
    }

    private int count(String sql, Object value) {
        Integer count = jdbc.queryForObject(sql, Integer.class, value);
        return count == null ? 0 : count;
    }

    private void verifySchema() {
        try {
            jdbc.execute("SELECT 1 FROM meta.analysis_batch LIMIT 0");
            jdbc.execute("SELECT 1 FROM flow.import_task LIMIT 0");
            jdbc.execute("SELECT 1 FROM flow.import_issue LIMIT 0");
            jdbc.execute("SELECT 1 FROM raw.pnl_fact LIMIT 0");
            jdbc.execute("SELECT 1 FROM raw.pnl_fact_trace LIMIT 0");
        } catch (RuntimeException failure) {
            assumeTrue(false, "BLOCKED: invalid XLSX PostgreSQL schema is not available: "
                    + failure.getMessage());
        }
    }

    private byte[] truncatedXlsxBytes() throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        try {
            workbook.createSheet("实际明细").createRow(0).createCell(0).setCellValue("期间");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            workbook.write(output);
            byte[] validXlsx = output.toByteArray();
            return Arrays.copyOf(validXlsx, Math.max(1, validXlsx.length - 32));
        } finally {
            workbook.close();
        }
    }

    private static String environment(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value;
    }
}
