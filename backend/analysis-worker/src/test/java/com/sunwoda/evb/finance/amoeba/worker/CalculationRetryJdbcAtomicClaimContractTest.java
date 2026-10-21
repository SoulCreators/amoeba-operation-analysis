package com.sunwoda.evb.finance.amoeba.worker;

import com.sunwoda.evb.finance.analysis.domain.model.CalculationRetryState;
import com.sunwoda.evb.finance.analysis.infrastructure.jdbc.JdbcCalculationRetryRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * V029 PostgreSQL 原子领取契约。
 *
 * <p>本次新增测试：两个独立 JDBC 仓储实例同时扫描同一个到期任务，数据库只能返回一个领取结果，
 * 验证 SKIP LOCKED 与唯一 job_id 租约协同生效。</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CalculationRetryJdbcAtomicClaimContractTest {
    private DataSource dataSource;
    private JdbcTemplate jdbc;
    private String jobId;
    private String requestKey;

    @BeforeAll
    void connectToConfiguredPostgres() {
        String url = System.getenv("AMOEBA_DB_URL");
        String username = System.getenv("AMOEBA_DB_USERNAME");
        assumeTrue(url != null && !url.trim().isEmpty(),
                "AMOEBA_DB_URL 未配置，跳过 V029 PostgreSQL 原子领取契约");
        assumeTrue(username != null && !username.trim().isEmpty(),
                "AMOEBA_DB_USERNAME 未配置，跳过 V029 PostgreSQL 原子领取契约");
        dataSource = new DriverManagerDataSource(url, username,
                System.getenv("AMOEBA_DB_PASSWORD"));
        jdbc = new JdbcTemplate(dataSource);
        String claimTable = jdbc.queryForObject(
                "SELECT to_regclass('flow.calculation_retry_claim')", String.class);
        assumeTrue(claimTable != null, "V029 领取表尚未迁移，跳过 PostgreSQL 原子领取契约");
    }

    @Test
    void onlyOneWorkerCanClaimTheSameDueRetry() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        jobId = "V029-TEST-JOB-" + suffix;
        requestKey = "V029-TEST-REQUEST-" + suffix;
        LocalDateTime now = LocalDateTime.now().withNano(0);
        jdbc.update("INSERT INTO flow.calculation_job "
                        + "(job_id, request_key, batch_no, status, attempt_no, last_error, created_at) "
                        + "VALUES (?, ?, ?, 'FAILED', 1, 'V029 concurrency test', ?)",
                jobId, requestKey, "V029-TEST-BATCH-" + suffix, Timestamp.valueOf(now));
        jdbc.update("INSERT INTO flow.calculation_retry "
                        + "(job_id, request_key, status, retry_no, last_failed_attempt_no, "
                        + "next_retry_at, last_error, created_at, updated_at) "
                        + "VALUES (?, ?, 'PENDING_RETRY', 1, 1, ?, 'V029 concurrency test', ?, ?)",
                jobId, requestKey, Timestamp.valueOf(now.minusSeconds(1)),
                Timestamp.valueOf(now), Timestamp.valueOf(now));

        JdbcCalculationRetryRepository first = new JdbcCalculationRetryRepository(dataSource);
        JdbcCalculationRetryRepository second = new JdbcCalculationRetryRepository(dataSource);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<List<CalculationRetryState>> left = pool.submit(() -> findDue(first, now, ready, start));
            Future<List<CalculationRetryState>> right = pool.submit(() -> findDue(second, now, ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            List<CalculationRetryState> leftResult = left.get(10, TimeUnit.SECONDS);
            List<CalculationRetryState> rightResult = right.get(10, TimeUnit.SECONDS);
            assertEquals(1, leftResult.size() + rightResult.size(),
                    "同一到期重试只能由一个 Worker 领取");
        } finally {
            pool.shutdownNow();
            jdbc.update("DELETE FROM flow.calculation_retry_claim WHERE job_id = ?", jobId);
            jdbc.update("DELETE FROM flow.calculation_retry WHERE job_id = ?", jobId);
            jdbc.update("DELETE FROM flow.calculation_job WHERE job_id = ?", jobId);
        }
    }

    private List<CalculationRetryState> findDue(JdbcCalculationRetryRepository repository,
                                                 LocalDateTime now,
                                                 CountDownLatch ready,
                                                 CountDownLatch start) {
        ready.countDown();
        try {
            if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("并发测试启动超时");
            return new ArrayList<CalculationRetryState>(repository.findDue(now, 1));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("并发测试被中断", interrupted);
        }
    }
}
