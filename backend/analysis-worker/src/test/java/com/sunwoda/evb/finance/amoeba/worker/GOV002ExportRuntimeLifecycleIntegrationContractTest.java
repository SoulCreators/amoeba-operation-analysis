package com.sunwoda.evb.finance.amoeba.worker;

import com.sunwoda.evb.finance.analysis.application.model.ExportFile;
import com.sunwoda.evb.finance.analysis.application.model.ExportJob;
import com.sunwoda.evb.finance.analysis.application.model.ExportJobStatus;
import com.sunwoda.evb.finance.analysis.application.port.ExportFileStoragePort;
import com.sunwoda.evb.finance.analysis.application.port.ExportTaskPort;
import com.sunwoda.evb.finance.analysis.infrastructure.filesystem.FileSystemExportFileStorageAdapter;
import com.sunwoda.evb.finance.analysis.infrastructure.jdbc.JdbcExportTaskRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.AopTestUtils;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * GOV-002-EXPORT-RUNTIME-001 的导出任务真实生命周期集成契约。
 *
 * <p>本次只新增测试，不修改生产代码：验证默认 profile 的 fail-closed 边界、
 * 显式 db + filesystem 的适配器选择，以及 JDBC 任务仓储和本地文件适配器的
 * create → markRunning → complete → download 生命周期、用户隔离和过期完成阻断。
 * PostgreSQL 配置或导出任务表缺失时直接失败，不把环境问题静默标记为跳过。</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GOV002ExportRuntimeLifecycleIntegrationContractTest {
    private static final String EXPORT_TABLE = "flow.export_task";

    private DataSource dataSource;
    private JdbcTemplate jdbc;
    private String runPrefix;
    private final List<String> createdJobIds = new ArrayList<String>();

    @TempDir
    Path storageRoot;

    @BeforeAll
    void connectToConfiguredPostgres() {
        String url = requiredEnvironment("AMOEBA_DB_URL");
        String username = requiredNonBlankEnvironment("AMOEBA_DB_USERNAME");
        String password = environment("AMOEBA_DB_PASSWORD");
        if (!migrationPresent()) {
            throw new IllegalStateException(
                    "缺少 database/migration 目录，无法执行 GOV-002 PostgreSQL 契约测试");
        }

        dataSource = new DriverManagerDataSource(url, username, password);
        jdbc = new JdbcTemplate(dataSource);
        try {
            jdbc.execute("SELECT job_id, user_id, request_key, file_name, content_type, "
                    + "status, created_at, updated_at, finished_at, expires_at "
                    + "FROM " + EXPORT_TABLE + " LIMIT 0");
        } catch (RuntimeException failure) {
            throw new IllegalStateException(
                    "PostgreSQL 导出任务表不可用，拒绝将集成测试静默跳过", failure);
        }
    }

    @BeforeEach
    void initializeIsolatedFixture() {
        runPrefix = "GOV002-" + UUID.randomUUID().toString().replace("-", "");
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

    /** 本次新增默认 profile 门禁测试，确保没有数据库和文件系统生产适配器。 */
    @Test
    void defaultProfileDoesNotEnableProductionDbOrFilesystemAdapters() {
        try (ConfigurableApplicationContext context = runtimeContext()) {
            assertEquals("memory", context.getEnvironment().getProperty("spring.profiles.default"));
            assertFalse(context.getEnvironment().acceptsProfiles("db"));
            assertFalse(context.getEnvironment().acceptsProfiles("filesystem"));
            assertTrue(context.getBeansOfType(DataSource.class).isEmpty());
            assertTrue(context.getBeansOfType(JdbcExportTaskRepository.class).isEmpty());
            assertTrue(context.getBeansOfType(FileSystemExportFileStorageAdapter.class).isEmpty());
            assertTrue(context.getBeansOfType(ExportTaskPort.class).isEmpty());
            assertTrue(context.getBeansOfType(ExportFileStoragePort.class).isEmpty());
        }
    }

    /** 本次新增显式 profile 接线测试，确保 JDBC 任务仓储和文件存储不会被内存适配器替代。 */
    @Test
    void explicitDbAndFilesystemSelectJdbcTaskAndFileSystemStorageBeans() {
        try (ConfigurableApplicationContext context = runtimeContext("db", "filesystem")) {
            assertTrue(context.getEnvironment().acceptsProfiles("db"));
            assertTrue(context.getEnvironment().acceptsProfiles("filesystem"));
            assertEquals(1, context.getBeansOfType(ExportTaskPort.class).size());
            assertEquals(JdbcExportTaskRepository.class,
                    AopTestUtils.getTargetObject(context.getBean(ExportTaskPort.class)).getClass());
            assertEquals(1, context.getBeansOfType(ExportFileStoragePort.class).size());
            assertEquals(FileSystemExportFileStorageAdapter.class,
                    AopTestUtils.getTargetObject(context.getBean(ExportFileStoragePort.class)).getClass());
            assertNotNull(context.getBean(DataSource.class));
        }
    }

    /** 本次新增真实生命周期测试，验证数据库任务状态与文件字节可完整往返且按用户隔离。 */
    @Test
    void jdbcTaskAndFilesystemStorageCompleteFullLifecycleWithUserIsolation() {
        FileSystemExportFileStorageAdapter storage = new FileSystemExportFileStorageAdapter(
                storageRoot.toString());
        JdbcExportTaskRepository repository = new JdbcExportTaskRepository(dataSource, storage);
        String userId = runPrefix + "-OWNER";
        String otherUserId = runPrefix + "-OTHER";
        ExportJob created = create(repository, userId, "LIFECYCLE");

        assertEquals(ExportJobStatus.QUEUED, created.getStatus());
        repository.markRunning(created.getJobId());
        assertEquals(ExportJobStatus.RUNNING,
                repository.status(created.getJobId(), userId).get().getStatus());

        byte[] content = "真实导出生命周期".getBytes(StandardCharsets.UTF_8);
        repository.complete(created.getJobId(), "lifecycle.csv", "text/csv", content);

        ExportJob completed = repository.status(created.getJobId(), userId).get();
        assertEquals(ExportJobStatus.SUCCEEDED, completed.getStatus());
        ExportFile downloaded = repository.download(created.getJobId(), userId).get();
        assertEquals("lifecycle.csv", downloaded.getFileName());
        assertEquals("text/csv", downloaded.getContentType());
        assertArrayEquals(content, downloaded.getContent());
        assertFalse(repository.status(created.getJobId(), otherUserId).isPresent());
        assertFalse(repository.download(created.getJobId(), otherUserId).isPresent());
        assertTrue(storage.open(created.getJobId(), userId).isPresent());
        assertFalse(storage.open(created.getJobId(), otherUserId).isPresent());
    }

    /** 本次新增过期任务门禁测试，验证队列态和运行态任务均不能落盘或发布成功。 */
    @Test
    void expiredQueuedAndRunningTasksCannotCompleteOrPersistFiles() {
        FileSystemExportFileStorageAdapter storage = new FileSystemExportFileStorageAdapter(
                storageRoot.toString());
        JdbcExportTaskRepository repository = new JdbcExportTaskRepository(dataSource, storage);

        ExportJob queued = create(repository, runPrefix + "-QUEUED-OWNER", "EXPIRED-QUEUED");
        expireInDatabase(queued.getJobId(), ExportJobStatus.QUEUED);
        assertThrows(IllegalStateException.class,
                () -> repository.complete(queued.getJobId(), "queued.csv", "text/csv",
                        new byte[] {1, 2, 3}));
        assertEquals(ExportJobStatus.EXPIRED,
                repository.status(queued.getJobId(), queued.getUserId()).get().getStatus());
        assertFalse(storage.open(queued.getJobId(), queued.getUserId()).isPresent());

        ExportJob running = create(repository, runPrefix + "-RUNNING-OWNER", "EXPIRED-RUNNING");
        repository.markRunning(running.getJobId());
        expireInDatabase(running.getJobId(), ExportJobStatus.RUNNING);
        assertThrows(IllegalStateException.class,
                () -> repository.complete(running.getJobId(), "running.csv", "text/csv",
                        new byte[] {4, 5, 6}));
        assertEquals(ExportJobStatus.EXPIRED,
                repository.status(running.getJobId(), running.getUserId()).get().getStatus());
        assertFalse(storage.open(running.getJobId(), running.getUserId()).isPresent());
    }

    private ExportJob create(JdbcExportTaskRepository repository, String userId, String suffix) {
        ExportJob job = repository.create(userId, runPrefix + "-REQUEST-" + suffix,
                "requested-" + suffix.toLowerCase() + ".csv", "text/csv");
        createdJobIds.add(job.getJobId());
        return job;
    }

    private void expireInDatabase(String jobId, ExportJobStatus status) {
        LocalDateTime expiredAt = LocalDateTime.now().minusMinutes(1);
        jdbc.update("UPDATE " + EXPORT_TABLE + " SET status = ?, "
                        + "created_at = ?, updated_at = ?, finished_at = NULL, "
                        + "error_message = NULL, expires_at = ? WHERE job_id = ?",
                status.name(), Timestamp.valueOf(expiredAt.minusMinutes(1)),
                Timestamp.valueOf(expiredAt.minusMinutes(1)), Timestamp.valueOf(expiredAt), jobId);
    }

    private ConfigurableApplicationContext runtimeContext(String... profiles) {
        return new SpringApplicationBuilder(AnalysisWorkerApplication.class)
                .web(WebApplicationType.NONE)
                .profiles(profiles)
                .properties(
                        "spring.main.web-application-type=none",
                        "analysis.worker.cleanup.scheduling-enabled=false",
                        "spring.datasource.url=" + requiredEnvironment("AMOEBA_DB_URL"),
                        "spring.datasource.username=" + requiredNonBlankEnvironment(
                                "AMOEBA_DB_USERNAME"),
                        "spring.datasource.password=" + environment("AMOEBA_DB_PASSWORD"),
                        "analysis.storage.root=" + storageRoot.toString())
                .run();
    }

    private boolean migrationPresent() {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            if (Files.isDirectory(current.resolve("database/migration"))) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException(name + " 必须配置，不能静默跳过 PostgreSQL 集成测试");
        }
        return value.trim();
    }

    private static String requiredNonBlankEnvironment(String name) {
        return requiredEnvironment(name);
    }

    private static String environment(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value;
    }

}
