package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.CalculationJob;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationJobStatus;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationRetryAction;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationRetryCommand;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationRetryDecision;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationRetryPolicy;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationRetryState;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationRetryStatus;
import com.sunwoda.evb.finance.analysis.domain.repository.CalculationJobRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.CalculationRetryActionRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.CalculationRetryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 计算失败处理应用服务。
 *
 * <p>本次新增：集中处理 request_key 幂等、自动退避、人工入口和作业状态机衔接；
 * 不直接修改 CalculationServiceImpl，也不允许人工命令跳到成功状态。</p>
 */
@Service
public class CalculationRetryApplicationService {
    private final CalculationJobRepository jobRepository;
    private final CalculationRetryRepository retryRepository;
    private final CalculationRetryActionRepository actionRepository;

    @Autowired
    public CalculationRetryApplicationService(CalculationJobRepository jobRepository,
                                              CalculationRetryRepository retryRepository,
                                              CalculationRetryActionRepository actionRepository) {
        if (jobRepository == null || retryRepository == null || actionRepository == null) {
            throw new IllegalArgumentException("失败处理仓储不能为空");
        }
        this.jobRepository = jobRepository;
        this.retryRepository = retryRepository;
        this.actionRepository = actionRepository;
    }

    /**
     * 幂等提交一个新的作业；相同 request_key 已有记录时返回首个作业，不创建第二个作业。
     */
    @Transactional
    public synchronized CalculationJob submit(CalculationJob job) {
        if (job == null) throw new IllegalArgumentException("job 不能为空");
        Optional<CalculationRetryState> existingState = retryRepository.findByRequestKey(job.getRequestKey());
        if (existingState.isPresent()) {
            return existingJob(existingState.get().getJobId());
        }
        Optional<CalculationJob> active = jobRepository.findActiveByRequestKey(job.getRequestKey());
        if (active.isPresent()) return active.get();
        jobRepository.save(job);
        retryRepository.save(CalculationRetryState.active(job.getJobId(), job.getRequestKey(),
                job.getCreatedAt()));
        return job;
    }

    /** 将 QUEUED 作业通过现有 CalculationJob 状态机置为 RUNNING。 */
    @Transactional
    public CalculationJob start(String jobId) {
        CalculationJob job = requiredJob(jobId);
        if (job.getStatus() != CalculationJobStatus.QUEUED) {
            throw new IllegalStateException("只有 QUEUED 作业可以开始执行: " + job.getStatus());
        }
        job.moveTo(CalculationJobStatus.RUNNING);
        return jobRepository.save(job);
    }

    /** 成功路径先完成作业状态，再完成重试状态。 */
    @Transactional
    public CalculationJob complete(String jobId, LocalDateTime now) {
        CalculationJob job = requiredJob(jobId);
        if (job.getStatus() != CalculationJobStatus.RUNNING) {
            throw new IllegalStateException("只有 RUNNING 作业可以完成: " + job.getStatus());
        }
        job.moveTo(CalculationJobStatus.SUCCEEDED);
        jobRepository.save(job);
        CalculationRetryState state = requiredState(jobId);
        state.succeed(now == null ? LocalDateTime.now() : now);
        retryRepository.save(state);
        return job;
    }

    /**
     * 首次执行或自动重试失败统一走这里；失败先进入 CalculationJob.FAILED，再计算下一步。
     */
    @Transactional
    public CalculationRetryDecision recordFailure(String jobId, String error, LocalDateTime failedAt) {
        CalculationJob job = requiredJob(jobId);
        if (job.getStatus() == CalculationJobStatus.RUNNING) {
            job.fail(error);
            jobRepository.save(job);
        } else if (job.getStatus() != CalculationJobStatus.FAILED) {
            throw new IllegalStateException("只有 RUNNING 作业可以记录失败: " + job.getStatus());
        }
        LocalDateTime actualTime = failedAt == null ? LocalDateTime.now() : failedAt;
        CalculationRetryDecision decision = CalculationRetryPolicy.decide(job.getAttemptNo(), error, actualTime);
        CalculationRetryState state = requiredState(jobId);
        if (decision.isManualRequired()) state.requireManual(decision, actualTime);
        else state.schedule(decision, actualTime);
        retryRepository.save(state);
        return decision;
    }

    /** 将到期自动重试置为 PROCESSING，并让作业按 FAILED -> QUEUED 状态机重新入队。 */
    @Transactional
    public CalculationJob prepareAutomaticRetry(String jobId, LocalDateTime now) {
        CalculationRetryState state = requiredState(jobId);
        LocalDateTime actualTime = now == null ? LocalDateTime.now() : now;
        if (state.getStatus() != CalculationRetryStatus.PENDING_RETRY) {
            throw new IllegalStateException("当前状态不可自动重试: " + state.getStatus());
        }
        state.claim(actualTime);
        CalculationJob job = requiredJob(jobId);
        if (job.getStatus() != CalculationJobStatus.FAILED) {
            throw new IllegalStateException("自动重试要求作业处于 FAILED: " + job.getStatus());
        }
        job.moveTo(CalculationJobStatus.QUEUED);
        jobRepository.save(job);
        retryRepository.save(state);
        return job;
    }

    /** 仅领取已经到期且仍处于 PENDING_RETRY 的记录。 */
    public List<CalculationRetryState> findDue(LocalDateTime now, int limit) {
        if (limit <= 0) throw new IllegalArgumentException("limit 必须大于 0");
        return retryRepository.findDue(now == null ? LocalDateTime.now() : now, limit);
    }

    /**
     * 人工重试入口：记录人工动作后只回到 QUEUED，实际执行仍由 Worker 完成。
     */
    @Transactional
    public CalculationRetryAction manualRetry(CalculationRetryCommand command) {
        validateManual(command, CalculationRetryCommand.Type.MANUAL_RETRY);
        CalculationJob job = validateManualJob(command);
        CalculationRetryState state = requiredState(command.getJobId());
        state.claim(command.getOccurredAt());
        job.moveTo(CalculationJobStatus.QUEUED);
        jobRepository.save(job);
        retryRepository.save(state);
        CalculationRetryAction action = new CalculationRetryAction(0L, job.getJobId(), job.getRequestKey(),
                CalculationRetryAction.Type.MANUAL_RETRY, command.getOperator(), command.getReason(),
                command.getOriginalAttemptNo(), "QUEUED", "人工重试已通过状态机重新入队", command.getOccurredAt());
        return actionRepository.save(action);
    }

    /** 人工补偿入口：只关闭失败处理状态并保留 FAILED 作业，不伪造成功结果。 */
    @Transactional
    public CalculationRetryAction manualCompensate(CalculationRetryCommand command) {
        validateManual(command, CalculationRetryCommand.Type.MANUAL_COMPENSATE);
        CalculationJob job = validateManualJob(command);
        CalculationRetryState state = requiredState(command.getJobId());
        state.compensate(command.getOccurredAt());
        retryRepository.save(state);
        CalculationRetryAction action = new CalculationRetryAction(0L, job.getJobId(), job.getRequestKey(),
                CalculationRetryAction.Type.MANUAL_COMPENSATE, command.getOperator(), command.getReason(),
                command.getOriginalAttemptNo(), "COMPENSATED", "人工补偿完成，作业仍保留 FAILED", command.getOccurredAt());
        return actionRepository.save(action);
    }

    private CalculationJob validateManualJob(CalculationRetryCommand command) {
        CalculationJob job = requiredJob(command.getJobId());
        if (!job.getRequestKey().equals(command.getRequestKey())) {
            throw new IllegalArgumentException("requestKey 与作业不一致");
        }
        if (job.getStatus() != CalculationJobStatus.FAILED) {
            throw new IllegalStateException("人工处理要求作业处于 FAILED: " + job.getStatus());
        }
        if (job.getAttemptNo() != command.getOriginalAttemptNo()) {
            throw new IllegalArgumentException("原尝试号与当前作业不一致");
        }
        return job;
    }

    private void validateManual(CalculationRetryCommand command, CalculationRetryCommand.Type expected) {
        if (command == null || command.getType() != expected) {
            throw new IllegalArgumentException("人工命令类型不正确");
        }
        CalculationRetryState state = requiredState(command.getJobId());
        if (state.getStatus() != CalculationRetryStatus.MANUAL_REQUIRED) {
            throw new IllegalStateException("只有 MANUAL_REQUIRED 可以人工处理: " + state.getStatus());
        }
        if (state.getLastFailedAttemptNo() == null
                || state.getLastFailedAttemptNo().intValue() != command.getOriginalAttemptNo()) {
            throw new IllegalArgumentException("人工命令原尝试号与失败记录不一致");
        }
    }

    private CalculationRetryState requiredState(String jobId) {
        return retryRepository.findByJobId(jobId)
                .orElseThrow(() -> new IllegalArgumentException("重试状态不存在: " + jobId));
    }

    private CalculationJob requiredJob(String jobId) {
        return jobRepository.findByJobId(jobId)
                .orElseThrow(() -> new IllegalArgumentException("作业不存在: " + jobId));
    }

    private CalculationJob existingJob(String jobId) {
        return requiredJob(jobId);
    }
}
