package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.application.model.ExportJob;
import com.sunwoda.evb.finance.analysis.application.model.ExportFormat;
import com.sunwoda.evb.finance.analysis.application.model.ExportTaskSnapshot;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResultStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
/**
 * 导出任务快照字段的真实 PostgreSQL 往返契约。
 *
 * <p>本次修改只新增契约测试，验证 batchNo、resultVersion、perspective、
 * resultStatus、summaryScopes、detailScopes 在首次创建、幂等重放和独立
 * 数据库回读中的组合一致性；不修改生产代码、迁移、权限或其他测试。</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcExportTaskRepositorySnapshotRoundTripPostgresContractTest {
    private static final String EXPORT_TABLE = "flow.export_task";
    private static final String MIGRATION_FILE = "V021__add_export_task_input_snapshot.sql";

    private DataSource dataSource;
    private JdbcTemplate jdbc;
    private JdbcExportTaskRepository repository;
    private String runPrefix;
    private final List<String> createdJobIds = new ArrayList<String>();

    @BeforeAll
    void connectToConfiguredPostgres() {
        String url = requiredNonBlankEnvironment("AMOEBA_DB_URL");
        String username = requiredNonBlankEnvironment("AMOEBA_DB_USERNAME");
        String password = requiredPresentEnvironment("AMOEBA_DB_PASSWORD");
        if (!migrationPresent()) {
            throw new IllegalStateException("缺少数据库迁移文件: database/migration/" + MIGRATION_FILE);
        }

        try {
            dataSource = new DriverManagerDataSource(url, username, password);
            jdbc = new JdbcTemplate(dataSource);
            jdbc.queryForObject("SELECT 1", Integer.class);
            Integer columns = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns"
                            + " WHERE table_schema = 'flow' AND table_name = 'export_task'"
                            + " AND column_name IN ('batch_no', 'result_version', 'perspective',"
                            + " 'result_status', 'format', 'summary_scopes', 'detail_scopes')",
                    Integer.class);
            if (columns == null || columns != 7) {
                throw new IllegalStateException("flow.export_task 快照字段不完整，期望 7 个字段，实际 "
                        + columns);
            }
            repository = new JdbcExportTaskRepository(dataSource);
        } catch (RuntimeException failure) {
            throw new IllegalStateException("PostgreSQL 导出任务快照契约初始化失败: "
                    + rootMessage(failure), failure);
        }
    }

    @BeforeEach
    void initializeIsolatedFixture() {
        if (repository == null) {
            throw new IllegalStateException("PostgreSQL 导出任务仓储未初始化");
        }
        runPrefix = "SNAPSHOT-ROUNDTRIP-" + UUID.randomUUID().toString().replace("-", "");
    }

    @AfterEach
    void removeDatabaseFixtures() {
        if (jdbc == null) {
            return;
        }
        for (String jobId : createdJobIds) {
            jdbc.update("DELETE FROM " + EXPORT_TABLE + " WHERE job_id = ?", jobId);
        }
        createdJobIds.clear();
    }

    @Test
    void createReplayAndIndependentDatabaseReadKeepTheFirstSnapshotCombination() {
        String userId = runPrefix + "-USER";
        String requestKey = runPrefix + "-REQUEST";
        ExportTaskSnapshot firstSnapshot = new ExportTaskSnapshot(
                runPrefix + "-BATCH-FIRST",
                runPrefix + "-RESULT-FIRST",
                AnalysisPerspective.PRODUCT_LINE,
                CalculationResultStatus.CONFIRMED,
                ExportFormat.XLSX,
                new LinkedHashSet<String>(Arrays.asList("SUMMARY-A", "SUMMARY-B")),
                new LinkedHashSet<String>(Arrays.asList("DETAIL-A", "DETAIL-B")));

        ExportJob first = repository.create(userId, requestKey, firstSnapshot);
        createdJobIds.add(first.getJobId());

        assertSnapshotEquals(firstSnapshot, repository.snapshot(first.getJobId()).get());
        assertDatabaseSnapshotEquals(first.getJobId(), firstSnapshot);

        ExportTaskSnapshot replaySnapshot = new ExportTaskSnapshot(
                runPrefix + "-BATCH-REPLAY",
                runPrefix + "-RESULT-REPLAY",
                AnalysisPerspective.BUSINESS_UNIT,
                CalculationResultStatus.PUBLISHED,
                ExportFormat.CSV,
                new LinkedHashSet<String>(Arrays.asList("REPLAY-SUMMARY")),
                new LinkedHashSet<String>(Arrays.asList("REPLAY-DETAIL")));

        ExportJob replay = repository.create(userId, requestKey, replaySnapshot);

        assertEquals(first.getJobId(), replay.getJobId(),
                "相同用户和 requestKey 的重放必须复用首次任务");
        assertSnapshotEquals(firstSnapshot, repository.snapshot(replay.getJobId()).get());
        assertDatabaseSnapshotEquals(replay.getJobId(), firstSnapshot);
    }

    private void assertSnapshotEquals(ExportTaskSnapshot expected, ExportTaskSnapshot actual) {
        assertNotNull(actual);
        assertEquals(expected.getBatchNo(), actual.getBatchNo());
        assertEquals(expected.getResultVersion(), actual.getResultVersion());
        assertEquals(expected.getPerspective(), actual.getPerspective());
        assertEquals(expected.getResultStatus(), actual.getResultStatus());
        assertEquals(expected.getFormat(), actual.getFormat());
        assertEquals(expected.getSummaryScopes(), actual.getSummaryScopes());
        assertEquals(expected.getDetailScopes(), actual.getDetailScopes());
    }

    private void assertDatabaseSnapshotEquals(String jobId, ExportTaskSnapshot expected) {
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT batch_no, result_version, perspective, result_status, format,"
                        + " summary_scopes, detail_scopes FROM " + EXPORT_TABLE
                        + " WHERE job_id = ?", jobId);

        assertEquals(expected.getBatchNo(), row.get("batch_no"));
        assertEquals(expected.getResultVersion(), row.get("result_version"));
        assertEquals(expected.getPerspective().getCode(), row.get("perspective"));
        assertEquals(expected.getResultStatus().name(), row.get("result_status"));
        assertEquals(expected.getFormat().name(), row.get("format"));
        assertArrayEquals(expected.getSummaryScopes().toArray(new String[0]),
                readTextArray(row.get("summary_scopes")));
        assertArrayEquals(expected.getDetailScopes().toArray(new String[0]),
                readTextArray(row.get("detail_scopes")));
    }

    private String[] readTextArray(Object value) {
        assertNotNull(value, "快照范围数组必须持久化为 PostgreSQL text[]");
        try {
            Object raw = ((Array) value).getArray();
            if (!(raw instanceof Object[])) {
                throw new AssertionError("PostgreSQL text[] 回读结果不是对象数组");
            }
            Object[] values = (Object[]) raw;
            String[] result = new String[values.length];
            for (int index = 0; index < values.length; index++) {
                result[index] = values[index] == null ? null : values[index].toString();
            }
            return result;
        } catch (java.sql.SQLException failure) {
            throw new AssertionError("读取 PostgreSQL text[] 快照失败", failure);
        }
    }

    private static boolean migrationPresent() {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("database/migration").resolve(MIGRATION_FILE))) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private static String requiredNonBlankEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("缺少 PostgreSQL 契约环境变量: " + name);
        }
        return value.trim();
    }

    /** 密码允许为空，只有未注入环境变量时才判定为契约配置缺失。 */
    private static String requiredPresentEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null) {
            throw new IllegalStateException("缺少 PostgreSQL 契约环境变量: " + name);
        }
        return value;
    }

    private static String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null
                ? current.getClass().getSimpleName() : current.getMessage();
    }
}
