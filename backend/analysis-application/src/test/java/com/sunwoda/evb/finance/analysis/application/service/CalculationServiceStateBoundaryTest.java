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
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalculationServiceStateBoundaryTest {
    @Test
    void publishedOrConfirmedBatchCannotBeRecalculatedDirectly() {
        for (BatchStatus frozenStatus : new BatchStatus[]{BatchStatus.CONFIRMED, BatchStatus.PUBLISHED}) {
            FakeBatchRepository repository = new FakeBatchRepository();
            repository.save(batch("CALC-STATE-001-" + frozenStatus, frozenStatus));
            CalculationServiceImpl service = new CalculationServiceImpl(repository,
                    unusedProvider(), new EmptyUserScopeRepository());

            IllegalStateException error = assertThrows(IllegalStateException.class,
                    () -> service.calculate("CALC-STATE-001-" + frozenStatus, "finance-001"));

            assertEquals("已确认或已发布批次不可直接重算", error.getMessage());
            assertEquals(frozenStatus, repository.findByBatchNo("CALC-STATE-001-" + frozenStatus).get().getStatus());
        }
    }

    @Test
    void calculationFailureLeavesBatchInFailedState() {
        FakeBatchRepository repository = new FakeBatchRepository();
        repository.save(batch("CALC-STATE-001-FAILED", BatchStatus.READY_FOR_CALCULATION));
        CalculationServiceImpl service = new CalculationServiceImpl(repository,
                batch -> { throw new IllegalStateException("事实读取失败"); }, new EmptyUserScopeRepository());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.calculate("CALC-STATE-001-FAILED", "finance-001"));

        assertEquals("事实读取失败", error.getMessage());
        assertEquals(BatchStatus.FAILED,
                repository.findByBatchNo("CALC-STATE-001-FAILED").get().getStatus());
        assertThrows(IllegalArgumentException.class,
                () -> service.get("CALC-STATE-001-FAILED"));
    }

    @Test
    void confirmingAnAlreadyConfirmedResultIsRejectedWithoutChangingState() {
        CalculationServiceImpl service = serviceWithReadyBatch("CALC-STATE-001-CONFIRM");

        service.calculate("CALC-STATE-001-CONFIRM", "finance-001");
        CalculationResult first = service.confirm("CALC-STATE-001-CONFIRM", "finance-001");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.confirm("CALC-STATE-001-CONFIRM", "finance-002"));

        assertEquals("只有已计算结果可以确认: CONFIRMED", error.getMessage());
        assertEquals(CalculationResultStatus.CONFIRMED, service.get("CALC-STATE-001-CONFIRM").getStatus());
        assertEquals("finance-001", service.get("CALC-STATE-001-CONFIRM").getConfirmedBy());
        assertTrue(first.getConfirmedAt() != null);
    }

    @Test
    void publishingAnAlreadyPublishedResultIsRejectedWithoutChangingState() {
        CalculationServiceImpl service = serviceWithReadyBatch("CALC-STATE-001-PUBLISH");

        service.calculate("CALC-STATE-001-PUBLISH", "finance-001");
        service.confirm("CALC-STATE-001-PUBLISH", "finance-001");
        CalculationResult first = service.publish("CALC-STATE-001-PUBLISH", "finance-002");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.publish("CALC-STATE-001-PUBLISH", "finance-003"));

        assertEquals("只有已确认结果可以发布: PUBLISHED", error.getMessage());
        assertEquals(CalculationResultStatus.PUBLISHED, service.get("CALC-STATE-001-PUBLISH").getStatus());
        assertEquals("finance-002", service.get("CALC-STATE-001-PUBLISH").getPublishedBy());
        assertTrue(first.getPublishedAt() != null);
    }

    private CalculationServiceImpl serviceWithReadyBatch(String batchNo) {
        FakeBatchRepository repository = new FakeBatchRepository();
        repository.save(batch(batchNo, BatchStatus.READY_FOR_CALCULATION));
        PnlFactProvider provider = batch -> Arrays.asList(new PnlFact(batchNo, decimal(1), decimal(10), decimal(7),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO));
        return new CalculationServiceImpl(repository, provider, new EmptyUserScopeRepository());
    }

    private static PnlFactProvider unusedProvider() {
        return batch -> { throw new AssertionError("冻结批次不应调用事实提供方"); };
    }

    private static AnalysisBatch batch(String batchNo, BatchStatus status) {
        return new AnalysisBatch(1L, batchNo, "2026-09", AnalysisPerspective.BASE,
                status, "tester", LocalDateTime.now());
    }

    private static BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value);
    }

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

    private static class EmptyUserScopeRepository implements UserScopeRepository {
        @Override
        public com.sunwoda.evb.finance.analysis.domain.model.UserScope save(
                com.sunwoda.evb.finance.analysis.domain.model.UserScope scope) {
            return scope;
        }

        @Override
        public java.util.List<com.sunwoda.evb.finance.analysis.domain.model.UserScope> findByUserAndPerspective(
                String userId, AnalysisPerspective perspective) {
            // 本次修改：状态边界测试的 finance-* 操作者显式具备总部财经权限。
            if (userId != null && userId.startsWith("finance-")) {
                return java.util.Collections.singletonList(
                        new com.sunwoda.evb.finance.analysis.domain.model.UserScope(
                                userId, "HQ_FINANCE", perspective, "ALL",
                                true, true, true, true));
            }
            return java.util.Collections.emptyList();
        }
    }
}
