package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.application.model.CalculationProvenance;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationSnapshot;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationVersion;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationVersionStatus;
import com.sunwoda.evb.finance.analysis.domain.model.UserScope;
import com.sunwoda.evb.finance.analysis.domain.repository.CalculationSnapshotRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.CalculationVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 历史上游配置重放应用服务：审计门禁 + 版本/追踪号还原。 */
class CalculationProvenanceServiceTest {
    private static final String VERSION_ID = "CALC-1";
    private static final String SNAPSHOT_ID = "SNAP-1";
    private static final String TRACE_ID = "TRACE-1";

    private CalculationVersionRepository versionRepository;
    private CalculationSnapshotRepository snapshotRepository;
    private CalculationProvenanceService service;

    @BeforeEach
    void setUp() {
        versionRepository = mock(CalculationVersionRepository.class);
        snapshotRepository = mock(CalculationSnapshotRepository.class);

        PermissionService permissionService = mock(PermissionService.class);
        when(permissionService.list(anyString(), any())).thenReturn(Collections.<UserScope>emptyList());
        when(permissionService.list(eq("U-ADMIN"), eq(AnalysisPerspective.BASE)))
                .thenReturn(Collections.singletonList(
                        new UserScope("U-ADMIN", "ADMIN", AnalysisPerspective.BASE, "ALL",
                                true, true, false, true)));

        AuditEventReadPolicy readPolicy = new AuditEventReadPolicy(permissionService);
        service = new CalculationProvenanceService(readPolicy, versionRepository, snapshotRepository);
    }

    @Test
    void replayByVersionReturnsFrozenSnapshotForAdmin() {
        when(versionRepository.findById(VERSION_ID)).thenReturn(Optional.of(version()));
        when(snapshotRepository.findById(SNAPSHOT_ID)).thenReturn(Optional.of(snapshot()));

        CalculationProvenance provenance = service.replayByVersion(VERSION_ID, "U-ADMIN");

        assertEquals(VERSION_ID, provenance.getVersion().getCalculationVersionId());
        assertEquals(SNAPSHOT_ID, provenance.getSnapshot().getSnapshotId());
    }

    @Test
    void replayByTraceReturnsFrozenSnapshotForAdmin() {
        when(versionRepository.findByTraceId(TRACE_ID)).thenReturn(Optional.of(version()));
        when(snapshotRepository.findById(SNAPSHOT_ID)).thenReturn(Optional.of(snapshot()));

        CalculationProvenance provenance = service.replayByTrace(TRACE_ID, "U-ADMIN");

        assertEquals(TRACE_ID, provenance.getVersion().getTraceId());
        assertEquals(SNAPSHOT_ID, provenance.getSnapshot().getSnapshotId());
    }

    @Test
    void replayRejectsNonAdminBeforeRepositoryAccess() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.replayByVersion(VERSION_ID, "U-OPS"));

        assertEquals("当前用户无审计查询权限", error.getMessage());
        verifyNoInteractions(versionRepository, snapshotRepository);
    }

    @Test
    void replayByVersionRejectsMissingVersion() {
        when(versionRepository.findById(VERSION_ID)).thenReturn(Optional.empty());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.replayByVersion(VERSION_ID, "U-ADMIN"));

        assertEquals("计算版本不存在: " + VERSION_ID, error.getMessage());
    }

    @Test
    void replayByTraceRejectsMissingTrace() {
        when(versionRepository.findByTraceId(TRACE_ID)).thenReturn(Optional.empty());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.replayByTrace(TRACE_ID, "U-ADMIN"));

        assertEquals("追踪号不存在: " + TRACE_ID, error.getMessage());
    }

    @Test
    void replayRejectsVersionWithoutSnapshot() {
        when(versionRepository.findById(VERSION_ID)).thenReturn(Optional.of(version()));
        when(snapshotRepository.findById(SNAPSHOT_ID)).thenReturn(Optional.empty());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.replayByVersion(VERSION_ID, "U-ADMIN"));

        assertEquals("计算版本缺少输入快照: " + SNAPSHOT_ID, error.getMessage());
    }

    private static CalculationVersion version() {
        return new CalculationVersion(VERSION_ID, "B-001", AnalysisPerspective.BASE, "R1",
                "JOB-1", SNAPSHOT_ID, "hash-source", "hash-config", "fingerprint",
                CalculationVersionStatus.PUBLISHED, true, null, TRACE_ID, "U-CALC",
                LocalDateTime.now(), null, null, null, null);
    }

    private static CalculationSnapshot snapshot() {
        return new CalculationSnapshot(SNAPSHOT_ID, "ORDINARY_INPUT", "ORDINARY-1",
                "{\"facts\":[]}", "[]", "hash-content", "hash-source", "R1", "U-CALC",
                LocalDateTime.now());
    }
}
