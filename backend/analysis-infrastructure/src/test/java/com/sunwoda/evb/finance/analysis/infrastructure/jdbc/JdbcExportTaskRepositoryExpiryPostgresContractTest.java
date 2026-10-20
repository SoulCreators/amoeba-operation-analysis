package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.application.model.ExportFile;
import com.sunwoda.evb.finance.analysis.application.model.ExportJob;
import com.sunwoda.evb.finance.analysis.application.model.ExportJobStatus;
import com.sunwoda.evb.finance.analysis.application.port.ExportFileStoragePort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * GOV-006-EXPIRY-GUARD-001 的真实 PostgreSQL 过期完成门禁契约。
 *
 * <p>本次修改只新增数据库契约测试，覆盖过期前启动、到期启动阻断、
 * 队列和运行中任务完成阻断，以及过期后的状态查询和下载稳定性；
 * 并补充并发迟到完成回归；不修改生产代码、迁移、权限、Worker 或财务公式。</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcExportTaskRepositoryExpiryPostgresContractTest {
    private static final String EXPORT_TABLE = "flow.export_task";
    private static final String MIGRATION_FILE = "V018__create_export_task.sql";

    private DataSource dataSource;
    private JdbcTemplate jdbc;
    private JdbcExportTaskRepository repository;
    private String runPrefix;
    private final List<String> createdJobIds = new ArrayList<String>();

    @BeforeAll
    void connectToConfiguredPostgres() {
        String url = System.getenv("AMOEBA_DB_URL");
        assumeTrue(url != null && !url.trim().isEmpty(),
                "BLOCKED: AMOEBA_DB_URL is not set; PostgreSQL integration tests were skipped");
        assumeTrue(migrationPresent(),
                "BLOCKED: missing database/migration/" + MIGRATION_FILE);

        dataSource = new DriverManagerDataSource(url,
                environment("AMOEBA_DB_USERNAME"), environment("AMOEBA_DB_PASSWORD"));
        jdbc = new JdbcTemplate(dataSource);
        try {
            jdbc.execute("SELECT job_id, status, expires_at, cleanup_status "
                    + "FROM flow.export_task LIMIT 0");
            repository = new JdbcExportTaskRepository(dataSource);
        } catch (RuntimeException failure) {
            assumeTrue(false, "BLOCKED: PostgreSQL export task schema is unavailable: "
                    + rootMessage(failure));
        }
    }

    @BeforeEach
    void initializeIsolatedFixture() {
        assumeTrue(repository != null,
                "BLOCKED: PostgreSQL export task repository is unavailable");
        runPrefix = "G6" + UUID.randomUUID().toString().replace("-", "");
    }

    @AfterEach
    void removeDatabaseFixtures() {
        if (jdbc == null) {
            return;
        }
        for (String jobId : createdJobIds) {
            jdbc.update("DELETE FROM " + EXPORT_TABLE + " WHERE job_id = ?", jobId);
        }
        createdJobIds.clear();
    }

    @Test
    void taskCanBeMarkedRunningBeforeExpiry() {
        ExportJob job = createQueued("BEFORE-RUNNING");

        repository.markRunning(job.getJobId());

        ExportJob current = status(job);
        assertEquals(ExportJobStatus.RUNNING, current.getStatus());
    }

    @Test
    void expiryBoundaryBlocksQueuedStartAndRepeatedStatusIsExpired() {
        ExportJob atBoundary = createQueued("AT-BOUNDARY");
        setExpiryToDatabaseCurrentTimestamp(atBoundary.getJobId(), ExportJobStatus.QUEUED);

        assertThrows(IllegalStateException.class,
                () -> repository.markRunning(atBoundary.getJobId()));
        assertEquals(ExportJobStatus.EXPIRED, status(atBoundary).getStatus());
        assertEquals(ExportJobStatus.EXPIRED, status(atBoundary).getStatus());
        assertEquals(ExportJobStatus.EXPIRED, databaseStatus(atBoundary.getJobId()));

        ExportJob past = createQueued("PAST-BOUNDARY");
        setLifecycle(past.getJobId(), ExportJobStatus.QUEUED,
                LocalDateTime.now().minusMinutes(1));
        assertThrows(IllegalStateException.class,
                () -> repository.markRunning(past.getJobId()));
        assertEquals(ExportJobStatus.EXPIRED, status(past).getStatus());
        assertEquals(ExportJobStatus.EXPIRED, status(past).getStatus());
    }

    private void setExpiryToDatabaseCurrentTimestamp(String jobId, ExportJobStatus status) {
        jdbc.update("UPDATE " + EXPORT_TABLE + " SET status = ?, "
                        + "created_at = CURRENT_TIMESTAMP - INTERVAL '1 minute', "
                        + "updated_at = CURRENT_TIMESTAMP, finished_at = NULL, "
                        + "error_message = NULL, expires_at = CURRENT_TIMESTAMP "
                        + "WHERE job_id = ?",
                status.name(), jobId);
    }

    @Test
    void expiredQueuedTaskCannotComplete() {
        ExportJob job = createQueued("EXPIRED-QUEUED-COMPLETE");
        setLifecycle(job.getJobId(), ExportJobStatus.QUEUED,
                LocalDateTime.now().minusMinutes(1));

        assertThrows(IllegalStateException.class,
                () -> repository.complete(job.getJobId(), "expired.csv", "text/csv",
                        new byte[] {1}));

        assertEquals(ExportJobStatus.EXPIRED, status(job).getStatus());
        assertEquals(ExportJobStatus.EXPIRED, databaseStatus(job.getJobId()));
    }

    @Test
    void expiredRunningTaskCannotComplete() {
        ExportJob job = createQueued("EXPIRED-RUNNING-COMPLETE");
        repository.markRunning(job.getJobId());
        setLifecycle(job.getJobId(), ExportJobStatus.RUNNING,
                LocalDateTime.now().minusMinutes(1));

        assertThrows(IllegalStateException.class,
                () -> repository.complete(job.getJobId(), "expired.csv", "text/csv",
                        new byte[] {2}));

        assertEquals(ExportJobStatus.EXPIRED, status(job).getStatus());
        assertEquals(ExportJobStatus.EXPIRED, databaseStatus(job.getJobId()));
    }

    @Test
    void expiredQueryAndDownloadResultsRemainStableAcrossRepeatedCalls() {
        RecordingFileStorage storage = new RecordingFileStorage();
        JdbcExportTaskRepository storageRepository =
                new JdbcExportTaskRepository(dataSource, storage);
        ExportJob job = createQueued("EXPIRED-REPEATABLE-READ");
        storageRepository.complete(job.getJobId(), "result.csv", "text/csv",
                new byte[] {3, 4, 5});
        setLifecycle(job.getJobId(), ExportJobStatus.SUCCEEDED,
                LocalDateTime.now().minusMinutes(1));

        ExportJob firstStatus = storageRepository.status(job.getJobId(), job.getUserId())
                .orElseThrow(() -> new AssertionError("过期任务第一次查询应可读"));
        ExportJob secondStatus = storageRepository.status(job.getJobId(), job.getUserId())
                .orElseThrow(() -> new AssertionError("过期任务第二次查询应可读"));
        Optional<ExportFile> firstDownload =
                storageRepository.download(job.getJobId(), job.getUserId());
        Optional<ExportFile> secondDownload =
                storageRepository.download(job.getJobId(), job.getUserId());

        assertEquals(ExportJobStatus.EXPIRED, firstStatus.getStatus());
        assertEquals(ExportJobStatus.EXPIRED, secondStatus.getStatus());
        assertFalse(firstDownload.isPresent());
        assertFalse(secondDownload.isPresent());
        assertEquals(ExportJobStatus.EXPIRED, databaseStatus(job.getJobId()));
        assertEquals(0, storage.openCount);
    }

    @Test
    void concurrentExpiryAndLateCompletionCannotPublishSucceededOrPersistFile()
            throws Exception {
        RecordingFileStorage storage = new RecordingFileStorage();
        JdbcExportTaskRepository expiryRepository = new JdbcExportTaskRepository(dataSource);
        JdbcExportTaskRepository completionRepository =
                new JdbcExportTaskRepository(dataSource, storage);
        ExportJob job = createQueued("CONCURRENT-LATE-COMPLETE");
        setLifecycle(job.getJobId(), ExportJobStatus.QUEUED,
                LocalDateTime.now().minusMinutes(1));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<ExportJobStatus> expiry = executor.submit(() -> {
                ready.countDown();
                start.await();
                return expiryRepository.status(job.getJobId(), job.getUserId())
                        .orElseThrow(() -> new AssertionError("并发过期任务应可被所属用户查询"))
                        .getStatus();
            });
            Future<String> lateCompletion = executor.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    completionRepository.complete(job.getJobId(), "late.csv", "text/csv",
                            new byte[] {9, 8, 7});
                    return "COMPLETED";
                } catch (IllegalStateException expected) {
                    return expected.getMessage();
                }
            });

            assertTrue(ready.await(5, TimeUnit.SECONDS),
                    "两个并发参与者都应进入门闩后再开始");
            start.countDown();

            assertEquals(ExportJobStatus.EXPIRED,
                    expiry.get(10, TimeUnit.SECONDS));
            assertFalse("COMPLETED".equals(lateCompletion.get(10, TimeUnit.SECONDS)),
                    "迟到完成不得发布 SUCCEEDED");
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS),
                    "并发回归线程应在测试结束前退出");
        }

        assertEquals(ExportJobStatus.EXPIRED, databaseStatus(job.getJobId()));
        assertFalse(completionRepository.download(job.getJobId(), job.getUserId()).isPresent(),
                "过期任务即使存在迟到完成调用也不得下载");
        assertEquals(0, storage.saveCount, "过期任务不得保存导出文件");
        assertEquals(0, storage.openCount, "过期任务不得打开导出文件");
    }

    private ExportJob createQueued(String suffix) {
        String userId = runPrefix + "-USER-" + suffix;
        String requestKey = runPrefix + "-REQUEST-" + suffix;
        ExportJob job = repository.create(userId, requestKey,
                "requested-" + suffix.toLowerCase() + ".csv", "text/csv");
        createdJobIds.add(job.getJobId());
        return job;
    }

    private void setLifecycle(String jobId, ExportJobStatus status, LocalDateTime expiresAt) {
        LocalDateTime createdAt = expiresAt.minusMinutes(1);
        jdbc.update("UPDATE " + EXPORT_TABLE + " SET status = ?, created_at = ?, "
                        + "updated_at = ?, finished_at = NULL, error_message = NULL, "
                        + "expires_at = ? WHERE job_id = ?",
                status.name(), Timestamp.valueOf(createdAt), Timestamp.valueOf(createdAt),
                Timestamp.valueOf(expiresAt), jobId);
    }

    private ExportJob status(ExportJob job) {
        return repository.status(job.getJobId(), job.getUserId()).orElseThrow(
                () -> new AssertionError("任务应可被所属用户查询"));
    }

    private ExportJobStatus databaseStatus(String jobId) {
        return ExportJobStatus.valueOf(jdbc.queryForObject(
                "SELECT status FROM " + EXPORT_TABLE + " WHERE job_id = ?",
                String.class, jobId));
    }

    private boolean migrationPresent() {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("database/migration").resolve(MIGRATION_FILE))) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private static String environment(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value;
    }

    private static String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null
                ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static final class RecordingFileStorage implements ExportFileStoragePort {
        private int saveCount;
        private int openCount;

        @Override
        public void save(String fileKey, String userId, String fileName,
                         String contentType, byte[] content) {
            saveCount++;
        }

        @Override
        public Optional<ExportFile> open(String fileKey, String userId) {
            openCount++;
            return Optional.empty();
        }
    }
}
