package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.exception.ApiExceptionHandler;
import com.sunwoda.evb.finance.analysis.application.model.ExportFile;
import com.sunwoda.evb.finance.analysis.application.model.ExportFormat;
import com.sunwoda.evb.finance.analysis.application.model.ExportJob;
import com.sunwoda.evb.finance.analysis.application.model.ExportRequest;
import com.sunwoda.evb.finance.analysis.application.service.ExportService;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResultStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Optional;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 本次新增的导出控制器请求绑定契约测试，固定现有 JSON、认证身份和空结果响应行为。
 * 测试只验证当前生产代码，不扩展权限、错误码或导出业务规则。
 */
class ExportControllerRequestContractTest {
    private static final String TASK_ID = "EXP-001";
    private static final String USER_ID = "principal-user";

    private ExportService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(ExportService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ExportController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void createPassesPrincipalAndBindsNormalizedJsonFields() throws Exception {
        ExportJob job = ExportJob.queued(TASK_ID, USER_ID, "REQ-001",
                "BATCH-001-V1.xlsx", ExportFormat.XLSX.getContentType(), timestamp());
        when(service.create(eq(USER_ID), any(ExportRequest.class))).thenReturn(job);

        mockMvc.perform(post("/api/v1/exports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"
                                + "\"requestKey\":\" REQ-001 \","
                                + "\"batchNo\":\" BATCH-001 \","
                                + "\"resultVersion\":\" V1 \","
                                + "\"perspective\":\"product_line\","
                                + "\"format\":\"xlsx\","
                                + "\"resultStatus\":\"confirmed\","
                                + "\"requestedScopes\":[\" summary \",\"\",null,\"detail\",\"summary\"]"
                                + "}")
                        .principal(principal("  " + USER_ID + "  ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.taskId").value(TASK_ID));

        org.mockito.ArgumentCaptor<ExportRequest> captor =
                org.mockito.ArgumentCaptor.forClass(ExportRequest.class);
        verify(service).create(eq(USER_ID), captor.capture());
        ExportRequest request = captor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals("REQ-001", request.getRequestKey());
        org.junit.jupiter.api.Assertions.assertEquals("BATCH-001", request.getBatchNo());
        org.junit.jupiter.api.Assertions.assertEquals("V1", request.getResultVersion());
        org.junit.jupiter.api.Assertions.assertEquals(AnalysisPerspective.PRODUCT_LINE,
                request.getPerspective());
        org.junit.jupiter.api.Assertions.assertEquals(ExportFormat.XLSX, request.getFormat());
        org.junit.jupiter.api.Assertions.assertEquals(CalculationResultStatus.CONFIRMED,
                request.getResultStatus());
        org.junit.jupiter.api.Assertions.assertEquals(
                new LinkedHashSet<String>(Arrays.asList("summary", "detail")),
                request.getRequestedScopes());
    }

    @Test
    void createWithMissingFieldUsesCurrentIllegalArgumentEnvelope() throws Exception {
        mockMvc.perform(createRequest("{\"batchNo\":\"BATCH-001\"}", principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0001"))
                .andExpect(jsonPath("$.message").value("requestKey不能为空"));

        verifyNoInteractions(service);
    }

    @Test
    void createWithIllegalPerspectiveUsesCurrentErrorEnvelope() throws Exception {
        mockMvc.perform(createRequest(createBody("UNKNOWN", "CSV", "PUBLISHED"), principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0001"))
                .andExpect(jsonPath("$.message").value("不支持的数据视角: UNKNOWN"));

        verifyNoInteractions(service);
    }

    @Test
    void createWithIllegalFormatUsesCurrentErrorEnvelope() throws Exception {
        mockMvc.perform(createRequest(createBody("BASE", "PDF", "PUBLISHED"), principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0001"))
                .andExpect(jsonPath("$.message").value("不支持的导出格式: PDF"));

        verifyNoInteractions(service);
    }

    @Test
    void createWithIllegalResultStatusUsesCurrentErrorEnvelope() throws Exception {
        mockMvc.perform(createRequest(createBody("BASE", "CSV", "ARCHIVED"), principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0001"))
                .andExpect(jsonPath("$.message").value("不支持的结果状态: ARCHIVED"));

        verifyNoInteractions(service);
    }

    @Test
    void statusEmptyResultUsesCurrentAuthorizationErrorResponse() throws Exception {
        when(service.status(USER_ID, TASK_ID)).thenReturn(Optional.<ExportJob>empty());

        mockMvc.perform(get("/api/v1/exports/{taskId}", TASK_ID)
                        .principal(principal("  " + USER_ID + "  ")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0002"))
                .andExpect(jsonPath("$.message").value("导出任务不存在、未授权或已不可用"));

        verify(service).status(USER_ID, TASK_ID);
    }

    @Test
    void downloadEmptyResultUsesCurrentAuthorizationErrorResponse() throws Exception {
        when(service.download(USER_ID, TASK_ID)).thenReturn(Optional.<ExportFile>empty());

        mockMvc.perform(get("/api/v1/exports/{taskId}/download", TASK_ID)
                        .principal(principal("  " + USER_ID + "  ")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0002"))
                .andExpect(jsonPath("$.message").value("导出文件不存在、未授权、未完成或已过期"));

        verify(service).download(USER_ID, TASK_ID);
    }

    private MockHttpServletRequestBuilder createRequest(String body, Principal principal) {
        MockHttpServletRequestBuilder request = post("/api/v1/exports")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        return principal == null ? request : request.principal(principal);
    }

    private String createBody(String perspective, String format, String resultStatus) {
        return "{"
                + "\"requestKey\":\"REQ-001\","
                + "\"batchNo\":\"BATCH-001\","
                + "\"resultVersion\":\"V1\","
                + "\"perspective\":\"" + perspective + "\","
                + "\"format\":\"" + format + "\","
                + "\"resultStatus\":\"" + resultStatus + "\","
                + "\"requestedScopes\":[]"
                + "}";
    }

    private Principal principal(String name) {
        return () -> name;
    }

    private LocalDateTime timestamp() {
        return LocalDateTime.of(2026, 10, 20, 10, 0);
    }
}
