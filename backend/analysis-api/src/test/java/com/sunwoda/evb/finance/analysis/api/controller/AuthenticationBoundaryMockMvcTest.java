package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.exception.ApiExceptionHandler;
import com.sunwoda.evb.finance.analysis.application.service.AiAnalysisService;
import com.sunwoda.evb.finance.analysis.application.service.BatchReadinessService;
import com.sunwoda.evb.finance.analysis.application.service.ExportAuthorizationException;
import com.sunwoda.evb.finance.analysis.application.service.ExportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthenticationBoundaryMockMvcTest {
    private BatchReadinessService batchReadinessService;
    private AiAnalysisService aiAnalysisService;
    private ExportService exportService;

    private MockMvc batchReadinessMockMvc;
    private MockMvc aiAnalysisMockMvc;
    private MockMvc exportMockMvc;

    @BeforeEach
    void setUp() {
        batchReadinessService = mock(BatchReadinessService.class);
        aiAnalysisService = mock(AiAnalysisService.class);
        exportService = mock(ExportService.class);

        batchReadinessMockMvc = MockMvcBuilders
                .standaloneSetup(new BatchReadinessController(batchReadinessService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
        aiAnalysisMockMvc = MockMvcBuilders
                .standaloneSetup(new AiAnalysisController(aiAnalysisService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
        exportMockMvc = MockMvcBuilders
                .standaloneSetup(new ExportController(exportService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void anonymousBatchReadinessRequestReturnsHandledAuthenticationFailure() throws Exception {
        batchReadinessMockMvc.perform(get("/api/v1/batches/BATCH-001/readiness"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A0002"))
                .andExpect(jsonPath("$.success").value(false));

        verifyNoInteractions(batchReadinessService);
    }

    @Test
    void anonymousAiAnalysisRequestReturnsHandledAuthenticationFailure() throws Exception {
        aiAnalysisMockMvc.perform(get("/api/v1/ai/analysis/BATCH-001"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A0002"))
                .andExpect(jsonPath("$.success").value(false));

        verifyNoInteractions(aiAnalysisService);
    }

    /** 认证主体缺失或名称为空时，创建导出请求必须返回 401 且不调用导出服务。 */
    @Test
    void anonymousExportRequestReturnsHandledAuthenticationFailure() throws Exception {
        assertUnauthenticated(exportCreateRequest(null));
        assertUnauthenticated(exportCreateRequest(principal(null)));
        assertUnauthenticated(exportCreateRequest(principal("")));
        assertUnauthenticated(exportCreateRequest(principal("   ")));

        verifyNoInteractions(exportService);
    }

    /** 认证主体缺失或名称为空时，查询导出状态必须返回 401 且不调用导出服务。 */
    @Test
    void anonymousExportStatusRequestReturnsHandledAuthenticationFailure() throws Exception {
        assertUnauthenticated(exportStatusRequest(null));
        assertUnauthenticated(exportStatusRequest(principal(null)));
        assertUnauthenticated(exportStatusRequest(principal("")));
        assertUnauthenticated(exportStatusRequest(principal("   ")));

        verifyNoInteractions(exportService);
    }

    /** 认证主体缺失或名称为空时，下载导出文件必须返回 401 且不调用导出服务。 */
    @Test
    void anonymousExportDownloadRequestReturnsHandledAuthenticationFailure() throws Exception {
        assertUnauthenticated(exportDownloadRequest(null));
        assertUnauthenticated(exportDownloadRequest(principal(null)));
        assertUnauthenticated(exportDownloadRequest(principal("")));
        assertUnauthenticated(exportDownloadRequest(principal("   ")));

        verifyNoInteractions(exportService);
    }

    private void assertUnauthenticated(RequestBuilder request) throws Exception {
        exportMockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A0002"))
                .andExpect(jsonPath("$.success").value(false));
    }

    private RequestBuilder exportCreateRequest(Principal principal) {
        MockHttpServletRequestBuilder request = post("/api/v1/exports")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}");
        return principal == null ? request : request.principal(principal);
    }

    private RequestBuilder exportStatusRequest(Principal principal) {
        MockHttpServletRequestBuilder request = get("/api/v1/exports/TASK-001");
        return principal == null ? request : request.principal(principal);
    }

    private RequestBuilder exportDownloadRequest(Principal principal) {
        MockHttpServletRequestBuilder request = get("/api/v1/exports/TASK-001/download");
        return principal == null ? request : request.principal(principal);
    }

    private Principal principal(String name) {
        return () -> name;
    }

    @Test
    void explicitExportPermissionDenialReturnsForbiddenWithCompatibleBody() throws Exception {
        when(exportService.create(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()))
                .thenThrow(new ExportAuthorizationException("角色不允许导出"));

        exportMockMvc.perform(post("/api/v1/exports")
                        .principal(() -> "U001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestKey\":\"REQ-001\",\"batchNo\":\"BATCH-001\","
                                + "\"resultVersion\":\"V1\",\"perspective\":\"BASE\","
                                + "\"format\":\"CSV\",\"resultStatus\":\"PUBLISHED\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("A0002"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("导出权限拒绝: 角色不允许导出"));
    }
}
