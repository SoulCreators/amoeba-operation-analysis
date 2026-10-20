package com.sunwoda.evb.finance.analysis.application.adapter.memory;

import com.sunwoda.evb.finance.analysis.application.model.ExportJob;
import com.sunwoda.evb.finance.analysis.application.model.ExportJobStatus;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryExportTaskAdapterExpiryCompletionContractTest {
    @Test
    void expiredQueuedTaskCannotBeCompletedOrPublished() throws Exception {
        InMemoryExportTaskAdapter adapter = new InMemoryExportTaskAdapter();
        ExportJob created = adapter.create("U-EXPIRY", "REQ-QUEUED", "result.csv", "text/csv");
        ExportJob expiredQueued = expiredQueued(created);
        putJob(adapter, expiredQueued);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> adapter.complete(created.getJobId(), "result.csv", "text/csv",
                        new byte[] {1, 2, 3}));

        assertTrue(failure.getMessage().contains("已过期"));
        assertFalse(adapter.download(created.getJobId(), "U-EXPIRY").isPresent());
    }

    @Test
    void expiredRunningTaskCannotBeCompletedOrPublished() throws Exception {
        InMemoryExportTaskAdapter adapter = new InMemoryExportTaskAdapter();
        ExportJob created = adapter.create("U-EXPIRY", "REQ-RUNNING", "result.csv", "text/csv");
        LocalDateTime createdAt = created.getCreatedAt().minusMinutes(30);
        ExportJob expiredRunning = ExportJob.restore(created.getJobId(), created.getUserId(),
                created.getRequestKey(), created.getFileName(), created.getContentType(),
                createdAt, createdAt, null, createdAt.plusMinutes(15),
                ExportJobStatus.RUNNING, null);
        putJob(adapter, expiredRunning);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> adapter.complete(created.getJobId(), "result.csv", "text/csv",
                        new byte[] {4, 5, 6}));

        assertTrue(failure.getMessage().contains("已过期"));
        assertFalse(adapter.download(created.getJobId(), "U-EXPIRY").isPresent());
    }

    private static ExportJob expiredQueued(ExportJob job) {
        LocalDateTime createdAt = job.getCreatedAt().minusMinutes(30);
        return ExportJob.restore(job.getJobId(), job.getUserId(), job.getRequestKey(),
                job.getFileName(), job.getContentType(), createdAt, createdAt, null,
                createdAt.plusMinutes(15), ExportJobStatus.QUEUED, null);
    }

    @SuppressWarnings("unchecked")
    private static void putJob(InMemoryExportTaskAdapter adapter, ExportJob job)
            throws ReflectiveOperationException {
        Field jobsField = InMemoryExportTaskAdapter.class.getDeclaredField("jobs");
        jobsField.setAccessible(true);
        Map<String, ExportJob> jobs = (Map<String, ExportJob>) jobsField.get(adapter);
        jobs.put(job.getJobId(), job);
    }
}
