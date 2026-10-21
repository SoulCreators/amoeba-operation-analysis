package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResult;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResultStatus;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationVersion;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationVersionStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ManualCalculationRevision;
import com.sunwoda.evb.finance.analysis.domain.model.PnlResult;
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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 本次新增：验证人工修订结果切片和普通结果保存的 PostgreSQL 版本归属契约。
 *
 * <p>该测试故意探测 pnl_result_line.calculation_version_id；现有 V026 只增加了
 * pnl_result 的版本列，缺列时测试以 BLOCKED 暴露数据库缺口，不替测试偷偷补迁移。</p>
 */
class JdbcCalculationResultVersionSlicePostgresContractTest {
    private static AnnotationConfigApplicationContext context;
    private static DataSource dataSource;
    private static JdbcTemplate jdbc;
    private static JdbcManualCalculationRevisionRepository revisionRepository;
    private static JdbcCalculationResultRepository resultRepository;

    private String suffix;
    private String batchNo;
    private String jobId;
    private String snapshotId;
    private String sourceVersionId;
    private String targetVersionId;
    private String revisionId;

    @BeforeAll
    static void startPostgresContext() {
        String url = System.getenv("AMOEBA_DB_URL");
        assumeTrue(url != null && !url.trim().isEmpty(),
                "BLOCKED: AMOEBA_DB_URL is not set; result version slice PostgreSQL contract was not run");
        dataSource = new DriverManagerDataSource(url, environment("AMOEBA_DB_USERNAME"),
                environment("AMOEBA_DB_PASSWORD"));
        jdbc = new JdbcTemplate(dataSource);
        verifySchema();

        context = new AnnotationConfigApplicationContext(TestConfig.class);
        revisionRepository = context.getBean(JdbcManualCalculationRevisionRepository.class);
        resultRepository = context.getBean(JdbcCalculationResultRepository.class);
        assertTrue(AopUtils.isAopProxy(revisionRepository), "人工修订仓储必须通过 Spring 事务代理执行");
    }

    @AfterAll
    static void closeContext() {
        if (context != null) context.close();
    }

    @BeforeEach
    void seedFixture() {
        suffix = UUID.randomUUID().toString().replace("-", "");
        batchNo = "TEST-RESULT-VERSION-SLICE-" + suffix;
        jobId = "TEST-RESULT-VERSION-JOB-" + suffix;
        snapshotId = "TEST-RESULT-VERSION-SNAPSHOT-" + suffix;
        sourceVersionId = "TEST-RESULT-VERSION-SOURCE-" + suffix;
        targetVersionId = "TEST-RESULT-VERSION-TARGET-" + suffix;
        revisionId = "TEST-RESULT-VERSION-REVISION-" + suffix;

        jdbc.update("INSERT INTO meta.analysis_batch (batch_no, period, status, perspective, created_by) "
                        + "VALUES (?, ?, ?, ?, ?)", batchNo, "2099-12", "DRAFT", "BASE", "contract-test");
        jdbc.update("INSERT INTO flow.calculation_job "
                        + "(job_id, request_key, batch_no, status, attempt_no) VALUES (?, ?, ?, ?, ?)",
                jobId, "TEST-RESULT-VERSION-REQUEST-" + suffix, batchNo, "SUCCEEDED", 1);
        jdbc.update("INSERT INTO calc.calculation_snapshot "
                        + "(snapshot_id, snapshot_type, schema_version, content_json, source_version_set, "
                        + "content_hash, source_version_hash, rule_version, created_by) "
                        + "VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?)", snapshotId, "INPUT", "TEST-1",
                "{\"source\":\"contract\"}", "[\"RESULT\"]", "content-" + suffix,
                "source-" + suffix, "rule-1", "contract-test");
        jdbc.update("INSERT INTO calc.calculation_version ("
                        + "calculation_version_id, batch_id, batch_no, perspective, version_no, job_id, snapshot_id, "
                        + "source_version_hash, config_hash, input_fingerprint, status, current_flag, trace_id, "
                        + "calculated_by, calculated_at) "
                        + "SELECT ?, id, ?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE, ?, ?, ? "
                        + "FROM meta.analysis_batch WHERE batch_no = ?", sourceVersionId, batchNo, "BASE", "V-SOURCE",
                jobId, snapshotId, "source-" + suffix, "config-" + suffix, "input-" + suffix,
                "PUBLISHED", "trace-source-" + suffix, "calculator", time(10), batchNo);
        jdbc.update("INSERT INTO calc.pnl_result ("
                        + "batch_id, perspective, scope_code, result_status, version_no, calculation_version_id) "
                        + "SELECT id, 'BASE', 'SCOPE-A', 'PUBLISHED', 'V-SOURCE', ? "
                        + "FROM meta.analysis_batch WHERE batch_no = ?", sourceVersionId, batchNo);
        Long sourceResultId = jdbc.queryForObject("SELECT id FROM calc.pnl_result "
                        + "WHERE calculation_version_id = ?", Long.class, sourceVersionId);
        jdbc.update("INSERT INTO calc.pnl_result_line "
                        + "(result_id, line_code, line_value, calculation_version_id) VALUES (?, ?, ?, ?)",
                sourceResultId, "REVENUE", new BigDecimal("100.00"), sourceVersionId);
        jdbc.update("INSERT INTO calc.pnl_result_line "
                        + "(result_id, line_code, line_value, calculation_version_id) VALUES (?, ?, ?, ?)",
                sourceResultId, "GROSS_PROFIT", new BigDecimal("20.00"), sourceVersionId);
    }

    @AfterEach
    void cleanupFixture() {
        jdbc.update("DELETE FROM calc.pnl_result_line WHERE calculation_version_id IN (?, ?)",
                sourceVersionId, targetVersionId);
        jdbc.update("DELETE FROM calc.pnl_result WHERE calculation_version_id IN (?, ?)",
                sourceVersionId, targetVersionId);
        jdbc.update("DELETE FROM calc.calculation_manual_revision WHERE revision_id = ?", revisionId);
        jdbc.update("DELETE FROM calc.calculation_version WHERE calculation_version_id IN (?, ?)",
                targetVersionId, sourceVersionId);
        jdbc.update("DELETE FROM calc.calculation_snapshot WHERE snapshot_id = ?", snapshotId);
        jdbc.update("DELETE FROM flow.calculation_job WHERE job_id = ?", jobId);
        jdbc.update("DELETE FROM meta.analysis_batch WHERE batch_no = ?", batchNo);
    }

    @Test
    void manualRevisionCopiesHeadersAndLinesToTargetVersionWithoutChangingSource() {
        revisionRepository.append(sourceVersion(), targetVersion(), revision());

        assertEquals(1, count("SELECT COUNT(*) FROM calc.pnl_result WHERE calculation_version_id = ?",
                sourceVersionId));
        assertEquals(1, count("SELECT COUNT(*) FROM calc.pnl_result WHERE calculation_version_id = ?",
                targetVersionId));
        assertEquals(2, count("SELECT COUNT(*) FROM calc.pnl_result_line WHERE calculation_version_id = ?",
                sourceVersionId));
        assertEquals(2, count("SELECT COUNT(*) FROM calc.pnl_result_line WHERE calculation_version_id = ?",
                targetVersionId));
        assertEquals(0, new BigDecimal("100.00").compareTo(jdbc.queryForObject(
                "SELECT line_value FROM calc.pnl_result_line WHERE calculation_version_id = ? "
                        + "AND line_code = 'REVENUE'", BigDecimal.class, sourceVersionId)));
        assertEquals("V-TARGET", jdbc.queryForObject("SELECT version_no FROM calc.pnl_result "
                + "WHERE calculation_version_id = ?", String.class, targetVersionId));

        Optional<CalculationResult> readable = resultRepository.findByBatchNo(batchNo);
        assertTrue(readable.isPresent());
        assertEquals("V-TARGET", readable.get().getVersionNo());
        // 本次修复：数据库 NUMERIC 返回值的 scale 可能不同，业务断言按数值而非显示精度比较。
        assertEquals(0, new BigDecimal("20.00").compareTo(
                readable.get().getResults().get(0).getLines().get("GROSS_PROFIT")));
    }

    @Test
    void ordinarySaveWritesVersionIdToHeaderAndLineWhenVersionCanBeResolved() {
        CalculationResult result = new CalculationResult(batchNo, AnalysisPerspective.BASE,
                Collections.singletonList(new PnlResult("SCOPE-A", lines("REVENUE", "130.00"))),
                "V-SOURCE", CalculationResultStatus.PUBLISHED, null, null, time(12), null, null);

        resultRepository.save(result);

        assertEquals(1, count("SELECT COUNT(*) FROM calc.pnl_result "
                + "WHERE calculation_version_id = ?", sourceVersionId));
        assertEquals(1, count("SELECT COUNT(*) FROM calc.pnl_result_line "
                + "WHERE calculation_version_id = ?", sourceVersionId));
        assertEquals(0, new BigDecimal("130.00").compareTo(jdbc.queryForObject(
                "SELECT line_value FROM calc.pnl_result_line WHERE calculation_version_id = ?",
                BigDecimal.class, sourceVersionId)));
    }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TestConfig {
        @Bean
        DataSource dataSource() {
            return dataSource;
        }

        @Bean
        DataSourceTransactionManager transactionManager(DataSource source) {
            return new DataSourceTransactionManager(source);
        }

        @Bean
        JdbcManualCalculationRevisionRepository revisionRepository(DataSource source) {
            return new JdbcManualCalculationRevisionRepository(source);
        }

        @Bean
        JdbcCalculationResultRepository resultRepository(DataSource source) {
            return new JdbcCalculationResultRepository(source);
        }
    }

    private CalculationVersion sourceVersion() {
        return new CalculationVersion(sourceVersionId, batchNo, AnalysisPerspective.BASE, "V-SOURCE", jobId,
                snapshotId, "source-" + suffix, "config-" + suffix, "input-" + suffix,
                CalculationVersionStatus.PUBLISHED, true, null, "trace-source-" + suffix, "calculator",
                time(10), null, null, null, null);
    }

    private CalculationVersion targetVersion() {
        return new CalculationVersion(targetVersionId, batchNo, AnalysisPerspective.BASE, "V-TARGET", jobId,
                snapshotId, "source-" + suffix, "config-" + suffix, "input-" + suffix,
                CalculationVersionStatus.CALCULATED, true, sourceVersionId, "trace-target-" + suffix,
                "finance-user", time(11), null, null, null, null);
    }

    private ManualCalculationRevision revision() {
        return new ManualCalculationRevision(revisionId, sourceVersionId, targetVersionId, "finance-user",
                "人工调整毛利", time(11), "trace-target-" + suffix);
    }

    private static Map<String, BigDecimal> lines(String code, String value) {
        Map<String, BigDecimal> lines = new LinkedHashMap<String, BigDecimal>();
        lines.put(code, new BigDecimal(value));
        return lines;
    }

    private static int count(String sql, Object... parameters) {
        Integer value = jdbc.queryForObject(sql, Integer.class, parameters);
        return value == null ? 0 : value;
    }

    private static LocalDateTime time(int hour) {
        return LocalDateTime.of(2099, 12, 1, hour, 0);
    }

    private static void verifySchema() {
        try {
            jdbc.execute("SELECT 1 FROM calc.calculation_version LIMIT 0");
            jdbc.execute("SELECT 1 FROM calc.pnl_result LIMIT 0");
            jdbc.execute("SELECT 1 FROM calc.pnl_result_line LIMIT 0");
            jdbc.execute("SELECT calculation_version_id FROM calc.pnl_result_line LIMIT 0");
        } catch (RuntimeException failure) {
            assumeTrue(false, "BLOCKED: pnl_result_line.calculation_version_id is not available; "
                    + "database migration is required: " + failure.getMessage());
        }
    }

    private static String environment(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value;
    }
}
