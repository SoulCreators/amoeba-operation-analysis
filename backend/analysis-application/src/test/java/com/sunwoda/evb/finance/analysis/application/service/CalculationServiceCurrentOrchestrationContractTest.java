package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.application.port.PnlFactProvider;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.BatchStatus;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResult;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResultStatus;
import com.sunwoda.evb.finance.analysis.domain.model.UserScope;
import com.sunwoda.evb.finance.analysis.domain.repository.AnalysisBatchRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.CalculationResultRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.UserScopeRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DATA-004-TECH-003：锁定当前计算服务的批次—结果写入编排现状。
 *
 * <p>本次新增只验证现有调用顺序和异常传播，不实现应用层共享事务，
 * 不决定重算失败旧结果可见性、失败重试、历史版本或发布权限。</p>
 */
class CalculationServiceCurrentOrchestrationContractTest {
    @Test
    void calculateSavesCalculatingBeforeResultAndCalculatedAfterResult() {
        AnalysisBatch batch = batch("CALC-ORCH-SUCCESS", BatchStatus.READY_FOR_CALCULATION);
        AnalysisBatchRepository batches = mock(AnalysisBatchRepository.class);
        CalculationResultRepository results = mock(CalculationResultRepository.class);
        when(batches.findByBatchNo(batch.getBatchNo())).thenReturn(Optional.of(batch));
        when(results.save(any(CalculationResult.class))).thenAnswer(invocation -> invocation.getArgument(0));
        List<BatchStatus> savedStatuses = new ArrayList<BatchStatus>();
        doAnswer(invocation -> {
            // 本次修改：记录当前批次状态，锁定现有“计算中→结果保存→已计算”编排，不改变生产行为。
            savedStatuses.add(batch.getStatus());
            return batch;
        }).when(batches).save(batch);

        CalculationServiceImpl service = service(batches, results, facts -> Collections.emptyList());
        service.calculate(batch.getBatchNo());

        assertEquals(java.util.Arrays.asList(BatchStatus.CALCULATING, BatchStatus.CALCULATED), savedStatuses);
        InOrder order = inOrder(batches, results);
        order.verify(batches).save(batch);
        order.verify(results).save(any(CalculationResult.class));
        order.verify(batches).save(batch);
    }

    @Test
    void calculateSavesFailedAfterFactLoadingFailureWithoutSavingResult() {
        AnalysisBatch batch = batch("CALC-ORCH-FAILED", BatchStatus.READY_FOR_CALCULATION);
        AnalysisBatchRepository batches = mock(AnalysisBatchRepository.class);
        CalculationResultRepository results = mock(CalculationResultRepository.class);
        when(batches.findByBatchNo(batch.getBatchNo())).thenReturn(Optional.of(batch));
        List<BatchStatus> savedStatuses = new ArrayList<BatchStatus>();
        doAnswer(invocation -> {
            // 本次修改：记录失败路径的状态落点，避免把当前 FAILED 行为误报为事务设计结论。
            savedStatuses.add(batch.getStatus());
            return batch;
        }).when(batches).save(batch);

        CalculationServiceImpl service = service(batches, results,
                facts -> { throw new IllegalStateException("事实读取失败"); });

        assertThrows(IllegalStateException.class, () -> service.calculate(batch.getBatchNo()));

        assertEquals(java.util.Arrays.asList(BatchStatus.CALCULATING, BatchStatus.FAILED), savedStatuses);
        verify(results, never()).save(any(CalculationResult.class));
    }

    @Test
    void calculateSavesFailedAfterResultRepositoryFailureAndRethrowsOriginalError() {
        AnalysisBatch batch = batch("CALC-ORCH-RESULT-FAILED", BatchStatus.READY_FOR_CALCULATION);
        AnalysisBatchRepository batches = mock(AnalysisBatchRepository.class);
        CalculationResultRepository results = mock(CalculationResultRepository.class);
        when(batches.findByBatchNo(batch.getBatchNo())).thenReturn(Optional.of(batch));
        IllegalStateException original = new IllegalStateException("结果保存失败");
        org.mockito.Mockito.doThrow(original).when(results).save(any(CalculationResult.class));
        List<BatchStatus> savedStatuses = new ArrayList<BatchStatus>();
        doAnswer(invocation -> {
            // 本次修改：补齐结果仓储失败后的现状证据，锁定 FAILED 写入和原始异常继续抛出。
            savedStatuses.add(batch.getStatus());
            return batch;
        }).when(batches).save(batch);

        CalculationServiceImpl service = service(batches, results, facts -> Collections.emptyList());

        IllegalStateException actual = assertThrows(IllegalStateException.class,
                () -> service.calculate(batch.getBatchNo()));

        assertEquals(original, actual);
        assertEquals(java.util.Arrays.asList(BatchStatus.CALCULATING, BatchStatus.FAILED), savedStatuses);
        verify(results).save(any(CalculationResult.class));
    }

    @Test
    void confirmSavesBatchBeforeConfirmedResult() {
        AnalysisBatch batch = batch("CALC-ORCH-CONFIRM", BatchStatus.CALCULATED);
        CalculationResult current = result(batch.getBatchNo(), CalculationResultStatus.CALCULATED);
        AnalysisBatchRepository batches = mock(AnalysisBatchRepository.class);
        CalculationResultRepository results = mock(CalculationResultRepository.class);
        when(batches.findByBatchNo(batch.getBatchNo())).thenReturn(Optional.of(batch));
        when(results.findByBatchNo(batch.getBatchNo())).thenReturn(Optional.of(current));
        when(results.save(any(CalculationResult.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CalculationServiceImpl service = service(batches, results, facts -> Collections.emptyList());
        CalculationResult confirmed = service.confirm(batch.getBatchNo(), "finance-001");

        assertEquals(CalculationResultStatus.CONFIRMED, confirmed.getStatus());
        assertEquals(BatchStatus.CONFIRMED, batch.getStatus());
        InOrder order = inOrder(batches, results);
        order.verify(batches).save(batch);
        order.verify(results).save(any(CalculationResult.class));
    }

    @Test
    void confirmKeepsBatchConfirmedWhenResultRepositoryFailsAndRethrowsOriginalError() {
        AnalysisBatch batch = batch("CALC-ORCH-CONFIRM-RESULT-FAILED", BatchStatus.CALCULATED);
        CalculationResult current = result(batch.getBatchNo(), CalculationResultStatus.CALCULATED);
        AnalysisBatchRepository batches = mock(AnalysisBatchRepository.class);
        CalculationResultRepository results = mock(CalculationResultRepository.class);
        when(batches.findByBatchNo(batch.getBatchNo())).thenReturn(Optional.of(batch));
        when(results.findByBatchNo(batch.getBatchNo())).thenReturn(Optional.of(current));
        IllegalStateException original = new IllegalStateException("确认结果保存失败");
        org.mockito.Mockito.doThrow(original).when(results).save(any(CalculationResult.class));
        List<BatchStatus> savedStatuses = new ArrayList<BatchStatus>();
        doAnswer(invocation -> {
            // 本次修改：锁定 confirm 结果保存失败时的当前状态落点和原始异常传播。
            savedStatuses.add(batch.getStatus());
            return batch;
        }).when(batches).save(batch);

        CalculationServiceImpl service = service(batches, results, facts -> Collections.emptyList());

        IllegalStateException actual = assertThrows(IllegalStateException.class,
                () -> service.confirm(batch.getBatchNo(), "finance-confirm"));

        assertEquals(original, actual);
        assertEquals(Collections.singletonList(BatchStatus.CONFIRMED), savedStatuses);
        verify(results).save(any(CalculationResult.class));
    }

    @Test
    void publishSavesBatchBeforePublishedResult() {
        AnalysisBatch batch = batch("CALC-ORCH-PUBLISH", BatchStatus.CONFIRMED);
        CalculationResult current = result(batch.getBatchNo(), CalculationResultStatus.CONFIRMED);
        AnalysisBatchRepository batches = mock(AnalysisBatchRepository.class);
        CalculationResultRepository results = mock(CalculationResultRepository.class);
        when(batches.findByBatchNo(batch.getBatchNo())).thenReturn(Optional.of(batch));
        when(results.findByBatchNo(batch.getBatchNo())).thenReturn(Optional.of(current));
        when(results.save(any(CalculationResult.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CalculationServiceImpl service = service(batches, results, facts -> Collections.emptyList());
        CalculationResult published = service.publish(batch.getBatchNo(), "finance-002");

        assertEquals(CalculationResultStatus.PUBLISHED, published.getStatus());
        assertEquals(BatchStatus.PUBLISHED, batch.getStatus());
        InOrder order = inOrder(batches, results);
        order.verify(batches).save(batch);
        order.verify(results).save(any(CalculationResult.class));
    }

    @Test
    void publishKeepsBatchPublishedWhenResultRepositoryFailsAndRethrowsOriginalError() {
        AnalysisBatch batch = batch("CALC-ORCH-PUBLISH-RESULT-FAILED", BatchStatus.CONFIRMED);
        CalculationResult current = result(batch.getBatchNo(), CalculationResultStatus.CONFIRMED);
        AnalysisBatchRepository batches = mock(AnalysisBatchRepository.class);
        CalculationResultRepository results = mock(CalculationResultRepository.class);
        when(batches.findByBatchNo(batch.getBatchNo())).thenReturn(Optional.of(batch));
        when(results.findByBatchNo(batch.getBatchNo())).thenReturn(Optional.of(current));
        IllegalStateException original = new IllegalStateException("发布结果保存失败");
        org.mockito.Mockito.doThrow(original).when(results).save(any(CalculationResult.class));
        List<BatchStatus> savedStatuses = new ArrayList<BatchStatus>();
        doAnswer(invocation -> {
            // 本次修改：锁定 publish 结果保存失败时的当前状态落点和原始异常传播。
            savedStatuses.add(batch.getStatus());
            return batch;
        }).when(batches).save(batch);

        CalculationServiceImpl service = service(batches, results, facts -> Collections.emptyList());

        IllegalStateException actual = assertThrows(IllegalStateException.class,
                () -> service.publish(batch.getBatchNo(), "finance-publish"));

        assertEquals(original, actual);
        assertEquals(Collections.singletonList(BatchStatus.PUBLISHED), savedStatuses);
        verify(results).save(any(CalculationResult.class));
    }

    private CalculationServiceImpl service(AnalysisBatchRepository batches,
                                            CalculationResultRepository results,
                                            PnlFactProvider facts) {
        UserScopeRepository scopes = mock(UserScopeRepository.class);
        when(scopes.findByUserAndPerspective(any(String.class), any(AnalysisPerspective.class)))
                .thenAnswer(invocation -> {
                    // 本次修改：为生命周期编排测试提供显式总部财经授权，避免授权检查遮蔽原有时序断言。
                    String userId = invocation.getArgument(0);
                    AnalysisPerspective perspective = invocation.getArgument(1);
                    return Collections.singletonList(new UserScope(userId, "HQ_FINANCE", perspective,
                            "ALL", true, true, true, true));
                });
        return new CalculationServiceImpl(batches, facts, scopes, results);
    }

    private AnalysisBatch batch(String batchNo, BatchStatus status) {
        return new AnalysisBatch(1L, batchNo, "2026-10", AnalysisPerspective.BASE,
                status, "DATA-004-TECH-003", LocalDateTime.now());
    }

    private CalculationResult result(String batchNo, CalculationResultStatus status) {
        return new CalculationResult(batchNo, AnalysisPerspective.BASE,
                Collections.emptyList(), batchNo + "-R1", status, null, null,
                LocalDateTime.now(), null, null, Collections.emptyList());
    }
}
