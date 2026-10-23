package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.CalculationJob;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationJobStatus;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationStage;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationStageLog;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationStageStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * CALC-STAGE-JDBC-001：阶段日志与作业阶段/心跳字段的真实 PostgreSQL 往返契约。
 *
 * <p>本次新增测试验证作业新增阶段/心跳/错误码/追踪号/计数列与独立阶段日志表按
 * (job_id, stage) 幂等覆盖，并在测试结束后清理夹具。</p>
 */
class JdbcCalculationStageLogRoundTripPostgresContractTest {
    private static DataSource dataSource;
    private static JdbcTemplate jdbc;
    private static JdbcCalculationJobRepository jobRepository;
    private static JdbcCalculationStageLogRepository stageLogRepository;

    private String fixturePrefix;

    @BeforeAll
    static void connectToConfiguredPostgres() {
        String url = System.getenv("AMOEBA_DB_URL");
        assumeTrue(url != null && !url.trim().isEmpty(),
                "BLOCKED: AMOEBA_DB_URL is not set; stage-log PostgreSQL contract was not run");

        String username = System.getenv("AMOEBA_DB_USERNAME");
        assumeTrue(username != null && !username.trim().isEmpty(),
                "BLOCKED: AMOEBA_DB_USERNAME is not set; stage-log PostgreSQL contract was not run");

        String password = System.getenv("AMOEBA_DB_PASSWORD");
        assumeTrue(password != null,
                "BLOCKED: AMOEBA_DB_PASSWORD is not set; stage-log PostgreSQL contract was not run");

        dataSource = new DriverManagerDataSource(url, username, password);
        jdbc = new JdbcTemplate(dataSource);
        try {
            jdbc.execute("SELECT id, job_id, stage, status, started_at, finished_at, processed_rows, "
                    + "control_amount, issue_count, error_code, trace_id, message "
                    + "FROM flow.calculation_stage_log LIMIT 0");
        } catch (RuntimeException failure) {
            assumeTrue(false, "BLOCKED: flow.calculation_stage_log is not available: "
                    + failure.getMessage());
        }
        jobRepository = new JdbcCalculationJobRepository(dataSource);
        stageLogRepository = new JdbcCalculationStageLogRepository(dataSource);
    }

    @BeforeEach
    void createFixturePrefix() {
        fixturePrefix = "CALC-STAGE-JDBC-001-" + UUID.randomUUID().toString().replace("-", "");
    }

    @AfterEach
    void cleanupFixtureRows() {
        if (jdbc == null || fixturePrefix == null) {
            return;
        }
        String pattern = fixturePrefix + "%";
        jdbc.update("DELETE FROM flow.calculation_stage_log WHERE job_id LIKE ?", pattern);
        jdbc.update("DELETE FROM flow.calculation_job WHERE job_id LIKE ?", pattern);
    }

    @Test
    void jobRoundTripsStageHeartbeatAndMetricsColumns() {
        String jobId = fixturePrefix + "-job";
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 22, 9, 0, 0);
        LocalDateTime heartbeat = LocalDateTime.of(2026, 10, 22, 9, 5, 0);
        CalculationJob input = CalculationJob.restore(
                jobId, fixturePrefix + "-request", fixturePrefix + "-batch", createdAt,
                CalculationJobStatus.RUNNING, 1, null, createdAt, null,
                CalculationStage.ALLOCATE, heartbeat, null, "trace-job-1", 900L, 4);

        jobRepository.save(input);
        Optional<CalculationJob> loaded = jobRepository.findByJobId(jobId);

        assertTrue(loaded.isPresent());
        CalculationJob persisted = loaded.get();
        assertEquals(CalculationStage.ALLOCATE, persisted.getStage());
        assertEquals(heartbeat, persisted.getHeartbeatAt());
        assertEquals("trace-job-1", persisted.getTraceId());
        assertEquals(900L, persisted.getProcessedRows());
        assertEquals(4, persisted.getIssueCount());
    }

    @Test
    void stageLogRoundTripsAndUpsertsByJobAndStage() {
        String jobId = fixturePrefix + "-job";
        jobRepository.save(CalculationJob.restore(jobId, fixturePrefix + "-request",
                fixturePrefix + "-batch", LocalDateTime.now(), CalculationJobStatus.RUNNING,
                1, null, LocalDateTime.now(), null));

        stageLogRepository.save(CalculationStageLog.start(jobId, CalculationStage.NORMALIZE, "trace-stage-1"));
        stageLogRepository.save(CalculationStageLog.start(jobId, CalculationStage.MAP, "trace-stage-1"));
        // 同一作业同一阶段幂等覆盖，RUNNING 推进为终态。
        stageLogRepository.save(CalculationStageLog.start(jobId, CalculationStage.NORMALIZE, "trace-stage-1")
                .succeed(1200L, new BigDecimal("98765.432100"), 7));

        List<CalculationStageLog> logs = stageLogRepository.findByJobId(jobId);
        assertEquals(2, logs.size());
        assertEquals(CalculationStage.NORMALIZE, logs.get(0).getStage());
        assertEquals(CalculationStageStatus.SUCCEEDED, logs.get(0).getStatus());
        assertEquals(1200L, logs.get(0).getProcessedRows());
        assertEquals(new BigDecimal("98765.432100"), logs.get(0).getControlAmount());
        assertEquals(7, logs.get(0).getIssueCount());
        assertEquals("trace-stage-1", logs.get(0).getTraceId());
        assertNotNull(logs.get(0).getFinishedAt());
        assertEquals(CalculationStage.MAP, logs.get(1).getStage());
        assertEquals(CalculationStageStatus.RUNNING, logs.get(1).getStatus());
    }

    @Test
    void failedStageRoundTripsErrorCodeAndMessage() {
        String jobId = fixturePrefix + "-job";
        jobRepository.save(CalculationJob.restore(jobId, fixturePrefix + "-request",
                fixturePrefix + "-batch", LocalDateTime.now(), CalculationJobStatus.RUNNING,
                1, null, LocalDateTime.now(), null));

        stageLogRepository.save(CalculationStageLog.start(jobId, CalculationStage.RECONCILE, "trace-stage-2")
                .fail("R001", "勾稽差异未通过"));

        List<CalculationStageLog> logs = stageLogRepository.findByJobId(jobId);
        assertEquals(1, logs.size());
        assertEquals(CalculationStageStatus.FAILED, logs.get(0).getStatus());
        assertEquals("R001", logs.get(0).getErrorCode());
        assertEquals("勾稽差异未通过", logs.get(0).getMessage());
    }
}
