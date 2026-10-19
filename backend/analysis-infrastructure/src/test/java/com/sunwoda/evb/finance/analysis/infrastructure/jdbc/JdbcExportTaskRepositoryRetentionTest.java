package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.application.model.ExportJob;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcExportTaskRepositoryRetentionTest {
    private static final String JOB_ID = "EXP-RETENTION-001";
    private static final String USER_ID = "U-RETENTION";

    @Test
    void completionSetsTheSuccessfulTaskRetentionToTwentyFourHours() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 19, 9, 0);
        ExportJob queued = ExportJob.queued(JOB_ID, USER_ID, "REQUEST-001",
                "requested.csv", "text/csv", createdAt);
        when(jdbc.query(anyString(), any(RowMapper.class), eq(JOB_ID)))
                .thenReturn(Collections.singletonList(queued));
        when(jdbc.update(contains("expires_at = ?"), any(), any(), any(), any(), any(),
                eq(JOB_ID), any())).thenReturn(1);

        new JdbcExportTaskRepository(jdbc).complete(JOB_ID, "result.csv", "text/csv",
                new byte[] {1, 2, 3});

        verify(jdbc).update(contains("status = 'SUCCEEDED'"), any(), any(), any(), any(),
                any(), eq(JOB_ID), any());
    }
}
