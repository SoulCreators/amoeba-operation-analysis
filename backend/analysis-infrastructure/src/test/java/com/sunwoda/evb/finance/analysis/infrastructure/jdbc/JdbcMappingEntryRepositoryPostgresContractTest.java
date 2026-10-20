package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.MappingEntry;
import com.sunwoda.evb.finance.analysis.domain.model.MappingType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Opt-in PostgreSQL contract for mapping persistence and catalogue filtering. */
class JdbcMappingEntryRepositoryPostgresContractTest {
    private static final MappingType TYPE = MappingType.SALES_PROJECT;
    private static final MappingType OTHER_TYPE = MappingType.INVENTORY;
    private static DataSource dataSource;
    private static JdbcTemplate jdbc;
    private static JdbcMappingEntryRepository mappings;
    private static String keyPrefix;

    @BeforeAll
    static void connectToConfiguredPostgres() {
        String url = System.getenv("AMOEBA_DB_URL");
        assumeTrue(url != null && !url.trim().isEmpty(),
                "BLOCKED: AMOEBA_DB_URL is not set; mapping PostgreSQL contract was not run");

        dataSource = new DriverManagerDataSource(
                url, environment("AMOEBA_DB_USERNAME"), environment("AMOEBA_DB_PASSWORD"));
        jdbc = new JdbcTemplate(dataSource);
        try {
            jdbc.execute("SELECT id, mapping_type, match_key, mapped_value, enabled, updated_by, updated_at "
                    + "FROM cfg.mapping_entry LIMIT 0");
        } catch (RuntimeException failure) {
            assumeTrue(false, "BLOCKED: mapping PostgreSQL schema is not available: "
                    + failure.getMessage());
        }

        mappings = new JdbcMappingEntryRepository(dataSource);
        keyPrefix = "JDBC-MAPPING-CONTRACT-" + UUID.randomUUID().toString().replace("-", "");
    }

    @AfterAll
    static void cleanup() {
        if (jdbc == null || keyPrefix == null) return;
        jdbc.update("DELETE FROM cfg.mapping_entry WHERE match_key LIKE ?", keyPrefix + "%");
    }

    @Test
    void persistsUpdatesFindsAndListsMappingsWithExpectedFilteringAndOrdering() {
        String firstKey = keyPrefix + "-A";
        String secondKey = keyPrefix + "-B";
        String thirdKey = keyPrefix + "-C";

        MappingEntry first = mappings.save(entry(firstKey, "产品线-A", true));
        MappingEntry second = mappings.save(entry(secondKey, "产品线-B", true));
        MappingEntry third = mappings.save(entry(thirdKey, "产品线-C", true));
        MappingEntry sameKeyDifferentType = mappings.save(
                new MappingEntry(null, OTHER_TYPE, firstKey, "库存-A", true,
                        "mapping-contract-update", null));

        assertNotNull(first.getId());
        assertNotNull(first.getUpdatedAt());
        assertEquals(firstKey, first.getMatchKey());
        assertEquals("产品线-A", first.getMappedValue());
        assertTrue(first.isEnabled());

        MappingEntry updatedSecond = mappings.save(entry(secondKey, "产品线-B-更新", false));
        assertEquals(second.getId(), updatedSecond.getId());
        assertEquals(secondKey, updatedSecond.getMatchKey());
        assertEquals("产品线-B-更新", updatedSecond.getMappedValue());
        assertTrue(!updatedSecond.isEnabled());
        assertEquals("mapping-contract-update", updatedSecond.getUpdatedBy());
        assertNotNull(updatedSecond.getUpdatedAt());

        assertNotNull(sameKeyDifferentType.getId());
        assertEquals(firstKey, mappings.find(OTHER_TYPE, firstKey)
                .orElseThrow(() -> new AssertionError("different mapping type was not found"))
                .getMatchKey());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM cfg.mapping_entry WHERE mapping_type = ? AND match_key = ?",
                Integer.class, TYPE.name(), secondKey));

        MappingEntry found = mappings.find(TYPE, secondKey).orElseThrow(
                () -> new AssertionError("updated mapping was not found"));
        assertEquals(updatedSecond.getId(), found.getId());
        assertEquals("产品线-B-更新", found.getMappedValue());
        assertTrue(!found.isEnabled());

        List<MappingEntry> enabledOnly = mappings.list(TYPE, false);
        assertEquals(Arrays.asList(firstKey, thirdKey), keysWithPrefix(enabledOnly));
        assertTrue(enabledOnly.stream().allMatch(MappingEntry::isEnabled));

        List<MappingEntry> includingDisabled = mappings.list(TYPE, true);
        List<MappingEntry> scopedIncludingDisabled = entriesWithPrefix(includingDisabled);
        assertEquals(Arrays.asList(firstKey, secondKey, thirdKey), keys(scopedIncludingDisabled));
        assertEquals("产品线-B-更新", scopedIncludingDisabled.get(1).getMappedValue());
        assertTrue(scopedIncludingDisabled.stream().anyMatch(entry -> !entry.isEnabled()));
    }

    private static MappingEntry entry(String matchKey, String mappedValue, boolean enabled) {
        String updatedBy = "mapping-contract-update";
        return new MappingEntry(null, TYPE, matchKey, mappedValue, enabled, updatedBy, null);
    }

    private static List<String> keys(List<MappingEntry> entries) {
        return entries.stream().map(MappingEntry::getMatchKey).collect(java.util.stream.Collectors.toList());
    }

    private List<String> keysWithPrefix(List<MappingEntry> entries) {
        return keys(entriesWithPrefix(entries));
    }

    private List<MappingEntry> entriesWithPrefix(List<MappingEntry> entries) {
        return entries.stream()
                .filter(entry -> entry.getMatchKey().startsWith(keyPrefix))
                .collect(java.util.stream.Collectors.toList());
    }

    private static String environment(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value;
    }
}
