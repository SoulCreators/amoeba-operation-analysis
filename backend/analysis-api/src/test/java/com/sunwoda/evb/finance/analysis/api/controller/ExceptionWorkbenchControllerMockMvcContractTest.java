package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.exception.ApiExceptionHandler;
import com.sunwoda.evb.finance.analysis.application.service.ExceptionWorkbenchService;
import com.sunwoda.evb.finance.analysis.domain.model.ExceptionWorkItem;
import com.sunwoda.evb.finance.analysis.domain.model.IssueSeverity;
import com.sunwoda.evb.finance.analysis.domain.model.IssueStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 异常工作台接口的 MockMvc 契约：筛选、受控忽略与修复复检。 */
class ExceptionWorkbenchControllerMockMvcContractTest {
    private static final String TASK_NO = "T-1";
    private static final String USER_ID = "finance-user";

    private ExceptionWorkbenchService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(ExceptionWorkbenchService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ExceptionWorkbenchController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void listParsesFiltersAndReturnsItems() throws Exception {
        ExceptionWorkItem item = ExceptionWorkItem.report(TASK_NO, "OBJ-1", "FIELD_EMPTY",
                IssueSeverity.BLOCKING, 1, "金额", "金额为空", "");
        when(service.list(eq(TASK_NO), eq(IssueSeverity.BLOCKING), eq("FIELD_EMPTY"),
                isNull(), isNull())).thenReturn(Collections.singletonList(item));

        mockMvc.perform(get("/api/v1/imports/{taskNo}/workbench", TASK_NO)
                        .param("severity", "BLOCKING").param("issueCode", "FIELD_EMPTY")
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].issueCode").value("FIELD_EMPTY"))
                .andExpect(jsonPath("$.data[0].severity").value("BLOCKING"));

        verify(service).list(TASK_NO, IssueSeverity.BLOCKING, "FIELD_EMPTY", null, null);
    }

    @Test
    void ignoreUsesBodyReasonAndPrincipalAsOperator() throws Exception {
        ExceptionWorkItem ignored = ExceptionWorkItem.report(TASK_NO, "OBJ-1", "TYPE_INVALID",
                        IssueSeverity.WARNING, 2, "金额", "类型无效", "")
                .ignore("历史空值", USER_ID, LocalDateTime.of(2026, 10, 22, 9, 0, 0));
        when(service.ignore(TASK_NO, 7L, "历史空值", USER_ID)).thenReturn(ignored);

        mockMvc.perform(post("/api/v1/imports/{taskNo}/workbench/{issueId}/ignore", TASK_NO, 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"历史空值\"}")
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IGNORED"))
                .andExpect(jsonPath("$.data.ignoredBy").value(USER_ID))
                .andExpect(jsonPath("$.data.ignoreReason").value("历史空值"));

        verify(service).ignore(TASK_NO, 7L, "历史空值", USER_ID);
    }

    @Test
    void recheckUsesPrincipalAsOperator() throws Exception {
        when(service.recheck(TASK_NO, USER_ID)).thenReturn(Collections.emptyList());

        mockMvc.perform(post("/api/v1/imports/{taskNo}/workbench/recheck", TASK_NO)
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(service).recheck(TASK_NO, USER_ID);
    }

    @Test
    void blockingIgnoreMapsToA0002Envelope() throws Exception {
        when(service.ignore(TASK_NO, 7L, "原因", USER_ID))
                .thenThrow(new IllegalStateException("阻断项不可忽略: FIELD_EMPTY"));

        mockMvc.perform(post("/api/v1/imports/{taskNo}/workbench/{issueId}/ignore", TASK_NO, 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"原因\"}")
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("A0002"))
                .andExpect(jsonPath("$.message").value("阻断项不可忽略: FIELD_EMPTY"));
    }

    @Test
    void anonymousPrincipalReturnsUnauthorizedWithoutCallingService() throws Exception {
        mockMvc.perform(get("/api/v1/imports/{taskNo}/workbench", TASK_NO))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A0002"))
                .andExpect(jsonPath("$.message").value("未获取到认证用户"));
    }

    private static Principal principal(String name) {
        return () -> name;
    }
}
