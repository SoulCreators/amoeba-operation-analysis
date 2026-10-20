package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.application.model.ExportJob;
import com.sunwoda.evb.finance.analysis.application.model.ExportJobStatus;
import com.sunwoda.evb.finance.analysis.application.port.ExportFileStoragePort;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JdbcExportTaskRepositoryExpiryCompletionContractTest {
    private static final String JOB_ID = "EXP-EXPIRED-COMPLETE-001";
    private static final String USER_ID = "U-EXPIRED-COMPLETE";

    @Test
    void storageBackedCompletionRejectsExpiredTaskBeforeSavingBytes() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ExportFileStoragePort storage = mock(ExportFileStoragePort.class);
        when(jdbc.query(anyString(), any(RowMapper.class), eq(JOB_ID)))
                .thenReturn(Collections.singletonList(expiredJob(ExportJobStatus.QUEUED)));

        JdbcExportTaskRepository repository = new JdbcExportTaskRepository(jdbc, storage);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> repository.complete(JOB_ID, "result.csv", "text/csv", new byte[] {1}));

        assertTrue(failure.getMessage().contains("已过期"));
        verifyNoInteractions(storage);
        verify(jdbc, never()).update(contains("status = 'SUCCEEDED'"), any(), any(), any(),
                any(), any(), eq(JOB_ID), any());
    }

    @Test
    void metadataOnlyCompletionKeepsDatabaseExpiryPredicate() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(contains("status = 'SUCCEEDED'"), any(), any(), any(), any(), any(),
                eq(JOB_ID), any())).thenReturn(0);
        when(jdbc.query(anyString(), any(RowMapper.class), eq(JOB_ID)))
                .thenReturn(Collections.singletonList(expiredJob(ExportJobStatus.RUNNING)));

        JdbcExportTaskRepository repository = new JdbcExportTaskRepository(jdbc);

        assertThrows(IllegalStateException.class,
                () -> repository.complete(JOB_ID, "result.csv", "text/csv", new byte[] {2}));

        verify(jdbc).update(contains("status = 'SUCCEEDED'"), any(), any(), any(), any(),
                any(), eq(JOB_ID), any());
        verify(jdbc).update(contains("expires_at > ?"), any(), any(), any(), any(), any(),
                eq(JOB_ID), any());
    }

    private static ExportJob expiredJob(ExportJobStatus status) {
        LocalDateTime createdAt = LocalDateTime.now().minusMinutes(30);
        return ExportJob.restore(JOB_ID, USER_ID, "REQ-EXPIRED", "queued.csv", "text/csv",
                createdAt, createdAt, null, createdAt.plusMinutes(15), status, null);
    }
}
