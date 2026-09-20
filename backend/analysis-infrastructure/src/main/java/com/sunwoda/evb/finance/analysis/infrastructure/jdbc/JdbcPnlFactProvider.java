package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.application.port.PnlFactProvider;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.PnlFact;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.List;

/** 从标准事实表按批次和视角汇总，生产db Profile下替换Excel事实适配器。 */
@Repository
@Primary
@Profile("db")
public class JdbcPnlFactProvider implements PnlFactProvider {
    private final JdbcTemplate jdbc;

    public JdbcPnlFactProvider(DataSource dataSource) { this.jdbc = new JdbcTemplate(dataSource); }

    @Override
    public List<PnlFact> load(AnalysisBatch batch) {
        String sql = "SELECT scope_code, SUM(volume) volume, SUM(revenue) revenue, SUM(sales_cost) sales_cost, "
                + "SUM(idle_expense) idle_expense, SUM(base_expense) base_expense, SUM(rd_expense) rd_expense, "
                + "SUM(asset_impairment) asset_impairment, SUM(credit_impairment) credit_impairment, SUM(other_income) other_income, "
                + "SUM(budget_volume) budget_volume, SUM(budget_revenue) budget_revenue, SUM(budget_sales_cost) budget_sales_cost, "
                + "SUM(budget_idle_expense) budget_idle_expense, SUM(budget_base_expense) budget_base_expense, "
                + "SUM(budget_rd_expense) budget_rd_expense, SUM(budget_asset_impairment) budget_asset_impairment, "
                + "SUM(budget_credit_impairment) budget_credit_impairment, SUM(budget_other_income) budget_other_income "
                + "FROM raw.pnl_fact WHERE batch_id = (SELECT id FROM meta.analysis_batch WHERE batch_no = ?) "
                + "AND perspective = ? GROUP BY scope_code ORDER BY scope_code";
        List<PnlFact> facts = jdbc.query(sql, (rs, row) -> new PnlFact(rs.getString("scope_code"),
                rs.getBigDecimal("volume"), rs.getBigDecimal("revenue"), rs.getBigDecimal("sales_cost"),
                rs.getBigDecimal("idle_expense"), rs.getBigDecimal("base_expense"), rs.getBigDecimal("rd_expense"),
                rs.getBigDecimal("asset_impairment"), rs.getBigDecimal("credit_impairment"), rs.getBigDecimal("other_income"),
                rs.getBigDecimal("budget_volume"), rs.getBigDecimal("budget_revenue"), rs.getBigDecimal("budget_sales_cost"),
                rs.getBigDecimal("budget_idle_expense"), rs.getBigDecimal("budget_base_expense"), rs.getBigDecimal("budget_rd_expense"),
                rs.getBigDecimal("budget_asset_impairment"), rs.getBigDecimal("budget_credit_impairment"),
                rs.getBigDecimal("budget_other_income")), batch.getBatchNo(), batch.getPerspective().getCode());
        if (facts.isEmpty()) throw new IllegalStateException("标准事实表没有可计算数据: " + batch.getBatchNo());
        return facts;
    }
}
