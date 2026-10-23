package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.NonfinancialFact;
import com.sunwoda.evb.finance.analysis.domain.model.NonfinancialPublication;
import com.sunwoda.evb.finance.analysis.domain.model.NonfinancialPublicationStatus;
import com.sunwoda.evb.finance.analysis.domain.model.NonfinancialResult;
import com.sunwoda.evb.finance.analysis.domain.model.PairingStatus;
import com.sunwoda.evb.finance.analysis.domain.model.TargetActual;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * NF-JDBC-001：非财经指标事实与结果的真实 PostgreSQL 往返契约。
 *
 * <p>以固定未来期间夹具，验证同期整期替换与全部字段往返，测试结束按期间清理。</p>
 */
class JdbcNonfinancialRoundTripPostgresContractTest {
    private static final String PERIOD = "2099-12";

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;
    private static JdbcNonfinancialFactRepository factRepository;
    private static JdbcNonfinancialResultRepository resultRepository;
    private static JdbcNonfinancialPublicationRepository publicationRepository;

    @BeforeAll
    static void connectToConfiguredPostgres() {
        String url = System.getenv("AMOEBA_DB_URL");
        assumeTrue(url != null && !url.trim().isEmpty(),
                "BLOCKED: AMOEBA_DB_URL is not set; nonfinancial PostgreSQL contract was not run");

        String username = System.getenv("AMOEBA_DB_USERNAME");
        assumeTrue(username != null && !username.trim().isEmpty(),
                "BLOCKED: AMOEBA_DB_USERNAME is not set; nonfinancial PostgreSQL contract was not run");

        String password = System.getenv("AMOEBA_DB_PASSWORD");
        assumeTrue(password != null,
                "BLOCKED: AMOEBA_DB_PASSWORD is not set; nonfinancial PostgreSQL contract was not run");

        dataSource = new DriverManagerDataSource(url, username, password);
        jdbc = new JdbcTemplate(dataSource);
        try {
            jdbc.execute("SELECT id, period, base_code, production_line, cell_model, "
                    + "quality_category, target_actual, quality_value, source_row_no, "
                    + "effective_file_version FROM flow.nonfinancial_fact LIMIT 0");
            jdbc.execute("SELECT id, period, base_code, production_line, cell_model, "
                    + "quality_category, target_value, actual_value, gap, achievement_rate, "
                    + "pairing_status, leader_employee_no, leader_conflict "
                    + "FROM flow.nonfinancial_result LIMIT 0");
            jdbc.execute("SELECT period, status, conflict_count, published_by, published_at "
                    + "FROM flow.nonfinancial_publication LIMIT 0");
        } catch (RuntimeException failure) {
            assumeTrue(false, "BLOCKED: nonfinancial tables are not available: "
                    + failure.getMessage());
        }
        factRepository = new JdbcNonfinancialFactRepository(dataSource);
        resultRepository = new JdbcNonfinancialResultRepository(dataSource);
        publicationRepository = new JdbcNonfinancialPublicationRepository(dataSource);
    }

    @AfterEach
    void cleanupFixtureRows() {
        if (jdbc == null) {
            return;
        }
        jdbc.update("DELETE FROM flow.nonfinancial_publication WHERE period = ?", PERIOD);
        jdbc.update("DELETE FROM flow.nonfinancial_result WHERE period = ?", PERIOD);
        jdbc.update("DELETE FROM flow.nonfinancial_fact WHERE period = ?", PERIOD);
    }

    @Test
    void factRoundTripPreservesAllFields() {
        NonfinancialFact target = NonfinancialFact.item("BASE-1", "L1", "CELL-1", "YIELD",
                TargetActual.TARGET, new BigDecimal("0.9800"), 1, "fv-1").bind(PERIOD);
        NonfinancialFact actual = NonfinancialFact.item("BASE-1", "L1", "CELL-1", "YIELD",
                TargetActual.ACTUAL, new BigDecimal("0.9700"), 2, "fv-1").bind(PERIOD);

        factRepository.replaceByPeriod(PERIOD, Arrays.asList(target, actual));
        List<NonfinancialFact> loaded = factRepository.findByPeriod(PERIOD);

        assertEquals(2, loaded.size());
        NonfinancialFact first = loaded.get(0);
        assertNotNull(first.getId());
        assertEquals(PERIOD, first.getPeriod());
        assertEquals("BASE-1", first.getBaseCode());
        assertEquals("L1", first.getProductionLine());
        assertEquals("CELL-1", first.getCellModel());
        assertEquals("YIELD", first.getQualityCategory());
        assertEquals(TargetActual.TARGET, first.getTargetActual());
        assertEquals(0, first.getQualityValue().compareTo(new BigDecimal("0.9800")));
        assertEquals(1, first.getSourceRowNo());
        assertEquals("fv-1", first.getEffectiveFileVersion());
    }

    @Test
    void replaceByPeriodReplacesWholePeriod() {
        factRepository.replaceByPeriod(PERIOD, Collections.singletonList(
                NonfinancialFact.item("BASE-1", "L1", "CELL-1", "YIELD",
                        TargetActual.TARGET, new BigDecimal("0.9800"), 1, "fv-1").bind(PERIOD)));
        factRepository.replaceByPeriod(PERIOD, Collections.singletonList(
                NonfinancialFact.item("BASE-2", "L1", "CELL-1", "YIELD",
                        TargetActual.TARGET, new BigDecimal("0.9900"), 1, "fv-2").bind(PERIOD)));

        List<NonfinancialFact> loaded = factRepository.findByPeriod(PERIOD);
        assertEquals(1, loaded.size());
        assertEquals("BASE-2", loaded.get(0).getBaseCode());
    }

    @Test
    void resultRoundTripPreservesComputedFields() {
        NonfinancialResult result = new NonfinancialResult(null, PERIOD, "BASE-1", "L1",
                "CELL-1", "YIELD", new BigDecimal("1.00"), new BigDecimal("0.50"),
                new BigDecimal("-0.50"), new BigDecimal("0.5000000000"), PairingStatus.BOTH,
                "LDR-1", false);

        resultRepository.replaceByPeriod(PERIOD, Collections.singletonList(result));
        List<NonfinancialResult> loaded = resultRepository.findByPeriod(PERIOD);

        assertEquals(1, loaded.size());
        NonfinancialResult first = loaded.get(0);
        assertNotNull(first.getId());
        assertEquals("BASE-1", first.getBaseCode());
        assertEquals(PairingStatus.BOTH, first.getPairingStatus());
        assertEquals(0, first.getGap().compareTo(new BigDecimal("-0.50")));
        assertEquals(0, first.getAchievementRate().compareTo(new BigDecimal("0.5000000000")));
        assertEquals("LDR-1", first.getLeaderEmployeeNo());
        assertTrue(!first.isLeaderConflict());
    }

    @Test
    void publicationUpsertAndFindRoundTrip() {
        NonfinancialPublication draft = NonfinancialPublication.draft(PERIOD);
        publicationRepository.save(draft);

        Optional<NonfinancialPublication> loaded = publicationRepository.findByPeriod(PERIOD);
        assertTrue(loaded.isPresent());
        assertEquals(NonfinancialPublicationStatus.DRAFT, loaded.get().getStatus());

        LocalDateTime publishedAt = LocalDateTime.of(2026, 10, 22, 9, 0, 0);
        NonfinancialPublication published = draft.publish(0, "hq-finance", publishedAt);
        publicationRepository.save(published);

        Optional<NonfinancialPublication> reloaded = publicationRepository.findByPeriod(PERIOD);
        assertTrue(reloaded.isPresent());
        assertEquals(NonfinancialPublicationStatus.PUBLISHED, reloaded.get().getStatus());
        assertEquals(0, reloaded.get().getConflictCount());
        assertEquals("hq-finance", reloaded.get().getPublishedBy());
        assertEquals(publishedAt, reloaded.get().getPublishedAt());
    }
}
