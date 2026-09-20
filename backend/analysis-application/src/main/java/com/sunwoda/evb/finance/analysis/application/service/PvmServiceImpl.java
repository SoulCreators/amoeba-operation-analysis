package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResult;
import com.sunwoda.evb.finance.analysis.domain.model.PnlResult;
import com.sunwoda.evb.finance.analysis.domain.model.PvmResult;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PvmServiceImpl implements PvmService {
    private final CalculationService calculationService;

    public PvmServiceImpl(CalculationService calculationService) {
        this.calculationService = calculationService;
    }

    @Override
    public List<PvmResult> analyze(String batchNo, String userId) {
        CalculationResult calculation = calculationService.getForUser(batchNo, userId);
        String logicCode = calculation.getPerspective() == AnalysisPerspective.BASE
                ? "BASE_VOLUME_PRICE_COST" : "PLBU_VOLUME_PRICE_COST";
        List<PvmResult> result = new ArrayList<PvmResult>();
        for (PnlResult pnl : calculation.getResults()) {
            Map<String, BigDecimal> v = pnl.getLines();
            BigDecimal budgetVolume = value(v, "BUDGET_VOLUME");
            BigDecimal actualVolume = value(v, "VOLUME");
            BigDecimal budgetRevenue = value(v, "BUDGET_REVENUE");
            BigDecimal actualRevenue = value(v, "REVENUE");
            BigDecimal budgetCost = value(v, "BUDGET_SALES_COST");
            BigDecimal actualCost = value(v, "SALES_COST");
            BigDecimal budgetUnitPrice = unitPrice(budgetRevenue, budgetVolume);
            BigDecimal actualUnitPrice = unitPrice(actualRevenue, actualVolume);
            BigDecimal volumeImpact = actualVolume.subtract(budgetVolume).multiply(budgetUnitPrice);
            BigDecimal priceImpact = actualUnitPrice.subtract(budgetUnitPrice).multiply(actualVolume);
            BigDecimal revenueGap = actualRevenue.subtract(budgetRevenue);
            BigDecimal costGap = actualCost.subtract(budgetCost);
            BigDecimal grossProfitGap = value(v, "GAP_GROSS_PROFIT");
            Map<String, BigDecimal> values = new LinkedHashMap<String, BigDecimal>();
            values.put("BUDGET_VOLUME", budgetVolume);
            values.put("ACTUAL_VOLUME", actualVolume);
            values.put("VOLUME_GAP", actualVolume.subtract(budgetVolume));
            values.put("BUDGET_REVENUE", budgetRevenue);
            values.put("ACTUAL_REVENUE", actualRevenue);
            values.put("REVENUE_GAP", revenueGap);
            values.put("REVENUE_VOLUME_IMPACT", volumeImpact);
            values.put("REVENUE_PRICE_IMPACT", priceImpact);
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
            values.put("PROFIT_VOLUME_IMPACT", volumeImpact);
            values.put("PROFIT_PRICE_IMPACT", priceImpact);
            values.put("PROFIT_COST_IMPACT", costGap.negate());
            values.put("PROFIT_MATERIAL_IMPACT", materialGap.negate());
            values.put("PROFIT_LABOR_IMPACT", laborGap.negate());
            values.put("PROFIT_OUTSOURCING_IMPACT", outsourcingGap.negate());
            values.put("PROFIT_VARIABLE_MFG_IMPACT", variableMfgGap.negate());
            values.put("PROFIT_FIXED_MFG_IMPACT", fixedMfgGap.negate());
            values.put("REVENUE_CHECK_DIFF", revenueGap.subtract(volumeImpact).subtract(priceImpact));
            values.put("GROSS_PROFIT_CHECK_DIFF", grossProfitGap.subtract(revenueGap).add(costGap));
            result.add(new PvmResult(pnl.getScopeCode(), logicCode, values));
        }
        return result;
    }

    private BigDecimal value(Map<String, BigDecimal> values, String key) {
        BigDecimal value = values.get(key);
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal unitPrice(BigDecimal revenue, BigDecimal volume) {
        return volume.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO
                : revenue.divide(volume, 8, RoundingMode.HALF_UP);
    }
}
