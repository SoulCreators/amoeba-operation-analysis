package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class JdbcExportCleanupRepositoryBoundaryContractTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 19, 10, 0);

    @Test
    void rejectsInvalidArgumentsBeforeOpeningJdbcOperations() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcExportCleanupRepository repository = new JdbcExportCleanupRepository(jdbc);

        assertThrows(IllegalArgumentException.class, () -> repository.expireDueTasks(null));
        assertThrows(IllegalArgumentException.class,
                () -> repository.claimExpired(" ", 10, NOW, NOW.plusMinutes(5)));
        assertThrows(IllegalArgumentException.class,
                () -> repository.claimExpired("worker-1", 10, null, NOW.plusMinutes(5)));
        assertThrows(IllegalArgumentException.class,
                () -> repository.claimExpired("worker-1", 10, NOW, null));
        assertThrows(IllegalArgumentException.class,
                () -> repository.claimExpired("worker-1", 10, NOW, NOW.minusSeconds(1)));
        assertThrows(IllegalArgumentException.class,
                () -> repository.markCleanupSucceeded(" ", "claim-1", NOW));
        assertThrows(IllegalArgumentException.class,
                () -> repository.markCleanupSucceeded("job-1", " ", NOW));
        assertThrows(IllegalArgumentException.class,
                () -> repository.markCleanupRetry("job-1", "claim-1", " ",
                        NOW.plusMinutes(5), NOW, false));

        verifyNoInteractions(jdbc);
    }

    @Test
    void nonPositiveClaimLimitReturnsEmptyWithoutQueryingDatabase() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcExportCleanupRepository repository = new JdbcExportCleanupRepository(jdbc);

        assertEquals(Collections.emptyList(), repository.claimExpired(
                "worker-1", 0, NOW, NOW.plusMinutes(5)));
        assertEquals(Collections.emptyList(), repository.claimExpired(
                "worker-1", -1, NOW, NOW.plusMinutes(5)));

        verifyNoInteractions(jdbc);
    }
}
