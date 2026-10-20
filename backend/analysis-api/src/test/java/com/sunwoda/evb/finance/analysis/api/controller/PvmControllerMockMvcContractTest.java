package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.exception.ApiExceptionHandler;
import com.sunwoda.evb.finance.analysis.application.service.PvmService;
import com.sunwoda.evb.finance.analysis.domain.model.PvmResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.Collections;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MockMvc contract coverage for the PVM endpoint authentication and response envelope. */
class PvmControllerMockMvcContractTest {
    private static final String BATCH_NO = "BATCH-001";
    private static final String USER_ID = "U001";

    private PvmService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(PvmService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new PvmController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void passesAuthenticatedPrincipalToServiceAndReturnsSuccessfulJsonEnvelope() throws Exception {
        PvmResult result = new PvmResult(
                "BASE-SZ",
                "PRICE_VARIANCE",
                Collections.singletonMap("amount", new BigDecimal("12.50")));
        when(service.analyze(BATCH_NO, USER_ID)).thenReturn(Collections.singletonList(result));

        mockMvc.perform(get("/api/v1/pvm/" + BATCH_NO).principal(() -> USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("成功"))
                .andExpect(jsonPath("$.data[0].scopeCode").value("BASE-SZ"))
                .andExpect(jsonPath("$.data[0].logicCode").value("PRICE_VARIANCE"))
                .andExpect(jsonPath("$.data[0].values.amount").value(12.50));

        verify(service).analyze(BATCH_NO, USER_ID);
    }

    @Test
    void anonymousOrEmptyPrincipalReturns401A0002AndSkipsService() throws Exception {
        assertUnauthenticated(get("/api/v1/pvm/" + BATCH_NO));
        assertUnauthenticated(get("/api/v1/pvm/" + BATCH_NO).principal(() -> ""));
        assertUnauthenticated(get("/api/v1/pvm/" + BATCH_NO).principal(() -> "   "));

        verifyNoInteractions(service);
    }

    private void assertUnauthenticated(RequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0002"))
                .andExpect(jsonPath("$.message")
                        .value("未获取到认证用户，拒绝返回PVM结果"));
    }
}
