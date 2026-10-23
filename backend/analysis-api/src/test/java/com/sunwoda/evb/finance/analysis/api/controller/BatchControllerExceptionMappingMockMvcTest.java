package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.exception.ApiExceptionHandler;
import com.sunwoda.evb.finance.analysis.application.service.BatchService;
import com.sunwoda.evb.finance.analysis.application.service.PermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP/error-code mapping coverage for the batch controller's existing contracts. */
class BatchControllerExceptionMappingMockMvcTest {
    private static final String BATCH_NO = "BATCH-001";
    private static final String USER_ID = "U001";

    private BatchService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(BatchService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new BatchController(service, mock(PermissionService.class)))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void rejectsAnonymousCreateWithUnauthorizedA0002() throws Exception {
        mockMvc.perform(createRequest(null, "BASE"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0002"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsBlankCreateWithUnauthorizedA0002() throws Exception {
        mockMvc.perform(createRequest(principal("   "), "BASE"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0002"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsNullNameCreateWithUnauthorizedA0002() throws Exception {
        // 认证主体存在但用户名为 null 时，仍须在进入批次服务前拒绝请求。
        mockMvc.perform(createRequest(principal(null), "BASE"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0002"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsAnonymousReadWithUnauthorizedA0002() throws Exception {
        mockMvc.perform(get("/api/v1/batches/{batchNo}", BATCH_NO))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0002"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsBlankReadWithUnauthorizedA0002() throws Exception {
        mockMvc.perform(get("/api/v1/batches/{batchNo}", BATCH_NO)
                        .principal(principal("")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0002"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsNullNameReadWithUnauthorizedA0002() throws Exception {
        // 查询入口对 null 用户名保持与匿名、空用户名相同的认证边界。
        mockMvc.perform(get("/api/v1/batches/{batchNo}", BATCH_NO)
                        .principal(principal(null)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0002"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsAnonymousMoveWithUnauthorizedA0002() throws Exception {
        mockMvc.perform(moveRequest(null, "READY_FOR_CALCULATION"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0002"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsBlankMoveWithUnauthorizedA0002() throws Exception {
        mockMvc.perform(moveRequest(principal("  "), "READY_FOR_CALCULATION"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0002"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsNullNameMoveWithUnauthorizedA0002() throws Exception {
        // 状态流转入口拒绝 null 用户名，避免未认证主体触发业务服务。
        mockMvc.perform(moveRequest(principal(null), "READY_FOR_CALCULATION"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0002"));

        verifyNoInteractions(service);
    }

    @Test
    void mapsIllegalPerspectiveToOkAndA0001() throws Exception {
        mockMvc.perform(createRequest(principal(USER_ID), "UNKNOWN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0001"))
                .andExpect(jsonPath("$.message").value("不支持的数据视角: UNKNOWN"));

        verifyNoInteractions(service);
    }

    @Test
    void mapsIllegalStatusToOkAndA0001() throws Exception {
        mockMvc.perform(moveRequest(principal(USER_ID), "UNKNOWN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0001"))
                .andExpect(jsonPath("$.message").value(containsString("No enum constant")));

        verifyNoInteractions(service);
    }

    @Test
    void mapsIllegalArgumentFromBatchServiceToOkAndA0001() throws Exception {
        when(service.get(BATCH_NO)).thenThrow(new IllegalArgumentException("批次不存在: BATCH-001"));

        mockMvc.perform(get("/api/v1/batches/{batchNo}", BATCH_NO)
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0001"))
                .andExpect(jsonPath("$.message").value("批次不存在: BATCH-001"));

        verify(service).get(BATCH_NO);
    }

    @Test
    void mapsUnexpectedBatchServiceFailureToOkAndA9999() throws Exception {
        when(service.get(BATCH_NO)).thenThrow(new RuntimeException("unexpected batch failure"));

        mockMvc.perform(get("/api/v1/batches/{batchNo}", BATCH_NO)
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A9999"))
                .andExpect(jsonPath("$.message").value("系统处理失败"));

        verify(service).get(BATCH_NO);
    }

    private MockHttpServletRequestBuilder createRequest(Principal principal, String perspective) {
        MockHttpServletRequestBuilder request = post("/api/v1/batches")
                .contentType(APPLICATION_JSON)
                .content("{\"batchNo\":\"BATCH-001\",\"period\":\"2026-01\",\"perspective\":\""
                        + perspective + "\"}");
        return principal == null ? request : request.principal(principal);
    }

    private MockHttpServletRequestBuilder moveRequest(Principal principal, String status) {
        MockHttpServletRequestBuilder request = post("/api/v1/batches/{batchNo}/status", BATCH_NO)
                .contentType(APPLICATION_JSON)
                .content("{\"status\":\"" + status + "\"}");
        return principal == null ? request : request.principal(principal);
    }

    private static Principal principal(String name) {
        return () -> name;
    }
}
