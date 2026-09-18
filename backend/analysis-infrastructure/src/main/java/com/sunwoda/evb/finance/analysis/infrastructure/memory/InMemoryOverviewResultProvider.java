package com.sunwoda.evb.finance.analysis.infrastructure.memory;

import com.sunwoda.evb.finance.analysis.application.port.OverviewResultProvider;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.OverviewMetricResult;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Repository
public class InMemoryOverviewResultProvider implements OverviewResultProvider {
    @Override
    public List<OverviewMetricResult> query(String period, AnalysisPerspective perspective, String scope) {
        String suffix = "（" + perspective.getDisplayName() + "·" + scope + "）";
        List<OverviewMetricResult> results = new ArrayList<OverviewMetricResult>();
        results.add(metric("REVENUE", "营业收入", "元", "128600000", "134000000", "收入低于预算，建议结合量价影响分析。", suffix));
        results.add(metric("GROSS_PROFIT", "毛利", "元", "23400000", "24900000", "毛利差异需结合材料和制造费用核查。", suffix));
        results.add(metric("OPERATING_PROFIT", "营业利润", "元", "8600000", "9200000", "营业利润受毛利和期间费用共同影响。", suffix));
        results.add(metric("NET_PROFIT_RATE", "净利率", "%", "0.126", "0.138", "净利率低于预算，建议下钻费用和减值项目。", suffix));
        return results;
    }

    private OverviewMetricResult metric(String code, String name, String unit,
                                        String actual, String budget, String ai, String suffix) {
        BigDecimal actualValue = new BigDecimal(actual);
        BigDecimal budgetValue = new BigDecimal(budget);
        return new OverviewMetricResult(code, name + suffix, unit, actualValue, budgetValue,
                actualValue.subtract(budgetValue), budgetValue.multiply(new BigDecimal("0.96")), ai);
    }
}
