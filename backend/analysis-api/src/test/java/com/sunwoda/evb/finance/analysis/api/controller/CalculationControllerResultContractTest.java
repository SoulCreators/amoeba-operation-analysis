package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.application.service.CalculationService;
import com.sunwoda.evb.finance.analysis.common.ApiResult;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CalculationControllerResultContractTest {
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
    void getUsesAuthenticatedPrincipalAndPreservesSuccessfulApiResultEnvelope() {
        CalculationResult expected = mock(CalculationResult.class);
        when(service.getForUser(BATCH_NO, USER_ID)).thenReturn(expected);

        ApiResult<CalculationResult> actual = controller.get(BATCH_NO, principal(USER_ID));

        assertTrue(actual.isSuccess());
        assertSame(expected, actual.getData());
        assertEquals("OK", actual.getCode());
        assertEquals("成功", actual.getMessage());
        verify(service).getForUser(BATCH_NO, USER_ID);
    }

    @Test
    void getKeepsSuccessfulApiResultEnvelopeWhenServiceReturnsNull() {
        when(service.getForUser(BATCH_NO, USER_ID)).thenReturn(null);

        ApiResult<CalculationResult> actual = controller.get(BATCH_NO, principal(USER_ID));

        assertTrue(actual.isSuccess());
        assertNull(actual.getData());
        assertEquals("OK", actual.getCode());
        assertEquals("成功", actual.getMessage());
        verify(service).getForUser(BATCH_NO, USER_ID);
    }

    @Test
    void getPreservesServiceExceptionInsteadOfInventingAnotherApiResult() {
        IllegalStateException expected = new IllegalStateException("计算结果读取失败");
        when(service.getForUser(BATCH_NO, USER_ID)).thenThrow(expected);

        IllegalStateException actual = assertThrows(IllegalStateException.class,
                () -> controller.get(BATCH_NO, principal(USER_ID)));

        assertSame(expected, actual);
        verify(service).getForUser(BATCH_NO, USER_ID);
    }

    private static Principal principal(String userId) {
        return () -> userId;
    }
}
