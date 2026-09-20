package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.ImportTask;
import com.sunwoda.evb.finance.analysis.domain.model.PnlFact;
import com.sunwoda.evb.finance.analysis.domain.repository.PnlFactWriter;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.List;

/** 将校验通过的任务事实按任务替换写入 raw.pnl_fact，避免重复导入造成重复金额。 */
@Repository
@Profile("db")
public class JdbcPnlFactWriter implements PnlFactWriter {
    private final JdbcTemplate jdbc;

    public JdbcPnlFactWriter(DataSource dataSource) { this.jdbc = new JdbcTemplate(dataSource); }

    @Override
    public void replace(AnalysisBatch batch, ImportTask task, List<PnlFact> facts) {
        Long batchId = jdbc.queryForObject("SELECT id FROM meta.analysis_batch WHERE batch_no = ?",
                Long.class, batch.getBatchNo());
        if (batchId == null) throw new IllegalStateException("批次不存在: " + batch.getBatchNo());
        jdbc.update("DELETE FROM raw.pnl_fact WHERE batch_id = ? AND perspective = ? AND source_task_no = ?",
                batchId, batch.getPerspective().getCode(), task.getTaskNo());
        String sql = "INSERT INTO raw.pnl_fact(batch_id, perspective, source_task_no, period, scope_code, "
                + "volume, revenue, sales_cost, idle_expense, base_expense, rd_expense, asset_impairment, "
                + "credit_impairment, other_income, budget_volume, budget_revenue, budget_sales_cost, "
                + "budget_idle_expense, budget_base_expense, budget_rd_expense, budget_asset_impairment, "
                + "budget_credit_impairment, budget_other_income, material_cost, labor_cost, outsourcing_cost, "
                + "variable_mfg_cost, fixed_mfg_cost, budget_material_cost, budget_labor_cost, "
                + "budget_outsourcing_cost, budget_variable_mfg_cost, budget_fixed_mfg_cost) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ? )";
        for (PnlFact fact : facts) {
            jdbc.update(sql, batchId, batch.getPerspective().getCode(), task.getTaskNo(),
                    batch.getPeriod(), fact.getScopeCode(), fact.getVolume(), fact.getRevenue(),
                    fact.getSalesCost(), fact.getIdleExpense(), fact.getBaseExpense(), fact.getRdExpense(),
                    fact.getAssetImpairment(), fact.getCreditImpairment(), fact.getOtherIncome(),
                    fact.getBudgetVolume(), fact.getBudgetRevenue(), fact.getBudgetSalesCost(),
                    fact.getBudgetIdleExpense(), fact.getBudgetBaseExpense(), fact.getBudgetRdExpense(),
                    fact.getBudgetAssetImpairment(), fact.getBudgetCreditImpairment(), fact.getBudgetOtherIncome(),
                    fact.getMaterialCost(), fact.getLaborCost(), fact.getOutsourcingCost(),
                    fact.getVariableManufacturingCost(), fact.getFixedManufacturingCost(),
                    fact.getBudgetMaterialCost(), fact.getBudgetLaborCost(), fact.getBudgetOutsourcingCost(),
                    fact.getBudgetVariableManufacturingCost(), fact.getBudgetFixedManufacturingCost());
        }
    }
}
