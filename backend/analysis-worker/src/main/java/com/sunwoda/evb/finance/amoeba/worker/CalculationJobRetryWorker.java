package com.sunwoda.evb.finance.amoeba.worker;

import com.sunwoda.evb.finance.analysis.application.port.CalculationRetryExecutor;
import com.sunwoda.evb.finance.analysis.application.service.CalculationRetryApplicationService;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationJob;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationRetryCommand;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationRetryDecision;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationRetryState;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationRetryAction;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 计算作业失败处理 Worker。
 *
 * <p>本次新增：首次执行和到期重试共用同一执行路径；失败先落 FAILED，再由应用服务按策略排队，
 * 人工重试/补偿只委托应用服务，不在 Worker 绕过状态机。</p>
 *
 * <p>本类保持无 Spring 定时注解，触发器可按现有部署方式调用 {@link #runOnce(LocalDateTime, int)}。</p>
 */
public final class CalculationJobRetryWorker {
    private final CalculationRetryApplicationService retryService;
    private final CalculationRetryExecutor executor;

    public CalculationJobRetryWorker(CalculationRetryApplicationService retryService,
                                     CalculationRetryExecutor executor) {
        if (retryService == null || executor == null) throw new IllegalArgumentException("Worker 依赖不能为空");
        this.retryService = retryService;
        this.executor = executor;
    }

    /** 幂等提交入口：相同 request_key 返回原作业。 */
    public CalculationJob submit(CalculationJob job) {
        return retryService.submit(job);
    }

    /** 处理一个 QUEUED 作业；异常已经转为失败记录和下一次自动动作，不向触发器泄漏业务异常。 */
    public void process(String jobId, LocalDateTime now) {
        CalculationJob job = retryService.start(required(jobId));
        try {
            executor.execute(job.getBatchNo());
            retryService.complete(job.getJobId(), now);
        } catch (RuntimeException failure) {
            retryService.recordFailure(job.getJobId(), message(failure), now);
        }
    }

    /** 扫描到期自动重试，最多处理 limit 条。 */
    public void runOnce(LocalDateTime now, int limit) {
        List<CalculationRetryState> due = retryService.findDue(now, limit);
        for (CalculationRetryState state : due) {
            retryService.prepareAutomaticRetry(state.getJobId(), now);
            process(state.getJobId(), now);
        }
    }

    /** 保留人工重试入口，动作记录和状态转移均由应用层完成。 */
    public CalculationRetryAction manualRetry(CalculationRetryCommand command) {
        return retryService.manualRetry(command);
    }

    /** 保留人工补偿入口，补偿不会把 FAILED 作业伪造成 SUCCEEDED。 */
    public CalculationRetryAction manualCompensate(CalculationRetryCommand command) {
        return retryService.manualCompensate(command);
    }

    /** 供调用方观察本次失败后的自动处理结果。 */
    public CalculationRetryDecision recordFailure(String jobId, String error, LocalDateTime now) {
        return retryService.recordFailure(required(jobId), error, now);
    }

    private String required(String value) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException("jobId 不能为空");
        return value.trim();
    }

    private String message(RuntimeException failure) {
        String detail = failure.getMessage();
        return detail == null || detail.trim().isEmpty() ? failure.getClass().getName() : detail;
    }
}
