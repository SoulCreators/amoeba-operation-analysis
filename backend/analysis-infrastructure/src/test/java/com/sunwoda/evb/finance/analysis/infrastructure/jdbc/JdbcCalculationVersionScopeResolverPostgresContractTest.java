package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationVersion;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationVersionStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * DATA-004-TECH-003-REVISION-003：来源版本 scope 的 PostgreSQL 契约。
 *
 * <p>本次新增测试：验证同一批次中 resolver 只读取指定 calculation_version_id 的真实结果范围，
 * 并验证没有结果头的历史版本不会被伪造为 ALL。</p>
 */
class JdbcCalculationVersionScopeResolverPostgresContractTest {
    private static DataSource dataSource;
    private static JdbcTemplate jdbc;
    private static JdbcCalculationVersionScopeResolver resolver;

    private String suffix;
    private String batchNo;
    private String jobId;
    private String snapshotId;
    private String sourceVersionId;
    private String legacyVersionId;

    @BeforeAll
    static void connect() {
        String url = System.getenv("AMOEBA_DB_URL");
        assumeTrue(url != null && !url.trim().isEmpty(),
                "BLOCKED: AMOEBA_DB_URL is not set; scope PostgreSQL contract was not run");
        dataSource = new DriverManagerDataSource(url, environment("AMOEBA_DB_USERNAME"),
                environment("AMOEBA_DB_PASSWORD"));
        jdbc = new JdbcTemplate(dataSource);
        verifySchema();
        resolver = new JdbcCalculationVersionScopeResolver(dataSource);
    }

    @AfterAll
    static void close() {
        // DriverManagerDataSource 不持有需要显式关闭的连接池资源。
    }

    @BeforeEach
    void seedFixture() {
        suffix = UUID.randomUUID().toString().replace("-", "");
        batchNo = "TEST-SCOPE-RESOLVER-" + suffix;
        jobId = "TEST-SCOPE-JOB-" + suffix;
        snapshotId = "TEST-SCOPE-SNAPSHOT-" + suffix;
        sourceVersionId = "TEST-SCOPE-SOURCE-" + suffix;
        legacyVersionId = "TEST-SCOPE-LEGACY-" + suffix;

        jdbc.update("INSERT INTO meta.analysis_batch "
                        + "(batch_no, period, status, perspective, created_by) VALUES (?, ?, ?, ?, ?)",
                batchNo, "2099-12", "DRAFT", "BASE", "scope-contract");
        jdbc.update("INSERT INTO flow.calculation_job "
                        + "(job_id, request_key, batch_no, status, attempt_no) VALUES (?, ?, ?, ?, ?)",
                jobId, "TEST-SCOPE-REQUEST-" + suffix, batchNo, "SUCCEEDED", 1);
        jdbc.update("INSERT INTO calc.calculation_snapshot "
                        + "(snapshot_id, snapshot_type, schema_version, content_json, source_version_set, "
                        + "content_hash, source_version_hash, rule_version, created_by) "
                        + "VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?)",
                snapshotId, "INPUT", "SCOPE-RESOLVER", "{\"source\":\"scope-contract\"}",
                "[\"SCOPE-RESOLVER\"]", "content-" + suffix, "source-" + suffix,
                "rule-scope", "scope-contract");

        insertVersion(sourceVersionId, "V-SOURCE", true);
        insertVersion(legacyVersionId, "V-LEGACY", false);
        jdbc.update("INSERT INTO calc.pnl_result "
                        + "(batch_id, perspective, scope_code, calculation_version_id) "
                        + "SELECT id, ?, ?, ? FROM meta.analysis_batch WHERE batch_no = ?",
                "BASE", "SCOPE-A", sourceVersionId, batchNo);
        jdbc.update("INSERT INTO calc.pnl_result "
                        + "(batch_id, perspective, scope_code, calculation_version_id) "
                        + "SELECT id, ?, ?, ? FROM meta.analysis_batch WHERE batch_no = ?",
                "BASE", "SCOPE-B", sourceVersionId, batchNo);
    }

    @AfterEach
    void cleanupFixture() {
        if (suffix == null || jdbc == null) {
            return;
        }
        jdbc.update("DELETE FROM calc.pnl_result WHERE calculation_version_id IN (?, ?)",
                sourceVersionId, legacyVersionId);
        jdbc.update("DELETE FROM calc.calculation_version WHERE calculation_version_id IN (?, ?)",
                sourceVersionId, legacyVersionId);
        jdbc.update("DELETE FROM calc.calculation_snapshot WHERE snapshot_id = ?", snapshotId);
        jdbc.update("DELETE FROM flow.calculation_job WHERE job_id = ?", jobId);
        jdbc.update("DELETE FROM meta.analysis_batch WHERE batch_no = ?", batchNo);
    }

    @Test
    void resolvesOnlyTheSourceVersionResultScopes() {
        Set<String> scopes = resolver.resolve(sourceVersion());

        assertEquals(Arrays.asList("SCOPE-A", "SCOPE-B"),
                Arrays.asList(scopes.toArray(new String[0])));
    }

    @Test
    void legacyVersionWithoutResultRowsFailsClosed() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> resolver.resolve(legacyVersion()));

        assertTrue(failure.getMessage().contains(legacyVersionId));
    }

    private void insertVersion(String versionId, String versionNo, boolean current) {
        // 本次修复：使用明确的 VALUES 参数顺序，避免测试 SQL 占位符数量多于绑定参数。
        jdbc.update("INSERT INTO calc.calculation_version ("
                        + "calculation_version_id, batch_id, batch_no, perspective, version_no, job_id, "
                        + "snapshot_id, source_version_hash, config_hash, input_fingerprint, status, "
                        + "current_flag, calculated_by, calculated_at) VALUES (?, "
                        + "(SELECT id FROM meta.analysis_batch WHERE batch_no = ?), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                versionId, batchNo, batchNo, "BASE", versionNo, jobId, snapshotId, "source-" + suffix,
                "config-" + suffix, "input-" + suffix, "PUBLISHED", current, "calculator",
                TimestampValue.of(LocalDateTime.of(2099, 12, 1, 10, 0)));
    }

    private CalculationVersion sourceVersion() {
        return version(sourceVersionId, "V-SOURCE", true);
    }

    private CalculationVersion legacyVersion() {
        return version(legacyVersionId, "V-LEGACY", false);
    }

    private CalculationVersion version(String versionId, String versionNo, boolean current) {
        return new CalculationVersion(versionId, batchNo, AnalysisPerspective.BASE, versionNo,
                jobId, snapshotId, "source-" + suffix, "config-" + suffix, "input-" + suffix,
                CalculationVersionStatus.PUBLISHED, current, null, "trace-" + suffix, "calculator",
                LocalDateTime.of(2099, 12, 1, 10, 0), null, null, null, null);
    }

    private static void verifySchema() {
        try {
            jdbc.execute("SELECT 1 FROM calc.pnl_result LIMIT 0");
            jdbc.execute("SELECT 1 FROM calc.calculation_version LIMIT 0");
            jdbc.execute("SELECT calculation_version_id FROM calc.pnl_result LIMIT 0");
        } catch (RuntimeException failure) {
            assumeTrue(false, "BLOCKED: scope resolver PostgreSQL schema is not available: "
                    + failure.getMessage());
        }
    }

    private static String environment(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value;
    }

    /** 保持 JDBC 参数类型为 Timestamp，避免依赖 PostgreSQL 驱动对 LocalDateTime 的隐式转换。 */
    private static final class TimestampValue {
        private TimestampValue() {
        }

        static java.sql.Timestamp of(LocalDateTime value) {
            return java.sql.Timestamp.valueOf(value);
        }
    }
}
