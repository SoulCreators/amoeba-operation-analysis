package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.exception.ApiExceptionHandler;
import com.sunwoda.evb.finance.analysis.application.service.AuditEventReadPolicy;
import com.sunwoda.evb.finance.analysis.application.service.CalculationProvenanceService;
import com.sunwoda.evb.finance.analysis.application.service.PermissionService;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationSnapshot;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationVersion;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationVersionStatus;
import com.sunwoda.evb.finance.analysis.domain.model.UserScope;
import com.sunwoda.evb.finance.analysis.domain.repository.CalculationSnapshotRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.CalculationVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 历史上游配置重放只读 HTTP 契约：认证边界与审计门禁映射。 */
class CalculationProvenanceControllerMockMvcContractTest {
    private static final String ADMIN_USER = "U-ADMIN";
    private static final String NON_ADMIN_USER = "U-OPS";
    private static final String VERSION_ID = "CALC-1";
    private static final String SNAPSHOT_ID = "SNAP-1";
    private static final String TRACE_ID = "TRACE-1";

    private CalculationVersionRepository versionRepository;
    private CalculationSnapshotRepository snapshotRepository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        versionRepository = mock(CalculationVersionRepository.class);
        snapshotRepository = mock(CalculationSnapshotRepository.class);

        PermissionService permissionService = mock(PermissionService.class);
        when(permissionService.list(anyString(), any(AnalysisPerspective.class))).thenAnswer(invocation ->
                ADMIN_USER.equals(invocation.getArgument(0))
                        ? Collections.singletonList(adminGrant()) : Collections.emptyList());

        CalculationProvenanceService service = new CalculationProvenanceService(
                new AuditEventReadPolicy(permissionService), versionRepository, snapshotRepository);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CalculationProvenanceController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void byVersionUsesAuthenticatedPrincipalAndReachesService() throws Exception {
        when(versionRepository.findById(VERSION_ID)).thenReturn(Optional.of(version()));
        when(snapshotRepository.findById(SNAPSHOT_ID)).thenReturn(Optional.of(snapshot()));

        mockMvc.perform(get("/api/v1/calculation-provenance/by-version/{id}", VERSION_ID)
                        .principal(principal("  " + ADMIN_USER + "  ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.version.calculationVersionId").value(VERSION_ID))
                .andExpect(jsonPath("$.data.snapshot.snapshotId").value(SNAPSHOT_ID));
    }

    @Test
    void byTraceUsesAuthenticatedPrincipalAndReachesService() throws Exception {
        when(versionRepository.findByTraceId(TRACE_ID)).thenReturn(Optional.of(version()));
        when(snapshotRepository.findById(SNAPSHOT_ID)).thenReturn(Optional.of(snapshot()));

        mockMvc.perform(get("/api/v1/calculation-provenance/by-trace/{trace}", TRACE_ID)
                        .principal(principal(ADMIN_USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.version.traceId").value(TRACE_ID));
    }

    @Test
    void anonymousRejectedBeforeServiceAccess() throws Exception {
        mockMvc.perform(get("/api/v1/calculation-provenance/by-version/{id}", VERSION_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A0002"));
        verifyNoInteractions(versionRepository, snapshotRepository);
    }

    @Test
    void nonAdminRejectedAsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/calculation-provenance/by-version/{id}", VERSION_ID)
                        .principal(principal(NON_ADMIN_USER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("A0002"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.message").value("当前用户无审计查询权限"));
        verifyNoInteractions(versionRepository, snapshotRepository);
    }

    private Principal principal(String name) {
        return () -> name;
    }

    private UserScope adminGrant() {
        return new UserScope(ADMIN_USER, "ADMIN", AnalysisPerspective.BASE,
                "ALL", true, true, false, true);
    }

    private CalculationVersion version() {
        return new CalculationVersion(VERSION_ID, "B-001",
                AnalysisPerspective.BASE, "R1", "JOB-1", SNAPSHOT_ID, "hash-source", "hash-config",
                "fingerprint", CalculationVersionStatus.PUBLISHED, true, null, TRACE_ID, "U-CALC",
                LocalDateTime.now(), null, null, null, null);
    }

    private CalculationSnapshot snapshot() {
        return new CalculationSnapshot(SNAPSHOT_ID, "ORDINARY_INPUT",
                "ORDINARY-1", "{\"facts\":[]}", "[]", "hash-content", "hash-source", "R1",
                "U-CALC", LocalDateTime.now());
    }
}
