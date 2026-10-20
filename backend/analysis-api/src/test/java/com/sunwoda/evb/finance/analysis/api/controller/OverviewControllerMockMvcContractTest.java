package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.exception.ApiExceptionHandler;
import com.sunwoda.evb.finance.analysis.application.service.OverviewService;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.OverviewMetricResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.Collections;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OverviewControllerMockMvcContractTest {
    private OverviewService overviewService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        overviewService = mock(OverviewService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new OverviewController(overviewService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void authenticatedQueryReturnsSuccessfulMetricJsonAndPassesAllParameters() throws Exception {
        OverviewMetricResult metric = metric();
        when(overviewService.query("2026-01", AnalysisPerspective.BASE, "NORTH"))
                .thenReturn(Collections.singletonList(metric));

        mockMvc.perform(get("/api/v1/overview")
                        .param("period", "2026-01")
                        .param("perspective", "base")
                        .param("scope", "NORTH")
                        .principal(principal("U001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("成功"))
                .andExpect(jsonPath("$.data[0].metricCode").value("NET_PROFIT"))
                .andExpect(jsonPath("$.data[0].metricName").value("净利润"))
                .andExpect(jsonPath("$.data[0].unit").value("万元"))
                .andExpect(jsonPath("$.data[0].actual").value(120.50))
                .andExpect(jsonPath("$.data[0].budget").value(100.00))
                .andExpect(jsonPath("$.data[0].gap").value(20.50))
                .andExpect(jsonPath("$.data[0].previous").value(110.00))
                .andExpect(jsonPath("$.data[0].aiSummary").value("利润较预算改善"));

        verify(overviewService).query("2026-01", AnalysisPerspective.BASE, "NORTH");
    }

    @Test
    void omittedScopeDefaultsToAllBeforeCallingService() throws Exception {
        when(overviewService.query("2026-02", AnalysisPerspective.PRODUCT_LINE, "ALL"))
                .thenReturn(Collections.singletonList(metric()));

        mockMvc.perform(get("/api/v1/overview")
                        .param("period", "2026-02")
                        .param("perspective", "PRODUCT_LINE")
                        .principal(principal("U001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].unit").value("万元"));

        verify(overviewService).query("2026-02", AnalysisPerspective.PRODUCT_LINE, "ALL");
    }

    private OverviewMetricResult metric() {
        return new OverviewMetricResult(
                "NET_PROFIT",
                "净利润",
                "万元",
                new BigDecimal("120.50"),
                new BigDecimal("100.00"),
                new BigDecimal("20.50"),
                new BigDecimal("110.00"),
                "利润较预算改善");
    }

    private Principal principal(String name) {
        return () -> name;
    }
}
