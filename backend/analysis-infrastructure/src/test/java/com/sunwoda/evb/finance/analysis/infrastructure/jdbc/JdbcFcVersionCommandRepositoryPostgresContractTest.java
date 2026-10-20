package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.FcSnapshot;
import com.sunwoda.evb.finance.analysis.domain.model.FcVersion;
import org.springframework.dao.EmptyResultDataAccessException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 基于真实 PostgreSQL 的 FC 版本命令仓储契约；只验证现有迁移和实现行为。
 *
 * <p>本次修改：补齐快照首次写入、同业务键覆盖、影响条数和缺失版本异常契约；
 * 每次运行随机生成合法基准期间，并将数据库连接或 schema 缺失视为硬失败，
 * 与 CI 的 PostgreSQL 契约门禁保持一致。</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcFcVersionCommandRepositoryPostgresContractTest {
    private static final String FC_VERSION_TABLE = "calc.fc_version";
    private static final String FC_SNAPSHOT_TABLE = "calc.fc_snapshot";
    private static final AnalysisPerspective PERSPECTIVE = AnalysisPerspective.BASE;

    private DataSource dataSource;
    private JdbcTemplate jdbc;
    private JdbcFcVersionCommandRepository repository;
    private String versionPrefix;
    private YearMonth baseYearMonth;
    private String basePeriod;
    private String coverageStart;
    private String coverageEnd;

    @BeforeAll
    void connectToConfiguredPostgres() {
        // 本次修改：按 CI 门禁要求，PostgreSQL 连接或 schema 缺失必须让本文件失败，不允许静默跳过。
        String url = System.getenv("AMOEBA_DB_URL");
        if (url == null || url.trim().isEmpty()) {
            fail("BLOCKED: AMOEBA_DB_URL is not set; FC version PostgreSQL contract must fail");
        }

        dataSource = new DriverManagerDataSource(
                url, environment("AMOEBA_DB_USERNAME"), environment("AMOEBA_DB_PASSWORD"));
        jdbc = new JdbcTemplate(dataSource);
        try {
            jdbc.execute("SELECT id, version_code, perspective, base_period, official "
                    + "FROM " + FC_VERSION_TABLE + " LIMIT 0");
            jdbc.execute("SELECT id, fc_version_id, period, scope_code, metric_code, metric_value, "
                    + "source_task_no FROM " + FC_SNAPSHOT_TABLE + " LIMIT 0");
        } catch (RuntimeException failure) {
            fail("BLOCKED: FC version PostgreSQL schema is not available: "
                    + failure.getMessage(), failure);
        }

        repository = new JdbcFcVersionCommandRepository(dataSource);
        versionPrefix = "TEST-FC-CMD-" + UUID.randomUUID().toString().replace("-", "");
        baseYearMonth = randomYearMonth();
        basePeriod = baseYearMonth.toString();
        coverageStart = baseYearMonth.plusMonths(1).toString();
        coverageEnd = baseYearMonth.plusMonths(11).toString();
    }

    @AfterEach
    void cleanupAfterTest() {
        // 本次修改：先删除快照再删除版本，保证新增 upsert 契约的测试数据可安全清理。
        deleteTestVersions();
    }

    @AfterAll
    void cleanupAfterAll() {
        // 本次修改：为测试异常中断后的统一收尾保留同一安全清理路径。
        deleteTestVersions();
    }

    @Test
    void createPersistsDraftVersionAndFindRehydratesMetadata() {
        // 本次修改：验证 JdbcFcVersionCommandRepository.create 在真实 PostgreSQL 中写入并回读草稿版本元数据。
        String versionCode = versionCode("CREATE");

        FcVersion created = repository.create(versionCode, PERSPECTIVE, basePeriod,
                coverageStart, coverageEnd, "fc-contract-creator");

        assertEquals(versionCode, created.getVersionCode());
        assertEquals(PERSPECTIVE, created.getPerspective());
        assertEquals(basePeriod, created.getBasePeriod());
        assertEquals(coverageStart, created.getCoverageStart());
        assertEquals(coverageEnd, created.getCoverageEnd());
        assertEquals("DRAFT", created.getStatus());
        assertEquals("fc-contract-creator", created.getUploadedBy());
        assertNotNull(created.getUploadedAt());
        assertFalse(created.isOfficial());
        assertNull(created.getPublishedBy());
        assertNull(created.getPublishedAt());

        FcVersion found = repository.find(versionCode, PERSPECTIVE, basePeriod)
                .orElseThrow(() -> new AssertionError("created FC version was not found"));
        assertEquals(versionCode, found.getVersionCode());
        assertEquals("DRAFT", found.getStatus());
        assertFalse(found.isOfficial());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM " + FC_VERSION_TABLE
                + " WHERE version_code = ? AND perspective = ? AND base_period = ?",
                Integer.class, versionCode, PERSPECTIVE.getCode(), basePeriod));
    }

    @Test
    void createWithSameVersionCodePerspectiveAndPeriodReportsExistingUniqueConstraintBehavior() {
        // 本次修改：验证 V014 的版本编码、视角和基准期间唯一约束由仓储转换为现有 IllegalStateException，而不是假设幂等。
        String versionCode = versionCode("DUPLICATE");
        repository.create(versionCode, PERSPECTIVE, basePeriod,
                coverageStart, coverageEnd, "fc-contract-first");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> repository.create(versionCode, PERSPECTIVE, basePeriod,
                        coverageStart, coverageEnd, "fc-contract-second"));

        assertEquals("FC版本已存在: " + versionCode, failure.getMessage());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM " + FC_VERSION_TABLE
                + " WHERE version_code = ? AND perspective = ? AND base_period = ?",
                Integer.class, versionCode, PERSPECTIVE.getCode(), basePeriod));
    }

    @Test
    void publishMarksExistingVersionOfficialAndRecordsOperator() {
        // 本次修改：验证现有 publish 行为将指定草稿更新为 PUBLISHED/official，并记录发布人和时间。
        String versionCode = versionCode("PUBLISH");
        repository.create(versionCode, PERSPECTIVE, basePeriod,
                coverageStart, coverageEnd, "fc-contract-uploader");

        FcVersion published = repository.publish(versionCode, PERSPECTIVE, basePeriod,
                "fc-contract-publisher");

        assertEquals(versionCode, published.getVersionCode());
        assertEquals("PUBLISHED", published.getStatus());
        assertTrue(published.isOfficial());
        assertEquals("fc-contract-publisher", published.getPublishedBy());
        assertNotNull(published.getPublishedAt());

        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM " + FC_VERSION_TABLE
                + " WHERE version_code = ? AND perspective = ? AND base_period = ? "
                + "AND status = 'PUBLISHED' AND official = TRUE AND published_by = ?",
                Integer.class, versionCode, PERSPECTIVE.getCode(), basePeriod,
                "fc-contract-publisher"));
    }

    @Test
    void publishRejectsSecondOfficialVersionForSamePerspectiveAndPeriod() {
        // 本次修改：验证现有正式版本冲突保护，不自行定义替换、回滚或并发治理规则。
        String firstVersion = versionCode("OFFICIAL-FIRST");
        String secondVersion = versionCode("OFFICIAL-SECOND");
        repository.create(firstVersion, PERSPECTIVE, basePeriod,
                coverageStart, coverageEnd, "fc-contract-uploader");
        repository.create(secondVersion, PERSPECTIVE, basePeriod,
                coverageStart, coverageEnd, "fc-contract-uploader");
        repository.publish(firstVersion, PERSPECTIVE, basePeriod, "fc-contract-publisher");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> repository.publish(secondVersion, PERSPECTIVE, basePeriod,
                        "fc-contract-second-publisher"));

        assertEquals("同一视角和基准期间已有正式 FC 版本，请先明确替换策略", failure.getMessage());
        FcVersion second = repository.find(secondVersion, PERSPECTIVE, basePeriod)
                .orElseThrow(() -> new AssertionError("second FC version was not found"));
        assertEquals("DRAFT", second.getStatus());
        assertFalse(second.isOfficial());
    }

    @Test
    void upsertSnapshotsInsertsAndOverwritesSameBusinessKeyWithAffectedRows() {
        // 本次修改：验证 V015 快照首次写入、同一版本/期间/范围/指标业务键覆盖数值与来源，并校验返回影响条数。
        String versionCode = versionCode("SNAPSHOT");
        repository.create(versionCode, PERSPECTIVE, basePeriod,
                coverageStart, coverageEnd, "fc-contract-uploader");
        String snapshotPeriod = basePeriod;
        String secondSnapshotPeriod = baseYearMonth.plusMonths(1).toString();

        int inserted = repository.upsertSnapshots(versionCode, PERSPECTIVE, basePeriod,
                Arrays.asList(
                        new FcSnapshot(snapshotPeriod, "SCOPE-A", "METRIC-REVENUE",
                                new BigDecimal("123.45")),
                        new FcSnapshot(secondSnapshotPeriod, "SCOPE-B", "METRIC-COST",
                                new BigDecimal("67.89"))),
                "TASK-FC-INITIAL");

        assertEquals(2, inserted);
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM " + FC_SNAPSHOT_TABLE
                + " WHERE fc_version_id = (SELECT id FROM " + FC_VERSION_TABLE
                + " WHERE version_code = ? AND perspective = ? AND base_period = ?)",
                Integer.class, versionCode, PERSPECTIVE.getCode(), basePeriod));
        assertSnapshot("SCOPE-A", "METRIC-REVENUE", snapshotPeriod,
                new BigDecimal("123.45"), "TASK-FC-INITIAL", versionCode);

        int overwritten = repository.upsertSnapshots(versionCode, PERSPECTIVE, basePeriod,
                Arrays.asList(new FcSnapshot(snapshotPeriod, "SCOPE-A", "METRIC-REVENUE",
                        new BigDecimal("456.78"))), "TASK-FC-REPLACEMENT");

        assertEquals(1, overwritten);
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM " + FC_SNAPSHOT_TABLE
                + " WHERE fc_version_id = (SELECT id FROM " + FC_VERSION_TABLE
                + " WHERE version_code = ? AND perspective = ? AND base_period = ?)",
                Integer.class, versionCode, PERSPECTIVE.getCode(), basePeriod));
        assertSnapshot("SCOPE-A", "METRIC-REVENUE", snapshotPeriod,
                new BigDecimal("456.78"), "TASK-FC-REPLACEMENT", versionCode);
    }

    @Test
    void upsertSnapshotsForMissingVersionReportsExistingPostgresLookupException() {
        // 本次修改：验证缺失 FC 版本时，当前 queryForObject 实现实际抛出 EmptyResultDataAccessException，不将其假设为其他业务异常。
        String versionCode = versionCode("MISSING-SNAPSHOT");
        String sourceTaskNo = versionCode("TASK-MISSING");

        assertThrows(EmptyResultDataAccessException.class,
                () -> repository.upsertSnapshots(versionCode, PERSPECTIVE, basePeriod,
                        Arrays.asList(new FcSnapshot(basePeriod, "SCOPE-A", "METRIC-REVENUE",
                                new BigDecimal("1.00"))), sourceTaskNo));

        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM " + FC_SNAPSHOT_TABLE
                + " WHERE source_task_no = ?", Integer.class, sourceTaskNo));
    }

    @Test
    void publishUnknownVersionReportsExistingNotFoundBehavior() {
        // 本次修改：验证发布不存在的版本时沿用仓储已有 IllegalArgumentException 行为。
        String versionCode = versionCode("MISSING");

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> repository.publish(versionCode, PERSPECTIVE, basePeriod,
                        "fc-contract-publisher"));

        assertEquals("FC版本不存在: " + versionCode, failure.getMessage());
    }

    private String versionCode(String suffix) {
        return versionPrefix + "-" + suffix;
    }

    private void assertSnapshot(String scopeCode, String metricCode, String period,
                                BigDecimal expectedValue, String expectedSourceTaskNo,
                                String versionCode) {
        BigDecimal actualValue = jdbc.queryForObject("SELECT metric_value FROM " + FC_SNAPSHOT_TABLE
                + " WHERE fc_version_id = (SELECT id FROM " + FC_VERSION_TABLE
                + " WHERE version_code = ? AND perspective = ? AND base_period = ?)"
                + " AND period = ? AND scope_code = ? AND metric_code = ?",
                BigDecimal.class, versionCode, PERSPECTIVE.getCode(), basePeriod,
                period, scopeCode, metricCode);
        assertNotNull(actualValue);
        assertEquals(0, actualValue.compareTo(expectedValue));
        assertEquals(expectedSourceTaskNo, jdbc.queryForObject("SELECT source_task_no FROM "
                + FC_SNAPSHOT_TABLE
                + " WHERE fc_version_id = (SELECT id FROM " + FC_VERSION_TABLE
                + " WHERE version_code = ? AND perspective = ? AND base_period = ?)"
                + " AND period = ? AND scope_code = ? AND metric_code = ?",
                String.class, versionCode, PERSPECTIVE.getCode(), basePeriod,
                period, scopeCode, metricCode));
    }

    private YearMonth randomYearMonth() {
        // 本次修改：使用 UUID 派生 2000-2100 年内的合法随机 YearMonth，降低并行测试争用正式版本唯一索引的概率。
        UUID random = UUID.randomUUID();
        int year = 2000 + (int) Math.floorMod(random.getMostSignificantBits(), 101);
        int month = 1 + (int) Math.floorMod(random.getLeastSignificantBits(), 12);
        return YearMonth.of(year, month);
    }

    private void deleteTestVersions() {
        if (jdbc != null && versionPrefix != null) {
            jdbc.update("DELETE FROM " + FC_SNAPSHOT_TABLE + " WHERE fc_version_id IN "
                    + "(SELECT id FROM " + FC_VERSION_TABLE + " WHERE version_code LIKE ?)",
                    versionPrefix + "%");
            jdbc.update("DELETE FROM " + FC_VERSION_TABLE + " WHERE version_code LIKE ?",
                    versionPrefix + "%");
        }
    }

    private static String environment(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value;
    }
}
