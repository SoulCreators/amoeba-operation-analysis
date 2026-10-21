package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationEventDeliveryStatus;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationLifecycleEvent;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationVersion;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationVersionStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ManualCalculationRevision;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * DATA-004-TECH-003-REVISION-003：验证 V026/V027 版本头和 Outbox 的真实 PostgreSQL 契约。
 *
 * <p>本次新增测试通过生产 JDBC 类和 Spring 事务代理验证历史查询、人工修订版本链以及
 * MANUAL_REVISED 事件落 Outbox；测试夹具使用随机前缀并在每个测试后按外键逆序清理。</p>
 */
class JdbcCalculationVersionAndLifecycleEventRepositoryPostgresContractTest {
    private static AnnotationConfigApplicationContext context;
    private static DataSource dataSource;
    private static JdbcTemplate jdbc;
    private static JdbcCalculationVersionRepository versionRepository;
    private static JdbcCalculationLifecycleEventRepository eventRepository;
    private static JdbcManualCalculationRevisionRepository revisionRepository;

    private String suffix;
    private String batchNo;
    private String jobId;
    private String snapshotId;
    private String sourceVersionId;
    private String targetVersionId;
    private String revisionId;
    private String eventId;

    @BeforeAll
    static void startRealPostgresContext() {
        String url = System.getenv("AMOEBA_DB_URL");
        assumeTrue(url != null && !url.trim().isEmpty(),
                "BLOCKED: AMOEBA_DB_URL is not set; DATA-004 version PostgreSQL contract was not run");
        dataSource = new DriverManagerDataSource(url, environment("AMOEBA_DB_USERNAME"),
                environment("AMOEBA_DB_PASSWORD"));
        jdbc = new JdbcTemplate(dataSource);
        verifySchema();

        context = new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles("db");
        context.register(TestConfig.class);
        context.refresh();
        versionRepository = context.getBean(JdbcCalculationVersionRepository.class);
        eventRepository = context.getBean(JdbcCalculationLifecycleEventRepository.class);
        revisionRepository = context.getBean(JdbcManualCalculationRevisionRepository.class);
        assertTrue(AopUtils.isAopProxy(versionRepository), "版本仓储 save 必须通过 Spring 事务代理");
        assertTrue(AopUtils.isAopProxy(eventRepository), "Outbox 仓储 save 必须通过 Spring 事务代理");
        assertTrue(AopUtils.isAopProxy(revisionRepository), "人工修订仓储必须通过 Spring 事务代理");
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
        batchNo = "TEST-DATA-004-REVISION-003-" + suffix;
        jobId = "TEST-REVISION-003-JOB-" + suffix;
        snapshotId = "TEST-REVISION-003-SNAPSHOT-" + suffix;
        sourceVersionId = "TEST-REVISION-003-SOURCE-" + suffix;
        targetVersionId = "TEST-REVISION-003-TARGET-" + suffix;
        revisionId = "TEST-REVISION-003-RELATION-" + suffix;
        eventId = "TEST-REVISION-003-EVENT-" + suffix;

        jdbc.update("INSERT INTO meta.analysis_batch (batch_no, period, status, perspective, created_by) "
                        + "VALUES (?, ?, ?, ?, ?)", batchNo, "2099-12", "DRAFT", "BASE", "contract-test");
        jdbc.update("INSERT INTO flow.calculation_job "
                        + "(job_id, request_key, batch_no, status, attempt_no) VALUES (?, ?, ?, ?, ?)",
                jobId, "TEST-REVISION-003-REQUEST-" + suffix, batchNo, "SUCCEEDED", 1);
        jdbc.update("INSERT INTO calc.calculation_snapshot "
                        + "(snapshot_id, snapshot_type, schema_version, content_json, source_version_set, "
                        + "content_hash, source_version_hash, rule_version, created_by) "
                        + "VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?)", snapshotId, "INPUT", "REVISION-3",
                "{\"source\":\"contract\"}", "[\"REVISION-003\"]", "content-" + suffix,
                "source-" + suffix, "rule-3", "contract-test");
    }

    @AfterEach
    void cleanupFixture() {
        // 本次新增契约：按 Outbox、修订关系、版本头、快照、作业、批次的外键逆序清理夹具。
        if (jdbc == null || suffix == null) {
            return;
        }
        jdbc.update("DELETE FROM flow.calculation_event_outbox WHERE event_id = ?", eventId);
        jdbc.update("DELETE FROM calc.calculation_manual_revision WHERE revision_id = ?", revisionId);
        jdbc.update("DELETE FROM calc.calculation_version WHERE calculation_version_id IN (?, ?)",
                targetVersionId, sourceVersionId);
        jdbc.update("DELETE FROM calc.calculation_snapshot WHERE snapshot_id = ?", snapshotId);
        jdbc.update("DELETE FROM flow.calculation_job WHERE job_id = ?", jobId);
        jdbc.update("DELETE FROM meta.analysis_batch WHERE batch_no = ?", batchNo);
    }

    @Test
    void versionQueriesAndManualRevisionKeepHistoryAndPersistManualRevisedOutbox() {
        // 本次新增契约：先用生产版本仓储保存来源版本，再通过 V027 修订仓储形成真实版本链。
        CalculationVersion source = sourceVersion();
        versionRepository.save(source);

        Optional<CalculationVersion> loadedSource = versionRepository.findById(sourceVersionId);
        assertTrue(loadedSource.isPresent());
        assertEquals(sourceVersionId, loadedSource.get().getCalculationVersionId());
        assertEquals(source.getCalculatedAt(), loadedSource.get().getCalculatedAt());
        assertTrue(versionRepository.findCurrentByBatchNo(batchNo, AnalysisPerspective.BASE).isPresent());
        assertEquals(1, versionRepository.findByBatchNo(batchNo, AnalysisPerspective.BASE).size());

        CalculationVersion target = targetVersion();
        revisionRepository.append(source, target, revision());

        Optional<CalculationVersion> oldVersion = versionRepository.findById(sourceVersionId);
        Optional<CalculationVersion> currentVersion = versionRepository.findCurrentByBatchNo(
                batchNo, AnalysisPerspective.BASE);
        List<CalculationVersion> history = versionRepository.findByBatchNo(batchNo, AnalysisPerspective.BASE);
        assertTrue(oldVersion.isPresent());
        assertFalse(oldVersion.get().isCurrent(), "旧版本必须保留但不再是当前版本");
        assertEquals(CalculationVersionStatus.SUPERSEDED, oldVersion.get().getStatus());
        assertTrue(currentVersion.isPresent());
        assertEquals(targetVersionId, currentVersion.get().getCalculationVersionId());
        assertEquals(2, history.size(), "人工修订后历史查询必须同时返回旧版本和新版本");

        LocalDateTime occurredAt = LocalDateTime.now().minusMinutes(1);
        CalculationLifecycleEvent event = new CalculationLifecycleEvent(
                eventId, "CALCULATION_VERSION", targetVersionId, "MANUAL_REVISED", targetVersionId,
                "trace-" + suffix,
                "{\"sourceVersionId\":\"" + sourceVersionId + "\","
                        + "\"targetVersionId\":\"" + targetVersionId + "\","
                        + "\"revisedBy\":\"finance-user\"}",
                CalculationEventDeliveryStatus.PENDING, 0, occurredAt, null, occurredAt, null);
        eventRepository.save(event);

        Optional<CalculationLifecycleEvent> persistedEvent = eventRepository.findByEventId(eventId);
        assertTrue(persistedEvent.isPresent());
        assertEquals("MANUAL_REVISED", persistedEvent.get().getEventType());
        assertEquals(targetVersionId, persistedEvent.get().getCalculationVersionId());
        // 本次修正：JSONB 读取会规范化键顺序和空格，使用数据库 JSONB 等价语义而非原始字符串比较。
        assertTrue(jdbc.queryForObject(
                "SELECT payload_json = ?::jsonb FROM flow.calculation_event_outbox WHERE event_id = ?",
                Boolean.class, event.getPayloadJson(), eventId));
        List<CalculationLifecycleEvent> ready = eventRepository.findReadyForDelivery(
                CalculationEventDeliveryStatus.PENDING, occurredAt.plusMinutes(1), 10);
        assertTrue(ready.stream().anyMatch(item -> eventId.equals(item.getEventId())));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM flow.calculation_event_outbox "
                        + "WHERE event_id = ? AND event_type = 'MANUAL_REVISED' "
                        + "AND calculation_version_id = ?", Integer.class, eventId, targetVersionId));
        assertTrue(jdbc.queryForObject(
                "SELECT payload_json @> ?::jsonb FROM flow.calculation_event_outbox WHERE event_id = ?",
                Boolean.class, "{\"revisedBy\":\"finance-user\"}", eventId));
    }

    @Test
    void duplicateEventIdIsIdempotentAndInvalidJsonIsRejectedByPostgres() {
        // 本次新增契约：验证 event_id 重放不会产生第二条记录，JSONB 继续由 V026 约束拒绝非法正文。
        versionRepository.save(sourceVersion());
        LocalDateTime occurredAt = LocalDateTime.now().minusMinutes(1);
        CalculationLifecycleEvent event = new CalculationLifecycleEvent(
                eventId, "CALCULATION_VERSION", sourceVersionId, "CALCULATED", sourceVersionId,
                "trace-" + suffix, "{\"status\":\"CALCULATED\"}",
                CalculationEventDeliveryStatus.PENDING, 0, occurredAt, null, occurredAt, null);
        eventRepository.save(event);
        eventRepository.save(event);
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM flow.calculation_event_outbox WHERE event_id = ?", Integer.class, eventId));

        try {
            jdbc.update("INSERT INTO flow.calculation_event_outbox "
                            + "(event_id, aggregate_type, aggregate_id, event_type, payload_json) "
                            + "VALUES (?, ?, ?, ?, ?::jsonb)", eventId + "-INVALID", "CALCULATION_VERSION",
                    sourceVersionId, "CALCULATED", "not-json");
        } catch (DataAccessException expected) {
            assertNotNull(expected.getMessage());
            return;
        }
        throw new AssertionError("PostgreSQL JSONB 应拒绝非法 Outbox 正文");
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
        JdbcCalculationLifecycleEventRepository calculationLifecycleEventRepository(DataSource dataSource) {
            return new JdbcCalculationLifecycleEventRepository(dataSource);
        }

        @Bean
        JdbcManualCalculationRevisionRepository manualCalculationRevisionRepository(DataSource dataSource) {
            return new JdbcManualCalculationRevisionRepository(dataSource);
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

    private ManualCalculationRevision revision() {
        return new ManualCalculationRevision(revisionId, sourceVersionId, targetVersionId, "finance-user",
                "人工调整毛利", LocalDateTime.of(2099, 12, 1, 11, 0), "trace-target-" + suffix);
    }

    private static void verifySchema() {
        try {
            jdbc.execute("SELECT 1 FROM calc.calculation_version LIMIT 0");
            jdbc.execute("SELECT 1 FROM calc.calculation_manual_revision LIMIT 0");
            jdbc.execute("SELECT 1 FROM flow.calculation_event_outbox LIMIT 0");
        } catch (RuntimeException failure) {
            assumeTrue(false, "BLOCKED: V026/V027 PostgreSQL schema is not available: "
                    + failure.getMessage());
        }
    }

    private static String environment(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value;
    }
}
