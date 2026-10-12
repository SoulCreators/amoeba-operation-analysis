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

class V13AnalysisServiceImplTest {
    @Test
    void overviewBuildsAbsoluteGapAndTopFromCalculationResult() {
        CalculationResult result = calculation();
        V13AnalysisService service = new V13AnalysisServiceImpl(
                new StubCalculationService(result),
                new StubPvmService());

        Map<String, Object> response = service.overview("B-013", "U001", "ACT_BGT");

        assertEquals("CALCULATED", response.get("status"));
        List<?> metrics = (List<?>) response.get("metrics");
        Map<?, ?> revenue = (Map<?, ?>) metrics.get(1);
        assertEquals(decimal(100), revenue.get("gap"));
        Map<?, ?> top = (Map<?, ?>) response.get("top");
        assertEquals(2, ((List<?>) top.get("positive")).size());
    }

    @Test
    void unresolvedFeaturesExposeBlockersInsteadOfDemoNumbers() {
        V13AnalysisService service = new V13AnalysisServiceImpl(
                new StubCalculationService(calculation()),
                new StubPvmService());

        Map<String, Object> unit = service.unitEfficiency("B-013", "U001");
        Map<String, Object> forecast = service.forecast("B-013", "U001", "ACT_FC", null);

        assertEquals("PENDING_DEFINITION", unit.get("status"));
        assertTrue(((List<?>) unit.get("blockers")).size() >= 1);
        assertEquals("PENDING_FC_VERSION", forecast.get("status"));
    }

    private static CalculationResult calculation() {
        Map<String, BigDecimal> first = lines(110, 1000, 700);
        Map<String, BigDecimal> second = lines(80, 760, 600);
        return new CalculationResult("B-013", AnalysisPerspective.BASE, Arrays.asList(
                new PnlResult("NANCHANG", first), new PnlResult("YICHANG", second)));
    }

    private static Map<String, BigDecimal> lines(double volume, double revenue, double cost) {
        Map<String, BigDecimal> lines = new LinkedHashMap<String, BigDecimal>();
        lines.put("VOLUME", decimal(volume));
        lines.put("BUDGET_VOLUME", decimal(volume - 10));
        lines.put("REVENUE", decimal(revenue));
        lines.put("BUDGET_REVENUE", decimal(revenue - 50));
        lines.put("SALES_COST", decimal(cost));
        lines.put("BUDGET_SALES_COST", decimal(cost - 20));
        lines.put("GROSS_PROFIT", decimal(revenue - cost));
        lines.put("BUDGET_GROSS_PROFIT", decimal((revenue - 50) - (cost - 20)));
        lines.put("NET_PROFIT", decimal(revenue - cost - 50));
        lines.put("BUDGET_NET_PROFIT", decimal(revenue - cost - 60));
        lines.put("PROFIT_RATE", decimal(0.25));
        lines.put("BUDGET_PROFIT_RATE", decimal(0.24));
        lines.put("BASE_EXPENSE", decimal(50));
        lines.put("BUDGET_BASE_EXPENSE", decimal(45));
        return lines;
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
        @Override public List<PvmResult> analyze(String batchNo, String userId) { return Collections.emptyList(); }
    }
}
