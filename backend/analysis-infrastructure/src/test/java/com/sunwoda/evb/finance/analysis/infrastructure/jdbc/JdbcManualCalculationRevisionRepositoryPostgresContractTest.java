package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 本次新增：验证 V026/V027 真实 PostgreSQL 下人工修订的版本追加、审计落库和事务回滚契约。
 *
 * <p>测试直接获取生产 JDBC 仓储，避免用测试事务掩盖仓储自身的 Spring 事务代理配置。</p>
 */
class JdbcManualCalculationRevisionRepositoryPostgresContractTest {
    private static AnnotationConfigApplicationContext context;
    private static DataSource dataSource;
    private static JdbcTemplate jdbc;
    private static JdbcManualCalculationRevisionRepository repository;
    private String suffix;
    private String batchNo;
    private String jobId;
    private String snapshotId;
    private String sourceVersionId;
    private String targetVersionId;
    private String revisionId;
    private String triggerName;
    private String functionName;

    @BeforeAll
    static void startRealPostgresContext() {
        String url = System.getenv("AMOEBA_DB_URL");
        assumeTrue(url != null && !url.trim().isEmpty(),
                "BLOCKED: AMOEBA_DB_URL is not set; manual revision PostgreSQL contract was not run");
        dataSource = new DriverManagerDataSource(url, environment("AMOEBA_DB_USERNAME"),
                environment("AMOEBA_DB_PASSWORD"));
        jdbc = new JdbcTemplate(dataSource);
        verifySchema();

        context = new AnnotationConfigApplicationContext();
        context.register(TestConfig.class);
        context.refresh();
        repository = context.getBean(JdbcManualCalculationRevisionRepository.class);
        assertTrue(AopUtils.isAopProxy(repository), "人工修订 JDBC 仓储必须通过 Spring 事务代理执行");
        assertTrue(AopUtils.isCglibProxy(repository), "人工修订 JDBC 仓储必须使用 CGLIB 事务代理");
    }

    @AfterAll
    static void closeContext() {
        if (context != null) context.close();
    }

    @BeforeEach
    void seedFixture() {
        suffix = UUID.randomUUID().toString().replace("-", "");
        batchNo = "TEST-DATA-004-REVISION-" + suffix;
        jobId = "TEST-REVISION-JOB-" + suffix;
        snapshotId = "TEST-REVISION-SNAPSHOT-" + suffix;
        sourceVersionId = "TEST-REVISION-SOURCE-" + suffix;
        targetVersionId = "TEST-REVISION-TARGET-" + suffix;
        revisionId = "TEST-REVISION-RELATION-" + suffix;
        jdbc.update("INSERT INTO meta.analysis_batch (batch_no, period, status, perspective, created_by) "
                        + "VALUES (?, ?, ?, ?, ?)", batchNo, "2099-12", "DRAFT", "BASE", "contract-test");
        jdbc.update("INSERT INTO flow.calculation_job "
                        + "(job_id, request_key, batch_no, status, attempt_no) VALUES (?, ?, ?, ?, ?)",
                jobId, "TEST-REVISION-REQUEST-" + suffix, batchNo, "SUCCEEDED", 1);
        jdbc.update("INSERT INTO calc.calculation_snapshot "
                        + "(snapshot_id, snapshot_type, schema_version, content_json, source_version_set, "
                        + "content_hash, source_version_hash, rule_version, created_by) "
                        + "VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?)", snapshotId, "INPUT", "REVISION-1",
                "{\"source\":\"contract\"}", "[\"REVISION\"]", "content-" + suffix,
                "source-" + suffix, "rule-1", "contract-test");
        jdbc.update("INSERT INTO calc.calculation_version ("
                        + "calculation_version_id, batch_id, batch_no, perspective, version_no, job_id, snapshot_id, "
                        + "source_version_hash, config_hash, input_fingerprint, status, current_flag, trace_id, "
                        + "calculated_by, calculated_at) "
                        + "SELECT ?, id, ?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE, ?, ?, ? "
                        + "FROM meta.analysis_batch WHERE batch_no = ?", sourceVersionId, batchNo, "BASE",
                "V-SOURCE", jobId, snapshotId, "source-" + suffix, "config-" + suffix, "input-" + suffix,
                "PUBLISHED", "trace-source-" + suffix, "calculator", LocalDateTime.of(2099, 12, 1, 10, 0),
                batchNo);
    }

    @AfterEach
    void cleanupFixture() {
        if (triggerName != null && functionName != null) {
            jdbc.execute("DROP TRIGGER IF EXISTS " + triggerName + " ON calc.calculation_manual_revision");
            jdbc.execute("DROP FUNCTION IF EXISTS calc." + functionName + "()");
        }
        jdbc.update("DELETE FROM calc.calculation_manual_revision WHERE revision_id = ?", revisionId);
        jdbc.update("DELETE FROM calc.calculation_version WHERE calculation_version_id IN (?, ?)",
                targetVersionId, sourceVersionId);
        jdbc.update("DELETE FROM calc.calculation_snapshot WHERE snapshot_id = ?", snapshotId);
        jdbc.update("DELETE FROM flow.calculation_job WHERE job_id = ?", jobId);
        jdbc.update("DELETE FROM meta.analysis_batch WHERE batch_no = ?", batchNo);
    }

    @Test
    void appendKeepsSourceAndPersistsTargetAndAuditInPostgres() {
        CalculationVersion target = targetVersion();
        ManualCalculationRevision revision = revision();

        CalculationVersion returned = repository.append(sourceVersion(), target, revision);

        assertEquals(targetVersionId, returned.getCalculationVersionId());
        assertEquals(1, count("SELECT COUNT(*) FROM calc.calculation_version WHERE calculation_version_id = ?",
                sourceVersionId));
        assertEquals(1, count("SELECT COUNT(*) FROM calc.calculation_version WHERE calculation_version_id = ?",
                targetVersionId));
        assertEquals(1, count("SELECT COUNT(*) FROM calc.calculation_version "
                + "WHERE batch_no = ? AND perspective = 'BASE' AND current_flag", batchNo),
                "同批次视角只能有一个当前版本");
        assertFalse(jdbc.queryForObject("SELECT current_flag FROM calc.calculation_version "
                        + "WHERE calculation_version_id = ?", Boolean.class, sourceVersionId));
        assertEquals("SUPERSEDED", jdbc.queryForObject("SELECT status FROM calc.calculation_version "
                        + "WHERE calculation_version_id = ?", String.class, sourceVersionId));
        assertTrue(jdbc.queryForObject("SELECT current_flag FROM calc.calculation_version "
                        + "WHERE calculation_version_id = ?", Boolean.class, targetVersionId));
        assertEquals(1, count("SELECT COUNT(*) FROM calc.calculation_manual_revision "
                        + "WHERE revision_id = ? AND revised_by = ? AND revision_reason = ? "
                        + "AND revised_at = ? AND trace_id = ?", revisionId, "finance-user", "人工调整毛利",
                java.sql.Timestamp.valueOf(revision.getRevisedAt()), revision.getTraceId()));

        Optional<ManualCalculationRevision> stored = repository.findByTargetVersionId(targetVersionId);
        assertTrue(stored.isPresent());
        assertEquals("finance-user", stored.get().getRevisedBy());
        assertEquals("人工调整毛利", stored.get().getRevisionReason());
    }

    @Test
    void failedAuditInsertRollsBackSourceSwitchAndTargetInsert() {
        triggerName = "trg_revision_failure_" + suffix;
        functionName = "fn_revision_failure_" + suffix;
        jdbc.execute("CREATE FUNCTION calc." + functionName + "() RETURNS trigger LANGUAGE plpgsql AS $$ "
                + "BEGIN RAISE EXCEPTION '人工修订审计注入失败'; END; $$");
        jdbc.execute("CREATE TRIGGER " + triggerName + " BEFORE INSERT ON calc.calculation_manual_revision "
                + "FOR EACH ROW EXECUTE FUNCTION calc." + functionName + "()");

        DataAccessException failure = assertThrows(DataAccessException.class,
                () -> repository.append(sourceVersion(), targetVersion(), revision()));
        assertNotNull(failure.getMessage());
        assertTrue(jdbc.queryForObject("SELECT current_flag FROM calc.calculation_version "
                + "WHERE calculation_version_id = ?", Boolean.class, sourceVersionId),
                "审计落库失败时来源版本必须恢复为当前版本");
        assertEquals("PUBLISHED", jdbc.queryForObject("SELECT status FROM calc.calculation_version "
                + "WHERE calculation_version_id = ?", String.class, sourceVersionId));
        assertEquals(0, count("SELECT COUNT(*) FROM calc.calculation_version WHERE calculation_version_id = ?",
                targetVersionId), "事务回滚后目标版本不得残留");
        assertEquals(0, count("SELECT COUNT(*) FROM calc.calculation_manual_revision WHERE revision_id = ?",
                revisionId), "事务回滚后修订审计不得残留");
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
        } catch (RuntimeException failure) {
            assumeTrue(false, "BLOCKED: V026/V027 PostgreSQL schema is not available: "
                    + failure.getMessage());
        }
    }

    private static int count(String sql, Object... parameters) {
        Integer value = jdbc.queryForObject(sql, Integer.class, parameters);
        return value == null ? 0 : value;
    }

    private static String environment(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value;
    }
}
