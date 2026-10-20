package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResult;
import com.sunwoda.evb.finance.analysis.domain.model.PnlFact;
import com.sunwoda.evb.finance.analysis.domain.model.PnlResult;
import com.sunwoda.evb.finance.analysis.domain.model.PvmResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * PVM-CH13-001-TECH-001~004 的增量技术回归。
 * 只验证当前公式、阻断和技术状态，不定义新的正式业务口径。
 */
class PvmCh13TechnicalRegressionTest {
    @Test
    void allPerspectivesKeepScopeRowsIndependentAndReconciled() {
        for (AnalysisPerspective perspective : AnalysisPerspective.values()) {
            List<PvmResult> results = analyze(perspective, Arrays.asList(
                    pnl("SCOPE-A", 100, 80, 1000, 720, 600, 500, -180),
                    pnl("SCOPE-B", 50, 60, 500, 720, 300, 390, 130)));

            assertEquals(2, results.size());
            assertEquals("SCOPE-A", results.get(0).getScopeCode());
            assertEquals("SCOPE-B", results.get(1).getScopeCode());
            assertEquals(expectedLogicCode(perspective), results.get(0).getLogicCode());
            assertEquals(expectedLogicCode(perspective), results.get(1).getLogicCode());

            Map<String, BigDecimal> first = results.get(0).getValues();
            assertDecimalEquals(decimal(-200), first.get("REVENUE_VOLUME_IMPACT"));
            assertDecimalEquals(decimal(-80), first.get("REVENUE_PRICE_IMPACT"));
            assertDecimalEquals(decimal(-100), first.get("COST_GAP"));
            assertDecimalEquals(decimal(100), first.get("PROFIT_COST_IMPACT"));
            assertDecimalEquals(BigDecimal.ZERO, first.get("REVENUE_CHECK_DIFF"));
            assertDecimalEquals(BigDecimal.ZERO, first.get("GROSS_PROFIT_CHECK_DIFF"));

            Map<String, BigDecimal> second = results.get(1).getValues();
            assertDecimalEquals(decimal(100), second.get("REVENUE_VOLUME_IMPACT"));
            assertDecimalEquals(decimal(120), second.get("REVENUE_PRICE_IMPACT"));
            assertDecimalEquals(decimal(90), second.get("COST_GAP"));
            assertDecimalEquals(decimal(-90), second.get("PROFIT_COST_IMPACT"));
            assertDecimalEquals(BigDecimal.ZERO, second.get("REVENUE_CHECK_DIFF"));
            assertDecimalEquals(BigDecimal.ZERO, second.get("GROSS_PROFIT_CHECK_DIFF"));
        }
    }

    @Test
    void negativeVolumeAndPriceScenarioKeepsAdverseDirectionsAndFiniteValues() {
        Map<String, BigDecimal> lines = lines(100, 80, 1000, 720, 600, 500, -180);

        Map<String, BigDecimal> values = analyze(AnalysisPerspective.BASE,
                Collections.singletonList(new PnlResult("NEGATIVE-SCOPE", lines)))
                .get(0).getValues();

        assertDecimalEquals(decimal(-280), values.get("REVENUE_GAP"));
        assertDecimalEquals(decimal(-200), values.get("REVENUE_VOLUME_IMPACT"));
        assertDecimalEquals(decimal(-80), values.get("REVENUE_PRICE_IMPACT"));
        assertDecimalEquals(decimal(100), values.get("PROFIT_COST_IMPACT"));
        assertDecimalEquals(BigDecimal.ZERO, values.get("REVENUE_CHECK_DIFF"));
        assertDecimalEquals(BigDecimal.ZERO, values.get("GROSS_PROFIT_CHECK_DIFF"));
        assertFinite(values);
    }

    @Test
    void missingPvmLinesDefaultToZeroWithoutNonFiniteValues() {
        Map<String, BigDecimal> incomplete = new LinkedHashMap<String, BigDecimal>();
        incomplete.put("BUDGET_VOLUME", decimal(10));
        incomplete.put("VOLUME", decimal(8));
        incomplete.put("BUDGET_REVENUE", decimal(100));
        incomplete.put("REVENUE", decimal(80));

        Map<String, BigDecimal> values = analyze(AnalysisPerspective.BUSINESS_UNIT,
                Collections.singletonList(new PnlResult("MISSING-COST-SCOPE", incomplete)))
                .get(0).getValues();

        assertDecimalEquals(BigDecimal.ZERO, values.get("BUDGET_COST"));
        assertDecimalEquals(BigDecimal.ZERO, values.get("ACTUAL_COST"));
        assertDecimalEquals(BigDecimal.ZERO, values.get("COST_GAP"));
        assertDecimalEquals(BigDecimal.ZERO, values.get("PROFIT_COST_IMPACT"));
        assertDecimalEquals(BigDecimal.ZERO, values.get("REVENUE_CHECK_DIFF"));
        assertDecimalEquals(decimal(20), values.get("GROSS_PROFIT_CHECK_DIFF"));
        assertFinite(values);
    }

    @Test
    void technicalEfficiencyStaysTechnicalWhenNonLineFactCoexists() {
        PnlFact validLine = factWithWh("SCOPE-A", "L01", 80, 100, 60, 80, 100, 100);
        PnlFact nonLineExpense = factWithoutLine("SCOPE-A", 999, 999);
        CalculationResult calculation = new CalculationResult(
                "B-PVM-TECH-004", AnalysisPerspective.BASE,
                Collections.singletonList(new PnlResult("SCOPE-A", lines(100, 80, 1000, 720, 600, 500, -180))),
                "B-PVM-TECH-004-R1",
                com.sunwoda.evb.finance.analysis.domain.model.CalculationResultStatus.CALCULATED,
                null, null, null, null, null, Arrays.asList(validLine, nonLineExpense));

        Map<String, Object> response = new V13AnalysisServiceImpl(
                new StubCalculationService(calculation), new StubPvmService()).unitEfficiency(
                "B-PVM-TECH-004", "U001");

        assertEquals("CALCULATED_TECHNICAL", response.get("status"));
        assertEquals(1, ((List<?>) response.get("rows")).size());
        assertEquals("L01", ((Map<?, ?>) ((List<?>) response.get("rows")).get(0)).get("lineCode"));
        assertEquals(Arrays.asList("VARIABLE_MFG_COST", "FIXED_MFG_COST"), response.get("costScope"));
    }

    private static List<PvmResult> analyze(AnalysisPerspective perspective, List<PnlResult> pnlResults) {
        CalculationResult calculation = new CalculationResult(
                "B-PVM-TECH-REGRESSION", perspective, pnlResults);
        return new PvmServiceImpl(new StubCalculationService(calculation))
                .analyze("B-PVM-TECH-REGRESSION", "U001");
    }

    private static PnlResult pnl(String scope, double budgetVolume, double actualVolume,
                                 double budgetRevenue, double actualRevenue,
                                 double budgetCost, double actualCost, double grossProfitGap) {
        return new PnlResult(scope, lines(budgetVolume, actualVolume, budgetRevenue,
                actualRevenue, budgetCost, actualCost, grossProfitGap));
    }

    private static Map<String, BigDecimal> lines(double budgetVolume, double actualVolume,
                                                  double budgetRevenue, double actualRevenue,
                                                  double budgetCost, double actualCost,
                                                  double grossProfitGap) {
        Map<String, BigDecimal> lines = new LinkedHashMap<String, BigDecimal>();
        lines.put("BUDGET_VOLUME", decimal(budgetVolume));
        lines.put("VOLUME", decimal(actualVolume));
        lines.put("BUDGET_REVENUE", decimal(budgetRevenue));
        lines.put("REVENUE", decimal(actualRevenue));
        lines.put("BUDGET_SALES_COST", decimal(budgetCost));
        lines.put("SALES_COST", decimal(actualCost));
        lines.put("GAP_GROSS_PROFIT", decimal(grossProfitGap));
        return lines;
    }

    private static PnlFact factWithWh(String scope, String line, double volume, double budgetVolume,
                                      double variableMfg, double fixedMfg,
                                      double outputWh, double budgetOutputWh) {
        BigDecimal zero = decimal(0);
        return new PnlFact(scope, null, null, line,
                decimal(volume), decimal(1000), decimal(700), zero, zero, zero, zero, zero, zero,
                decimal(budgetVolume), decimal(900), decimal(600), zero, zero, zero, zero, zero, zero,
                zero, zero, zero, decimal(variableMfg), decimal(fixedMfg),
                zero, zero, zero, zero, zero, decimal(outputWh), decimal(budgetOutputWh));
    }

    private static PnlFact factWithoutLine(String scope, double variableMfg, double fixedMfg) {
        BigDecimal zero = decimal(0);
        return new PnlFact(scope, zero, zero, zero, zero, zero, zero, zero, zero, zero);
    }

    private static String expectedLogicCode(AnalysisPerspective perspective) {
        if (perspective == AnalysisPerspective.BASE) return "BASE_VOLUME_PRICE_COST";
        if (perspective == AnalysisPerspective.PRODUCT_LINE) return "PRODUCT_LINE_VOLUME_PRICE_COST";
        return "BUSINESS_UNIT_VOLUME_PRICE_COST";
    }

    private static void assertFinite(Map<String, BigDecimal> values) {
        for (BigDecimal value : values.values()) {
            assertNotNull(value);
            assertFalse(Double.isNaN(value.doubleValue()));
            assertFalse(Double.isInfinite(value.doubleValue()));
        }
    }

    private static void assertDecimalEquals(BigDecimal expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, expected.compareTo(actual));
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

    private static class StubPvmService implements PvmService {
        @Override public List<PvmResult> analyze(String batchNo, String userId) {
            return Collections.emptyList();
        }
    }
}
