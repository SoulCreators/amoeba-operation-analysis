package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.exception.ApiExceptionHandler;
import com.sunwoda.evb.finance.analysis.application.service.BatchReadinessService;
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

/** HTTP/error-code mapping coverage for failures raised by the batch readiness service. */
class BatchReadinessControllerExceptionMappingMockMvcTest {
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
    void mapsIllegalArgumentFromBatchReadinessServiceToOkAndA0001() throws Exception {
        when(service.get(BATCH_NO)).thenThrow(new IllegalArgumentException("批次不存在: BATCH-001"));

        mockMvc.perform(readinessRequest())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0001"))
                .andExpect(jsonPath("$.message").value("批次不存在: BATCH-001"));

        verify(service).get(BATCH_NO);
    }

    @Test
    void mapsUnexpectedBatchReadinessFailureToOkAndA9999() throws Exception {
        when(service.get(BATCH_NO)).thenThrow(new RuntimeException("unexpected readiness failure"));

        mockMvc.perform(readinessRequest())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A9999"))
                .andExpect(jsonPath("$.message").value("系统处理失败"));

        verify(service).get(BATCH_NO);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder readinessRequest() {
        return get("/api/v1/batches/{batchNo}/readiness", BATCH_NO)
                .principal(principal(USER_ID));
    }

    private static Principal principal(String name) {
        return () -> name;
    }
}
