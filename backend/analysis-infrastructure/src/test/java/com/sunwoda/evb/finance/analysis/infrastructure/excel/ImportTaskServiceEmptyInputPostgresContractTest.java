package com.sunwoda.evb.finance.analysis.infrastructure.excel;

import com.sunwoda.evb.finance.analysis.application.port.DatasetCatalog;
import com.sunwoda.evb.finance.analysis.application.port.TemplateValidator;
import com.sunwoda.evb.finance.analysis.application.service.ImportTaskServiceImpl;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.BatchStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ImportTask;
import com.sunwoda.evb.finance.analysis.domain.repository.AnalysisBatchRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.ImportFileRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.ImportIssueRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.ImportTaskRepository;
import com.sunwoda.evb.finance.analysis.infrastructure.filesystem.FileSystemImportFileRepository;
import com.sunwoda.evb.finance.analysis.infrastructure.jdbc.JdbcAnalysisBatchRepository;
import com.sunwoda.evb.finance.analysis.infrastructure.jdbc.JdbcImportIssueRepository;
import com.sunwoda.evb.finance.analysis.infrastructure.jdbc.JdbcImportTaskRepository;
import com.sunwoda.evb.finance.analysis.infrastructure.memory.InMemoryDatasetCatalog;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * TEST-DATA-002-B-EMPTY-001: empty import input rollback contract.
 *
 * <p>This is an opt-in contract against a real PostgreSQL database. The
 * service is obtained from a real Spring transaction context configured with
 * class-based (CGLIB) proxies. An empty stream must raise the existing
 * {@link IllegalArgumentException} and roll back the intermediate
 * VALIDATING state without creating any issue, fact, trace, or file rows.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ImportTaskServiceEmptyInputPostgresContractTest {
    private static Path storageRoot;
    private AnnotationConfigApplicationContext context;
    private DataSource dataSource;
    private JdbcTemplate jdbc;
    private ImportTaskServiceImpl service;
    private AnalysisBatchRepository batches;
    private ImportTaskRepository tasks;
    private String batchNo;
    private String taskNo;

    @BeforeAll
    void startRealSpringContext() throws Exception {
        String url = System.getenv("AMOEBA_DB_URL");
        String username = System.getenv("AMOEBA_DB_USERNAME");
        assumeTrue(url != null && !url.trim().isEmpty(),
                "BLOCKED: AMOEBA_DB_URL is not set; TEST-DATA-002-B-EMPTY-001 was not run");
        assumeTrue(username != null && !username.trim().isEmpty(),
                "BLOCKED: AMOEBA_DB_USERNAME is not set; TEST-DATA-002-B-EMPTY-001 was not run");

        dataSource = new DriverManagerDataSource(url, username, environment("AMOEBA_DB_PASSWORD"));
        jdbc = new JdbcTemplate(dataSource);
        verifySchema();

        storageRoot = Files.createTempDirectory("amoeba-empty-input-");
        context = new AnnotationConfigApplicationContext();
        context.register(TestConfig.class);
        context.refresh();

        service = context.getBean(ImportTaskServiceImpl.class);
        batches = context.getBean(AnalysisBatchRepository.class);
        tasks = context.getBean(ImportTaskRepository.class);

        assertTrue(AopUtils.isAopProxy(service),
                "ImportTaskServiceImpl must be invoked through a Spring transaction proxy");
        assertTrue(AopUtils.isCglibProxy(service),
                "ImportTaskServiceImpl must use the required class-based CGLIB proxy");
    }

    @AfterEach
    void cleanupRowsAndFiles() throws Exception {
        if (jdbc == null || batchNo == null) return;
        jdbc.update("DELETE FROM raw.pnl_fact_trace WHERE source_task_no = ?", taskNo);
        jdbc.update("DELETE FROM raw.pnl_fact WHERE source_task_no = ?", taskNo);
        jdbc.update("DELETE FROM flow.import_issue WHERE task_id IN "
                + "(SELECT id FROM flow.import_task WHERE task_no = ?)", taskNo);
        jdbc.update("DELETE FROM flow.import_task WHERE task_no = ?", taskNo);
        jdbc.update("DELETE FROM meta.analysis_batch WHERE batch_no = ?", batchNo);
        deleteDirectory(storageRoot);
        taskNo = null;
        batchNo = null;
    }

    @AfterAll
    void closeSpringContext() {
        if (context != null) context.close();
    }

    @Test
    void emptyInputRaisesExistingIllegalArgumentExceptionAndLeavesNoSideEffects() {
        batchNo = "EMPTY-INPUT-DB-" + UUID.randomUUID().toString().replace("-", "");
        AnalysisBatch batch = batches.save(new AnalysisBatch(
                null, batchNo, "2026-10", AnalysisPerspective.BASE,
                BatchStatus.DRAFT, "TEST-DATA-002-B-EMPTY-001", LocalDateTime.now()));
        ImportTask task = service.create(batchNo, "BASE_ACT_INC_COST", "empty.xlsx",
                "empty-input-checksum", "TEST-DATA-002-B-EMPTY-001");
        taskNo = task.getTaskNo();

        assertEquals("CREATED", jdbc.queryForObject(
                "SELECT status FROM flow.import_task WHERE task_no = ?", String.class, taskNo));
        int issueCountBefore = count("SELECT COUNT(*) FROM flow.import_issue i "
                + "JOIN flow.import_task t ON t.id = i.task_id WHERE t.task_no = ?");
        int factCountBefore = count("SELECT COUNT(*) FROM raw.pnl_fact WHERE source_task_no = ?");
        int traceCountBefore = count("SELECT COUNT(*) FROM raw.pnl_fact_trace WHERE source_task_no = ?");

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> service.validate(taskNo, new ByteArrayInputStream(new byte[0])));

        assertEquals("导入文件不能为空", failure.getMessage());
        assertEquals("CREATED", service.get(taskNo).getStatus().name());
        assertEquals("CREATED", jdbc.queryForObject(
                "SELECT status FROM flow.import_task WHERE task_no = ?", String.class, taskNo));
        assertEquals(issueCountBefore, count("SELECT COUNT(*) FROM flow.import_issue i "
                + "JOIN flow.import_task t ON t.id = i.task_id WHERE t.task_no = ?"));
        assertEquals(0, service.issues(taskNo).size());
        assertEquals(factCountBefore, count("SELECT COUNT(*) FROM raw.pnl_fact WHERE source_task_no = ?"));
        assertEquals(traceCountBefore,
                count("SELECT COUNT(*) FROM raw.pnl_fact_trace WHERE source_task_no = ?"));
        assertFalse(Files.exists(storageRoot.resolve(taskNo + ".bin")),
                "empty validation must not create the task's stored content file");
        assertFalse(Files.exists(storageRoot.resolve(taskNo + ".name")),
                "empty validation must not create the task's stored filename marker");
    }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TestConfig {
        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource(
                    System.getenv("AMOEBA_DB_URL"),
                    environment("AMOEBA_DB_USERNAME"),
                    environment("AMOEBA_DB_PASSWORD"));
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
        ImportTaskServiceImpl importTaskService(AnalysisBatchRepository batches,
                                                ImportTaskRepository tasks,
                                                DatasetCatalog catalog,
                                                TemplateValidator validator,
                                                ImportIssueRepository issues,
                                                ImportFileRepository files) {
            return new ImportTaskServiceImpl(batches, tasks, catalog, validator, issues, files);
        }
    }

    private int count(String sql) {
        Integer count = jdbc.queryForObject(sql, Integer.class, taskNo);
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
            assumeTrue(false, "BLOCKED: empty-input PostgreSQL schema is not available: "
                    + failure.getMessage());
        }
    }

    private static void deleteDirectory(Path root) throws Exception {
        if (root == null || !Files.exists(root)) return;
        try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (java.io.IOException ex) {
                    throw new IllegalStateException("空文件校验测试目录清理失败: " + path, ex);
                }
            });
        }
    }

    private static String environment(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value;
    }
}
