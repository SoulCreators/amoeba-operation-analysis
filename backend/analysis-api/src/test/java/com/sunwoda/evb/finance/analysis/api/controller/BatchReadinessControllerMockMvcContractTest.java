package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.exception.ApiExceptionHandler;
import com.sunwoda.evb.finance.analysis.application.service.BatchReadinessService;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.BatchReadiness;
import com.sunwoda.evb.finance.analysis.domain.model.DatasetReadiness;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.util.Collections;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MockMvc contract coverage for READINESS-API-001. */
class BatchReadinessControllerMockMvcContractTest {
    private static final String BATCH_NO = "BATCH-001";
    private static final String USER_ID = "U001";

    private BatchReadinessService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(BatchReadinessService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new BatchReadinessController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void authenticatedRequestReturnsSuccessfulEnvelopeAndPassesBatchNoToService() throws Exception {
        BatchReadiness readiness = new BatchReadiness(
                BATCH_NO,
                AnalysisPerspective.BASE,
                true,
                Collections.singletonList(new DatasetReadiness(
                        "SALES", "销售数据", true, true, "READY", 0, "TASK-001")));
        when(service.get(BATCH_NO)).thenReturn(readiness);

        mockMvc.perform(get("/api/v1/batches/{batchNo}/readiness", BATCH_NO)
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("成功"))
                .andExpect(jsonPath("$.data.batchNo").value(BATCH_NO))
                .andExpect(jsonPath("$.data.perspective").value("BASE"))
                .andExpect(jsonPath("$.data.readyForCalculation").value(true))
                .andExpect(jsonPath("$.data.datasets[0].datasetCode").value("SALES"))
                .andExpect(jsonPath("$.data.datasets[0].taskNo").value("TASK-001"));

        verify(service).get(BATCH_NO);
    }

    @Test
    void anonymousOrBlankPrincipalReturns401AndDoesNotCallService() throws Exception {
        assertUnauthenticated(readinessRequest(null));
        // 本次认证边界补充：Principal 对象存在但 getName() 返回 null 时仍应返回 401/A0002。
        assertUnauthenticated(readinessRequest(principal(null)));
        assertUnauthenticated(readinessRequest(principal("")));
        assertUnauthenticated(readinessRequest(principal("   ")));

        verifyNoInteractions(service);
    }

    private void assertUnauthenticated(RequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0002"));
    }

    private RequestBuilder readinessRequest(Principal principal) {
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request = get(
                "/api/v1/batches/{batchNo}/readiness", BATCH_NO);
        return principal == null ? request : request.principal(principal);
    }

    private Principal principal(String name) {
        return () -> name;
    }
}
