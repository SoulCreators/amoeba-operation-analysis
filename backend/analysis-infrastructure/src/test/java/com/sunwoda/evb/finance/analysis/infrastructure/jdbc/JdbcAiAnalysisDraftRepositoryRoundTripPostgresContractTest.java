package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.AiAnalysisDraft;
import com.sunwoda.evb.finance.analysis.domain.model.AiAnalysisDraftStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * AI-JDBC-001-A：针对 JdbcAiAnalysisDraftRepository 的真实 PostgreSQL 往返契约。
 *
 * <p>本次修改只新增该独立契约测试，使用唯一批次夹具验证当前 sections 编码语义以及生命周期字段回读。</p>
 */
class JdbcAiAnalysisDraftRepositoryRoundTripPostgresContractTest {
    private static DataSource dataSource;
    private static JdbcTemplate jdbc;
    private static JdbcAiAnalysisDraftRepository repository;
    private static String batchNo;

    @BeforeAll
    static void connectAndSeedPostgres() {
        String url = System.getenv("AMOEBA_DB_URL");
        assumeTrue(url != null && !url.trim().isEmpty(),
                "BLOCKED: AMOEBA_DB_URL is not set; AI-JDBC-001-A PostgreSQL contract was not run");

        String username = System.getenv("AMOEBA_DB_USERNAME");
        assumeTrue(username != null && !username.trim().isEmpty(),
                "BLOCKED: AMOEBA_DB_USERNAME is not set; AI-JDBC-001-A PostgreSQL contract was not run");

        String password = System.getenv("AMOEBA_DB_PASSWORD");
        assumeTrue(password != null,
                "BLOCKED: AMOEBA_DB_PASSWORD is not set; AI-JDBC-001-A PostgreSQL contract was not run");

        dataSource = new DriverManagerDataSource(url, username, password);
        jdbc = new JdbcTemplate(dataSource);
        try {
            jdbc.execute("SELECT id FROM meta.analysis_batch LIMIT 0");
            jdbc.execute("SELECT id FROM calc.ai_analysis_draft LIMIT 0");
        } catch (RuntimeException failure) {
            assumeTrue(false, "BLOCKED: AI-JDBC-001-A PostgreSQL schema is not available: "
                    + failure.getMessage());
        }

        batchNo = "TEST-AI-JDBC-001-A-" + UUID.randomUUID().toString().replace("-", "");
        jdbc.update("INSERT INTO meta.analysis_batch(batch_no, period, status, created_by) "
                        + "VALUES (?, ?, ?, ?)",
                batchNo, "2026-10", "DRAFT", "AI-JDBC-001-A");
        repository = new JdbcAiAnalysisDraftRepository(dataSource);
    }

    @AfterAll
    static void cleanupFixture() {
        if (jdbc == null || batchNo == null) return;
        jdbc.update("DELETE FROM calc.ai_analysis_draft WHERE batch_id IN "
                + "(SELECT id FROM meta.analysis_batch WHERE batch_no = ?)", batchNo);
        jdbc.update("DELETE FROM meta.analysis_batch WHERE batch_no = ?", batchNo);
    }

    @Test
    void saveThenFindByBatchNoPreservesCurrentSectionEncodingAndLifecycleFields() {
        // 本次修改：验证真实 PostgreSQL 往返保持中文、竖线、等号及空值的当前编码语义，并回读时间和生成者。
        LocalDateTime generatedAt = LocalDateTime.of(2026, 10, 20, 9, 30, 45);
        Map<String, String> sections = new LinkedHashMap<String, String>();
        sections.put("中文|标题=一", "中文|内容=二");
        sections.put("空值字段", null);
        sections.put("空字符串字段", "");

        AiAnalysisDraft draft = new AiAnalysisDraft(batchNo, "AI-V1", sections,
                "AI-生成者-测试", generatedAt, AiAnalysisDraftStatus.DRAFT, null, null);

        repository.save(draft);

        Optional<AiAnalysisDraft> loaded = repository.findByBatchNo(batchNo);
        assertTrue(loaded.isPresent());
        AiAnalysisDraft persisted = loaded.get();
        assertEquals(batchNo, persisted.getBatchNo());
        assertEquals("AI-V1", persisted.getVersionNo());
        assertEquals(AiAnalysisDraftStatus.DRAFT, persisted.getStatus());
        assertEquals("AI-生成者-测试", persisted.getGeneratedBy());
        assertEquals(generatedAt, persisted.getGeneratedAt());
        assertNull(persisted.getPublishedBy());
        assertNull(persisted.getPublishedAt());

        Map<String, String> expectedSections = new LinkedHashMap<String, String>();
        expectedSections.put("中文|标题=一", "中文|内容=二");
        expectedSections.put("空值字段", "");
        expectedSections.put("空字符串字段", "");
        assertNotNull(persisted.getSections());
        assertEquals(expectedSections, persisted.getSections());
    }
}
