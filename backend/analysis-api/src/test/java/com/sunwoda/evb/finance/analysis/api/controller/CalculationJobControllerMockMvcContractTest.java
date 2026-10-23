package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.exception.ApiExceptionHandler;
import com.sunwoda.evb.finance.analysis.application.model.CalculationJobDetail;
import com.sunwoda.evb.finance.analysis.application.service.CalculationStageLogService;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationJob;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationJobStatus;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationStage;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationStageLog;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationStageStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MockMvc contract coverage for the calculation-job detail query API. */
class CalculationJobControllerMockMvcContractTest {
    private static final String JOB_ID = "JOB-DETAIL-001";
    private static final String USER_ID = "U-DETAIL-001";

    private CalculationStageLogService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(CalculationStageLogService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CalculationJobController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void authenticatedRequestReturnsJobDetailWithStageLogsAndRetryability() throws Exception {
        when(service.getJobDetail(JOB_ID)).thenReturn(detail(true));

        mockMvc.perform(get("/api/v1/calculations/jobs/{jobId}", JOB_ID)
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.job.jobId").value(JOB_ID))
                .andExpect(jsonPath("$.data.job.status").value("FAILED"))
                .andExpect(jsonPath("$.data.job.stage").value("RECONCILE"))
                .andExpect(jsonPath("$.data.job.errorCode").value("R001"))
                .andExpect(jsonPath("$.data.job.processedRows").value(99))
                .andExpect(jsonPath("$.data.retryable").value(true))
                .andExpect(jsonPath("$.data.stageLogs[0].stage").value("RECONCILE"))
                .andExpect(jsonPath("$.data.stageLogs[0].status").value("FAILED"))
                .andExpect(jsonPath("$.data.stageLogs[0].errorCode").value("R001"));

        verify(service).getJobDetail(JOB_ID);
    }

    @Test
    void anonymousOrBlankPrincipalReturnsUnauthorizedWithoutCallingService() throws Exception {
        assertUnauthenticated(request(null));
        assertUnauthenticated(request(principal(null)));
        assertUnauthenticated(request(principal("")));
        assertUnauthenticated(request(principal("   ")));

        verifyNoInteractions(service);
    }

    @Test
    void mapsUnknownJobToA0001Envelope() throws Exception {
        when(service.getJobDetail(JOB_ID))
                .thenThrow(new IllegalArgumentException("计算作业不存在: " + JOB_ID));

        mockMvc.perform(request(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("A0001"))
                .andExpect(jsonPath("$.message").value("计算作业不存在: " + JOB_ID));

        verify(service).getJobDetail(JOB_ID);
    }

    private void assertUnauthenticated(RequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("A0002"))
                .andExpect(jsonPath("$.message").value("未获取到认证用户，拒绝查看计算作业详情"));
    }

    private RequestBuilder request(Principal principal) {
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request =
                get("/api/v1/calculations/jobs/{jobId}", JOB_ID);
        return principal == null ? request : request.principal(principal);
    }

    private Principal principal(String name) {
        return () -> name;
    }

    private CalculationJobDetail detail(boolean retryable) {
        CalculationJob job = CalculationJob.restore(
                JOB_ID, "REQ-DETAIL-001", "BATCH-DETAIL-001",
                LocalDateTime.of(2026, 10, 22, 9, 0, 0),
                retryable ? CalculationJobStatus.FAILED : CalculationJobStatus.RUNNING,
                1, retryable ? "整体失败" : null,
                LocalDateTime.of(2026, 10, 22, 9, 0, 0), null,
                CalculationStage.RECONCILE,
                LocalDateTime.of(2026, 10, 22, 9, 30, 0),
                "R001", "trace-detail-1", 99L, 1);
        CalculationStageLog log = CalculationStageLog.restore(
                1L, JOB_ID, CalculationStage.RECONCILE, CalculationStageStatus.FAILED,
                LocalDateTime.of(2026, 10, 22, 9, 25, 0),
                LocalDateTime.of(2026, 10, 22, 9, 30, 0),
                99L, new BigDecimal("12345.67"), 1, "R001", "trace-detail-1", "勾稽差异");
        return new CalculationJobDetail(job, Collections.singletonList(log), retryable);
    }
}
