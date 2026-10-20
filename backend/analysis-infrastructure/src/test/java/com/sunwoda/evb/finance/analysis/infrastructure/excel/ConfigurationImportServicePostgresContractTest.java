package com.sunwoda.evb.finance.analysis.infrastructure.excel;

import com.sunwoda.evb.finance.analysis.application.service.ConfigurationImportServiceImpl;
import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportTask;
import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportType;
import com.sunwoda.evb.finance.analysis.domain.model.ImportStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ImportValidationResult;
import com.sunwoda.evb.finance.analysis.domain.model.MappingType;
import com.sunwoda.evb.finance.analysis.infrastructure.filesystem.FileSystemImportFileRepository;
import com.sunwoda.evb.finance.analysis.infrastructure.jdbc.JdbcConfigurationImportIssueRepository;
import com.sunwoda.evb.finance.analysis.infrastructure.jdbc.JdbcConfigurationImportTaskRepository;
import com.sunwoda.evb.finance.analysis.infrastructure.jdbc.JdbcMappingEntryRepository;
import com.sunwoda.evb.finance.analysis.infrastructure.jdbc.JdbcMetricDefinitionRepository;
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
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Real PostgreSQL contract for the complete POI configuration import chain.
 *
 * <p>The contract is opt-in through {@code AMOEBA_DB_URL}. Without that
 * variable the tests are skipped, never reported as a successful database
 * verification. It deliberately uses the V001/V003/V005/V006 schema objects
 * already provisioned by the project's migration set.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConfigurationImportServicePostgresContractTest {
    private static final String[] MIGRATION_TABLES = {
            "cfg.metric_definition",
            "flow.configuration_import_task",
            "flow.configuration_import_issue",
            "cfg.mapping_entry"
    };

    private DataSource dataSource;
    private JdbcTemplate jdbc;
    private JdbcConfigurationImportTaskRepository tasks;
    private JdbcMetricDefinitionRepository metrics;
    private JdbcMappingEntryRepository mappings;
    private FileSystemImportFileRepository files;
    private JdbcConfigurationImportIssueRepository issues;
    private Path storageRoot;
    private String runPrefix;
    private final List<String> taskNos = new ArrayList<String>();

    @BeforeAll
    void connectToConfiguredPostgresAndVerifyMigrations() throws Exception {
        String url = System.getenv("AMOEBA_DB_URL");
        assumeTrue(url != null && !url.trim().isEmpty(),
                "BLOCKED: AMOEBA_DB_URL is not set; configuration import PostgreSQL contract was skipped");

        dataSource = new DriverManagerDataSource(
                url, environment("AMOEBA_DB_USERNAME"), environment("AMOEBA_DB_PASSWORD"));
        jdbc = new JdbcTemplate(dataSource);
        try {
            Long schemaCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.schemata "
                            + "WHERE schema_name IN ('iam', 'meta', 'raw', 'cfg', 'calc', 'mart', 'flow', 'audit')",
                    Long.class);
            assumeTrue(schemaCount != null && schemaCount == 8L,
                    "BLOCKED: V001 schema migration is not fully available");
            for (String table : MIGRATION_TABLES) {
                jdbc.execute("SELECT 1 FROM " + table + " LIMIT 0");
            }
        } catch (RuntimeException failure) {
            assumeTrue(false, "BLOCKED: V003/V005/V006 PostgreSQL schema is not available: "
                    + failure.getMessage());
        }

        tasks = new JdbcConfigurationImportTaskRepository(dataSource);
        metrics = new JdbcMetricDefinitionRepository(dataSource);
        mappings = new JdbcMappingEntryRepository(dataSource);
        storageRoot = Files.createTempDirectory("amoeba-config-import-");
        files = new FileSystemImportFileRepository(storageRoot.toString());
        issues = new JdbcConfigurationImportIssueRepository(dataSource);
        runPrefix = "IMPORT-DB-" + UUID.randomUUID().toString().replace("-", "");
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
                    throw new IllegalStateException("配置导入测试目录清理失败: " + path, ex);
                }
            });
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("配置导入测试目录读取失败", ex);
        }
    }

    @Test
    void realXlsxMetricImportFlowsThroughServicePoiAndJdbcToPostgres() throws Exception {
        String metricCode = runPrefix + "-METRIC";
        ConfigurationImportServiceImpl service = service(
                new PoiConfigurationImportApplier(metrics, mappings));
        ConfigurationImportTask task = service.create(ConfigurationImportType.METRIC_DEFINITION,
                runPrefix + "-metric.xlsx", runPrefix + "-checksum", runPrefix + "-user");
        taskNos.add(task.getTaskNo());
        byte[] workbook = workbookBytes("指标",
                new String[]{"指标编码", "指标名称", "计算公式", "适用视角", "单位", "展示顺序",
                        "是否有效", "AI简析模板"},
                new String[]{metricCode, "真实PostgreSQL导入指标", "SUM(REVENUE)", "BASE", "CNY",
                        "17", "是", "真实导入"});

        ImportValidationResult result = service.validate(task.getTaskNo(),
                new ByteArrayInputStream(workbook));

        assertEquals(ImportStatus.VALID, result.getStatus());
        assertEquals(1, result.getRowCount());
        assertTrue(result.getIssues().isEmpty());
        assertEquals(ImportStatus.VALID, tasks.findByTaskNo(task.getTaskNo())
                .orElseThrow(() -> new AssertionError("configuration task was not persisted"))
                .getStatus());
        assertTrue(issues.findByTaskNo(task.getTaskNo()).isEmpty());
        assertArrayEquals(workbook, read(files.open(task.getTaskNo())));

        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM cfg.metric_definition WHERE metric_code = ?",
                Integer.class, metricCode).intValue());
        assertEquals("真实PostgreSQL导入指标", jdbc.queryForObject(
                "SELECT metric_name FROM cfg.metric_definition WHERE metric_code = ?",
                String.class, metricCode));
        assertEquals("SUM(REVENUE)", metrics.findByCode(metricCode)
                .orElseThrow(() -> new AssertionError("metric was not applied"))
                .getFormulaExpression());
    }

    @Test
    void realXlsxSalesMappingImportFlowsThroughServicePoiAndJdbcToPostgres() throws Exception {
        String matchKey = runPrefix + "-CUSTOMER|项目A|106Ah";
        ConfigurationImportServiceImpl service = service(
                new PoiConfigurationImportApplier(metrics, mappings));
        ConfigurationImportTask task = service.create(ConfigurationImportType.SALES_PROJECT_MAPPING,
                runPrefix + "-mapping.xlsx", runPrefix + "-checksum", runPrefix + "-user");
        taskNos.add(task.getTaskNo());
        byte[] workbook = workbookBytes("21_销售项目映射",
                new String[]{"客户简称", "客户项目", "电芯型号", "产品线", "事业部", "B品标识",
                        "备注", "是否有效"},
                new String[]{runPrefix + "-CUSTOMER", "项目A", "106Ah", "产品线A", "事业部A",
                        "否", "真实导入", "是"});

        ImportValidationResult result = service.validate(task.getTaskNo(),
                new ByteArrayInputStream(workbook));

        assertEquals(ImportStatus.VALID, result.getStatus());
        assertEquals(1, result.getRowCount());
        assertTrue(result.getIssues().isEmpty());
        assertEquals(ImportStatus.VALID, tasks.findByTaskNo(task.getTaskNo())
                .orElseThrow(() -> new AssertionError("configuration task was not persisted"))
                .getStatus());

        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM cfg.mapping_entry WHERE mapping_type = ? AND match_key = ?",
                Integer.class, MappingType.SALES_PROJECT.name(), matchKey).intValue());
        assertEquals("产品线A|事业部A|否|真实导入||", mappings.find(MappingType.SALES_PROJECT, matchKey)
                .orElseThrow(() -> new AssertionError("mapping was not applied"))
                .getMappedValue());
    }

    @Test
    void invalidRealXlsxPersistsIssuesAgainstConfigurationTask() throws Exception {
        ConfigurationImportServiceImpl service = service(
                new PoiConfigurationImportApplier(metrics, mappings));
        ConfigurationImportTask task = service.create(ConfigurationImportType.METRIC_DEFINITION,
                runPrefix + "-invalid.xlsx", runPrefix + "-checksum-invalid", runPrefix + "-user");
        taskNos.add(task.getTaskNo());

        ImportValidationResult result = service.validate(task.getTaskNo(),
                new ByteArrayInputStream(workbookBytes("指标",
                        new String[]{"指标编码"}, new String[]{runPrefix + "-INVALID"})));

        assertEquals(ImportStatus.INVALID, result.getStatus());
        assertTrue(result.getIssues().stream()
                .anyMatch(issue -> "FIELD_MISSING".equals(issue.getIssueCode())));
        assertEquals(ImportStatus.INVALID, tasks.findByTaskNo(task.getTaskNo())
                .orElseThrow(() -> new AssertionError("configuration task was not persisted"))
                .getStatus());
        assertEquals(result.getIssues().size(), jdbc.queryForObject(
                "SELECT COUNT(*) FROM flow.configuration_import_issue i "
                        + "JOIN flow.configuration_import_task t ON t.id = i.configuration_task_id "
                        + "WHERE t.task_no = ?", Integer.class, task.getTaskNo()).intValue());
        assertEquals("FIELD_MISSING", issues.findByTaskNo(task.getTaskNo()).get(0).getIssueCode());
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM cfg.metric_definition WHERE metric_code = ?",
                Integer.class, runPrefix + "-INVALID").intValue());
    }

    private ConfigurationImportServiceImpl service(PoiConfigurationImportApplier applier)
            throws Exception {
        ConfigurationImportServiceImpl service = new ConfigurationImportServiceImpl(tasks, files);
        set(service, "validator", new PoiConfigurationTemplateValidator());
        set(service, "issueRepository", issues);
        set(service, "applier", applier);
        return service;
    }

    private void cleanupRows() {
        if (jdbc == null || runPrefix == null) return;
        jdbc.update("DELETE FROM cfg.metric_definition WHERE metric_code LIKE ?", runPrefix + "%");
        jdbc.update("DELETE FROM cfg.mapping_entry WHERE match_key LIKE ?", runPrefix + "%");
        for (String taskNo : new ArrayList<String>(taskNos)) {
            jdbc.update("DELETE FROM flow.configuration_import_issue WHERE configuration_task_id IN "
                    + "(SELECT id FROM flow.configuration_import_task WHERE task_no = ?)", taskNo);
            jdbc.update("DELETE FROM flow.configuration_import_task WHERE task_no = ?", taskNo);
        }
        taskNos.clear();
    }

    private static byte[] workbookBytes(String sheetName, String[] headers, String[] values)
            throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        try {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet(sheetName);
            org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
            org.apache.poi.ss.usermodel.Row row = sheet.createRow(1);
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
                row.createCell(i).setCellValue(values[i]);
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

    private static void set(ConfigurationImportServiceImpl service, String fieldName,
                            Object value) throws Exception {
        Field field = ConfigurationImportServiceImpl.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(service, value);
    }

    private static String environment(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value;
    }
}
