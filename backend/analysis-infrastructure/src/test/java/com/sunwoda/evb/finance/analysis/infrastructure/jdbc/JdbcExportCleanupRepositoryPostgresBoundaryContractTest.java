package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.application.model.ExportCleanupTask;
import com.sunwoda.evb.finance.analysis.application.model.ExportJob;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real PostgreSQL boundary contract for export cleanup expiration and claims.
 *
 * <p>The environment is mandatory.  The test deliberately fails during setup
 * when AMOEBA_DB_URL or AMOEBA_DB_USERNAME is absent, so an unavailable
 * database cannot be reported as a successful contract run.</p>
 */
class JdbcExportCleanupRepositoryPostgresBoundaryContractTest {
    private static DataSource dataSource;
    private static JdbcTemplate jdbc;
    private static JdbcExportTaskRepository tasks;
    private static JdbcExportCleanupRepository cleanup;
    private static TransactionTemplate transaction;

    private final List<String> createdJobIds = new ArrayList<String>();
    private String runToken;

    @BeforeAll
    static void connectToConfiguredPostgresAndVerifyMigration() {
        String url = requiredNonBlankEnvironment("AMOEBA_DB_URL");
        String username = requiredNonBlankEnvironment("AMOEBA_DB_USERNAME");
        String password = requiredEnvironment("AMOEBA_DB_PASSWORD");

        dataSource = new DriverManagerDataSource(url, username, password);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("SELECT job_id, cleanup_status, cleanup_attempts "
                + "FROM flow.export_task LIMIT 0");

        tasks = new JdbcExportTaskRepository(dataSource);
        cleanup = new JdbcExportCleanupRepository(dataSource);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @AfterEach
    void removeRandomFixtures() {
        if (jdbc == null) {
            return;
        }
        for (String jobId : createdJobIds) {
            jdbc.update("DELETE FROM flow.export_task WHERE job_id = ?", jobId);
        }
        createdJobIds.clear();
        runToken = null;
    }

    @Test
    void expiredSuccessfulTaskEntersQueueWhileNonEligibleTasksDoNot() {
        inRollbackTransaction(new Runnable() {
            @Override
            public void run() {
                LocalDateTime now = LocalDateTime.now().withNano(0);
                ExportJob expiredSuccess = createTask("EXPIRED-SUCCESS");
                tasks.complete(expiredSuccess.getJobId(), fileName("EXPIRED-SUCCESS"),
                        "text/csv", new byte[] {1});
                makeExpired(expiredSuccess.getJobId(), now);

                ExportJob futureSuccess = createTask("FUTURE-SUCCESS");
                tasks.complete(futureSuccess.getJobId(), fileName("FUTURE-SUCCESS"),
                        "text/csv", new byte[] {2});

                ExportJob expiredQueued = createTask("EXPIRED-QUEUED");
                makeExpired(expiredQueued.getJobId(), now);

                ExportJob expiredRunning = createTask("EXPIRED-RUNNING");
                tasks.markRunning(expiredRunning.getJobId());
                makeExpired(expiredRunning.getJobId(), now);

                ExportJob failed = createTask("FAILED");
                tasks.fail(failed.getJobId(), "contract failure");
                makeExpired(failed.getJobId(), now);

                cleanup.expireDueTasks(now);

                assertEquals("PENDING", cleanupStatus(expiredSuccess.getJobId()));
                assertEquals("NOT_REQUIRED", cleanupStatus(futureSuccess.getJobId()));
                assertEquals("NOT_REQUIRED", cleanupStatus(expiredQueued.getJobId()));
                assertEquals("NOT_REQUIRED", cleanupStatus(expiredRunning.getJobId()));
                assertEquals("NOT_REQUIRED", cleanupStatus(failed.getJobId()));

                List<ExportCleanupTask> claims = cleanup.claimExpired(
                        workerId(), 1000, now, now.plusMinutes(5));
                ExportCleanupTask claim = findClaim(claims, expiredSuccess.getJobId());
                assertEquals(expiredSuccess.getJobId(), claim.getFileKey());
                assertFalse(hasClaim(claims, futureSuccess.getJobId()));
                assertFalse(hasClaim(claims, expiredQueued.getJobId()));
                assertFalse(hasClaim(claims, expiredRunning.getJobId()));
                assertFalse(hasClaim(claims, failed.getJobId()));
            }
        });
    }

    @Test
    void claimParameterBoundsShortCircuitAndValidLeaseTokenUpdatesCleanupState() {
        inRollbackTransaction(new Runnable() {
            @Override
            public void run() {
                LocalDateTime now = LocalDateTime.now().withNano(0);
                LocalDateTime leaseUntil = now.plusMinutes(5);
                ExportJob succeeded = createTask("CLAIM-BOUNDARY");
                tasks.complete(succeeded.getJobId(), fileName("CLAIM-BOUNDARY"),
                        "text/csv", new byte[] {3});
                makeExpired(succeeded.getJobId(), now);
                cleanup.expireDueTasks(now);

                assertTrue(cleanup.claimExpired(workerId(), 0, now, leaseUntil).isEmpty());
                assertTrue(cleanup.claimExpired(workerId(), -1, now, leaseUntil).isEmpty());
                assertThrows(IllegalArgumentException.class,
                        () -> cleanup.claimExpired(" ", 1, now, leaseUntil));
                assertThrows(IllegalArgumentException.class,
                        () -> cleanup.claimExpired(workerId(), 1, now, now.minusSeconds(1)));
                assertEquals(0, cleanupAttempts(succeeded.getJobId()));
                assertEquals("PENDING", cleanupStatus(succeeded.getJobId()));

                List<ExportCleanupTask> claims = cleanup.claimExpired(
                        workerId(), 1000, now, leaseUntil);
                ExportCleanupTask claim = findClaim(claims, succeeded.getJobId());
                assertNotNull(claim.getClaimToken());
                assertFalse(claim.getClaimToken().trim().isEmpty());
                assertEquals(1, claim.getAttempts());
                assertEquals("IN_PROGRESS", cleanupStatus(succeeded.getJobId()));
                assertEquals(leaseUntil, cleanupLeaseUntil(succeeded.getJobId()));
                assertEquals(claim.getClaimToken(), cleanupClaimToken(succeeded.getJobId()));

                cleanup.markCleanupSucceeded(succeeded.getJobId(),
                        claim.getClaimToken(), now);

                assertEquals("DELETED", cleanupStatus(succeeded.getJobId()));
                assertEquals(1, cleanupAttempts(succeeded.getJobId()));
                assertNull(cleanupClaimToken(succeeded.getJobId()));
                assertNull(cleanupLeaseUntil(succeeded.getJobId()));
                assertNotNull(cleanupCompletedAt(succeeded.getJobId()));
            }
        });
    }

    private void inRollbackTransaction(Runnable assertions) {
        transaction.executeWithoutResult(status -> {
            runToken = UUID.randomUUID().toString().replace("-", "");
            assertions.run();
            status.setRollbackOnly();
        });
    }

    private ExportJob createTask(String suffix) {
        String userId = "GOV006-U-" + runToken + "-" + suffix;
        String requestKey = "GOV006-K-" + runToken + "-" + suffix;
        ExportJob job = tasks.create(userId, requestKey, fileName(suffix), "text/csv");
        createdJobIds.add(job.getJobId());
        return job;
    }

    private String fileName(String suffix) {
        return "gov006-" + runToken + "-" + suffix.toLowerCase() + ".csv";
    }

    private void makeExpired(String jobId, LocalDateTime now) {
        jdbc.update("UPDATE flow.export_task SET created_at = ?, expires_at = ?, updated_at = ? "
                        + "WHERE job_id = ?",
                Timestamp.valueOf(now.minusMinutes(2)),
                Timestamp.valueOf(now.minusMinutes(1)),
                Timestamp.valueOf(now), jobId);
    }

    private String workerId() {
        return "GOV006-W-" + runToken;
    }

    private String cleanupStatus(String jobId) {
        return jdbc.queryForObject("SELECT cleanup_status FROM flow.export_task WHERE job_id = ?",
                String.class, jobId);
    }

    private int cleanupAttempts(String jobId) {
        return jdbc.queryForObject("SELECT cleanup_attempts FROM flow.export_task WHERE job_id = ?",
                Integer.class, jobId);
    }

    private String cleanupClaimToken(String jobId) {
        return jdbc.queryForObject("SELECT cleanup_claim_token FROM flow.export_task WHERE job_id = ?",
                String.class, jobId);
    }

    private LocalDateTime cleanupLeaseUntil(String jobId) {
        Timestamp value = jdbc.queryForObject(
                "SELECT cleanup_lease_until FROM flow.export_task WHERE job_id = ?",
                Timestamp.class, jobId);
        return value == null ? null : value.toLocalDateTime();
    }

    private Timestamp cleanupCompletedAt(String jobId) {
        return jdbc.queryForObject(
                "SELECT cleanup_completed_at FROM flow.export_task WHERE job_id = ?",
                Timestamp.class, jobId);
    }

    private ExportCleanupTask findClaim(List<ExportCleanupTask> claims, String jobId) {
        return claims.stream().filter(value -> jobId.equals(value.getJobId())).findFirst()
                .orElseThrow(() -> new AssertionError("expected cleanup claim for " + jobId));
    }

    private boolean hasClaim(List<ExportCleanupTask> claims, String jobId) {
        return claims.stream().anyMatch(value -> jobId.equals(value.getJobId()));
    }

    private static String requiredNonBlankEnvironment(String name) {
        String value = requiredEnvironment(name);
        if (value.trim().isEmpty()) {
            throw new IllegalStateException(name + " must not be blank for the real PostgreSQL contract test");
        }
        return value;
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null) {
            throw new IllegalStateException(name + " must be set for the real PostgreSQL contract test");
        }
        return value;
    }
}
