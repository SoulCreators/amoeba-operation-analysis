package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.application.model.ExportJob;
import com.sunwoda.evb.finance.analysis.application.model.ExportJobStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * GOV-002-EXPORT-GENERATION-TECH-001 的真实 PostgreSQL 领取契约。
 *
 * <p>本次只验证数据库原子领取和过期/未知任务门禁，不引入正式导出格式、权限、
 * 结果版本、留存期限或审计规则。</p>
 */
class JdbcExportTaskRepositoryGenerationClaimPostgresContractTest {
    private static final String EXPORT_TASK_TABLE = "flow.export_task";

    private static JdbcTemplate jdbc;
    private static JdbcExportTaskRepository repository;
    private final List<String> createdJobIds = new ArrayList<String>();

    @BeforeAll
    static void connectToConfiguredPostgresAndVerifyMigrations() {
        String url = System.getenv("AMOEBA_DB_URL");
        assumeTrue(url != null && !url.trim().isEmpty(),
                "BLOCKED: AMOEBA_DB_URL 未配置，未执行 PostgreSQL 领取契约");
        Path root = repositoryRoot();
        assumeTrue(Files.isRegularFile(root.resolve("database/migration/V018__create_export_task.sql")),
                "BLOCKED: 导出任务迁移文件不存在");
        assumeTrue(Files.isRegularFile(root.resolve("database/migration/V021__add_export_task_input_snapshot.sql")),
                "BLOCKED: 导出任务快照迁移文件不存在");

        DataSource dataSource = new DriverManagerDataSource(
                url, environment("AMOEBA_DB_USERNAME"), environment("AMOEBA_DB_PASSWORD"));
        jdbc = new JdbcTemplate(dataSource);
        try {
            jdbc.execute("SELECT job_id, status, expires_at, batch_no, result_version, "
                    + "perspective, result_status, format, summary_scopes, detail_scopes "
                    + "FROM " + EXPORT_TASK_TABLE + " LIMIT 0");
        } catch (RuntimeException failure) {
            assumeTrue(false, "BLOCKED: PostgreSQL 导出任务迁移未应用：" + failure.getMessage());
        }
        repository = new JdbcExportTaskRepository(dataSource);
    }

    @AfterEach
    void removeRandomFixtures() {
        if (jdbc == null) {
            return;
        }
        for (String jobId : createdJobIds) {
            jdbc.update("DELETE FROM " + EXPORT_TASK_TABLE + " WHERE job_id = ?", jobId);
        }
        createdJobIds.clear();
    }

    @Test
    void firstClaimPersistsRunningAndSecondClaimReturnsFalse() {
        ExportJob job = createTask("SINGLE");

        assertTrue(repository.tryMarkRunning(job.getJobId()));
        assertEquals("RUNNING", jdbc.queryForObject(
                "SELECT status FROM " + EXPORT_TASK_TABLE + " WHERE job_id = ?",
                String.class, job.getJobId()));
        assertEquals(ExportJobStatus.RUNNING,
                repository.status(job.getJobId(), job.getUserId()).get().getStatus());

        assertFalse(repository.tryMarkRunning(job.getJobId()));
        assertEquals("RUNNING", jdbc.queryForObject(
                "SELECT status FROM " + EXPORT_TASK_TABLE + " WHERE job_id = ?",
                String.class, job.getJobId()));
    }

    @Test
    void expiredQueuedTaskCannotBeClaimed() {
        ExportJob job = createTask("EXPIRED");
        LocalDateTime now = LocalDateTime.now().withNano(0);
        jdbc.update("UPDATE " + EXPORT_TASK_TABLE
                        + " SET created_at = ?, expires_at = ?, updated_at = ? WHERE job_id = ?",
                Timestamp.valueOf(now.minusMinutes(2)),
                Timestamp.valueOf(now.minusMinutes(1)),
                Timestamp.valueOf(now), job.getJobId());

        assertThrows(IllegalStateException.class,
                () -> repository.tryMarkRunning(job.getJobId()));
    }

    @Test
    void unknownTaskCannotBeClaimed() {
        assertThrows(IllegalArgumentException.class,
                () -> repository.tryMarkRunning("EXP-GENERATION-UNKNOWN-"
                        + UUID.randomUUID().toString().replace("-", "")));
    }

    private ExportJob createTask(String suffix) {
        String token = UUID.randomUUID().toString().replace("-", "");
        ExportJob job = repository.create("GENERATION-CLAIM-USER-" + token + "-" + suffix,
                "GENERATION-CLAIM-REQUEST-" + token + "-" + suffix,
                "generation-claim.csv", "text/csv");
        createdJobIds.add(job.getJobId());
        return job;
    }

    private static Path repositoryRoot() {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            if (Files.isDirectory(current.resolve("database").resolve("migration"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("database/migration 目录不存在");
    }

    private static String environment(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value;
    }
}
