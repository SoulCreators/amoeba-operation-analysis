package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.dto.MoveImportTaskRequest;
import com.sunwoda.evb.finance.analysis.api.exception.ApiExceptionHandler;
import com.sunwoda.evb.finance.analysis.application.service.ImportTaskService;
import com.sunwoda.evb.finance.analysis.domain.model.ImportIssue;
import com.sunwoda.evb.finance.analysis.domain.model.ImportStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ImportTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MockMvc contract coverage for IMPORT-API-001. */
class ImportTaskQueryControllerMockMvcContractTest {
    private static final String TASK_NO = "TASK-001";
    private static final String USER_ID = "U001";

    private ImportTaskService importTaskService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        importTaskService = mock(ImportTaskService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ImportTaskQueryController(importTaskService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void getReturnsSuccessfulEnvelopeAndPassesTaskNo() throws Exception {
        ImportTask task = task(ImportStatus.VALID, 0);
        when(importTaskService.get(TASK_NO)).thenReturn(task);

        mockMvc.perform(get("/api/v1/imports/{taskNo}", TASK_NO)
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("成功"))
                .andExpect(jsonPath("$.data.taskNo").value(TASK_NO))
                .andExpect(jsonPath("$.data.status").value("VALID"))
                .andExpect(jsonPath("$.data.issueCount").value(0));

        verify(importTaskService).get(TASK_NO);
    }

    @Test
    void issuesReturnsSuccessfulEnvelopeAndPassesTaskNo() throws Exception {
        when(importTaskService.issues(TASK_NO)).thenReturn(Collections.singletonList(
                new ImportIssue(3, "amount", "REQUIRED", "金额不能为空", "")));

        mockMvc.perform(get("/api/v1/imports/{taskNo}/issues", TASK_NO)
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data[0].rowNo").value(3))
                .andExpect(jsonPath("$.data[0].fieldName").value("amount"))
                .andExpect(jsonPath("$.data[0].issueCode").value("REQUIRED"));

        verify(importTaskService).issues(TASK_NO);
    }

    @Test
    void statusPassesStatusEnumAndIssueCountAndReturnsSuccessfulEnvelope() throws Exception {
        when(importTaskService.move(TASK_NO, ImportStatus.VALID, 0))
                .thenReturn(task(ImportStatus.VALID, 0));

        mockMvc.perform(post("/api/v1/imports/{taskNo}/status", TASK_NO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"VALID\",\"issueCount\":0}")
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.taskNo").value(TASK_NO))
                .andExpect(jsonPath("$.data.status").value("VALID"))
                .andExpect(jsonPath("$.data.issueCount").value(0));

        verify(importTaskService).move(TASK_NO, ImportStatus.VALID, 0);
    }

    @Test
    void anonymousOrBlankPrincipalReturns401ForQueryAndStatusEndpoints() throws Exception {
        assertUnauthenticated(queryRequest(null));
        // 本次认证边界补充：查询请求的 Principal 存在但 getName() 返回 null 时仍应返回 401/A0002。
        assertUnauthenticated(queryRequest(principal(null)));
        assertUnauthenticated(queryRequest(principal("")));
        assertUnauthenticated(queryRequest(principal("   ")));
        assertUnauthenticated(issuesRequest(null));
        // 本次认证边界补充：问题明细请求的 Principal 存在但 getName() 返回 null 时仍应返回 401/A0002。
        assertUnauthenticated(issuesRequest(principal(null)));
        assertUnauthenticated(issuesRequest(principal("")));
        assertUnauthenticated(issuesRequest(principal("   ")));
        assertUnauthenticated(statusRequest(null));
        // 本次认证边界补充：状态变更请求的 Principal 存在但 getName() 返回 null 时仍应返回 401/A0002。
        assertUnauthenticated(statusRequest(principal(null)));
        assertUnauthenticated(statusRequest(principal("")));
        assertUnauthenticated(statusRequest(principal("   ")));

        verifyNoInteractions(importTaskService);
    }

    private void assertUnauthenticated(RequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0002"));
    }

    private RequestBuilder queryRequest(Principal principal) {
        return withPrincipal(get("/api/v1/imports/{taskNo}", TASK_NO), principal);
    }

    private RequestBuilder issuesRequest(Principal principal) {
        return withPrincipal(get("/api/v1/imports/{taskNo}/issues", TASK_NO), principal);
    }

    private RequestBuilder statusRequest(Principal principal) {
        return withPrincipal(post("/api/v1/imports/{taskNo}/status", TASK_NO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"VALID\",\"issueCount\":0}"),
                principal);
    }

    private RequestBuilder withPrincipal(MockHttpServletRequestBuilder request, Principal principal) {
        return principal == null ? request : request.principal(principal);
    }

    private Principal principal(String name) {
        return () -> name;
    }

    private ImportTask task(ImportStatus status, int issueCount) {
        LocalDateTime timestamp = LocalDateTime.of(2026, 10, 20, 10, 0);
        return ImportTask.restore(1L, TASK_NO, "BATCH-001", "SALES",
                "sales.xlsx", "sha256", USER_ID, timestamp, status, issueCount, timestamp);
    }
}
