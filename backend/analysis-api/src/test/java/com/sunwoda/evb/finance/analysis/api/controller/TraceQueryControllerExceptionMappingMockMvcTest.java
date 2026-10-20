package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.exception.ApiExceptionHandler;
import com.sunwoda.evb.finance.analysis.application.service.TraceQueryServiceImpl;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP/error-code mapping coverage for exceptions raised by the trace query service. */
class TraceQueryControllerExceptionMappingMockMvcTest {
    private static final String TRACE_PATH = "/api/v1/calculations/BATCH-001/trace";
    private static final String USER_ID = "U001";

    private TraceQueryServiceImpl service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(TraceQueryServiceImpl.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TraceQueryController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void mapsIllegalArgumentFromTraceQueryServiceToOkAndA0001() throws Exception {
        when(service.findForUser("BATCH-001", AnalysisPerspective.BASE,
                "TASK-001", USER_ID)).thenThrow(new IllegalArgumentException("trace query argument invalid"));

        mockMvc.perform(traceRequest())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0001"))
                .andExpect(jsonPath("$.message").value("trace query argument invalid"));

        verify(service).findForUser("BATCH-001", AnalysisPerspective.BASE, "TASK-001", USER_ID);
    }

    @Test
    void mapsIllegalStateFromTraceQueryServiceToForbiddenAndA0002() throws Exception {
        when(service.findForUser("BATCH-001", AnalysisPerspective.BASE,
                "TASK-001", USER_ID)).thenThrow(new IllegalStateException("当前用户无追溯明细权限"));

        mockMvc.perform(traceRequest())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0002"))
                .andExpect(jsonPath("$.message").value("当前用户无追溯明细权限"));

        verify(service).findForUser("BATCH-001", AnalysisPerspective.BASE, "TASK-001", USER_ID);
    }

    @Test
    void mapsOtherExceptionFromTraceQueryServiceToOkAndA9999() throws Exception {
        when(service.findForUser("BATCH-001", AnalysisPerspective.BASE,
                "TASK-001", USER_ID)).thenThrow(new RuntimeException("unexpected trace failure"));

        mockMvc.perform(traceRequest())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A9999"))
                .andExpect(jsonPath("$.message").value("系统处理失败"));

        verify(service).findForUser("BATCH-001", AnalysisPerspective.BASE, "TASK-001", USER_ID);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder traceRequest() {
        return get(TRACE_PATH)
                .param("perspective", "BASE")
                .param("taskNo", "TASK-001")
                .principal(principal(USER_ID));
    }

    private Principal principal(String name) {
        return () -> name;
    }
}
