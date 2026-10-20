package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.application.model.ExportCleanupTask;
import com.sunwoda.evb.finance.analysis.application.model.ExportJob;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * GOV-006-CLEANUP-CRASH-RECOVERY-001：真实 PostgreSQL 的清理租约崩溃恢复契约。
 *
 * <p>本次新增测试只调用既有 JDBC 仓储和既有数据库迁移，不改变任何生产逻辑。
 * 数据库、迁移、字段或连接不可用时必须直接失败，不能用 assume 跳过。</p>
 */
class JdbcExportCleanupRepositoryCrashRecoveryPostgresContractTest {
    private static final String EXPORT_TASK_TABLE = "flow.export_task";
    private static final String[] REQUIRED_CLEANUP_COLUMNS = {
            "cleanup_status", "cleanup_attempts", "cleanup_claim_token",
            "cleanup_lease_until", "cleanup_next_attempt_at", "cleanup_last_error",
            "cleanup_completed_at"
    };

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;
    private static JdbcExportTaskRepository tasks;
    private static JdbcExportCleanupRepository cleanup;

    private final List<String> createdJobIds = new ArrayList<String>();
    private String runToken;

    @BeforeAll
    static void connectToPostgresAndVerifyExistingMigrations() {
        // 本次契约明确要求真实环境；缺少环境变量时让测试硬失败，避免假绿。
        String url = requiredNonBlankEnvironment("AMOEBA_DB_URL");
        String username = requiredNonBlankEnvironment("AMOEBA_DB_USERNAME");
        String password = requiredEnvironment("AMOEBA_DB_PASSWORD");

        Path repositoryRoot = repositoryRoot();
        requireMigration(repositoryRoot, "V018__create_export_task.sql");
        requireMigration(repositoryRoot, "V019__add_export_cleanup_lifecycle.sql");

        dataSource = new DriverManagerDataSource(url, username, password);
        jdbc = new JdbcTemplate(dataSource);
        verifyPostgreSqlProduct();
        // SELECT 1 和字段查询都不捕获异常；连接或迁移缺失必须显示为测试失败。
        assertEquals(1, jdbc.queryForObject("SELECT 1", Integer.class));
        verifyCleanupSchema();

        tasks = new JdbcExportTaskRepository(dataSource);
        cleanup = new JdbcExportCleanupRepository(dataSource);
    }

    @AfterEach
    void removeRandomFixtures() {
        if (jdbc == null) {
            return;
        }
        for (String jobId : createdJobIds) {
            jdbc.update("DELETE FROM " + EXPORT_TASK_TABLE + " WHERE job_id = ?", jobId);
        }
        createdJobIds.clear();
        runToken = null;
    }

    @Test
    void expiredInProgressLeaseCanBeReclaimedAndStaleTokenCannotMutateCurrentClaim() {
        // 本次修改覆盖一次 worker 崩溃：旧租约过期后由新 worker 接管同一个随机任务。
        runToken = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime firstNow = LocalDateTime.now().withNano(0);
        ExportJob job = createExpiredSuccessfulTask(firstNow);

        cleanup.expireDueTasks(firstNow);
        ExportCleanupTask oldClaim = claimFor(job.getJobId(), "old-worker", firstNow,
                firstNow.plusMinutes(1));
        assertEquals("IN_PROGRESS", cleanupStatus(job.getJobId()));
        assertEquals(1, cleanupAttempts(job.getJobId()));
        assertEquals(oldClaim.getClaimToken(), cleanupClaimToken(job.getJobId()));
        assertEquals(firstNow.plusMinutes(1), cleanupLeaseUntil(job.getJobId()));
        LocalDateTime reclaimNow = firstNow.plusMinutes(2);
        ExportCleanupTask newClaim = claimFor(job.getJobId(), "new-worker", reclaimNow,
                reclaimNow.plusMinutes(5));

        assertNotEquals(oldClaim.getClaimToken(), newClaim.getClaimToken(),
                "租约过期后新 worker 必须获得新 token");
        assertEquals(2, cleanupAttempts(job.getJobId()));
        assertEquals("IN_PROGRESS", cleanupStatus(job.getJobId()));
        assertEquals(newClaim.getClaimToken(), cleanupClaimToken(job.getJobId()));
        assertEquals(reclaimNow.plusMinutes(5), cleanupLeaseUntil(job.getJobId()));

        // 旧 token 的 complete/delete 只能命中旧 claim，不能把新 claim 提前标记为 DELETED。
        cleanup.markCleanupSucceeded(job.getJobId(), oldClaim.getClaimToken(), reclaimNow);
        assertClaimStillOwnedByNewWorker(job.getJobId(), newClaim.getClaimToken());

        // 旧 token 的 update/retry 同样不能清空或改写新 worker 的租约状态。
        cleanup.markCleanupRetry(job.getJobId(), oldClaim.getClaimToken(), "stale worker",
                reclaimNow.plusMinutes(5), reclaimNow, true);
        assertClaimStillOwnedByNewWorker(job.getJobId(), newClaim.getClaimToken());

        // 只有当前 token 可以完成清理；完成后既有生产逻辑将状态置为 DELETED。
        cleanup.markCleanupSucceeded(job.getJobId(), newClaim.getClaimToken(), reclaimNow);
        assertEquals("DELETED", cleanupStatus(job.getJobId()));
        assertEquals(2, cleanupAttempts(job.getJobId()));
        assertNull(cleanupClaimToken(job.getJobId()));
        assertNull(cleanupLeaseUntil(job.getJobId()));
        assertNotNull(cleanupCompletedAt(job.getJobId()));
        assertEquals(1, taskRowCount(job.getJobId()), "清理只更新任务状态，不删除任务元数据行");
    }

    private ExportJob createExpiredSuccessfulTask(LocalDateTime now) {
        String userId = "GOV006-CRASH-USER-" + runToken;
        String requestKey = "GOV006-CRASH-REQUEST-" + runToken;
        String fileName = "gov006-crash-" + runToken + ".csv";
        ExportJob job = tasks.create(userId, requestKey, fileName, "text/csv");
        createdJobIds.add(job.getJobId());
        tasks.complete(job.getJobId(), fileName, "text/csv", new byte[] {1});

        // 仅用测试 SQL 制造已过期前置条件；生命周期推进仍全部交给生产仓储。
        jdbc.update("UPDATE " + EXPORT_TASK_TABLE
                        + " SET created_at = ?, expires_at = ?, updated_at = ? WHERE job_id = ?",
                Timestamp.valueOf(now.minusMinutes(2)),
                Timestamp.valueOf(now.minusMinutes(1)), Timestamp.valueOf(now), job.getJobId());
        return job;
    }

    private ExportCleanupTask claimFor(String jobId, String workerSuffix,
                                       LocalDateTime now, LocalDateTime leaseUntil) {
        List<ExportCleanupTask> claims = cleanup.claimExpired(
                "GOV006-CRASH-WORKER-" + runToken + "-" + workerSuffix,
                1000, now, leaseUntil);
        return claims.stream().filter(value -> jobId.equals(value.getJobId())).findFirst()
                .orElseThrow(() -> new AssertionError("expected cleanup claim for " + jobId));
    }

    private void assertClaimStillOwnedByNewWorker(String jobId, String newToken) {
        assertEquals("IN_PROGRESS", cleanupStatus(jobId));
        assertEquals(2, cleanupAttempts(jobId));
        assertEquals(newToken, cleanupClaimToken(jobId));
        assertNotNull(cleanupLeaseUntil(jobId));
        assertNull(cleanupNextAttemptAt(jobId));
        assertNull(cleanupLastError(jobId));
        assertNull(cleanupCompletedAt(jobId));
    }

    private String cleanupStatus(String jobId) {
        return jdbc.queryForObject("SELECT cleanup_status FROM " + EXPORT_TASK_TABLE
                + " WHERE job_id = ?", String.class, jobId);
    }

    private int cleanupAttempts(String jobId) {
        return jdbc.queryForObject("SELECT cleanup_attempts FROM " + EXPORT_TASK_TABLE
                + " WHERE job_id = ?", Integer.class, jobId);
    }

    private String cleanupClaimToken(String jobId) {
        return jdbc.queryForObject("SELECT cleanup_claim_token FROM " + EXPORT_TASK_TABLE
                + " WHERE job_id = ?", String.class, jobId);
    }

    private LocalDateTime cleanupLeaseUntil(String jobId) {
        Timestamp value = jdbc.queryForObject("SELECT cleanup_lease_until FROM "
                + EXPORT_TASK_TABLE + " WHERE job_id = ?", Timestamp.class, jobId);
        return value == null ? null : value.toLocalDateTime();
    }

    private Timestamp cleanupCompletedAt(String jobId) {
        return jdbc.queryForObject("SELECT cleanup_completed_at FROM " + EXPORT_TASK_TABLE
                + " WHERE job_id = ?", Timestamp.class, jobId);
    }

    private Timestamp cleanupNextAttemptAt(String jobId) {
        return jdbc.queryForObject("SELECT cleanup_next_attempt_at FROM " + EXPORT_TASK_TABLE
                + " WHERE job_id = ?", Timestamp.class, jobId);
    }

    private String cleanupLastError(String jobId) {
        return jdbc.queryForObject("SELECT cleanup_last_error FROM " + EXPORT_TASK_TABLE
                + " WHERE job_id = ?", String.class, jobId);
    }

    private int taskRowCount(String jobId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + EXPORT_TASK_TABLE
                + " WHERE job_id = ?", Integer.class, jobId);
    }

    private static void verifyPostgreSqlProduct() {
        try (Connection connection = dataSource.getConnection()) {
            assertEquals("PostgreSQL", connection.getMetaData().getDatabaseProductName(),
                    "本契约必须连接真实 PostgreSQL");
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("无法读取数据库产品类型", e);
        }
    }

    private static void verifyCleanupSchema() {
        jdbc.execute("SELECT job_id, user_id, status, expires_at, cleanup_status, "
                + "cleanup_attempts, cleanup_claim_token, cleanup_lease_until, "
                + "cleanup_next_attempt_at, cleanup_last_error, cleanup_completed_at "
                + "FROM " + EXPORT_TASK_TABLE + " LIMIT 0");
        Integer columnCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = 'flow' AND table_name = 'export_task' "
                        + "AND column_name IN (?, ?, ?, ?, ?, ?, ?)",
                Integer.class, (Object[]) REQUIRED_CLEANUP_COLUMNS);
        if (columnCount == null || columnCount != REQUIRED_CLEANUP_COLUMNS.length) {
            throw new IllegalStateException("V019 cleanup columns are incomplete: " + columnCount);
        }
    }

    private static Path repositoryRoot() {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            if (Files.isDirectory(current.resolve("database").resolve("migration"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("database/migration directory is missing");
    }

    private static void requireMigration(Path repositoryRoot, String migrationFile) {
        if (!Files.isRegularFile(repositoryRoot.resolve("database/migration").resolve(migrationFile))) {
            throw new IllegalStateException("missing database/migration/" + migrationFile);
        }
    }

    private static String requiredNonBlankEnvironment(String name) {
        String value = requiredEnvironment(name);
        if (value.trim().isEmpty()) {
            throw new IllegalStateException(name
                    + " must not be blank for the real PostgreSQL contract test");
        }
        return value;
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null) {
            throw new IllegalStateException(name
                    + " must be set for the real PostgreSQL contract test");
        }
        return value;
    }
}
