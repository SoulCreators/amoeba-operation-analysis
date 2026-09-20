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
