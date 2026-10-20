package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.exception.ApiExceptionHandler;
import com.sunwoda.evb.finance.analysis.application.service.MetricService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.util.Collections;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP/error-code mapping coverage for the metric controller's existing GET contract. */
class MetricControllerExceptionMappingMockMvcTest {
    private static final String METRIC_PATH = "/api/v1/metrics";
    private static final String USER_ID = "U001";

    private MetricService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(MetricService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new MetricController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void rejectsAnonymousMetricQueryWithUnauthorizedA0002() throws Exception {
        mockMvc.perform(metricRequest(null).param("perspective", "BASE"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0002"))
                .andExpect(jsonPath("$.message").value("未获取到认证用户"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsBlankMetricQueryWithUnauthorizedA0002() throws Exception {
        mockMvc.perform(metricRequest(principal("   ")).param("perspective", "BASE"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0002"))
                .andExpect(jsonPath("$.message").value("未获取到认证用户"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsNullNameMetricQueryWithUnauthorizedA0002() throws Exception {
        // 认证主体存在但 Principal.getName() 为 null 时，仍须在进入指标服务前拒绝请求。
        mockMvc.perform(metricRequest(principal(null)).param("perspective", "BASE"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0002"))
                .andExpect(jsonPath("$.message").value("未获取到认证用户"));

        verifyNoInteractions(service);
    }

    @Test
    void mapsIllegalArgumentFromMetricServiceToOkAndA0001() throws Exception {
        when(service.list("BASE", false))
                .thenThrow(new IllegalArgumentException("指标查询参数无效"));

        mockMvc.perform(metricRequest(principal(USER_ID)).param("perspective", "BASE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0001"))
                .andExpect(jsonPath("$.message").value("指标查询参数无效"));

        verify(service).list("BASE", false);
    }

    @Test
    void mapsUnexpectedMetricServiceFailureToOkAndA9999() throws Exception {
        when(service.list("BASE", false))
                .thenThrow(new RuntimeException("unexpected metric failure"));

        mockMvc.perform(metricRequest(principal(USER_ID)).param("perspective", "BASE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A9999"))
                .andExpect(jsonPath("$.message").value("系统处理失败"));

        verify(service).list("BASE", false);
    }

    @Test
    void usesFalseWhenIncludeDisabledIsOmittedAndPerspectiveIsOptional() throws Exception {
        when(service.list(null, false)).thenReturn(Collections.emptyList());

        mockMvc.perform(metricRequest(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"));

        verify(service).list(null, false);
    }

    @Test
    void bindsExplicitIncludeDisabledValues() throws Exception {
        when(service.list("BASE", true)).thenReturn(Collections.emptyList());

        mockMvc.perform(metricRequest(principal(USER_ID))
                        .param("perspective", "BASE")
                        .param("includeDisabled", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(service).list("BASE", true);
    }

    private MockHttpServletRequestBuilder metricRequest(Principal principal) {
        MockHttpServletRequestBuilder request = get(METRIC_PATH);
        return principal == null ? request : request.principal(principal);
    }

    private static Principal principal(String name) {
        return () -> name;
    }
}
