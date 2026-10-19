package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.application.model.ExportCleanupTask;
import com.sunwoda.evb.finance.analysis.application.model.ExportJob;
import com.sunwoda.evb.finance.analysis.application.port.ExportTaskPort;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Optional executable PostgreSQL contract; skipped unless AMOEBA_DB_URL is configured. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcExportCleanupRepositorySecurityContractTest {
    private DataSource dataSource;
    private JdbcTemplate jdbc;
    private ExportTaskPort tasks;
    private JdbcExportCleanupRepository cleanup;
    private String runPrefix;

    @BeforeAll
    void connectToConfiguredPostgres() {
        String url = System.getenv("AMOEBA_DB_URL");
        assumeTrue(url != null && !url.trim().isEmpty(),
                "BLOCKED: AMOEBA_DB_URL is not set; PostgreSQL cleanup contract was not run");

        dataSource = new DriverManagerDataSource(
                url, environment("AMOEBA_DB_USERNAME"), environment("AMOEBA_DB_PASSWORD"));
        jdbc = new JdbcTemplate(dataSource);
        try {
            Integer cleanupColumns = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns "
                            + "WHERE table_schema = 'flow' AND table_name = 'export_task' "
                            + "AND column_name IN ('cleanup_status', 'cleanup_claim_token', "
                            + "'cleanup_lease_until', 'cleanup_attempts', "
                            + "'cleanup_next_attempt_at')",
                    Integer.class);
            assumeTrue(cleanupColumns != null && cleanupColumns == 5,
                    "BLOCKED: V019 cleanup columns are not available");
        } catch (RuntimeException failure) {
            assumeTrue(false, "BLOCKED: V019 cleanup columns are not available: "
                    + failure.getMessage());
        }
        tasks = new JdbcExportTaskRepository(dataSource);
        cleanup = new JdbcExportCleanupRepository(dataSource);
        runPrefix = "TEST-CLEANUP-DB-" + UUID.randomUUID().toString().replace("-", "");
    }

    @Test
    void expiryClaimRetryAndTerminalFailureArePersistedWithoutDeletingTaskRows() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        ExportJob queued = tasks.create(user("Q"), key("Q"), "queued.csv", "text/csv");
        ExportJob running = tasks.create(user("R"), key("R"), "running.csv", "text/csv");
        tasks.markRunning(running.getJobId());
        ExportJob succeeded = tasks.create(user("S"), key("S"), "success.csv", "text/csv");
        tasks.complete(succeeded.getJobId(), "success.csv", "text/csv", new byte[] {1});
        due(queued.getJobId(), now);
        due(running.getJobId(), now);
        due(succeeded.getJobId(), now);

        cleanup.expireDueTasks(now);
        assertEquals("NOT_REQUIRED", cleanupStatus(queued.getJobId()));
        assertEquals("NOT_REQUIRED", cleanupStatus(running.getJobId()));
        assertEquals("PENDING", cleanupStatus(succeeded.getJobId()));

        ExportCleanupTask first = onlyFor(claim(now), succeeded.getJobId());
        assertNotNull(first.getClaimToken());
        assertEquals(1, first.getAttempts());
        cleanup.markCleanupRetry(first.getJobId(), first.getClaimToken(), "first failure",
                now.plusMinutes(1), now, false);

        for (int attempts = 2; attempts <= 3; attempts++) {
            makeDue(succeeded.getJobId(), now);
            ExportCleanupTask next = onlyFor(claim(now), succeeded.getJobId());
            assertEquals(attempts, next.getAttempts());
            cleanup.markCleanupRetry(next.getJobId(), next.getClaimToken(), "retry failure",
                    now.plusMinutes(1), now, attempts == 3);
        }

        assertEquals("FAILED", cleanupStatus(succeeded.getJobId()));
        assertEquals(3, attempts(succeeded.getJobId()));
        assertEquals(1, count(succeeded.getJobId()));
        cleanup.markCleanupSucceeded(succeeded.getJobId(), first.getClaimToken(), now);
        assertEquals("FAILED", cleanupStatus(succeeded.getJobId()),
                "a stale claim token must not acknowledge a later terminal state");
    }

    @Test
    void validClaimCanBeAcknowledgedAndLeavesTaskMetadata() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        ExportJob succeeded = tasks.create(user("D"), key("D"), "delete.csv", "text/csv");
        tasks.complete(succeeded.getJobId(), "delete.csv", "text/csv", new byte[] {2});
        due(succeeded.getJobId(), now);
        cleanup.expireDueTasks(now);

        ExportCleanupTask claim = onlyFor(claim(now), succeeded.getJobId());
        cleanup.markCleanupSucceeded(claim.getJobId(), claim.getClaimToken(), now);
        assertEquals("DELETED", cleanupStatus(succeeded.getJobId()));
        assertEquals(1, count(succeeded.getJobId()));
    }

    private List<ExportCleanupTask> claim(LocalDateTime now) {
        return cleanup.claimExpired("contract-worker", 10, now, now.plusMinutes(5));
    }

    private void due(String jobId, LocalDateTime now) {
        jdbc.update("UPDATE flow.export_task SET created_at = ?, expires_at = ? WHERE job_id = ?",
                Timestamp.valueOf(now.minusMinutes(2)),
                Timestamp.valueOf(now.minusMinutes(1)), jobId);
    }

    private void makeDue(String jobId, LocalDateTime now) {
        jdbc.update("UPDATE flow.export_task SET cleanup_next_attempt_at = ? WHERE job_id = ?",
                Timestamp.valueOf(now), jobId);
    }

    private String cleanupStatus(String jobId) {
        return jdbc.queryForObject("SELECT cleanup_status FROM flow.export_task WHERE job_id = ?",
                String.class, jobId);
    }

    private int attempts(String jobId) {
        return jdbc.queryForObject("SELECT cleanup_attempts FROM flow.export_task WHERE job_id = ?",
                Integer.class, jobId);
    }

    private int count(String jobId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM flow.export_task WHERE job_id = ?",
                Integer.class, jobId);
    }

    private ExportCleanupTask onlyFor(List<ExportCleanupTask> values, String jobId) {
        return values.stream().filter(value -> jobId.equals(value.getJobId())).findFirst()
                .orElseThrow(() -> new AssertionError("expected cleanup claim for " + jobId));
    }

    private String user(String suffix) { return runPrefix + "-U-" + suffix; }

    private String key(String suffix) { return runPrefix + "-K-" + suffix; }

    private static String environment(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value;
    }
}
