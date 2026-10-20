package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.exception.ApiExceptionHandler;
import com.sunwoda.evb.finance.analysis.application.service.ConfigurationImportService;
import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportTask;
import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportType;
import com.sunwoda.evb.finance.analysis.domain.model.ImportIssue;
import com.sunwoda.evb.finance.analysis.domain.model.ImportStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MockMvc contract coverage for configuration import CRUD and state endpoints. */
class ConfigurationImportControllerCrudContractTest {
    private static final String TASK_NO = "CFG-TASK-001";
    private static final String USER_ID = "U001";

    private ConfigurationImportService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(ConfigurationImportService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ConfigurationImportController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void createReturnsSuccessfulEnvelopeAndUsesAuthenticatedUser() throws Exception {
        when(service.create(ConfigurationImportType.FX_RATE, "fx-rate.xlsx", "sha256", USER_ID))
                .thenReturn(task(ImportStatus.CREATED));

        mockMvc.perform(post("/api/v1/config/imports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"FX_RATE\",\"fileName\":\"fx-rate.xlsx\","
                                + "\"checksum\":\"sha256\"}")
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("成功"))
                .andExpect(jsonPath("$.data.taskNo").value(TASK_NO))
                .andExpect(jsonPath("$.data.type").value("FX_RATE"))
                .andExpect(jsonPath("$.data.status").value("CREATED"));

        verify(service).create(ConfigurationImportType.FX_RATE, "fx-rate.xlsx", "sha256", USER_ID);
    }

    @Test
    void createWithUnknownTypeUsesIllegalArgumentErrorEnvelopeWithoutCallingService() throws Exception {
        mockMvc.perform(post("/api/v1/config/imports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"UNKNOWN\",\"fileName\":\"fx-rate.xlsx\","
                                + "\"checksum\":\"sha256\"}")
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0001"))
                .andExpect(jsonPath("$.message").value(containsString("No enum constant")));

        verifyNoInteractions(service);
    }

    @Test
    void getReturnsSuccessfulEnvelopeAndPassesTaskNo() throws Exception {
        when(service.get(TASK_NO)).thenReturn(task(ImportStatus.VALID));

        mockMvc.perform(get("/api/v1/config/imports/{taskNo}", TASK_NO)
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.taskNo").value(TASK_NO))
                .andExpect(jsonPath("$.data.type").value("FX_RATE"))
                .andExpect(jsonPath("$.data.status").value("VALID"));

        verify(service).get(TASK_NO);
    }

    @Test
    void getMapsServiceIllegalArgumentToIllegalArgumentErrorEnvelope() throws Exception {
        when(service.get(TASK_NO)).thenThrow(new IllegalArgumentException("配置导入任务不存在"));

        mockMvc.perform(get("/api/v1/config/imports/{taskNo}", TASK_NO)
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0001"))
                .andExpect(jsonPath("$.message").value("配置导入任务不存在"));

        verify(service).get(TASK_NO);
    }

    @Test
    void issuesReturnsSuccessfulEnvelopeAndPassesTaskNo() throws Exception {
        when(service.issues(TASK_NO)).thenReturn(Collections.singletonList(
                new ImportIssue(2, "currency", "INVALID", "币种无效", "XXX")));

        mockMvc.perform(get("/api/v1/config/imports/{taskNo}/issues", TASK_NO)
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data[0].rowNo").value(2))
                .andExpect(jsonPath("$.data[0].fieldName").value("currency"))
                .andExpect(jsonPath("$.data[0].issueCode").value("INVALID"))
                .andExpect(jsonPath("$.data[0].issueMessage").value("币种无效"))
                .andExpect(jsonPath("$.data[0].rawValue").value("XXX"));

        verify(service).issues(TASK_NO);
    }

    @Test
    void issuesMapsUnexpectedServiceFailureToGenericErrorEnvelope() throws Exception {
        when(service.issues(TASK_NO)).thenThrow(new RuntimeException("unexpected issue query failure"));

        mockMvc.perform(get("/api/v1/config/imports/{taskNo}/issues", TASK_NO)
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A9999"))
                .andExpect(jsonPath("$.message").value("系统处理失败"));

        verify(service).issues(TASK_NO);
    }

    @Test
    void statusReturnsSuccessfulEnvelopeAndConvertsStatusEnum() throws Exception {
        when(service.move(TASK_NO, ImportStatus.VALID)).thenReturn(task(ImportStatus.VALID));

        mockMvc.perform(post("/api/v1/config/imports/{taskNo}/status", TASK_NO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"VALID\"}")
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.taskNo").value(TASK_NO))
                .andExpect(jsonPath("$.data.status").value("VALID"));

        verify(service).move(TASK_NO, ImportStatus.VALID);
    }

    @Test
    void statusWithUnknownStatusUsesIllegalArgumentErrorEnvelopeWithoutCallingService() throws Exception {
        mockMvc.perform(post("/api/v1/config/imports/{taskNo}/status", TASK_NO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"UNKNOWN\"}")
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0001"))
                .andExpect(jsonPath("$.message").value(containsString("No enum constant")));

        verifyNoInteractions(service);
    }

    private Principal principal(String name) {
        return () -> name;
    }

    private ConfigurationImportTask task(ImportStatus status) {
        ConfigurationImportTask task = new ConfigurationImportTask(
                1L, TASK_NO, ConfigurationImportType.FX_RATE,
                "fx-rate.xlsx", "sha256", USER_ID,
                LocalDateTime.of(2026, 10, 20, 10, 0));
        if (status != ImportStatus.CREATED) {
            task.moveTo(status);
        }
        return task;
    }
}
