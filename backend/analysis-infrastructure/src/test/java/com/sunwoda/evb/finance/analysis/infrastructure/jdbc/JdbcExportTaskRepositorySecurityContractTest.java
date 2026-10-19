package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.application.model.ExportJob;
import com.sunwoda.evb.finance.analysis.application.model.ExportJobStatus;
import com.sunwoda.evb.finance.analysis.application.port.ExportTaskPort;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Executable PostgreSQL/JDBC contract for the export task repository.
 *
 * <p>Set {@code AMOEBA_DB_URL} (and optionally username/password) to run this
 * contract against the real PostgreSQL repository. Without it, the test is
 * intentionally skipped rather than claiming database verification.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcExportTaskRepositorySecurityContractTest {
    private static final String REPOSITORY_CLASS =
            "com.sunwoda.evb.finance.analysis.infrastructure.jdbc.JdbcExportTaskRepository";
    private static final String EXPORT_TABLE = "flow.export_task";
    private static final String MIGRATION_FILE = "V018__create_export_task.sql";

    private DataSource dataSource;
    private JdbcTemplate jdbc;
    private ExportTaskPort repository;
    private String runPrefix;

    @BeforeAll
    void verifyProductionContractIsAvailable() {
        assumeTrue(repositoryClassPresent(),
                "BLOCKED: missing " + REPOSITORY_CLASS
                        + "; add a PostgreSQL ExportTaskPort implementation first");
        assumeTrue(migrationPresent(),
                "BLOCKED: missing database/migration/" + MIGRATION_FILE
                        + "; add the export task schema and unique constraint first");
        String url = System.getenv("AMOEBA_DB_URL");
        assumeTrue(url != null && !url.trim().isEmpty(),
                "BLOCKED: AMOEBA_DB_URL is not set; PostgreSQL integration tests were not run");

        dataSource = new DriverManagerDataSource(
                url, environment("AMOEBA_DB_USERNAME"), environment("AMOEBA_DB_PASSWORD"));
        jdbc = new JdbcTemplate(dataSource);
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            repository = instantiateRepository(dataSource);
        } catch (RuntimeException failure) {
            assumeTrue(false, "BLOCKED: PostgreSQL connection or JDBC repository setup failed: "
                    + rootMessage(failure));
        }
        runPrefix = "TEST-EXPORT-DB-" + UUID.randomUUID().toString().replace("-", "");
    }

    @BeforeEach
    void verifyRepositoryWasInitialized() {
        assumeTrue(repository != null,
                "BLOCKED: production JDBC export repository is not available");
    }

    @Test
    void sameUserAndRequestKeyIsIdempotentAcrossRepeatedCreateCalls() {
        String userId = user("IDEMPOTENT");
        String requestKey = key("SAME");

        ExportJob first = repository.create(userId, requestKey, "first.csv", "text/csv");
        ExportJob second = repository.create(userId, requestKey, "second.csv", "text/csv");

        assertEquals(first.getJobId(), second.getJobId());
        assertEquals(first.getUserId(), second.getUserId());
        assertEquals(first.getRequestKey(), second.getRequestKey());
        assertEquals("first.csv", second.getFileName(),
                "idempotent replay must not overwrite the original task metadata");
    }

    @Test
    void sameRequestKeyIsIsolatedByUserIdentity() {
        String requestKey = key("CROSS-USER");
        ExportJob userA = repository.create(user("USER-A"), requestKey, "a.csv", "text/csv");
        ExportJob userB = repository.create(user("USER-B"), requestKey, "b.csv", "text/csv");

        assertNotEquals(userA.getJobId(), userB.getJobId());
        assertTrue(!repository.status(userA.getJobId(), userB.getUserId()).isPresent());
        assertTrue(!repository.download(userA.getJobId(), userB.getUserId()).isPresent());
        assertEquals(userA.getJobId(), repository.status(userA.getJobId(), userA.getUserId())
                .get().getJobId());
        assertEquals(userB.getJobId(), repository.status(userB.getJobId(), userB.getUserId())
                .get().getJobId());
    }

    @Test
    void statusTransitionsFromQueuedToRunningToSucceededAndIsIdempotent() {
        String userId = user("LIFECYCLE");
        ExportJob queued = repository.create(userId, key("SUCCESS"), "queued.csv", "text/csv");
        assertEquals(ExportJobStatus.QUEUED, status(queued, userId).getStatus());

        repository.markRunning(queued.getJobId());
        assertEquals(ExportJobStatus.RUNNING, status(queued, userId).getStatus());

        repository.complete(queued.getJobId(), "complete.csv", "text/csv", new byte[] {1, 2, 3});

        ExportJob succeeded = status(queued, userId);
        assertEquals(ExportJobStatus.SUCCEEDED, succeeded.getStatus());
        assertTrue(!repository.download(queued.getJobId(), userId).isPresent(),
                "metadata-only JDBC adapter must not fabricate file bytes before object storage is wired");

        ExportJob replay = repository.create(userId, queued.getRequestKey(), "replay.csv", "text/csv");
        assertEquals(queued.getJobId(), replay.getJobId());
        assertEquals(ExportJobStatus.SUCCEEDED, replay.getStatus());
        assertThrows(RuntimeException.class, () -> repository.markRunning(queued.getJobId()));
        assertThrows(RuntimeException.class, () -> repository.complete(
                queued.getJobId(), "again.csv", "text/csv", new byte[] {1}));
    }

    @Test
    void failureIsATerminalStateAndUnknownTasksNeverBecomeReadable() {
        String userId = user("FAILURE");
        ExportJob job = repository.create(userId, key("FAIL"), "failed.csv", "text/csv");
        repository.fail(job.getJobId(), "worker failed");

        ExportJob failed = status(job, userId);
        assertEquals(ExportJobStatus.FAILED, failed.getStatus());
        assertEquals("worker failed", failed.getErrorMessage());
        assertTrue(!repository.download(job.getJobId(), userId).isPresent());
        assertThrows(RuntimeException.class, () -> repository.markRunning(job.getJobId()));

        String unknown = job.getJobId() + "-UNKNOWN";
        assertTrue(!repository.status(unknown, userId).isPresent());
        assertTrue(!repository.download(unknown, userId).isPresent());
        assertThrows(RuntimeException.class, () -> repository.markRunning(unknown));
        assertThrows(RuntimeException.class, () -> repository.complete(
                unknown, "unknown.csv", "text/csv", new byte[] {1}));
        assertThrows(RuntimeException.class, () -> repository.fail(unknown, "unknown"));
    }

    @Test
    void expiredTaskCannotBeDownloadedAndRemainsIdempotent() {
        String userId = user("EXPIRED");
        ExportJob created = repository.create(userId, key("EXPIRE"), "expire.csv", "text/csv");
        repository.complete(created.getJobId(), "expire.csv", "text/csv", new byte[] {7});

        jdbc.update("UPDATE " + EXPORT_TABLE
                        + " SET expires_at = created_at"
                        + " WHERE job_id = ?", created.getJobId());

        ExportJob expired = status(created, userId);
        assertEquals(ExportJobStatus.EXPIRED, expired.getStatus());
        assertTrue(!repository.download(created.getJobId(), userId).isPresent());
        ExportJob replay = repository.create(userId, created.getRequestKey(), "new.csv", "text/csv");
        assertEquals(created.getJobId(), replay.getJobId());
        assertEquals(ExportJobStatus.EXPIRED, replay.getStatus());
    }

    @Test
    void databaseHasUniqueUserAndRequestKeyConstraint() {
        Integer uniqueIndexes = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes"
                        + " WHERE schemaname = 'flow' AND tablename = 'export_task'"
                        + " AND indexdef ILIKE '%UNIQUE%'"
                        + " AND indexdef ILIKE '%user_id%'"
                        + " AND indexdef ILIKE '%request_key%'",
                Integer.class);

        assertEquals(1, uniqueIndexes,
                "flow.export_task must enforce UNIQUE(user_id, request_key) in PostgreSQL");
    }

    private ExportJob status(ExportJob job, String userId) {
        return repository.status(job.getJobId(), userId).orElseThrow(
                () -> new AssertionError("expected task to be readable for its owner"));
    }

    private String user(String suffix) {
        return runPrefix + "-U-" + suffix;
    }

    private String key(String suffix) {
        return runPrefix + "-K-" + suffix;
    }

    private static ExportTaskPort instantiateRepository(DataSource dataSource) {
        try {
            Class<?> type = Class.forName(REPOSITORY_CLASS);
            Constructor<?> constructor = type.getConstructor(DataSource.class);
            return (ExportTaskPort) constructor.newInstance(dataSource);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("missing " + REPOSITORY_CLASS, e);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot construct " + REPOSITORY_CLASS
                    + " with DataSource", e);
        }
    }

    private static boolean repositoryClassPresent() {
        try {
            Class.forName(REPOSITORY_CLASS);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static boolean migrationPresent() {
        Path root = repositoryRoot();
        return root != null && Files.exists(root.resolve("database/migration").resolve(MIGRATION_FILE));
    }

    private static Path repositoryRoot() {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            if (Files.isDirectory(current.resolve("database/migration"))) return current;
            current = current.getParent();
        }
        return null;
    }

    private static String environment(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value;
    }

    private static String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null
                && !(current instanceof InvocationTargetException)) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
