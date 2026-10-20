package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.application.service.BatchReadinessService;
import com.sunwoda.evb.finance.analysis.common.ApiResult;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.BatchReadiness;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** READINESS-API-001 API contract; authentication is distinct from permission and business readiness. */
class BatchReadinessControllerContractTest {
    private BatchReadinessService service;
    private BatchReadinessController controller;

    @BeforeEach
    void setUp() {
        service = mock(BatchReadinessService.class);
        controller = new BatchReadinessController(service);
    }

    @Test
    void authenticatedUserReceivesBlockedBusinessStateWithoutRoleOrFormulaEvaluation() {
        BatchReadiness blocked = new BatchReadiness("BATCH-001", AnalysisPerspective.BASE, false,
                Collections.emptyList());
        when(service.get("BATCH-001")).thenReturn(blocked);

        ApiResult<BatchReadiness> result = controller.get("BATCH-001", () -> "authenticated-user");

        assertTrue(result.isSuccess());
        assertFalse(result.getData().isReadyForCalculation());
        assertSame(blocked, result.getData());
        verify(service).get("BATCH-001");
    }
}
