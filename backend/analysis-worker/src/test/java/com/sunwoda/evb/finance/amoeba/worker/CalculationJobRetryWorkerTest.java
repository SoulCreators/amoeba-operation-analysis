package com.sunwoda.evb.finance.amoeba.worker;

import com.sunwoda.evb.finance.analysis.application.port.CalculationRetryExecutor;
import com.sunwoda.evb.finance.analysis.application.service.CalculationRetryApplicationService;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationJob;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V028 Worker 独立测试。
 *
 * <p>本次新增测试：验证执行异常被送回应用层失败处理入口，而不是由 Worker 自行跳转状态或吞掉记录。</p>
 */
class CalculationJobRetryWorkerTest {
    @Test
    void shouldDelegateExecutionFailureToApplicationService() {
        CalculationRetryApplicationService service = mock(CalculationRetryApplicationService.class);
        CalculationRetryExecutor executor = mock(CalculationRetryExecutor.class);
        CalculationJob job = new CalculationJob("JOB-W-1", "REQ-W-1", "BATCH-W-1",
                LocalDateTime.of(2026, 10, 21, 10, 0));
        when(service.start("JOB-W-1")).thenReturn(job);
        doThrow(new IllegalStateException("模拟计算失败")).when(executor).execute("BATCH-W-1");

        new CalculationJobRetryWorker(service, executor).process("JOB-W-1", LocalDateTime.now());

        verify(service).recordFailure(eq("JOB-W-1"), contains("模拟计算失败"), org.mockito.ArgumentMatchers.any());
    }
}
