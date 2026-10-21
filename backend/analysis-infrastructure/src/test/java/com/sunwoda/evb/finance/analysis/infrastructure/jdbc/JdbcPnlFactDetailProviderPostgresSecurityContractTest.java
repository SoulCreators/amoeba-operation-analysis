package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.PnlFact;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * FACT-DETAIL-JDBC-PG-SEC-001：验证 JdbcPnlFactDetailProvider 的真实 PostgreSQL 明细安全契约。
 *
 * <p>本测试只新增数据库契约夹具和断言，覆盖精确范围、ALL、disabled、无明细权限、
 * 批次/视角隔离以及当前 RowMapper 的维度、Wh 和材料损耗字段映射。</p>
 */
class JdbcPnlFactDetailProviderPostgresSecurityContractTest {
    private static final String BASE = AnalysisPerspective.BASE.getCode();
    private static final String PRODUCT_LINE = AnalysisPerspective.PRODUCT_LINE.getCode();
    private static final String FACT_INSERT_SQL = "INSERT INTO raw.pnl_fact("
            + "batch_id, perspective, source_task_no, period, scope_code, "
            + "base_code, product_code, line_code, "
            + "volume, revenue, sales_cost, idle_expense, base_expense, rd_expense, "
            + "asset_impairment, credit_impairment, other_income, budget_volume, budget_revenue, "
            + "budget_sales_cost, budget_idle_expense, budget_base_expense, budget_rd_expense, "
            + "budget_asset_impairment, budget_credit_impairment, budget_other_income, "
            + "material_cost, labor_cost, outsourcing_cost, variable_mfg_cost, fixed_mfg_cost, "
            + "budget_material_cost, budget_labor_cost, budget_outsourcing_cost, "
            + "budget_variable_mfg_cost, budget_fixed_mfg_cost, output_wh, budget_output_wh, "
            + "material_loss, budget_material_loss) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
            + "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;
    private static JdbcPnlFactDetailProvider provider;

    private String token;
    private String targetBatchNo;
    private String otherBatchNo;
    private String targetTaskNo;
    private String targetProductLineTaskNo;
    private String otherBatchTaskNo;
    private String scopeA;
    private String scopeB;
    private String scopeC;
    private String scopeD;
    private String exactScopeUser;
    private String allScopeUser;
    private String disabledUser;
    private String noDetailUser;
    private String exportOnlyUser;
    private String editOnlyUser;
    private String adminUser;
    private String adminNoDetailUser;
    private String disabledAdminUser;
    private String productLineUser;
    private final List<String> fixtureBatchNos = new ArrayList<String>();
    private final List<String> fixtureUserIds = new ArrayList<String>();

    @BeforeAll
    static void connectToConfiguredPostgresAndVerifySchema() {
        // 本次修改：数据库配置缺失时按既有约定 assume 跳过，明确表示真实契约未运行。
        String url = System.getenv("AMOEBA_DB_URL");
        assumeTrue(url != null && !url.trim().isEmpty(),
                "BLOCKED: AMOEBA_DB_URL is not set; FACT-DETAIL-JDBC-PG-SEC-001 was not run");

        dataSource = new DriverManagerDataSource(url,
                environment("AMOEBA_DB_USERNAME"), environment("AMOEBA_DB_PASSWORD"));
        jdbc = new JdbcTemplate(dataSource);
        try {
            // 本次修改：按 V002、V003、V008、V011、V013、V016、V023 的真实字段做数据库门禁。
            jdbc.execute("SELECT id, batch_no, period, perspective FROM meta.analysis_batch LIMIT 0");
            jdbc.execute("SELECT user_id, perspective, scope_code, can_view_detail, enabled "
                    + "FROM iam.user_scope LIMIT 0");
            jdbc.execute("SELECT id, batch_id, perspective, source_task_no, scope_code, "
                    + "base_code, product_code, line_code, output_wh, budget_output_wh, "
                    + "material_loss, budget_material_loss, material_cost, labor_cost, "
                    + "outsourcing_cost, variable_mfg_cost, fixed_mfg_cost, budget_material_cost, "
                    + "budget_labor_cost, budget_outsourcing_cost, budget_variable_mfg_cost, "
                    + "budget_fixed_mfg_cost FROM raw.pnl_fact LIMIT 0");
        } catch (RuntimeException failure) {
            assumeTrue(false, "BLOCKED: FACT-DETAIL-JDBC-PG-SEC-001 PostgreSQL schema is not available: "
                    + failure.getMessage());
        }

        // 本次修改：直接实例化当前生产适配器，确保断言来自真实 JdbcTemplate 查询和 RowMapper。
        provider = new JdbcPnlFactDetailProvider(dataSource);
    }

    @BeforeEach
    void seedRandomFixture() {
        // 本次修改：每个测试使用随机 token 生成批次、任务、范围和用户，避免共享库中的固定数据冲突。
        token = UUID.randomUUID().toString().replace("-", "");
        targetBatchNo = "FDPG-B-" + token;
        otherBatchNo = "FDPG-OB-" + token;
        targetTaskNo = "FDPG-T-" + token;
        targetProductLineTaskNo = "FDPG-PT-" + token;
        otherBatchTaskNo = "FDPG-OBT-" + token;
        scopeA = "FDPG-S-A-" + token;
        scopeB = "FDPG-S-B-" + token;
        scopeC = "FDPG-S-C-" + token;
        scopeD = "FDPG-S-D-" + token;
        exactScopeUser = "FDPG-U-EXACT-" + token;
        allScopeUser = "FDPG-U-ALL-" + token;
        disabledUser = "FDPG-U-DISABLED-" + token;
        noDetailUser = "FDPG-U-NODETAIL-" + token;
        exportOnlyUser = "FDPG-U-EXPORT-" + token;
        editOnlyUser = "FDPG-U-EDIT-" + token;
        adminUser = "FDPG-U-ADMIN-" + token;
        adminNoDetailUser = "FDPG-U-ADMIN-NODETAIL-" + token;
        disabledAdminUser = "FDPG-U-ADMIN-DISABLED-" + token;
        productLineUser = "FDPG-U-PL-" + token;

        // 本次修改：在写入前校验 V002/V003 中 varchar 上限，避免夹具自身掩盖权限契约结果。
        assertTrue(targetBatchNo.length() <= 64);
        assertTrue(otherBatchNo.length() <= 64);
        assertTrue(targetTaskNo.length() <= 64);
        assertTrue(targetProductLineTaskNo.length() <= 64);
        assertTrue(otherBatchTaskNo.length() <= 64);
        assertTrue(scopeA.length() <= 128);
        assertTrue(scopeB.length() <= 128);
        assertTrue(scopeC.length() <= 128);
        assertTrue(scopeD.length() <= 128);
        assertTrue(exactScopeUser.length() <= 64);
        assertTrue(allScopeUser.length() <= 64);
        assertTrue(disabledUser.length() <= 64);
        assertTrue(noDetailUser.length() <= 64);
        assertTrue(exportOnlyUser.length() <= 64);
        assertTrue(editOnlyUser.length() <= 64);
        assertTrue(adminUser.length() <= 64);
        assertTrue(adminNoDetailUser.length() <= 64);
        assertTrue(disabledAdminUser.length() <= 64);
        assertTrue(productLineUser.length() <= 64);

        fixtureBatchNos.add(targetBatchNo);
        fixtureBatchNos.add(otherBatchNo);
        fixtureUserIds.add(exactScopeUser);
        fixtureUserIds.add(allScopeUser);
        fixtureUserIds.add(disabledUser);
        fixtureUserIds.add(noDetailUser);
        fixtureUserIds.add(exportOnlyUser);
        fixtureUserIds.add(editOnlyUser);
        fixtureUserIds.add(adminUser);
        fixtureUserIds.add(adminNoDetailUser);
        fixtureUserIds.add(disabledAdminUser);
        fixtureUserIds.add(productLineUser);

        insertBatch(targetBatchNo, BASE);
        insertBatch(otherBatchNo, BASE);

        // 本次修改：目标批次 BASE 写入两个授权范围，数值和维度均可区分以验证 RowMapper。
        insertFact(targetBatchNo, BASE, targetTaskNo, scopeA, "BASE-A", "PRODUCT-A", "LINE-A", values(100));
        insertFact(targetBatchNo, BASE, targetTaskNo, scopeB, "BASE-B", "PRODUCT-B", "LINE-B", values(200));
        // 本次修改：增加 Wh 和材料损耗全为 NULL 的事实行，验证读取不把缺失值转换为零。
        BigDecimal[] nullWhAndLoss = values(500);
        nullWhAndLoss[28] = null;
        nullWhAndLoss[29] = null;
        nullWhAndLoss[30] = null;
        nullWhAndLoss[31] = null;
        insertFact(targetBatchNo, BASE, targetTaskNo, scopeC,
                "BASE-C", "PRODUCT-C", "LINE-C", nullWhAndLoss);
        // 本次修改：增加 Wh 和材料损耗明确为零的事实行，验证显式零值保持为零。
        BigDecimal[] zeroWhAndLoss = values(600);
        zeroWhAndLoss[28] = BigDecimal.ZERO;
        zeroWhAndLoss[29] = BigDecimal.ZERO;
        zeroWhAndLoss[30] = BigDecimal.ZERO;
        zeroWhAndLoss[31] = BigDecimal.ZERO;
        insertFact(targetBatchNo, BASE, targetTaskNo, scopeD,
                "BASE-D", "PRODUCT-D", "LINE-D", zeroWhAndLoss);
        // 本次修改：同一批次写入 PRODUCT_LINE 事实，验证 BASE 请求不会越过视角过滤。
        insertFact(targetBatchNo, PRODUCT_LINE, targetProductLineTaskNo, scopeA,
                "PL-BASE-A", "PL-PRODUCT-A", "PL-LINE-A", values(300));
        // 本次修改：其他批次写入相同范围，验证 batch_no 过滤不会跨批次串读。
        insertFact(otherBatchNo, BASE, otherBatchTaskNo, scopeA,
                "OTHER-BASE-A", "OTHER-PRODUCT-A", "OTHER-LINE-A", values(400));

        // 本次修改：分别构造精确 scope、ALL、disabled、无明细权限及其他视角授权。
        grant(exactScopeUser, BASE, scopeA, true, true, false, false);
        grant(allScopeUser, BASE, "ALL", true, true, false, false);
        grant(disabledUser, BASE, scopeA, false, true, false, false);
        grant(noDetailUser, BASE, scopeA, true, false, false, false);
        // 本次修改：仅有导出权限但没有明细权限的用户必须不能读取事实明细。
        grant(exportOnlyUser, BASE, scopeA, true, false, true, false);
        // 本次修改：仅有配置编辑权限但没有明细权限的用户必须不能读取事实明细。
        grant(editOnlyUser, BASE, scopeA, true, false, false, true);
        grant(adminUser, BASE, scopeA, true, true, false, false, "ADMIN");
        grant(adminNoDetailUser, BASE, scopeA, true, false, false, false, "ADMIN");
        grant(disabledAdminUser, BASE, scopeA, false, true, false, false, "ADMIN");
        grant(productLineUser, PRODUCT_LINE, "ALL", true, true, false, false);
    }

    @AfterEach
    void cleanupFixtureRowsInReverseOrderAndVerifyNoResidue() {
        // 本次修改：先删事实子表，再删独立权限，最后逆序删批次，遵守外键依赖并支持失败后清理。
        if (jdbc == null || token == null) return;
        for (int index = fixtureBatchNos.size() - 1; index >= 0; index--) {
            String batchNo = fixtureBatchNos.get(index);
            jdbc.update("DELETE FROM raw.pnl_fact WHERE batch_id IN "
                    + "(SELECT id FROM meta.analysis_batch WHERE batch_no = ?)", batchNo);
        }
        for (int index = fixtureUserIds.size() - 1; index >= 0; index--) {
            jdbc.update("DELETE FROM iam.user_scope WHERE user_id = ?", fixtureUserIds.get(index));
        }
        for (int index = fixtureBatchNos.size() - 1; index >= 0; index--) {
            jdbc.update("DELETE FROM meta.analysis_batch WHERE batch_no = ?", fixtureBatchNos.get(index));
        }

        // 本次修改：按随机 token 反查事实、权限和批次，确认清理没有残留而非仅依赖删除行数。
        String tokenLike = "%" + token;
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM raw.pnl_fact WHERE source_task_no LIKE ?", Integer.class, tokenLike));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM iam.user_scope WHERE user_id LIKE ?", Integer.class, tokenLike));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM meta.analysis_batch WHERE batch_no LIKE ?", Integer.class, tokenLike));
        fixtureBatchNos.clear();
        fixtureUserIds.clear();
        token = null;
    }

    @Test
    void filtersDetailRowsByExactAllDisabledDetailPermissionBatchAndPerspective() {
        // 本次修改：用真实生产查询覆盖精确范围、ALL、disabled、无明细权限和跨批次/视角隔离。
        List<PnlFact> exactRows = provider.findForUser(targetBatchNo, AnalysisPerspective.BASE, exactScopeUser);
        assertEquals(Arrays.asList(scopeA), scopes(exactRows));
        assertFact(exactRows.get(0), scopeA, "BASE-A", "PRODUCT-A", "LINE-A", values(100));

        List<PnlFact> allRows = provider.findForUser(targetBatchNo, AnalysisPerspective.BASE, allScopeUser);
        assertEquals(Arrays.asList(scopeA, scopeB, scopeC, scopeD), scopes(allRows));
        assertTrue(allRows.stream().noneMatch(row -> "OTHER-BASE-A".equals(row.getBaseCode())),
                "ALL scope must not expose a different batch");
        assertTrue(allRows.stream().noneMatch(row -> "PL-BASE-A".equals(row.getBaseCode())),
                "ALL scope must not expose a different perspective");
        // 本次修改：验证 RowMapper 保留 Wh/材料损耗的 NULL 与明确 0 语义，而不是统一归零。
        BigDecimal[] expectedNullWhAndLoss = values(500);
        expectedNullWhAndLoss[28] = null;
        expectedNullWhAndLoss[29] = null;
        expectedNullWhAndLoss[30] = null;
        expectedNullWhAndLoss[31] = null;
        assertFact(factForScope(allRows, scopeC), scopeC,
                "BASE-C", "PRODUCT-C", "LINE-C", expectedNullWhAndLoss);
        BigDecimal[] expectedZeroWhAndLoss = values(600);
        expectedZeroWhAndLoss[28] = BigDecimal.ZERO;
        expectedZeroWhAndLoss[29] = BigDecimal.ZERO;
        expectedZeroWhAndLoss[30] = BigDecimal.ZERO;
        expectedZeroWhAndLoss[31] = BigDecimal.ZERO;
        assertFact(factForScope(allRows, scopeD), scopeD,
                "BASE-D", "PRODUCT-D", "LINE-D", expectedZeroWhAndLoss);

        // 本次修改：enabled=false 与 can_view_detail=false 均必须阻断全部明细，不因 scope 匹配而放行。
        assertTrue(provider.findForUser(targetBatchNo, AnalysisPerspective.BASE, disabledUser).isEmpty(),
                "disabled scope grant must not expose detail rows");
        assertTrue(provider.findForUser(targetBatchNo, AnalysisPerspective.BASE, noDetailUser).isEmpty(),
                "can_view_detail=false grant must not expose detail rows");
        assertTrue(provider.findForUser(targetBatchNo, AnalysisPerspective.BASE, exportOnlyUser).isEmpty(),
                "can_export=true must not substitute for can_view_detail=true");
        assertTrue(provider.findForUser(targetBatchNo, AnalysisPerspective.BASE, editOnlyUser).isEmpty(),
                "can_edit_config=true must not substitute for can_view_detail=true");

        // 本次修改：验证启用 ADMIN 可跨越具体 scope 读取同一 perspective 的全部事实明细。
        List<PnlFact> adminRows = provider.findForUser(
                targetBatchNo, AnalysisPerspective.BASE, adminUser);
        assertEquals(Arrays.asList(scopeA, scopeB, scopeC, scopeD), scopes(adminRows));
        // 本次修改：Owner 已确认 ADMIN 可查看全部信息，不能因普通明细布尔值而丢失事实明细读取。
        assertEquals(Arrays.asList(scopeA, scopeB, scopeC, scopeD),
                scopes(provider.findForUser(targetBatchNo, AnalysisPerspective.BASE,
                        adminNoDetailUser)));
        assertTrue(provider.findForUser(targetBatchNo, AnalysisPerspective.BASE,
                disabledAdminUser).isEmpty(),
                "disabled ADMIN grant must not expose detail rows");

        // 本次修改：BASE 用户查询 PRODUCT_LINE 时不得复用 BASE 授权，独立 PRODUCT_LINE 授权才可读取该视角。
        assertTrue(provider.findForUser(targetBatchNo, AnalysisPerspective.PRODUCT_LINE, allScopeUser).isEmpty(),
                "a BASE grant must not expose PRODUCT_LINE detail rows");
        List<PnlFact> productLineRows = provider.findForUser(
                targetBatchNo, AnalysisPerspective.PRODUCT_LINE, productLineUser);
        assertEquals(Arrays.asList(scopeA), scopes(productLineRows));
        assertFact(productLineRows.get(0), scopeA, "PL-BASE-A", "PL-PRODUCT-A", "PL-LINE-A", values(300));
    }

    private void insertBatch(String batchNo, String perspective) {
        // 本次修改：依据真实批次迁移写入 DRAFT 批次，保留 provider 的 batch_no 连接条件。
        jdbc.update("INSERT INTO meta.analysis_batch(batch_no, period, perspective, status, created_by) "
                        + "VALUES (?, ?, ?, 'DRAFT', ?)",
                batchNo, YearMonth.now().toString(), perspective, "FACT-DETAIL-JDBC-PG-SEC-001");
    }

    private void insertFact(String batchNo, String perspective, String taskNo, String scopeCode,
                            String baseCode, String productCode, String lineCode, BigDecimal[] values) {
        // 本次修改：写入包含维度、Wh、材料损耗在内的 32 个数值字段，直接覆盖 RowMapper 全部映射。
        if (values.length != 32) {
            throw new IllegalArgumentException("事实夹具字段数量必须为 32: " + values.length);
        }
        Long batchId = jdbc.queryForObject("SELECT id FROM meta.analysis_batch WHERE batch_no = ?",
                Long.class, batchNo);
        Object[] arguments = new Object[40];
        arguments[0] = batchId;
        arguments[1] = perspective;
        arguments[2] = taskNo;
        arguments[3] = YearMonth.now().toString();
        arguments[4] = scopeCode;
        arguments[5] = baseCode;
        arguments[6] = productCode;
        arguments[7] = lineCode;
        System.arraycopy(values, 0, arguments, 8, values.length);
        jdbc.update(FACT_INSERT_SQL, arguments);
    }

    private void grant(String userId, String perspective, String scopeCode,
                       boolean enabled, boolean canViewDetail,
                       boolean canExport, boolean canEditConfig) {
        grant(userId, perspective, scopeCode, enabled, canViewDetail,
                canExport, canEditConfig, "FACT_DETAIL_JDBC_PG_SEC_001_VIEWER");
    }

    private void grant(String userId, String perspective, String scopeCode,
                       boolean enabled, boolean canViewDetail,
                       boolean canExport, boolean canEditConfig, String roleCode) {
        // 本次修改：直接写入 iam.user_scope 的四类权限布尔值，确保负例来自数据库 EXISTS 条件。
        jdbc.update("INSERT INTO iam.user_scope(user_id, role_code, perspective, scope_code, "
                        + "can_view_detail, can_export, can_edit_config, enabled) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                userId, roleCode, perspective, scopeCode,
                canViewDetail, canExport, canEditConfig, enabled);
    }

    private List<String> scopes(List<PnlFact> facts) {
        // 本次修改：提取返回范围以锁定 SQL ORDER BY 和权限边界，不依赖数据库自增 id。
        List<String> result = new ArrayList<String>();
        for (PnlFact fact : facts) result.add(fact.getScopeCode());
        return result;
    }

    private PnlFact factForScope(List<PnlFact> facts, String scopeCode) {
        // 本次修改：按随机 scope 定位 NULL/0 夹具行，避免断言依赖数据库自增 id。
        for (PnlFact fact : facts) {
            if (scopeCode.equals(fact.getScopeCode())) return fact;
        }
        throw new AssertionError("未找到 scope_code: " + scopeCode);
    }

    private void assertFact(PnlFact fact, String scopeCode, String baseCode,
                            String productCode, String lineCode, BigDecimal[] expected) {
        // 本次修改：同时验证维度字段和全部数值映射，特别覆盖 Wh 与材料损耗字段。
        assertEquals(scopeCode, fact.getScopeCode());
        assertEquals(baseCode, fact.getBaseCode());
        assertEquals(productCode, fact.getProductCode());
        assertEquals(lineCode, fact.getLineCode());
        assertDecimal(expected[0], fact.getVolume());
        assertDecimal(expected[1], fact.getRevenue());
        assertDecimal(expected[2], fact.getSalesCost());
        assertDecimal(expected[3], fact.getIdleExpense());
        assertDecimal(expected[4], fact.getBaseExpense());
        assertDecimal(expected[5], fact.getRdExpense());
        assertDecimal(expected[6], fact.getAssetImpairment());
        assertDecimal(expected[7], fact.getCreditImpairment());
        assertDecimal(expected[8], fact.getOtherIncome());
        assertDecimal(expected[9], fact.getBudgetVolume());
        assertDecimal(expected[10], fact.getBudgetRevenue());
        assertDecimal(expected[11], fact.getBudgetSalesCost());
        assertDecimal(expected[12], fact.getBudgetIdleExpense());
        assertDecimal(expected[13], fact.getBudgetBaseExpense());
        assertDecimal(expected[14], fact.getBudgetRdExpense());
        assertDecimal(expected[15], fact.getBudgetAssetImpairment());
        assertDecimal(expected[16], fact.getBudgetCreditImpairment());
        assertDecimal(expected[17], fact.getBudgetOtherIncome());
        assertDecimal(expected[18], fact.getMaterialCost());
        assertDecimal(expected[19], fact.getLaborCost());
        assertDecimal(expected[20], fact.getOutsourcingCost());
        assertDecimal(expected[21], fact.getVariableManufacturingCost());
        assertDecimal(expected[22], fact.getFixedManufacturingCost());
        assertDecimal(expected[23], fact.getBudgetMaterialCost());
        assertDecimal(expected[24], fact.getBudgetLaborCost());
        assertDecimal(expected[25], fact.getBudgetOutsourcingCost());
        assertDecimal(expected[26], fact.getBudgetVariableManufacturingCost());
        assertDecimal(expected[27], fact.getBudgetFixedManufacturingCost());
        // 本次修改：明确锁定 V016 的实际/预算 Wh 字段 RowMapper 映射。
        assertNullableDecimal(expected[28], fact.getOutputWh());
        assertNullableDecimal(expected[29], fact.getBudgetOutputWh());
        // 本次修改：明确锁定 V023 的实际/预算材料损耗字段 RowMapper 映射。
        assertNullableDecimal(expected[30], fact.getMaterialLoss());
        assertNullableDecimal(expected[31], fact.getBudgetMaterialLoss());
    }

    private void assertDecimal(BigDecimal expected, BigDecimal actual) {
        // 本次修改：使用数值比较忽略 PostgreSQL NUMERIC 返回值的 scale 差异。
        assertEquals(0, actual.compareTo(expected));
    }

    private void assertNullableDecimal(BigDecimal expected, BigDecimal actual) {
        // 本次修改：NULL 夹具必须保持 NULL，明确零夹具则继续使用数值比较验证为零。
        if (expected == null) {
            assertNull(actual);
        } else {
            assertDecimal(expected, actual);
        }
    }

    private BigDecimal[] values(int start) {
        // 本次修改：生成彼此可区分的随机夹具行数值，避免字段错位映射仍然通过。
        BigDecimal[] values = new BigDecimal[32];
        for (int index = 0; index < values.length; index++) {
            values[index] = BigDecimal.valueOf(start + index);
        }
        return values;
    }

    private static String environment(String name) {
        // 本次修改：密码沿用项目既有约定，未设置时传入空字符串以支持免密 PostgreSQL。
        String value = System.getenv(name);
        return value == null ? "" : value;
    }
}
