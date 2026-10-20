package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.BatchStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * BATCH-JDBC-001-B：新增真实 PostgreSQL 契约，锁定分析批次仓储按同一 id 更新状态和数据视角，
 * 并锁定未知 batchNo 返回空 Optional 的当前行为。
 */
class JdbcAnalysisBatchRepositoryUpdateAndMissingPostgresContractTest {
    private static DataSource dataSource;
    private static JdbcTemplate jdbc;
    private static JdbcAnalysisBatchRepository batches;
    private static String batchNo;

    @BeforeAll
    static void connectToConfiguredPostgres() {
        // 本次修改：按仓库既有规范接入真实 PostgreSQL，并在连接参数或表结构缺失时阻断契约执行。
        String url = System.getenv("AMOEBA_DB_URL");
        assumeTrue(url != null && !url.trim().isEmpty(),
                "BLOCKED: AMOEBA_DB_URL is not set; BATCH-JDBC-001-B PostgreSQL contract was not run");

        String username = System.getenv("AMOEBA_DB_USERNAME");
        assumeTrue(username != null && !username.trim().isEmpty(),
                "BLOCKED: AMOEBA_DB_USERNAME is not set; BATCH-JDBC-001-B PostgreSQL contract was not run");
        String password = System.getenv("AMOEBA_DB_PASSWORD");
        assumeTrue(password != null,
                "BLOCKED: AMOEBA_DB_PASSWORD is not set; BATCH-JDBC-001-B PostgreSQL contract was not run");

        dataSource = new DriverManagerDataSource(url, username, password);
        jdbc = new JdbcTemplate(dataSource);
        try {
            jdbc.execute("SELECT id, batch_no, period, perspective, status, created_by, created_at "
                    + "FROM meta.analysis_batch LIMIT 0");
        } catch (RuntimeException failure) {
            assumeTrue(false, "BLOCKED: BATCH-JDBC-001-B PostgreSQL schema is not available: "
                    + failure.getMessage());
        }

        batches = new JdbcAnalysisBatchRepository(dataSource);
        batchNo = "BATCH-JDBC-001-B-" + UUID.randomUUID().toString().replace("-", "");
    }

    @AfterAll
    static void cleanupFixture() {
        // 本次修改：按 UUID 批次号清理本契约创建的批次夹具，避免污染共享 PostgreSQL 数据。
        if (jdbc == null || batchNo == null) return;
        jdbc.update("DELETE FROM meta.analysis_batch WHERE batch_no = ?", batchNo);
    }

    @Test
    void savesThenUpdatesSameIdAndReadsBackStatusAndPerspective() {
        // 本次修改：先保存 UUID 唯一批次，再用同 id 的新对象保存更新后的状态和数据视角，验证真实数据库回读结果。
        LocalDateTime createdAt = LocalDateTime.now().withNano(0);
        AnalysisBatch created = batches.save(new AnalysisBatch(null, batchNo, "2026-10",
                AnalysisPerspective.BASE, BatchStatus.DRAFT, "BATCH-JDBC-001-B", createdAt));

        AnalysisBatch updated = new AnalysisBatch(created.getId(), created.getBatchNo(), created.getPeriod(),
                AnalysisPerspective.PRODUCT_LINE, BatchStatus.CHECKING, created.getCreatedBy(), created.getCreatedAt());
        batches.save(updated);

        Optional<AnalysisBatch> persisted = batches.findByBatchNo(batchNo);
        assertTrue(persisted.isPresent(), "更新后应能按 batchNo 读取分析批次");
        assertEquals(created.getId(), persisted.get().getId());
        assertEquals(BatchStatus.CHECKING, persisted.get().getStatus());
        assertEquals(AnalysisPerspective.PRODUCT_LINE, persisted.get().getPerspective());
        // 本次修改：确认更新状态和视角时，批次期间、创建人和创建时间仍保持原始持久化值。
        assertEquals(created.getPeriod(), persisted.get().getPeriod());
        assertEquals(created.getCreatedBy(), persisted.get().getCreatedBy());
        assertEquals(created.getCreatedAt(), persisted.get().getCreatedAt());
    }

    @Test
    void returnsEmptyForUnknownBatchNo() {
        // 本次修改：增加未知 batchNo 的真实 PostgreSQL 查询契约，确认仓储返回 Optional.empty 而不是伪造批次。
        String unknownBatchNo = "BATCH-JDBC-001-B-MISSING-"
                + UUID.randomUUID().toString().replace("-", "");

        Optional<AnalysisBatch> persisted = batches.findByBatchNo(unknownBatchNo);

        assertTrue(!persisted.isPresent(), "未知 batchNo 应返回 Optional.empty");
    }
}
