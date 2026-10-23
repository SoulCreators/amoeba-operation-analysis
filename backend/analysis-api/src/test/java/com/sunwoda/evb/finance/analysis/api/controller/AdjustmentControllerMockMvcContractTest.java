package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.exception.ApiExceptionHandler;
import com.sunwoda.evb.finance.analysis.application.service.AdjustmentService;
import com.sunwoda.evb.finance.analysis.domain.model.Adjustment;
import com.sunwoda.evb.finance.analysis.domain.model.AdjustmentDirection;
import com.sunwoda.evb.finance.analysis.domain.model.AdjustmentScenario;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MockMvc 契约：调整单录入、查询与作废命令的认证边界与响应协议。 */
class AdjustmentControllerMockMvcContractTest {
    private static final String USER_ID = "U001";
    private static final String PERIOD = "2026-09";

    private AdjustmentService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(AdjustmentService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdjustmentController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void createAdjustmentUsesAuthenticatedPrincipalAndReachesService() throws Exception {
        when(service.create(PERIOD, AnalysisPerspective.BASE, "NANCHANG",
                AdjustmentScenario.ACTUAL, "REVENUE", new BigDecimal("100.00"),
                AdjustmentDirection.INCREASE, "口径补充", "ev-1", "R1", USER_ID))
                .thenReturn(adjustment());

        mockMvc.perform(post("/api/v1/adjustments")
                        .principal(principal("  " + USER_ID + "  "))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"period\":\"2026-09\",\"perspective\":\"BASE\",\"scope\":\"NANCHANG\","
                                + "\"scenario\":\"ACT\",\"metricCode\":\"REVENUE\",\"amount\":100.00,"
                                + "\"direction\":\"INCREASE\",\"reason\":\"口径补充\","
                                + "\"evidenceFileId\":\"ev-1\",\"expectedVersion\":\"R1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("成功"))
                .andExpect(jsonPath("$.data.adjustmentId").value("ADJ-1"));

        verify(service).create(PERIOD, AnalysisPerspective.BASE, "NANCHANG",
                AdjustmentScenario.ACTUAL, "REVENUE", new BigDecimal("100.00"),
                AdjustmentDirection.INCREASE, "口径补充", "ev-1", "R1", USER_ID);
    }

    @Test
    void listRejectsAnonymousRequestBeforeServiceAccess() throws Exception {
        mockMvc.perform(get("/api/v1/adjustments")
                        .param("period", PERIOD)
                        .param("perspective", "BASE"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void createRejectsAnonymousRequestBeforeServiceAccess() throws Exception {
        mockMvc.perform(post("/api/v1/adjustments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"period\":\"2026-09\",\"perspective\":\"BASE\",\"scope\":\"NANCHANG\","
                                + "\"scenario\":\"ACT\",\"metricCode\":\"REVENUE\",\"amount\":100.00,"
                                + "\"direction\":\"INCREASE\",\"reason\":\"口径补充\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void voidAdjustmentUsesPrincipalAndReachesService() throws Exception {
        when(service.voidAdjustment("ADJ-1", "政策变更", USER_ID)).thenReturn(adjustment());

        mockMvc.perform(post("/api/v1/adjustments/{id}/void", "ADJ-1")
                        .principal(principal(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"政策变更\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.adjustmentId").value("ADJ-1"));

        verify(service).voidAdjustment("ADJ-1", "政策变更", USER_ID);
    }

    @Test
    void permissionDeniedCreateReturnsStandard403() throws Exception {
        when(service.create(PERIOD, AnalysisPerspective.BASE, "NANCHANG",
                AdjustmentScenario.ACTUAL, "REVENUE", new BigDecimal("100.00"),
                AdjustmentDirection.INCREASE, "口径补充", "ev-1", "R1", USER_ID))
                .thenThrow(new IllegalStateException("当前用户无调整录入权限"));

        mockMvc.perform(post("/api/v1/adjustments")
                        .principal(principal(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"period\":\"2026-09\",\"perspective\":\"BASE\",\"scope\":\"NANCHANG\","
                                + "\"scenario\":\"ACT\",\"metricCode\":\"REVENUE\",\"amount\":100.00,"
                                + "\"direction\":\"INCREASE\",\"reason\":\"口径补充\","
                                + "\"evidenceFileId\":\"ev-1\",\"expectedVersion\":\"R1\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("A0002"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.message").value("当前用户无调整录入权限"));
    }

    private Principal principal(String name) {
        return () -> name;
    }

    private Adjustment adjustment() {
        return Adjustment.create("ADJ-1", PERIOD, AnalysisPerspective.BASE, "NANCHANG",
                AdjustmentScenario.ACTUAL, "REVENUE", new BigDecimal("100.00"),
                AdjustmentDirection.INCREASE, "口径补充", "ev-1", "R1", USER_ID,
                LocalDateTime.now()).activate();
    }
}
