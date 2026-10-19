package com.sunwoda.evb.finance.analysis.application.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExportJobTest {
    @Test
    void restorePreservesPersistedLifecycleTimestamps() {
        LocalDateTime created = LocalDateTime.of(2026, 10, 19, 12, 0);
        LocalDateTime updated = created.plusMinutes(3);
        LocalDateTime finished = created.plusMinutes(2);
        LocalDateTime expires = created.plusHours(1);

        ExportJob job = ExportJob.restore("EXP-1", "U001", "REQ-1", "result.csv",
                "text/csv", created, updated, finished, expires,
                ExportJobStatus.SUCCEEDED, null);

        assertEquals(created, job.getCreatedAt());
        assertEquals(updated, job.getUpdatedAt());
        assertEquals(finished, job.getFinishedAt());
        assertEquals(expires, job.getExpiresAt());
        assertEquals(ExportJobStatus.SUCCEEDED, job.getStatus());
    }

    @Test
    void successStartsASeparateTwentyFourHourRetentionWindow() {
        LocalDateTime created = LocalDateTime.of(2026, 10, 19, 12, 0);
        LocalDateTime finished = created.plusMinutes(4);

        ExportJob job = ExportJob.queued("EXP-2", "U001", "REQ-2", "result.csv",
                "text/csv", created).succeeded("result.csv", "text/csv", finished);

        assertEquals(finished, job.getFinishedAt());
        assertEquals(finished.plusHours(24), job.getExpiresAt());
        assertEquals(created.plusMinutes(15),
                ExportJob.queued("EXP-3", "U001", "REQ-3", "result.csv",
                        "text/csv", created).getExpiresAt());
    }
}
