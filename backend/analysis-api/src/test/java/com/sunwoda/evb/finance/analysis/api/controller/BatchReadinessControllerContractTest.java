package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.application.service.BatchReadinessService;
import com.sunwoda.evb.finance.analysis.application.service.BatchService;
import com.sunwoda.evb.finance.analysis.application.service.PermissionService;
import com.sunwoda.evb.finance.analysis.common.ApiResult;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.BatchReadiness;
import com.sunwoda.evb.finance.analysis.domain.model.BatchStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

/** READINESS-API-001 API contract; authentication is distinct from permission and business readiness. */
class BatchReadinessControllerContractTest {
    private BatchReadinessService service;
    private BatchService batchService;
    private PermissionService permissionService;
    private BatchReadinessController controller;

    @BeforeEach
    void setUp() {
        service = mock(BatchReadinessService.class);
        batchService = mock(BatchService.class);
        permissionService = mock(PermissionService.class);
        controller = new BatchReadinessController(service, batchService, permissionService);
    }

    @Test
    void authenticatedUserReceivesBlockedBusinessStateWithoutRoleOrFormulaEvaluation() {
        BatchReadiness blocked = new BatchReadiness("BATCH-001", AnalysisPerspective.BASE, false,
                Collections.emptyList());
        when(service.get("BATCH-001")).thenReturn(blocked);
        when(batchService.get("BATCH-001")).thenReturn(new AnalysisBatch(1L, "BATCH-001", "2026-08",
                AnalysisPerspective.BASE, BatchStatus.DRAFT, "authenticated-user", LocalDateTime.now()));
        when(permissionService.canViewScope("authenticated-user", AnalysisPerspective.BASE, "ALL"))
                .thenReturn(true);

        ApiResult<BatchReadiness> result = controller.get("BATCH-001", () -> "authenticated-user");

        assertTrue(result.isSuccess());
        assertFalse(result.getData().isReadyForCalculation());
        assertSame(blocked, result.getData());
        verify(service).get("BATCH-001");
        verify(batchService).get("BATCH-001");
    }
}
