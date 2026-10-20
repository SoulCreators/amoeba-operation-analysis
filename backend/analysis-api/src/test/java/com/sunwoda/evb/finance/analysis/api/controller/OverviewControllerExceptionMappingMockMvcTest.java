package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.exception.ApiExceptionHandler;
import com.sunwoda.evb.finance.analysis.application.service.OverviewService;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP/error-code mapping coverage for the overview controller's existing contracts. */
class OverviewControllerExceptionMappingMockMvcTest {
    private static final String OVERVIEW_PATH = "/api/v1/overview";
    private static final String PERIOD = "2026-01";
    private static final String USER_ID = "U001";

    private OverviewService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(OverviewService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new OverviewController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void rejectsAnonymousOverviewQueryWithUnauthorizedA0002() throws Exception {
        mockMvc.perform(overviewRequest(null, "BASE"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0002"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsBlankOverviewQueryWithUnauthorizedA0002() throws Exception {
        mockMvc.perform(overviewRequest(principal("   "), "BASE"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0002"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsNullNameOverviewQueryWithUnauthorizedA0002() throws Exception {
        // 认证主体存在但用户名为 null 时，仍必须在进入 OverviewService 前拒绝请求。
        mockMvc.perform(overviewRequest(principal(null), "BASE"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0002"));

        verifyNoInteractions(service);
    }

    @Test
    void mapsIllegalPerspectiveToOkAndA0001() throws Exception {
        mockMvc.perform(overviewRequest(principal(USER_ID), "UNKNOWN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0001"))
                .andExpect(jsonPath("$.message").value("不支持的数据视角: UNKNOWN"));

        verifyNoInteractions(service);
    }

    @Test
    void mapsIllegalArgumentFromOverviewServiceToOkAndA0001() throws Exception {
        when(service.query(PERIOD, AnalysisPerspective.BASE, "ALL"))
                .thenThrow(new IllegalArgumentException("概览查询参数无效"));

        mockMvc.perform(overviewRequest(principal(USER_ID), "BASE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0001"))
                .andExpect(jsonPath("$.message").value("概览查询参数无效"));

        verify(service).query(PERIOD, AnalysisPerspective.BASE, "ALL");
    }

    @Test
    void mapsUnexpectedOverviewServiceFailureToOkAndA9999() throws Exception {
        when(service.query(PERIOD, AnalysisPerspective.BASE, "ALL"))
                .thenThrow(new RuntimeException("unexpected overview failure"));

        mockMvc.perform(overviewRequest(principal(USER_ID), "BASE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A9999"))
                .andExpect(jsonPath("$.message").value("系统处理失败"));

        verify(service).query(PERIOD, AnalysisPerspective.BASE, "ALL");
    }

    private MockHttpServletRequestBuilder overviewRequest(Principal principal, String perspective) {
        MockHttpServletRequestBuilder request = get(OVERVIEW_PATH)
                .param("period", PERIOD)
                .param("perspective", perspective);
        return principal == null ? request : request.principal(principal);
    }

    private static Principal principal(String name) {
        return () -> name;
    }
}
