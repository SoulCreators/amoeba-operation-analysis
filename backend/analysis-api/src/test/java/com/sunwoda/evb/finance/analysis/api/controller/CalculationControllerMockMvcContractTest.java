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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MockMvc contract coverage for the calculation command API. */
class CalculationControllerMockMvcContractTest {
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
    void historicalResultUsesAuthenticatedPrincipalAndExplicitVersion() throws Exception {
        when(service.getVersionForUser(BATCH_NO, "R1", USER_ID)).thenReturn(result());
        mockMvc.perform(get("/api/v1/calculations/{batch}/versions/{version}/result", BATCH_NO, "R1")
                        .principal(principal("  " + USER_ID + "  ")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.batchNo").value(BATCH_NO));
        verify(service).getVersionForUser(BATCH_NO, "R1", USER_ID);
        verifyNoMoreInteractions(service);
    }

    @Test
    void historicalResultRejectsAnonymousRequestBeforeRepositoryAccess() throws Exception {
        mockMvc.perform(get("/api/v1/calculations/{batch}/versions/{version}/result", BATCH_NO, "R1"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void authenticatedCalculateCommandReachesServiceAndReturnsSuccessfulApiResult() throws Exception {
        CalculationResult expected = result();
        when(service.getForUser(BATCH_NO, USER_ID)).thenReturn(expected);

        mockMvc.perform(post("/api/v1/calculations")
                        .principal(principal(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"batchNo\":\"BATCH-001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("成功"))
                .andExpect(jsonPath("$.data.batchNo").value(BATCH_NO));

        verify(service).calculate(BATCH_NO, USER_ID);
        verify(service).getForUser(BATCH_NO, USER_ID);
    }

    @Test
    void authenticatedConfirmCommandUsesPrincipalAndReachesService() throws Exception {
        when(service.confirm(BATCH_NO, USER_ID)).thenReturn(result());

        mockMvc.perform(post("/api/v1/calculations/confirm")
                        .principal(principal(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"batchNo\":\"BATCH-001\",\"operator\":\"request-operator\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.batchNo").value(BATCH_NO));

        verify(service).confirm(BATCH_NO, USER_ID);
    }

    @Test
    void authenticatedPublishCommandUsesPrincipalAndReachesService() throws Exception {
        when(service.publish(BATCH_NO, USER_ID)).thenReturn(result());

        mockMvc.perform(post("/api/v1/calculations/publish")
                        .principal(principal(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"batchNo\":\"BATCH-001\",\"operator\":\"request-operator\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.batchNo").value(BATCH_NO));

        verify(service).publish(BATCH_NO, USER_ID);
    }

    @Test
    void permissionDeniedLifecycleCommandReturnsStandard403AndDoesNotExposeSuccess() throws Exception {
        when(service.confirm(BATCH_NO, USER_ID))
                .thenThrow(new IllegalStateException("当前用户无结果确认权限"));

        mockMvc.perform(post("/api/v1/calculations/confirm")
                        .principal(principal(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"batchNo\":\"BATCH-001\",\"operator\":\"request-operator\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("A0002"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.message").value("当前用户无结果确认权限"));

        // 本次新增：确认权限被拒绝时只返回标准 403，不能把失败包装成成功结果。
        verify(service).confirm(BATCH_NO, USER_ID);
        verifyNoMoreInteractions(service);
    }

    @Test
    void illegalArgumentFromCalculationServiceKeepsExistingApiResultContract() throws Exception {
        when(service.calculate(BATCH_NO, USER_ID)).thenThrow(new IllegalArgumentException("批次不存在: BATCH-001"));

        mockMvc.perform(calculateRequest(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0001"))
                .andExpect(jsonPath("$.message").value("批次不存在: BATCH-001"));

        verify(service).calculate(BATCH_NO, USER_ID);
        verify(service, never()).getForUser(BATCH_NO, USER_ID);
    }

    @Test
    void nonAuthenticationIllegalStateFromServiceKeepsExistingHttpApiResultContract() throws Exception {
        when(service.publish(BATCH_NO, USER_ID)).thenThrow(new IllegalStateException("发布前校验失败"));

        mockMvc.perform(lifecycleRequest("/api/v1/calculations/publish", principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0002"))
                .andExpect(jsonPath("$.message").value("发布前校验失败"));

        verify(service).publish(BATCH_NO, USER_ID);
    }

    @Test
    void anonymousAndBlankPrincipalCannotReachCalculationCommands() throws Exception {
        assertUnauthenticated(calculateRequest(null));
        assertUnauthenticated(lifecycleRequest("/api/v1/calculations/confirm", null));
        assertUnauthenticated(lifecycleRequest("/api/v1/calculations/publish", null));

        assertUnauthenticated(calculateRequest(principal("")));
        assertUnauthenticated(lifecycleRequest("/api/v1/calculations/confirm", principal("")));
        assertUnauthenticated(lifecycleRequest("/api/v1/calculations/publish", principal("")));

        // 本次认证边界补充 Principal 对象存在但 getName() 返回 null 的 HTTP 请求，三类命令均应拒绝且不触发 Service。
        assertUnauthenticated(calculateRequest(principal(null)));
        assertUnauthenticated(lifecycleRequest("/api/v1/calculations/confirm", principal(null)));
        assertUnauthenticated(lifecycleRequest("/api/v1/calculations/publish", principal(null)));

        verifyNoInteractions(service);
    }

    private void assertUnauthenticated(RequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0002"))
                .andExpect(jsonPath("$.message").value("未获取到认证用户"));
    }

    private RequestBuilder calculateRequest(Principal principal) {
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request = post(
                "/api/v1/calculations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"batchNo\":\"BATCH-001\"}");
        return principal == null ? request : request.principal(principal);
    }

    private RequestBuilder lifecycleRequest(String path, Principal principal) {
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request = post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"batchNo\":\"BATCH-001\",\"operator\":\"request-operator\"}");
        return principal == null ? request : request.principal(principal);
    }

    private Principal principal(String name) {
        return () -> name;
    }

    private CalculationResult result() {
        return new CalculationResult(BATCH_NO, AnalysisPerspective.BASE, Collections.emptyList());
    }
}
