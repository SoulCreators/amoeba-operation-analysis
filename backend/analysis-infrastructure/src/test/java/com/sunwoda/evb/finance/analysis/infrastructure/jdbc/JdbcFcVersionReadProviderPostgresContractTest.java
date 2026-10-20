package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.FcVersion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 基于真实 PostgreSQL 的 FC 版本读取契约，锁定当前 JDBC 查询与过滤行为。 */
class JdbcFcVersionReadProviderPostgresContractTest {
    private static DataSource dataSource;
    private static JdbcTemplate jdbc;
    private static JdbcFcVersionProvider versions;
    private static String runPrefix;
    private static YearMonth periodSeed;

    @BeforeAll
    static void connectToConfiguredPostgres() {
        String url = System.getenv("AMOEBA_DB_URL");
        // 本次修改将 PostgreSQL 环境缺失改为硬失败，防止 CI 在未执行真实契约时误报通过。
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalStateException(
                    "REQUIRED: AMOEBA_DB_URL is not set; FC version PostgreSQL contract must run");
        }

        dataSource = new DriverManagerDataSource(
                url, environment("AMOEBA_DB_USERNAME"), environment("AMOEBA_DB_PASSWORD"));
        jdbc = new JdbcTemplate(dataSource);
        try {
            jdbc.execute("SELECT id, version_code, perspective, base_period, status, official, updated_at "
                    + "FROM calc.fc_version LIMIT 0");
            jdbc.execute("SELECT id, batch_no, period FROM meta.analysis_batch LIMIT 0");
        } catch (RuntimeException failure) {
            throw new IllegalStateException(
                    "REQUIRED: FC version PostgreSQL schema is not available: "
                            + failure.getMessage(), failure);
        }

        versions = new JdbcFcVersionProvider(dataSource);
        // 本次测试使用短随机前缀，确保夹具值符合 V002/V014 的 VARCHAR(64) 约束。
        runPrefix = "FVR-" + UUID.randomUUID().toString().replace("-", "");
        // 本次测试为正式版本唯一索引生成隔离期间，避免与其他契约测试共享业务期间。
        int yearOffset = (runPrefix.hashCode() & 0x7fffffff) % 7000;
        periodSeed = YearMonth.of(2100 + yearOffset, 1);
    }

    @AfterAll
    static void cleanup() {
        if (jdbc == null || runPrefix == null) return;
        jdbc.update("DELETE FROM calc.fc_version WHERE version_code LIKE ?", runPrefix + "%");
        jdbc.update("DELETE FROM meta.analysis_batch WHERE batch_no LIKE ?", runPrefix + "%");
    }

    @Test
    void defaultLookupReturnsOfficialVersionAndReadsCurrentStatusFields() {
        String batchNo = batchNo("OFFICIAL");
        String period = period(0);
        insertBatch(batchNo, period);
        insertVersion(versionCode("DRAFT"), "BASE", period, "DRAFT", false,
                "draft-user", null, null);
        insertVersion(versionCode("OFFICIAL"), "BASE", period, "PUBLISHED", true,
                "publish-user", "publish-user", "2026-01-20 10:00:00");

        // 本测试块验证当前默认查询只返回正式版本，并读取 status、official 和发布元数据。
        Optional<FcVersion> result = versions.findForBatch(batchNo, AnalysisPerspective.BASE, null);

        assertTrue(result.isPresent());
        FcVersion version = result.get();
        assertEquals(versionCode("OFFICIAL"), version.getVersionCode());
        assertEquals(AnalysisPerspective.BASE, version.getPerspective());
        assertEquals(period, version.getBasePeriod());
        assertEquals("PUBLISHED", version.getStatus());
        assertTrue(version.isOfficial());
        assertEquals("publish-user", version.getPublishedBy());
        assertTrue(version.getPublishedAt() != null);
    }

    @Test
    void explicitVersionLookupReturnsNonOfficialVersionWithoutSubstitution() {
        String batchNo = batchNo("EXPLICIT");
        String draftCode = versionCode("EXPLICIT-DRAFT");
        String period = period(1);
        insertBatch(batchNo, period);
        insertVersion(versionCode("EXPLICIT-OFFICIAL"), "BASE", period, "PUBLISHED", true,
                "publish-user", "publish-user", "2026-01-22 10:00:00");
        insertVersion(draftCode, "BASE", period, "DRAFT", false,
                "draft-user", null, null);

        // 本测试块验证同期间存在正式版本时，显式版本号仍读取指定草稿，不被正式版本替换。
        Optional<FcVersion> result = versions.findForBatch(
                batchNo, AnalysisPerspective.BASE, draftCode);

        assertTrue(result.isPresent());
        FcVersion version = result.get();
        assertEquals(draftCode, version.getVersionCode());
        assertEquals("DRAFT", version.getStatus());
        assertFalse(version.isOfficial());
        assertEquals("draft-user", version.getUploadedBy());
    }

    @Test
    void lookupFiltersByPerspectiveAndBatchPeriod() {
        String batchNo = batchNo("FILTER");
        String period = period(2);
        insertBatch(batchNo, period);
        insertVersion(versionCode("BASE"), "BASE", period, "PUBLISHED", true,
                "base-user", "base-publisher", "2026-01-21 10:00:00");
        insertVersion(versionCode("PRODUCT"), "PRODUCT_LINE", period, "PUBLISHED", true,
                "product-user", "product-publisher", "2026-01-21 11:00:00");
        insertVersion(versionCode("OTHER-PERIOD"), "BASE", period(3), "PUBLISHED", true,
                "other-user", "other-publisher", "2026-02-21 10:00:00");

        // 本测试块验证当前 SQL 按批次期间和 perspective 过滤，不跨视角或期间返回版本。
        Optional<FcVersion> base = versions.findForBatch(batchNo, AnalysisPerspective.BASE, null);
        Optional<FcVersion> product = versions.findForBatch(
                batchNo, AnalysisPerspective.PRODUCT_LINE, null);
        Optional<FcVersion> businessUnit = versions.findForBatch(
                batchNo, AnalysisPerspective.BUSINESS_UNIT, null);

        assertTrue(base.isPresent());
        assertEquals(versionCode("BASE"), base.get().getVersionCode());
        assertTrue(product.isPresent());
        assertEquals(versionCode("PRODUCT"), product.get().getVersionCode());
        assertFalse(businessUnit.isPresent());
    }

    @Test
    void defaultLookupDoesNotFallBackToNonOfficialVersion() {
        String batchNo = batchNo("NO-OFFICIAL");
        String period = period(4);
        insertBatch(batchNo, period);
        insertVersion(versionCode("ONLY-DRAFT"), "BASE", period, "DRAFT", false,
                "draft-user", null, null);

        // 本测试块验证默认查询没有正式版本时保持空结果，不把非正式版本当作正式版本返回。
        Optional<FcVersion> result = versions.findForBatch(batchNo, AnalysisPerspective.BASE, null);

        assertFalse(result.isPresent());
    }

    private static void insertBatch(String batchNo, String period) {
        jdbc.update("INSERT INTO meta.analysis_batch "
                        + "(batch_no, period, status, perspective, created_by, created_at, updated_by, updated_at) "
                        + "VALUES (?, ?, 'DRAFT', 'BASE', ?, CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP)",
                batchNo, period, runPrefix, runPrefix);
    }

    private static void insertVersion(String versionCode, String perspective, String basePeriod,
                                      String status, boolean official, String uploadedBy,
                                      String publishedBy, String publishedAt) {
        LocalDateTime uploadedTime = LocalDateTime.of(2026, 1, 10, 10, 0);
        String coverageEnd = YearMonth.parse(basePeriod).plusMonths(11).toString();
        if (publishedAt != null) {
            jdbc.update("INSERT INTO calc.fc_version "
                            + "(version_code, perspective, base_period, coverage_start, coverage_end, status, "
                            + "uploaded_by, uploaded_at, official, published_by, published_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::timestamp, ?::timestamp)",
                    versionCode, perspective, basePeriod, basePeriod, coverageEnd, status,
                    uploadedBy, uploadedTime, official, publishedBy, publishedAt, publishedAt);
            return;
        }
        jdbc.update("INSERT INTO calc.fc_version "
                        + "(version_code, perspective, base_period, coverage_start, coverage_end, status, "
                        + "uploaded_by, uploaded_at, official, published_by, published_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                versionCode, perspective, basePeriod, basePeriod, coverageEnd, status,
                uploadedBy, uploadedTime, official, publishedBy, null);
    }

    private static String batchNo(String suffix) {
        return runPrefix + "-BATCH-" + suffix;
    }

    private static String versionCode(String suffix) {
        return runPrefix + "-VERSION-" + suffix;
    }

    private static String period(int offset) {
        return periodSeed.plusMonths(offset).toString();
    }

    private static String environment(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value;
    }
}
