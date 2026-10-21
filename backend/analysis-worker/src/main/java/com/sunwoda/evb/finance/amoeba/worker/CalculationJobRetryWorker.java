package com.sunwoda.evb.finance.amoeba.worker;

import com.sunwoda.evb.finance.analysis.application.port.CalculationRetryExecutor;
import com.sunwoda.evb.finance.analysis.application.port.CalculationRetryAlertPort;
import com.sunwoda.evb.finance.analysis.application.service.CalculationRetryApplicationService;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationJob;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationRetryClaim;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationRetryClaimLostException;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationRetryCommand;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationRetryDecision;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationRetryState;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationRetryAction;
import com.sunwoda.evb.finance.analysis.application.model.CalculationRetryExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 计算作业失败处理 Worker。
 *
 * <p>本次新增：首次执行和到期重试共用同一状态机；本次修改将到期重试的租约确认、实际计算、
 * 成功/失败回写放入应用事务，并补充 MANUAL_REQUIRED 告警接线。</p>
 *
 * <p>本类保持无 Spring 定时注解，触发器可按现有部署方式调用 {@link #runOnce(LocalDateTime, int)}。</p>
 */
public final class CalculationJobRetryWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(CalculationJobRetryWorker.class);

    private final CalculationRetryApplicationService retryService;
    private final CalculationRetryExecutor executor;
    private final CalculationRetryAlertPort alert;

    public CalculationJobRetryWorker(CalculationRetryApplicationService retryService,
                                     CalculationRetryExecutor executor,
                                     CalculationRetryAlertPort alert) {
        if (retryService == null || executor == null || alert == null) {
            throw new IllegalArgumentException("Worker 依赖不能为空");
        }
        this.retryService = retryService;
        this.executor = executor;
        this.alert = alert;
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
            // 本次修改：先由应用服务完成失败状态持久化，再依据返回决策决定是否告警。
            CalculationRetryDecision decision = retryService.recordFailure(
                    job.getJobId(), message(failure), now);
            if (decision != null && decision.isManualRequired()) {
                notifyManualRequired(job, decision);
            }
        }
    }

    /** 扫描到期自动重试，最多处理 limit 条。 */
    public void runOnce(LocalDateTime now, int limit) {
        List<CalculationRetryClaim> due = retryService.findDueClaims(now, limit);
        for (CalculationRetryClaim claim : due) {
            try {
                CalculationRetryExecutionResult result = retryService.executeAutomaticRetry(claim, now, executor);
                if (result.getDecision() != null && result.getDecision().isManualRequired()) {
                    notifyManualRequired(result.getJob(), result.getDecision());
                }
            } catch (CalculationRetryClaimLostException lostClaim) {
                // 本次新增：租约已被其他 Worker 接管时跳过当前任务，不能继续执行或覆盖新结果。
                LOGGER.warn("计算重试租约已丢失，跳过执行: jobId={}, error={}",
                        claim.getJobId(), message(lostClaim));
            }
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

    /**
     * 本次修改：告警失败只记录日志，不重新进入失败持久化路径，保证已落库的 MANUAL_REQUIRED 不被覆盖。
     */
    private void notifyManualRequired(CalculationJob job, CalculationRetryDecision decision) {
        try {
            alert.alert(job, decision);
        } catch (RuntimeException alertFailure) {
            LOGGER.error("计算重试 MANUAL_REQUIRED 告警失败: jobId={}, batchNo={}, error={}",
                    job.getJobId(), job.getBatchNo(), message(alertFailure), alertFailure);
        }
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
