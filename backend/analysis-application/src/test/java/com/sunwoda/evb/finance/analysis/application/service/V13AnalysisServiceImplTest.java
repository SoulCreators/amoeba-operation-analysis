package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResult;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResultStatus;
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
import java.time.LocalDateTime;

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

    @Test
    void unitEfficiencyCalculatesTechnicalRowsWhenWhAndLineAreAvailable() {
        CalculationResult result = new CalculationResult("B-013", AnalysisPerspective.BASE,
                Collections.singletonList(new PnlResult("NANCHANG", lines(110, 1000, 700))),
                "B-013-R1", CalculationResultStatus.CALCULATED, null, null, LocalDateTime.now(), null, null,
                Collections.singletonList(factWithWh("NANCHANG", "L01", 100, 80, 1000, 900)));
        V13AnalysisService service = new V13AnalysisServiceImpl(
                new StubCalculationService(result), new StubPvmService());

        Map<String, Object> response = service.unitEfficiency("B-013", "U001");

        assertEquals("CALCULATED_TECHNICAL", response.get("status"));
        Map<?, ?> row = (Map<?, ?>) ((List<?>) response.get("rows")).get(0);
        assertEquals(0, ((BigDecimal) row.get("actualPerWh")).compareTo(decimal(10)));
        assertEquals(0, ((BigDecimal) row.get("budgetPerWh")).compareTo(decimal(11.25)));
        assertEquals(0, ((BigDecimal) row.get("gap")).compareTo(decimal(-1.25)));
    }

    @Test
    void waterfallNormalizesPvmFactorsAndProvidesReconciliationCheck() {
        Map<String, BigDecimal> values = new LinkedHashMap<String, BigDecimal>();
        values.put("PROFIT_VOLUME_IMPACT", decimal(20));
        values.put("PROFIT_PRICE_IMPACT", decimal(40));
        values.put("PROFIT_COST_IMPACT", decimal(0));
        values.put("PROFIT_MATERIAL_IMPACT", decimal(0));
        values.put("PROFIT_LABOR_IMPACT", decimal(0));
        values.put("PROFIT_OUTSOURCING_IMPACT", decimal(0));
        values.put("PROFIT_VARIABLE_MFG_IMPACT", decimal(0));
        values.put("PROFIT_FIXED_MFG_IMPACT", decimal(0));
        PvmResult pvm = new PvmResult("NANCHANG", "BASE_VOLUME_PRICE_COST", values);
        V13AnalysisService service = new V13AnalysisServiceImpl(
                new StubCalculationService(calculation()), new StubPvmService(Collections.singletonList(pvm)));

        Map<String, Object> response = service.waterfall("B-013", "U001");

        assertEquals("CALCULATED_TECHNICAL", response.get("status"));
        Map<?, ?> unitGrossProfit = (Map<?, ?>) response.get("unitGrossProfit");
        assertEquals("VOLUME", unitGrossProfit.get("unitBasis"));
        assertEquals(0, ((BigDecimal) unitGrossProfit.get("check")).compareTo(BigDecimal.ZERO));
        Map<?, ?> unitFactors = (Map<?, ?>) unitGrossProfit.get("factors");
        assertEquals(8, unitFactors.size());
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

    private static PnlFact factWithWh(String scope, String line, double actualWh, double budgetWh,
                                      double actualFixedMfg, double budgetFixedMfg) {
        BigDecimal zero = decimal(0);
        return new PnlFact(scope, "BASE-1", "PRODUCT-1", line,
                decimal(100), decimal(1000), decimal(700), zero, zero, zero, zero, zero, zero,
                decimal(90), decimal(950), decimal(680), zero, zero, zero, zero, zero, zero,
                zero, zero, zero, zero, decimal(actualFixedMfg), zero, zero, zero, zero,
                decimal(budgetFixedMfg), decimal(actualWh), decimal(budgetWh));
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
        private final List<PvmResult> results;
        StubPvmService() { this(Collections.<PvmResult>emptyList()); }
        StubPvmService(List<PvmResult> results) { this.results = results; }
        @Override public List<PvmResult> analyze(String batchNo, String userId) { return results; }
    }
}
