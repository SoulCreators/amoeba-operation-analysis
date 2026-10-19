package com.sunwoda.evb.finance.analysis.infrastructure.memory;

import com.sunwoda.evb.finance.analysis.application.adapter.memory.InMemoryExportTaskAdapter;
import com.sunwoda.evb.finance.analysis.application.model.ExportFile;
import com.sunwoda.evb.finance.analysis.application.model.ExportJob;
import com.sunwoda.evb.finance.analysis.application.model.ExportJobStatus;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Contract tests for the currently available export storage/download adapter.
 *
 * <p>The file key is treated as an opaque identifier. This is important for
 * storage implementations that may later map keys to a controlled filesystem
 * root: path-looking input must never become an alias for another key.</p>
 */
class ExportFileStorageAndDownloadContractTest {

    @Test
    void pathTraversalLookingKeyDoesNotAliasAnotherStoredKey() {
        InMemoryExportTaskAdapter adapter = new InMemoryExportTaskAdapter();
        adapter.save("exports/user-a/result.csv", "U001", "result.csv", "text/csv",
                new byte[] {1, 2, 3});

        assertFalse(adapter.open("exports/user-a/../user-a/result.csv", "U001").isPresent());
        assertFalse(adapter.open("../exports/user-a/result.csv", "U001").isPresent());
        assertArrayEquals(new byte[] {1, 2, 3},
                adapter.open("exports/user-a/result.csv", "U001").get().getContent());
    }

    @Test
    void differentUserAndUnknownFileKeyCannotReadStoredContent() {
        InMemoryExportTaskAdapter adapter = new InMemoryExportTaskAdapter();
        adapter.save("file-key-001", "U001", "result.csv", "text/csv",
                new byte[] {7, 8, 9});

        assertFalse(adapter.open("file-key-001", "U002").isPresent());
        assertFalse(adapter.open("file-key-unknown", "U001").isPresent());
        assertFalse(adapter.open("file-key-001", "").isPresent());
    }

    @Test
    void saveAndOpenUseDefensiveCopiesAndSameKeyOverwriteIsExplicit() {
        InMemoryExportTaskAdapter adapter = new InMemoryExportTaskAdapter();
        byte[] firstContent = new byte[] {1, 2, 3};

        adapter.save("file-key-002", "U001", "first.csv", "text/csv", firstContent);
        firstContent[0] = 99;
        assertArrayEquals(new byte[] {1, 2, 3},
                adapter.open("file-key-002", "U001").get().getContent());

        adapter.save("file-key-002", "U001", "second.csv", "text/plain",
                new byte[] {4, 5});
        ExportFile overwritten = adapter.open("file-key-002", "U001").get();
        assertEquals("second.csv", overwritten.getFileName());
        assertEquals("text/plain", overwritten.getContentType());

        byte[] returnedContent = overwritten.getContent();
        returnedContent[0] = 88;
        assertArrayEquals(new byte[] {4, 5},
                adapter.open("file-key-002", "U001").get().getContent());
    }

    @Test
    void downloadDoesNotExposeStoredBytesUntilTaskSucceeds() {
        InMemoryExportTaskAdapter adapter = new InMemoryExportTaskAdapter();
        ExportJob job = adapter.create("U001", "REQ-DOWNLOAD", "result.csv", "text/csv");

        // A file may exist independently of task metadata; lifecycle state is
        // still the authorization gate for the download operation.
        adapter.save(job.getJobId(), "U001", "result.csv", "text/csv",
                new byte[] {1, 2, 3});
        assertFalse(adapter.download(job.getJobId(), "U001").isPresent());

        adapter.markRunning(job.getJobId());
        assertFalse(adapter.download(job.getJobId(), "U001").isPresent());

        adapter.complete(job.getJobId(), "result.csv", "text/csv", new byte[] {4, 5, 6});
        assertArrayEquals(new byte[] {4, 5, 6},
                adapter.download(job.getJobId(), "U001").get().getContent());
    }

    @Test
    void expiredTaskCannotDownloadEvenWhenItsStoredBytesRemain() throws Exception {
        InMemoryExportTaskAdapter adapter = new InMemoryExportTaskAdapter();
        ExportJob job = adapter.create("U001", "REQ-EXPIRED-DOWNLOAD", "result.csv", "text/csv");
        adapter.complete(job.getJobId(), "result.csv", "text/csv", new byte[] {1, 2, 3});

        expire(adapter, job.getJobId());

        assertEquals(ExportJobStatus.EXPIRED,
                adapter.status(job.getJobId(), "U001").get().getStatus());
        assertFalse(adapter.download(job.getJobId(), "U001").isPresent());
    }

    @SuppressWarnings("unchecked")
    private static void expire(InMemoryExportTaskAdapter adapter, String jobId) throws Exception {
        Field jobsField = InMemoryExportTaskAdapter.class.getDeclaredField("jobs");
        jobsField.setAccessible(true);
        Map<String, ExportJob> jobs = (Map<String, ExportJob>) jobsField.get(adapter);
        ExportJob job = jobs.get(jobId);
        jobs.put(jobId, job.expired(job.getExpiresAt()));
    }
}
