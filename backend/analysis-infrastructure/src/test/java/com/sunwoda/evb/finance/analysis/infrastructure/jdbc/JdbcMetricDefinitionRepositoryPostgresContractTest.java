package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.MetricDefinition;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Executable PostgreSQL contract for the metric definition JDBC repository. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcMetricDefinitionRepositoryPostgresContractTest {
    private static final String METRIC_TABLE = "cfg.metric_definition";

    private DataSource dataSource;
    private JdbcTemplate jdbc;
    private JdbcMetricDefinitionRepository repository;
    private String runPrefix;

    @BeforeAll
    void connectToConfiguredPostgres() {
        String url = System.getenv("AMOEBA_DB_URL");
        assumeTrue(url != null && !url.trim().isEmpty(),
                "BLOCKED: AMOEBA_DB_URL is not set; metric PostgreSQL contract was not run");

        dataSource = new DriverManagerDataSource(
                url, environment("AMOEBA_DB_USERNAME"), environment("AMOEBA_DB_PASSWORD"));
        jdbc = new JdbcTemplate(dataSource);
        try {
            jdbc.queryForObject("SELECT COUNT(*) FROM " + METRIC_TABLE, Long.class);
        } catch (RuntimeException failure) {
            assumeTrue(false, "BLOCKED: metric PostgreSQL schema is not available: "
                    + failure.getMessage());
        }
        repository = new JdbcMetricDefinitionRepository(dataSource);
        runPrefix = "TEST-METRIC-DB-" + UUID.randomUUID().toString().replace("-", "");
    }

    @AfterEach
    void cleanupAfterTest() {
        deleteTestMetrics();
    }

    @AfterAll
    void cleanupAfterAll() {
        deleteTestMetrics();
    }

    @Test
    void metricDefinitionTableIsAvailable() {
        Long rowCount = jdbc.queryForObject("SELECT COUNT(*) FROM " + METRIC_TABLE, Long.class);

        assertNotNull(rowCount);
        assertTrue(rowCount >= 0);
    }

    @Test
    void saveInsertsAndUpdatesMetricDefinitionAndFindRehydratesIt() {
        String code = metricCode("SAVE");
        MetricDefinition inserted = new MetricDefinition(null, code, "测试指标", "BASE", "元",
                "REVENUE - COST", 30, true, "summary", "postgres-test", LocalDateTime.now());

        repository.save(inserted);

        MetricDefinition persisted = repository.findByCode(code).orElse(null);
        assertNotNull(persisted);
        assertNotNull(persisted.getId());
        assertEquals(code, persisted.getMetricCode());
        assertEquals("测试指标", persisted.getMetricName());
        assertEquals("BASE", persisted.getApplicablePerspective());
        assertEquals("元", persisted.getUnit());
        assertEquals("REVENUE - COST", persisted.getFormulaExpression());
        assertEquals(30, persisted.getDisplayOrder());
        assertTrue(persisted.isEnabled());
        assertEquals("summary", persisted.getAiSummaryTemplate());
        assertEquals("postgres-test", persisted.getUpdatedBy());
        assertNotNull(persisted.getUpdatedAt());

        MetricDefinition updated = new MetricDefinition(persisted.getId(), code, "更新指标",
                "ALL", "%", "REVENUE / COST", 5, false, "updated-summary",
                "postgres-updater", LocalDateTime.now());
        repository.save(updated);

        MetricDefinition rehydrated = repository.findByCode(code).orElse(null);
        assertNotNull(rehydrated);
        assertEquals(persisted.getId(), rehydrated.getId());
        assertEquals("更新指标", rehydrated.getMetricName());
        assertEquals("ALL", rehydrated.getApplicablePerspective());
        assertEquals("%", rehydrated.getUnit());
        assertEquals("REVENUE / COST", rehydrated.getFormulaExpression());
        assertEquals(5, rehydrated.getDisplayOrder());
        assertTrue(!rehydrated.isEnabled());
        assertEquals("updated-summary", rehydrated.getAiSummaryTemplate());
        assertEquals("postgres-updater", rehydrated.getUpdatedBy());
        assertNotNull(rehydrated.getUpdatedAt());
    }

    @Test
    void findReturnsEmptyForUnknownMetricCode() {
        assertTrue(!repository.findByCode(metricCode("UNKNOWN")).isPresent());
    }

    @Test
    void listAppliesPerspectiveAllEnabledFilterAndDisplayOrder() {
        save(metricCode("BASE-LOW"), "BASE", 20, true);
        save(metricCode("BASE-HIGH"), "BASE", 30, true);
        save(metricCode("ALL"), "ALL", 10, true);
        save(metricCode("PRODUCT"), "PRODUCT_LINE", 5, true);
        save(metricCode("DISABLED"), "BASE", 1, false);

        assertEquals(Arrays.asList(metricCode("ALL"), metricCode("BASE-LOW"), metricCode("BASE-HIGH")),
                codesForPrefix(repository.list("BASE", false)));
        assertEquals(Arrays.asList(metricCode("DISABLED"), metricCode("ALL"),
                        metricCode("BASE-LOW"), metricCode("BASE-HIGH")),
                codesForPrefix(repository.list("BASE", true)));
        assertEquals(Arrays.asList(metricCode("PRODUCT"), metricCode("ALL"),
                        metricCode("BASE-LOW"), metricCode("BASE-HIGH")),
                codesForPrefix(repository.list(null, false)));
        assertEquals(Arrays.asList(metricCode("PRODUCT"), metricCode("ALL")),
                codesForPrefix(repository.list("PRODUCT_LINE", false)));
    }

    private void save(String suffix, String perspective, int displayOrder, boolean enabled) {
        repository.save(new MetricDefinition(null, suffix, suffix, perspective, "元",
                "FORMULA_" + suffix, displayOrder, enabled, null, "postgres-test", LocalDateTime.now()));
    }

    private List<String> codesForPrefix(List<MetricDefinition> definitions) {
        List<String> codes = new ArrayList<String>();
        for (MetricDefinition definition : definitions) {
            if (definition.getMetricCode().startsWith(runPrefix)) {
                codes.add(definition.getMetricCode());
            }
        }
        return codes;
    }

    private String metricCode(String suffix) {
        return runPrefix + "-" + suffix;
    }

    private void deleteTestMetrics() {
        if (jdbc != null && runPrefix != null) {
            jdbc.update("DELETE FROM " + METRIC_TABLE + " WHERE metric_code LIKE ?", runPrefix + "%");
        }
    }

    private static String environment(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value;
    }
}
