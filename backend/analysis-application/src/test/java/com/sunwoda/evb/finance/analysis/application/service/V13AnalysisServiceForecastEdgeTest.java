package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.application.port.FcSnapshotProvider;
import com.sunwoda.evb.finance.analysis.application.port.FcVersionProvider;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResult;
import com.sunwoda.evb.finance.analysis.domain.model.FcSnapshot;
import com.sunwoda.evb.finance.analysis.domain.model.FcVersion;
import com.sunwoda.evb.finance.analysis.domain.model.PnlResult;
import com.sunwoda.evb.finance.analysis.domain.model.PvmResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V13AnalysisServiceForecastEdgeTest {
    @Test
    void missingFcVersionDoesNotFallbackToBudgetOrHistoricalForecast() {
        V13AnalysisService service = serviceWith(null, null);

        Map<String, Object> response = service.forecast("B-FC-001", "U001", "ACT_FC", null);

        assertEquals("PENDING_FC_VERSION", response.get("status"));
        assertTrue(((List<?>) response.get("blockers")).size() >= 1);
        assertEquals(null, response.get("fcVersion"));
        assertTrue(((List<?>) response.get("rows")).stream()
                .allMatch(row -> ((Map<?, ?>) row).get("fc") == null));
    }

    @Test
    void incompleteFcSnapshotRemainsBlockedWithoutPartialForecastMetrics() {
        FcVersion version = version("FC-2026-10");
        FcVersionProvider versions = (batchNo, perspective, versionCode) -> Optional.of(version);
        FcSnapshotProvider snapshots = (batchNo, perspective, versionCode, userId) ->
                Collections.singletonList(snapshot("REVENUE", 120));

        Map<String, Object> response = serviceWith(versions, snapshots)
                .forecast("B-FC-001", "U001", "ACT_FC", version.getVersionCode());

        assertEquals("PENDING_SOURCE_DATA", response.get("status"));
        assertEquals(version.getVersionCode(), response.get("fcVersion"));
        assertTrue(((List<?>) response.get("metrics")).isEmpty());
        assertTrue(((Map<?, ?>) response.get("top")).isEmpty());
        assertTrue(String.valueOf(response.get("notes")).contains("不会用BGT"));
    }

    @Test
    void completeFcSnapshotCalculatesOnlyFromTheSelectedVersion() {
        FcVersion version = version("FC-2026-10");
        FcVersionProvider versions = (batchNo, perspective, versionCode) -> Optional.of(version);
        FcSnapshotProvider snapshots = (batchNo, perspective, versionCode, userId) -> Arrays.asList(
                snapshot("VOLUME", 10), snapshot("REVENUE", 120), snapshot("GROSS_PROFIT", 30),
                snapshot("BASE_EXPENSE", 12), snapshot("NET_PROFIT", 18), snapshot("PROFIT_RATE", 0.15));

        Map<String, Object> response = serviceWith(versions, snapshots)
                .forecast("B-FC-001", "U001", "ACT_FC", version.getVersionCode());

        assertEquals("CALCULATED", response.get("status"));
        assertEquals(version.getVersionCode(), response.get("fcVersion"));
        assertEquals(6, ((List<?>) response.get("metrics")).size());
        assertFalse(((List<?>) response.get("blockers")).size() > 0);
    }

    private static V13AnalysisService serviceWith(FcVersionProvider versions, FcSnapshotProvider snapshots) {
        CalculationResult calculation = new CalculationResult(
                "B-FC-001", AnalysisPerspective.BASE,
                Collections.singletonList(new PnlResult("SCOPE-A", Collections.emptyMap())));
        return new V13AnalysisServiceImpl(
                new StubCalculationService(calculation), new StubPvmService(),
                null, versions, snapshots);
    }

    private static FcVersion version(String code) {
        return new FcVersion(code, AnalysisPerspective.BASE, "2026-10",
                "2026-10", "2026-12", "PUBLISHED", "finance",
                LocalDateTime.now(), true, "finance", LocalDateTime.now());
    }

    private static FcSnapshot snapshot(String metric, double value) {
        return new FcSnapshot("2026-10", "SCOPE-A", metric, BigDecimal.valueOf(value));
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
