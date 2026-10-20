package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.application.model.ExportJob;
import com.sunwoda.evb.finance.analysis.application.port.ExportFileStoragePort;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GOV-002 文件发布失败补偿 / exactly-once 边界契约测试。
 *
 * <p>本次修改：仅验证文件已保存与任务状态更新之间的即时补偿边界，
 * 不覆盖正式导出格式、权限、财务公式、结果版本、保留策略、审计或 S3/OSS。</p>
 */
class JdbcExportTaskRepositoryFilePublicationCompensationTest {
    private static final String JOB_ID = "EXP-PUBLICATION-COMPENSATION-001";
    private static final String USER_ID = "U-PUBLICATION-COMPENSATION";
    private static final String FILE_NAME = "result.csv";
    private static final String CONTENT_TYPE = "text/csv";
    private static final byte[] CONTENT = new byte[] {1, 2, 3};

    @Test
    void successfulStatusPublicationDoesNotCompensateTheSavedFile() {
        // 本次修改：验证正常发布只保存一次并更新成功，不应误删已发布文件。
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ExportFileStoragePort storage = mock(ExportFileStoragePort.class);
        ExportJob queued = queuedJob();
        when(jdbc.query(anyString(), any(RowMapper.class), eq(JOB_ID)))
                .thenReturn(Collections.singletonList(queued));
        when(jdbc.update(contains("status = 'SUCCEEDED'"), eq(FILE_NAME), eq(CONTENT_TYPE),
                any(), any(), any(), eq(JOB_ID), any())).thenReturn(1);

        JdbcExportTaskRepository repository = new JdbcExportTaskRepository(jdbc, storage);

        repository.complete(JOB_ID, FILE_NAME, CONTENT_TYPE, CONTENT);

        verify(storage).save(JOB_ID, USER_ID, FILE_NAME, CONTENT_TYPE, CONTENT);
        verify(storage, never()).delete(anyString(), anyString());
    }

    @Test
    void rejectedStatusPublicationDeletesTheSavedFileExactlyOnce() {
        // 本次修改：验证文件已保存但状态条件更新失败时，按同一任务键和用户域
        // 即时补偿删除一次。
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ExportFileStoragePort storage = mock(ExportFileStoragePort.class);
        ExportJob queued = queuedJob();
        when(jdbc.query(anyString(), any(RowMapper.class), eq(JOB_ID)))
                .thenReturn(Arrays.asList(queued, queued));
        when(jdbc.update(contains("status = 'SUCCEEDED'"), eq(FILE_NAME), eq(CONTENT_TYPE),
                any(), any(), any(), eq(JOB_ID), any())).thenReturn(0);

        JdbcExportTaskRepository repository = new JdbcExportTaskRepository(jdbc, storage);

        assertThrows(IllegalStateException.class,
                () -> repository.complete(JOB_ID, FILE_NAME, CONTENT_TYPE, CONTENT));

        InOrder order = inOrder(storage, jdbc);
        order.verify(storage).save(JOB_ID, USER_ID, FILE_NAME, CONTENT_TYPE, CONTENT);
        order.verify(jdbc).update(contains("status = 'SUCCEEDED'"), eq(FILE_NAME),
                eq(CONTENT_TYPE), any(), any(), any(), eq(JOB_ID), any());
        order.verify(storage).delete(JOB_ID, USER_ID);
        verify(storage, times(1)).delete(JOB_ID, USER_ID);
    }

    @Test
    void rejectedStatusPublicationKeepsLifecycleFailureWhenCompensationFails() {
        // 本次修改：验证条件更新失败且删除失败时，生命周期异常仍是主异常，删除异常仅作 suppressed。
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ExportFileStoragePort storage = mock(ExportFileStoragePort.class);
        ExportJob queued = queuedJob();
        RuntimeException deleteFailure = new IllegalStateException("delete failed");
        when(jdbc.query(anyString(), any(RowMapper.class), eq(JOB_ID)))
                .thenReturn(Collections.singletonList(queued), Collections.singletonList(queued));
        when(jdbc.update(contains("status = 'SUCCEEDED'"), eq(FILE_NAME), eq(CONTENT_TYPE),
                any(), any(), any(), eq(JOB_ID), any())).thenReturn(0);
        doThrow(deleteFailure).when(storage).delete(JOB_ID, USER_ID);

        JdbcExportTaskRepository repository = new JdbcExportTaskRepository(jdbc, storage);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> repository.complete(JOB_ID, FILE_NAME, CONTENT_TYPE, CONTENT));

        assertEquals("导出任务不可完成: " + JOB_ID, thrown.getMessage());
        assertSame(deleteFailure, thrown.getSuppressed()[0]);
    }

    @Test
    void jdbcStatusUpdateFailureKeepsOriginalFailureAndCompensates() {
        // 本次修改：验证 JDBC 状态更新抛错时仍按复核结果补偿，并保留原始异常。
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ExportFileStoragePort storage = mock(ExportFileStoragePort.class);
        ExportJob queued = queuedJob();
        RuntimeException updateFailure = new IllegalStateException("jdbc update failed");
        when(jdbc.query(anyString(), any(RowMapper.class), eq(JOB_ID)))
                .thenReturn(Collections.singletonList(queued), Collections.singletonList(queued));
        when(jdbc.update(contains("status = 'SUCCEEDED'"), eq(FILE_NAME), eq(CONTENT_TYPE),
                any(), any(), any(), eq(JOB_ID), any())).thenThrow(updateFailure);

        JdbcExportTaskRepository repository = new JdbcExportTaskRepository(jdbc, storage);

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> repository.complete(JOB_ID, FILE_NAME, CONTENT_TYPE, CONTENT));

        assertSame(updateFailure, thrown);
        verify(storage).delete(JOB_ID, USER_ID);
    }

    @Test
    void jdbcStatusUpdateFailureKeepsOriginalFailureWhenCompensationFails() {
        // 本次修改：验证 JDBC 异常和删除异常同时出现时，原始 JDBC 异常仍为主异常。
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ExportFileStoragePort storage = mock(ExportFileStoragePort.class);
        ExportJob queued = queuedJob();
        RuntimeException updateFailure = new IllegalStateException("jdbc update failed");
        RuntimeException deleteFailure = new IllegalStateException("delete failed");
        when(jdbc.query(anyString(), any(RowMapper.class), eq(JOB_ID)))
                .thenReturn(Collections.singletonList(queued), Collections.singletonList(queued));
        when(jdbc.update(contains("status = 'SUCCEEDED'"), eq(FILE_NAME), eq(CONTENT_TYPE),
                any(), any(), any(), eq(JOB_ID), any())).thenThrow(updateFailure);
        doThrow(deleteFailure).when(storage).delete(JOB_ID, USER_ID);

        JdbcExportTaskRepository repository = new JdbcExportTaskRepository(jdbc, storage);

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> repository.complete(JOB_ID, FILE_NAME, CONTENT_TYPE, CONTENT));

        assertSame(updateFailure, thrown);
        assertSame(deleteFailure, thrown.getSuppressed()[0]);
    }

    @Test
    void statusUpdateExceptionKeepsFileWhenConcurrentPublicationAlreadySucceeded() {
        // 本次修改：验证状态更新异常后若并发发布已经成功，不能误删仍被成功任务
        // 引用的文件。
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ExportFileStoragePort storage = mock(ExportFileStoragePort.class);
        ExportJob queued = queuedJob();
        ExportJob succeeded = queued.succeeded(FILE_NAME, CONTENT_TYPE, LocalDateTime.now());
        RuntimeException stateUpdateFailure = new IllegalStateException("state update failed");
        when(jdbc.query(anyString(), any(RowMapper.class), eq(JOB_ID)))
                .thenReturn(Collections.singletonList(queued), Collections.singletonList(succeeded));
        when(jdbc.update(contains("status = 'SUCCEEDED'"), eq(FILE_NAME), eq(CONTENT_TYPE),
                any(), any(), any(), eq(JOB_ID), any())).thenThrow(stateUpdateFailure);

        JdbcExportTaskRepository repository = new JdbcExportTaskRepository(jdbc, storage);

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> repository.complete(JOB_ID, FILE_NAME, CONTENT_TYPE, CONTENT));

        assertSame(stateUpdateFailure, thrown);
        verify(storage).save(JOB_ID, USER_ID, FILE_NAME, CONTENT_TYPE, CONTENT);
        verify(storage, never()).delete(anyString(), anyString());
    }

    @Test
    void failedFileSaveDoesNotUpdateTaskOrCompensate() {
        // 本次修改：验证文件未成功保存时不进入状态更新，也不执行删除补偿。
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ExportFileStoragePort storage = mock(ExportFileStoragePort.class);
        ExportJob queued = queuedJob();
        RuntimeException saveFailure = new IllegalStateException("file save failed");
        when(jdbc.query(anyString(), any(RowMapper.class), eq(JOB_ID)))
                .thenReturn(Collections.singletonList(queued));
        doThrow(saveFailure).when(storage)
                .save(JOB_ID, USER_ID, FILE_NAME, CONTENT_TYPE, CONTENT);

        JdbcExportTaskRepository repository = new JdbcExportTaskRepository(jdbc, storage);

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> repository.complete(JOB_ID, FILE_NAME, CONTENT_TYPE, CONTENT));

        assertSame(saveFailure, thrown);
        verify(jdbc, never()).update(contains("status = 'SUCCEEDED'"), any(), any(), any(),
                any(), any(), eq(JOB_ID), any());
        verify(storage, never()).delete(anyString(), anyString());
    }

    private static ExportJob queuedJob() {
        return ExportJob.queued(JOB_ID, USER_ID, "REQUEST-PUBLICATION-COMPENSATION-001",
                "requested.csv", CONTENT_TYPE, LocalDateTime.now());
    }
}
