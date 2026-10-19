package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.application.adapter.db.DbExportTaskUnavailableAdapter;
import com.sunwoda.evb.finance.analysis.application.adapter.memory.InMemoryExportTaskAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Profile boundary checks for the export task adapters.
 *
 * <p>The JDBC implementation is checked reflectively so the test keeps the
 * application-layer profile boundary independent from infrastructure wiring.</p>
 */
class ExportTaskDbProfileIsolationContractTest {

    @Test
    void memoryExportAdapterIsExcludedByDbProfile() {
        Profile profile = InMemoryExportTaskAdapter.class.getAnnotation(Profile.class);

        assertNotNull(profile);
        assertArrayEquals(new String[] {"!db"}, profile.value());
    }

    @Test
    void unavailableDbExportAdapterIsExplicitlyDbOnlyAsFallbackBoundary() {
        Profile profile = DbExportTaskUnavailableAdapter.class.getAnnotation(Profile.class);

        assertNotNull(profile);
        assertArrayEquals(new String[] {"db"}, profile.value());
    }

    @Test
    void jdbcExportRepositoryMustBeDbOnlyWhenItIsIntroduced() throws Exception {
        Class<?> repository = Class.forName(
                "com.sunwoda.evb.finance.analysis.infrastructure.jdbc.JdbcExportTaskRepository");
        Profile profile = repository.getAnnotation(Profile.class);

        assertNotNull(profile);
        assertEquals(1, profile.value().length);
        assertEquals("db", profile.value()[0]);
    }
}
