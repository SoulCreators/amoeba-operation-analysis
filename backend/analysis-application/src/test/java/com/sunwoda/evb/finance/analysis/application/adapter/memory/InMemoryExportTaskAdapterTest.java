package com.sunwoda.evb.finance.analysis.application.adapter.memory;

import com.sunwoda.evb.finance.analysis.application.model.ExportJob;
import com.sunwoda.evb.finance.analysis.application.model.ExportJobStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryExportTaskAdapterTest {
    @Test
    void sameUserAndRequestKeyReturnTheSameJob() {
        InMemoryExportTaskAdapter adapter = new InMemoryExportTaskAdapter();

        ExportJob first = adapter.create("U001", "REQ-001", "result.csv", "text/csv");
        ExportJob second = adapter.create("U001", "REQ-001", "result.csv", "text/csv");

        assertEquals(first.getJobId(), second.getJobId());
    }

    @Test
    void statusAndDownloadAreBoundToTheCreatingUser() {
        InMemoryExportTaskAdapter adapter = new InMemoryExportTaskAdapter();
        ExportJob job = adapter.create("U001", "REQ-002", "result.csv", "text/csv");

        assertTrue(adapter.status(job.getJobId(), "U001").isPresent());
        assertFalse(adapter.status(job.getJobId(), "U002").isPresent());
        assertFalse(adapter.download(job.getJobId(), "U001").isPresent());

        adapter.complete(job.getJobId(), "result.csv", "text/csv", new byte[] {1, 2, 3});

        assertTrue(adapter.download(job.getJobId(), "U001").isPresent());
        assertFalse(adapter.download(job.getJobId(), "U002").isPresent());
        assertArrayEquals(new byte[] {1, 2, 3},
                adapter.download(job.getJobId(), "U001").get().getContent());
    }

    @Test
    void queuedJobsHaveAFiniteLifetime() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 19, 12, 0);
        ExportJob job = ExportJob.queued("EXP-001", "U001", "REQ-003",
                "result.csv", "text/csv", createdAt);

        assertTrue(job.getExpiresAt().isAfter(createdAt));
        assertTrue(job.isExpired(job.getExpiresAt()));
        assertEquals(ExportJobStatus.EXPIRED,
                job.expired(job.getExpiresAt()).getStatus());
    }
}
