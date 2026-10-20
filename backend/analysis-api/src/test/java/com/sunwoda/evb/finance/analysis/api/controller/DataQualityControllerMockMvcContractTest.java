package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.exception.ApiExceptionHandler;
import com.sunwoda.evb.finance.analysis.application.service.DataQualityService;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.DataQualityReport;
import com.sunwoda.evb.finance.analysis.domain.model.DatasetReadiness;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.util.Arrays;
import java.util.Collections;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MockMvc contract coverage for the data-quality query API. */
class DataQualityControllerMockMvcContractTest {
    private static final String BATCH_NO = "BATCH-QUALITY-001";
    private static final String USER_ID = "U-QUALITY-001";

    private DataQualityService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(DataQualityService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new DataQualityController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void authenticatedRequestReturnsSuccessfulQualityEnvelopeAndPassesBatchNoToService() throws Exception {
        DataQualityReport report = new DataQualityReport(
                BATCH_NO,
                AnalysisPerspective.BASE,
                "BLOCKED",
                2,
                2,
                1,
                1,
                3,
                Collections.singletonList("COST"),
                Collections.singletonList("SALES"),
                Arrays.asList(
                        new DatasetReadiness("SALES", "销售数据", true, true, "INVALID", 2, "TASK-SALES"),
                        new DatasetReadiness("COST", "成本数据", true, false, "MISSING", 1, null)));
        when(service.get(BATCH_NO)).thenReturn(report);

        mockMvc.perform(get("/api/v1/batches/{batchNo}/quality", BATCH_NO)
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("成功"))
                .andExpect(jsonPath("$.data.batchNo").value(BATCH_NO))
                .andExpect(jsonPath("$.data.perspective").value("BASE"))
                .andExpect(jsonPath("$.data.status").value("BLOCKED"))
                .andExpect(jsonPath("$.data.totalDatasetCount").value(2))
                .andExpect(jsonPath("$.data.requiredDatasetCount").value(2))
                .andExpect(jsonPath("$.data.presentRequiredDatasetCount").value(1))
                .andExpect(jsonPath("$.data.validRequiredDatasetCount").value(1))
                .andExpect(jsonPath("$.data.issueCount").value(3))
                .andExpect(jsonPath("$.data.missingDatasetCodes[0]").value("COST"))
                .andExpect(jsonPath("$.data.invalidDatasetCodes[0]").value("SALES"))
                .andExpect(jsonPath("$.data.datasets[0].datasetCode").value("SALES"))
                .andExpect(jsonPath("$.data.datasets[0].taskNo").value("TASK-SALES"))
                .andExpect(jsonPath("$.data.datasets[1].taskNo").value(nullValue()));

        verify(service).get(BATCH_NO);
    }

    @Test
    void anonymousOrBlankPrincipalReturnsUnauthorizedAndDoesNotCallService() throws Exception {
        assertUnauthenticated(qualityRequest(null));
        // 认证主体存在但用户名为 null 时，同样必须在进入数据质量 Service 前拒绝请求。
        assertUnauthenticated(qualityRequest(principal(null)));
        assertUnauthenticated(qualityRequest(principal("")));
        assertUnauthenticated(qualityRequest(principal("   ")));

        verifyNoInteractions(service);
    }

    @Test
    void mapsServiceIllegalArgumentToA0001Envelope() throws Exception {
        when(service.get(BATCH_NO)).thenThrow(new IllegalArgumentException("批次数据质量参数无效"));

        mockMvc.perform(qualityRequest(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0001"))
                .andExpect(jsonPath("$.message").value("批次数据质量参数无效"));

        verify(service).get(BATCH_NO);
    }

    @Test
    void mapsUnexpectedServiceFailureToA9999Envelope() throws Exception {
        when(service.get(BATCH_NO)).thenThrow(new RuntimeException("unexpected quality failure"));

        mockMvc.perform(qualityRequest(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A9999"))
                .andExpect(jsonPath("$.message").value("系统处理失败"));

        verify(service).get(BATCH_NO);
    }

    private void assertUnauthenticated(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0002"))
                .andExpect(jsonPath("$.message").value("未获取到认证用户，拒绝查看批次数据质量"));
    }

    private MockHttpServletRequestBuilder qualityRequest(Principal principal) {
        MockHttpServletRequestBuilder request = get("/api/v1/batches/{batchNo}/quality", BATCH_NO);
        return principal == null ? request : request.principal(principal);
    }

    private Principal principal(String name) {
        return () -> name;
    }
}
