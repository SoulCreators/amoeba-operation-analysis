package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.CalculationJob;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationJobLifecyclePolicy;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationJobStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDateTime;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 计算作业生命周期契约测试。
 *
 * <p>本次新增测试只固化 CalculationJob 与 CalculationJobLifecyclePolicy 已经表达的行为，
 * 不新增或推导业务规则。</p>
 */
class CalculationJobLifecycleContractTest {

    @ParameterizedTest(name = "允许转移：{0} -> {1}")
    @MethodSource("legalTransitions")
    void shouldAllowEveryDeclaredTransition(CalculationJobStatus current, CalculationJobStatus next) {
        // 本次修改显式覆盖策略当前声明的全部合法状态转移。
        assertDoesNotThrow(() -> CalculationJobLifecyclePolicy.validate(current, next));
    }

    @ParameterizedTest(name = "拒绝转移：{0} -> {1}")
    @MethodSource("illegalTransitions")
    void shouldRejectEveryUndeclaredTransition(CalculationJobStatus current, CalculationJobStatus next) {
        // 本次修改显式覆盖四种状态之间除合法转移外的全部非法组合。
        assertThrows(IllegalStateException.class,
                () -> CalculationJobLifecyclePolicy.validate(current, next));
    }

    @ParameterizedTest(name = "拒绝后保持状态：{0} -> {1}")
    @MethodSource("illegalTransitions")
    void shouldKeepJobFieldsUnchangedWhenTransitionIsRejected(
            CalculationJobStatus current, CalculationJobStatus next) {
        CalculationJob job = jobAt(current);
        int originalAttemptNo = job.getAttemptNo();
        String originalLastError = job.getLastError();
        LocalDateTime originalStartedAt = job.getStartedAt();
        LocalDateTime originalFinishedAt = job.getFinishedAt();

        // 本次修改验证非法转移不会部分修改当前作业的已表达字段。
        assertThrows(IllegalStateException.class, () -> job.moveTo(next));

        assertEquals(current, job.getStatus());
        assertEquals(originalAttemptNo, job.getAttemptNo());
        assertEquals(originalLastError, job.getLastError());
        assertEquals(originalStartedAt, job.getStartedAt());
        assertEquals(originalFinishedAt, job.getFinishedAt());
    }

    @ParameterizedTest(name = "合法转移可执行：{0} -> {1}")
    @MethodSource("legalTransitions")
    void shouldExecuteEveryDeclaredTransitionOnJob(
            CalculationJobStatus current, CalculationJobStatus next) {
        CalculationJob job = jobAt(current);

        // 本次修改验证策略允许的每一条转移都能由 CalculationJob 执行。
        assertDoesNotThrow(() -> job.moveTo(next));
        assertEquals(next, job.getStatus());
    }

    @Test
    void shouldTrackAttemptAndLifecycleTimestamps() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 20, 9, 30);
        CalculationJob job = new CalculationJob("JOB-001", "request-001", "B-001", createdAt);

        assertEquals("JOB-001", job.getJobId());
        assertEquals("request-001", job.getRequestKey());
        assertEquals("B-001", job.getBatchNo());
        assertEquals(createdAt, job.getCreatedAt());
        assertEquals(CalculationJobStatus.QUEUED, job.getStatus());
        assertEquals(1, job.getAttemptNo());
        assertNull(job.getStartedAt());
        assertNull(job.getFinishedAt());
        assertNull(job.getLastError());

        job.moveTo(CalculationJobStatus.RUNNING);
        LocalDateTime startedAt = job.getStartedAt();
        assertNotNull(startedAt);
        assertEquals(1, job.getAttemptNo());
        assertNull(job.getFinishedAt());

        job.moveTo(CalculationJobStatus.SUCCEEDED);
        assertEquals(CalculationJobStatus.SUCCEEDED, job.getStatus());
        assertEquals(1, job.getAttemptNo());
        assertEquals(startedAt, job.getStartedAt());
        assertNotNull(job.getFinishedAt());
        assertNull(job.getLastError());
    }

    @Test
    void shouldRecordFailureAndResetRetryFields() {
        CalculationJob job = job();

        job.moveTo(CalculationJobStatus.RUNNING);
        job.fail("事实数据读取失败");
        assertEquals(CalculationJobStatus.FAILED, job.getStatus());
        assertEquals(1, job.getAttemptNo());
        assertEquals("事实数据读取失败", job.getLastError());
        assertNotNull(job.getStartedAt());
        assertNotNull(job.getFinishedAt());

        // 本次修改锁定 FAILED -> QUEUED 当前已表达的重试字段行为。
        job.moveTo(CalculationJobStatus.QUEUED);
        assertEquals(CalculationJobStatus.QUEUED, job.getStatus());
        assertEquals(2, job.getAttemptNo());
        assertEquals("事实数据读取失败", job.getLastError());
        assertNull(job.getStartedAt());
        assertNull(job.getFinishedAt());

        job.moveTo(CalculationJobStatus.RUNNING);
        job.moveTo(CalculationJobStatus.SUCCEEDED);
        assertNull(job.getLastError());
    }

    private static Stream<Arguments> legalTransitions() {
        // 这些数据与 CalculationJobLifecyclePolicy 当前显式声明的转移一一对应。
        return Stream.of(
                Arguments.of(CalculationJobStatus.QUEUED, CalculationJobStatus.RUNNING),
                Arguments.of(CalculationJobStatus.RUNNING, CalculationJobStatus.SUCCEEDED),
                Arguments.of(CalculationJobStatus.RUNNING, CalculationJobStatus.FAILED),
                Arguments.of(CalculationJobStatus.FAILED, CalculationJobStatus.QUEUED));
    }

    private static Stream<Arguments> illegalTransitions() {
        // 这些数据覆盖全部 4 x 4 状态组合中未被策略声明的转移。
        return Stream.of(
                Arguments.of(CalculationJobStatus.QUEUED, CalculationJobStatus.QUEUED),
                Arguments.of(CalculationJobStatus.QUEUED, CalculationJobStatus.SUCCEEDED),
                Arguments.of(CalculationJobStatus.QUEUED, CalculationJobStatus.FAILED),
                Arguments.of(CalculationJobStatus.RUNNING, CalculationJobStatus.QUEUED),
                Arguments.of(CalculationJobStatus.RUNNING, CalculationJobStatus.RUNNING),
                Arguments.of(CalculationJobStatus.FAILED, CalculationJobStatus.RUNNING),
                Arguments.of(CalculationJobStatus.FAILED, CalculationJobStatus.SUCCEEDED),
                Arguments.of(CalculationJobStatus.FAILED, CalculationJobStatus.FAILED),
                Arguments.of(CalculationJobStatus.SUCCEEDED, CalculationJobStatus.QUEUED),
                Arguments.of(CalculationJobStatus.SUCCEEDED, CalculationJobStatus.RUNNING),
                Arguments.of(CalculationJobStatus.SUCCEEDED, CalculationJobStatus.SUCCEEDED),
                Arguments.of(CalculationJobStatus.SUCCEEDED, CalculationJobStatus.FAILED));
    }

    private static CalculationJob jobAt(CalculationJobStatus status) {
        CalculationJob job = job();
        if (status == CalculationJobStatus.RUNNING) {
            job.moveTo(CalculationJobStatus.RUNNING);
        } else if (status == CalculationJobStatus.SUCCEEDED) {
            job.moveTo(CalculationJobStatus.RUNNING);
            job.moveTo(CalculationJobStatus.SUCCEEDED);
        } else if (status == CalculationJobStatus.FAILED) {
            job.moveTo(CalculationJobStatus.RUNNING);
            job.fail("事实数据读取失败");
        }
        return job;
    }

    private static CalculationJob job() {
        return new CalculationJob("JOB-001", "request-001", "B-001", LocalDateTime.now());
    }
}
