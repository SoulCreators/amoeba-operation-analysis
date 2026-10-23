package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResult;
import com.sunwoda.evb.finance.analysis.domain.model.PnlResult;
import com.sunwoda.evb.finance.analysis.domain.model.PvmResult;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PvmServiceImpl implements PvmService {
    private static final String BASE_LOGIC_CODE = "BASE_VOLUME_PRICE_COST";
    private static final String PRODUCT_LINE_LOGIC_CODE = "PRODUCT_LINE_VOLUME_PRICE_COST";
    private static final String BUSINESS_UNIT_LOGIC_CODE = "BUSINESS_UNIT_VOLUME_PRICE_COST";

    private final CalculationService calculationService;
    private final SamplePvmCalculator sampleCalculator;

    public PvmServiceImpl(CalculationService calculationService) {
        this.calculationService = calculationService;
        this.sampleCalculator = new SamplePvmCalculator();
    }

    @Override
    public List<PvmResult> analyze(String batchNo, String userId) {
        CalculationResult calculation = calculationService.getForUser(batchNo, userId);
        String logicCode = logicCodeFor(calculation.getPerspective());
        List<PvmResult> result = new ArrayList<PvmResult>();
        for (PnlResult pnl : calculation.getResults()) {
            Map<String, BigDecimal> v = pnl.getLines();
            BigDecimal budgetVolume = value(v, "BUDGET_VOLUME");
            BigDecimal actualVolume = value(v, "VOLUME");
            BigDecimal budgetRevenue = value(v, "BUDGET_REVENUE");
            BigDecimal actualRevenue = value(v, "REVENUE");
            BigDecimal budgetCost = value(v, "BUDGET_SALES_COST");
            BigDecimal actualCost = value(v, "SALES_COST");
            Map<String, BigDecimal> impacts = sampleCalculator.calculate(pnl, calculation.getFacts());
            BigDecimal revenueGap = actualRevenue.subtract(budgetRevenue);
            BigDecimal costGap = actualCost.subtract(budgetCost);
            BigDecimal grossProfitGap = actualRevenue.subtract(actualCost)
                    .subtract(budgetRevenue.subtract(budgetCost));
            BigDecimal revenueBridge = value(impacts, "REVENUE_PRICE_IMPACT")
                    .add(value(impacts, "REVENUE_VOLUME_IMPACT"))
                    .add(value(impacts, "REVENUE_STRUCTURE_IMPACT"));
            BigDecimal profitBridge = value(impacts, "PROFIT_PRICE_IMPACT")
                    .add(value(impacts, "PROFIT_VOLUME_IMPACT"))
                    .add(value(impacts, "PROFIT_COST_IMPACT"))
                    .add(value(impacts, "PROFIT_STRUCTURE_IMPACT"));
            Map<String, BigDecimal> values = new LinkedHashMap<String, BigDecimal>();
            values.put("BUDGET_VOLUME", budgetVolume);
            values.put("ACTUAL_VOLUME", actualVolume);
            values.put("VOLUME_GAP", actualVolume.subtract(budgetVolume));
            values.put("BUDGET_REVENUE", budgetRevenue);
            values.put("ACTUAL_REVENUE", actualRevenue);
            values.put("REVENUE_GAP", revenueGap);
            values.put("BUDGET_COST", budgetCost);
            values.put("ACTUAL_COST", actualCost);
            values.put("COST_GAP", costGap);
            values.put("GROSS_PROFIT_GAP", grossProfitGap);
            BigDecimal materialGap = value(v, "GAP_MATERIAL_COST");
            BigDecimal laborGap = value(v, "GAP_LABOR_COST");
            BigDecimal outsourcingGap = value(v, "GAP_OUTSOURCING_COST");
            BigDecimal variableMfgGap = value(v, "GAP_VARIABLE_MFG_COST");
            BigDecimal fixedMfgGap = value(v, "GAP_FIXED_MFG_COST");
            values.put("MATERIAL_GAP", materialGap);
            values.put("LABOR_GAP", laborGap);
            values.put("OUTSOURCING_GAP", outsourcingGap);
            values.put("VARIABLE_MFG_GAP", variableMfgGap);
            values.put("FIXED_MFG_GAP", fixedMfgGap);
            values.putAll(impacts);
            values.put("REVENUE_CHECK_DIFF", revenueGap.subtract(revenueBridge));
            values.put("GROSS_PROFIT_CHECK_DIFF", grossProfitGap.subtract(profitBridge));
            result.add(new PvmResult(pnl.getScopeCode(), logicCode, values));
        }
        return result;
    }

    private String logicCodeFor(AnalysisPerspective perspective) {
        if (perspective == AnalysisPerspective.BASE) {
            return BASE_LOGIC_CODE;
        }
        if (perspective == AnalysisPerspective.PRODUCT_LINE) {
            return PRODUCT_LINE_LOGIC_CODE;
        }
        if (perspective == AnalysisPerspective.BUSINESS_UNIT) {
            return BUSINESS_UNIT_LOGIC_CODE;
        }
        throw new IllegalArgumentException("不支持的 PVM 分析视角: " + perspective);
    }

    private BigDecimal value(Map<String, BigDecimal> values, String key) {
        BigDecimal value = values.get(key);
        return value == null ? BigDecimal.ZERO : value;
    }

}
