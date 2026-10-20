package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.exception.ApiExceptionHandler;
import com.sunwoda.evb.finance.analysis.application.service.TraceQueryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP parameter-validation contract for TRACE-API-001. */
class TraceQueryControllerHttpContractTest {
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
    void unsupportedPerspectiveReturnsValidationErrorAndSkipsService() throws Exception {
        mockMvc.perform(get("/api/v1/calculations/BATCH-001/trace")
                        .param("perspective", "UNKNOWN")
                        .param("taskNo", "TASK-001")
                        .principal(() -> "U001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("A0001"));

        verifyNoInteractions(traceQueryService);
    }

    @Test
    void missingTaskNumberReturnsGenericApiErrorAndSkipsService() throws Exception {
        mockMvc.perform(get("/api/v1/calculations/BATCH-001/trace")
                        .param("perspective", "BASE")
                        .principal(() -> "U001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("A9999"));

        verifyNoInteractions(traceQueryService);
    }
}
