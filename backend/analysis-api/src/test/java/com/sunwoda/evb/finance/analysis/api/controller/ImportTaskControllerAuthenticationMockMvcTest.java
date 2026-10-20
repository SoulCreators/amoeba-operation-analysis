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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 导入任务创建接口的认证边界 MockMvc 覆盖。 */
class ImportTaskControllerAuthenticationMockMvcTest {
    private static final String BATCH_NO = "BATCH-2026-01";

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
    void rejectsCreateWhenPrincipalExistsButNameIsNull() throws Exception {
        // 认证主体对象存在但 getName() 为 null 时，必须在导入任务创建 Service 前拒绝请求。
        mockMvc.perform(post("/api/v1/batches/{batchNo}/imports", BATCH_NO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"datasetCode\":\"SALES\",\"fileName\":\"sales.csv\","
                                + "\"checksum\":\"sha256-demo\",\"createdBy\":\"request-user\"}")
                        .principal(principal(null)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0002"))
                .andExpect(jsonPath("$.message").value("未获取到认证用户"));

        verifyNoInteractions(service);
    }

    private static Principal principal(String name) {
        return () -> name;
    }
}
