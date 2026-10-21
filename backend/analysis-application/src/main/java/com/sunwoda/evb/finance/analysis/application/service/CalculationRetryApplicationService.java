package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.CalculationJob;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationJobStatus;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationRetryAction;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationRetryCommand;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationRetryDecision;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationRetryPolicy;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationRetryState;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationRetryStatus;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.PermissionAction;
import com.sunwoda.evb.finance.analysis.domain.repository.AnalysisBatchRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.CalculationJobRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.CalculationRetryActionRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.CalculationRetryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 计算失败处理应用服务。
 *
 * <p>本次新增：集中处理 request_key 幂等、自动退避、人工入口和作业状态机衔接；
 * 不直接修改 CalculationServiceImpl，也不允许人工命令跳到成功状态。</p>
 */
@Service
public class CalculationRetryApplicationService {
    /** 本次新增：由运行时显式提供作业涉及的业务范围，人工动作不得自行猜测范围。 */
    @FunctionalInterface
    public interface ScopeResolver {
        Set<String> resolve(String batchNo);
    }

    private final CalculationJobRepository jobRepository;
    private final CalculationRetryRepository retryRepository;
    private final CalculationRetryActionRepository actionRepository;
    private final AnalysisBatchRepository batchRepository;
    private final PermissionService permissionService;
    private final ScopeResolver scopeResolver;
    private final boolean manualAuthorizationConfigured;

    /**
     * 兼容无人工动作授权上下文的单元测试/非生产自动编排构造器。
     *
     * <p>本次修复：保留通用应用上下文的失败关闭 Bean；Worker profile 通过独立的主 Bean
     * 注入完整授权上下文，避免 API 启动依赖 Worker 专属 ScopeResolver。</p>
     */
    @Autowired
    public CalculationRetryApplicationService(CalculationJobRepository jobRepository,
                                              CalculationRetryRepository retryRepository,
                                              CalculationRetryActionRepository actionRepository) {
        this(jobRepository, retryRepository, actionRepository, null, null, null, false);
    }

    /** 本次新增：人工入口必须同时注入批次视角、权限服务和 scope 解析器。 */
    public CalculationRetryApplicationService(CalculationJobRepository jobRepository,
                                              CalculationRetryRepository retryRepository,
                                              CalculationRetryActionRepository actionRepository,
                                              AnalysisBatchRepository batchRepository,
                                              PermissionService permissionService,
                                              ScopeResolver scopeResolver) {
        this(jobRepository, retryRepository, actionRepository, batchRepository,
                permissionService, scopeResolver, true);
    }

    private CalculationRetryApplicationService(CalculationJobRepository jobRepository,
                                               CalculationRetryRepository retryRepository,
                                               CalculationRetryActionRepository actionRepository,
                                               AnalysisBatchRepository batchRepository,
                                               PermissionService permissionService,
                                               ScopeResolver scopeResolver,
                                               boolean manualAuthorizationConfigured) {
        if (jobRepository == null || retryRepository == null || actionRepository == null) {
            throw new IllegalArgumentException("失败处理仓储不能为空");
        }
        if (manualAuthorizationConfigured && (batchRepository == null || permissionService == null
                || scopeResolver == null)) {
            throw new IllegalArgumentException("人工动作授权上下文不能为空");
        }
        this.jobRepository = jobRepository;
        this.retryRepository = retryRepository;
        this.actionRepository = actionRepository;
        this.batchRepository = batchRepository;
        this.permissionService = permissionService;
        this.scopeResolver = scopeResolver;
        this.manualAuthorizationConfigured = manualAuthorizationConfigured;
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
        // 本次修改：旧入口没有认证操作者，明确失败关闭，禁止把命令字段当成认证身份。
        return manualRetry(command, null);
    }

    /** 本次新增：人工重试必须由认证上下文传入操作者，并与命令操作者一致。 */
    @Transactional
    public CalculationRetryAction manualRetry(CalculationRetryCommand command,
                                              String authenticatedOperator) {
        validateManual(command, CalculationRetryCommand.Type.MANUAL_RETRY,
                authenticatedOperator, PermissionAction.CALCULATE);
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
        // 本次修改：旧入口没有认证操作者，明确失败关闭，禁止把命令字段当成认证身份。
        return manualCompensate(command, null);
    }

    /** 本次新增：人工补偿必须由认证上下文传入操作者，并校验补偿动作的授权范围。 */
    @Transactional
    public CalculationRetryAction manualCompensate(CalculationRetryCommand command,
                                                   String authenticatedOperator) {
        validateManual(command, CalculationRetryCommand.Type.MANUAL_COMPENSATE,
                authenticatedOperator, PermissionAction.COMPENSATE);
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

    private void validateManual(CalculationRetryCommand command, CalculationRetryCommand.Type expected,
                                String authenticatedOperator, PermissionAction action) {
        if (command == null || command.getType() != expected) {
            throw new IllegalArgumentException("人工命令类型不正确");
        }
        if (!manualAuthorizationConfigured) {
            throw new IllegalStateException("人工动作必须通过带认证操作者和授权上下文的入口");
        }
        if (authenticatedOperator == null || authenticatedOperator.trim().isEmpty()) {
            throw new IllegalArgumentException("认证操作者不能为空");
        }
        String normalizedOperator = authenticatedOperator.trim();
        if (!normalizedOperator.equals(command.getOperator())) {
            throw new IllegalStateException("命令操作者与认证操作者不一致");
        }
        CalculationRetryState state = requiredState(command.getJobId());
        if (state.getStatus() != CalculationRetryStatus.MANUAL_REQUIRED) {
            throw new IllegalStateException("只有 MANUAL_REQUIRED 可以人工处理: " + state.getStatus());
        }
        if (state.getLastFailedAttemptNo() == null
                || state.getLastFailedAttemptNo().intValue() != command.getOriginalAttemptNo()) {
            throw new IllegalArgumentException("人工命令原尝试号与失败记录不一致");
        }
        CalculationJob job = requiredJob(command.getJobId());
        AnalysisBatch batch = batchRepository.findByBatchNo(job.getBatchNo())
                .orElseThrow(() -> new IllegalArgumentException("批次不存在: " + job.getBatchNo()));
        Set<String> scopeCodes = scopeResolver.resolve(job.getBatchNo());
        if (scopeCodes == null || scopeCodes.isEmpty()) {
            throw new IllegalStateException("作业缺少可审计的授权范围");
        }
        // 本次修复：人工动作必须把真实批次涉及的全部 scope 交给权限服务逐项校验，不能只做视角级放行。
        if (!permissionService.canPerform(normalizedOperator, batch.getPerspective(), action, scopeCodes)) {
            throw new IllegalStateException("当前用户无人工处理授权范围");
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
