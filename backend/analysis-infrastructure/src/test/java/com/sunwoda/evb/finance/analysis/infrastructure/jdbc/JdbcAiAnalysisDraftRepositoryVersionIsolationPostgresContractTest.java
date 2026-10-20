package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.AiAnalysisDraft;
import com.sunwoda.evb.finance.analysis.domain.model.AiAnalysisDraftStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * AI-JDBC-001-C 的真实 PostgreSQL 契约：同一批次的不同结果版本必须隔离保存。
 *
 * <p>本次修改：新增版本隔离契约，直接读取 calc.ai_analysis_draft 验证两个版本共存、
 * sections_json 不互相覆盖，并覆盖未知批次查询返回空结果的行为。</p>
 */
class JdbcAiAnalysisDraftRepositoryVersionIsolationPostgresContractTest {
    private static final String BATCH_PREFIX = "AI-JDBC-001-C-";
    private static final String VERSION_ONE = "AI-RESULT-V1";
    private static final String VERSION_TWO = "AI-RESULT-V2";

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;
    private static JdbcAiAnalysisDraftRepository repository;
    private static String batchNo;

    /** 本次修改：按仓库既有 opt-in 规范读取 PostgreSQL 配置并建立唯一批次夹具。 */
    @BeforeAll
    static void connectAndSeedPostgres() {
        String url = System.getenv("AMOEBA_DB_URL");
        assumeTrue(url != null && !url.trim().isEmpty(),
                "BLOCKED: AMOEBA_DB_URL is not set; AI draft PostgreSQL contract was not run");
        String username = System.getenv("AMOEBA_DB_USERNAME");
        assumeTrue(username != null && !username.trim().isEmpty(),
                "BLOCKED: AMOEBA_DB_USERNAME is not set; AI draft PostgreSQL contract was not run");
        String password = System.getenv("AMOEBA_DB_PASSWORD");
        assumeTrue(password != null,
                "BLOCKED: AMOEBA_DB_PASSWORD is not set; AI draft PostgreSQL contract was not run");

        dataSource = new DriverManagerDataSource(url, username, password);
        jdbc = new JdbcTemplate(dataSource);
        try {
            jdbc.queryForObject("SELECT COUNT(*) FROM meta.analysis_batch", Long.class);
            jdbc.queryForObject("SELECT COUNT(*) FROM calc.ai_analysis_draft", Long.class);
        } catch (RuntimeException failure) {
            assumeTrue(false, "BLOCKED: AI draft PostgreSQL schema is not available: "
                    + failure.getMessage());
        }

        repository = new JdbcAiAnalysisDraftRepository(dataSource);
        batchNo = BATCH_PREFIX + UUID.randomUUID().toString().replace("-", "");
        jdbc.update("INSERT INTO meta.analysis_batch "
                        + "(batch_no, period, status, created_by, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                batchNo, "2026-10", "DRAFT", "AI-JDBC-001-C", timestamp(2026, 10, 20, 10, 0),
                timestamp(2026, 10, 20, 10, 0));
    }

    /** 本次修改：删除本测试创建的 AI 草稿和唯一批次，避免污染真实 PostgreSQL。 */
    @AfterAll
    static void cleanupFixture() {
        if (jdbc == null || batchNo == null) {
            return;
        }
        jdbc.update("DELETE FROM calc.ai_analysis_draft WHERE batch_id IN "
                + "(SELECT id FROM meta.analysis_batch WHERE batch_no = ?)", batchNo);
        jdbc.update("DELETE FROM meta.analysis_batch WHERE batch_no = ?", batchNo);
    }

    /** 本次修改：验证同一 batchNo 保存两个版本后数据库中保留两行，且 sections 不串写。 */
    @Test
    void savingDifferentVersionsKeepsTwoRowsAndIndependentSections() {
        Map<String, String> firstSections = sections("summary", "VERSION-ONE-SUMMARY",
                "risk", "VERSION-ONE-RISK");
        Map<String, String> secondSections = sections("summary", "VERSION-TWO-SUMMARY",
                "risk", "VERSION-TWO-RISK");

        repository.save(draft(VERSION_ONE, firstSections));
        repository.save(draft(VERSION_TWO, secondSections));

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT d.result_version_no, d.sections_json FROM calc.ai_analysis_draft d "
                        + "WHERE d.batch_id = (SELECT id FROM meta.analysis_batch WHERE batch_no = ?) "
                        + "ORDER BY d.result_version_no", batchNo);

        assertEquals(2, rows.size());
        Map<String, String> sectionsByVersion = new LinkedHashMap<String, String>();
        for (Map<String, Object> row : rows) {
            sectionsByVersion.put((String) row.get("result_version_no"),
                    (String) row.get("sections_json"));
        }
        assertEquals(2, sectionsByVersion.size());
        assertEquals(encodeSections(firstSections), sectionsByVersion.get(VERSION_ONE));
        assertEquals(encodeSections(secondSections), sectionsByVersion.get(VERSION_TWO));
    }

    /** 本次修改：验证不存在的 batchNo 通过仓储查询时返回 Optional.empty。 */
    @Test
    void findingUnknownBatchReturnsEmpty() {
        Optional<AiAnalysisDraft> result = repository.findByBatchNo(
                BATCH_PREFIX + "UNKNOWN-" + UUID.randomUUID().toString().replace("-", ""));

        assertFalse(result.isPresent());
    }

    private static AiAnalysisDraft draft(String versionNo, Map<String, String> sections) {
        return new AiAnalysisDraft(batchNo, versionNo, sections, "AI-JDBC-001-C",
                timestamp(2026, 10, 20, 10, 1), AiAnalysisDraftStatus.DRAFT, null, null);
    }

    private static Map<String, String> sections(String... values) {
        Map<String, String> sections = new LinkedHashMap<String, String>();
        for (int i = 0; i < values.length; i += 2) {
            sections.put(values[i], values[i + 1]);
        }
        return sections;
    }

    private static String encodeSections(Map<String, String> sections) {
        StringBuilder value = new StringBuilder();
        for (Map.Entry<String, String> entry : sections.entrySet()) {
            if (value.length() > 0) {
                value.append('|');
            }
            value.append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));
        }
        return value.toString();
    }

    private static String encode(String value) {
        return java.util.Base64.getEncoder().encodeToString(
                (value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private static LocalDateTime timestamp(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute);
    }

}
