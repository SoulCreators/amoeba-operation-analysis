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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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

    @Test
    void revenuePvmVolumeAndPriceImpactsReconcileToRevenueGap() {
        Map<String, BigDecimal> lines = new LinkedHashMap<String, BigDecimal>();
        lines.put("BUDGET_VOLUME", decimal(100));
        lines.put("VOLUME", decimal(125));
        lines.put("BUDGET_REVENUE", decimal(1000));
        lines.put("REVENUE", decimal(1500));
        lines.put("BUDGET_SALES_COST", decimal(600));
        lines.put("SALES_COST", decimal(900));
        lines.put("GAP_GROSS_PROFIT", decimal(200));

        PvmResult pvm = analyze(lines).get(0);
        Map<String, BigDecimal> values = pvm.getValues();

        assertEquals(0, values.get("REVENUE_VOLUME_IMPACT").compareTo(decimal(250)));
        assertEquals(0, values.get("REVENUE_PRICE_IMPACT").compareTo(decimal(250)));
        assertEquals(0, values.get("REVENUE_GAP").compareTo(decimal(500)));
        assertEquals(0, values.get("REVENUE_CHECK_DIFF").compareTo(BigDecimal.ZERO));
    }

    @Test
    void grossProfitPvmCostImpactIsAdverseWhenCostIncreasesAndReconciles() {
        Map<String, BigDecimal> lines = new LinkedHashMap<String, BigDecimal>();
        lines.put("BUDGET_VOLUME", decimal(100));
        lines.put("VOLUME", decimal(110));
        lines.put("BUDGET_REVENUE", decimal(1000));
        lines.put("REVENUE", decimal(1210));
        lines.put("BUDGET_SALES_COST", decimal(700));
        lines.put("SALES_COST", decimal(770));
        lines.put("GAP_GROSS_PROFIT", decimal(140));

        Map<String, BigDecimal> values = analyze(lines).get(0).getValues();

        assertEquals(0, values.get("COST_GAP").compareTo(decimal(70)));
        assertEquals(0, values.get("PROFIT_COST_IMPACT").compareTo(decimal(-70)));
        assertEquals(0, values.get("GROSS_PROFIT_CHECK_DIFF").compareTo(BigDecimal.ZERO));
    }

    @Test
    void zeroBudgetOrActualVolumeDoesNotProduceNonFinitePvmValues() {
        Map<String, BigDecimal> zeroBudgetVolume = new LinkedHashMap<String, BigDecimal>();
        zeroBudgetVolume.put("BUDGET_VOLUME", BigDecimal.ZERO);
        zeroBudgetVolume.put("VOLUME", decimal(10));
        zeroBudgetVolume.put("BUDGET_REVENUE", BigDecimal.ZERO);
        zeroBudgetVolume.put("REVENUE", decimal(120));
        zeroBudgetVolume.put("BUDGET_SALES_COST", BigDecimal.ZERO);
        zeroBudgetVolume.put("SALES_COST", decimal(60));
        zeroBudgetVolume.put("GAP_GROSS_PROFIT", decimal(60));

        Map<String, BigDecimal> zeroActualVolume = new LinkedHashMap<String, BigDecimal>();
        zeroActualVolume.put("BUDGET_VOLUME", decimal(10));
        zeroActualVolume.put("VOLUME", BigDecimal.ZERO);
        zeroActualVolume.put("BUDGET_REVENUE", decimal(100));
        zeroActualVolume.put("REVENUE", BigDecimal.ZERO);
        zeroActualVolume.put("BUDGET_SALES_COST", decimal(60));
        zeroActualVolume.put("SALES_COST", BigDecimal.ZERO);
        zeroActualVolume.put("GAP_GROSS_PROFIT", decimal(-40));

        assertFinite(analyze(zeroBudgetVolume).get(0).getValues());
        assertFinite(analyze(zeroActualVolume).get(0).getValues());
        assertEquals(0, analyze(zeroBudgetVolume).get(0).getValues()
                .get("REVENUE_CHECK_DIFF").compareTo(BigDecimal.ZERO));
        assertEquals(0, analyze(zeroActualVolume).get(0).getValues()
                .get("REVENUE_CHECK_DIFF").compareTo(BigDecimal.ZERO));
    }

    private static List<PvmResult> analyze(Map<String, BigDecimal> lines) {
        CalculationResult calculation = new CalculationResult("B-PVM-REGRESSION", AnalysisPerspective.BASE,
                Arrays.asList(new PnlResult("南昌", lines)));
        return new PvmServiceImpl(new StubCalculationService(calculation)).analyze("B-PVM-REGRESSION", "U001");
    }

    private static void assertFinite(Map<String, BigDecimal> values) {
        for (BigDecimal value : values.values()) {
            assertNotNull(value);
            assertFalse(Double.isNaN(value.doubleValue()));
            assertFalse(Double.isInfinite(value.doubleValue()));
        }
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
