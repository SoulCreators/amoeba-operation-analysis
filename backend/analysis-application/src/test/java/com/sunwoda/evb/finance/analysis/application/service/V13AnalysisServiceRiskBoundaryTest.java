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
import static org.junit.jupiter.api.Assertions.assertTrue;

class V13AnalysisServiceRiskBoundaryTest {
    @Test
    void negativeGapsBecomeObserveOnlyRisksUntilThresholdsAreConfirmed() {
        V13AnalysisService service = serviceWithResults(
                result("BASE-SZ", 90, 100, 180, 200, 70, 80));

        Map<String, Object> response = service.risk("B-RISK-001", "U001");

        assertEquals("PARTIAL_PENDING_THRESHOLD", response.get("status"));
        List<?> risks = (List<?>) response.get("risks");
        assertEquals(3, risks.size());
        for (Object value : risks) {
            Map<?, ?> risk = (Map<?, ?>) value;
            assertEquals("OBSERVE", risk.get("level"));
            assertEquals("PENDING_DEFINITION", risk.get("thresholdStatus"));
        }
        assertTrue(((List<?>) response.get("blockers")).get(0).toString().contains("阈值"));
    }

    @Test
    void noNegativeGapProducesNoRiskRowsButKeepsThresholdBlocker() {
        V13AnalysisService service = serviceWithResults(
                result("BASE-SZ", 110, 100, 220, 200, 90, 80));

        Map<String, Object> response = service.risk("B-RISK-002", "U001");

        assertEquals("CALCULATED", response.get("status"));
        assertTrue(((List<?>) response.get("risks")).isEmpty());
        assertTrue(((List<?>) response.get("blockers")).get(0).toString().contains("阈值"));
    }

    private static V13AnalysisService serviceWithResults(PnlResult... results) {
        CalculationResult calculation = new CalculationResult(
                "B-RISK", AnalysisPerspective.BASE, Arrays.asList(results));
        return new V13AnalysisServiceImpl(
                new StubCalculationService(calculation), new StubPvmService());
    }

    private static PnlResult result(String scope, double revenue, double budgetRevenue,
                                    double grossProfit, double budgetGrossProfit,
                                    double netProfit, double budgetNetProfit) {
        Map<String, BigDecimal> values = new LinkedHashMap<String, BigDecimal>();
        values.put("REVENUE", decimal(revenue));
        values.put("BUDGET_REVENUE", decimal(budgetRevenue));
        values.put("GROSS_PROFIT", decimal(grossProfit));
        values.put("BUDGET_GROSS_PROFIT", decimal(budgetGrossProfit));
        values.put("NET_PROFIT", decimal(netProfit));
        values.put("BUDGET_NET_PROFIT", decimal(budgetNetProfit));
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
