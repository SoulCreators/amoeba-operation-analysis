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
import org.springframework.transaction.TransactionStatus;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GOV-006-CLEANUP-EXPIRY-RACE-001 的真实 PostgreSQL 并发契约。
 *
 * <p>本次修改：重构为两个真实 PostgreSQL 锁竞争场景。第一个场景由事务 A 用
 * SELECT FOR UPDATE 明确锁住已提交的 EXPIRED/PENDING 行，直接证明 claimExpired
 * 使用 SKIP LOCKED 跳过锁定行，并在新事务提交领取后用独立连接验证持久化。第二个
 * 场景由 claim-first 事务先持有活动租约，再由 expireDueTasks 真实等待行锁，证明
 * 活动 token、lease 和 attempts 不会被过期扫描重置。
 * 测试不修改生产逻辑，不覆盖文件删除 exactly-once、权限或业务口径。</p>
 *
 * <p>测试明确使用两个独立 DataSource、两个独立事务和 CountDownLatch；数据库
 * 不可用时直接失败，避免把未执行的并发契约误报为通过。</p>
 */
class JdbcExportCleanupRepositoryExpiryRacePostgresContractTest {
    private static final int LATCH_TIMEOUT_SECONDS = 10;

    private static String dbUrl;
    private static String dbUsername;
    private static String dbPassword;
    private static JdbcTemplate jdbc;
    private static JdbcExportTaskRepository tasks;

    private final List<String> createdJobIds = new ArrayList<String>();
    private String runToken;

    @BeforeAll
    static void connectToConfiguredPostgresAndVerifyMigration() {
        dbUrl = requiredNonBlankEnvironment("AMOEBA_DB_URL");
        dbUsername = requiredNonBlankEnvironment("AMOEBA_DB_USERNAME");
        dbPassword = requiredEnvironment("AMOEBA_DB_PASSWORD");

        DataSource dataSource = new DriverManagerDataSource(dbUrl, dbUsername, dbPassword);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("SELECT job_id, status, cleanup_status, cleanup_claim_token, "
                + "cleanup_lease_until, cleanup_attempts FROM flow.export_task LIMIT 0");
        tasks = new JdbcExportTaskRepository(dataSource);
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
    void claimSkipsExplicitlyLockedExpiredRowAndPersistsCommittedRetry()
            throws Exception {
        ExportJob job = createExpiredPendingTask();
        LocalDateTime now = LocalDateTime.now().withNano(0);
        LocalDateTime leaseUntil = now.plusMinutes(5);
        DataSource dataSourceA = newWorkerDataSource();
        DataSource dataSourceB = newWorkerDataSource();
        JdbcTemplate jdbcA = new JdbcTemplate(dataSourceA);
        JdbcExportCleanupRepository workerB = new JdbcExportCleanupRepository(dataSourceB);
        TransactionTemplate transactionA = newWorkerTransaction(dataSourceA);
        TransactionTemplate transactionB = newWorkerTransaction(dataSourceB);

        CountDownLatch rowLocked = new CountDownLatch(1);
        CountDownLatch claimFinished = new CountDownLatch(1);
        CountDownLatch releaseRow = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Void> lockFuture = executor.submit(new Callable<Void>() {
                @Override
                public Void call() {
                    transactionA.executeWithoutResult(status -> {
                        // 本次修改：对已提交的 EXPIRED/PENDING 目标行显式加锁，排除
                        // MVCC 看到旧版本的解释，直接构造 FOR UPDATE SKIP LOCKED 证据。
                        jdbcA.queryForObject(
                                "SELECT id FROM flow.export_task WHERE job_id = ? FOR UPDATE",
                                Long.class, job.getJobId());
                        rowLocked.countDown();
                        awaitLatchOrRollback(releaseRow, status,
                                "锁定目标行的事务未及时收到提交闸门");
                    });
                    return null;
                }
            });

            Future<List<ExportCleanupTask>> firstClaimFuture = executor.submit(
                    new Callable<List<ExportCleanupTask>>() {
                        @Override
                        public List<ExportCleanupTask> call() throws Exception {
                            awaitLatch(rowLocked, "事务 A 未完成目标行锁建立");
                            return transactionB.execute(status -> {
                                try {
                                    // 本次修改：事务 A 尚未提交时，claimExpired 必须因
                                    // FOR UPDATE SKIP LOCKED 直接返回，不得等待或改写目标行。
                                    List<ExportCleanupTask> claims = workerB.claimExpired(
                                            workerId("RACE-FIRST"), 1000, now, leaseUntil);
                                    status.setRollbackOnly();
                                    return claims;
                                } finally {
                                    // 本次修改：在 claim 返回后通知主线程，证明 B 在
                                    // A 提交前完成了跳过，而不是顺序执行。
                                    claimFinished.countDown();
                                }
                            });
                        }
                    });

            assertTrue(rowLocked.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    "事务 A 必须先锁住已提交的 EXPIRED/PENDING 目标行");
            assertTrue(claimFinished.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    "事务 B 使用 SKIP LOCKED 时必须在事务 A 提交前返回");

            List<ExportCleanupTask> firstClaims = getWithin(firstClaimFuture);
            assertFalse(hasClaim(firstClaims, job.getJobId()),
                    "被 FOR UPDATE 锁住的 EXPIRED/PENDING 任务必须被 claimExpired 跳过");

            // 本次修改：提交事务 A 后，在全新事务中领取并提交，随后用独立连接验证持久化。
            releaseRow.countDown();
            getVoidWithin(lockFuture);
            List<ExportCleanupTask> retryClaims = transactionB.execute(status ->
                    workerB.claimExpired(workerId("RACE-RETRY"), 1000, now, leaseUntil));
            ExportCleanupTask retryClaim = findClaim(retryClaims, job.getJobId());
            assertNotNull(retryClaim, "事务 A 提交后，事务 B 重试必须可以领取该任务");
            assertEquals(1, retryClaim.getAttempts());
            assertNotNull(retryClaim.getClaimToken());
            assertFalse(retryClaim.getClaimToken().trim().isEmpty());

            JdbcTemplate independentJdbc = new JdbcTemplate(newWorkerDataSource());
            assertEquals("IN_PROGRESS", cleanupStatus(independentJdbc, job.getJobId()),
                    "已提交领取必须被独立连接看到");
            assertEquals(1, cleanupAttempts(independentJdbc, job.getJobId()),
                    "已提交领取必须持久化 attempts=1");
            assertEquals(retryClaim.getClaimToken(),
                    cleanupClaimToken(independentJdbc, job.getJobId()),
                    "已提交领取必须持久化 claim token");
            assertEquals(leaseUntil, cleanupLeaseUntil(independentJdbc, job.getJobId()),
                    "已提交领取必须持久化 lease");
        } finally {
            // 本次修改：无论断言是否失败，都释放行锁闸门并等待线程退出，确保
            // 失败场景不会把未提交事务或后台线程带入后续测试。
            releaseRow.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    "并发测试线程必须在测试结束前退出");
        }
    }

    @Test
    void claimFirstRaceDoesNotResetActiveLeaseAfterExpireScanWaitsForCommit()
            throws Exception {
        // 本次修改：claim-first 场景必须从 SUCCEEDED/已过期夹具开始，确保事务 B
        // 的 expireDueTasks 会产生未提交的行更新，事务 A 才能真实等待该行锁。
        ExportJob job = createExpiredSucceededTask();
        LocalDateTime now = LocalDateTime.now().withNano(0);
        LocalDateTime leaseUntil = now.plusMinutes(5);
        DataSource dataSourceA = newWorkerDataSource();
        DataSource dataSourceB = newWorkerDataSource();
        JdbcExportCleanupRepository workerA = new JdbcExportCleanupRepository(dataSourceA);
        JdbcExportCleanupRepository workerB = new JdbcExportCleanupRepository(dataSourceB);
        JdbcTemplate jdbcA = new JdbcTemplate(dataSourceA);
        TransactionTemplate transactionA = newWorkerTransaction(dataSourceA);
        TransactionTemplate transactionB = newWorkerTransaction(dataSourceB);
        CountDownLatch claimHeld = new CountDownLatch(1);
        CountDownLatch expireStarted = new CountDownLatch(1);
        CountDownLatch releaseClaim = new CountDownLatch(1);
        AtomicReference<ExportCleanupTask> claimReference =
                new AtomicReference<ExportCleanupTask>();
        AtomicInteger expireBackendPid = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Void> claimFuture = executor.submit(new Callable<Void>() {
                @Override
                public Void call() {
                    transactionB.executeWithoutResult(status -> {
                        // 本次修改：在同一事务内先推进 SUCCEEDED -> EXPIRED/PENDING，
                        // 再领取目标行并持锁，构造过期扫描与领取的真实 claim-first 竞态。
                        workerB.expireDueTasks(now);
                        ExportCleanupTask claim = findClaim(workerB.claimExpired(
                                workerId("RACE-CLAIM-FIRST"), 1000, now, leaseUntil),
                                job.getJobId());
                        assertNotNull(claim, "claim-first 事务必须成功领取测试夹具");
                        claimReference.set(claim);
                        // 本次修改：领取事务在提交前持有目标行锁，等待过期扫描事务
                        // 真正进入锁等待后再放行，形成可观察的 claim-first 竞态。
                        claimHeld.countDown();
                        awaitLatchOrRollback(releaseClaim, status,
                                "claim-first 事务未及时收到提交闸门");
                    });
                    return null;
                }
            });

            assertTrue(claimHeld.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    "事务 B 必须先领取并持有活动租约行锁");

            Future<Void> expireFuture = executor.submit(new Callable<Void>() {
                @Override
                public Void call() {
                    transactionA.executeWithoutResult(status -> {
                        // 本次修改：记录事务 A 的 PostgreSQL backend pid，后续由
                        // pg_stat_activity 直接证明 expireDueTasks 正在等待事务 B 的行锁。
                        expireBackendPid.set(jdbcA.queryForObject(
                                "SELECT pg_backend_pid()", Integer.class));
                        expireStarted.countDown();
                        workerA.expireDueTasks(now.plusSeconds(1));
                    });
                    return null;
                }
            });

            assertTrue(expireStarted.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    "事务 A 必须进入 expireDueTasks 事务");
            assertTrue(waitForPostgresLockWait(expireBackendPid.get()),
                    "expireDueTasks 必须在事务 B 持有目标行锁期间等待，而不是顺序执行");
            assertFalse(expireFuture.isDone(),
                    "事务 B 未提交前，expireDueTasks 不应完成更新");

            // 本次修改：提交 claim-first 事务，验证被阻塞的 expireDueTasks 随后才能完成。
            releaseClaim.countDown();
            getVoidWithin(claimFuture);
            getVoidWithin(expireFuture);

            ExportCleanupTask claim = claimReference.get();
            assertNotNull(claim, "必须保留 claim-first 事务提交的领取结果");
            JdbcTemplate independentJdbc = new JdbcTemplate(newWorkerDataSource());
            assertEquals("IN_PROGRESS", cleanupStatus(independentJdbc, job.getJobId()),
                    "过期扫描完成后不能把活动租约重置为其他状态");
            assertEquals(1, cleanupAttempts(independentJdbc, job.getJobId()),
                    "过期扫描完成后 attempts 不能被重置");
            assertEquals(claim.getClaimToken(),
                    cleanupClaimToken(independentJdbc, job.getJobId()),
                    "过期扫描完成后活动 claim token 必须保留");
            assertEquals(leaseUntil, cleanupLeaseUntil(independentJdbc, job.getJobId()),
                    "过期扫描完成后活动 lease 必须保留");
        } finally {
            // 本次修改：失败时同时释放 claim-first 闸门、终止等待线程并等待退出，
            // 使 AfterEach 可以安全删除夹具。
            releaseClaim.countDown();
            executor.shutdown();
            boolean terminated = executor.awaitTermination(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!terminated) {
                // 本次修改：只有正常释放闸门后仍未退出，才中断线程，避免过早
                // shutdownNow 掩盖主断言或留下未完成的数据库事务。
                executor.shutdownNow();
                terminated = executor.awaitTermination(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
            assertTrue(terminated,
                    "claim-first 并发测试线程必须在测试结束前退出");
        }
    }

    private ExportJob createExpiredSucceededTask() {
        runToken = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime now = LocalDateTime.now().withNano(0);
        String userId = "GOV006-EXPIRY-RACE-U-" + runToken;
        String requestKey = "GOV006-EXPIRY-RACE-K-" + runToken;
        String fileName = "gov006-expiry-race-" + runToken + ".csv";
        ExportJob job = tasks.create(userId, requestKey, fileName, "text/csv");
        tasks.complete(job.getJobId(), fileName, "text/csv", new byte[] {1});
        jdbc.update("UPDATE flow.export_task SET created_at = ?, expires_at = ?, updated_at = ?, "
                        + "cleanup_next_attempt_at = NULL WHERE job_id = ?",
                Timestamp.valueOf(now.minusMinutes(2)),
                Timestamp.valueOf(now.minusMinutes(1)),
                Timestamp.valueOf(now), job.getJobId());
        createdJobIds.add(job.getJobId());
        return job;
    }

    private ExportJob createExpiredPendingTask() {
        runToken = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime now = LocalDateTime.now().withNano(0);
        String userId = "GOV006-EXPIRY-RACE-U-" + runToken;
        String requestKey = "GOV006-EXPIRY-RACE-K-" + runToken;
        String fileName = "gov006-expiry-race-" + runToken + ".csv";
        ExportJob job = tasks.create(userId, requestKey, fileName, "text/csv");
        tasks.complete(job.getJobId(), fileName, "text/csv", new byte[] {1});
        jdbc.update("UPDATE flow.export_task SET created_at = ?, expires_at = ?, updated_at = ?, "
                        + "status = 'EXPIRED', cleanup_status = 'PENDING', cleanup_attempts = 0, "
                        + "cleanup_claim_token = NULL, cleanup_lease_until = NULL, "
                        + "cleanup_next_attempt_at = ?, cleanup_last_error = NULL, "
                        + "cleanup_completed_at = NULL WHERE job_id = ?",
                Timestamp.valueOf(now.minusMinutes(2)),
                Timestamp.valueOf(now.minusMinutes(1)),
                Timestamp.valueOf(now), Timestamp.valueOf(now.minusSeconds(1)), job.getJobId());
        createdJobIds.add(job.getJobId());
        return job;
    }

    private DataSource newWorkerDataSource() {
        return new DriverManagerDataSource(dbUrl, dbUsername, dbPassword);
    }

    private TransactionTemplate newWorkerTransaction(DataSource dataSource) {
        return new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    private String workerId(String suffix) {
        return "GOV006-EXPIRY-RACE-W-" + suffix + "-" + runToken;
    }

    private String cleanupStatus(String jobId) {
        return cleanupStatus(jdbc, jobId);
    }

    private String cleanupStatus(JdbcTemplate queryJdbc, String jobId) {
        return queryJdbc.queryForObject("SELECT cleanup_status FROM flow.export_task WHERE job_id = ?",
                String.class, jobId);
    }

    private int cleanupAttempts(String jobId) {
        return cleanupAttempts(jdbc, jobId);
    }

    private int cleanupAttempts(JdbcTemplate queryJdbc, String jobId) {
        return queryJdbc.queryForObject("SELECT cleanup_attempts FROM flow.export_task WHERE job_id = ?",
                Integer.class, jobId);
    }

    private String cleanupClaimToken(String jobId) {
        return cleanupClaimToken(jdbc, jobId);
    }

    private String cleanupClaimToken(JdbcTemplate queryJdbc, String jobId) {
        return queryJdbc.queryForObject(
                "SELECT cleanup_claim_token FROM flow.export_task WHERE job_id = ?",
                String.class, jobId);
    }

    private LocalDateTime cleanupLeaseUntil(String jobId) {
        return cleanupLeaseUntil(jdbc, jobId);
    }

    private LocalDateTime cleanupLeaseUntil(JdbcTemplate queryJdbc, String jobId) {
        Timestamp value = queryJdbc.queryForObject(
                "SELECT cleanup_lease_until FROM flow.export_task WHERE job_id = ?",
                Timestamp.class, jobId);
        return value == null ? null : value.toLocalDateTime();
    }

    private ExportCleanupTask findClaim(List<ExportCleanupTask> claims, String jobId) {
        for (ExportCleanupTask claim : claims) {
            if (jobId.equals(claim.getJobId())) {
                return claim;
            }
        }
        return null;
    }

    private boolean hasClaim(List<ExportCleanupTask> claims, String jobId) {
        return findClaim(claims, jobId) != null;
    }

    private List<ExportCleanupTask> getWithin(Future<List<ExportCleanupTask>> future)
            throws InterruptedException, ExecutionException, TimeoutException {
        return future.get(20, TimeUnit.SECONDS);
    }

    private void getVoidWithin(Future<Void> future)
            throws InterruptedException, ExecutionException, TimeoutException {
        future.get(20, TimeUnit.SECONDS);
    }

    private boolean waitForPostgresLockWait(int backendPid) {
        long deadline = System.nanoTime()
                + TimeUnit.SECONDS.toNanos(LATCH_TIMEOUT_SECONDS);
        while (System.nanoTime() < deadline) {
            Boolean waiting = jdbc.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM pg_stat_activity "
                            + "WHERE pid = ? AND state = 'active' "
                            + "AND wait_event_type = 'Lock')",
                    Boolean.class, backendPid);
            if (Boolean.TRUE.equals(waiting)) {
                return true;
            }
            // 本次修改：使用 PostgreSQL 活动视图轮询锁等待状态，不使用固定 sleep，
            // 只有确认数据库已进入锁等待后才释放 claim-first 事务。
            Thread.yield();
        }
        return false;
    }

    private void awaitLatchOrRollback(CountDownLatch latch, TransactionStatus status,
                                      String message) {
        try {
            if (!latch.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                status.setRollbackOnly();
                throw new IllegalStateException(message);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            status.setRollbackOnly();
            throw new IllegalStateException(message, interrupted);
        }
    }

    private void awaitLatch(CountDownLatch latch, String message) throws InterruptedException {
        if (!latch.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new IllegalStateException(message);
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
