package com.sunwoda.evb.finance.analysis.infrastructure.excel;

import com.sunwoda.evb.finance.analysis.application.service.ImportTaskServiceImpl;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.BatchStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ImportStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ImportTask;
import com.sunwoda.evb.finance.analysis.domain.model.PnlFact;
import com.sunwoda.evb.finance.analysis.domain.model.PnlFactSource;
import com.sunwoda.evb.finance.analysis.infrastructure.filesystem.FileSystemImportFileRepository;
import com.sunwoda.evb.finance.analysis.infrastructure.jdbc.JdbcAnalysisBatchRepository;
import com.sunwoda.evb.finance.analysis.infrastructure.jdbc.JdbcImportIssueRepository;
import com.sunwoda.evb.finance.analysis.infrastructure.jdbc.JdbcImportTaskRepository;
import com.sunwoda.evb.finance.analysis.infrastructure.jdbc.JdbcPnlFactSourceWriter;
import com.sunwoda.evb.finance.analysis.infrastructure.jdbc.JdbcPnlFactWriter;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Real PostgreSQL contract for the monthly fact import and provenance chain.
 *
 * <p>The test intentionally exercises the application orchestration rather
 * than calling either JDBC writer in isolation:</p>
 *
 * <pre>
 * XLSX -&gt; ImportTaskServiceImpl -&gt; POI readers
 *      -&gt; JdbcPnlFactWriter / JdbcPnlFactSourceWriter
 *      -&gt; raw.pnl_fact / raw.pnl_fact_trace
 * </pre>
 *
 * <p>It is opt-in through {@code AMOEBA_DB_URL}; an unavailable database is
 * reported as a skipped contract, never as a false passing verification.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PnlFactImportPostgresTraceContractTest {
    private DataSource dataSource;
    private JdbcTemplate jdbc;
    private JdbcAnalysisBatchRepository batches;
    private JdbcImportTaskRepository tasks;
    private FileSystemImportFileRepository files;
    private JdbcImportIssueRepository issues;
    private JdbcPnlFactWriter factWriter;
    private JdbcPnlFactSourceWriter sourceWriter;
    private Path storageRoot;
    private String runPrefix;
    private final List<String> batchNos = new ArrayList<String>();
    private final List<String> taskNos = new ArrayList<String>();

    @BeforeAll
    void connectToConfiguredPostgresAndVerifyMigrations() throws Exception {
        String url = System.getenv("AMOEBA_DB_URL");
        assumeTrue(url != null && !url.trim().isEmpty(),
                "BLOCKED: AMOEBA_DB_URL is not set; fact PostgreSQL trace contract was skipped");

        dataSource = new DriverManagerDataSource(
                url, environment("AMOEBA_DB_USERNAME"), environment("AMOEBA_DB_PASSWORD"));
        jdbc = new JdbcTemplate(dataSource);
        try {
            Long schemaCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.schemata "
                            + "WHERE schema_name IN ('meta', 'raw', 'flow')", Long.class);
            assumeTrue(schemaCount != null && schemaCount == 3L,
                    "BLOCKED: required meta/raw/flow schemas are not available");
            jdbc.execute("SELECT 1 FROM meta.analysis_batch LIMIT 0");
            jdbc.execute("SELECT 1 FROM flow.import_task LIMIT 0");
            jdbc.execute("SELECT 1 FROM raw.pnl_fact LIMIT 0");
            jdbc.execute("SELECT 1 FROM raw.pnl_fact_trace LIMIT 0");
        } catch (RuntimeException failure) {
            assumeTrue(false, "BLOCKED: V002/V004/V008/V017 PostgreSQL schema is not available: "
                    + failure.getMessage());
        }

        batches = new JdbcAnalysisBatchRepository(dataSource);
        tasks = new JdbcImportTaskRepository(dataSource);
        storageRoot = Files.createTempDirectory("amoeba-fact-import-");
        files = new FileSystemImportFileRepository(storageRoot.toString());
        issues = new JdbcImportIssueRepository(dataSource);
        factWriter = new JdbcPnlFactWriter(dataSource);
        sourceWriter = new JdbcPnlFactSourceWriter(dataSource);
        runPrefix = "FACT-DB-" + UUID.randomUUID().toString().replace("-", "");
    }

    @AfterEach
    void cleanupAfterTest() {
        cleanupRows();
    }

    @AfterAll
    void cleanupAfterAll() {
        cleanupRows();
        if (storageRoot == null) return;
        try (java.util.stream.Stream<Path> paths = Files.walk(storageRoot)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (java.io.IOException ex) {
                    throw new IllegalStateException("事实导入测试目录清理失败: " + path, ex);
                }
            });
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("事实导入测试目录读取失败", ex);
        }
    }

    @Test
    void realXlsxImportWritesFactAndTraceRowsToPostgres() throws Exception {
        String batchNo = runPrefix + "-BATCH";
        String taskNo;
        AnalysisBatch batch = batches.save(new AnalysisBatch(
                null, batchNo, "2026-10", AnalysisPerspective.BASE,
                BatchStatus.DRAFT, "trace-test", java.time.LocalDateTime.now()));
        batchNos.add(batchNo);

        ImportTaskServiceImpl service = service();
        ImportTask task = service.create(batchNo, "BASE_ACT_INC_COST",
                runPrefix + ".xlsx", runPrefix + "-checksum", "trace-test");
        taskNo = task.getTaskNo();
        taskNos.add(taskNo);
        byte[] workbook = workbookBytes();

        com.sunwoda.evb.finance.analysis.domain.model.ImportValidationResult result =
                service.validate(taskNo, new ByteArrayInputStream(workbook));

        assertEquals(ImportStatus.VALID, result.getStatus());
        assertEquals(2, result.getRowCount());
        assertTrue(result.getIssues().isEmpty());
        ImportTask persistedTask = tasks.findByTaskNo(taskNo)
                .orElseThrow(() -> new AssertionError("import task was not persisted"));
        assertEquals(ImportStatus.VALID, persistedTask.getStatus());
        assertEquals(0, persistedTask.getIssueCount());
        assertTrue(issues.findByTaskNo(taskNo).isEmpty());
        assertArrayEquals(workbook, read(files.open(taskNo)));

        Integer factCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM raw.pnl_fact f "
                        + "JOIN meta.analysis_batch b ON b.id = f.batch_id "
                        + "WHERE b.batch_no = ? AND f.source_task_no = ?",
                Integer.class, batchNo, taskNo);
        assertEquals(1, factCount.intValue(), "two source rows should aggregate to one fact scope");
        assertDecimal("30", "volume", taskNo);
        assertDecimal("300", "revenue", taskNo);
        assertDecimal("180", "sales_cost", taskNo);
        assertDecimal("3000", "output_wh", taskNo);
        assertDecimal("5", "material_loss", taskNo);
        assertEquals("南昌", jdbc.queryForObject(
                "SELECT base_code FROM raw.pnl_fact WHERE source_task_no = ?", String.class, taskNo));
        assertEquals("产品A", jdbc.queryForObject(
                "SELECT product_code FROM raw.pnl_fact WHERE source_task_no = ?", String.class, taskNo));
        assertEquals("一线", jdbc.queryForObject(
                "SELECT line_code FROM raw.pnl_fact WHERE source_task_no = ?", String.class, taskNo));

        Integer traceCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM raw.pnl_fact_trace WHERE source_task_no = ?",
                Integer.class, taskNo);
        assertEquals(2, traceCount.intValue());
        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM raw.pnl_fact_trace t "
                        + "JOIN flow.import_task i ON i.task_no = t.source_task_no "
                        + "JOIN meta.analysis_batch b ON b.id = t.batch_id "
                        + "WHERE i.task_no = ? AND b.batch_no = ?",
                Integer.class, taskNo, batchNo).intValue());
        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM raw.pnl_fact_trace "
                        + "WHERE source_task_no = ? AND source_file_name = ? "
                        + "AND source_sheet_name = ? AND scope_code = ? "
                        + "AND base_code = ? AND product_code = ? AND line_code = ?",
                Integer.class, taskNo, runPrefix + ".xlsx", "实际明细", "南昌",
                "南昌", "产品A", "一线").intValue());

        List<PnlFactSource> persistedSources = sourceWriter
                .findByBatchNoAndPerspectiveAndTaskNo(batchNo, AnalysisPerspective.BASE, taskNo);
        assertEquals(2, persistedSources.size());
        assertEquals(2, persistedSources.get(0).getRowNo());
        assertEquals(3, persistedSources.get(1).getRowNo());
        assertEquals("产品A", persistedSources.get(0).getProductCode());
        assertEquals("一线", persistedSources.get(0).getLineCode());
        assertFalse(persistedSources.get(0).getTaskNo().isEmpty());
    }

    @Test
    void sourceWriterReplacesTraceRowsForTheSameTask() throws Exception {
        String batchNo = runPrefix + "-REPLACE-BATCH";
        AnalysisBatch batch = batches.save(new AnalysisBatch(
                null, batchNo, "2026-10", AnalysisPerspective.BASE,
                BatchStatus.DRAFT, "trace-test", java.time.LocalDateTime.now()));
        batchNos.add(batchNo);
        ImportTask task = tasks.save(new ImportTask(null, runPrefix + "-REPLACE-TASK", batchNo,
                "BASE_ACT_INC_COST", runPrefix + "-replace.xlsx", runPrefix + "-replace-checksum",
                "trace-test", java.time.LocalDateTime.now()));
        taskNos.add(task.getTaskNo());

        List<PnlFactSource> firstSources = java.util.Collections.singletonList(
                source(task.getTaskNo(), 2, "南昌", "产品A", "一线"));
        sourceWriter.replace(batch, task, firstSources);

        List<PnlFactSource> replacementSources = java.util.Arrays.asList(
                source(task.getTaskNo(), 2, "上海", "产品B", "二线"),
                source(task.getTaskNo(), 3, "上海", "产品B", "二线"));
        sourceWriter.replace(batch, task, replacementSources);

        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM raw.pnl_fact_trace WHERE source_task_no = ?",
                Integer.class, task.getTaskNo()).intValue());
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM raw.pnl_fact_trace "
                        + "WHERE source_task_no = ? AND scope_code = '南昌'",
                Integer.class, task.getTaskNo()).intValue());
        assertEquals(2, sourceWriter.findByBatchNoAndPerspectiveAndTaskNo(
                batchNo, AnalysisPerspective.BASE, task.getTaskNo()).size());
    }

    private ImportTaskServiceImpl service() throws Exception {
        ImportTaskServiceImpl service = new ImportTaskServiceImpl(
                new JdbcAnalysisBatchRepository(dataSource), tasks,
                new com.sunwoda.evb.finance.analysis.infrastructure.memory.InMemoryDatasetCatalog(),
                new PoiTemplateValidator(), issues, files);
        set(service, "factReader", new PoiPnlFactProvider(tasks, files));
        set(service, "sourceReader", new PoiPnlFactSourceReader(files));
        set(service, "factWriter", factWriter);
        set(service, "sourceWriter", sourceWriter);
        return service;
    }

    private PnlFact fact(String scope, String product, String line,
                         int volume, int revenue) {
        return new PnlFact(scope, decimal(volume), decimal(revenue), decimal(revenue - 40),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private PnlFactSource source(String taskNo, int rowNo, String scope,
                                 String product, String line) {
        return new PnlFactSource(taskNo, runPrefix + ".xlsx", runPrefix + "-checksum",
                "实际明细", rowNo, scope, scope, product, line);
    }

    private byte[] workbookBytes() throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        try {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("实际明细");
            String[] headers = {"期间", "收入计算基地", "产品线", "电芯型号", "财经修正项目",
                    "客户简称", "拉线", "销量", "营业收入", "销售成本", "材料成本", "Wh", "材料损耗"};
            String[][] rows = {
                    {"2026-10", "南昌", "产品A", "106Ah", "项目A", "客户A", "一线", "10", "100", "60", "40", "1000", "2"},
                    {"2026-10", "南昌", "产品A", "106Ah", "项目A", "客户A", "一线", "20", "200", "120", "80", "2000", "3"}
            };
            org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
            for (int rowIndex = 0; rowIndex < rows.length; rowIndex++) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIndex + 1);
                for (int column = 0; column < rows[rowIndex].length; column++) {
                    row.createCell(column).setCellValue(rows[rowIndex][column]);
                }
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            workbook.write(output);
            return output.toByteArray();
        } finally {
            workbook.close();
        }
    }

    private static byte[] read(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int count;
        while ((count = input.read(buffer)) >= 0) {
            if (count > 0) output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private void cleanupRows() {
        if (jdbc == null) return;
        for (String taskNo : new ArrayList<String>(taskNos)) {
            jdbc.update("DELETE FROM raw.pnl_fact_trace WHERE source_task_no = ?", taskNo);
            jdbc.update("DELETE FROM raw.pnl_fact WHERE source_task_no = ?", taskNo);
            jdbc.update("DELETE FROM flow.import_issue WHERE task_id IN "
                    + "(SELECT id FROM flow.import_task WHERE task_no = ?)", taskNo);
            jdbc.update("DELETE FROM flow.import_task WHERE task_no = ?", taskNo);
        }
        for (String batchNo : new ArrayList<String>(batchNos)) {
            jdbc.update("DELETE FROM meta.analysis_batch WHERE batch_no = ?", batchNo);
        }
        taskNos.clear();
        batchNos.clear();
    }

    private static BigDecimal decimal(int value) {
        return BigDecimal.valueOf(value);
    }

    private void assertDecimal(String expected, String column, String taskNo) {
        BigDecimal actual = jdbc.queryForObject(
                "SELECT " + column + " FROM raw.pnl_fact WHERE source_task_no = ?",
                BigDecimal.class, taskNo);
        assertEquals(0, actual.compareTo(new BigDecimal(expected)),
                column + " should match by numeric value");
    }

    private static void set(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = ImportTaskServiceImpl.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static String environment(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value;
    }
}
