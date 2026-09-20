package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.application.port.PnlFactProvider;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.BatchStatus;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResult;
import com.sunwoda.evb.finance.analysis.domain.model.PnlFact;
import com.sunwoda.evb.finance.analysis.domain.repository.AnalysisBatchRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CalculationServiceImplTest {
    @Test
    void calculatesProfitLinesByPerspectiveAndScope() {
        FakeBatchRepository repository = new FakeBatchRepository();
        repository.save(new AnalysisBatch(1L, "B-003", "2026-09", AnalysisPerspective.BASE,
                BatchStatus.READY_FOR_CALCULATION, "tester", LocalDateTime.now()));
        PnlFactProvider provider = batch -> Arrays.asList(
                new PnlFact("NANCHANG", decimal(100), decimal(1000), decimal(700),
                        decimal(20), decimal(50), decimal(10), decimal(5), decimal(0), decimal(2)));

        CalculationResult result = new CalculationServiceImpl(repository, provider).calculate("B-003");

        assertEquals(1, result.getResults().size());
        assertEquals(decimal(300), result.getResults().get(0).getLines().get("GROSS_PROFIT"));
        assertEquals(decimal(217), result.getResults().get(0).getLines().get("NET_PROFIT"));
        assertEquals(BatchStatus.CALCULATED, repository.findByBatchNo("B-003").get().getStatus());
    }

    @Test
    void calculatesBudgetAndGapLinesTogether() {
        FakeBatchRepository repository = new FakeBatchRepository();
        repository.save(new AnalysisBatch(1L, "B-004", "2026-09", AnalysisPerspective.PRODUCT_LINE,
                BatchStatus.READY_FOR_CALCULATION, "tester", LocalDateTime.now()));
        PnlFactProvider provider = batch -> Arrays.asList(new PnlFact(
                "产品线A", decimal(110), decimal(1100), decimal(760),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                decimal(100), decimal(1000), decimal(700),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO));

        CalculationResult result = new CalculationServiceImpl(repository, provider).calculate("B-004");

        Map<String, BigDecimal> lines = result.getResults().get(0).getLines();
        assertEquals(decimal(1000), lines.get("BUDGET_REVENUE"));
        assertEquals(decimal(300), lines.get("BUDGET_GROSS_PROFIT"));
        assertEquals(decimal(100), lines.get("GAP_REVENUE"));
        assertEquals(decimal(40), lines.get("GAP_GROSS_PROFIT"));
    }

    private static BigDecimal decimal(double value) { return BigDecimal.valueOf(value); }

    private static class FakeBatchRepository implements AnalysisBatchRepository {
        private final Map<String, AnalysisBatch> data = new HashMap<String, AnalysisBatch>();

        @Override
        public AnalysisBatch save(AnalysisBatch batch) {
            data.put(batch.getBatchNo(), batch);
            return batch;
        }

        @Override
        public Optional<AnalysisBatch> findByBatchNo(String batchNo) {
            return Optional.ofNullable(data.get(batchNo));
        }
    }
}
