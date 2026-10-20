package com.sunwoda.evb.finance.amoeba.worker;

import com.sunwoda.evb.finance.analysis.infrastructure.filesystem.FileSystemExportFileStorageAdapter;
import com.sunwoda.evb.finance.analysis.infrastructure.jdbc.JdbcExportCleanupRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real PostgreSQL + filesystem contract for the existing cleanup worker flow.
 *
 * <p>The database fixture is an existing {@code flow.export_task} row from
 * the cleanup perspective: the worker only advances its expired lifecycle,
 * claims it, deletes its user-scoped bytes, and records the cleanup result.
 * No orphan scan or production permission is involved.</p>
 */
class GOV006WorkerIntegrationContractTest {
    private static DataSource dataSource;
    private static JdbcTemplate jdbc;

    @TempDir
    Path storageRoot;

    private FileSystemExportFileStorageAdapter storage;
    private String jobId;
    private String userId;
    private LocalDateTime now;

    @BeforeAll
    static void connectToConfiguredPostgresAndVerifyCleanupSchema() {
        String url = requiredNonBlankEnvironment("AMOEBA_DB_URL");
        String username = requiredNonBlankEnvironment("AMOEBA_DB_USERNAME");
        String password = requiredEnvironment("AMOEBA_DB_PASSWORD");

        dataSource = new DriverManagerDataSource(url, username, password);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("SELECT job_id, status, expires_at, cleanup_status, "
                + "cleanup_attempts, cleanup_claim_token, cleanup_completed_at "
                + "FROM flow.export_task LIMIT 0");
    }

    @BeforeEach
    void insertExpiredSuccessfulTaskAndStoreItsBytes() {
        String runToken = UUID.randomUUID().toString().replace("-", "");
        jobId = "GOV006-WI-" + runToken;
        userId = "GOV006-WI-USER-" + runToken;
        String requestKey = "GOV006-WI-REQUEST-" + runToken;
        now = LocalDateTime.now().withNano(0);
        storage = new FileSystemExportFileStorageAdapter(storageRoot.toString());

        jdbc.update("INSERT INTO flow.export_task "
                        + "(job_id, user_id, request_key, file_name, content_type, "
                        + "status, created_at, updated_at, finished_at, expires_at, "
                        + "cleanup_status, cleanup_attempts) "
                        + "VALUES (?, ?, ?, ?, ?, 'SUCCEEDED', ?, ?, ?, ?, "
                        + "'NOT_REQUIRED', 0)",
                jobId, userId, requestKey, "expired.csv", "text/csv",
                Timestamp.valueOf(now.minusMinutes(30)),
                Timestamp.valueOf(now.minusMinutes(2)),
                Timestamp.valueOf(now.minusMinutes(29)),
                Timestamp.valueOf(now.minusMinutes(1)));

        storage.save(jobId, userId, "expired.csv", "text/csv",
                "expired-export".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @AfterEach
    void removeFixture() {
        if (jdbc != null && jobId != null) {
            jdbc.update("DELETE FROM flow.export_task WHERE job_id = ?", jobId);
        }
    }

    @Test
    void cleansExpiredFlowTaskBytesAndPersistsDeletedState() {
        assertTrue(storage.open(jobId, userId).isPresent());

        ExportCleanupWorker worker = new ExportCleanupWorker(
                new JdbcExportCleanupRepository(dataSource),
                storage,
                new LoggingExportCleanupAlert());

        worker.runOnce(now);

        assertFalse(storage.open(jobId, userId).isPresent());
        assertEquals("EXPIRED", taskColumn("status"));
        assertEquals("DELETED", taskColumn("cleanup_status"));
        assertEquals(1, taskColumn("cleanup_attempts"));
        assertNull(taskColumn("cleanup_claim_token"));
        assertNotNull(taskColumn("cleanup_completed_at"));
    }

    private Object taskColumn(String column) {
        return jdbc.queryForObject("SELECT " + column
                        + " FROM flow.export_task WHERE job_id = ?",
                Object.class, jobId);
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
}
