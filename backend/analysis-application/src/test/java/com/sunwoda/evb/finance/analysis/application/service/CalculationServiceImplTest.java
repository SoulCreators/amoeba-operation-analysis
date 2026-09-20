package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.application.port.PnlFactProvider;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.BatchStatus;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResult;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResultStatus;
import com.sunwoda.evb.finance.analysis.domain.model.PnlFact;
import com.sunwoda.evb.finance.analysis.domain.repository.AnalysisBatchRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.UserScopeRepository;
import com.sunwoda.evb.finance.analysis.domain.model.UserScope;
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

        CalculationResult result = new CalculationServiceImpl(repository, provider, new FakeUserScopeRepository()).calculate("B-003");

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

        CalculationResult result = new CalculationServiceImpl(repository, provider, new FakeUserScopeRepository()).calculate("B-004");

        Map<String, BigDecimal> lines = result.getResults().get(0).getLines();
        assertEquals(decimal(1000), lines.get("BUDGET_REVENUE"));
        assertEquals(decimal(300), lines.get("BUDGET_GROSS_PROFIT"));
        assertEquals(decimal(100), lines.get("GAP_REVENUE"));
        assertEquals(decimal(40), lines.get("GAP_GROSS_PROFIT"));
    }

    @Test
    void confirmsThenPublishesResultAndFreezesBatch() {
        FakeBatchRepository repository = new FakeBatchRepository();
        repository.save(new AnalysisBatch(1L, "B-005", "2026-09", AnalysisPerspective.BASE,
                BatchStatus.READY_FOR_CALCULATION, "tester", LocalDateTime.now()));
        PnlFactProvider provider = batch -> Arrays.asList(
                new PnlFact("南昌", decimal(1), decimal(10), decimal(7),
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO));
        CalculationServiceImpl service = new CalculationServiceImpl(repository, provider, new FakeUserScopeRepository());

        service.calculate("B-005");
        CalculationResult confirmed = service.confirm("B-005", "finance-001");
        assertEquals(CalculationResultStatus.CONFIRMED, confirmed.getStatus());
        assertEquals("finance-001", confirmed.getConfirmedBy());
        assertEquals(BatchStatus.CONFIRMED, repository.findByBatchNo("B-005").get().getStatus());

        CalculationResult published = service.publish("B-005", "finance-002");
        assertEquals(CalculationResultStatus.PUBLISHED, published.getStatus());
        assertEquals("finance-002", published.getPublishedBy());
        assertEquals(BatchStatus.PUBLISHED, repository.findByBatchNo("B-005").get().getStatus());
    }

    @Test
    void filtersResultByUserScope() {
        FakeBatchRepository repository = new FakeBatchRepository();
        repository.save(new AnalysisBatch(1L, "B-006", "2026-09", AnalysisPerspective.BASE,
                BatchStatus.READY_FOR_CALCULATION, "tester", LocalDateTime.now()));
        PnlFactProvider provider = batch -> Arrays.asList(
                new PnlFact("南昌", decimal(1), decimal(10), decimal(7),
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO),
                new PnlFact("宜昌", decimal(2), decimal(20), decimal(12),
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO));
        FakeUserScopeRepository scopes = new FakeUserScopeRepository();
        scopes.save(new UserScope("U001", "BASE_FINANCE", AnalysisPerspective.BASE,
                "南昌", true, true, true, true));
        CalculationServiceImpl service = new CalculationServiceImpl(repository, provider, scopes);
        service.calculate("B-006");

        CalculationResult filtered = service.getForUser("B-006", "U001");

        assertEquals(1, filtered.getResults().size());
        assertEquals("南昌", filtered.getResults().get(0).getScopeCode());
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

    private static class FakeUserScopeRepository implements UserScopeRepository {
        private final java.util.List<UserScope> data = new java.util.ArrayList<UserScope>();
        @Override public UserScope save(UserScope scope) { data.add(scope); return scope; }
        @Override public java.util.List<UserScope> findByUserAndPerspective(String userId,
                                                                              AnalysisPerspective perspective) {
            java.util.List<UserScope> result = new java.util.ArrayList<UserScope>();
            for (UserScope scope : data) {
                if (scope.isEnabled() && scope.getUserId().equals(userId)
                        && scope.getPerspective() == perspective) result.add(scope);
            }
            return result;
        }
    }
}
