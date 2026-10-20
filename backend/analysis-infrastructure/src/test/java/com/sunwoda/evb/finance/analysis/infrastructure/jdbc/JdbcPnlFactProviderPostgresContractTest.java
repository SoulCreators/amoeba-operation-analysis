package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.BatchStatus;
import com.sunwoda.evb.finance.analysis.domain.model.PnlFact;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * CALC-JDBC-FACT-001：基于真实 PostgreSQL 锁定 JdbcPnlFactProvider 当前查询与映射契约。
 *
 * <p>本契约只验证既有 raw.pnl_fact SQL/字段映射，不新增公式、权限、版本或治理口径；
 * PostgreSQL 连接缺失时按项目真实契约测试的环境变量门禁直接失败。</p>
 */
class JdbcPnlFactProviderPostgresContractTest {
    private static final String FACT_INSERT_SQL = "INSERT INTO raw.pnl_fact("
            + "batch_id, perspective, source_task_no, period, scope_code, "
            + "volume, revenue, sales_cost, idle_expense, base_expense, rd_expense, "
            + "asset_impairment, credit_impairment, other_income, budget_volume, budget_revenue, "
            + "budget_sales_cost, budget_idle_expense, budget_base_expense, budget_rd_expense, "
            + "budget_asset_impairment, budget_credit_impairment, budget_other_income, "
            + "material_cost, labor_cost, outsourcing_cost, variable_mfg_cost, fixed_mfg_cost, "
            + "budget_material_cost, budget_labor_cost, budget_outsourcing_cost, "
            + "budget_variable_mfg_cost, budget_fixed_mfg_cost, output_wh, budget_output_wh, "
            + "material_loss, budget_material_loss) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
            + "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
            + "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
            + "?, ?, ?, ?, ?, ?, ? )";

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;
    private static JdbcPnlFactProvider provider;

    private final List<String> fixtureBatchNos = new ArrayList<>();

    @BeforeAll
    static void connectToConfiguredPostgresAndVerifySchema() {
        // 本次修改：使用项目真实 PostgreSQL 环境变量门禁，避免未执行真实数据库契约却误报通过。
        String url = requiredEnvironment("AMOEBA_DB_URL");
        dataSource = new DriverManagerDataSource(
                url, requiredEnvironment("AMOEBA_DB_USERNAME"), environment("AMOEBA_DB_PASSWORD"));
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("SELECT id, batch_no, perspective FROM meta.analysis_batch LIMIT 0");
        jdbc.execute("SELECT id, batch_id, perspective, source_task_no, period, scope_code, "
                + "material_loss, budget_material_loss FROM raw.pnl_fact LIMIT 0");
        provider = new JdbcPnlFactProvider(dataSource);
    }

    @AfterEach
    void cleanupFixtureRows() {
        // 本次修改：按本次随机 batchNo 清理事实和批次，避免测试依赖或污染固定数据。
        if (jdbc == null) return;
        for (String batchNo : fixtureBatchNos) {
            jdbc.update("DELETE FROM raw.pnl_fact WHERE batch_id IN "
                    + "(SELECT id FROM meta.analysis_batch WHERE batch_no = ?)", batchNo);
            jdbc.update("DELETE FROM meta.analysis_batch WHERE batch_no = ?", batchNo);
            assertEquals(0, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM meta.analysis_batch WHERE batch_no = ?", Integer.class, batchNo));
        }
        fixtureBatchNos.clear();
    }

    @Test
    void aggregatesSameBatchPerspectiveByScopeWithoutMixingOtherBatchOrPerspective() {
        // 本次修改：同步验证包含 output_wh/budget_output_wh 在内的 32 个字段聚合，并隔离其他 batch 与 perspective。
        AnalysisBatch targetBatch = createBatch(AnalysisPerspective.BASE);
        AnalysisBatch otherBatch = createBatch(AnalysisPerspective.BASE);
        AnalysisBatch targetProductPerspective = withPerspective(targetBatch, AnalysisPerspective.PRODUCT_LINE);
        String sharedScope = scopeCode("SHARED");
        String secondScope = scopeCode("SECOND");

        insertFact(targetBatch, AnalysisPerspective.BASE, taskNo(), sharedScope, values(1));
        insertFact(targetBatch, AnalysisPerspective.BASE, taskNo(), sharedScope, values(2));
        insertFact(targetBatch, AnalysisPerspective.BASE, taskNo(), secondScope, values(3));
        insertFact(targetBatch, AnalysisPerspective.PRODUCT_LINE, taskNo(), sharedScope, values(100));
        insertFact(otherBatch, AnalysisPerspective.BASE, taskNo(), sharedScope, values(200));

        List<PnlFact> baseFacts = provider.load(targetBatch);
        assertEquals(2, baseFacts.size());
        assertMappedValues(factForScope(baseFacts, sharedScope), sum(values(1), values(2)));
        assertMappedValues(factForScope(baseFacts, secondScope), values(3));

        List<PnlFact> productFacts = provider.load(targetProductPerspective);
        assertEquals(1, productFacts.size());
        assertMappedValues(factForScope(productFacts, sharedScope), values(100));

        List<PnlFact> otherBatchFacts = provider.load(otherBatch);
        assertEquals(1, otherBatchFacts.size());
        assertMappedValues(factForScope(otherBatchFacts, sharedScope), values(200));
    }

    @Test
    void preservesNullAndExplicitZeroMaterialLossValues() {
        // 本次修改：在 32 字段夹具中验证材料损耗字段的 SUM 结果保持全 NULL 为 null、明确零为 0。
        AnalysisBatch batch = createBatch(AnalysisPerspective.BASE);
        String nullScope = scopeCode("NULL-LOSS");
        String zeroScope = scopeCode("ZERO-LOSS");
        BigDecimal[] allNullLoss = values(10);
        allNullLoss[30] = null;
        allNullLoss[31] = null;
        BigDecimal[] explicitZeroLoss = values(20);
        explicitZeroLoss[30] = BigDecimal.ZERO;
        explicitZeroLoss[31] = BigDecimal.ZERO;

        insertFact(batch, AnalysisPerspective.BASE, taskNo(), nullScope, allNullLoss);
        insertFact(batch, AnalysisPerspective.BASE, taskNo(), zeroScope, explicitZeroLoss);

        List<PnlFact> facts = provider.load(batch);
        PnlFact nullLossFact = factForScope(facts, nullScope);
        PnlFact zeroLossFact = factForScope(facts, zeroScope);
        assertNull(nullLossFact.getMaterialLoss());
        assertNull(nullLossFact.getBudgetMaterialLoss());
        assertEquals(0, zeroLossFact.getMaterialLoss().compareTo(BigDecimal.ZERO));
        assertEquals(0, zeroLossFact.getBudgetMaterialLoss().compareTo(BigDecimal.ZERO));
    }

    @Test
    void throwsCurrentIllegalStateExceptionWhenNoFactDataMatchesBatchAndPerspective() {
        // 本次修改：验证当前无匹配事实数据时仍抛出既有 IllegalStateException 及消息。
        AnalysisBatch emptyBatch = createBatch(AnalysisPerspective.BASE);
        AnalysisBatch unrelatedBatch = createBatch(AnalysisPerspective.BASE);
        insertFact(unrelatedBatch, AnalysisPerspective.BASE, taskNo(), scopeCode("UNRELATED"), values(1));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> provider.load(emptyBatch));

        assertEquals("标准事实表没有可计算数据: " + emptyBatch.getBatchNo(), failure.getMessage());
    }

    private AnalysisBatch createBatch(AnalysisPerspective perspective) {
        // 本次修改：使用随机 UUID 生成批次夹具，返回真实数据库生成的 batch id 供事实外键使用。
        String batchNo = "CALC-JDBC-FACT-001-B-" + UUID.randomUUID().toString().replace("-", "");
        fixtureBatchNos.add(batchNo);
        String period = YearMonth.now().toString();
        Long id = jdbc.queryForObject("INSERT INTO meta.analysis_batch "
                        + "(batch_no, period, perspective, status, created_by) VALUES (?, ?, ?, ?, ?) RETURNING id",
                Long.class, batchNo, period, perspective.getCode(), BatchStatus.DRAFT.name(),
                "CALC-JDBC-FACT-001");
        return new AnalysisBatch(id, batchNo, period, perspective, BatchStatus.DRAFT,
                "CALC-JDBC-FACT-001", LocalDateTime.now());
    }

    private AnalysisBatch withPerspective(AnalysisBatch batch, AnalysisPerspective perspective) {
        // 本次修改：复用同一真实 batch id，仅切换查询入参 perspective，以直接验证现有 SQL 的视角过滤。
        return new AnalysisBatch(batch.getId(), batch.getBatchNo(), batch.getPeriod(), perspective,
                batch.getStatus(), batch.getCreatedBy(), batch.getCreatedAt());
    }

    private void insertFact(AnalysisBatch batch, AnalysisPerspective perspective, String taskNo,
                            String scopeCode, BigDecimal[] values) {
        // 本次修改：用随机 taskNo 写入包含两个 Wh 字段的 32 个数值字段，允许同 scope 多行并验证现有聚合。
        if (values.length != 32) {
            throw new IllegalArgumentException("事实夹具字段数量必须为 32: " + values.length);
        }
        Object[] arguments = new Object[37];
        arguments[0] = batch.getId();
        arguments[1] = perspective.getCode();
        arguments[2] = taskNo;
        arguments[3] = batch.getPeriod();
        arguments[4] = scopeCode;
        System.arraycopy(values, 0, arguments, 5, values.length);
        jdbc.update(FACT_INSERT_SQL, arguments);
    }

    private PnlFact factForScope(List<PnlFact> facts, String scopeCode) {
        // 本次修改：按 scope_code 读取 provider 返回的聚合行，避免测试依赖数据库返回顺序。
        for (PnlFact fact : facts) {
            if (scopeCode.equals(fact.getScopeCode())) return fact;
        }
        throw new AssertionError("未找到 scope_code: " + scopeCode);
    }

    private void assertMappedValues(PnlFact fact, BigDecimal[] expected) {
        // 本次修改：逐字段锁定当前实际、预算、成本、Wh 产出及材料损耗映射，不引入新的业务计算口径。
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
        // 本次修改：补强 V016 output_wh/budget_output_wh 的 JDBC 映射契约。
        assertDecimal(expected[28], fact.getOutputWh());
        assertDecimal(expected[29], fact.getBudgetOutputWh());
        assertDecimal(expected[30], fact.getMaterialLoss());
        assertDecimal(expected[31], fact.getBudgetMaterialLoss());
    }

    private void assertDecimal(BigDecimal expected, BigDecimal actual) {
        // 本次修改：使用数值比较忽略 PostgreSQL NUMERIC 返回的小数位 scale 差异。
        assertEquals(0, actual.compareTo(expected));
    }

    private BigDecimal[] values(int start) {
        // 本次修改：生成彼此可区分的事实数值，覆盖当前 provider 映射的全部 32 个数值字段。
        BigDecimal[] values = new BigDecimal[32];
        for (int index = 0; index < values.length; index++) {
            values[index] = BigDecimal.valueOf(start + index);
        }
        return values;
    }

    private BigDecimal[] sum(BigDecimal[] left, BigDecimal[] right) {
        // 本次修改：只为断言两条原始事实的 SQL SUM 结果生成期望值，不定义新的生产公式。
        BigDecimal[] result = new BigDecimal[left.length];
        for (int index = 0; index < result.length; index++) {
            result[index] = left[index].add(right[index]);
        }
        return result;
    }

    private String taskNo() {
        // 本次修改：使用随机 UUID 生成任务夹具，确保同 scope 多行不依赖固定 source_task_no。
        return "CALC-JDBC-FACT-001-T-" + UUID.randomUUID().toString().replace("-", "");
    }

    private String scopeCode(String label) {
        // 本次修改：使用随机 scope_code 隔离并发测试数据，同时保持断言可读。
        return "CALC-JDBC-FACT-001-S-" + label + "-"
                + UUID.randomUUID().toString().replace("-", "");
    }

    private static String requiredEnvironment(String name) {
        // 本次修改：统一采用当前真实 PostgreSQL 契约测试的必需环境变量校验。
        String value = System.getenv(name);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException(name
                    + " must be set for the real PostgreSQL PnlFactProvider contract test");
        }
        return value;
    }

    private static String environment(String name) {
        // 本次修改：密码沿用项目现有风格，未设置时传入空字符串以支持免密测试库。
        String value = System.getenv(name);
        return value == null ? "" : value;
    }
}
