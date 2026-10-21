package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.CalculationJob;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationJobStatus;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationRetryAction;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationRetryClaim;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationRetryCommand;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationRetryState;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationRetryStatus;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.BatchStatus;
import com.sunwoda.evb.finance.analysis.domain.model.UserScope;
import com.sunwoda.evb.finance.analysis.domain.repository.AnalysisBatchRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.CalculationJobRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.CalculationRetryActionRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.CalculationRetryRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.UserScopeRepository;
import com.sunwoda.evb.finance.analysis.application.model.CalculationRetryExecutionResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * V028 应用编排独立测试。
 *
 * <p>本次新增测试：验证 request_key 幂等、自动失败链、人工重试/补偿入口和状态机门禁。</p>
 */
class CalculationRetryApplicationServiceTest {
    private static final LocalDateTime t0 = LocalDateTime.of(2026, 10, 21, 10, 0);

    @Test
    void shouldBeIdempotentAndMoveFailureThroughThreeRetriesToManualRequired() {
        Fixture fixture = new Fixture();
        CalculationJob first = new CalculationJob("JOB-R-1", "REQ-R-1", "BATCH-R-1", t0);
        CalculationJob accepted = fixture.submit(first);
        assertSame(first, accepted);
        assertSame(accepted, fixture.submit(
                new CalculationJob("JOB-R-2", "REQ-R-1", "BATCH-R-2", t0.plusSeconds(1))));

        fixture.service.start(first.getJobId());
        fixture.service.recordFailure(first.getJobId(), "failure-1", t0);
        assertEquals(CalculationRetryStatus.PENDING_RETRY, fixture.state().getStatus());
        assertEquals(t0.plusMinutes(1), fixture.state().getNextRetryAt());

        fixture.service.prepareAutomaticRetry(first.getJobId(), t0.plusMinutes(1));
        fixture.service.start(first.getJobId());
        fixture.service.recordFailure(first.getJobId(), "failure-2", t0.plusMinutes(1));
        assertEquals(t0.plusMinutes(6), fixture.state().getNextRetryAt());

        fixture.service.prepareAutomaticRetry(first.getJobId(), t0.plusMinutes(6));
        fixture.service.start(first.getJobId());
        fixture.service.recordFailure(first.getJobId(), "failure-3", t0.plusMinutes(6));
        assertEquals(t0.plusMinutes(21), fixture.state().getNextRetryAt());

        fixture.service.prepareAutomaticRetry(first.getJobId(), t0.plusMinutes(21));
        fixture.service.start(first.getJobId());
        fixture.service.recordFailure(first.getJobId(), "failure-4", t0.plusMinutes(21));

        assertEquals(4, first.getAttemptNo());
        assertEquals(CalculationJobStatus.FAILED, first.getStatus());
        assertEquals(CalculationRetryStatus.MANUAL_REQUIRED, fixture.state().getStatus());
        assertEquals(4, fixture.state().getLastFailedAttemptNo().intValue());
    }

    @Test
    void manualRetryMustRecordFieldsAndReturnThroughQueued() {
        Fixture fixture = manualRequiredFixture();
        CalculationRetryCommand command = CalculationRetryCommand.manualRetry(
                "JOB-R-1", "REQ-R-1", 4, "operator-1", "修复源数据后重试", t0.plusMinutes(22));

        CalculationRetryAction action = fixture.service.manualRetry(command, "operator-1");

        assertEquals(CalculationRetryAction.Type.MANUAL_RETRY, action.getType());
        assertEquals("operator-1", action.getOperator());
        assertEquals("修复源数据后重试", action.getReason());
        assertEquals(4, action.getOriginalAttemptNo());
        assertEquals("QUEUED", action.getResult());
        assertEquals(CalculationJobStatus.QUEUED, fixture.job.getStatus());
        assertEquals(CalculationRetryStatus.PROCESSING, fixture.state().getStatus());
    }

    @Test
    void manualCompensationMustNotFabricateSuccess() {
        Fixture fixture = manualRequiredFixture();
        CalculationRetryCommand command = CalculationRetryCommand.manualCompensate(
                "JOB-R-1", "REQ-R-1", 4, "operator-2", "业务确认不再重算", t0.plusMinutes(22));

        CalculationRetryAction action = fixture.service.manualCompensate(command, "operator-2");

        assertEquals(CalculationRetryAction.Type.MANUAL_COMPENSATE, action.getType());
        assertEquals("COMPENSATED", action.getResult());
        assertEquals(CalculationJobStatus.FAILED, fixture.job.getStatus());
        assertEquals(CalculationRetryStatus.COMPENSATED, fixture.state().getStatus());
    }

    @Test
    void manualCommandCannotBypassStateMachine() {
        Fixture fixture = new Fixture();
        CalculationJob job = new CalculationJob("JOB-R-1", "REQ-R-1", "BATCH-R-1", t0);
        fixture.service.submit(job);
        CalculationRetryCommand command = CalculationRetryCommand.manualCompensate(
                job.getJobId(), job.getRequestKey(), 1, "operator", "reason", t0);

        assertThrows(IllegalStateException.class,
                () -> fixture.service.manualCompensate(command, "operator"));
        assertEquals(CalculationJobStatus.QUEUED, job.getStatus());
    }

    @Test
    void manualEntryWithoutAuthenticatedOperatorFailsClosed() {
        Fixture fixture = manualRequiredFixture();
        CalculationRetryCommand command = CalculationRetryCommand.manualCompensate(
                "JOB-R-1", "REQ-R-1", 4, "operator-2", "业务确认不再重算", t0.plusMinutes(22));

        assertThrows(IllegalArgumentException.class, () -> fixture.service.manualCompensate(command));
        assertEquals(CalculationRetryStatus.MANUAL_REQUIRED, fixture.state().getStatus());
    }

    @Test
    void manualEntryRejectsAuthenticatedOperatorMismatch() {
        Fixture fixture = manualRequiredFixture();
        CalculationRetryCommand command = CalculationRetryCommand.manualRetry(
                "JOB-R-1", "REQ-R-1", 4, "operator-1", "修复源数据后重试", t0.plusMinutes(22));

        assertThrows(IllegalStateException.class,
                () -> fixture.service.manualRetry(command, "operator-2"));
        assertEquals(CalculationRetryStatus.MANUAL_REQUIRED, fixture.state().getStatus());
    }

    @Test
    void manualEntryRejectsScopeOutsideAuthenticatedHqFinanceGrant() {
        Fixture fixture = manualRequiredFixture("YICHANG");
        CalculationRetryCommand command = CalculationRetryCommand.manualRetry(
                "JOB-R-1", "REQ-R-1", 4, "operator-1", "修复源数据后重试", t0.plusMinutes(22));

        assertThrows(IllegalStateException.class,
                () -> fixture.service.manualRetry(command, "operator-1"));
        assertEquals(CalculationRetryStatus.MANUAL_REQUIRED, fixture.state().getStatus());
    }

    @Test
    void automaticRetrySuccessWritesJobAndRetryStateAsOneApplicationResult() {
        Fixture fixture = pendingRetryFixture();
        CalculationRetryClaim claim = fixture.retries.findDueClaims(t0.plusMinutes(1), 1).get(0);

        CalculationRetryExecutionResult result = fixture.service.executeAutomaticRetry(
                claim, t0.plusMinutes(1), batchNo -> { });

        assertEquals(CalculationJobStatus.SUCCEEDED, result.getJob().getStatus());
        assertEquals(CalculationRetryStatus.SUCCEEDED, fixture.state().getStatus());
        org.junit.jupiter.api.Assertions.assertNull(result.getDecision());
    }

    @Test
    void automaticRetryFailureWritesFailureDecisionBeforeReturning() {
        Fixture fixture = pendingRetryFixture();
        CalculationRetryClaim claim = fixture.retries.findDueClaims(t0.plusMinutes(1), 1).get(0);

        CalculationRetryExecutionResult result = fixture.service.executeAutomaticRetry(
                claim, t0.plusMinutes(1), batchNo -> { throw new IllegalStateException("自动重试失败"); });

        assertEquals(CalculationJobStatus.FAILED, result.getJob().getStatus());
        assertEquals(CalculationRetryStatus.PENDING_RETRY, fixture.state().getStatus());
        assertEquals("自动重试失败", fixture.state().getLastError());
        assertEquals(2, result.getDecision().getRetryNo());
    }

    private Fixture pendingRetryFixture() {
        Fixture fixture = new Fixture();
        fixture.job = new CalculationJob("JOB-R-ATOMIC", "REQ-R-ATOMIC", "BATCH-R-ATOMIC", t0);
        fixture.submit(fixture.job);
        fixture.service.start(fixture.job.getJobId());
        fixture.service.recordFailure(fixture.job.getJobId(), "首次失败", t0);
        return fixture;
    }

    private Fixture manualRequiredFixture() {
        return manualRequiredFixture("NANCHANG");
    }

    private Fixture manualRequiredFixture(String scopeCode) {
        Fixture fixture = new Fixture(scopeCode);
        fixture.job = new CalculationJob("JOB-R-1", "REQ-R-1", "BATCH-R-1", t0);
        fixture.submit(fixture.job);
        for (int attempt = 1; attempt <= 4; attempt++) {
            fixture.service.start(fixture.job.getJobId());
            LocalDateTime failedAt = t0.plusMinutes(attempt == 1 ? 0 : attempt == 2 ? 1 : attempt == 3 ? 6 : 21);
            fixture.service.recordFailure(fixture.job.getJobId(), "failure-" + attempt, failedAt);
            if (attempt < 4) fixture.service.prepareAutomaticRetry(fixture.job.getJobId(),
                    failedAt.plusMinutes(attempt == 1 ? 1 : attempt == 2 ? 5 : 15));
        }
        return fixture;
    }

    private static final class Fixture {
        private final JobRepository jobs = new JobRepository();
        private final RetryRepository retries = new RetryRepository();
        private final ActionRepository actions = new ActionRepository();
        private final BatchRepository batches = new BatchRepository();
        private final ScopeRepository scopes = new ScopeRepository();
        private final PermissionService permissions = new PermissionServiceImpl(scopes);
        private final String scopeCode;
        private final CalculationRetryApplicationService service;
        private CalculationJob job;

        private Fixture() {
            this("NANCHANG");
        }

        private Fixture(String scopeCode) {
            this.scopeCode = scopeCode;
            this.service = new CalculationRetryApplicationService(jobs, retries, actions, batches,
                    permissions, batchNo -> java.util.Collections.singleton(this.scopeCode));
        }

        private CalculationJob submit(CalculationJob candidate) {
            // 本次修复：夹具始终保存应用服务返回的规范作业，幂等 replay 不再丢失状态查询主键。
            CalculationJob accepted = service.submit(candidate);
            if (!batches.findByBatchNo(accepted.getBatchNo()).isPresent()) {
                batches.save(new AnalysisBatch(1L, accepted.getBatchNo(), "2026-10",
                        AnalysisPerspective.BASE, BatchStatus.READY_FOR_CALCULATION,
                        "retry-test", t0));
            }
            if (job == null) job = accepted;
            return accepted;
        }

        private CalculationRetryState state() {
            if (job == null) throw new AssertionError("测试夹具尚未提交规范作业");
            return retries.findByJobId(job.getJobId())
                    .orElseThrow(() -> new AssertionError("测试夹具缺少重试状态: " + job.getJobId()));
        }
    }

    private static final class BatchRepository implements AnalysisBatchRepository {
        private final Map<String, AnalysisBatch> batches = new HashMap<String, AnalysisBatch>();

        @Override public AnalysisBatch save(AnalysisBatch batch) {
            batches.put(batch.getBatchNo(), batch);
            return batch;
        }

        @Override public Optional<AnalysisBatch> findByBatchNo(String batchNo) {
            return Optional.ofNullable(batches.get(batchNo));
        }
    }

    private static final class ScopeRepository implements UserScopeRepository {
        @Override public UserScope save(UserScope scope) { return scope; }

        @Override public List<UserScope> findByUserAndPerspective(String userId,
                                                                   com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective perspective) {
            if (userId != null && userId.startsWith("operator-")) {
                return java.util.Collections.singletonList(new UserScope(userId, "HQ_FINANCE",
                        perspective, "NANCHANG", true, true, true, true));
            }
            return java.util.Collections.emptyList();
        }
    }

    private static final class JobRepository implements CalculationJobRepository {
        private final Map<String, CalculationJob> jobs = new HashMap<String, CalculationJob>();

        @Override public synchronized CalculationJob save(CalculationJob job) {
            for (CalculationJob existing : jobs.values()) {
                if (!existing.getJobId().equals(job.getJobId())
                        && existing.getRequestKey().equals(job.getRequestKey())
                        && (existing.getStatus() == CalculationJobStatus.QUEUED
                        || existing.getStatus() == CalculationJobStatus.RUNNING)) {
                    throw new IllegalStateException("requestKey duplicate");
                }
            }
            jobs.put(job.getJobId(), job);
            return job;
        }
        @Override public Optional<CalculationJob> findByJobId(String jobId) { return Optional.ofNullable(jobs.get(jobId)); }
        @Override public Optional<CalculationJob> findActiveByRequestKey(String requestKey) {
            for (CalculationJob job : jobs.values()) {
                if (job.getRequestKey().equals(requestKey)
                        && (job.getStatus() == CalculationJobStatus.QUEUED
                        || job.getStatus() == CalculationJobStatus.RUNNING)) return Optional.of(job);
            }
            return Optional.empty();
        }
    }

    private static final class RetryRepository implements CalculationRetryRepository {
        private final Map<String, CalculationRetryState> states = new HashMap<String, CalculationRetryState>();
        @Override public synchronized CalculationRetryState save(CalculationRetryState state) {
            for (CalculationRetryState existing : states.values()) {
                if (!existing.getJobId().equals(state.getJobId())
                        && existing.getRequestKey().equals(state.getRequestKey())) throw new IllegalStateException("request duplicate");
            }
            states.put(state.getJobId(), state); return state;
        }
        @Override public Optional<CalculationRetryState> findByJobId(String jobId) { return Optional.ofNullable(states.get(jobId)); }
        @Override public Optional<CalculationRetryState> findByRequestKey(String requestKey) {
            for (CalculationRetryState state : states.values()) if (state.getRequestKey().equals(requestKey)) return Optional.of(state);
            return Optional.empty();
        }
        @Override public List<CalculationRetryState> findDue(LocalDateTime now, int limit) {
            List<CalculationRetryState> result = new ArrayList<CalculationRetryState>();
            for (CalculationRetryState state : states.values()) {
                if (state.getStatus() == CalculationRetryStatus.PENDING_RETRY && !state.getNextRetryAt().isAfter(now)) result.add(state);
            }
            result.sort(Comparator.comparing(CalculationRetryState::getNextRetryAt));
            return result.size() <= limit ? result : result.subList(0, limit);
        }
    }

    private static final class ActionRepository implements CalculationRetryActionRepository {
        private final List<CalculationRetryAction> actions = new ArrayList<CalculationRetryAction>();
        @Override public CalculationRetryAction save(CalculationRetryAction action) { actions.add(action); return action; }
        @Override public List<CalculationRetryAction> findByJobId(String jobId) { return actions; }
    }
}
