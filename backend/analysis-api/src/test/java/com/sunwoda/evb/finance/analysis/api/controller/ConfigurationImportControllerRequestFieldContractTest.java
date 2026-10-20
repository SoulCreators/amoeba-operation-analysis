package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.exception.ApiExceptionHandler;
import com.sunwoda.evb.finance.analysis.application.service.ConfigurationImportService;
import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportTask;
import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MockMvc contract coverage for configuration-import request fields. */
class ConfigurationImportControllerRequestFieldContractTest {
    private static final String TASK_NO = "CFG-TASK-006";
    private static final String PRINCIPAL_USER = "principal-user";
    private static final String FORGED_REQUEST_USER = "forged-request-user";

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
    void createIgnoresForgedCreatedByAndUsesPrincipalName() throws Exception {
        when(service.create(ConfigurationImportType.FX_RATE, "fx-rate.xlsx", "sha256", PRINCIPAL_USER))
                .thenReturn(task());

        mockMvc.perform(post("/api/v1/config/imports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"FX_RATE\",\"fileName\":\"fx-rate.xlsx\","
                                + "\"checksum\":\"sha256\",\"createdBy\":\""
                                + FORGED_REQUEST_USER + "\"}")
                        .principal(principal(PRINCIPAL_USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.taskNo").value(TASK_NO));

        verify(service).create(ConfigurationImportType.FX_RATE, "fx-rate.xlsx", "sha256", PRINCIPAL_USER);
    }

    @Test
    void createWithMissingFileNamePassesNullToServiceUnderCurrentBinding() throws Exception {
        when(service.create(ConfigurationImportType.FX_RATE, null, "sha256", PRINCIPAL_USER))
                .thenReturn(task());

        mockMvc.perform(post("/api/v1/config/imports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"FX_RATE\",\"checksum\":\"sha256\"}")
                        .principal(principal(PRINCIPAL_USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.taskNo").value(TASK_NO));

        verify(service).create(ConfigurationImportType.FX_RATE, null, "sha256", PRINCIPAL_USER);
    }

    @Test
    void createWithMissingChecksumPassesNullToServiceUnderCurrentBinding() throws Exception {
        when(service.create(ConfigurationImportType.FX_RATE, "fx-rate.xlsx", null, PRINCIPAL_USER))
                .thenReturn(task());

        mockMvc.perform(post("/api/v1/config/imports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"FX_RATE\",\"fileName\":\"fx-rate.xlsx\"}")
                        .principal(principal(PRINCIPAL_USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.taskNo").value(TASK_NO));

        verify(service).create(ConfigurationImportType.FX_RATE, "fx-rate.xlsx", null, PRINCIPAL_USER);
    }

    @Test
    void statusWithEmptyStatusUsesCurrentIllegalArgumentErrorEnvelope() throws Exception {
        mockMvc.perform(post("/api/v1/config/imports/{taskNo}/status", TASK_NO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"\"}")
                        .principal(principal(PRINCIPAL_USER)))
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

    private ConfigurationImportTask task() {
        return new ConfigurationImportTask(
                1L, TASK_NO, ConfigurationImportType.FX_RATE,
                "fx-rate.xlsx", "sha256", PRINCIPAL_USER,
                LocalDateTime.of(2026, 10, 20, 10, 0));
    }
}
