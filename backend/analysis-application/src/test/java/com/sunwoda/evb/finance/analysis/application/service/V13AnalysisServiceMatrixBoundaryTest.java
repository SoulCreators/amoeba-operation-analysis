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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V13AnalysisServiceMatrixBoundaryTest {
    @Test
    void missingBaseAndProductDimensionsRemainPendingWithoutFormalMatrixRows() {
        CalculationResult calculation = new CalculationResult(
                "B-MATRIX-001", AnalysisPerspective.BUSINESS_UNIT,
                Collections.singletonList(new PnlResult("SCOPE-A", Collections.emptyMap())));
        V13AnalysisService service = new V13AnalysisServiceImpl(
                new StubCalculationService(calculation), new StubPvmService());

        Map<String, Object> response = service.matrix("B-MATRIX-001", "U001", "GROSS_PROFIT");

        assertEquals("PENDING_DATA_MODEL", response.get("status"));
        List<?> rows = (List<?>) response.get("rows");
        assertEquals(1, rows.size());
        assertNull(((Map<?, ?>) rows.get(0)).get("productCode"));
        assertEquals("PENDING_DATA_MODEL", ((Map<?, ?>) rows.get(0)).get("dimensionStatus"));
        assertTrue(((List<?>) response.get("blockers")).size() >= 1);
    }

    @Test
    void baseProductMatrixAggregatesHistoricalPnlFormulaAndExcludesUnassignedExpenses() {
        List<PnlFact> facts = Arrays.asList(
                dimensionedFact("BASE-A", "PRODUCT-X", "100", "60", "5", "10", "0", "0", "0", "80", "50", "4", "8"),
                dimensionedFact("BASE-A", "PRODUCT-X", "50", "20", "2", "3", "0", "0", "0", "40", "18", "1", "2"),
                // 研发、运营、减值费用没有产品归属，不应进入基地×产品矩阵。
                dimensionedFact("BASE-A", null, "0", "0", "0", "90", "80", "20", "10", "0", "0", "0", "0")
        );
        CalculationResult calculation = new CalculationResult(
                "B-MATRIX-002", AnalysisPerspective.BUSINESS_UNIT,
                Collections.singletonList(new PnlResult("BASE-A", Collections.emptyMap())),
                "B-MATRIX-002-R1", com.sunwoda.evb.finance.analysis.domain.model.CalculationResultStatus.CALCULATED,
                null, null, null, null, null, facts);
        V13AnalysisService service = new V13AnalysisServiceImpl(
                new StubCalculationService(calculation), new StubPvmService());

        Map<String, Object> revenue = service.matrix("B-MATRIX-002", "U001", "REVENUE");
        Map<String, Object> grossProfit = service.matrix("B-MATRIX-002", "U001", "GROSS_PROFIT");
        Map<String, Object> netProfit = service.matrix("B-MATRIX-002", "U001", "NET_PROFIT");

        assertEquals("CALCULATED", revenue.get("status"));
        assertEquals(1, ((List<?>) revenue.get("rows")).size());
        assertMatrixRow(revenue, "BASE-A", "PRODUCT-X", "150", "120", "30");
        assertMatrixRow(grossProfit, "BASE-A", "PRODUCT-X", "70", "52", "18");
        assertMatrixRow(netProfit, "BASE-A", "PRODUCT-X", "50", "37", "13");
    }

    private static void assertMatrixRow(Map<String, Object> response, String baseCode, String productCode,
                                        String actual, String budget, String gap) {
        assertEquals("CALCULATED", response.get("status"));
        List<?> rows = (List<?>) response.get("rows");
        assertEquals(1, rows.size());
        Map<?, ?> row = (Map<?, ?>) rows.get(0);
        assertEquals(baseCode, row.get("baseCode"));
        assertEquals(productCode, row.get("productCode"));
        assertEquals(new BigDecimal(actual), row.get("actual"));
        assertEquals(new BigDecimal(budget), row.get("budget"));
        assertEquals(new BigDecimal(gap), row.get("gap"));
    }

    private static PnlFact dimensionedFact(String baseCode, String productCode,
                                            String revenue, String salesCost, String idleExpense,
                                            String baseExpense, String rdExpense, String assetImpairment,
                                            String creditImpairment, String budgetRevenue,
                                            String budgetSalesCost, String budgetIdleExpense,
                                            String budgetBaseExpense) {
        return new PnlFact("SCOPE-A", baseCode, productCode, null,
                decimal("0"), decimal(revenue), decimal(salesCost), decimal(idleExpense), decimal(baseExpense),
                decimal(rdExpense), decimal(assetImpairment), decimal(creditImpairment), decimal("0"),
                decimal("0"), decimal(budgetRevenue), decimal(budgetSalesCost), decimal(budgetIdleExpense),
                decimal(budgetBaseExpense), decimal("0"), decimal("0"), decimal("0"), decimal("0"),
                decimal("0"), decimal("0"), decimal("0"), decimal("0"), decimal("0"),
                decimal("0"), decimal("0"), decimal("0"), null, null);
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }

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
