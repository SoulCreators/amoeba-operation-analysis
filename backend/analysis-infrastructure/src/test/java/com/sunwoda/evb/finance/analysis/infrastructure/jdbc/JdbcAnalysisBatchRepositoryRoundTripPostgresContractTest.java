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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * BATCH-JDBC-001-A：基于真实 PostgreSQL 的分析批次 JDBC 往返契约。
 *
 * <p>本次修改：新增独立测试，使用 UUID 唯一批次夹具验证无 id 保存后的主键生成、
 * 保存返回对象字段和按批次号回读字段，并在测试结束后清理夹具。</p>
 */
class JdbcAnalysisBatchRepositoryRoundTripPostgresContractTest {
    private static DataSource dataSource;
    private static JdbcTemplate jdbc;
    private static JdbcAnalysisBatchRepository repository;
    private static String batchNo;

    @BeforeAll
    static void connectToConfiguredPostgres() {
        // 本次修改：按仓库真实 PostgreSQL 契约规范，对数据库连接参数和批次表执行环境门禁。
        String url = System.getenv("AMOEBA_DB_URL");
        assumeTrue(url != null && !url.trim().isEmpty(),
                "BLOCKED: AMOEBA_DB_URL is not set; BATCH-JDBC-001-A PostgreSQL contract was not run");

        String username = System.getenv("AMOEBA_DB_USERNAME");
        assumeTrue(username != null && !username.trim().isEmpty(),
                "BLOCKED: AMOEBA_DB_USERNAME is not set; BATCH-JDBC-001-A PostgreSQL contract was not run");

        String password = System.getenv("AMOEBA_DB_PASSWORD");
        assumeTrue(password != null,
                "BLOCKED: AMOEBA_DB_PASSWORD is not set; BATCH-JDBC-001-A PostgreSQL contract was not run");

        dataSource = new DriverManagerDataSource(url, username, password);
        jdbc = new JdbcTemplate(dataSource);
        try {
            jdbc.execute("SELECT id, batch_no, period, perspective, status, created_by, created_at "
                    + "FROM meta.analysis_batch LIMIT 0");
        } catch (RuntimeException failure) {
            assumeTrue(false, "BLOCKED: BATCH-JDBC-001-A PostgreSQL schema is not available: "
                    + failure.getMessage());
        }

        repository = new JdbcAnalysisBatchRepository(dataSource);
        batchNo = "TEST-BATCH-JDBC-001-A-" + UUID.randomUUID().toString().replace("-", "");
    }

    @AfterAll
    static void cleanupFixture() {
        // 本次修改：按 UUID 批次号删除本测试创建的记录，避免污染真实 PostgreSQL 数据。
        if (jdbc == null || batchNo == null) return;
        jdbc.update("DELETE FROM meta.analysis_batch WHERE batch_no = ?", batchNo);
    }

    @Test
    void saveWithoutIdGeneratesIdAndRoundTripsCurrentBatchFields() {
        // 本次修改：验证无 id 保存生成主键、save 返回对象当前字段及 findByBatchNo 的完整字段回读。
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 20, 10, 11, 12);
        AnalysisBatch input = new AnalysisBatch(null, batchNo, "2026-10",
                AnalysisPerspective.PRODUCT_LINE, "PL-002", BatchStatus.CHECKING,
                "batch-jdbc-001-a", createdAt);

        AnalysisBatch saved = repository.save(input);

        assertNotNull(saved.getId());
        assertEquals(batchNo, saved.getBatchNo());
        assertEquals("2026-10", saved.getPeriod());
        assertEquals(AnalysisPerspective.PRODUCT_LINE, saved.getPerspective());
        assertEquals("PL-002", saved.getScopeCode());
        assertEquals(BatchStatus.CHECKING, saved.getStatus());
        assertEquals("batch-jdbc-001-a", saved.getCreatedBy());
        assertEquals(createdAt, saved.getCreatedAt());

        Optional<AnalysisBatch> loaded = repository.findByBatchNo(batchNo);
        assertTrue(loaded.isPresent());
        AnalysisBatch persisted = loaded.get();
        assertEquals(saved.getId(), persisted.getId());
        assertEquals(batchNo, persisted.getBatchNo());
        assertEquals("2026-10", persisted.getPeriod());
        assertEquals(AnalysisPerspective.PRODUCT_LINE, persisted.getPerspective());
        assertEquals("PL-002", persisted.getScopeCode());
        assertEquals(BatchStatus.CHECKING, persisted.getStatus());
        assertEquals("batch-jdbc-001-a", persisted.getCreatedBy());
        assertEquals(createdAt, persisted.getCreatedAt());
    }
}
