package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.exception.ApiExceptionHandler;
import com.sunwoda.evb.finance.analysis.application.service.V13AnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MockMvc contract coverage for the V1.3 unit-efficiency response. */
class V13AnalysisControllerMockMvcContractTest {
    private V13AnalysisService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(V13AnalysisService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new V13AnalysisController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void returnsFormalUnitEfficiencyFieldsInsideSuccessfulApiResult() throws Exception {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("scopeCode", "BASE-SZ");
        row.put("lineCode", "LINE-01");
        row.put("actualManufacturingCost", new BigDecimal("1250.00"));
        row.put("actualOutputWh", new BigDecimal("100.00"));
        row.put("actualPerWh", new BigDecimal("12.50"));
        row.put("budgetManufacturingCost", new BigDecimal("1000.00"));
        row.put("budgetOutputWh", new BigDecimal("100.00"));
        row.put("budgetPerWh", new BigDecimal("10.00"));
        row.put("gap", new BigDecimal("2.50"));
        row.put("gapRate", new BigDecimal("0.25"));

        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("batchNo", "BATCH-001");
        response.put("rows", Arrays.asList(row));
        when(service.unitEfficiency("BATCH-001", "U001")).thenReturn(response);

        mockMvc.perform(get("/api/v1/v13/unit-efficiency/BATCH-001")
                        .principal(() -> "U001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("成功"))
                .andExpect(jsonPath("$.data.rows[0].scopeCode").value("BASE-SZ"))
                .andExpect(jsonPath("$.data.rows[0].lineCode").value("LINE-01"))
                .andExpect(jsonPath("$.data.rows[0].actualManufacturingCost").value(1250.00))
                .andExpect(jsonPath("$.data.rows[0].actualOutputWh").value(100.00))
                .andExpect(jsonPath("$.data.rows[0].actualPerWh").value(12.50))
                .andExpect(jsonPath("$.data.rows[0].budgetManufacturingCost").value(1000.00))
                .andExpect(jsonPath("$.data.rows[0].budgetOutputWh").value(100.00))
                .andExpect(jsonPath("$.data.rows[0].budgetPerWh").value(10.00))
                .andExpect(jsonPath("$.data.rows[0].gap").value(2.50))
                .andExpect(jsonPath("$.data.rows[0].gapRate").value(0.25));
    }

    @Test
    void missingPrincipalKeepsExistingAuthenticationExceptionContract() throws Exception {
        mockMvc.perform(get("/api/v1/v13/unit-efficiency/BATCH-001"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0002"))
                .andExpect(jsonPath("$.message")
                        .value("未获取到认证用户，拒绝返回 V1.3 分析结果"));

        verifyNoInteractions(service);
    }
}
