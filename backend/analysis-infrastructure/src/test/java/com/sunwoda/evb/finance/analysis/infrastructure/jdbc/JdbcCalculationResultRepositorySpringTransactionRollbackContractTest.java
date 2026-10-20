package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResult;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResultStatus;
import com.sunwoda.evb.finance.analysis.domain.model.PnlResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 本次修改：补齐结果仓储在真实 Spring 事务代理下的 PostgreSQL 回滚契约。
 *
 * <p>本测试直接从 Spring 容器取得生产 {@link JdbcCalculationResultRepository}，并断言
 * 生产仓储自身必须由 Spring CGLIB 代理执行。测试不使用测试专用事务包装器，也不会把
 * 外层测试事务当成生产能力，更不会放宽到只验证异常。</p>
 *
 * <p>PostgreSQL 触发器在第二条新明细写入时抛出异常。若仓储参与同一事务，旧 header/line
 * 必须完整保留，新 header/line 必须全部不存在；任何半写入结果都应使测试失败。</p>
 */
class JdbcCalculationResultRepositorySpringTransactionRollbackContractTest {
    private static final String PERSPECTIVE = "BASE";
    private static final String OLD_VERSION = "DATA-004-TECH-002-OLD";
    private static final String NEW_VERSION = "DATA-004-TECH-002-NEW";
    private static final String FAILURE_LINE = "DATA-004-TECH-002-FAIL";

    private static AnnotationConfigApplicationContext context;
    private static DataSource dataSource;
    private static JdbcTemplate jdbc;
    private static JdbcCalculationResultRepository repository;
    private static String batchNo;
    private static String triggerName;
    private static String functionName;

    @BeforeAll
    static void startRealSpringTransactionContext() {
        String url = System.getenv("AMOEBA_DB_URL");
        assumeTrue(url != null && !url.trim().isEmpty(),
                "BLOCKED: AMOEBA_DB_URL is not set; PostgreSQL rollback contract was not run");

        dataSource = new DriverManagerDataSource(
                url, environment("AMOEBA_DB_USERNAME"), environment("AMOEBA_DB_PASSWORD"));
        jdbc = new JdbcTemplate(dataSource);
        verifySchema();

        context = new AnnotationConfigApplicationContext();
        context.register(TestConfig.class);
        context.refresh();

        repository = context.getBean(JdbcCalculationResultRepository.class);
        assertTrue(AopUtils.isAopProxy(repository),
                "生产 JdbcCalculationResultRepository 必须通过 Spring AOP 代理执行");
        assertTrue(AopUtils.isCglibProxy(repository),
                "生产 JdbcCalculationResultRepository 必须使用 CGLIB 事务代理");

        String token = UUID.randomUUID().toString().replace("-", "");
        batchNo = "TEST-DATA-004-TECH-002-" + token;
        triggerName = "trg_data004_tech002_fail_" + token;
        functionName = "fn_data004_tech002_fail_" + token;
        seedBatch();
        installFailureTrigger();
    }

    @AfterAll
    static void cleanFixtureAndCloseContext() {
        if (jdbc != null && triggerName != null && functionName != null) {
            dropFailureTrigger();
        }
        if (jdbc != null && batchNo != null) {
            jdbc.update("DELETE FROM calc.pnl_result_line WHERE result_id IN "
                    + "(SELECT r.id FROM calc.pnl_result r JOIN meta.analysis_batch b ON b.id = r.batch_id "
                    + "WHERE b.batch_no = ?)", batchNo);
            jdbc.update("DELETE FROM calc.pnl_result WHERE batch_id IN "
                    + "(SELECT id FROM meta.analysis_batch WHERE batch_no = ?)", batchNo);
            jdbc.update("DELETE FROM meta.analysis_batch WHERE batch_no = ?", batchNo);
        }
        if (context != null) {
            context.close();
        }
    }

    @Test
    void failedNewLineWriteRollsBackTheEntireResultSnapshot() {
        CalculationResult replacement = newResult(NEW_VERSION,
                result("SCOPE-A", line("REVENUE_NEW", "900.00", FAILURE_LINE, "901.00")));

        DataAccessException failure = assertThrows(DataAccessException.class,
                () -> repository.save(replacement));
        assertNotNull(failure.getMessage());

        assertEquals(1, count("SELECT COUNT(*) FROM calc.pnl_result r "
                        + "JOIN meta.analysis_batch b ON b.id = r.batch_id WHERE b.batch_no = ?",
                batchNo),
                "事务回滚后旧结果 header 必须完整保留");
        assertEquals(OLD_VERSION, jdbc.queryForObject(
                "SELECT version_no FROM calc.pnl_result r "
                        + "JOIN meta.analysis_batch b ON b.id = r.batch_id WHERE b.batch_no = ?",
                String.class, batchNo),
                "事务回滚后不得覆盖旧版本号");
        assertEquals(2, count("SELECT COUNT(*) FROM calc.pnl_result_line l "
                        + "JOIN calc.pnl_result r ON r.id = l.result_id "
                        + "JOIN meta.analysis_batch b ON b.id = r.batch_id WHERE b.batch_no = ?",
                batchNo),
                "事务回滚后旧结果 line 必须完整保留");
        assertEquals(0, count("SELECT COUNT(*) FROM calc.pnl_result r "
                        + "JOIN meta.analysis_batch b ON b.id = r.batch_id "
                        + "WHERE b.batch_no = ? AND r.version_no = ?",
                batchNo, NEW_VERSION),
                "失败的新结果 header 不得残留");
        assertEquals(0, count("SELECT COUNT(*) FROM calc.pnl_result_line l "
                        + "JOIN calc.pnl_result r ON r.id = l.result_id "
                        + "JOIN meta.analysis_batch b ON b.id = r.batch_id "
                        + "WHERE b.batch_no = ? AND l.line_code IN (?, ?)",
                batchNo, "REVENUE_NEW", FAILURE_LINE),
                "失败的新结果 line 不得残留");
        assertEquals(0, count("SELECT COUNT(*) FROM calc.pnl_result_line l "
                        + "LEFT JOIN calc.pnl_result r ON r.id = l.result_id "
                        + "WHERE r.id IS NULL"),
                "回滚后不得产生孤儿结果明细");
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
        JdbcCalculationResultRepository calculationResultRepository(DataSource dataSource) {
            return new JdbcCalculationResultRepository(dataSource);
        }

    }

    private static void verifySchema() {
        try {
            jdbc.execute("SELECT 1 FROM meta.analysis_batch LIMIT 0");
            jdbc.execute("SELECT 1 FROM calc.pnl_result LIMIT 0");
            jdbc.execute("SELECT 1 FROM calc.pnl_result_line LIMIT 0");
        } catch (RuntimeException failure) {
            assumeTrue(false, "BLOCKED: result rollback PostgreSQL schema is not available: "
                    + failure.getMessage());
        }
    }

    private static void seedBatch() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 20, 10, 0);
        jdbc.update("INSERT INTO meta.analysis_batch "
                        + "(batch_no, period, perspective, status, created_by, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                batchNo, "2026-10", PERSPECTIVE, "DRAFT", "TEST-DATA-004-TECH-002", now, now);
        repository.save(newResult(OLD_VERSION,
                result("SCOPE-A", line("REVENUE_OLD", "100.00", "GROSS_PROFIT_OLD", "20.00"))));
    }

    private static void installFailureTrigger() {
        jdbc.execute("CREATE FUNCTION calc." + functionName + "() RETURNS trigger "
                + "LANGUAGE plpgsql AS $$ BEGIN "
                + "IF NEW.line_code = '" + FAILURE_LINE + "' THEN "
                + "RAISE EXCEPTION 'DATA-004-TECH-002 injected line write failure'; "
                + "END IF; RETURN NEW; END; $$");
        jdbc.execute("CREATE TRIGGER " + triggerName + " BEFORE INSERT ON calc.pnl_result_line "
                + "FOR EACH ROW EXECUTE FUNCTION calc." + functionName + "()");
    }

    private static void dropFailureTrigger() {
        jdbc.execute("DROP TRIGGER IF EXISTS " + triggerName + " ON calc.pnl_result_line");
        jdbc.execute("DROP FUNCTION IF EXISTS calc." + functionName + "()");
    }

    private static CalculationResult newResult(String versionNo, PnlResult pnlResult) {
        return new CalculationResult(batchNo, AnalysisPerspective.BASE,
                java.util.Collections.singletonList(pnlResult), versionNo,
                CalculationResultStatus.CALCULATED, null, null,
                LocalDateTime.of(2026, 10, 20, 10, 0), null, null);
    }

    private static PnlResult result(String scopeCode, Map<String, BigDecimal> lines) {
        return new PnlResult(scopeCode, lines);
    }

    private static Map<String, BigDecimal> line(String firstCode, String firstValue,
                                                 String secondCode, String secondValue) {
        Map<String, BigDecimal> lines = new LinkedHashMap<String, BigDecimal>();
        lines.put(firstCode, new BigDecimal(firstValue));
        lines.put(secondCode, new BigDecimal(secondValue));
        return lines;
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
