package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.exception.ApiExceptionHandler;
import com.sunwoda.evb.finance.analysis.application.service.ImportTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 导入任务创建 Service 参数异常 HTTP 契约测试，固定当前 A0001 错误包络行为。 */
class ImportTaskControllerServiceIllegalArgumentMockMvcTest {
    private static final String BATCH_NO = "BATCH-2026-01";
    private static final String USER_ID = "finance-user";
    private static final String ERROR_MESSAGE = "导入任务参数校验失败";

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
    void createMapsServiceIllegalArgumentToA0001AndPassesAllArguments() throws Exception {
        when(service.create(BATCH_NO, "SALES", "sales.csv", "sha256-demo", USER_ID))
                .thenThrow(new IllegalArgumentException(ERROR_MESSAGE));

        // 本次新增契约验证 Service 参数异常保持 HTTP 200、A0001、消息透传和空数据包络。
        mockMvc.perform(post("/api/v1/batches/{batchNo}/imports", BATCH_NO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"datasetCode\":\"SALES\",\"fileName\":\"sales.csv\","
                                + "\"checksum\":\"sha256-demo\",\"createdBy\":\"request-user\"}")
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.code").value("A0001"))
                .andExpect(jsonPath("$.message").value(ERROR_MESSAGE));

        // 本次新增契约确认异常前 Controller 仍按路径、请求体和认证主体调用一次 Service，且不再产生额外交互。
        verify(service).create(BATCH_NO, "SALES", "sales.csv", "sha256-demo", USER_ID);
        verifyNoMoreInteractions(service);
    }

    private static Principal principal(String name) {
        return () -> name;
    }
}
