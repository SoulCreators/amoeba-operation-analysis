package com.sunwoda.evb.finance.analysis.infrastructure.excel;

import com.sunwoda.evb.finance.analysis.application.service.ConfigurationImportServiceImpl;
import com.sunwoda.evb.finance.analysis.application.port.ConfigurationImportApplier;
import com.sunwoda.evb.finance.analysis.application.port.ConfigurationTemplateValidator;
import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportTask;
import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportType;
import com.sunwoda.evb.finance.analysis.domain.model.ImportIssue;
import com.sunwoda.evb.finance.analysis.domain.model.ImportStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ImportValidationResult;
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
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.dao.DataAccessException;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IMPORT-TEMPLATE-001-DB-TX-001：配置导入真实 PostgreSQL 事务回滚契约。
 *
 * <p>本次新增只验证既有 {@link ConfigurationImportServiceImpl#validate(String,
 * java.io.InputStream)} 的真实 Spring 事务语义，不修改生产代码、迁移、其他
 * 测试或业务规则。测试必须通过真实 PostgreSQL、真实 JDBC 仓储和 Spring
 * CGLIB 事务代理运行；数据库缺失或结构不完整时直接失败，禁止静默跳过。</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConfigurationImportServicePostgresTransactionContractTest {
    private static Path storageRoot;
    private AnnotationConfigApplicationContext context;
    private DataSource dataSource;
    private JdbcTemplate jdbc;
    private FileSystemImportFileRepository files;
    private ConfigurationImportServiceImpl service;
    private String runPrefix;
    private final List<String> taskNos = new ArrayList<String>();

    @BeforeAll
    void startRealSpringContext() throws Exception {
        String url = requiredEnvironment("AMOEBA_DB_URL");
        dataSource = new DriverManagerDataSource(url,
                environment("AMOEBA_DB_USERNAME"), environment("AMOEBA_DB_PASSWORD"));
        jdbc = new JdbcTemplate(dataSource);
        verifySchema();

        storageRoot = Files.createTempDirectory("amoeba-config-tx-");
        context = new AnnotationConfigApplicationContext();
        // 本次修改显式启用 db/filesystem 组件组合，确保测试不是内存替身或手工 new 服务。
        context.getEnvironment().setActiveProfiles("db", "filesystem");
        context.register(TestConfig.class);
        context.refresh();

        service = context.getBean(ConfigurationImportServiceImpl.class);
        files = context.getBean(FileSystemImportFileRepository.class);
        runPrefix = "CFG-TX-" + UUID.randomUUID().toString().replace("-", "");
        assertTrue(AopUtils.isAopProxy(service),
                "配置导入服务必须通过 Spring 事务代理调用");
        assertTrue(AopUtils.isCglibProxy(service),
                "配置导入服务必须使用 CGLIB 类代理");
    }

    @AfterEach
    void cleanupRowsAndFiles() {
        if (jdbc == null || runPrefix == null) return;
        for (String taskNo : new ArrayList<String>(taskNos)) {
            if (files != null) files.delete(taskNo);
            jdbc.update("DELETE FROM flow.configuration_import_issue WHERE configuration_task_id IN "
                    + "(SELECT id FROM flow.configuration_import_task WHERE task_no = ?)", taskNo);
            jdbc.update("DELETE FROM flow.configuration_import_task WHERE task_no = ?", taskNo);
        }
        jdbc.update("DELETE FROM cfg.metric_definition WHERE metric_code LIKE ?", runPrefix + "%");
        taskNos.clear();
    }

    @AfterAll
    void closeContextAndStorage() throws Exception {
        if (context != null) context.close();
        if (storageRoot == null) return;
        try (java.util.stream.Stream<Path> paths = Files.walk(storageRoot)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (java.io.IOException ex) {
                    throw new IllegalStateException("配置导入事务测试目录清理失败: " + path, ex);
                }
            });
        }
    }

    @Test
    void multiRowMetricInsertRollsBackWhenSecondRowViolatesPostgresLengthConstraint()
            throws Exception {
        // 本次修改验证第一行真实写入后，第二行 VARCHAR(64) 约束失败会回滚整批新增。
        String firstCode = runPrefix + "-FIRST";
        String tooLongCode = runPrefix + "-" + repeat("X", 65);
        ConfigurationImportTask task = createTask("insert-rollback");

        DataAccessException failure = assertThrows(DataAccessException.class,
                () -> service.validate(task.getTaskNo(), new ByteArrayInputStream(workbookBytes(
                        new String[]{firstCode, tooLongCode},
                        new String[]{"首行指标", "超长编码指标"},
                        new String[]{"SUM(FIRST)", "SUM(SECOND)"}))));

        assertTrue(hasSqlState(failure, "22001"),
                "第二行必须由 PostgreSQL VARCHAR(64) 约束触发失败: " + failure);
        assertEquals(0, count("SELECT COUNT(*) FROM cfg.metric_definition WHERE metric_code LIKE ?",
                runPrefix + "%"), "第二行失败后不得保留任何新增指标");
        assertEquals(ImportStatus.CREATED, persistedTask(task.getTaskNo()).getStatus(),
                "数据库异常应回滚任务状态更新");
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM flow.configuration_import_issue i "
                + "JOIN flow.configuration_import_task t ON t.id = i.configuration_task_id "
                + "WHERE t.task_no = ?", Integer.class, task.getTaskNo()) == 0,
                "数据库异常应回滚问题记录替换");
        assertFalse(files.snapshot(task.getTaskNo()).isPresent(),
                "失败后的文件副作用应按既有补偿契约清理");
    }

    @Test
    void multiRowValidMetricImportCommitsAllRowsAndMarksTaskValid() throws Exception {
        // 本次修改补齐正向验收：真实 PostgreSQL 中两行有效配置必须全部提交。
        String firstCode = runPrefix + "-VALID-FIRST";
        String secondCode = runPrefix + "-VALID-SECOND";
        ConfigurationImportTask task = createTask("success");

        ImportValidationResult result = service.validate(task.getTaskNo(),
                new ByteArrayInputStream(workbookBytes(
                        new String[]{firstCode, secondCode},
                        new String[]{"正向验收指标一", "正向验收指标二"},
                        new String[]{"SUM(FIRST)", "SUM(SECOND)"})));

        assertEquals(ImportStatus.VALID, result.getStatus(),
                "两行有效配置的校验结果必须为 VALID");
        assertEquals(2, result.getRowCount(), "正向验收文件必须识别两条配置行");
        assertTrue(result.getIssues().isEmpty(), "有效配置不应产生导入问题");
        assertEquals(ImportStatus.VALID, persistedTask(task.getTaskNo()).getStatus(),
                "成功提交后任务状态必须持久化为 VALID");
        assertEquals(2, count("SELECT COUNT(*) FROM cfg.metric_definition WHERE metric_code LIKE ?",
                runPrefix + "%"), "两条有效配置必须全部落库");
        assertEquals("正向验收指标一", scalar("SELECT metric_name FROM cfg.metric_definition "
                + "WHERE metric_code = ?", firstCode));
        assertEquals("SUM(FIRST)", scalar("SELECT formula_expression FROM cfg.metric_definition "
                + "WHERE metric_code = ?", firstCode));
        assertEquals("正向验收指标二", scalar("SELECT metric_name FROM cfg.metric_definition "
                + "WHERE metric_code = ?", secondCode));
        assertEquals("SUM(SECOND)", scalar("SELECT formula_expression FROM cfg.metric_definition "
                + "WHERE metric_code = ?", secondCode));
    }

    @Test
    void existingMetricUpdateRollsBackWhenLaterRowViolatesPostgresConstraint() throws Exception {
        // 本次修改验证已有指标在同一批次先更新、后续行约束失败时仍恢复旧值。
        String existingCode = runPrefix + "-EXISTING";
        String tooLongCode = runPrefix + "-" + repeat("Y", 65);
        jdbc.update("INSERT INTO cfg.metric_definition(metric_code, metric_name, "
                        + "applicable_perspective, unit, formula_expression, display_order, enabled, "
                        + "ai_summary_template, created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                existingCode, "旧指标名称", "BASE", "CNY", "OLD_FORMULA", 19, true,
                "旧模板", runPrefix + "-creator", runPrefix + "-old-updater");
        ConfigurationImportTask task = createTask("update-rollback");

        DataAccessException failure = assertThrows(DataAccessException.class,
                () -> service.validate(task.getTaskNo(), new ByteArrayInputStream(workbookBytes(
                        new String[]{existingCode, tooLongCode},
                        new String[]{"新指标名称", "第二行超长指标"},
                        new String[]{"NEW_FORMULA", "SUM(SECOND)"}))));

        assertTrue(hasSqlState(failure, "22001"),
                "后续行必须由 PostgreSQL VARCHAR(64) 约束触发失败: " + failure);
        assertEquals(1, count("SELECT COUNT(*) FROM cfg.metric_definition WHERE metric_code = ?",
                existingCode));
        assertEquals("旧指标名称", scalar("SELECT metric_name FROM cfg.metric_definition "
                + "WHERE metric_code = ?", existingCode));
        assertEquals("OLD_FORMULA", scalar("SELECT formula_expression FROM cfg.metric_definition "
                + "WHERE metric_code = ?", existingCode));
        assertEquals("旧模板", scalar("SELECT ai_summary_template FROM cfg.metric_definition "
                + "WHERE metric_code = ?", existingCode));
        assertEquals(runPrefix + "-old-updater", scalar("SELECT updated_by FROM cfg.metric_definition "
                + "WHERE metric_code = ?", existingCode));
        assertEquals(19, jdbc.queryForObject("SELECT display_order FROM cfg.metric_definition "
                + "WHERE metric_code = ?", Integer.class, existingCode).intValue());
        assertEquals(ImportStatus.CREATED, persistedTask(task.getTaskNo()).getStatus(),
                "已有配置更新失败时任务状态也必须保持原状态");
    }

    @Test
    void structurallyInvalidImportStillPersistsInvalidStatusInsteadOfFailed() throws Exception {
        // 本次修改锁定 INVALID 的既有语义，明确不引入 FAILED 状态。
        ConfigurationImportTask task = createTask("invalid");
        ImportValidationResult result = service.validate(task.getTaskNo(),
                new ByteArrayInputStream(invalidWorkbookBytes()));

        assertEquals(ImportStatus.INVALID, result.getStatus());
        assertFalse(result.getIssues().isEmpty());
        assertEquals(ImportStatus.INVALID, persistedTask(task.getTaskNo()).getStatus());
        assertNotNull(result.getIssues().get(0).getIssueCode());
    }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TestConfig {
        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource(requiredEnvironment("AMOEBA_DB_URL"),
                    environment("AMOEBA_DB_USERNAME"), environment("AMOEBA_DB_PASSWORD"));
        }

        @Bean
        DataSourceTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        JdbcConfigurationImportTaskRepository configurationImportTaskRepository(DataSource dataSource) {
            return new JdbcConfigurationImportTaskRepository(dataSource);
        }

        @Bean
        JdbcConfigurationImportIssueRepository configurationImportIssueRepository(DataSource dataSource) {
            return new JdbcConfigurationImportIssueRepository(dataSource);
        }

        @Bean
        JdbcMetricDefinitionRepository metricDefinitionRepository(DataSource dataSource) {
            return new JdbcMetricDefinitionRepository(dataSource);
        }

        @Bean
        JdbcMappingEntryRepository mappingEntryRepository(DataSource dataSource) {
            return new JdbcMappingEntryRepository(dataSource);
        }

        @Bean
        FileSystemImportFileRepository importFileRepository() {
            return new FileSystemImportFileRepository(storageRoot.toString());
        }

        @Bean
        ConfigurationTemplateValidator configurationTemplateValidator() {
            return new PoiConfigurationTemplateValidator();
        }

        @Bean
        ConfigurationImportApplier configurationImportApplier(
                JdbcMetricDefinitionRepository metrics,
                JdbcMappingEntryRepository mappings) {
            return new PoiConfigurationImportApplier(metrics, mappings);
        }

        @Bean
        ConfigurationImportServiceImpl configurationImportService(
                JdbcConfigurationImportTaskRepository tasks,
                FileSystemImportFileRepository files) {
            return new ConfigurationImportServiceImpl(tasks, files);
        }
    }

    private ConfigurationImportTask createTask(String suffix) {
        ConfigurationImportTask task = service.create(ConfigurationImportType.METRIC_DEFINITION,
                runPrefix + "-" + suffix + ".xlsx", runPrefix + "-checksum-" + suffix,
                runPrefix + "-user");
        taskNos.add(task.getTaskNo());
        return task;
    }

    private ConfigurationImportTask persistedTask(String taskNo) {
        return context.getBean(JdbcConfigurationImportTaskRepository.class).findByTaskNo(taskNo)
                .orElseThrow(() -> new AssertionError("配置导入任务未持久化: " + taskNo));
    }

    private int count(String sql, String value) {
        Integer result = jdbc.queryForObject(sql, Integer.class, value);
        return result == null ? 0 : result;
    }

    private String scalar(String sql, String value) {
        return jdbc.queryForObject(sql, String.class, value);
    }

    private byte[] workbookBytes(String[] codes, String[] names, String[] formulas)
            throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        try {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("指标");
            String[] headers = {"指标编码", "指标名称", "计算公式", "适用视角", "单位",
                    "展示顺序", "是否有效", "AI简析模板"};
            org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
            for (int rowIndex = 0; rowIndex < codes.length; rowIndex++) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIndex + 1);
                String[] values = {codes[rowIndex], names[rowIndex], formulas[rowIndex],
                        "BASE", "CNY", String.valueOf(10 + rowIndex), "是", "测试模板"};
                for (int column = 0; column < values.length; column++) {
                    row.createCell(column).setCellValue(values[column]);
                }
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            workbook.write(output);
            return output.toByteArray();
        } finally {
            workbook.close();
        }
    }

    private byte[] invalidWorkbookBytes() throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        try {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("指标");
            sheet.createRow(0).createCell(0).setCellValue("指标编码");
            sheet.getRow(0).createCell(1).setCellValue("指标名称");
            sheet.createRow(1).createCell(0).setCellValue(runPrefix + "-INVALID");
            sheet.getRow(1).createCell(1).setCellValue("缺少公式");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            workbook.write(output);
            return output.toByteArray();
        } finally {
            workbook.close();
        }
    }

    private void verifySchema() {
        jdbc.execute("SELECT 1 FROM cfg.metric_definition LIMIT 0");
        jdbc.execute("SELECT 1 FROM flow.configuration_import_task LIMIT 0");
        jdbc.execute("SELECT 1 FROM flow.configuration_import_issue LIMIT 0");
    }

    private boolean hasSqlState(Throwable failure, String expected) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof java.sql.SQLException
                    && expected.equals(((java.sql.SQLException) current).getSQLState())) return true;
            current = current.getCause();
        }
        return false;
    }

    private static String repeat(String value, int count) {
        StringBuilder result = new StringBuilder(value.length() * count);
        for (int i = 0; i < count; i++) result.append(value);
        return result.toString();
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("BLOCKED: " + name + " 未配置，真实 PostgreSQL 事务契约不能跳过");
        }
        return value;
    }

    private static String environment(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value;
    }
}
