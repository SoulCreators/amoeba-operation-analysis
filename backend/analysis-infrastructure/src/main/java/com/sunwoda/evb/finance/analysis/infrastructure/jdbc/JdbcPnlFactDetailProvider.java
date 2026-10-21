package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.application.port.PnlFactDetailProvider;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.PnlFact;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.List;

/** 从 raw.pnl_fact 回读带维度的事实明细，并在 SQL 层应用用户数据范围。 */
@Repository
@Profile("db")
public class JdbcPnlFactDetailProvider implements PnlFactDetailProvider {
    private final JdbcTemplate jdbc;

    public JdbcPnlFactDetailProvider(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public List<PnlFact> findForUser(String batchNo, AnalysisPerspective perspective, String userId) {
        // 本次修复：批次号和用户标识拒绝 null/空白并统一去除首尾空格，传入 SQL 的是清理后的值。
        String requiredBatchNo = required(batchNo, "batchNo");
        if (perspective == null) {
            throw new IllegalArgumentException("perspective must not be null");
        }
        String requiredUserId = required(userId, "userId");

        String sql = "SELECT f.scope_code, f.base_code, f.product_code, f.line_code, "
                + "f.volume, f.revenue, f.sales_cost, f.idle_expense, f.base_expense, f.rd_expense, "
                + "f.asset_impairment, f.credit_impairment, f.other_income, f.budget_volume, f.budget_revenue, "
                + "f.budget_sales_cost, f.budget_idle_expense, f.budget_base_expense, f.budget_rd_expense, "
                + "f.budget_asset_impairment, f.budget_credit_impairment, f.budget_other_income, "
                + "f.material_cost, f.labor_cost, f.outsourcing_cost, f.variable_mfg_cost, f.fixed_mfg_cost, "
                + "f.budget_material_cost, f.budget_labor_cost, f.budget_outsourcing_cost, "
                + "f.budget_variable_mfg_cost, f.budget_fixed_mfg_cost, f.output_wh, f.budget_output_wh, "
                + "f.material_loss, f.budget_material_loss "
                + "FROM raw.pnl_fact f "
                + "JOIN meta.analysis_batch b ON b.id = f.batch_id "
                + "WHERE b.batch_no = ? AND f.perspective = ? "
                + "AND EXISTS (SELECT 1 FROM iam.user_scope s "
                + "WHERE s.user_id = ? AND s.perspective = f.perspective AND s.enabled = TRUE "
                + "AND s.can_view_detail = TRUE "
                // 本次修改：ADMIN 只扩大同视角 scope 读取范围，保留明细权限布尔门禁。
                + "AND (s.role_code = 'ADMIN' OR s.scope_code = 'ALL' OR s.scope_code = f.scope_code)) "
                + "ORDER BY f.scope_code, f.base_code, f.product_code, f.line_code, f.id";
        return jdbc.query(sql, (rs, row) -> new PnlFact(
                rs.getString("scope_code"), rs.getString("base_code"),
                rs.getString("product_code"), rs.getString("line_code"),
                rs.getBigDecimal("volume"), rs.getBigDecimal("revenue"),
                rs.getBigDecimal("sales_cost"), rs.getBigDecimal("idle_expense"),
                rs.getBigDecimal("base_expense"), rs.getBigDecimal("rd_expense"),
                rs.getBigDecimal("asset_impairment"), rs.getBigDecimal("credit_impairment"),
                rs.getBigDecimal("other_income"), rs.getBigDecimal("budget_volume"),
                rs.getBigDecimal("budget_revenue"), rs.getBigDecimal("budget_sales_cost"),
                rs.getBigDecimal("budget_idle_expense"), rs.getBigDecimal("budget_base_expense"),
                rs.getBigDecimal("budget_rd_expense"), rs.getBigDecimal("budget_asset_impairment"),
                rs.getBigDecimal("budget_credit_impairment"), rs.getBigDecimal("budget_other_income"),
                rs.getBigDecimal("material_cost"), rs.getBigDecimal("labor_cost"),
                rs.getBigDecimal("outsourcing_cost"), rs.getBigDecimal("variable_mfg_cost"),
                rs.getBigDecimal("fixed_mfg_cost"), rs.getBigDecimal("budget_material_cost"),
                rs.getBigDecimal("budget_labor_cost"), rs.getBigDecimal("budget_outsourcing_cost"),
                rs.getBigDecimal("budget_variable_mfg_cost"), rs.getBigDecimal("budget_fixed_mfg_cost"),
                rs.getBigDecimal("output_wh"), rs.getBigDecimal("budget_output_wh"),
                rs.getBigDecimal("material_loss"), rs.getBigDecimal("budget_material_loss")),
                requiredBatchNo, perspective.getCode(), requiredUserId);
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
