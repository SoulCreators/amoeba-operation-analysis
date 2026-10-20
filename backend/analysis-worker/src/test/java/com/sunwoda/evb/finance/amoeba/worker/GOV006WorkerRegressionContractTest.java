package com.sunwoda.evb.finance.amoeba.worker;

import com.sunwoda.evb.finance.analysis.application.model.ExportJob;
import com.sunwoda.evb.finance.analysis.application.model.ExportCleanupTask;
import com.sunwoda.evb.finance.analysis.application.port.ExportCleanupAlertPort;
import com.sunwoda.evb.finance.analysis.infrastructure.filesystem.FileSystemExportFileStorageAdapter;
import com.sunwoda.evb.finance.analysis.infrastructure.jdbc.JdbcExportCleanupRepository;
import com.sunwoda.evb.finance.analysis.infrastructure.jdbc.JdbcExportTaskRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression contract for the real PostgreSQL/JDBC/filesystem cleanup flow.
 *
 * <p>This class deliberately exercises only the existing worker boundary. It
 * does not add an orphan scanner, alter retention semantics, or mutate any
 * production configuration.</p>
 */
class GOV006WorkerRegressionContractTest {
    private static DataSource dataSource;
    private static JdbcTemplate jdbc;
    private static JdbcExportTaskRepository tasks;

    @TempDir
    Path storageRoot;

    private FileSystemExportFileStorageAdapter storage;
    private RecordingAlert alert;
    private String runToken;
    private final List<String> createdJobIds = new ArrayList<String>();

    @BeforeAll
    static void connectToConfiguredPostgresAndVerifyCleanupSchema() {
        String url = requiredNonBlankEnvironment("AMOEBA_DB_URL");
        String username = requiredNonBlankEnvironment("AMOEBA_DB_USERNAME");
        String password = requiredEnvironment("AMOEBA_DB_PASSWORD");

        dataSource = new DriverManagerDataSource(url, username, password);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("SELECT job_id, user_id, status, expires_at, cleanup_status, "
                + "cleanup_attempts, cleanup_claim_token, cleanup_completed_at "
                + "FROM flow.export_task LIMIT 0");
        tasks = new JdbcExportTaskRepository(dataSource);
    }

    @BeforeEach
    void initializeIsolatedFixture() {
        runToken = UUID.randomUUID().toString().replace("-", "");
        storage = new FileSystemExportFileStorageAdapter(storageRoot.toString());
        alert = new RecordingAlert();
    }

    @AfterEach
    void removeDatabaseFixtures() {
        if (jdbc == null) {
            return;
        }
        for (String jobId : createdJobIds) {
            jdbc.update("DELETE FROM flow.export_task WHERE job_id = ?", jobId);
        }
        createdJobIds.clear();
    }

    @Test
    void expiredTaskWithoutFileIsDeletedWithoutAlert() {
        LocalDateTime now = now();
        ExportJob job = createSuccessfulTask("ORP002-MISSING");
        makeExpired(job.getJobId(), now);

        worker().runOnce(now);

        assertEquals("EXPIRED", taskTextColumn("status", job.getJobId()));
        assertEquals("DELETED", taskTextColumn("cleanup_status", job.getJobId()));
        assertEquals(1, taskIntColumn("cleanup_attempts", job.getJobId()));
        assertNotNull(taskTimestampColumn("cleanup_completed_at", job.getJobId()));
        assertEquals(0, alert.count());
    }

    @Test
    void futureTaskWithFileIsNotClaimedOrDeleted() {
        LocalDateTime now = now();
        ExportJob job = createSuccessfulTask("ORP003-FUTURE");
        storage.save(job.getJobId(), job.getUserId(), job.getFileName(),
                job.getContentType(), bytes("future-export"));

        worker().runOnce(now);

        assertEquals("SUCCEEDED", taskTextColumn("status", job.getJobId()));
        assertEquals("NOT_REQUIRED", taskTextColumn("cleanup_status", job.getJobId()));
        assertEquals(0, taskIntColumn("cleanup_attempts", job.getJobId()));
        assertTrue(storage.open(job.getJobId(), job.getUserId()).isPresent());
        assertEquals(0, alert.count());
    }

    @Test
    void secondCleanupPassAfterDeletionIsStableAndDoesNotRealert() {
        LocalDateTime now = now();
        ExportJob job = createSuccessfulTask("ORP005-RERUN");
        storage.save(job.getJobId(), job.getUserId(), job.getFileName(),
                job.getContentType(), bytes("rerun-export"));
        makeExpired(job.getJobId(), now);

        ExportCleanupWorker worker = worker();
        worker.runOnce(now);
        worker.runOnce(now.plusMinutes(1));

        assertFalse(storage.open(job.getJobId(), job.getUserId()).isPresent());
        assertEquals("DELETED", taskTextColumn("cleanup_status", job.getJobId()));
        assertEquals(1, taskIntColumn("cleanup_attempts", job.getJobId()));
        assertEquals(0, alert.count());
    }

    @Test
    void cleanupDeletesOnlyTheExpiredUsersScopedFile() {
        LocalDateTime now = now();
        ExportJob job = createSuccessfulTask("ORP010-SCOPE");
        String otherUser = "GOV006-ORP010-OTHER-" + runToken;
        storage.save(job.getJobId(), job.getUserId(), job.getFileName(),
                job.getContentType(), bytes("owner-a-export"));
        storage.save(job.getJobId(), otherUser, job.getFileName(),
                job.getContentType(), bytes("owner-b-export"));
        makeExpired(job.getJobId(), now);

        worker().runOnce(now);

        assertFalse(storage.open(job.getJobId(), job.getUserId()).isPresent());
        assertTrue(storage.open(job.getJobId(), otherUser).isPresent());
        assertEquals("DELETED", taskTextColumn("cleanup_status", job.getJobId()));
        assertEquals(1, taskIntColumn("cleanup_attempts", job.getJobId()));
        assertEquals(0, alert.count());
    }

    private ExportCleanupWorker worker() {
        return new ExportCleanupWorker(new JdbcExportCleanupRepository(dataSource),
                storage, alert);
    }

    private ExportJob createSuccessfulTask(String suffix) {
        String userId = "GOV006-ORP-USER-" + runToken + "-" + suffix;
        String requestKey = "GOV006-ORP-REQUEST-" + runToken + "-" + suffix;
        String fileName = "gov006-orp-" + runToken + "-"
                + suffix.toLowerCase() + ".csv";
        ExportJob job = tasks.create(userId, requestKey, fileName, "text/csv");
        createdJobIds.add(job.getJobId());
        tasks.complete(job.getJobId(), fileName, "text/csv", bytes(suffix));
        return job;
    }

    private void makeExpired(String jobId, LocalDateTime now) {
        jdbc.update("UPDATE flow.export_task SET created_at = ?, expires_at = ?, "
                        + "updated_at = ? WHERE job_id = ?",
                Timestamp.valueOf(now.minusMinutes(2)),
                Timestamp.valueOf(now.minusMinutes(1)),
                Timestamp.valueOf(now), jobId);
    }

    private String taskTextColumn(String column, String jobId) {
        return jdbc.queryForObject("SELECT " + column
                        + " FROM flow.export_task WHERE job_id = ?",
                String.class, jobId);
    }

    private int taskIntColumn(String column, String jobId) {
        return jdbc.queryForObject("SELECT " + column
                        + " FROM flow.export_task WHERE job_id = ?",
                Integer.class, jobId);
    }

    private Timestamp taskTimestampColumn(String column, String jobId) {
        return jdbc.queryForObject("SELECT " + column
                        + " FROM flow.export_task WHERE job_id = ?",
                Timestamp.class, jobId);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static LocalDateTime now() {
        return LocalDateTime.now().withNano(0);
    }

    private static String requiredNonBlankEnvironment(String name) {
        String value = requiredEnvironment(name);
        if (value.trim().isEmpty()) {
            throw new IllegalStateException(name
                    + " must not be blank for the real PostgreSQL contract test");
        }
        return value;
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null) {
            throw new IllegalStateException(name
                    + " must be set for the real PostgreSQL contract test");
        }
        return value;
    }

    private static final class RecordingAlert implements ExportCleanupAlertPort {
        private final List<String> jobIds = new ArrayList<String>();

        @Override
        public void alert(ExportCleanupTask task, String errorMessage) {
            jobIds.add(task.getJobId());
        }

        private int count() {
            return jobIds.size();
        }
    }
}
