package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.exception.ApiExceptionHandler;
import com.sunwoda.evb.finance.analysis.application.service.TraceQueryServiceImpl;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.PnlFactSource;
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

/** MockMvc contract coverage for the trace query authentication and envelope. */
class TraceQueryControllerMockMvcContractTest {
    private static final String BATCH_NO = "BATCH-001";
    private static final String TASK_NO = "TASK-001";
    private static final String USER_ID = "U001";

    private TraceQueryServiceImpl traceQueryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        traceQueryService = mock(TraceQueryServiceImpl.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TraceQueryController(traceQueryService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void authenticatedTraceQueryPassesPrincipalToServiceAndReturnsSuccessfulEnvelope() throws Exception {
        PnlFactSource source = new PnlFactSource(
                TASK_NO, "source.xlsx", "checksum", "Sheet1", 2,
                "NANCHANG", "BASE-001", "PRODUCT-001", "LINE-001");
        when(traceQueryService.findForUser(BATCH_NO, AnalysisPerspective.BASE, TASK_NO, USER_ID))
                .thenReturn(Collections.singletonList(source));

        mockMvc.perform(get("/api/v1/calculations/{batchNo}/trace", BATCH_NO)
                        .param("perspective", "BASE")
                        .param("taskNo", TASK_NO)
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("成功"))
                .andExpect(jsonPath("$.data[0].taskNo").value(TASK_NO))
                .andExpect(jsonPath("$.data[0].fileName").value("source.xlsx"));

        verify(traceQueryService).findForUser(BATCH_NO, AnalysisPerspective.BASE, TASK_NO, USER_ID);
    }

    @Test
    void anonymousOrBlankPrincipalIsRejectedBeforeServiceInvocation() throws Exception {
        assertUnauthenticated(traceRequest(null));
        // 补充认证主体存在但名称为 null 的 HTTP 门禁回归，确保追溯 Service 不被调用。
        assertUnauthenticated(traceRequest(principal(null)));
        assertUnauthenticated(traceRequest(principal("")));
        assertUnauthenticated(traceRequest(principal("   ")));

        verifyNoInteractions(traceQueryService);
    }

    private void assertUnauthenticated(RequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0002"))
                .andExpect(jsonPath("$.message").value("未获取到认证用户"));
    }

    private RequestBuilder traceRequest(Principal principal) {
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request = get(
                "/api/v1/calculations/{batchNo}/trace", BATCH_NO)
                .param("perspective", "BASE")
                .param("taskNo", TASK_NO);
        return principal == null ? request : request.principal(principal);
    }

    private Principal principal(String name) {
        return () -> name;
    }
}
