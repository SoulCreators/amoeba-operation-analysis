package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.CalculationJob;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationJobStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * CALC-001-JDBC-ADAPTER-001：计算作业 JDBC 适配器的真实 PostgreSQL 契约。
 *
 * <p>本次新增测试只验证 save/findByJobId、活动 requestKey 约束及数据库字段约束，
 * 不测试或决定计算 Service、Controller、Worker、重试、历史版本、批次结果事务和权限。</p>
 */
class JdbcCalculationJobRepositoryPostgresContractTest {
    private static DataSource dataSource;
    private static JdbcTemplate jdbc;
    private static JdbcCalculationJobRepository repository;

    private String fixturePrefix;

    @BeforeAll
    static void connectToConfiguredPostgres() {
        // 本次新增契约：使用项目约定的 PostgreSQL 环境变量；未配置时明确跳过而不是伪造通过。
        String url = System.getenv("AMOEBA_DB_URL");
        assumeTrue(url != null && !url.trim().isEmpty(),
                "BLOCKED: AMOEBA_DB_URL is not set; calculation-job PostgreSQL contract was not run");

        String username = System.getenv("AMOEBA_DB_USERNAME");
        assumeTrue(username != null && !username.trim().isEmpty(),
                "BLOCKED: AMOEBA_DB_USERNAME is not set; calculation-job PostgreSQL contract was not run");

        String password = System.getenv("AMOEBA_DB_PASSWORD");
        assumeTrue(password != null,
                "BLOCKED: AMOEBA_DB_PASSWORD is not set; calculation-job PostgreSQL contract was not run");

        dataSource = new DriverManagerDataSource(url, username, password);
        jdbc = new JdbcTemplate(dataSource);
        try {
            jdbc.execute("SELECT job_id, request_key, batch_no, status, attempt_no, last_error, "
                    + "created_at, started_at, finished_at FROM flow.calculation_job LIMIT 0");
        } catch (RuntimeException failure) {
            assumeTrue(false, "BLOCKED: flow.calculation_job is not available: "
                    + failure.getMessage());
        }
        repository = new JdbcCalculationJobRepository(dataSource);
    }

    @BeforeEach
    void createFixturePrefix() {
        fixturePrefix = "CALC-001-JDBC-ADAPTER-001-"
                + UUID.randomUUID().toString().replace("-", "");
    }

    @AfterEach
    void cleanupFixtureRows() {
        // 本次新增契约：只清理本测试随机前缀的行，避免污染 PostgreSQL 中的其他计算作业。
        if (jdbc == null || fixturePrefix == null) {
            return;
        }
        String pattern = fixturePrefix + "%";
        jdbc.update("DELETE FROM flow.calculation_job "
                        + "WHERE job_id LIKE ? OR request_key LIKE ? OR batch_no LIKE ?",
                pattern, pattern, pattern);
    }

    @Test
    void saveAndFindByJobIdRoundTripsEveryCalculationJobField() {
        // 本次新增契约：真实 save/findByJobId 往返验证标识、状态、尝试、错误和三个时间字段。
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 21, 10, 15, 30, 123456000);
        LocalDateTime startedAt = LocalDateTime.of(2026, 10, 21, 10, 16, 30, 234567000);
        LocalDateTime finishedAt = LocalDateTime.of(2026, 10, 21, 10, 17, 30, 345678000);
        CalculationJob input = CalculationJob.restore(
                fixturePrefix + "-roundtrip-job",
                fixturePrefix + "-roundtrip-request",
                fixturePrefix + "-roundtrip-batch",
                createdAt,
                CalculationJobStatus.FAILED,
                3,
                "calculation failed for contract test",
                startedAt,
                finishedAt);

        CalculationJob saved = repository.save(input);
        Optional<CalculationJob> loaded = repository.findByJobId(input.getJobId());

        assertNotNull(saved);
        assertTrue(loaded.isPresent());
        CalculationJob persisted = loaded.get();
        assertEquals(input.getJobId(), persisted.getJobId());
        assertEquals(input.getRequestKey(), persisted.getRequestKey());
        assertEquals(input.getBatchNo(), persisted.getBatchNo());
        assertEquals(input.getCreatedAt(), persisted.getCreatedAt());
        assertEquals(input.getStatus(), persisted.getStatus());
        assertEquals(input.getAttemptNo(), persisted.getAttemptNo());
        assertEquals(input.getLastError(), persisted.getLastError());
        assertEquals(input.getStartedAt(), persisted.getStartedAt());
        assertEquals(input.getFinishedAt(), persisted.getFinishedAt());
    }

    @Test
    void activeRequestKeyConflictIsRejectedByPostgres() {
        // 本次新增契约：只验证 QUEUED/RUNNING 的活动 requestKey 冲突由数据库拒绝。
        String requestKey = fixturePrefix + "-active-request";
        repository.save(job(fixturePrefix + "-active-first", requestKey,
                CalculationJobStatus.QUEUED, 1, null));
        CalculationJob conflicting = job(fixturePrefix + "-active-second", requestKey,
                CalculationJobStatus.RUNNING, 1, null);

        assertThrows(DataAccessException.class, () -> repository.save(conflicting));
        Optional<CalculationJob> active = repository.findActiveByRequestKey(requestKey);
        assertTrue(active.isPresent());
        assertEquals(fixturePrefix + "-active-first", active.get().getJobId());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM flow.calculation_job WHERE request_key = ?", Integer.class,
                requestKey));
    }

    @Test
    void terminalJobReleasesRequestKeyForNextActiveJob() {
        // 本次新增契约：分别验证 SUCCEEDED/FAILED 终态释放 requestKey，未引入重试或幂等业务规则。
        for (CalculationJobStatus terminalStatus : new CalculationJobStatus[]{
                CalculationJobStatus.SUCCEEDED, CalculationJobStatus.FAILED}) {
            String requestKey = fixturePrefix + "-released-" + terminalStatus.name();
            String terminalJobId = fixturePrefix + "-terminal-" + terminalStatus.name();
            repository.save(job(terminalJobId, requestKey, terminalStatus, 2,
                    terminalStatus == CalculationJobStatus.FAILED ? "terminal failure" : null));

            String replacementJobId = fixturePrefix + "-replacement-" + terminalStatus.name();
            repository.save(job(replacementJobId, requestKey,
                    CalculationJobStatus.QUEUED, 1, null));

            Optional<CalculationJob> active = repository.findActiveByRequestKey(requestKey);
            assertTrue(active.isPresent());
            assertEquals(replacementJobId, active.get().getJobId());
        }
    }

    @Test
    void databaseRejectsIllegalStatusAttemptAndFailureError() {
        // 本次新增契约：绕过 Java 枚举直接探测数据库约束，确保非法状态、尝试次数和错误值不能落库。
        assertThrows(DataAccessException.class, () -> insertRaw(
                fixturePrefix + "-invalid-status", fixturePrefix + "-invalid-status-request",
                "UNKNOWN", 1, null));
        assertThrows(DataAccessException.class, () -> insertRaw(
                fixturePrefix + "-invalid-attempt", fixturePrefix + "-invalid-attempt-request",
                "QUEUED", 0, null));
        assertThrows(DataAccessException.class, () -> insertRaw(
                fixturePrefix + "-invalid-error-blank", fixturePrefix + "-invalid-error-blank-request",
                "FAILED", 1, "   "));
        assertThrows(DataAccessException.class, () -> insertRaw(
                fixturePrefix + "-invalid-error-null", fixturePrefix + "-invalid-error-null-request",
                "FAILED", 1, null));
    }

    private CalculationJob job(String jobId, String requestKey, CalculationJobStatus status,
                                int attemptNo, String lastError) {
        return CalculationJob.restore(jobId, requestKey, batchNoFor(jobId),
                LocalDateTime.of(2026, 10, 21, 11, 0), status, attemptNo, lastError,
                null, null);
    }

    private void insertRaw(String jobId, String requestKey, String status, int attemptNo,
                           String lastError) {
        jdbc.update("INSERT INTO flow.calculation_job "
                        + "(job_id, request_key, batch_no, status, attempt_no, last_error) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                jobId, requestKey, batchNoFor(jobId),
                status, attemptNo, lastError);
    }

    private String batchNoFor(String jobId) {
        // 本次新增契约：避免重复拼接随机前缀，确保夹具长度落在 V025 的 VARCHAR(128) 边界内。
        String suffix = jobId.startsWith(fixturePrefix)
                ? jobId.substring(fixturePrefix.length()) : jobId;
        return fixturePrefix + "-batch-" + suffix;
    }
}
