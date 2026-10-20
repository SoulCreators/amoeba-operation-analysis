package com.sunwoda.evb.finance.analysis.infrastructure.excel;

import com.sunwoda.evb.finance.analysis.application.port.DatasetCatalog;
import com.sunwoda.evb.finance.analysis.application.port.PnlFactSourceReader;
import com.sunwoda.evb.finance.analysis.application.port.PnlFactTaskReader;
import com.sunwoda.evb.finance.analysis.application.port.TemplateValidator;
import com.sunwoda.evb.finance.analysis.application.service.ImportTaskServiceImpl;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.BatchStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ImportTask;
import com.sunwoda.evb.finance.analysis.domain.model.PnlFact;
import com.sunwoda.evb.finance.analysis.domain.model.PnlFactSource;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * TEST-DATA-002-B-TX-002: real PostgreSQL transaction rollback contract.
 *
 * <p>The test uses a real Spring CGLIB proxy around {@link ImportTaskServiceImpl},
 * real JDBC fact/source writers, and the real XLSX import readers. The source
 * writer delegates its complete PostgreSQL write and then raises a test-only
 * failure. The assertion is made through a separate JDBC query after Spring
 * has rolled the transaction back, so a half-written fact/trace pair cannot
 * satisfy the contract.</p>
 *
 * <p>Set {@code AMOEBA_DB_URL} (and optionally {@code AMOEBA_DB_USERNAME} /
 * {@code AMOEBA_DB_PASSWORD}) to execute this opt-in contract.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PnlFactImportPostgresRollbackContractTest {
    private static Path storageRoot;
    private AnnotationConfigApplicationContext context;
    private DataSource dataSource;
    private JdbcTemplate jdbc;
    private ImportTaskServiceImpl service;
    private AnalysisBatchRepository batches;
    private ImportTaskRepository tasks;
    private RollbackProbe probe;
    private JdbcPnlFactWriter realFactWriter;
    private JdbcPnlFactSourceWriter realTraceWriter;
    private String batchNo;
    private String taskNo;

    @BeforeAll
    void startRealSpringContext() throws Exception {
        String url = System.getenv("AMOEBA_DB_URL");
        assumeTrue(url != null && !url.trim().isEmpty(),
                "BLOCKED: AMOEBA_DB_URL is not set; PostgreSQL rollback contract was skipped");

        dataSource = new DriverManagerDataSource(
                url, environment("AMOEBA_DB_USERNAME"), environment("AMOEBA_DB_PASSWORD"));
        jdbc = new JdbcTemplate(dataSource);
        verifySchema();

        storageRoot = Files.createTempDirectory("amoeba-fact-rollback-");
        context = new AnnotationConfigApplicationContext();
        context.register(TestConfig.class);
        context.refresh();

        service = context.getBean(ImportTaskServiceImpl.class);
        batches = context.getBean(AnalysisBatchRepository.class);
        tasks = context.getBean(ImportTaskRepository.class);
        probe = context.getBean(RollbackProbe.class);
        realFactWriter = context.getBean(JdbcPnlFactWriter.class);
        realTraceWriter = context.getBean(JdbcPnlFactSourceWriter.class);

        assertTrue(AopUtils.isAopProxy(service),
                "ImportTaskServiceImpl must be invoked through a Spring transaction proxy");
        assertTrue(AopUtils.isCglibProxy(service),
                "ImportTaskServiceImpl must use the class proxy required by this concrete service");
    }

    @AfterEach
    void cleanupRows() {
        if (jdbc == null || batchNo == null) return;
        jdbc.update("DELETE FROM raw.pnl_fact_trace WHERE source_task_no = ?", taskNo);
        jdbc.update("DELETE FROM raw.pnl_fact WHERE source_task_no = ?", taskNo);
        jdbc.update("DELETE FROM flow.import_issue WHERE task_id IN "
                + "(SELECT id FROM flow.import_task WHERE task_no = ?)", taskNo);
        jdbc.update("DELETE FROM flow.import_task WHERE task_no = ?", taskNo);
        jdbc.update("DELETE FROM meta.analysis_batch WHERE batch_no = ?", batchNo);
        taskNo = null;
        batchNo = null;
    }

    @AfterAll
    void closeSpringContextAndTemporaryFiles() throws Exception {
        if (context != null) context.close();
        if (storageRoot == null) return;
        try (java.util.stream.Stream<Path> paths = Files.walk(storageRoot)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (java.io.IOException ex) {
                    throw new IllegalStateException("事务回滚测试目录清理失败: " + path, ex);
                }
            });
        }
    }

    @Test
    void factAndTraceAreBothRolledBackWhenTraceWriteFailsAfterFactWrite() throws Exception {
        batchNo = "TX-ROLLBACK-DB-" + UUID.randomUUID().toString().replace("-", "");
        AnalysisBatch batch = batches.save(new AnalysisBatch(
                null, batchNo, "2026-10", AnalysisPerspective.BASE,
                BatchStatus.DRAFT, "TEST-DATA-002-B-TX-002", LocalDateTime.now()));
        ImportTask task = service.create(batch.getBatchNo(), "BASE_ACT_INC_COST",
                "rollback.xlsx", "rollback-checksum", "TEST-DATA-002-B-TX-002");
        taskNo = task.getTaskNo();

        realFactWriter.replace(batch, task, Arrays.asList(
                fact("旧事实-甲", 111, 222),
                fact("旧事实-乙", 333, 444)));
        realTraceWriter.replace(batch, task, Arrays.asList(
                source(taskNo, 2, "旧事实-甲"),
                source(taskNo, 3, "旧事实-乙")));
        assertEquals(2, count("SELECT COUNT(*) FROM raw.pnl_fact WHERE source_task_no = ?"),
                "old fact set must be committed before validate starts");
        assertEquals(2, count("SELECT COUNT(*) FROM raw.pnl_fact_trace WHERE source_task_no = ?"),
                "old trace set must be committed before validate starts");

        probe.factWriteCompleted.set(false);
        probe.traceWriteCompleted.set(false);
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> service.validate(taskNo, new ByteArrayInputStream(workbookBytes())));

        assertTrue(failure.getMessage().contains("TEST-DATA-002-B-TX-002"));
        assertTrue(probe.factWriteCompleted.get(),
                "real fact writer must complete before the injected trace failure");
        assertTrue(probe.traceWriteCompleted.get(),
                "real trace writer must complete before the injected failure");
        assertEquals(2, count("SELECT COUNT(*) FROM raw.pnl_fact WHERE source_task_no = ?"),
                "old fact rows must remain after failed replacement");
        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM raw.pnl_fact WHERE source_task_no = ? "
                        + "AND scope_code IN (?, ?)", Integer.class,
                taskNo, "旧事实-甲", "旧事实-乙").intValue(),
                "the complete old fact set must remain");
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM raw.pnl_fact WHERE source_task_no = ? "
                        + "AND scope_code = ? AND volume = ? AND revenue = ?", Integer.class,
                taskNo, "旧事实-甲", new BigDecimal("111"), new BigDecimal("222")).intValue(),
                "old fact values must remain unchanged");
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM raw.pnl_fact WHERE source_task_no = ? AND scope_code = ?",
                Integer.class, taskNo, "南昌").intValue(),
                "new fact set must not remain");
        assertEquals(2, count("SELECT COUNT(*) FROM raw.pnl_fact_trace WHERE source_task_no = ?"),
                "old trace rows must remain after failed replacement");
        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM raw.pnl_fact_trace WHERE source_task_no = ? "
                        + "AND source_file_name = ? AND source_checksum = ? "
                        + "AND source_sheet_name = ?", Integer.class,
                taskNo, "old-rollback.xlsx", "old-rollback-checksum", "旧明细").intValue(),
                "the complete old trace set must remain");
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM raw.pnl_fact_trace WHERE source_task_no = ? "
                        + "AND source_file_name = ?", Integer.class,
                taskNo, "rollback.xlsx").intValue(),
                "new trace set must not remain");
        assertEquals("CREATED", jdbc.queryForObject(
                "SELECT status FROM flow.import_task WHERE task_no = ?", String.class, taskNo),
                "task lifecycle update must be rolled back");
        assertEquals(Integer.valueOf(0), jdbc.queryForObject(
                "SELECT issue_count FROM flow.import_task WHERE task_no = ?", Integer.class, taskNo),
                "task issue count must remain at its pre-validation value");
        assertEquals(0, count("SELECT COUNT(*) FROM flow.import_issue i "
                + "JOIN flow.import_task t ON t.id = i.task_id WHERE t.task_no = ?"),
                "issue replacement must be rolled back");
    }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TestConfig {
        @Bean
        DataSource dataSource() {
            String url = System.getenv("AMOEBA_DB_URL");
            return new DriverManagerDataSource(
                    url, environment("AMOEBA_DB_USERNAME"), environment("AMOEBA_DB_PASSWORD"));
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
        JdbcPnlFactWriter realFactWriter(DataSource dataSource) {
            return new JdbcPnlFactWriter(dataSource);
        }

        @Bean
        @Primary
        PnlFactWriter factWriter(JdbcPnlFactWriter delegate, RollbackProbe probe) {
            return new RecordingFactWriter(delegate, probe);
        }

        @Bean
        JdbcPnlFactSourceWriter realTraceWriter(DataSource dataSource) {
            return new JdbcPnlFactSourceWriter(dataSource);
        }

        @Bean
        @Primary
        PnlFactSourceWriter sourceWriter(JdbcPnlFactSourceWriter delegate, RollbackProbe probe) {
            return new FailingSourceWriter(delegate, probe);
        }

        @Bean
        RollbackProbe rollbackProbe() {
            return new RollbackProbe();
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

    private static final class RollbackProbe {
        private final AtomicBoolean factWriteCompleted = new AtomicBoolean();
        private final AtomicBoolean traceWriteCompleted = new AtomicBoolean();
    }

    private static final class RecordingFactWriter implements PnlFactWriter {
        private final PnlFactWriter delegate;
        private final RollbackProbe probe;

        private RecordingFactWriter(PnlFactWriter delegate, RollbackProbe probe) {
            this.delegate = delegate;
            this.probe = probe;
        }

        @Override
        public void replace(AnalysisBatch batch, ImportTask task,
                            List<com.sunwoda.evb.finance.analysis.domain.model.PnlFact> facts) {
            delegate.replace(batch, task, facts);
            probe.factWriteCompleted.set(true);
        }
    }

    private static final class FailingSourceWriter implements PnlFactSourceWriter {
        private final PnlFactSourceWriter delegate;
        private final RollbackProbe probe;

        private FailingSourceWriter(PnlFactSourceWriter delegate, RollbackProbe probe) {
            this.delegate = delegate;
            this.probe = probe;
        }

        @Override
        public void replace(AnalysisBatch batch, ImportTask task,
                            List<com.sunwoda.evb.finance.analysis.domain.model.PnlFactSource> sources) {
            delegate.replace(batch, task, sources);
            probe.traceWriteCompleted.set(true);
            throw new IllegalStateException("TEST-DATA-002-B-TX-002: injected trace write failure");
        }

        @Override
        public List<com.sunwoda.evb.finance.analysis.domain.model.PnlFactSource>
        findByBatchNoAndPerspectiveAndTaskNo(String batchNo, AnalysisPerspective perspective,
                                              String taskNo) {
            return delegate.findByBatchNoAndPerspectiveAndTaskNo(batchNo, perspective, taskNo);
        }
    }

    private int count(String sql) {
        Integer count = jdbc.queryForObject(sql, Integer.class, taskNo);
        return count == null ? 0 : count;
    }

    private PnlFact fact(String scope, int volume, int revenue) {
        return new PnlFact(scope, decimal(volume), decimal(revenue), decimal(revenue - 10),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private PnlFactSource source(String taskNo, int rowNo, String scope) {
        return new PnlFactSource(taskNo, "old-rollback.xlsx", "old-rollback-checksum",
                "旧明细", rowNo, scope, "旧基地", "旧产品", "旧拉线");
    }

    private static BigDecimal decimal(int value) {
        return BigDecimal.valueOf(value);
    }

    private void verifySchema() {
        try {
            jdbc.execute("SELECT 1 FROM meta.analysis_batch LIMIT 0");
            jdbc.execute("SELECT 1 FROM flow.import_task LIMIT 0");
            jdbc.execute("SELECT 1 FROM flow.import_issue LIMIT 0");
            jdbc.execute("SELECT 1 FROM raw.pnl_fact LIMIT 0");
            jdbc.execute("SELECT 1 FROM raw.pnl_fact_trace LIMIT 0");
        } catch (RuntimeException failure) {
            assumeTrue(false, "BLOCKED: rollback PostgreSQL schema is not available: "
                    + failure.getMessage());
        }
    }

    private byte[] workbookBytes() throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        try {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("实际明细");
            String[] headers = {"期间", "收入计算基地", "产品线", "电芯型号", "财经修正项目",
                    "客户简称", "拉线", "销量", "营业收入", "销售成本", "材料成本", "Wh", "材料损耗"};
            String[] rowValues = {"2026-10", "南昌", "产品A", "106Ah", "项目A", "客户A",
                    "一线", "10", "100", "60", "40", "1000", "2"};
            org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
            org.apache.poi.ss.usermodel.Row row = sheet.createRow(1);
            for (int i = 0; i < rowValues.length; i++) row.createCell(i).setCellValue(rowValues[i]);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            workbook.write(output);
            return output.toByteArray();
        } finally {
            workbook.close();
        }
    }

    private static String environment(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value;
    }
}
