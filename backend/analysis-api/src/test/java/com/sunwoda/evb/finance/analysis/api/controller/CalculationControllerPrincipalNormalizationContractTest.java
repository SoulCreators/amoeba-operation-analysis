package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.dto.CalculateRequest;
import com.sunwoda.evb.finance.analysis.api.dto.ResultLifecycleRequest;
import com.sunwoda.evb.finance.analysis.application.service.CalculationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.Principal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证 CalculationController 对认证 Principal 用户名的既有 trim 约定。 */
class CalculationControllerPrincipalNormalizationContractTest {
    private static final String BATCH_NO = "BATCH-001";
    private static final String USER_ID = "U001";

    private CalculationService service;
    private CalculationController controller;

    @BeforeEach
    void setUp() {
        service = mock(CalculationService.class);
        controller = new CalculationController(service);
    }

    @Test
    void getTrimsPrincipalUsernameBeforeUserScopedLookup() {
        when(service.getForUser(BATCH_NO, USER_ID)).thenReturn(null);

        controller.get(BATCH_NO, principal("  " + USER_ID + "  "));

        verify(service).getForUser(BATCH_NO, USER_ID);
    }

    @Test
    void calculateConfirmAndPublishTrimPrincipalUsernameThroughSharedUserBoundary() {
        when(service.getForUser(BATCH_NO, USER_ID)).thenReturn(null);
        when(service.confirm(BATCH_NO, USER_ID)).thenReturn(null);
        when(service.publish(BATCH_NO, USER_ID)).thenReturn(null);

        CalculateRequest calculateRequest = new CalculateRequest();
        calculateRequest.setBatchNo(BATCH_NO);
        ResultLifecycleRequest lifecycleRequest = new ResultLifecycleRequest();
        lifecycleRequest.setBatchNo(BATCH_NO);

        controller.calculate(calculateRequest, principal("  " + USER_ID + "  "));
        controller.confirm(lifecycleRequest, principal("  " + USER_ID + "  "));
        controller.publish(lifecycleRequest, principal("  " + USER_ID + "  "));

        verify(service).calculate(BATCH_NO, USER_ID);
        verify(service).getForUser(BATCH_NO, USER_ID);
        verify(service).confirm(BATCH_NO, USER_ID);
        verify(service).publish(BATCH_NO, USER_ID);
    }

    private static Principal principal(String name) {
        return () -> name;
    }
}
