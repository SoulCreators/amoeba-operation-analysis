package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.exception.ApiExceptionHandler;
import com.sunwoda.evb.finance.analysis.application.service.MetricService;
import com.sunwoda.evb.finance.analysis.domain.model.MetricDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.time.LocalDateTime;

import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MockMvc contract coverage for the metric catalogue write API. */
class MetricControllerWriteMockMvcContractTest {
    private static final String METRICS_PATH = "/api/v1/metrics";
    private static final String PRINCIPAL_USER = "principal-user";
    private static final String REQUEST_USER = "request-body-user";

    private MetricService metricService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        metricService = mock(MetricService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new MetricController(metricService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void authenticatedUpsertPassesEveryRequestFieldAndPrincipalAsUpdatedBy() throws Exception {
        when(metricService.upsert(
                eq("NET_PROFIT"),
                eq("净利润"),
                eq("BASE"),
                eq("万元"),
                eq("REVENUE-COST"),
                eq(10),
                eq(false),
                eq("利润为{actual}万元"),
                eq(PRINCIPAL_USER)))
                .thenReturn(metric());

        mockMvc.perform(post(METRICS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody())
                        .principal(principal(PRINCIPAL_USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("成功"))
                .andExpect(jsonPath("$.data.id").value(101))
                .andExpect(jsonPath("$.data.metricCode").value("NET_PROFIT"))
                .andExpect(jsonPath("$.data.metricName").value("净利润"))
                .andExpect(jsonPath("$.data.applicablePerspective").value("BASE"))
                .andExpect(jsonPath("$.data.unit").value("万元"))
                .andExpect(jsonPath("$.data.formulaExpression").value("REVENUE-COST"))
                .andExpect(jsonPath("$.data.displayOrder").value(10))
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.aiSummaryTemplate").value("利润为{actual}万元"))
                .andExpect(jsonPath("$.data.updatedBy").value(PRINCIPAL_USER))
                .andExpect(jsonPath("$.data.updatedAt").isArray())
                .andExpect(jsonPath("$.data.updatedAt[0]").value(2026))
                .andExpect(jsonPath("$.data.updatedAt[1]").value(10))
                .andExpect(jsonPath("$.data.updatedAt[2]").value(20))
                .andExpect(jsonPath("$.data.updatedAt[3]").value(10))
                .andExpect(jsonPath("$.data.updatedAt[4]").value(15))
                .andExpect(jsonPath("$.data.updatedAt[5]").value(30));

        verify(metricService).upsert(
                "NET_PROFIT", "净利润", "BASE", "万元", "REVENUE-COST", 10,
                false, "利润为{actual}万元", PRINCIPAL_USER);
    }

    @Test
    void omittedOptionalMetricFieldsUseDtoDefaultsBeforeCallingService() throws Exception {
        when(metricService.upsert(
                eq("REVENUE"),
                eq("营业收入"),
                eq("ALL"),
                eq(null),
                eq(null),
                eq(0),
                eq(true),
                eq(null),
                eq(PRINCIPAL_USER)))
                .thenReturn(defaultMetric());

        mockMvc.perform(post(METRICS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metricCode\":\"REVENUE\",\"metricName\":\"营业收入\"}")
                        .principal(principal(PRINCIPAL_USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.applicablePerspective").value("ALL"))
                .andExpect(jsonPath("$.data.displayOrder").value(0))
                .andExpect(jsonPath("$.data.enabled").value(true));

        verify(metricService).upsert(
                "REVENUE", "营业收入", "ALL", null, null, 0,
                true, null, PRINCIPAL_USER);
    }

    private String requestBody() {
        return "{" +
                "\"metricCode\":\"NET_PROFIT\"," +
                "\"metricName\":\"净利润\"," +
                "\"applicablePerspective\":\"BASE\"," +
                "\"unit\":\"万元\"," +
                "\"formulaExpression\":\"REVENUE-COST\"," +
                "\"displayOrder\":10," +
                "\"enabled\":false," +
                "\"aiSummaryTemplate\":\"利润为{actual}万元\"," +
                "\"updatedBy\":\"" + REQUEST_USER + "\"" +
                "}";
    }

    private MetricDefinition metric() {
        return new MetricDefinition(
                101L,
                "NET_PROFIT",
                "净利润",
                "BASE",
                "万元",
                "REVENUE-COST",
                10,
                false,
                "利润为{actual}万元",
                PRINCIPAL_USER,
                LocalDateTime.of(2026, 10, 20, 10, 15, 30));
    }

    private MetricDefinition defaultMetric() {
        return new MetricDefinition(
                102L,
                "REVENUE",
                "营业收入",
                "ALL",
                null,
                null,
                0,
                true,
                null,
                PRINCIPAL_USER,
                LocalDateTime.of(2026, 10, 20, 10, 15, 30));
    }

    private Principal principal(String name) {
        return () -> name;
    }
}
