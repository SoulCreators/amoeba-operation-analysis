package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.application.model.ExportFile;
import com.sunwoda.evb.finance.analysis.application.model.ExportJob;
import com.sunwoda.evb.finance.analysis.application.model.ExportJobStatus;
import com.sunwoda.evb.finance.analysis.application.port.ExportFileStoragePort;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Contract for the JDBC metadata-only boundary: task success must not turn
 * metadata into downloadable bytes when no storage port is wired.
 */
class JdbcExportFileStorageBoundaryContractTest {

    @Test
    void successfulMetadataWithoutStoragePortStillCannotLeakFileBytes() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ExportJob succeeded = ExportJob.restore(
                "EXP-001", "U001", "REQ-001", "result.csv", "text/csv",
                LocalDateTime.of(2026, 10, 19, 10, 0),
                LocalDateTime.of(2026, 10, 19, 10, 1),
                LocalDateTime.of(2026, 10, 19, 10, 1),
                LocalDateTime.of(2026, 10, 19, 11, 0),
                ExportJobStatus.SUCCEEDED, null);
        when(jdbc.query(anyString(), any(RowMapper.class), eq("EXP-001"), eq("U001")))
                .thenReturn(Collections.singletonList(succeeded));

        JdbcExportTaskRepository repository = new JdbcExportTaskRepository(jdbc);

        assertFalse(repository.download("EXP-001", "U001").isPresent());
        assertFalse(ExportFileStoragePort.class.isAssignableFrom(
                JdbcExportTaskRepository.class),
                "metadata-only JDBC repository must not silently become a file store");
    }

    @Test
    void unknownOrWrongOwnerMetadataCannotBeReadAsAFile() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(RowMapper.class), eq("EXP-UNKNOWN"), eq("U001")))
                .thenReturn(Collections.<ExportFile>emptyList());

        JdbcExportTaskRepository repository = new JdbcExportTaskRepository(jdbc);

        assertFalse(repository.download("EXP-UNKNOWN", "U001").isPresent());
        assertFalse(repository.download("EXP-UNKNOWN", "U002").isPresent());
    }
}
