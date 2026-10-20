package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.application.model.ExportCleanupTask;
import com.sunwoda.evb.finance.analysis.application.model.ExportJob;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GOV-006 清理 Worker 数据库并发领取真实 PostgreSQL 技术契约。
 *
 * <p>本次修改：只新增并发契约测试，验证既有 SKIP LOCKED 领取策略在两个
 * 独立 Worker 下的有效 claim 唯一性、任务分摊、租约/token/attempts 持久化，
 * 以及旧 token 不能确认后续 claim；不修改生产策略、保留期、文件删除、权限或公式。</p>
 *
 * <p>测试要求显式配置 PostgreSQL，数据库不可用时直接失败，避免把未执行的并发
 * 契约误报为通过。</p>
 */
class JdbcExportCleanupRepositoryConcurrencyPostgresContractTest {
    private static final LocalDateTime CLAIM_ORDER_TIME = LocalDateTime.of(2000, 1, 1, 0, 0);
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
        jdbc.execute("SELECT job_id, cleanup_status, cleanup_claim_token, "
                + "cleanup_lease_until, cleanup_attempts "
                + "FROM flow.export_task LIMIT 0");
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
    void twoIndependentWorkersClaimSameBatchWithoutDuplicateAndPersistLeases() throws Exception {
        List<ExportJob> jobs = createExpiredSucceededTasks(4);
        LocalDateTime now = LocalDateTime.now().withNano(0);
        LocalDateTime leaseUntil = now.plusMinutes(10);
        DataSource dataSourceA = newWorkerDataSource();
        DataSource dataSourceB = newWorkerDataSource();
        JdbcExportCleanupRepository workerA = new JdbcExportCleanupRepository(dataSourceA);
        JdbcExportCleanupRepository workerB = new JdbcExportCleanupRepository(dataSourceB);
        TransactionTemplate transactionA = newWorkerTransaction(dataSourceA);
        TransactionTemplate transactionB = newWorkerTransaction(dataSourceB);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch claimed = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        try {
            Future<List<ExportCleanupTask>> claimsA = executor.submit(
                    claimWhenReleased(transactionA, workerA,
                            "GOV006-CONCURRENCY-A-" + runToken,
                            now, leaseUntil, ready, start, claimed, release));
            Future<List<ExportCleanupTask>> claimsB = executor.submit(
                    claimWhenReleased(transactionB, workerB,
                            "GOV006-CONCURRENCY-B-" + runToken,
                            now, leaseUntil, ready, start, claimed, release));

            assertTrue(ready.await(10, TimeUnit.SECONDS),
                    "两个 Worker 必须先完成并发闸门准备");
            start.countDown();
            assertTrue(claimed.await(10, TimeUnit.SECONDS),
                    "两个独立事务必须都先完成 claim，再释放行锁");
            // 本次修改：两个事务在 claim 后保持行锁，只有都完成领取才释放，
            // 强制验证 PostgreSQL 的 SKIP LOCKED 真实跳过，而非顺序执行假通过。
            release.countDown();

            List<ExportCleanupTask> claimedByA = getWithin(claimsA);
            List<ExportCleanupTask> claimedByB = getWithin(claimsB);
            List<ExportCleanupTask> allClaims = new ArrayList<ExportCleanupTask>();
            allClaims.addAll(claimedByA);
            allClaims.addAll(claimedByB);

            // 本次修改：两个 Worker 各领取一半任务，证明同一批任务可以安全分摊。
            assertEquals(2, claimedByA.size());
            assertEquals(2, claimedByB.size());
            assertEquals(jobs.size(), allClaims.size());
            assertEquals(jobs.size(), distinctJobIds(allClaims).size(),
                    "同一任务在并发领取结果中最多只能出现一个有效 claim");
            assertEquals(jobs.size(), distinctTokens(allClaims).size(),
                    "不同有效 claim 必须持久化不同 token");

            for (ExportCleanupTask claim : allClaims) {
                assertEquals("IN_PROGRESS", cleanupStatus(claim.getJobId()));
                assertEquals(1, cleanupAttempts(claim.getJobId()),
                        "并发首轮领取只能把 attempts 持久化为 1");
                assertEquals(leaseUntil, cleanupLeaseUntil(claim.getJobId()));
                assertEquals(claim.getClaimToken(), cleanupClaimToken(claim.getJobId()));
                assertNotNull(claim.getClaimToken());
                assertFalse(claim.getClaimToken().trim().isEmpty());
            }

            // 本批任务的 attempts=1 且 job/token 均唯一，已证明当前租约下没有第二个有效 claim；
            // 不再发起全局第三次领取，避免改变数据库中其他历史清理任务的状态。
            assertEquals(jobs.size(), countInProgressTasks());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS),
                    "并发 Worker 线程必须在契约结束前退出");
        }
    }

    @Test
    void staleTokenCannotAcknowledgeTheNextClaim() {
        ExportJob job = createExpiredSucceededTasks(1).get(0);
        LocalDateTime firstNow = LocalDateTime.now().withNano(0);
        LocalDateTime firstLease = firstNow.plusMinutes(1);
        JdbcExportCleanupRepository workerA = newWorkerRepository();
        JdbcExportCleanupRepository workerB = newWorkerRepository();

        ExportCleanupTask firstClaim = onlyClaim(workerA.claimExpired(
                "GOV006-TOKEN-A-" + runToken, 1, firstNow, firstLease), job.getJobId());
        String staleToken = firstClaim.getClaimToken();

        LocalDateTime reclaimNow = firstLease.plusSeconds(1);
        LocalDateTime secondLease = reclaimNow.plusMinutes(5);
        ExportCleanupTask secondClaim = onlyClaim(workerB.claimExpired(
                "GOV006-TOKEN-B-" + runToken, 1, reclaimNow, secondLease), job.getJobId());

        assertFalse(staleToken.equals(secondClaim.getClaimToken()),
                "后续 claim 必须使用新 token");
        assertEquals(2, secondClaim.getAttempts());
        assertEquals("IN_PROGRESS", cleanupStatus(job.getJobId()));
        assertEquals(secondClaim.getClaimToken(), cleanupClaimToken(job.getJobId()));
        assertEquals(secondLease, cleanupLeaseUntil(job.getJobId()));

        // 本次修改：旧 token 只能被拒绝，不能确认后续 Worker 的 claim。
        workerA.markCleanupSucceeded(job.getJobId(), staleToken, reclaimNow);
        assertEquals("IN_PROGRESS", cleanupStatus(job.getJobId()));
        assertEquals(2, cleanupAttempts(job.getJobId()));
        assertEquals(secondClaim.getClaimToken(), cleanupClaimToken(job.getJobId()));

        workerB.markCleanupSucceeded(job.getJobId(), secondClaim.getClaimToken(), reclaimNow);
        assertEquals("DELETED", cleanupStatus(job.getJobId()));
        assertEquals(2, cleanupAttempts(job.getJobId()));
    }

    private Callable<List<ExportCleanupTask>> claimWhenReleased(
            final TransactionTemplate transaction,
            final JdbcExportCleanupRepository worker,
            final String workerId,
            final LocalDateTime now,
            final LocalDateTime leaseUntil,
            final CountDownLatch ready,
            final CountDownLatch start,
            final CountDownLatch claimed,
            final CountDownLatch release) {
        return new Callable<List<ExportCleanupTask>>() {
            @Override
            public List<ExportCleanupTask> call() throws Exception {
                return transaction.execute(status -> {
                    ready.countDown();
                    try {
                        if (!start.await(10, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("并发领取闸门未及时释放");
                        }
                        List<ExportCleanupTask> claims = worker.claimExpired(
                                workerId, 2, now, leaseUntil);
                        claimed.countDown();
                        if (!release.await(10, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("并发领取事务未及时释放行锁");
                        }
                        return claims;
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        status.setRollbackOnly();
                        throw new IllegalStateException("并发领取线程被中断", interrupted);
                    }
                });
            }
        };
    }

    private List<ExportCleanupTask> getWithin(Future<List<ExportCleanupTask>> future)
            throws InterruptedException, ExecutionException, TimeoutException {
        return future.get(20, TimeUnit.SECONDS);
    }

    private List<ExportJob> createExpiredSucceededTasks(int count) {
        runToken = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime now = LocalDateTime.now().withNano(0);
        List<ExportJob> jobs = new ArrayList<ExportJob>();
        for (int index = 1; index <= count; index++) {
            String suffix = "TASK-" + index;
            String userId = "GOV006-CONCURRENCY-U-" + runToken + "-" + suffix;
            String requestKey = "GOV006-CONCURRENCY-K-" + runToken + "-" + suffix;
            String fileName = "gov006-concurrency-" + runToken + "-" + index + ".csv";
            ExportJob job = tasks.create(userId, requestKey, fileName, "text/csv");
            tasks.complete(job.getJobId(), fileName, "text/csv", new byte[] {(byte) index});
            jdbc.update("UPDATE flow.export_task SET created_at = ?, expires_at = ?, "
                            + "updated_at = ? WHERE job_id = ?",
                    Timestamp.valueOf(CLAIM_ORDER_TIME.minusMinutes(2)),
                    Timestamp.valueOf(CLAIM_ORDER_TIME.minusMinutes(1)),
                    Timestamp.valueOf(now), job.getJobId());
            tasks.status(job.getJobId(), userId);
            // 本次修改：仅把本测试夹具排到队列最前并清空下一次尝试时间，
            // 避免共享 PostgreSQL 中其他历史清理行干扰本契约，不改变生产 SQL。
            jdbc.update("UPDATE flow.export_task SET cleanup_next_attempt_at = NULL "
                            + "WHERE job_id = ?", job.getJobId());
            createdJobIds.add(job.getJobId());
            jobs.add(job);
        }
        return jobs;
    }

    private JdbcExportCleanupRepository newWorkerRepository() {
        return new JdbcExportCleanupRepository(newWorkerDataSource());
    }

    private DataSource newWorkerDataSource() {
        return new DriverManagerDataSource(dbUrl, dbUsername, dbPassword);
    }

    private TransactionTemplate newWorkerTransaction(DataSource dataSource) {
        return new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    private Set<String> distinctJobIds(List<ExportCleanupTask> claims) {
        Set<String> values = new HashSet<String>();
        for (ExportCleanupTask claim : claims) {
            values.add(claim.getJobId());
        }
        return values;
    }

    private Set<String> distinctTokens(List<ExportCleanupTask> claims) {
        Set<String> values = new HashSet<String>();
        for (ExportCleanupTask claim : claims) {
            values.add(claim.getClaimToken());
        }
        return values;
    }

    private ExportCleanupTask onlyClaim(List<ExportCleanupTask> claims, String jobId) {
        assertEquals(1, claims.size(), "单任务 token 契约必须恰好返回一个 claim");
        for (ExportCleanupTask claim : claims) {
            if (jobId.equals(claim.getJobId())) {
                return claim;
            }
        }
        throw new AssertionError("expected cleanup claim for " + jobId);
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
        return jdbc.queryForObject(
                "SELECT cleanup_claim_token FROM flow.export_task WHERE job_id = ?",
                String.class, jobId);
    }

    private LocalDateTime cleanupLeaseUntil(String jobId) {
        Timestamp value = jdbc.queryForObject(
                "SELECT cleanup_lease_until FROM flow.export_task WHERE job_id = ?",
                Timestamp.class, jobId);
        return value == null ? null : value.toLocalDateTime();
    }

    private int countInProgressTasks() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM flow.export_task "
                        + "WHERE cleanup_status = 'IN_PROGRESS' AND user_id LIKE ?",
                Integer.class, "GOV006-CONCURRENCY-U-" + runToken + "-%");
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
