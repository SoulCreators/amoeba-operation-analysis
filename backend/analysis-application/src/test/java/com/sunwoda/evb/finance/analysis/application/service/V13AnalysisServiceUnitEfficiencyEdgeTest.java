package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResult;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResultStatus;
import com.sunwoda.evb.finance.analysis.domain.model.PnlFact;
import com.sunwoda.evb.finance.analysis.domain.model.PnlResult;
import com.sunwoda.evb.finance.analysis.domain.model.PvmResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V13AnalysisServiceUnitEfficiencyEdgeTest {
    @Test
    void sameLineUsesVariablePlusFixedManufacturingCostAndWeightedWhRatio() {
        V13AnalysisService service = serviceWithFacts(
                factWithWh("NANCHANG", "L01", 1000, 800, 100, 50, 80, 40),
                factWithWh("NANCHANG", "L01", 3000, 4000, 300, 100, 200, 250));

        Map<String, Object> response = service.unitEfficiency("B-EDGE-000", "U001");

        assertEquals("CALCULATED_TECHNICAL", response.get("status"));
        Map<?, ?> row = (Map<?, ?>) ((List<?>) response.get("rows")).get(0);
        assertEquals(0, ((BigDecimal) row.get("actualManufacturingCost")).compareTo(decimal(550)));
        assertEquals(0, ((BigDecimal) row.get("budgetManufacturingCost")).compareTo(decimal(570)));
        assertEquals(0, ((BigDecimal) row.get("actualOutputWh")).compareTo(decimal(4000)));
        assertEquals(0, ((BigDecimal) row.get("budgetOutputWh")).compareTo(decimal(4800)));
        assertEquals(0, ((BigDecimal) row.get("actualPerWh")).compareTo(decimal("0.1375")));
        assertEquals(0, ((BigDecimal) row.get("budgetPerWh")).compareTo(decimal("0.11875")));
    }

    @Test
    void sameScopeAndLineRecalculatesFromTotalCostAndTotalWh() {
        V13AnalysisService service = serviceWithFacts(
                factWithWh("NANCHANG", "L01", 1000, 900, 100, 90),
                factWithWh("NANCHANG", "L01", 3000, 2700, 300, 270));

        Map<String, Object> response = service.unitEfficiency("B-EDGE-001", "U001");

        assertEquals("CALCULATED_TECHNICAL", response.get("status"));
        List<?> rows = (List<?>) response.get("rows");
        assertEquals(1, rows.size());
        Map<?, ?> row = (Map<?, ?>) rows.get(0);
        assertEquals(0, ((BigDecimal) row.get("actualManufacturingCost")).compareTo(decimal(400)));
        assertEquals(0, ((BigDecimal) row.get("budgetManufacturingCost")).compareTo(decimal(360)));
        assertEquals(0, ((BigDecimal) row.get("actualOutputWh")).compareTo(decimal(4000)));
        assertEquals(0, ((BigDecimal) row.get("budgetOutputWh")).compareTo(decimal(3600)));
        assertEquals(0, ((BigDecimal) row.get("actualPerWh")).compareTo(decimal("0.1")));
        assertEquals(0, ((BigDecimal) row.get("budgetPerWh")).compareTo(decimal("0.1")));
    }

    @Test
    void nullLineFactsAreFilteredWithoutBlockingValidLineResults() {
        V13AnalysisService service = serviceWithFacts(
                factWithWh("NANCHANG", null, 1000, 900, 100, 90),
                factWithWh("NANCHANG", "L01", 1000, 900, 100, 90));

        Map<String, Object> response = service.unitEfficiency("B-EDGE-002", "U001");

        assertEquals("CALCULATED_TECHNICAL", response.get("status"));
        List<?> rows = (List<?>) response.get("rows");
        assertEquals(1, rows.size());
        Map<?, ?> row = (Map<?, ?>) rows.get(0);
        assertEquals("NANCHANG", row.get("scopeCode"));
        assertEquals("L01", row.get("lineCode"));
        assertTrue(((List<?>) response.get("blockers")).get(0).toString().contains("业务仍需确认"));
    }

    @Test
    void zeroOrNegativeWhFactsAreBlockedWithoutNaNOrInfinity() {
        V13AnalysisService service = serviceWithFacts(
                factWithWh("NANCHANG", "ACTUAL_ZERO", 0, 900, 100, 90),
                factWithWh("NANCHANG", "ACTUAL_NEGATIVE", -1, 900, 100, 90),
                factWithWh("NANCHANG", "BUDGET_ZERO", 1000, 0, 100, 90),
                factWithWh("NANCHANG", "BUDGET_NEGATIVE", 1000, -1, 100, 90));

        Map<String, Object> response = service.unitEfficiency("B-EDGE-003", "U001");
        String responseText = String.valueOf(response);

        assertEquals("PENDING_DEFINITION", response.get("status"));
        assertTrue(((List<?>) response.get("rows")).isEmpty());
        List<?> blockers = (List<?>) response.get("blockers");
        assertEquals(4, blockers.size());
        assertTrue(blockers.stream().anyMatch(value -> value.toString().contains("ACTUAL_ZERO")));
        assertTrue(blockers.stream().anyMatch(value -> value.toString().contains("ACTUAL_NEGATIVE")));
        assertTrue(blockers.stream().anyMatch(value -> value.toString().contains("BUDGET_ZERO")));
        assertTrue(blockers.stream().anyMatch(value -> value.toString().contains("BUDGET_NEGATIVE")));
        assertFalse(responseText.contains("NaN"));
        assertFalse(responseText.contains("Infinity"));
    }

    @Test
    void noFactsReturnsPendingDefinitionWithEmptyRowsAndBlockers() {
        V13AnalysisService service = new V13AnalysisServiceImpl(
                new StubCalculationService(emptyCalculation()), new StubPvmService());

        Map<String, Object> response = service.unitEfficiency("B-EDGE-004", "U001");

        assertEquals("PENDING_DEFINITION", response.get("status"));
        assertTrue(((List<?>) response.get("rows")).isEmpty());
        assertFalse(((List<?>) response.get("blockers")).isEmpty());
    }

    private static V13AnalysisService serviceWithFacts(PnlFact... facts) {
        return new V13AnalysisServiceImpl(
                new StubCalculationService(calculationWithFacts(facts)), new StubPvmService());
    }

    private static CalculationResult calculationWithFacts(PnlFact... facts) {
        return new CalculationResult("B-EDGE", AnalysisPerspective.BASE,
                Collections.singletonList(new PnlResult("NANCHANG", lines())),
                "B-EDGE-R1", CalculationResultStatus.CALCULATED, null, null,
                LocalDateTime.now(), null, null, Arrays.asList(facts));
    }

    private static CalculationResult emptyCalculation() {
        return new CalculationResult("B-EDGE-004", AnalysisPerspective.BASE,
                Collections.singletonList(new PnlResult("NANCHANG", lines())),
                "B-EDGE-004-R1", CalculationResultStatus.CALCULATED, null, null,
                LocalDateTime.now(), null, null, Collections.<PnlFact>emptyList());
    }

    private static Map<String, BigDecimal> lines() {
        Map<String, BigDecimal> lines = new LinkedHashMap<String, BigDecimal>();
        lines.put("VOLUME", decimal(100));
        lines.put("BUDGET_VOLUME", decimal(90));
        lines.put("REVENUE", decimal(1000));
        lines.put("BUDGET_REVENUE", decimal(900));
        lines.put("SALES_COST", decimal(700));
        lines.put("BUDGET_SALES_COST", decimal(650));
        return lines;
    }

    private static PnlFact factWithWh(String scope, String line, double actualWh, double budgetWh,
                                      double actualFixedMfg, double budgetFixedMfg) {
        return factWithWh(scope, line, actualWh, budgetWh, 0, actualFixedMfg, 0, budgetFixedMfg);
    }

    private static PnlFact factWithWh(String scope, String line, double actualWh, double budgetWh,
                                      double actualVariableMfg, double actualFixedMfg,
                                      double budgetVariableMfg, double budgetFixedMfg) {
        BigDecimal zero = decimal(0);
        return new PnlFact(scope, "BASE-1", "PRODUCT-1", line,
                decimal(100), decimal(1000), decimal(700), zero, zero, zero, zero, zero, zero,
                decimal(90), decimal(950), decimal(680), zero, zero, zero, zero, zero, zero,
                zero, zero, zero, decimal(actualVariableMfg), decimal(actualFixedMfg),
                zero, zero, zero, decimal(budgetVariableMfg),
                decimal(budgetFixedMfg), decimal(actualWh), decimal(budgetWh));
    }

    private static BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value);
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }

    private static class StubCalculationService implements CalculationService {
        private final CalculationResult result;

        StubCalculationService(CalculationResult result) {
            this.result = result;
        }

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
