package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResult;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class V13AnalysisServiceOverviewEdgeTest {
    @Test
    void zeroBudgetGapRateRemainsFiniteAndTopSortsByAbsoluteGapValue() {
        V13AnalysisService service = serviceWithResults(
                result("SCOPE-A", 300, 100),
                result("SCOPE-B", 50, 0));

        Map<String, Object> response = service.overview("B-OVERVIEW-001", "U001", "ACT_BGT");

        assertEquals("CALCULATED", response.get("status"));
        List<?> metrics = (List<?>) response.get("metrics");
        String responseText = String.valueOf(response);
        assertFalse(responseText.contains("NaN"));
        assertFalse(responseText.contains("Infinity"));

        Map<?, ?> top = (Map<?, ?>) response.get("top");
        List<?> positive = (List<?>) top.get("positive");
        assertEquals("SCOPE-A", ((Map<?, ?>) positive.get(0)).get("scopeCode"));
        Map<?, ?> zeroBudgetItem = (Map<?, ?>) positive.get(1);
        assertEquals("SCOPE-B", zeroBudgetItem.get("scopeCode"));
        assertEquals(0, ((BigDecimal) zeroBudgetItem.get("gapRate")).compareTo(BigDecimal.ZERO));
        assertTrue(metrics.size() >= 2);
        assertTrue(((Map<?, ?>) metrics.get(0)).containsKey("gap"));
    }

    private static V13AnalysisService serviceWithResults(PnlResult... results) {
        CalculationResult calculation = new CalculationResult(
                "B-OVERVIEW", AnalysisPerspective.BASE, Arrays.asList(results));
        return new V13AnalysisServiceImpl(
                new StubCalculationService(calculation), new StubPvmService());
    }

    private static PnlResult result(String scope, double actualGrossProfit, double budgetGrossProfit) {
        Map<String, BigDecimal> values = new LinkedHashMap<String, BigDecimal>();
        values.put("GROSS_PROFIT", decimal(actualGrossProfit));
        values.put("BUDGET_GROSS_PROFIT", decimal(budgetGrossProfit));
        return new PnlResult(scope, values);
    }

    private static BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value);
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
