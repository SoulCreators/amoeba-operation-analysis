package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.exception.ApiExceptionHandler;
import com.sunwoda.evb.finance.analysis.application.service.ImportTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 导入任务创建 Service 异常 HTTP 契约测试，固定当前 A0002/A9999 包络行为。 */
class ImportTaskControllerServiceExceptionMockMvcTest {
    private static final String BATCH_NO = "BATCH-2026-01";
    private static final String USER_ID = "finance-user";
    private static final String REQUEST_JSON = "{\"datasetCode\":\"SALES\",\"fileName\":\"sales.csv\","
            + "\"checksum\":\"sha256-demo\",\"createdBy\":\"request-user\"}";

    private ImportTaskService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(ImportTaskService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ImportTaskController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void mapsServiceIllegalStateExceptionToCurrentA0002Envelope() throws Exception {
        String message = "导入任务状态不允许创建";
        when(service.create(BATCH_NO, "SALES", "sales.csv", "sha256-demo", USER_ID))
                .thenThrow(new IllegalStateException(message));

        // 本次新增契约验证 Service 的业务状态异常返回 HTTP 200、A0002 和原始消息。
        mockMvc.perform(post("/api/v1/batches/{batchNo}/imports", BATCH_NO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_JSON)
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0002"))
                .andExpect(jsonPath("$.message").value(message));

        // 本次新增契约确认异常发生前请求字段和认证主体已透传给 ImportTaskService。
        verify(service).create(BATCH_NO, "SALES", "sales.csv", "sha256-demo", USER_ID);
    }

    @Test
    void mapsServiceRuntimeExceptionToCurrentA9999Envelope() throws Exception {
        when(service.create(BATCH_NO, "SALES", "sales.csv", "sha256-demo", USER_ID))
                .thenThrow(new RuntimeException("数据库连接中断"));

        // 本次新增契约验证 Service 的未知异常返回 HTTP 200、A9999 和统一系统失败消息。
        mockMvc.perform(post("/api/v1/batches/{batchNo}/imports", BATCH_NO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_JSON)
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A9999"))
                .andExpect(jsonPath("$.message").value("系统处理失败"));

        // 本次新增契约确认未知异常仍由已绑定的请求调用一次 Service，不绕过业务层。
        verify(service).create(BATCH_NO, "SALES", "sales.csv", "sha256-demo", USER_ID);
    }

    private static Principal principal(String name) {
        return () -> name;
    }
}
