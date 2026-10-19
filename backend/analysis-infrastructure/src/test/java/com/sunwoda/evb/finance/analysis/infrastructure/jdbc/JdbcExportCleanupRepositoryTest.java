package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.application.model.ExportCleanupTask;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcExportCleanupRepositoryTest {
    private static final String JOB_ID = "EXP-CLEANUP-001";
    private static final String CLAIM_TOKEN = "claim-token-001";

    @Test
    void expireDueTasksQueuesSuccessfulFiles() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(contains("status IN ('QUEUED', 'RUNNING', 'SUCCEEDED')"),
                any(), any(), any())).thenReturn(3);

        new JdbcExportCleanupRepository(jdbc).expireDueTasks(
                LocalDateTime.of(2026, 10, 19, 10, 0));

        verify(jdbc).update(contains("THEN 'PENDING' ELSE cleanup_status END"),
                any(), any(), any());
    }

    @Test
    void claimUsesWorkerLeaseAndSkipLocked() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ExportCleanupTask task = new ExportCleanupTask(JOB_ID, "U-CLEANUP", JOB_ID,
                1, CLAIM_TOKEN, LocalDateTime.of(2026, 10, 19, 9, 0));
        when(jdbc.query(contains("FOR UPDATE SKIP LOCKED"), any(RowMapper.class),
                any(), any(), any(), eq(10), any(), any()))
                .thenReturn(Collections.singletonList(task));

        List<ExportCleanupTask> claimed = new JdbcExportCleanupRepository(jdbc).claimExpired(
                "worker-1", 10, LocalDateTime.of(2026, 10, 19, 10, 0),
                LocalDateTime.of(2026, 10, 19, 10, 5));

        assertEquals(1, claimed.size());
        assertEquals(CLAIM_TOKEN, claimed.get(0).getClaimToken());
        verify(jdbc).query(contains("cleanup_attempts < 3"), any(RowMapper.class),
                any(), any(), any(), eq(10), any(), any());
    }

    @Test
    void completionAndRetryAreConditionalOnClaimToken() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(contains("cleanup_status = 'DELETED'"),
                any(), any(), eq(JOB_ID), eq(CLAIM_TOKEN))).thenReturn(1);
        when(jdbc.update(contains("cleanup_status = CASE WHEN cleanup_attempts >= 3"),
                eq(false), eq(false), any(), eq("not found"), any(), eq(JOB_ID),
                eq(CLAIM_TOKEN))).thenReturn(1);

        JdbcExportCleanupRepository repository = new JdbcExportCleanupRepository(jdbc);
        LocalDateTime now = LocalDateTime.of(2026, 10, 19, 10, 0);
        repository.markCleanupSucceeded(JOB_ID, CLAIM_TOKEN, now);
        repository.markCleanupRetry(JOB_ID, CLAIM_TOKEN, "not found",
                now.plusMinutes(5), now, false);

        verify(jdbc).update(contains("cleanup_claim_token = ?"),
                any(), any(), eq(JOB_ID), eq(CLAIM_TOKEN));
        verify(jdbc).update(contains("cleanup_claim_token = ?"), eq(false), eq(false),
                any(), eq("not found"), any(), eq(JOB_ID), eq(CLAIM_TOKEN));
    }
}
