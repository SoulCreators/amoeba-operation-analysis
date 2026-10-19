package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResult;
import com.sunwoda.evb.finance.analysis.domain.model.PnlResult;
import com.sunwoda.evb.finance.analysis.domain.model.PvmResult;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V13AnalysisServiceTopicBoundaryTest {
    @Test
    void unsupportedTopicsRemainBlockedWithoutDemoRows() {
        V13AnalysisService service = service();
        for (String topicCode : Arrays.asList("AGING", "OPERATING_EXPENSE", "RND_TICKET", "ACCOUNT_MATRIX")) {
            Map<String, Object> response = service.topic("B-TOPIC-001", "U001", topicCode);

            assertEquals("PENDING_SOURCE_DATA", response.get("status"));
            assertEquals(topicCode, response.get("topicCode"));
            assertTrue(((List<?>) response.get("rows")).isEmpty());
            assertTrue(((List<?>) response.get("blockers")).size() >= 1);
        }

        Map<String, Object> unknown = service.topic("B-TOPIC-001", "U001", null);
        assertEquals("UNKNOWN", unknown.get("topicCode"));
        assertEquals("PENDING_SOURCE_DATA", unknown.get("status"));
        assertTrue(((List<?>) unknown.get("rows")).isEmpty());
    }

    private static V13AnalysisService service() {
        CalculationResult calculation = new CalculationResult(
                "B-TOPIC-001", AnalysisPerspective.BASE,
                Collections.singletonList(new PnlResult("SCOPE-A", Collections.emptyMap())));
        return new V13AnalysisServiceImpl(new StubCalculationService(calculation), new StubPvmService());
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
