package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.exception.ApiExceptionHandler;
import com.sunwoda.evb.finance.analysis.application.service.PvmService;
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

/** HTTP error-mapping coverage for failures raised by the PVM service. */
class PvmControllerMockMvcTest {
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
    void mapsIllegalArgumentFromPvmServiceToApiError() throws Exception {
        when(service.analyze(BATCH_NO, USER_ID))
                .thenThrow(new IllegalArgumentException("批次不存在: BATCH-001"));

        mockMvc.perform(get("/api/v1/pvm/{batchNo}", BATCH_NO).principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0001"))
                .andExpect(jsonPath("$.message").value("批次不存在: BATCH-001"));

        verify(service).analyze(BATCH_NO, USER_ID);
    }

    @Test
    void mapsIllegalStateFromPvmServiceToForbiddenApiErrorWhenPermissionDenied() throws Exception {
        when(service.analyze(BATCH_NO, USER_ID))
                .thenThrow(new IllegalStateException("当前用户无PVM结果查看权限"));

        mockMvc.perform(get("/api/v1/pvm/{batchNo}", BATCH_NO).principal(principal(USER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0002"))
                .andExpect(jsonPath("$.message").value("当前用户无PVM结果查看权限"));

        verify(service).analyze(BATCH_NO, USER_ID);
    }

    @Test
    void mapsUnexpectedPvmFailureToGenericApiError() throws Exception {
        when(service.analyze(BATCH_NO, USER_ID)).thenThrow(new RuntimeException("unexpected failure"));

        mockMvc.perform(get("/api/v1/pvm/{batchNo}", BATCH_NO).principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A9999"))
                .andExpect(jsonPath("$.message").value("系统处理失败"));

        verify(service).analyze(BATCH_NO, USER_ID);
    }

    private static Principal principal(String name) {
        return () -> name;
    }
}
