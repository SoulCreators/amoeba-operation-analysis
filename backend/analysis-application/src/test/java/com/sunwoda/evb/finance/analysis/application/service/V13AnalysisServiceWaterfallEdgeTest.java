package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResult;
import com.sunwoda.evb.finance.analysis.domain.model.PnlResult;
import com.sunwoda.evb.finance.analysis.domain.model.PvmResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class V13AnalysisServiceWaterfallEdgeTest {
    @Test
    void zeroOrNegativeVolumeBlocksUnitGrossProfitWithoutDemoNumbers() {
        V13AnalysisService service = serviceWithLines(lines(0, 100, 200, 180));

        Map<String, Object> response = service.waterfall("B-WATERFALL-001", "U001");

        assertEquals("PENDING_DEFINITION", response.get("status"));
        assertFalse(response.containsKey("unitGrossProfit"));
        assertEquals(7, ((List<?>) response.get("factors")).size());
        assertFalse(((List<?>) response.get("blockers")).isEmpty());
    }

    @Test
    void missingPvmFactorsRemainExplicitResidualAndReconcile() {
        V13AnalysisService service = serviceWithLines(lines(100, 90, 200, 180));

        Map<String, Object> response = service.waterfall("B-WATERFALL-002", "U001");

        assertEquals("CALCULATED_TECHNICAL", response.get("status"));
        Map<?, ?> unitGrossProfit = (Map<?, ?>) response.get("unitGrossProfit");
        assertNotNull(unitGrossProfit);
        assertEquals(0, ((BigDecimal) unitGrossProfit.get("check")).compareTo(BigDecimal.ZERO));
        Map<?, ?> factors = (Map<?, ?>) unitGrossProfit.get("factors");
        assertEquals(8, factors.size());
        assertEquals(0, ((BigDecimal) factors.get("OTHER_COST_RESIDUAL_PER_UNIT")).compareTo(decimal("0.2")));
    }

    private static V13AnalysisService serviceWithLines(Map<String, BigDecimal> values) {
        CalculationResult result = new CalculationResult(
                "B-WATERFALL", AnalysisPerspective.BASE,
                Collections.singletonList(new PnlResult("NANCHANG", values)));
        return new V13AnalysisServiceImpl(
                new StubCalculationService(result), new StubPvmService());
    }

    private static Map<String, BigDecimal> lines(double actualVolume, double budgetVolume,
                                                  double actualGrossProfit, double budgetGrossProfit) {
        Map<String, BigDecimal> values = new LinkedHashMap<String, BigDecimal>();
        values.put("VOLUME", decimal(actualVolume));
        values.put("BUDGET_VOLUME", decimal(budgetVolume));
        values.put("GROSS_PROFIT", decimal(actualGrossProfit));
        values.put("BUDGET_GROSS_PROFIT", decimal(budgetGrossProfit));
        return values;
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
