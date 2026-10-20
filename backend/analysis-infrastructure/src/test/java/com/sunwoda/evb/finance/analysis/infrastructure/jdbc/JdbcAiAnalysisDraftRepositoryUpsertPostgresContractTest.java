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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * AI-JDBC-001-B：本次新增真实 PostgreSQL 契约，验证 AI 分析草稿仓储对同一批次和版本的 upsert 行为。
 * 测试只使用唯一批次夹具，并在测试结束后删除草稿和批次数据，避免污染共享契约数据库。
 */
class JdbcAiAnalysisDraftRepositoryUpsertPostgresContractTest {
    private static DataSource dataSource;
    private static JdbcTemplate jdbc;
    private static JdbcAiAnalysisDraftRepository drafts;
    private static String batchNo;

    @BeforeAll
    static void connectToConfiguredPostgresAndCreateFixture() {
        String url = System.getenv("AMOEBA_DB_URL");
        assumeTrue(url != null && !url.trim().isEmpty(),
                "BLOCKED: AMOEBA_DB_URL is not set; AI-JDBC-001-B PostgreSQL contract was not run");

        String username = environment("AMOEBA_DB_USERNAME");
        assumeTrue(username != null && !username.trim().isEmpty(),
                "BLOCKED: AMOEBA_DB_USERNAME is not set; AI-JDBC-001-B PostgreSQL contract was not run");
        String password = System.getenv("AMOEBA_DB_PASSWORD");
        assumeTrue(password != null,
                "BLOCKED: AMOEBA_DB_PASSWORD is not set; AI-JDBC-001-B PostgreSQL contract was not run");

        dataSource = new DriverManagerDataSource(url, username, password);
        jdbc = new JdbcTemplate(dataSource);
        try {
            jdbc.execute("SELECT 1 FROM meta.analysis_batch LIMIT 0");
            jdbc.execute("SELECT 1 FROM calc.ai_analysis_draft LIMIT 0");
        } catch (RuntimeException failure) {
            assumeTrue(false, "BLOCKED: AI-JDBC-001-B PostgreSQL schema is not available: "
                    + failure.getMessage());
        }

        drafts = new JdbcAiAnalysisDraftRepository(dataSource);
        batchNo = "AI-JDBC-001-B-" + UUID.randomUUID().toString().replace("-", "");
        jdbc.update("INSERT INTO meta.analysis_batch(batch_no, period, perspective, status, created_by) "
                        + "VALUES (?, ?, ?, ?, ?)",
                batchNo, "2026-10", "BASE", "DRAFT", "AI-JDBC-001-B");
    }

    @AfterAll
    static void cleanupFixture() {
        if (jdbc == null || batchNo == null) return;
        jdbc.update("DELETE FROM calc.ai_analysis_draft WHERE batch_id IN "
                + "(SELECT id FROM meta.analysis_batch WHERE batch_no = ?)", batchNo);
        jdbc.update("DELETE FROM meta.analysis_batch WHERE batch_no = ?", batchNo);
    }

    @Test
    void savesDraftThenPublishedWithSameBatchAndVersionAsSingleUpdatedRow() {
        // 本次修改：先保存 DRAFT，再用相同 batchNo+versionNo 保存不同 sections 的 PUBLISHED，验证数据库真实 upsert。
        String versionNo = "AI-V1";
        Map<String, String> draftSections = sections("summary", "draft-content");
        LocalDateTime generatedAt = LocalDateTime.now().withNano(0);
        AiAnalysisDraft draft = new AiAnalysisDraft(batchNo, versionNo, draftSections,
                "ai-generator", generatedAt, AiAnalysisDraftStatus.DRAFT, null, null);

        drafts.save(draft);
        assertEquals(1, countDraftRows(versionNo));
        Optional<AiAnalysisDraft> persistedDraft = drafts.findByBatchNo(batchNo);
        assertTruePresent(persistedDraft);
        assertEquals(draftSections, persistedDraft.get().getSections());
        assertEquals(AiAnalysisDraftStatus.DRAFT, persistedDraft.get().getStatus());
        Long draftId = draftId(versionNo);

        Map<String, String> publishedSections = sections(
                "summary", "published-content", "recommendation", "publish-now");
        LocalDateTime publishedAt = generatedAt.plusMinutes(1);
        AiAnalysisDraft published = new AiAnalysisDraft(batchNo, versionNo, publishedSections,
                "ai-generator", generatedAt, AiAnalysisDraftStatus.PUBLISHED,
                "publisher-001", publishedAt);

        drafts.save(published);

        assertEquals(1, countDraftRows(versionNo), "相同 batchNo+versionNo 保存后只能保留一行");
        assertEquals(draftId, draftId(versionNo), "upsert 应更新原行而不是重复插入");
        Optional<AiAnalysisDraft> persisted = drafts.findByBatchNo(batchNo);
        assertTruePresent(persisted);
        AiAnalysisDraft actual = persisted.get();
        assertEquals(publishedSections, actual.getSections());
        assertEquals(AiAnalysisDraftStatus.PUBLISHED, actual.getStatus());
        assertEquals("publisher-001", actual.getPublishedBy());
        assertNotNull(actual.getPublishedAt());
        assertEquals(publishedAt, actual.getPublishedAt());
    }

    private static Map<String, String> sections(String... values) {
        Map<String, String> sections = new LinkedHashMap<String, String>();
        for (int i = 0; i < values.length; i += 2) {
            sections.put(values[i], values[i + 1]);
        }
        return sections;
    }

    private static int countDraftRows(String versionNo) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM calc.ai_analysis_draft d "
                + "JOIN meta.analysis_batch b ON b.id = d.batch_id "
                + "WHERE b.batch_no = ? AND d.result_version_no = ?", Integer.class, batchNo, versionNo);
    }

    private static Long draftId(String versionNo) {
        return jdbc.queryForObject("SELECT d.id FROM calc.ai_analysis_draft d "
                + "JOIN meta.analysis_batch b ON b.id = d.batch_id "
                + "WHERE b.batch_no = ? AND d.result_version_no = ?", Long.class, batchNo, versionNo);
    }

    private static void assertTruePresent(Optional<AiAnalysisDraft> draft) {
        assertTrue(draft.isPresent(), "upsert 后应能按 batchNo 读取 AI 草稿");
    }

    private static String environment(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value;
    }
}
