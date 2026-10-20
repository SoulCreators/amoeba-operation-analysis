package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.BatchStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ImportTask;
import com.sunwoda.evb.finance.analysis.domain.model.PnlFactSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * DATA-002：真实 PostgreSQL 上来源追溯 JDBC 写入的任务隔离契约。
 *
 * <p>本次新增只验证底层 writer 的替换删除条件同时受批次、视角和任务约束，
 * 不修改生产代码、数据库迁移或既有业务规则；迁移仍由当前测试环境预先执行。</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcPnlFactSourceWriterIsolationPostgresContractTest {
    private DataSource dataSource;
    private JdbcTemplate jdbc;
    private JdbcPnlFactSourceWriter writer;
    private String token;
    private String batchNo;
    private String targetTaskNo;
    private String otherTaskNo;

    @BeforeAll
    void connectToConfiguredPostgresAndVerifySchema() {
        // 本次新增沿用现有 PostgreSQL 契约测试的环境变量和迁移前置方式；缺少环境或迁移时跳过，不伪造数据库结果。
        String url = System.getenv("AMOEBA_DB_URL");
        assumeTrue(url != null && !url.trim().isEmpty(),
                "BLOCKED: AMOEBA_DB_URL is not set; DATA-002 writer isolation contract was skipped");

        dataSource = new DriverManagerDataSource(
                url, environment("AMOEBA_DB_USERNAME"), environment("AMOEBA_DB_PASSWORD"));
        jdbc = new JdbcTemplate(dataSource);
        try {
            jdbc.execute("SELECT id, batch_no, perspective FROM meta.analysis_batch LIMIT 0");
            jdbc.execute("SELECT id, task_no, batch_id FROM flow.import_task LIMIT 0");
            jdbc.execute("SELECT id, batch_id, perspective, source_task_no, source_sheet_name, source_row_no "
                    + "FROM raw.pnl_fact_trace LIMIT 0");
        } catch (RuntimeException failure) {
            assumeTrue(false, "BLOCKED: DATA-002 writer isolation PostgreSQL schema is not available: "
                    + failure.getMessage());
        }
        writer = new JdbcPnlFactSourceWriter(dataSource);
    }

    @AfterEach
    void removeFixtureRows() {
        // 本次新增只清理本测试生成的随机夹具，不改变业务数据和其他测试的数据库状态。
        if (jdbc == null || token == null) {
            return;
        }
        jdbc.update("DELETE FROM raw.pnl_fact_trace WHERE source_task_no IN (?, ?)",
                targetTaskNo, otherTaskNo);
        jdbc.update("DELETE FROM flow.import_task WHERE task_no IN (?, ?)",
                targetTaskNo, otherTaskNo);
        jdbc.update("DELETE FROM meta.analysis_batch WHERE batch_no = ?", batchNo);
        token = null;
        batchNo = null;
        targetTaskNo = null;
        otherTaskNo = null;
    }

    @Test
    void replacingOneTaskPreservesOtherTaskAndPerspectiveTraceRows() {
        // 本次新增锁定当前 writer 的删除边界，不引入版本留存、有效期或其他业务规则。
        token = UUID.randomUUID().toString().replace("-", "");
        batchNo = "DATA002-SI-B-" + token;
        targetTaskNo = "DATA002-SI-T-" + token;
        otherTaskNo = "DATA002-SI-O-" + token;

        insertBatch();
        insertTask(targetTaskNo, "DATA002_TARGET");
        insertTask(otherTaskNo, "DATA002_OTHER");

        insertTrace(AnalysisPerspective.BASE, targetTaskNo, "old-target.xlsx", 2,
                "TARGET-OLD");
        insertTrace(AnalysisPerspective.BASE, otherTaskNo, "other-task.xlsx", 20,
                "OTHER-TASK");
        insertTrace(AnalysisPerspective.PRODUCT_LINE, targetTaskNo, "other-perspective.xlsx", 30,
                "OTHER-PERSPECTIVE");

        AnalysisBatch batch = batch();
        ImportTask targetTask = task(targetTaskNo);
        writer.replace(batch, targetTask, Collections.singletonList(
                source(targetTaskNo, "new-target.xlsx", 8, "TARGET-NEW")));

        assertEquals(1, countTrace(AnalysisPerspective.BASE, targetTaskNo));
        assertEquals(0, countTraceByFile(AnalysisPerspective.BASE, targetTaskNo, "old-target.xlsx"));
        assertEquals(1, countTraceByFile(AnalysisPerspective.BASE, targetTaskNo, "new-target.xlsx"));
        assertEquals(1, countTrace(AnalysisPerspective.BASE, otherTaskNo));
        assertEquals(1, countTraceByFile(AnalysisPerspective.BASE, otherTaskNo, "other-task.xlsx"));
        assertEquals(1, countTrace(AnalysisPerspective.PRODUCT_LINE, targetTaskNo));
        assertEquals(1, countTraceByFile(
                AnalysisPerspective.PRODUCT_LINE, targetTaskNo, "other-perspective.xlsx"));
    }

    private void insertBatch() {
        jdbc.update("INSERT INTO meta.analysis_batch(batch_no, period, perspective, status, created_by) "
                        + "VALUES (?, ?, ?, ?, ?)",
                batchNo, "2026-10", AnalysisPerspective.BASE.getCode(),
                BatchStatus.DRAFT.name(), "DATA-002-WRITER-ISOLATION");
    }

    private void insertTask(String taskNo, String datasetCode) {
        jdbc.update("INSERT INTO flow.import_task(task_no, batch_id, dataset_code, file_name, "
                        + "checksum, status, created_by) "
                        + "SELECT ?, id, ?, ?, ?, ?, ? FROM meta.analysis_batch WHERE batch_no = ?",
                taskNo, datasetCode, taskNo + ".xlsx", "checksum-" + taskNo,
                "CREATED", "DATA-002-WRITER-ISOLATION", batchNo);
    }

    private void insertTrace(AnalysisPerspective perspective, String taskNo,
                             String fileName, int rowNo, String scopeCode) {
        jdbc.update("INSERT INTO raw.pnl_fact_trace(batch_id, perspective, source_task_no, "
                        + "source_file_name, source_checksum, source_sheet_name, source_row_no, "
                        + "scope_code, base_code, product_code, line_code) "
                        + "SELECT id, ?, ?, ?, ?, ?, ?, ?, ?, ?, ? "
                        + "FROM meta.analysis_batch WHERE batch_no = ?",
                perspective.getCode(), taskNo, fileName, "checksum-" + taskNo,
                "Sheet1", rowNo, scopeCode, null, null, null, batchNo);
    }

    private AnalysisBatch batch() {
        Long batchId = jdbc.queryForObject(
                "SELECT id FROM meta.analysis_batch WHERE batch_no = ?", Long.class, batchNo);
        return new AnalysisBatch(batchId, batchNo, "2026-10", AnalysisPerspective.BASE,
                BatchStatus.DRAFT, "DATA-002-WRITER-ISOLATION", LocalDateTime.now());
    }

    private ImportTask task(String taskNo) {
        Long taskId = jdbc.queryForObject(
                "SELECT id FROM flow.import_task WHERE task_no = ?", Long.class, taskNo);
        return new ImportTask(taskId, taskNo, batchNo, "DATA002_TARGET", taskNo + ".xlsx",
                "checksum-" + taskNo, "DATA-002-WRITER-ISOLATION", LocalDateTime.now());
    }

    private PnlFactSource source(String taskNo, String fileName, int rowNo, String scopeCode) {
        return new PnlFactSource(taskNo, fileName, "new-checksum", "Sheet1", rowNo,
                scopeCode, null, null, null);
    }

    private int countTrace(AnalysisPerspective perspective, String taskNo) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM raw.pnl_fact_trace "
                        + "WHERE batch_id = (SELECT id FROM meta.analysis_batch WHERE batch_no = ?) "
                        + "AND perspective = ? AND source_task_no = ?", Integer.class,
                batchNo, perspective.getCode(), taskNo);
    }

    private int countTraceByFile(AnalysisPerspective perspective, String taskNo, String fileName) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM raw.pnl_fact_trace "
                        + "WHERE batch_id = (SELECT id FROM meta.analysis_batch WHERE batch_no = ?) "
                        + "AND perspective = ? AND source_task_no = ? AND source_file_name = ?",
                Integer.class, batchNo, perspective.getCode(), taskNo, fileName);
    }

    private static String environment(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value;
    }
}
