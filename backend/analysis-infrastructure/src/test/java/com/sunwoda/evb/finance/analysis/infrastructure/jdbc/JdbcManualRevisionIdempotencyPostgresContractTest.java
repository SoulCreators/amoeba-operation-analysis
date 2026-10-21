package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationVersion;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationVersionStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ManualCalculationRevision;
import com.sunwoda.evb.finance.analysis.domain.repository.CalculationVersionRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.ManualCalculationRevisionRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * V030 PostgreSQL 持久幂等合约。
 *
 * <p>本次新增：用两个 Spring 事务代理模拟两个实例，验证唯一键领取、结果重放、载荷冲突、
 * 异常回滚和并发请求只执行一次；未配置 AMOEBA_DB_URL 时明确跳过，不把未运行误报为通过。</p>
 */
class JdbcManualRevisionIdempotencyPostgresContractTest {
    private static AnnotationConfigApplicationContext context;
    private static DataSource dataSource;
    private static JdbcTemplate jdbc;
    private static CalculationVersionRepository versionRepository;
    private static ManualCalculationRevisionRepository revisionRepository;
    private static JdbcManualRevisionIdempotencyAdapter adapterOne;
    private static JdbcManualRevisionIdempotencyAdapter adapterTwo;

    private String suffix;
    private String batchNo;
    private String jobId;
    private String snapshotId;
    private String sourceVersionId;
    private String targetVersionId;
    private CalculationVersion source;

    @BeforeAll
    static void startRealPostgresContext() {
        String url = System.getenv("AMOEBA_DB_URL");
        assumeTrue(url != null && !url.trim().isEmpty(),
                "BLOCKED: AMOEBA_DB_URL is not set; V030 PostgreSQL idempotency contract was not run");
        dataSource = new DriverManagerDataSource(url, environment("AMOEBA_DB_USERNAME"),
                environment("AMOEBA_DB_PASSWORD"));
        jdbc = new JdbcTemplate(dataSource);
        verifySchema();

        context = new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles("db");
        context.register(TestConfig.class);
        context.refresh();
        versionRepository = context.getBean(CalculationVersionRepository.class);
        revisionRepository = context.getBean(ManualCalculationRevisionRepository.class);
        adapterOne = context.getBean("manualRevisionIdempotencyAdapterOne",
                JdbcManualRevisionIdempotencyAdapter.class);
        adapterTwo = context.getBean("manualRevisionIdempotencyAdapterTwo",
                JdbcManualRevisionIdempotencyAdapter.class);
        assertTrue(AopUtils.isAopProxy(adapterOne), "实例一必须通过 Spring 事务代理");
        assertTrue(AopUtils.isAopProxy(adapterTwo), "实例二必须通过 Spring 事务代理");
    }

    @AfterAll
    static void closeContext() {
        if (context != null) {
            context.close();
        }
    }

    @BeforeEach
    void seedFixture() {
        suffix = UUID.randomUUID().toString().replace("-", "");
        batchNo = "TEST-V030-IDEMP-" + suffix;
        jobId = "TEST-V030-IDEMP-JOB-" + suffix;
        snapshotId = "TEST-V030-IDEMP-SNAPSHOT-" + suffix;
        sourceVersionId = "TEST-V030-IDEMP-SOURCE-" + suffix;
        targetVersionId = "TEST-V030-IDEMP-TARGET-" + suffix;
        jdbc.update("INSERT INTO meta.analysis_batch (batch_no, period, status, perspective, created_by) "
                        + "VALUES (?, ?, ?, ?, ?)", batchNo, "2099-12", "DRAFT", "BASE", "contract-test");
        jdbc.update("INSERT INTO flow.calculation_job "
                        + "(job_id, request_key, batch_no, status, attempt_no) VALUES (?, ?, ?, ?, ?)",
                jobId, "TEST-V030-IDEMP-REQUEST-" + suffix, batchNo, "SUCCEEDED", 1);
        jdbc.update("INSERT INTO calc.calculation_snapshot "
                        + "(snapshot_id, snapshot_type, schema_version, content_json, source_version_set, "
                        + "content_hash, source_version_hash, rule_version, created_by) "
                        + "VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?)", snapshotId, "INPUT", "V030",
                "{\"source\":\"idempotency-contract\"}", "[\"V030\"]", "content-" + suffix,
                "source-" + suffix, "rule-v030", "contract-test");
        source = sourceVersion();
        versionRepository.save(source);
    }

    @AfterEach
    void cleanupFixture() {
        if (jdbc == null || suffix == null) {
            return;
        }
        jdbc.update("DELETE FROM flow.manual_revision_idempotency WHERE user_id LIKE ?",
                "TEST-V030-IDEMP-USER-" + suffix + "%");
        jdbc.update("DELETE FROM calc.calculation_manual_revision WHERE revision_id LIKE ?",
                "TEST-V030-IDEMP-REVISION-" + suffix + "%");
        jdbc.update("DELETE FROM calc.calculation_version WHERE calculation_version_id IN (?, ?)",
                targetVersionId, sourceVersionId);
        jdbc.update("DELETE FROM calc.calculation_snapshot WHERE snapshot_id = ?", snapshotId);
        jdbc.update("DELETE FROM flow.calculation_job WHERE job_id = ?", jobId);
        jdbc.update("DELETE FROM meta.analysis_batch WHERE batch_no = ?", batchNo);
    }

    @Test
    void persistsRequestAndReplaysTargetAcrossTwoAdapterInstances() {
        String userId = "TEST-V030-IDEMP-USER-" + suffix;
        String requestKey = "request-" + suffix;
        CalculationVersion target = targetVersion();
        AtomicInteger executions = new AtomicInteger();

        CalculationVersion first = adapterOne.execute(userId, requestKey, sourceVersionId, "人工修订原因",
                () -> {
                    executions.incrementAndGet();
                    revisionRepository.append(source, target, revision(target));
                    return target;
                });
        CalculationVersion replay = adapterTwo.execute(userId, requestKey, sourceVersionId, "人工修订原因",
                () -> {
                    throw new AssertionError("重放不能重新执行人工修订");
                });

        assertSame(target, first);
        assertEquals(targetVersionId, replay.getCalculationVersionId());
        assertEquals(1, executions.get());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM flow.manual_revision_idempotency "
                        + "WHERE user_id = ? AND request_key = ?", Integer.class, userId, requestKey));
        assertEquals(targetVersionId, jdbc.queryForObject(
                "SELECT target_calculation_version_id FROM flow.manual_revision_idempotency "
                        + "WHERE user_id = ? AND request_key = ?", String.class, userId, requestKey));
        assertTrue(jdbc.queryForObject(
                "SELECT request_payload_json @> ?::jsonb FROM flow.manual_revision_idempotency "
                        + "WHERE user_id = ? AND request_key = ?", Boolean.class,
                "{\"sourceVersionId\":\"" + sourceVersionId + "\","
                        + "\"revisionReason\":\"人工修订原因\"}", userId, requestKey));
    }

    @Test
    void differentPayloadConflictsAndFailedOperationRollsBackClaim() {
        String userId = "TEST-V030-IDEMP-USER-" + suffix;
        String requestKey = "request-conflict-" + suffix;
        CalculationVersion target = targetVersion();

        assertThrows(IllegalStateException.class, () -> adapterOne.execute(userId, requestKey,
                sourceVersionId, "第一次尝试", () -> {
                    throw new IllegalStateException("模拟人工修订失败");
                }));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM flow.manual_revision_idempotency "
                        + "WHERE user_id = ? AND request_key = ?", Integer.class, userId, requestKey));

        adapterTwo.execute(userId, requestKey, sourceVersionId, "第二次成功原因", () -> {
            revisionRepository.append(source, target, revision(target));
            return target;
        });
        assertThrows(IllegalStateException.class, () -> adapterOne.execute(userId, requestKey,
                sourceVersionId, "不同载荷", () -> target));
        assertFalse(jdbc.queryForObject(
                "SELECT status = 'IN_PROGRESS' FROM flow.manual_revision_idempotency "
                        + "WHERE user_id = ? AND request_key = ?", Boolean.class, userId, requestKey));
    }

    @Test
    void concurrentInstancesExecuteOperationOnlyOnce() throws Exception {
        String userId = "TEST-V030-IDEMP-USER-" + suffix + "-CONCURRENT";
        String requestKey = "request-concurrent-" + suffix;
        CalculationVersion target = targetVersion();
        AtomicInteger executions = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<CalculationVersion> first = executor.submit(() -> adapterOne.execute(userId, requestKey,
                    sourceVersionId, "并发人工修订", () -> {
                        executions.incrementAndGet();
                        entered.countDown();
                        try {
                            if (!release.await(5, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("并发合约等待超时");
                            }
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("并发合约被中断", interrupted);
                        }
                        revisionRepository.append(source, target, revision(target));
                        return target;
                    }));
            Future<CalculationVersion> second = executor.submit(() -> adapterTwo.execute(userId, requestKey,
                    sourceVersionId, "并发人工修订", () -> {
                        executions.incrementAndGet();
                        throw new AssertionError("第二实例不应进入人工修订操作");
                    }));

            assertTrue(entered.await(5, TimeUnit.SECONDS));
            release.countDown();
            assertEquals(targetVersionId, first.get(10, TimeUnit.SECONDS).getCalculationVersionId());
            assertEquals(targetVersionId, second.get(10, TimeUnit.SECONDS).getCalculationVersionId());
            assertEquals(1, executions.get());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TestConfig {
        @Bean
        DataSource dataSource() {
            return dataSource;
        }

        @Bean
        DataSourceTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        JdbcCalculationVersionRepository calculationVersionRepository(DataSource dataSource) {
            return new JdbcCalculationVersionRepository(dataSource);
        }

        @Bean
        JdbcManualCalculationRevisionRepository manualCalculationRevisionRepository(DataSource dataSource) {
            return new JdbcManualCalculationRevisionRepository(dataSource);
        }

        @Bean(name = "manualRevisionIdempotencyAdapterOne")
        JdbcManualRevisionIdempotencyAdapter manualRevisionIdempotencyAdapterOne(
                DataSource dataSource, CalculationVersionRepository repository) {
            return new JdbcManualRevisionIdempotencyAdapter(dataSource, repository);
        }

        @Bean(name = "manualRevisionIdempotencyAdapterTwo")
        JdbcManualRevisionIdempotencyAdapter manualRevisionIdempotencyAdapterTwo(
                DataSource dataSource, CalculationVersionRepository repository) {
            return new JdbcManualRevisionIdempotencyAdapter(dataSource, repository);
        }
    }

    private CalculationVersion sourceVersion() {
        return new CalculationVersion(sourceVersionId, batchNo, AnalysisPerspective.BASE, "V-SOURCE", jobId,
                snapshotId, "source-" + suffix, "config-" + suffix, "input-" + suffix,
                CalculationVersionStatus.PUBLISHED, true, null, "trace-source-" + suffix, "calculator",
                LocalDateTime.of(2099, 12, 1, 10, 0), null, null, null, null);
    }

    private CalculationVersion targetVersion() {
        return new CalculationVersion(targetVersionId, batchNo, AnalysisPerspective.BASE, "V-TARGET", jobId,
                snapshotId, "source-" + suffix, "config-" + suffix, "input-" + suffix,
                CalculationVersionStatus.CALCULATED, true, sourceVersionId, "trace-target-" + suffix,
                "finance-user", LocalDateTime.of(2099, 12, 1, 11, 0), null, null, null, null);
    }

    private ManualCalculationRevision revision(CalculationVersion target) {
        return new ManualCalculationRevision("TEST-V030-IDEMP-REVISION-" + suffix,
                sourceVersionId, target.getCalculationVersionId(), "finance-user", "人工修订原因",
                target.getCalculatedAt(), target.getTraceId());
    }

    private static void verifySchema() {
        try {
            jdbc.execute("SELECT 1 FROM calc.calculation_version LIMIT 0");
            jdbc.execute("SELECT 1 FROM calc.calculation_manual_revision LIMIT 0");
            jdbc.execute("SELECT 1 FROM flow.manual_revision_idempotency LIMIT 0");
        } catch (RuntimeException failure) {
            assumeTrue(false, "BLOCKED: V026/V027/V030 PostgreSQL schema is not available: "
                    + failure.getMessage());
        }
    }

    private static String environment(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value;
    }
}
