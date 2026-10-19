package com.sunwoda.evb.finance.analysis.application.adapter.memory;

import com.sunwoda.evb.finance.analysis.application.model.ExportJob;
import com.sunwoda.evb.finance.analysis.application.model.ExportJobStatus;
import com.sunwoda.evb.finance.analysis.application.model.ExportFormat;
import com.sunwoda.evb.finance.analysis.application.model.ExportTaskSnapshot;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResultStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryExportTaskAdapterTest {
    @Test
    void persistsSnapshotForWorkerAndKeepsItOnIdempotentReplay() {
        InMemoryExportTaskAdapter adapter = new InMemoryExportTaskAdapter();
        ExportTaskSnapshot snapshot = new ExportTaskSnapshot("B-001", "R3",
                AnalysisPerspective.BASE, CalculationResultStatus.PUBLISHED,
                ExportFormat.XLSX, Collections.singleton("NANCHANG"),
                Collections.<String>emptySet());

        ExportJob first = adapter.create("U001", "REQ-SNAPSHOT", snapshot);
        ExportJob replay = adapter.create("U001", "REQ-SNAPSHOT",
                new ExportTaskSnapshot("B-CHANGED", "R9", AnalysisPerspective.BASE,
                        CalculationResultStatus.PUBLISHED, ExportFormat.CSV,
                        Collections.singleton("WUHAN"), Collections.<String>emptySet()));

        assertEquals(first.getJobId(), replay.getJobId());
        assertEquals(snapshot.getBatchNo(), adapter.snapshot(first.getJobId()).get().getBatchNo());
        assertEquals(snapshot.getSummaryScopes(),
                adapter.snapshot(first.getJobId()).get().getSummaryScopes());
    }
    @Test
    void sameUserAndRequestKeyReturnTheSameJob() {
        InMemoryExportTaskAdapter adapter = new InMemoryExportTaskAdapter();

        ExportJob first = adapter.create("U001", "REQ-001", "result.csv", "text/csv");
        ExportJob second = adapter.create("U001", "REQ-001", "result.csv", "text/csv");

        assertEquals(first.getJobId(), second.getJobId());
    }

    @Test
    void rejectsMissingOrBlankRequestKey() {
        InMemoryExportTaskAdapter adapter = new InMemoryExportTaskAdapter();

        assertThrows(IllegalArgumentException.class,
                () -> adapter.create("U001", null, "result.csv", "text/csv"));
        assertThrows(IllegalArgumentException.class,
                () -> adapter.create("U001", "   ", "result.csv", "text/csv"));
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
    void unknownTaskCannotBeReadOrMutated() {
        InMemoryExportTaskAdapter adapter = new InMemoryExportTaskAdapter();

        assertFalse(adapter.status("EXP-UNKNOWN", "U001").isPresent());
        assertFalse(adapter.download("EXP-UNKNOWN", "U001").isPresent());
        assertThrows(IllegalArgumentException.class,
                () -> adapter.markRunning("EXP-UNKNOWN"));
        assertThrows(IllegalArgumentException.class,
                () -> adapter.complete("EXP-UNKNOWN", "result.csv", "text/csv",
                        new byte[] {1}));
        assertThrows(IllegalArgumentException.class,
                () -> adapter.fail("EXP-UNKNOWN", "not found"));
    }

    @Test
    void downloadRemainsUnavailableUntilTaskSucceeds() {
        InMemoryExportTaskAdapter adapter = new InMemoryExportTaskAdapter();
        ExportJob job = adapter.create("U001", "REQ-INCOMPLETE", "result.csv", "text/csv");

        assertEquals(ExportJobStatus.QUEUED,
                adapter.status(job.getJobId(), "U001").get().getStatus());
        assertFalse(adapter.download(job.getJobId(), "U001").isPresent());

        adapter.markRunning(job.getJobId());

        assertEquals(ExportJobStatus.RUNNING,
                adapter.status(job.getJobId(), "U001").get().getStatus());
        assertFalse(adapter.download(job.getJobId(), "U001").isPresent());
    }

    @Test
    void expiredTaskCannotBeDownloaded() throws Exception {
        InMemoryExportTaskAdapter adapter = new InMemoryExportTaskAdapter();
        ExportJob job = adapter.create("U001", "REQ-EXPIRED", "result.csv", "text/csv");
        adapter.complete(job.getJobId(), "result.csv", "text/csv", new byte[] {1, 2, 3});

        java.lang.reflect.Field jobsField = InMemoryExportTaskAdapter.class
                .getDeclaredField("jobs");
        jobsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, ExportJob> jobs =
                (java.util.Map<String, ExportJob>) jobsField.get(adapter);
        ExportJob completed = jobs.get(job.getJobId());
        jobs.put(job.getJobId(), completed.expired(completed.getExpiresAt()));

        assertEquals(ExportJobStatus.EXPIRED,
                adapter.status(job.getJobId(), "U001").get().getStatus());
        assertFalse(adapter.download(job.getJobId(), "U001").isPresent());
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

    @Test
    void memoryAdapterIsNotActiveForDbProfile() {
        org.springframework.context.annotation.Profile profile =
                InMemoryExportTaskAdapter.class.getAnnotation(
                        org.springframework.context.annotation.Profile.class);

        assertTrue(profile != null);
        assertArrayEquals(new String[] {"!db"}, profile.value());
    }
}
