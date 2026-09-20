package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResult;
import com.sunwoda.evb.finance.analysis.domain.model.MetricDefinition;
import com.sunwoda.evb.finance.analysis.domain.model.PnlResult;
import com.sunwoda.evb.finance.analysis.domain.repository.MetricDefinitionRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MetricTreeServiceImplTest {
    @Test
    void evaluatesConfiguredActualBudgetAndGap() {
        Map<String, BigDecimal> lines = new LinkedHashMap<String, BigDecimal>();
        lines.put("REVENUE", decimal("120"));
        lines.put("SALES_COST", decimal("70"));
        lines.put("NET_PROFIT", decimal("24"));
        lines.put("BUDGET_REVENUE", decimal("100"));
        lines.put("BUDGET_SALES_COST", decimal("60"));
        lines.put("BUDGET_NET_PROFIT", decimal("20"));
        CalculationResult calculation = new CalculationResult("B-009", AnalysisPerspective.BASE,
                Arrays.asList(new PnlResult("南昌", lines)));

        MetricDefinitionRepository repository = new StubMetricRepository();
        List<com.sunwoda.evb.finance.analysis.domain.model.MetricResult> results =
                new MetricTreeServiceImpl(new StubCalculationService(calculation), repository)
                        .calculate("B-009", "U001");

        assertEquals(2, results.size());
        assertEquals(0, results.get(0).getActualValue().compareTo(decimal("120")));
        assertEquals(0, results.get(0).getBudgetValue().compareTo(decimal("100")));
        assertEquals(0, results.get(0).getGapValue().compareTo(decimal("20")));
        assertEquals(0, results.get(1).getActualValue().compareTo(decimal("0.2")));
        assertEquals(0, results.get(1).getBudgetValue().compareTo(decimal("0.2")));
    }

    private static BigDecimal decimal(String value) { return new BigDecimal(value); }

    private static class StubMetricRepository implements MetricDefinitionRepository {
        @Override public MetricDefinition save(MetricDefinition definition) { return definition; }
        @Override public Optional<MetricDefinition> findByCode(String metricCode) { return Optional.empty(); }
        @Override public List<MetricDefinition> list(String perspective, boolean includeDisabled) {
            return Arrays.asList(
                    new MetricDefinition(1L, "REVENUE", "营业收入", "ALL", "CNY",
                            "SUM(REVENUE)", 1, true, "收入", "test", LocalDateTime.now()),
                    new MetricDefinition(2L, "PROFIT_RATE", "净利率", "ALL", "%",
                            "NET_PROFIT/REVENUE", 2, true, "利润率", "test", LocalDateTime.now()));
        }
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
}
