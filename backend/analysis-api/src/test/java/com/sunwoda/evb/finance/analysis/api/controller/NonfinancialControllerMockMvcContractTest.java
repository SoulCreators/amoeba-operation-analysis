package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.exception.ApiExceptionHandler;
import com.sunwoda.evb.finance.analysis.application.model.NonfinancialCalculationSummary;
import com.sunwoda.evb.finance.analysis.application.service.NonfinancialMetricService;
import com.sunwoda.evb.finance.analysis.domain.model.NonfinancialFact;
import com.sunwoda.evb.finance.analysis.domain.model.NonfinancialPublication;
import com.sunwoda.evb.finance.analysis.domain.model.NonfinancialPublicationStatus;
import com.sunwoda.evb.finance.analysis.domain.model.NonfinancialResult;
import com.sunwoda.evb.finance.analysis.domain.model.PairingStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 非财经指标接口的 MockMvc 契约：长表导入、配对计算与巴长授权查询。 */
class NonfinancialControllerMockMvcContractTest {
    private static final String BATCH_NO = "BATCH-NF-001";
    private static final String USER_ID = "finance-user";

    private NonfinancialMetricService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(NonfinancialMetricService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new NonfinancialController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void importFactsParsesItemsAndUsesPrincipalAsUploader() throws Exception {
        when(service.importFacts(eq(BATCH_NO), anyList(), eq(USER_ID))).thenReturn(2);

        mockMvc.perform(post("/api/v1/batches/{batchNo}/nonfinancial/facts", BATCH_NO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":["
                                + "{\"baseCode\":\"BASE-1\",\"productionLine\":\"L1\","
                                + "\"cellModel\":\"CELL-1\",\"qualityCategory\":\"YIELD\","
                                + "\"targetActual\":\"TARGET\",\"qualityValue\":0.98,"
                                + "\"sourceRowNo\":1,\"effectiveFileVersion\":\"fv-1\"},"
                                + "{\"baseCode\":\"BASE-1\",\"productionLine\":\"L1\","
                                + "\"cellModel\":\"CELL-1\",\"qualityCategory\":\"YIELD\","
                                + "\"targetActual\":\"ACTUAL\",\"qualityValue\":0.97,"
                                + "\"sourceRowNo\":2,\"effectiveFileVersion\":\"fv-1\"}"
                                + "]}")
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data").value(2));

        verify(service).importFacts(eq(BATCH_NO), anyList(), eq(USER_ID));
    }

    @Test
    void calculateReturnsSummary() throws Exception {
        NonfinancialResult result = new NonfinancialResult(1L, "2026-10", "BASE-1", "L1",
                "CELL-1", "YIELD", new BigDecimal("1.00"), new BigDecimal("0.50"),
                new BigDecimal("-0.50"), new BigDecimal("0.5000000000"), PairingStatus.BOTH,
                "LDR-1", false);
        NonfinancialCalculationSummary summary = new NonfinancialCalculationSummary(
                1, 0, 0, Collections.singletonList(result));
        when(service.calculate(BATCH_NO)).thenReturn(summary);

        mockMvc.perform(post("/api/v1/batches/{batchNo}/nonfinancial/calculate", BATCH_NO)
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.conflictCount").value(0))
                .andExpect(jsonPath("$.data.results[0].pairingStatus").value("BOTH"));

        verify(service).calculate(BATCH_NO);
    }

    @Test
    void listVisibleUsesPrincipalAndReturnsFilteredResults() throws Exception {
        NonfinancialResult result = new NonfinancialResult(1L, "2026-10", "BASE-1", "L1",
                "CELL-1", "YIELD", new BigDecimal("1.00"), new BigDecimal("0.50"),
                new BigDecimal("-0.50"), new BigDecimal("0.5000000000"), PairingStatus.BOTH,
                "LDR-1", false);
        when(service.listVisible("2026-10", USER_ID))
                .thenReturn(Collections.singletonList(result));

        mockMvc.perform(get("/api/v1/nonfinancial/results")
                        .param("period", "2026-10")
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].cellModel").value("CELL-1"))
                .andExpect(jsonPath("$.data[0].leaderEmployeeNo").value("LDR-1"));

        verify(service).listVisible("2026-10", USER_ID);
    }

    @Test
    void exportAppliesSameServerSideFilter() throws Exception {
        NonfinancialResult result = new NonfinancialResult(1L, "2026-10", "BASE-1", "L1",
                "CELL-1", "YIELD", new BigDecimal("1.00"), new BigDecimal("0.50"),
                new BigDecimal("-0.50"), new BigDecimal("0.5000000000"), PairingStatus.BOTH,
                "LDR-1", false);
        when(service.listVisible("2026-10", USER_ID))
                .thenReturn(Collections.singletonList(result));

        mockMvc.perform(get("/api/v1/nonfinancial/results/export")
                        .param("period", "2026-10")
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].cellModel").value("CELL-1"));

        verify(service).listVisible("2026-10", USER_ID);
    }

    @Test
    void emptyImportMapsToA0001Envelope() throws Exception {
        mockMvc.perform(post("/api/v1/batches/{batchNo}/nonfinancial/facts", BATCH_NO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[]}")
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("A0001"))
                .andExpect(jsonPath("$.message").value("导入事实不能为空"));
    }

    @Test
    void publishReturnsPublicationAndUsesPrincipalAsOperator() throws Exception {
        NonfinancialPublication publication = new NonfinancialPublication("2026-10",
                NonfinancialPublicationStatus.PUBLISHED, 0, USER_ID,
                LocalDateTime.of(2026, 10, 22, 9, 0, 0));
        when(service.publish("2026-10", USER_ID)).thenReturn(publication);

        mockMvc.perform(post("/api/v1/nonfinancial/results/{period}/publish", "2026-10")
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.publishedBy").value(USER_ID))
                .andExpect(jsonPath("$.data.conflictCount").value(0));

        verify(service).publish("2026-10", USER_ID);
    }

    @Test
    void getPublicationReturnsDraftWhenUnpublished() throws Exception {
        when(service.getPublication("2026-10"))
                .thenReturn(NonfinancialPublication.draft("2026-10"));

        mockMvc.perform(get("/api/v1/nonfinancial/results/{period}/publication", "2026-10")
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.publishedBy").doesNotExist());

        verify(service).getPublication("2026-10");
    }

    @Test
    void anonymousPrincipalReturnsUnauthorizedWithoutCallingService() throws Exception {
        mockMvc.perform(post("/api/v1/batches/{batchNo}/nonfinancial/calculate", BATCH_NO))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A0002"))
                .andExpect(jsonPath("$.message").value("未获取到认证用户"));
    }

    private static Principal principal(String name) {
        return () -> name;
    }
}
