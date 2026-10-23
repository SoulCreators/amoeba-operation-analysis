package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.CalculationResult;
import com.sunwoda.evb.finance.analysis.domain.model.PnlResult;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 一期损益来源与金额勾稽；样表 PVM 因素及原生余数在分析结果中单列。 */
public final class CalculationChecks {
    public static final String RULE_VERSION = "SAMPLE_PNL_PVM_1";
    private CalculationChecks() { }

    public static List<Check> evaluate(CalculationResult result) {
        List<Check> checks = new ArrayList<>();
        for (PnlResult pnl : result.getResults()) {
            Map<String, BigDecimal> v = pnl.getLines();
            for (String prefix : new String[]{"", "BUDGET_"}) {
                add(checks, pnl, prefix + "COST_COMPONENTS", get(v, prefix + "SALES_COST"),
                        get(v, prefix + "MATERIAL_COST").add(get(v, prefix + "LABOR_COST"))
                                .add(get(v, prefix + "OUTSOURCING_COST"))
                                .add(get(v, prefix + "VARIABLE_MFG_COST"))
                                .add(get(v, prefix + "FIXED_MFG_COST")));
                add(checks, pnl, prefix + "PRODUCT_GROSS_PROFIT",
                        get(v, prefix + "REVENUE").subtract(get(v, prefix + "SALES_COST")),
                        get(v, prefix + "GROSS_PROFIT"));
            }
            // 样表零量分支已经定义；PVM 桥差在查询结果中显示，不作为损益发布门禁。
        }
        if (checks.isEmpty()) checks.add(Check.blocked("ALL", "EMPTY_RESULT", "无可勾稽结果"));
        return checks;
    }

    private static BigDecimal get(Map<String, BigDecimal> v, String key) {
        if (v.get(key) == null) throw new IllegalStateException("勾稽缺少必要结果项: " + key);
        return v.get(key);
    }
    private static void add(List<Check> list, PnlResult pnl, String code, BigDecimal expected, BigDecimal actual) {
        list.add(new Check(pnl.getScopeCode(), code, expected, actual, null));
    }
    public static final class Check {
        public final String scopeCode, code, status, reason;
        public final BigDecimal expected, actual, difference;
        private Check(String scope, String code, BigDecimal expected, BigDecimal actual, String reason) {
            this.scopeCode = scope; this.code = code; this.expected = expected; this.actual = actual;
            this.difference = expected == null ? null : actual.subtract(expected);
            this.status = reason != null ? "BLOCKED" : difference.signum() == 0 ? "PASS" : "FAIL";
            this.reason = reason;
        }
        public static Check blocked(String scope, String code, String reason) {
            return new Check(scope, code, null, null, reason);
        }
    }
}
