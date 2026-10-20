package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.exception.ApiExceptionHandler;
import com.sunwoda.evb.finance.analysis.application.service.CalculationService;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.util.Collections;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CalculationController GET 查询接口的现状契约测试。
 *
 * <p>本次仅锁定当前 HTTP 行为：认证 Principal 透传、匿名/空白主体的既有
 * 401/A0002 响应、service 调用边界和既有 ApiResult 包络；不扩展新的状态码或权限规则。</p>
 */
class CalculationControllerGetContractTest {
    private static final String BATCH_NO = "BATCH-001";
    private static final String USER_ID = "U001";

    private CalculationService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(CalculationService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CalculationController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void authenticatedGetPassesPrincipalToServiceAndPreservesSuccessEnvelope() throws Exception {
        when(service.getForUser(BATCH_NO, USER_ID)).thenReturn(result());

        mockMvc.perform(get("/api/v1/calculations/{batchNo}", BATCH_NO)
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("成功"))
                .andExpect(jsonPath("$.data.batchNo").value(BATCH_NO))
                .andExpect(jsonPath("$.data.perspective").value("BASE"))
                .andExpect(jsonPath("$.data.results").isArray());

        // 认证用户只能从 HTTP Principal 进入 service，查询只允许发生一次且不附带其他调用。
        verify(service).getForUser(BATCH_NO, USER_ID);
        verifyNoMoreInteractions(service);
    }

    @Test
    void authenticatedGetKeepsSuccessEnvelopeWhenServiceReturnsNull() throws Exception {
        when(service.getForUser(BATCH_NO, USER_ID)).thenReturn(null);

        mockMvc.perform(get("/api/v1/calculations/{batchNo}", BATCH_NO)
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("成功"));

        verify(service).getForUser(BATCH_NO, USER_ID);
        verifyNoMoreInteractions(service);
    }

    @Test
    void anonymousGetReturnsExistingUnauthorizedA0002EnvelopeWithoutCallingService() throws Exception {
        assertUnauthenticated(get("/api/v1/calculations/{batchNo}", BATCH_NO));

        verifyNoInteractions(service);
    }

    @Test
    void blankPrincipalGetReturnsExistingUnauthorizedA0002EnvelopeWithoutCallingService() throws Exception {
        assertUnauthenticated(get("/api/v1/calculations/{batchNo}", BATCH_NO)
                .principal(principal("")));
        assertUnauthenticated(get("/api/v1/calculations/{batchNo}", BATCH_NO)
                .principal(principal("   ")));
        assertUnauthenticated(get("/api/v1/calculations/{batchNo}", BATCH_NO)
                .principal(principal(null)));

        verifyNoInteractions(service);
    }

    /** 校验当前异常处理器对匿名/空白主体的既有 HTTP 包络，不引入新的参数或权限判断。 */
    private void assertUnauthenticated(RequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0002"))
                .andExpect(jsonPath("$.message").value("未获取到认证用户，拒绝返回经营结果"));
    }

    private Principal principal(String name) {
        return () -> name;
    }

    private CalculationResult result() {
        return new CalculationResult(BATCH_NO, AnalysisPerspective.BASE, Collections.emptyList());
    }
}
