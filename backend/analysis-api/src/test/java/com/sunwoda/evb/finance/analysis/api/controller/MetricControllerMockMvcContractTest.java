package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.exception.ApiExceptionHandler;
import com.sunwoda.evb.finance.analysis.application.service.MetricService;
import com.sunwoda.evb.finance.analysis.domain.model.MetricDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MockMvc contract coverage for the metric catalogue read API. */
class MetricControllerMockMvcContractTest {
    private static final String METRICS_PATH = "/api/v1/metrics";
    private static final String USER_ID = "U001";

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
    void authenticatedQueryWithoutPerspectiveDefaultsIncludeDisabledToFalse() throws Exception {
        when(metricService.list(null, false))
                .thenReturn(Collections.singletonList(metric()));

        mockMvc.perform(get(METRICS_PATH).principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("成功"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1));

        verify(metricService).list(null, false);
    }

    @Test
    void authenticatedQueryWithPerspectivePassesPerspectiveAndPrincipal() throws Exception {
        when(metricService.list("PRODUCT_LINE", false))
                .thenReturn(Collections.singletonList(metric()));

        mockMvc.perform(get(METRICS_PATH)
                        .param("perspective", "PRODUCT_LINE")
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].metricCode").value("NET_PROFIT"));

        verify(metricService).list("PRODUCT_LINE", false);
    }

    @Test
    void explicitIncludeDisabledTrueIsPassedToService() throws Exception {
        when(metricService.list(null, true))
                .thenReturn(Collections.singletonList(metric()));

        mockMvc.perform(get(METRICS_PATH)
                        .param("includeDisabled", "true")
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].enabled").value(true));

        verify(metricService).list(null, true);
    }

    @Test
    void successfulResponseSerializesEveryMetricDefinitionField() throws Exception {
        when(metricService.list("BASE", false))
                .thenReturn(Collections.singletonList(metric()));

        mockMvc.perform(get(METRICS_PATH)
                        .param("perspective", "BASE")
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("成功"))
                .andExpect(jsonPath("$.data[0].id").value(101))
                .andExpect(jsonPath("$.data[0].metricCode").value("NET_PROFIT"))
                .andExpect(jsonPath("$.data[0].metricName").value("净利润"))
                .andExpect(jsonPath("$.data[0].applicablePerspective").value("BASE"))
                .andExpect(jsonPath("$.data[0].unit").value("万元"))
                .andExpect(jsonPath("$.data[0].formulaExpression").value("REVENUE-COST"))
                .andExpect(jsonPath("$.data[0].displayOrder").value(10))
                .andExpect(jsonPath("$.data[0].enabled").value(true))
                .andExpect(jsonPath("$.data[0].aiSummaryTemplate").value("利润为{actual}万元"))
                .andExpect(jsonPath("$.data[0].updatedBy").value(USER_ID))
                .andExpect(jsonPath("$.data[0].updatedAt").isArray())
                .andExpect(jsonPath("$.data[0].updatedAt[0]").value(2026))
                .andExpect(jsonPath("$.data[0].updatedAt[1]").value(10))
                .andExpect(jsonPath("$.data[0].updatedAt[2]").value(20))
                .andExpect(jsonPath("$.data[0].updatedAt[3]").value(10))
                .andExpect(jsonPath("$.data[0].updatedAt[4]").value(15))
                .andExpect(jsonPath("$.data[0].updatedAt[5]").value(30));

        verify(metricService).list("BASE", false);
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
                true,
                "利润为{actual}万元",
                USER_ID,
                LocalDateTime.of(2026, 10, 20, 10, 15, 30));
    }

    private Principal principal(String name) {
        return () -> name;
    }
}
