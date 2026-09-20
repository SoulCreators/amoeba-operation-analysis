package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResult;
import com.sunwoda.evb.finance.analysis.domain.model.PnlResult;
import com.sunwoda.evb.finance.analysis.domain.model.PvmResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PvmServiceImplTest {
    @Test
    void calculatesVolumePriceCostImpactsAndChecks() {
        Map<String, BigDecimal> lines = new LinkedHashMap<String, BigDecimal>();
        lines.put("BUDGET_VOLUME", decimal(100));
        lines.put("VOLUME", decimal(110));
        lines.put("BUDGET_REVENUE", decimal(1000));
        lines.put("REVENUE", decimal(1210));
        lines.put("BUDGET_SALES_COST", decimal(700));
        lines.put("SALES_COST", decimal(770));
        lines.put("GAP_GROSS_PROFIT", decimal(140));
        CalculationResult calculation = new CalculationResult("B-008", AnalysisPerspective.BASE,
                Arrays.asList(new PnlResult("南昌", lines)));
        CalculationService service = new StubCalculationService(calculation);

        List<PvmResult> result = new PvmServiceImpl(service).analyze("B-008", "U001");

        Map<String, BigDecimal> values = result.get(0).getValues();
        assertEquals(0, values.get("REVENUE_VOLUME_IMPACT").compareTo(decimal(100)));
        assertEquals(0, values.get("REVENUE_PRICE_IMPACT").compareTo(decimal(110)));
        assertEquals(0, values.get("REVENUE_CHECK_DIFF").compareTo(BigDecimal.ZERO));
        assertEquals(0, values.get("GROSS_PROFIT_CHECK_DIFF").compareTo(BigDecimal.ZERO));
        assertEquals("BASE_VOLUME_PRICE_COST", result.get(0).getLogicCode());
    }

    private static BigDecimal decimal(double value) { return BigDecimal.valueOf(value); }

    private static class StubCalculationService implements CalculationService {
        private final CalculationResult result;
        StubCalculationService(CalculationResult result) { this.result = result; }
        @Override public CalculationResult calculate(String batchNo) { return result; }
        @Override public CalculationResult get(String batchNo) { return result; }
        @Override public CalculationResult getForUser(String batchNo, String userId) { return result; }
        @Override public CalculationResult confirm(String batchNo, String operator) { return result; }
        @Override public CalculationResult publish(String batchNo, String operator) { return result; }
    }
}
