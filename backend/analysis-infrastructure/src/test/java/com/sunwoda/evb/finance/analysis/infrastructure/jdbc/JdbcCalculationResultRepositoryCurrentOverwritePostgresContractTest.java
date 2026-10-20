package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResult;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResultStatus;
import com.sunwoda.evb.finance.analysis.domain.model.PnlResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 本次修改：验证结果仓储当前“同批次覆盖式保存”的 PostgreSQL JDBC 最小契约。
 *
 * <p>测试只覆盖当前实现已经具备的行为：保存新结果会清理旧 header/line，
 * 以及重新保存后的生命周期字段能够从数据库读取；不引入历史版本或结果追溯口径。</p>
 */
class JdbcCalculationResultRepositoryCurrentOverwritePostgresContractTest {
    private static final String BATCH_PREFIX = "TEST-RESULT-OVERWRITE-";
    private static final String SCOPE_CODE = "SCOPE-OVERWRITE";
    private static final String PERSPECTIVE = "BASE";

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;
    private static JdbcCalculationResultRepository repository;
    private static String batchNo;

    @BeforeAll
    static void connectAndSeedPostgres() {
        String url = System.getenv("AMOEBA_DB_URL");
        assumeTrue(url != null && !url.trim().isEmpty(),
                "BLOCKED: AMOEBA_DB_URL is not set; current overwrite PostgreSQL contract was not run");

        dataSource = new DriverManagerDataSource(
                url, environment("AMOEBA_DB_USERNAME"), environment("AMOEBA_DB_PASSWORD"));
        jdbc = new JdbcTemplate(dataSource);
        try {
            jdbc.queryForObject("SELECT COUNT(*) FROM meta.analysis_batch", Long.class);
            jdbc.queryForObject("SELECT COUNT(*) FROM calc.pnl_result", Long.class);
            jdbc.queryForObject("SELECT COUNT(*) FROM calc.pnl_result_line", Long.class);
        } catch (RuntimeException failure) {
            assumeTrue(false, "BLOCKED: current overwrite PostgreSQL schema is not available: "
                    + failure.getMessage());
        }

        repository = new JdbcCalculationResultRepository(dataSource);
        batchNo = BATCH_PREFIX + UUID.randomUUID().toString().replace("-", "");
        jdbc.update("INSERT INTO meta.analysis_batch "
                        + "(batch_no, period, perspective, status, created_by, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                batchNo, "2026-10", PERSPECTIVE, "DRAFT", "TEST-DATA-004", timestamp(2026, 10, 20, 10, 0),
                timestamp(2026, 10, 20, 10, 0));
    }

    @AfterAll
    static void cleanupFixture() {
        if (jdbc == null || batchNo == null) return;
        jdbc.update("DELETE FROM calc.pnl_result_line WHERE result_id IN "
                + "(SELECT r.id FROM calc.pnl_result r JOIN meta.analysis_batch b ON b.id = r.batch_id "
                + "WHERE b.batch_no = ?)", batchNo);
        jdbc.update("DELETE FROM calc.pnl_result WHERE batch_id IN "
                + "(SELECT id FROM meta.analysis_batch WHERE batch_no = ?)", batchNo);
        jdbc.update("DELETE FROM meta.analysis_batch WHERE batch_no = ?", batchNo);
    }

    @Test
    void savingSameBatchReplacesOldHeaderAndLinesWithoutResidue() {
        repository.save(result("DATA-004-R1", CalculationResultStatus.CALCULATED, null, null,
                timestamp(2026, 10, 20, 10, 1), null, null,
                lines("REVENUE", "100.00", "OLD_ONLY", "9.00")));

        repository.save(result("DATA-004-R2", CalculationResultStatus.CALCULATED, null, null,
                timestamp(2026, 10, 20, 10, 2), null, null,
                lines("REVENUE", "120.00", "NEW_ONLY", "12.00")));

        Optional<CalculationResult> loaded = repository.findByBatchNo(batchNo);
        assertTrue(loaded.isPresent());
        CalculationResult result = loaded.get();
        assertEquals("DATA-004-R2", result.getVersionNo());
        assertEquals(1, result.getResults().size());
        assertEquals(2, result.getResults().get(0).getLines().size());
        assertEquals(0, new BigDecimal("120.00").compareTo(
                result.getResults().get(0).getLines().get("REVENUE")));
        assertFalse(result.getResults().get(0).getLines().containsKey("OLD_ONLY"));
        assertTrue(result.getResults().get(0).getLines().containsKey("NEW_ONLY"));

        Long resultCount = jdbc.queryForObject("SELECT COUNT(*) FROM calc.pnl_result "
                + "WHERE batch_id = (SELECT id FROM meta.analysis_batch WHERE batch_no = ?)", Long.class, batchNo);
        Long lineCount = jdbc.queryForObject("SELECT COUNT(*) FROM calc.pnl_result_line "
                + "WHERE result_id IN (SELECT id FROM calc.pnl_result WHERE batch_id = "
                + "(SELECT id FROM meta.analysis_batch WHERE batch_no = ?))", Long.class, batchNo);
        assertEquals(Long.valueOf(1L), resultCount);
        assertEquals(Long.valueOf(2L), lineCount);
    }

    @Test
    void savingLifecycleFieldsMakesStatusOperatorsAndTimesReadable() {
        LocalDateTime calculatedAt = timestamp(2026, 10, 20, 11, 0);
        LocalDateTime confirmedAt = timestamp(2026, 10, 20, 11, 5);
        LocalDateTime publishedAt = timestamp(2026, 10, 20, 11, 10);
        CalculationResult published = new CalculationResult(
                batchNo, AnalysisPerspective.BASE,
                Collections.singletonList(new PnlResult(SCOPE_CODE, lines("REVENUE", "130.00"))),
                "DATA-004-LIFECYCLE", CalculationResultStatus.PUBLISHED,
                "CONFIRM-USER", "PUBLISH-USER", calculatedAt, confirmedAt, publishedAt);

        repository.save(published);

        Optional<CalculationResult> loaded = repository.findByBatchNo(batchNo);
        assertTrue(loaded.isPresent());
        CalculationResult result = loaded.get();
        assertEquals(CalculationResultStatus.PUBLISHED, result.getStatus());
        assertEquals("DATA-004-LIFECYCLE", result.getVersionNo());
        assertEquals("CONFIRM-USER", result.getConfirmedBy());
        assertEquals("PUBLISH-USER", result.getPublishedBy());
        assertEquals(calculatedAt, result.getCalculatedAt());
        assertEquals(confirmedAt, result.getConfirmedAt());
        assertEquals(publishedAt, result.getPublishedAt());
    }

    private static CalculationResult result(String versionNo, CalculationResultStatus status,
                                            String confirmedBy, String publishedBy,
                                            LocalDateTime calculatedAt, LocalDateTime confirmedAt,
                                            LocalDateTime publishedAt, Map<String, BigDecimal> lines) {
        return new CalculationResult(batchNo, AnalysisPerspective.BASE,
                Collections.singletonList(new PnlResult(SCOPE_CODE, lines)), versionNo, status,
                confirmedBy, publishedBy, calculatedAt, confirmedAt, publishedAt);
    }

    private static Map<String, BigDecimal> lines(String... values) {
        Map<String, BigDecimal> lines = new LinkedHashMap<String, BigDecimal>();
        for (int i = 0; i < values.length; i += 2) {
            lines.put(values[i], new BigDecimal(values[i + 1]));
        }
        return lines;
    }

    private static LocalDateTime timestamp(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute);
    }

    private static String environment(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value;
    }
}
