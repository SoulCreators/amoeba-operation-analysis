package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.FcSnapshot;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V13-FC-JDBC-001-C：验证 FC 快照 JDBC 查询已有的用户范围过滤和排序行为。
 *
 * <p>该契约只使用真实 PostgreSQL 和现有 V014/V015、V003 表结构，不扩展权限矩阵，
 * 也不修改 FC 版本治理或快照查询生产实现。</p>
 */
class JdbcFcSnapshotProviderScopePostgresContractTest {
    private static final String PERSPECTIVE = AnalysisPerspective.BASE.getCode();
    private static final String SCOPE_A = "FC-SCOPE-A";
    private static final String SCOPE_B = "FC-SCOPE-B";
    private static final String SCOPE_C = "FC-SCOPE-C";

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;
    private static JdbcFcSnapshotProvider provider;
    private static YearMonth runPeriod;
    private static String batchNo;
    private static String versionCode;
    private static String allUser;
    private static String singleScopeUser;
    private static String crossScopeUser;
    private static String disabledScopeUser;

    @BeforeAll
    static void connectAndSeedConfiguredPostgres() {
        String url = requiredEnvironment("AMOEBA_DB_URL");
        dataSource = new DriverManagerDataSource(url,
                requiredEnvironment("AMOEBA_DB_USERNAME"), environment("AMOEBA_DB_PASSWORD"));
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("SELECT id FROM meta.analysis_batch LIMIT 0");
        jdbc.execute("SELECT id FROM calc.fc_version LIMIT 0");
        jdbc.execute("SELECT id FROM calc.fc_snapshot LIMIT 0");
        jdbc.execute("SELECT id FROM iam.user_scope LIMIT 0");

        // 本次修改：为每次测试运行生成合法且随机的 YearMonth，降低并行夹具的期间冲突概率。
        runPeriod = randomRunPeriod();
        String token = UUID.randomUUID().toString().replace("-", "");
        batchNo = "TEST-FC-SNAPSHOT-SCOPE-" + token;
        versionCode = "TEST-FC-V" + runPeriod.toString().replace("-", "") + "-" + token;
        allUser = "TEST-FC-SNAPSHOT-ALL-" + token;
        singleScopeUser = "TEST-FC-SNAPSHOT-SINGLE-" + token;
        crossScopeUser = "TEST-FC-SNAPSHOT-CROSS-" + token;
        disabledScopeUser = "TEST-FC-SNAPSHOT-DISABLED-" + token;

        seedBatch();
        Long versionId = seedVersion();
        seedSnapshots(versionId);
        seedScopeGrants();
        provider = new JdbcFcSnapshotProvider(dataSource);
    }

    @AfterAll
    static void cleanupFixture() {
        if (jdbc == null || versionCode == null) return;
        jdbc.update("DELETE FROM calc.fc_snapshot WHERE fc_version_id IN "
                + "(SELECT id FROM calc.fc_version WHERE version_code = ?)", versionCode);
        jdbc.update("DELETE FROM calc.fc_version WHERE version_code = ?", versionCode);
        jdbc.update("DELETE FROM iam.user_scope WHERE user_id IN (?, ?, ?, ?)",
                allUser, singleScopeUser, crossScopeUser, disabledScopeUser);
        jdbc.update("DELETE FROM meta.analysis_batch WHERE batch_no = ?", batchNo);
    }

    @Test
    void allScopeGrantReturnsEverySnapshot() {
        // 本次修改：验证 ALL 范围授权只返回当前批次和 FC 版本的全部快照。
        List<FcSnapshot> snapshots = provider.findForBatch(
                batchNo, AnalysisPerspective.BASE, versionCode, allUser);

        assertEquals(Arrays.asList(
                key(SCOPE_A, "METRIC-A"), key(SCOPE_A, "METRIC-B"),
                key(SCOPE_B, "METRIC-A"), key(SCOPE_B, "METRIC-B"),
                key(SCOPE_C, "METRIC-A")), keys(snapshots));
        assertEquals(5, snapshots.size());
    }

    @Test
    void singleScopeGrantReturnsOnlyItsScope() {
        // 本次修改：验证单范围授权不会读取其他范围的 FC 快照。
        List<FcSnapshot> snapshots = provider.findForBatch(
                batchNo, AnalysisPerspective.BASE, versionCode, singleScopeUser);

        assertEquals(Arrays.asList(key(SCOPE_A, "METRIC-A"), key(SCOPE_A, "METRIC-B")),
                keys(snapshots));
        assertTrue(snapshots.stream().allMatch(snapshot -> SCOPE_A.equals(snapshot.getScopeCode())));
    }

    @Test
    void crossScopeGrantsReturnEachEnabledAuthorizedScope() {
        // 本次修改：验证多个范围授权只合并已授权范围，不越权读取未授权范围。
        List<FcSnapshot> snapshots = provider.findForBatch(
                batchNo, AnalysisPerspective.BASE, versionCode, crossScopeUser);

        assertEquals(Arrays.asList(
                key(SCOPE_A, "METRIC-A"), key(SCOPE_A, "METRIC-B"),
                key(SCOPE_B, "METRIC-A"), key(SCOPE_B, "METRIC-B")), keys(snapshots));
        assertTrue(snapshots.stream().noneMatch(snapshot -> SCOPE_C.equals(snapshot.getScopeCode())));
    }

    @Test
    void disabledScopeGrantDoesNotExposeSnapshots() {
        // 本次修改：验证 disabled 范围授权不会暴露该范围的 FC 快照。
        List<FcSnapshot> snapshots = provider.findForBatch(
                batchNo, AnalysisPerspective.BASE, versionCode, disabledScopeUser);

        assertTrue(snapshots.isEmpty(), "disabled scope grant must not expose snapshots");
    }

    @Test
    void orderingIsDeterministicByScopeThenMetricCode() {
        // 本次修改：验证现有 ORDER BY scope_code, metric_code 在重复读取时保持确定性顺序。
        List<String> firstRead = keys(provider.findForBatch(
                batchNo, AnalysisPerspective.BASE, versionCode, allUser));
        List<String> secondRead = keys(provider.findForBatch(
                batchNo, AnalysisPerspective.BASE, versionCode, allUser));

        assertEquals(firstRead, secondRead);
        assertEquals(Arrays.asList(
                key(SCOPE_A, "METRIC-A"), key(SCOPE_A, "METRIC-B"),
                key(SCOPE_B, "METRIC-A"), key(SCOPE_B, "METRIC-B"),
                key(SCOPE_C, "METRIC-A")), firstRead);
    }

    private static void seedBatch() {
        jdbc.update("INSERT INTO meta.analysis_batch(batch_no, period, status, perspective, created_by) "
                        + "VALUES (?, ?, 'DRAFT', ?, ?)",
                batchNo, runPeriod.toString(), PERSPECTIVE, "V13-FC-JDBC-001-C");
    }

    private static Long seedVersion() {
        return jdbc.queryForObject("INSERT INTO calc.fc_version(version_code, perspective, base_period, "
                        + "coverage_start, coverage_end, status, uploaded_by, official) "
                        + "VALUES (?, ?, ?, ?, ?, 'PUBLISHED', ?, TRUE) RETURNING id",
                Long.class, versionCode, PERSPECTIVE, runPeriod.toString(), runPeriod.toString(),
                runPeriod.plusMonths(2).toString(),
                "V13-FC-JDBC-001-C");
    }

    private static void seedSnapshots(Long versionId) {
        insertSnapshot(versionId, SCOPE_A, "METRIC-B", "2.00000000");
        insertSnapshot(versionId, SCOPE_B, "METRIC-A", "3.00000000");
        insertSnapshot(versionId, SCOPE_A, "METRIC-A", "1.00000000");
        insertSnapshot(versionId, SCOPE_B, "METRIC-B", "4.00000000");
        insertSnapshot(versionId, SCOPE_C, "METRIC-A", "5.00000000");
    }

    private static void insertSnapshot(Long versionId, String scopeCode,
                                       String metricCode, String value) {
        jdbc.update("INSERT INTO calc.fc_snapshot(fc_version_id, period, scope_code, metric_code, "
                        + "metric_value, source_task_no) VALUES (?, ?, ?, ?, ?, ?)",
                versionId, runPeriod.toString(), scopeCode, metricCode, new BigDecimal(value),
                "TASK-V13-FC-JDBC-001-C");
    }

    private static void seedScopeGrants() {
        grant(allUser, "ALL", true);
        grant(singleScopeUser, SCOPE_A, true);
        grant(crossScopeUser, SCOPE_A, true);
        grant(crossScopeUser, SCOPE_B, true);
        grant(disabledScopeUser, SCOPE_C, false);
    }

    private static void grant(String userId, String scopeCode, boolean enabled) {
        jdbc.update("INSERT INTO iam.user_scope(user_id, role_code, perspective, scope_code, "
                        + "can_view_detail, can_export, can_edit_config, enabled) "
                        + "VALUES (?, 'TEST_FC_SNAPSHOT_VIEWER', ?, ?, FALSE, FALSE, FALSE, ?)",
                userId, PERSPECTIVE, scopeCode, enabled);
    }

    private static List<String> keys(List<FcSnapshot> snapshots) {
        return snapshots.stream()
                .map(snapshot -> key(snapshot.getScopeCode(), snapshot.getMetricCode()))
                .collect(Collectors.toList());
    }

    private static String key(String scopeCode, String metricCode) {
        return scopeCode + "/" + metricCode;
    }

    private static YearMonth randomRunPeriod() {
        YearMonth current = YearMonth.now();
        return current.plusMonths(ThreadLocalRandom.current().nextLong(0, 121));
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException(name
                    + " must be set for the real PostgreSQL FC snapshot scope contract test");
        }
        return value;
    }

    private static String environment(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value;
    }
}
