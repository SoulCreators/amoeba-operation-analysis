package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CalculationChecksTest {
    @Test void balancedScopeBridgeDoesNotApproveMissingFormalGrain() {
        List<CalculationChecks.Check> checks = evaluate(lines());
        assertEquals("PASS", find(checks, "PVM_REVENUE").status);
        assertEquals("PASS", find(checks, "PVM_PRODUCT_GROSS_PROFIT").status);
        assertEquals("BLOCKED", find(checks, "PVM_FORMAL_GRAIN").status);
    }
    @Test void zeroOnEitherSideBlocksInsteadOfInventingUnitPrices() {
        for (String key : Arrays.asList("VOLUME", "BUDGET_VOLUME")) {
            Map<String, BigDecimal> values = lines(); values.put(key, BigDecimal.ZERO);
            List<CalculationChecks.Check> checks = evaluate(values);
            assertEquals("BLOCKED", find(checks, "PVM_ZERO_VOLUME").status);
            assertFalse(checks.stream().anyMatch(c -> c.code.equals("PVM_REVENUE")));
        }
    }
    @Test void smallestNonzeroComponentDifferenceFailsWithoutRounding() {
        Map<String, BigDecimal> values = lines();
        values.put("MATERIAL_COST", new BigDecimal("60.0000000000001"));
        CalculationChecks.Check check = find(evaluate(values), "COST_COMPONENTS");
        assertEquals("FAIL", check.status);
        assertEquals(0, new BigDecimal("0.0000000000001").compareTo(check.difference));
    }
    @Test void missingAmountsCannotSilentlyBecomeZero() {
        Map<String, BigDecimal> values = lines(); values.remove("BUDGET_REVENUE");
        assertThrows(IllegalStateException.class, () -> evaluate(values));
    }
    @Test void grossProfitBridgeChecksTheSumOfReturnedFactors() {
        Map<String, BigDecimal> values = lines();
        values.put("VOLUME", new BigDecimal("3")); values.put("BUDGET_VOLUME", new BigDecimal("3"));
        values.put("BUDGET_REVENUE", new BigDecimal("90")); values.put("BUDGET_GROSS_PROFIT", new BigDecimal("50"));
        values.put("GAP_GROSS_PROFIT", new BigDecimal("-10"));
        List<CalculationChecks.Check> checks = evaluate(values);
        assertEquals("FAIL", find(checks, "PVM_REVENUE").status);
        assertEquals("FAIL", find(checks, "PVM_PRODUCT_GROSS_PROFIT").status);
        assertNotEquals(0, new PvmReconciliationValidator().calculate(new PnlResult("A", values))
                .getGrossProfitCheckDiff().signum());
    }
    private Map<String, BigDecimal> lines() {
        Map<String, BigDecimal> v = new LinkedHashMap<>();
        String[] keys = {"VOLUME", "REVENUE", "SALES_COST", "GROSS_PROFIT", "MATERIAL_COST", "LABOR_COST", "OUTSOURCING_COST", "VARIABLE_MFG_COST", "FIXED_MFG_COST"};
        int[] actual = {10,100,60,40,60,0,0,0,0}, budget = {8,80,40,40,40,0,0,0,0};
        for (int i=0;i<keys.length;i++) { v.put(keys[i], BigDecimal.valueOf(actual[i])); v.put("BUDGET_"+keys[i], BigDecimal.valueOf(budget[i])); }
        v.put("GAP_GROSS_PROFIT", BigDecimal.ZERO);
        return v;
    }
    private List<CalculationChecks.Check> evaluate(Map<String, BigDecimal> lines) {
        return CalculationChecks.evaluate(new CalculationResult("B", AnalysisPerspective.BASE,
                Collections.singletonList(new PnlResult("A", lines))));
    }
    private CalculationChecks.Check find(List<CalculationChecks.Check> checks, String code) {
        return checks.stream().filter(c -> c.code.equals(code)).findFirst().get();
    }
}
