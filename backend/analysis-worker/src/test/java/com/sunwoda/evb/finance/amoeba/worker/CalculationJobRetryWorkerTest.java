package com.sunwoda.evb.finance.amoeba.worker;

import com.sunwoda.evb.finance.analysis.application.port.CalculationRetryExecutor;
import com.sunwoda.evb.finance.analysis.application.port.CalculationRetryAlertPort;
import com.sunwoda.evb.finance.analysis.application.service.CalculationRetryApplicationService;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationJob;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationRetryClaim;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationRetryState;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationRetryDecision;
import com.sunwoda.evb.finance.analysis.application.model.CalculationRetryExecutionResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V028 Worker 独立测试。
 *
 * <p>本次新增测试：验证执行异常被送回应用层失败处理入口，并只在应用层返回 MANUAL_REQUIRED 时告警。</p>
 */
class CalculationJobRetryWorkerTest {
    @Test
    void shouldDelegateExecutionFailureToApplicationService() {
        CalculationRetryApplicationService service = mock(CalculationRetryApplicationService.class);
        CalculationRetryExecutor executor = mock(CalculationRetryExecutor.class);
        CalculationRetryAlertPort alert = mock(CalculationRetryAlertPort.class);
        CalculationJob job = new CalculationJob("JOB-W-1", "REQ-W-1", "BATCH-W-1",
                LocalDateTime.of(2026, 10, 21, 10, 0));
        when(service.start("JOB-W-1")).thenReturn(job);
        when(service.recordFailure(eq("JOB-W-1"), contains("模拟计算失败"),
                org.mockito.ArgumentMatchers.any())).thenReturn(
                CalculationRetryDecision.schedule(1, 1, LocalDateTime.now().plusMinutes(1), "模拟计算失败"));
        doThrow(new IllegalStateException("模拟计算失败")).when(executor).execute("BATCH-W-1");

        new CalculationJobRetryWorker(service, executor, alert).process("JOB-W-1", LocalDateTime.now());

        verify(service).recordFailure(eq("JOB-W-1"), contains("模拟计算失败"), org.mockito.ArgumentMatchers.any());
        // 本次新增断言：普通自动重试失败不应触发人工接管告警。
        verify(alert, never()).alert(org.mockito.ArgumentMatchers.any(CalculationJob.class),
                org.mockito.ArgumentMatchers.any(CalculationRetryDecision.class));
    }

    @Test
    void shouldAlertOnlyWhenFailureDecisionRequiresManualHandling() {
        CalculationRetryApplicationService service = mock(CalculationRetryApplicationService.class);
        CalculationRetryExecutor executor = mock(CalculationRetryExecutor.class);
        CalculationRetryAlertPort alert = mock(CalculationRetryAlertPort.class);
        CalculationJob job = new CalculationJob("JOB-W-MANUAL", "REQ-W-MANUAL", "BATCH-W-MANUAL",
                LocalDateTime.of(2026, 10, 21, 10, 0));
        CalculationRetryDecision decision = CalculationRetryDecision.manualRequired(4, "第四次失败");
        when(service.start(job.getJobId())).thenReturn(job);
        when(service.recordFailure(eq(job.getJobId()), contains("第四次失败"),
                org.mockito.ArgumentMatchers.any())).thenReturn(decision);
        doThrow(new IllegalStateException("第四次失败")).when(executor).execute(job.getBatchNo());

        new CalculationJobRetryWorker(service, executor, alert).process(job.getJobId(), LocalDateTime.now());

        // 本次新增断言：告警使用 recordFailure 返回的人工接管决策，且发生在状态记录之后。
        verify(service).recordFailure(eq(job.getJobId()), contains("第四次失败"), org.mockito.ArgumentMatchers.any());
        verify(alert).alert(job, decision);
    }

    @Test
    void shouldNotRewriteFailureWhenAlertFails() {
        CalculationRetryApplicationService service = mock(CalculationRetryApplicationService.class);
        CalculationRetryExecutor executor = mock(CalculationRetryExecutor.class);
        CalculationRetryAlertPort alert = mock(CalculationRetryAlertPort.class);
        CalculationJob job = new CalculationJob("JOB-W-ALERT-FAIL", "REQ-W-ALERT-FAIL", "BATCH-W-ALERT-FAIL",
                LocalDateTime.of(2026, 10, 21, 10, 0));
        CalculationRetryDecision decision = CalculationRetryDecision.manualRequired(4, "告警前已落库");
        when(service.start(job.getJobId())).thenReturn(job);
        when(service.recordFailure(eq(job.getJobId()), contains("告警前已落库"),
                org.mockito.ArgumentMatchers.any())).thenReturn(decision);
        doThrow(new IllegalStateException("告警通道不可用")).when(alert).alert(job, decision);
        doThrow(new IllegalStateException("告警前已落库")).when(executor).execute(job.getBatchNo());

        new CalculationJobRetryWorker(service, executor, alert).process(job.getJobId(), LocalDateTime.now());

        // 本次新增断言：告警异常不会再次调用 recordFailure，也不会覆盖已持久化的人工接管结果。
        verify(service).recordFailure(eq(job.getJobId()), contains("告警前已落库"), org.mockito.ArgumentMatchers.any());
        verify(service, org.mockito.Mockito.times(1)).recordFailure(
                eq(job.getJobId()), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void runOnceMustUseClaimAwareAtomicExecutionPath() {
        CalculationRetryApplicationService service = mock(CalculationRetryApplicationService.class);
        CalculationRetryExecutor executor = mock(CalculationRetryExecutor.class);
        CalculationRetryAlertPort alert = mock(CalculationRetryAlertPort.class);
        LocalDateTime now = LocalDateTime.of(2026, 10, 21, 10, 0);
        CalculationJob job = new CalculationJob("JOB-W-CLAIM", "REQ-W-CLAIM", "BATCH-W-CLAIM", now);
        CalculationRetryClaim claim = new CalculationRetryClaim(
                CalculationRetryState.active(job.getJobId(), job.getRequestKey(), now),
                "claim-token-1", now.plusMinutes(5));
        when(service.findDueClaims(now, 1)).thenReturn(Collections.singletonList(claim));
        when(service.executeAutomaticRetry(eq(claim), eq(now), eq(executor)))
                .thenReturn(new CalculationRetryExecutionResult(job, null));

        new CalculationJobRetryWorker(service, executor, alert).runOnce(now, 1);

        verify(service).executeAutomaticRetry(eq(claim), eq(now), eq(executor));
        verify(service, never()).prepareAutomaticRetry(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
        verify(service, never()).start(org.mockito.ArgumentMatchers.anyString());
    }
}
