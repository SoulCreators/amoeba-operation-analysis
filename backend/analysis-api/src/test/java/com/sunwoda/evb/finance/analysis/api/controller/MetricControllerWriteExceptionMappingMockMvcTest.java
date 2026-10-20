package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.exception.ApiExceptionHandler;
import com.sunwoda.evb.finance.analysis.application.service.MetricService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP/error-code mapping coverage for the metric catalogue write API. */
class MetricControllerWriteExceptionMappingMockMvcTest {
    private static final String METRICS_PATH = "/api/v1/metrics";
    private static final String USER_ID = "U001";
    private static final String VALID_BODY = "{" +
            "\"metricCode\":\"NET_PROFIT\"," +
            "\"metricName\":\"净利润\"," +
            "\"applicablePerspective\":\"BASE\"," +
            "\"unit\":\"万元\"," +
            "\"formulaExpression\":\"REVENUE-COST\"," +
            "\"displayOrder\":10," +
            "\"enabled\":true," +
            "\"aiSummaryTemplate\":\"利润为{actual}万元\"," +
            "\"updatedBy\":\"spoofed-user\"" +
            "}";

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
    void rejectsAnonymousMetricUpsertWithUnauthorizedA0002() throws Exception {
        mockMvc.perform(metricRequest(null).content(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0002"))
                .andExpect(jsonPath("$.message").value("未获取到认证用户"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsBlankMetricUpsertWithUnauthorizedA0002() throws Exception {
        mockMvc.perform(metricRequest(principal("   ")).content(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0002"))
                .andExpect(jsonPath("$.message").value("未获取到认证用户"));

        verifyNoInteractions(service);
    }

    @Test
    void mapsIllegalArgumentFromMetricServiceToOkAndA0001() throws Exception {
        when(service.upsert(
                eq("NET_PROFIT"), eq("净利润"), eq("BASE"), eq("万元"),
                eq("REVENUE-COST"), eq(10), eq(true), eq("利润为{actual}万元"),
                eq(USER_ID)))
                .thenThrow(new IllegalArgumentException("指标公式无效"));

        mockMvc.perform(metricRequest(principal(USER_ID)).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0001"))
                .andExpect(jsonPath("$.message").value("指标公式无效"));

        verify(service).upsert(
                "NET_PROFIT", "净利润", "BASE", "万元", "REVENUE-COST", 10,
                true, "利润为{actual}万元", USER_ID);
    }

    @Test
    void mapsUnexpectedMetricServiceFailureToOkAndA9999() throws Exception {
        when(service.upsert(
                eq("NET_PROFIT"), eq("净利润"), eq("BASE"), eq("万元"),
                eq("REVENUE-COST"), eq(10), eq(true), eq("利润为{actual}万元"),
                eq(USER_ID)))
                .thenThrow(new RuntimeException("unexpected metric write failure"));

        mockMvc.perform(metricRequest(principal(USER_ID)).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A9999"))
                .andExpect(jsonPath("$.message").value("系统处理失败"));

        verify(service).upsert(
                "NET_PROFIT", "净利润", "BASE", "万元", "REVENUE-COST", 10,
                true, "利润为{actual}万元", USER_ID);
    }

    @Test
    void emptyRequestBodyCurrentlyMapsToOkAndA9999() throws Exception {
        mockMvc.perform(metricRequest(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A9999"))
                .andExpect(jsonPath("$.message").value("系统处理失败"));

        verifyNoInteractions(service);
    }

    private MockHttpServletRequestBuilder metricRequest(Principal principal) {
        MockHttpServletRequestBuilder request = post(METRICS_PATH)
                .contentType(MediaType.APPLICATION_JSON);
        return principal == null ? request : request.principal(principal);
    }

    private static Principal principal(String name) {
        return () -> name;
    }
}
