package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.PnlFactSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * TRACE-JDBC-PG-SEC-001：验证真实 PostgreSQL 上 JdbcPnlFactSourceProvider 的数据库侧范围过滤。
 *
 * <p>本测试只新增真实数据库契约，不修改生产 SQL、迁移或其他测试；夹具覆盖精确范围、ALL
 * 范围、disabled、can_view_detail=false，以及批次、视角、任务三层隔离。</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcPnlFactSourceProviderPostgresSecurityContractTest {
    private static final String BASE = AnalysisPerspective.BASE.getCode();
    private static final String PRODUCT_LINE = AnalysisPerspective.PRODUCT_LINE.getCode();

    private DataSource dataSource;
    private JdbcTemplate jdbc;
    private JdbcPnlFactSourceProvider provider;
    private String token;
    private String targetBatchNo;
    private String otherBatchNo;
    private String targetTaskNo;
    private String otherTaskNo;
    private String otherPerspectiveTaskNo;
    private String otherBatchTaskNo;
    private String scopeA;
    private String scopeB;
    private String scopeC;
    private String exactScopeUser;
    private String allScopeUser;
    private String disabledUser;
    private String noDetailUser;
    private String adminUser;
    private String adminNoDetailUser;
    private String disabledAdminUser;
    private final List<String> fixtureTaskNos = new ArrayList<String>();
    private final List<String> fixtureBatchNos = new ArrayList<String>();
    private final List<String> fixtureUserIds = new ArrayList<String>();

    @BeforeAll
    void connectToConfiguredPostgresAndVerifySchema() {
        // 本次修改：数据库配置缺失时用 assume 跳过，明确表示真实契约未运行，避免伪造成功。
        String url = System.getenv("AMOEBA_DB_URL");
        assumeTrue(url != null && !url.trim().isEmpty(),
                "BLOCKED: AMOEBA_DB_URL is not set; TRACE-JDBC-PG-SEC-001 was not run");

        dataSource = new DriverManagerDataSource(url,
                environment("AMOEBA_DB_USERNAME"), environment("AMOEBA_DB_PASSWORD"));
        jdbc = new JdbcTemplate(dataSource);
        try {
            // 本次修改：按 V002/V003/V004/V017 的真实表结构门禁，缺迁移时跳过而不是伪造通过。
            jdbc.execute("SELECT id, batch_no, period, perspective FROM meta.analysis_batch LIMIT 0");
            jdbc.execute("SELECT task_no, batch_id, dataset_code FROM flow.import_task LIMIT 0");
            jdbc.execute("SELECT id, batch_id, perspective, source_task_no, scope_code "
                    + "FROM raw.pnl_fact_trace LIMIT 0");
            jdbc.execute("SELECT user_id, perspective, scope_code, can_view_detail, enabled "
                    + "FROM iam.user_scope LIMIT 0");
        } catch (RuntimeException failure) {
            assumeTrue(false, "BLOCKED: V002/V003/V004/V017 PostgreSQL schema is not available: "
                    + failure.getMessage());
        }

        // 本次修改：生产适配器直接连接真实 DataSource，确保断言来自当前 JdbcPnlFactSourceProvider。
        provider = new JdbcPnlFactSourceProvider(dataSource);
    }

    @AfterEach
    void cleanupFixtureRows() {
        // 本次修改：按随机任务、用户和批次逆向清理外键关联，避免污染共享 PostgreSQL。
        if (jdbc == null || token == null) return;
        for (String taskNo : fixtureTaskNos) {
            jdbc.update("DELETE FROM raw.pnl_fact_trace WHERE source_task_no = ?", taskNo);
            jdbc.update("DELETE FROM flow.import_task WHERE task_no = ?", taskNo);
        }
        for (String userId : fixtureUserIds) {
            jdbc.update("DELETE FROM iam.user_scope WHERE user_id = ?", userId);
        }
        for (String batchNo : fixtureBatchNos) {
            jdbc.update("DELETE FROM meta.analysis_batch WHERE batch_no = ?", batchNo);
            assertEquals(0, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM meta.analysis_batch WHERE batch_no = ?", Integer.class, batchNo));
        }
        // 本次修改：清理后按本次 UUID token 反查三张夹具表，确保没有遗漏未登记的随机行。
        String tokenLike = "%" + token;
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM raw.pnl_fact_trace WHERE source_task_no LIKE ?",
                Integer.class, tokenLike));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM flow.import_task WHERE task_no LIKE ?",
                Integer.class, tokenLike));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM iam.user_scope WHERE user_id LIKE ?",
                Integer.class, tokenLike));
        fixtureTaskNos.clear();
        fixtureBatchNos.clear();
        fixtureUserIds.clear();
    }

    @Test
    void filtersTraceRowsByExactAllAndDisabledDetailScopeAndIsolationDimensions() {
        // 本次修改：一次真实查询夹具同时锁定精确 scope、ALL scope 以及批次/视角/任务隔离。
        seedRandomFixture();

        List<PnlFactSource> exactScopeRows = provider.findForUser(
                targetBatchNo, AnalysisPerspective.BASE, targetTaskNo, exactScopeUser);
        assertEquals(Arrays.asList(key(scopeA, 2)), keys(exactScopeRows));
        assertSource(exactScopeRows.get(0), targetTaskNo, "target.xlsx", "实际明细", 2, scopeA);

        List<PnlFactSource> allScopeRows = provider.findForUser(
                targetBatchNo, AnalysisPerspective.BASE, targetTaskNo, allScopeUser);
        assertEquals(Arrays.asList(key(scopeA, 2), key(scopeB, 3), key(scopeC, 4)),
                keys(allScopeRows));
        assertTrue(allScopeRows.stream().allMatch(row -> targetTaskNo.equals(row.getTaskNo())),
                "ALL scope must still be limited to the requested task");
        assertTrue(allScopeRows.stream().allMatch(row -> "target.xlsx".equals(row.getFileName())),
                "ALL scope must still be limited to the requested batch/perspective/task rows");

        // 本次修改：disabled 和 can_view_detail=false 均必须在数据库 EXISTS 授权条件中阻断明细。
        assertTrue(provider.findForUser(targetBatchNo, AnalysisPerspective.BASE,
                targetTaskNo, disabledUser).isEmpty(),
                "disabled scope grant must not expose trace rows");
        assertTrue(provider.findForUser(targetBatchNo, AnalysisPerspective.BASE,
                targetTaskNo, noDetailUser).isEmpty(),
                "can_view_detail=false grant must not expose trace rows");

        // 本次修改：验证启用 ADMIN 可跨越具体 scope 读取同一 perspective 的全部来源行。
        List<PnlFactSource> adminRows = provider.findForUser(
                targetBatchNo, AnalysisPerspective.BASE, targetTaskNo, adminUser);
        assertEquals(Arrays.asList(key(scopeA, 2), key(scopeB, 3), key(scopeC, 4)), keys(adminRows));
        // 本次修改：ADMIN 不得绕过 can_view_detail 和 enabled 布尔门禁。
        assertTrue(provider.findForUser(targetBatchNo, AnalysisPerspective.BASE,
                targetTaskNo, adminNoDetailUser).isEmpty(),
                "ADMIN with can_view_detail=false must not expose trace rows");
        assertTrue(provider.findForUser(targetBatchNo, AnalysisPerspective.BASE,
                targetTaskNo, disabledAdminUser).isEmpty(),
                "disabled ADMIN grant must not expose trace rows");

        // 本次修改：目标任务之外的同批次任务、其他视角和其他批次数据均写入真实表，验证不会串读。
        assertEquals(Arrays.asList(key(scopeA, 10)),
                keys(provider.findForUser(targetBatchNo, AnalysisPerspective.BASE,
                        otherTaskNo, allScopeUser)));
        // 本次修改：正交验证目标批次 + BASE + 其他批次 task 不得返回任何来源行。
        assertTrue(provider.findForUser(targetBatchNo, AnalysisPerspective.BASE,
                otherBatchTaskNo, allScopeUser).isEmpty(),
                "a task belonging to another batch must be empty for the target batch");
        // 本次修改：正交验证目标批次 + BASE + PRODUCT_LINE task 不得因 task_no 命中而越过视角过滤。
        assertTrue(provider.findForUser(targetBatchNo, AnalysisPerspective.BASE,
                otherPerspectiveTaskNo, allScopeUser).isEmpty(),
                "a PRODUCT_LINE task must be empty for a BASE perspective request");
        // 本次修改：正交验证目标批次 + PRODUCT_LINE + BASE 用户授权不得暴露产品线来源行。
        assertTrue(provider.findForUser(targetBatchNo, AnalysisPerspective.PRODUCT_LINE,
                otherPerspectiveTaskNo, allScopeUser).isEmpty(),
                "a BASE grant must be empty for a PRODUCT_LINE request");
        assertEquals(Arrays.asList(key(scopeA, 20)),
                keys(provider.findForUser(targetBatchNo, AnalysisPerspective.PRODUCT_LINE,
                        otherPerspectiveTaskNo, perspectiveUser())));
        assertEquals(Arrays.asList(key(scopeA, 30)),
                keys(provider.findForUser(otherBatchNo, AnalysisPerspective.BASE,
                        otherBatchTaskNo, allScopeUser)));
        // 本次修改：同一 source_task_no 写入不同 perspective，确认请求参数由 t.perspective 过滤而非只按 task 隔离。
        assertEquals(Arrays.asList(key(scopeB, 5)),
                keys(provider.findForUser(targetBatchNo, AnalysisPerspective.PRODUCT_LINE,
                        targetTaskNo, perspectiveUser())));
    }

    private void seedRandomFixture() {
        // 本次修改：每次测试使用 UUID 后缀生成唯一批次、任务、用户和范围，支持共享库及并行执行。
        token = UUID.randomUUID().toString().replace("-", "");
        scopeA = "TJPS-S-A-" + token;
        scopeB = "TJPS-S-B-" + token;
        scopeC = "TJPS-S-C-" + token;
        exactScopeUser = "TJPS-U-EXACT-" + token;
        allScopeUser = "TJPS-U-ALL-" + token;
        disabledUser = "TJPS-U-DISABLED-" + token;
        noDetailUser = "TJPS-U-NODETAIL-" + token;
        adminUser = "TJPS-U-ADMIN-" + token;
        adminNoDetailUser = "TJPS-U-ADMIN-NODETAIL-" + token;
        disabledAdminUser = "TJPS-U-ADMIN-DISABLED-" + token;

        // 本次修改：缩短授权主体、批次和任务标识前缀，确保拼接 UUID 后符合迁移中的 varchar(64)。
        targetBatchNo = "TJPS-B-" + token;
        otherBatchNo = "TJPS-OB-" + token;
        targetTaskNo = "TJPS-T-" + token;
        otherTaskNo = "TJPS-OT-" + token;
        otherPerspectiveTaskNo = "TJPS-PT-" + token;
        otherBatchTaskNo = "TJPS-OBT-" + token;
        assertFixtureIdentifierLengths();

        fixtureBatchNos.add(targetBatchNo);
        fixtureBatchNos.add(otherBatchNo);
        fixtureUserIds.add(exactScopeUser);
        fixtureUserIds.add(allScopeUser);
        fixtureUserIds.add(disabledUser);
        fixtureUserIds.add(noDetailUser);
        fixtureUserIds.add(adminUser);
        fixtureUserIds.add(adminNoDetailUser);
        fixtureUserIds.add(disabledAdminUser);

        insertBatch(targetBatchNo, BASE);
        insertBatch(otherBatchNo, BASE);
        insertTask(targetBatchNo, targetTaskNo, "TRACE_TARGET");
        insertTask(targetBatchNo, otherTaskNo, "TRACE_OTHER_TASK");
        insertTask(targetBatchNo, otherPerspectiveTaskNo, "TRACE_OTHER_PERSPECTIVE");
        insertTask(otherBatchNo, otherBatchTaskNo, "TRACE_OTHER_BATCH");

        insertTrace(targetBatchNo, BASE, targetTaskNo, scopeA, 2, "target.xlsx");
        insertTrace(targetBatchNo, BASE, targetTaskNo, scopeB, 3, "target.xlsx");
        insertTrace(targetBatchNo, BASE, targetTaskNo, scopeC, 4, "target.xlsx");
        // 本次修改：同一任务增加一条 PRODUCT_LINE 来源行，专门验证 t.perspective 的正交过滤。
        insertTrace(targetBatchNo, PRODUCT_LINE, targetTaskNo, scopeB, 5, "target-product-line.xlsx");
        insertTrace(targetBatchNo, BASE, otherTaskNo, scopeA, 10, "other-task.xlsx");
        insertTrace(targetBatchNo, PRODUCT_LINE, otherPerspectiveTaskNo,
                scopeA, 20, "other-perspective.xlsx");
        insertTrace(otherBatchNo, BASE, otherBatchTaskNo, scopeA, 30, "other-batch.xlsx");

        grant(exactScopeUser, BASE, scopeA, true, true);
        grant(allScopeUser, BASE, "ALL", true, true);
        grant(disabledUser, BASE, scopeA, false, true);
        grant(noDetailUser, BASE, scopeA, true, false);
        grant(adminUser, BASE, scopeA, true, true, "ADMIN");
        grant(adminNoDetailUser, BASE, scopeA, true, false, "ADMIN");
        grant(disabledAdminUser, BASE, scopeA, false, true, "ADMIN");
        grant(perspectiveUser(), PRODUCT_LINE, "ALL", true, true);
    }

    private String perspectiveUser() {
        // 本次修改：为其他视角使用独立授权主体，避免把 BASE 授权误当成跨视角授权。
        return "TJPS-U-PERSPECTIVE-" + token;
    }

    private void assertFixtureIdentifierLengths() {
        // 本次修改：在写库前锁定 V002/V004/V017/V003 中批次、任务、范围和用户字段的长度上限。
        assertIdentifierLength("batchNo", targetBatchNo, 64);
        assertIdentifierLength("otherBatchNo", otherBatchNo, 64);
        assertIdentifierLength("targetTaskNo", targetTaskNo, 64);
        assertIdentifierLength("otherTaskNo", otherTaskNo, 64);
        assertIdentifierLength("otherPerspectiveTaskNo", otherPerspectiveTaskNo, 64);
        assertIdentifierLength("otherBatchTaskNo", otherBatchTaskNo, 64);
        assertIdentifierLength("scopeA", scopeA, 128);
        assertIdentifierLength("scopeB", scopeB, 128);
        assertIdentifierLength("scopeC", scopeC, 128);
        assertIdentifierLength("exactScopeUser", exactScopeUser, 64);
        assertIdentifierLength("allScopeUser", allScopeUser, 64);
        assertIdentifierLength("disabledUser", disabledUser, 64);
        assertIdentifierLength("noDetailUser", noDetailUser, 64);
        assertIdentifierLength("perspectiveUser", perspectiveUser(), 64);
    }

    private void assertIdentifierLength(String fieldName, String value, int maxLength) {
        // 本次修改：将数据库 varchar 上限转成夹具自检，避免再次在 grant 或 insert 时才暴露长度错误。
        assertTrue(value.length() <= maxLength,
                fieldName + " fixture identifier exceeds varchar(" + maxLength + "): " + value.length());
    }

    private void insertBatch(String batchNo, String perspective) {
        // 本次修改：依据 V002/V003 写入真实批次，查询时再由生产实现按 batch_no 绑定批次。
        jdbc.update("INSERT INTO meta.analysis_batch(batch_no, period, perspective, status, created_by) "
                        + "VALUES (?, ?, ?, 'DRAFT', ?)",
                batchNo, YearMonth.now().toString(), perspective, "TRACE-JDBC-PG-SEC-001");
    }

    private void insertTask(String batchNo, String taskNo, String datasetCode) {
        // 本次修改：依据 V004 的月度导入任务约束写入 source_task_no；V005 配置任务保持独立不混入夹具。
        jdbc.update("INSERT INTO flow.import_task(task_no, batch_id, dataset_code, file_name, "
                        + "checksum, status, created_by) "
                        + "SELECT ?, id, ?, ?, ?, 'CREATED', ? "
                        + "FROM meta.analysis_batch WHERE batch_no = ?",
                taskNo, datasetCode + "_" + token, taskNo + ".xlsx", "checksum-" + taskNo,
                "TRACE-JDBC-PG-SEC-001", batchNo);
        fixtureTaskNos.add(taskNo);
    }

    private void insertTrace(String batchNo, String perspective, String taskNo,
                             String scopeCode, int rowNo, String fileName) {
        // 本次修改：依据 V017 写入可区分的来源行，验证 provider 的 task/scope/排序映射。
        jdbc.update("INSERT INTO raw.pnl_fact_trace(batch_id, perspective, source_task_no, "
                        + "source_file_name, source_checksum, source_sheet_name, source_row_no, "
                        + "scope_code, base_code, product_code, line_code) "
                        + "SELECT id, ?, ?, ?, ?, '实际明细', ?, ?, ?, ?, ? "
                        + "FROM meta.analysis_batch WHERE batch_no = ?",
                perspective, taskNo, fileName, "checksum-" + taskNo, rowNo, scopeCode,
                scopeCode + "-BASE", scopeCode + "-PRODUCT", scopeCode + "-LINE", batchNo);
    }

    private void grant(String userId, String perspective, String scopeCode,
                       boolean enabled, boolean canViewDetail) {
        grant(userId, perspective, scopeCode, enabled, canViewDetail,
                "TRACE_JDBC_PG_SEC_001_VIEWER");
    }

    private void grant(String userId, String perspective, String scopeCode,
                       boolean enabled, boolean canViewDetail, String roleCode) {
        // 本次修改：分别构造精确 scope、ALL、disabled 和无明细权限授权，直接覆盖生产 EXISTS 条件。
        jdbc.update("INSERT INTO iam.user_scope(user_id, role_code, perspective, scope_code, "
                        + "can_view_detail, can_export, can_edit_config, enabled) "
                        + "VALUES (?, ?, ?, ?, ?, FALSE, FALSE, ?)",
                userId, roleCode, perspective, scopeCode,
                canViewDetail, enabled);
        if (!fixtureUserIds.contains(userId)) {
            fixtureUserIds.add(userId);
        }
    }

    private List<String> keys(List<PnlFactSource> rows) {
        // 本次修改：用来源范围和 Excel 行号断言数据库 ORDER BY 及结果边界，不依赖自增 id 的具体值。
        List<String> result = new ArrayList<String>();
        for (PnlFactSource row : rows) {
            result.add(key(row.getScopeCode(), row.getRowNo()));
        }
        return result;
    }

    private String key(String scopeCode, int rowNo) {
        // 本次修改：集中生成可读的来源行断言键，覆盖 scope_code 与 source_row_no 映射。
        return scopeCode + "/" + rowNo;
    }

    private void assertSource(PnlFactSource source, String taskNo, String fileName,
                              String sheetName, int rowNo, String scopeCode) {
        // 本次修改：验证当前生产 RowMapper 的主要 provenance 字段没有被测试夹具或查询改写。
        assertEquals(taskNo, source.getTaskNo());
        assertEquals(fileName, source.getFileName());
        assertEquals("checksum-" + taskNo, source.getChecksum());
        assertEquals(sheetName, source.getSheetName());
        assertEquals(rowNo, source.getRowNo());
        assertEquals(scopeCode, source.getScopeCode());
        assertEquals(scopeCode + "-BASE", source.getBaseCode());
        assertEquals(scopeCode + "-PRODUCT", source.getProductCode());
        assertEquals(scopeCode + "-LINE", source.getLineCode());
    }

    private static String environment(String name) {
        // 本次修改：沿用仓库 PostgreSQL 契约测试约定，密码和用户名未设置时支持免密本地库。
        String value = System.getenv(name);
        return value == null ? "" : value;
    }
}
