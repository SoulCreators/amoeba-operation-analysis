package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.application.model.ExportFile;
import com.sunwoda.evb.finance.analysis.application.model.ExportJob;
import com.sunwoda.evb.finance.analysis.application.port.ExportFileStoragePort;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcExportTaskRepositoryFileStorageTest {
    private static final String JOB_ID = "EXP-STORAGE-001";
    private static final String USER_ID = "U-STORAGE";

    @Test
    void completeSavesByJobIdAndDownloadReadsTheSameFileKey() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RecordingFileStorage storage = new RecordingFileStorage();
        ExportJob queued = queuedJob();
        ExportJob succeeded = queued.succeeded("result.csv", "text/csv", LocalDateTime.now());

        when(jdbc.query(anyString(), any(RowMapper.class), eq(JOB_ID)))
                .thenReturn(Collections.singletonList(queued));
        when(jdbc.update(anyString(), eq("result.csv"), eq("text/csv"), any(), any(), eq(JOB_ID)))
                .thenReturn(1);

        JdbcExportTaskRepository repository = new JdbcExportTaskRepository(jdbc, storage);
        byte[] content = new byte[] {1, 2, 3};
        repository.complete(JOB_ID, "result.csv", "text/csv", content);

        assertEquals(JOB_ID, storage.savedFileKey);
        assertEquals(USER_ID, storage.savedUserId);
        assertEquals("result.csv", storage.savedFileName);
        assertEquals("text/csv", storage.savedContentType);
        assertArrayEquals(content, storage.savedContent);

        when(jdbc.query(anyString(), any(RowMapper.class), eq(JOB_ID), eq(USER_ID)))
                .thenReturn(Collections.singletonList(succeeded));
        ExportFile file = new ExportFile("result.csv", "text/csv", content);
        storage.openedFile = file;

        Optional<ExportFile> downloaded = repository.download(" " + JOB_ID + " ",
                " " + USER_ID + " ");

        assertTrue(downloaded.isPresent());
        assertEquals(file, downloaded.get());
        assertEquals(JOB_ID, storage.openedFileKey);
        assertEquals(USER_ID, storage.openedUserId);
    }

    @Test
    void metadataOnlyRepositoryDoesNotDownload() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ExportJob succeeded = queuedJob().succeeded("result.csv", "text/csv", LocalDateTime.now());
        when(jdbc.query(anyString(), any(RowMapper.class), eq(JOB_ID), eq(USER_ID)))
                .thenReturn(Collections.singletonList(succeeded));

        JdbcExportTaskRepository repository = new JdbcExportTaskRepository(jdbc);

        assertFalse(repository.download(JOB_ID, USER_ID).isPresent());
    }

    @Test
    void unauthorizedDownloadDoesNotTouchFileStorage() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RecordingFileStorage storage = new RecordingFileStorage();
        when(jdbc.query(anyString(), any(RowMapper.class), eq(JOB_ID), eq("OTHER-USER")))
                .thenReturn(Collections.emptyList());

        JdbcExportTaskRepository repository = new JdbcExportTaskRepository(jdbc, storage);

        assertFalse(repository.download(JOB_ID, "OTHER-USER").isPresent());
        verify(jdbc).query(anyString(), any(RowMapper.class), eq(JOB_ID), eq("OTHER-USER"));
        assertFalse(storage.openCalled);
    }

    private static ExportJob queuedJob() {
        return ExportJob.queued(JOB_ID, USER_ID, "REQUEST-001", "requested.csv",
                "text/csv", LocalDateTime.now());
    }

    private static final class RecordingFileStorage implements ExportFileStoragePort {
        private String savedFileKey;
        private String savedUserId;
        private String savedFileName;
        private String savedContentType;
        private byte[] savedContent;
        private String openedFileKey;
        private String openedUserId;
        private ExportFile openedFile;
        private boolean openCalled;

        @Override
        public void save(String fileKey, String userId, String fileName,
                         String contentType, byte[] content) {
            this.savedFileKey = fileKey;
            this.savedUserId = userId;
            this.savedFileName = fileName;
            this.savedContentType = contentType;
            this.savedContent = content.clone();
        }

        @Override
        public Optional<ExportFile> open(String fileKey, String userId) {
            this.openCalled = true;
            this.openedFileKey = fileKey;
            this.openedUserId = userId;
            return Optional.ofNullable(openedFile);
        }
    }
}
