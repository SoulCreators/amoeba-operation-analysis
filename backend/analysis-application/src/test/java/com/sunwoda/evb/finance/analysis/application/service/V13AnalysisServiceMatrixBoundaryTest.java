package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResult;
import com.sunwoda.evb.finance.analysis.domain.model.PnlResult;
import com.sunwoda.evb.finance.analysis.domain.model.PvmResult;
import org.junit.jupiter.api.Test;

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
