package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.application.port.ManualRevisionIdempotencyPort;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationVersion;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationVersionStatus;
import com.sunwoda.evb.finance.analysis.domain.repository.CalculationVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V030 JDBC 适配器纯单测。
 *
 * <p>本次新增：不启动数据库，覆盖原子领取、相同载荷重放、冲突拒绝和 db profile/事务接线；
 * PostgreSQL 的唯一约束、JSONB 和并发阻塞行为由同包合约测试验证。</p>
 */
class JdbcManualRevisionIdempotencyAdapterTest {
    private JdbcTemplate jdbc;
    private CalculationVersionRepository versionRepository;
    private JdbcManualRevisionIdempotencyAdapter adapter;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        versionRepository = mock(CalculationVersionRepository.class);
        adapter = new JdbcManualRevisionIdempotencyAdapter(jdbc, versionRepository);
    }

    @Test
    void firstRequestClaimsAtomicallyExecutesOnceAndPersistsTargetId() {
        CalculationVersion target = version("target-001");
        AtomicInteger executions = new AtomicInteger();
        // 本次修复：按 JdbcTemplate 的真实 varargs 重载逐参数匹配，避免 Mockito 将整组参数误判为未命中。
        doReturn(1).when(jdbc).update(contains("ON CONFLICT (user_id, request_key) DO NOTHING"),
                any(), any(), any(), any(), any());
        doReturn(1).when(jdbc).update(contains("status = 'COMPLETED'"), any(), any(), any());

        CalculationVersion actual = adapter.execute(" user-001 ", " request-001 ", "source-001",
                "人工修订", new ManualRevisionIdempotencyPort.RevisionOperation() {
                    @Override
                    public CalculationVersion execute() {
                        executions.incrementAndGet();
                        return target;
                    }
                });

        assertSame(target, actual);
        assertEquals(1, executions.get());
        verify(jdbc).update(contains("ON CONFLICT (user_id, request_key) DO NOTHING"),
                eq("user-001"), eq("request-001"), eq("source-001"), eq("人工修订"),
                anyString());
        verify(jdbc).update(contains("status = 'COMPLETED'"), eq("target-001"),
                eq("user-001"), eq("request-001"));
    }

    @Test
    void completedRecordReplaysVersionWithoutCallingOperation() {
        CalculationVersion target = version("target-002");
        doReturn(0).when(jdbc).update(anyString(), any(), any(), any(), any(), any());
        when(jdbc.query(anyString(), any(RowMapper.class), any(), any()))
                .thenReturn(Collections.singletonList(record("source-002", "人工修订", "COMPLETED",
                        "target-002")));
        when(versionRepository.findById("target-002")).thenReturn(Optional.of(target));

        CalculationVersion actual = adapter.execute("user-002", "request-002", "source-002",
                "人工修订", new ManualRevisionIdempotencyPort.RevisionOperation() {
                    @Override
                    public CalculationVersion execute() {
                        throw new AssertionError("已完成请求不能再次执行人工修订");
                    }
                });

        assertSame(target, actual);
        verify(versionRepository).findById("target-002");
    }

    @Test
    void sameBusinessKeyWithDifferentPayloadIsRejected() {
        doReturn(0).when(jdbc).update(anyString(), any(), any(), any(), any(), any());
        when(jdbc.query(anyString(), any(RowMapper.class), any(), any()))
                .thenReturn(Collections.singletonList(record("source-003", "原始原因", "COMPLETED",
                        "target-003")));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> adapter.execute("user-003", "request-003", "source-003", "另一原因",
                        new ManualRevisionIdempotencyPort.RevisionOperation() {
                            @Override
                            public CalculationVersion execute() {
                                throw new AssertionError("冲突载荷不能执行");
                            }
                        }));

        assertTrue(failure.getMessage().contains("不同人工修订请求"));
        verify(versionRepository, never()).findById(anyString());
    }

    @Test
    void dbAdapterIsThePortAndRunsWithTransactionBoundary() throws Exception {
        assertTrue(ManualRevisionIdempotencyPort.class.isAssignableFrom(
                JdbcManualRevisionIdempotencyAdapter.class));
        assertNotNull(JdbcManualRevisionIdempotencyAdapter.class.getAnnotation(Repository.class));
        assertEquals("db", JdbcManualRevisionIdempotencyAdapter.class
                .getAnnotation(Profile.class).value()[0]);
        assertTrue(JdbcManualRevisionIdempotencyAdapter.class
                .getMethod("execute", String.class, String.class, String.class, String.class,
                        ManualRevisionIdempotencyPort.RevisionOperation.class)
                .isAnnotationPresent(Transactional.class));
    }

    private static JdbcManualRevisionIdempotencyAdapter.IdempotencyRecordView record(
            String sourceVersionId, String revisionReason, String status, String targetVersionId) {
        return new JdbcManualRevisionIdempotencyAdapter.IdempotencyRecordView(
                sourceVersionId, revisionReason, status, targetVersionId);
    }

    private static CalculationVersion version(String id) {
        return new CalculationVersion(id, "batch-001", AnalysisPerspective.BASE, "V-001", "job-001",
                "snapshot-001", "source-hash", "config-hash", "input-fingerprint",
                CalculationVersionStatus.CALCULATED, true, "source-001", "trace-001", "user-001",
                LocalDateTime.of(2026, 10, 21, 10, 30), null, null, null, null);
    }
}
