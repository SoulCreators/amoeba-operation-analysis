package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.dto.CalculateRequest;
import com.sunwoda.evb.finance.analysis.api.dto.ResultLifecycleRequest;
import com.sunwoda.evb.finance.analysis.application.service.CalculationService;
import com.sunwoda.evb.finance.analysis.common.ApiResult;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CalculationControllerCommandContractTest {
    private static final String BATCH_NO = "BATCH-001";
    private static final String TRUSTED_PRINCIPAL = "trusted-user";

    private CalculationService service;
    private CalculationController controller;

    @BeforeEach
    void setUp() {
        service = mock(CalculationService.class);
        controller = new CalculationController(service);
    }

    @Test
    void calculateUsesBatchNoAndAuthenticatedPrincipalForResultLookup() {
        CalculateRequest request = new CalculateRequest();
        request.setBatchNo(BATCH_NO);
        CalculationResult expected = result();
        when(service.getForUser(BATCH_NO, TRUSTED_PRINCIPAL)).thenReturn(expected);

        ApiResult<CalculationResult> actual = controller.calculate(request, principal());

        verify(service).calculate(BATCH_NO);
        verify(service).getForUser(BATCH_NO, TRUSTED_PRINCIPAL);
        assertSuccessfulResult(actual, expected);
    }

    @Test
    void confirmUsesBatchNoAndAuthenticatedPrincipal() {
        ResultLifecycleRequest request = lifecycleRequest("untrusted-request-operator");
        CalculationResult expected = result();
        when(service.confirm(BATCH_NO, TRUSTED_PRINCIPAL)).thenReturn(expected);

        ApiResult<CalculationResult> actual = controller.confirm(request, principal());

        verify(service).confirm(BATCH_NO, TRUSTED_PRINCIPAL);
        assertSuccessfulResult(actual, expected);
    }

    @Test
    void publishUsesBatchNoAndAuthenticatedPrincipal() {
        ResultLifecycleRequest request = lifecycleRequest("untrusted-request-operator");
        CalculationResult expected = result();
        when(service.publish(BATCH_NO, TRUSTED_PRINCIPAL)).thenReturn(expected);

        ApiResult<CalculationResult> actual = controller.publish(request, principal());

        verify(service).publish(BATCH_NO, TRUSTED_PRINCIPAL);
        assertSuccessfulResult(actual, expected);
    }

    private Principal principal() {
        return () -> TRUSTED_PRINCIPAL;
    }

    private ResultLifecycleRequest lifecycleRequest(String operator) {
        ResultLifecycleRequest request = new ResultLifecycleRequest();
        request.setBatchNo(BATCH_NO);
        request.setOperator(operator);
        return request;
    }

    private CalculationResult result() {
        return new CalculationResult(BATCH_NO, AnalysisPerspective.BASE,
                Collections.emptyList());
    }

    private void assertSuccessfulResult(ApiResult<CalculationResult> actual,
                                        CalculationResult expected) {
        assertTrue(actual.isSuccess());
        assertEquals("OK", actual.getCode());
        assertSame(expected, actual.getData());
    }
}
