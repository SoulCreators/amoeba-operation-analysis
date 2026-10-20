package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.exception.ApiExceptionHandler;
import com.sunwoda.evb.finance.analysis.application.service.ImportTaskService;
import com.sunwoda.evb.finance.analysis.domain.model.ImportTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.time.LocalDateTime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 导入任务创建成功 HTTP 契约测试，固定请求绑定与认证主体传递行为。 */
class ImportTaskControllerCreateMockMvcContractTest {
    private static final String BATCH_NO = "BATCH-2026-01";
    private static final String USER_ID = "finance-user";

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
    void createReturnsSuccessfulEnvelopeBindsRequestFieldsAndUsesPrincipalUser() throws Exception {
        ImportTask task = new ImportTask(1L, "TASK-2026-01", BATCH_NO,
                "SALES", "sales.csv", "sha256-demo", USER_ID,
                LocalDateTime.of(2026, 10, 20, 10, 0));
        when(service.create(BATCH_NO, "SALES", "sales.csv", "sha256-demo", USER_ID))
                .thenReturn(task);

        // 本次新增契约验证请求字段透传，并确认请求体 createdBy 不能覆盖认证主体。
        mockMvc.perform(post("/api/v1/batches/{batchNo}/imports", BATCH_NO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"datasetCode\":\"SALES\",\"fileName\":\"sales.csv\","
                                + "\"checksum\":\"sha256-demo\",\"createdBy\":\"request-user\"}")
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("成功"))
                .andExpect(jsonPath("$.data.taskNo").value("TASK-2026-01"))
                .andExpect(jsonPath("$.data.batchNo").value(BATCH_NO))
                .andExpect(jsonPath("$.data.datasetCode").value("SALES"))
                .andExpect(jsonPath("$.data.fileName").value("sales.csv"))
                .andExpect(jsonPath("$.data.checksum").value("sha256-demo"))
                .andExpect(jsonPath("$.data.createdBy").value(USER_ID))
                .andExpect(jsonPath("$.data.status").value("CREATED"))
                .andExpect(jsonPath("$.data.issueCount").value(0));

        // 本次新增契约确认 Controller 使用路径 batchNo、请求字段和 Principal 用户调用 Service。
        verify(service).create(BATCH_NO, "SALES", "sales.csv", "sha256-demo", USER_ID);
    }

    private static Principal principal(String name) {
        return () -> name;
    }
}
