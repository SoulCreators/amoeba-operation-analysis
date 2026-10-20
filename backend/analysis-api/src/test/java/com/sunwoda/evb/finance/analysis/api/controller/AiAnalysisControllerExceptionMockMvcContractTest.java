package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.exception.ApiExceptionHandler;
import com.sunwoda.evb.finance.analysis.application.service.AiAnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.nullValue;

/** AI 分析 Controller 异常 HTTP 契约测试，固定当前 A0001/A0002/A9999 包络行为。 */
class AiAnalysisControllerExceptionMockMvcContractTest {
    private static final String BATCH_NO = "BATCH-AI-2026-01";
    private static final String USER_ID = "finance-user";

    private AiAnalysisService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(AiAnalysisService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AiAnalysisController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void mapsServiceIllegalArgumentExceptionToCurrentA0001Envelope() throws Exception {
        String message = "批次不存在，无法生成AI分析";
        when(service.generate(BATCH_NO, USER_ID))
                .thenThrow(new IllegalArgumentException(message));

        // 本次新增契约验证 AI Service 的参数异常返回 HTTP 200、A0001 和原始业务消息。
        mockMvc.perform(post("/api/v1/ai/analysis/{batchNo}", BATCH_NO)
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0001"))
                .andExpect(jsonPath("$.message").value(message));

        // 本次新增契约确认批次号和认证用户按当前 Controller 规则透传给 AI Service。
        verify(service).generate(BATCH_NO, USER_ID);
    }

    @Test
    void mapsServiceBusinessIllegalStateExceptionToCurrentA0002Envelope() throws Exception {
        String message = "当前结果状态不允许生成AI分析";
        when(service.generate(BATCH_NO, USER_ID))
                .thenThrow(new IllegalStateException(message));

        // 本次新增契约验证 AI Service 的业务状态异常返回 HTTP 200、A0002 和原始消息。
        mockMvc.perform(post("/api/v1/ai/analysis/{batchNo}", BATCH_NO)
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0002"))
                .andExpect(jsonPath("$.message").value(message));

        // 本次新增契约确认业务状态异常发生前已调用一次 AI Service，且参数未被 Controller 改写。
        verify(service).generate(BATCH_NO, USER_ID);
    }

    @Test
    void mapsServiceUnknownExceptionToCurrentA9999Envelope() throws Exception {
        when(service.generate(BATCH_NO, USER_ID))
                .thenThrow(new RuntimeException("AI 网关连接中断"));

        // 本次新增契约验证 AI Service 的未知异常返回 HTTP 200、A9999 和统一系统失败消息。
        mockMvc.perform(post("/api/v1/ai/analysis/{batchNo}", BATCH_NO)
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A9999"))
                .andExpect(jsonPath("$.message").value("系统处理失败"));

        // 本次新增契约确认未知异常仍通过当前 AI Service 入口处理一次，不绕过业务层。
        verify(service).generate(BATCH_NO, USER_ID);
    }

    private static Principal principal(String name) {
        return () -> name;
    }
}
