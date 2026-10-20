package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.exception.ApiExceptionHandler;
import com.sunwoda.evb.finance.analysis.application.service.ImportTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;

import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Binding coverage for the current IMPORT controller behavior. */
class ImportTaskQueryControllerBindingContractTest {
    private static final String TASK_NO = "TASK-001";
    private static final String USER_ID = "U001";

    private ImportTaskService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(ImportTaskService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ImportTaskQueryController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void validateWithoutFileUsesCurrentGenericBindingErrorEnvelope() throws Exception {
        mockMvc.perform(multipart("/api/v1/imports/{taskNo}/validate", TASK_NO)
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A9999"))
                .andExpect(jsonPath("$.message").value("系统处理失败"));

        verifyNoInteractions(service);
    }

    @Test
    void validateWithNonMultipartRequestUsesCurrentGenericBindingErrorEnvelope() throws Exception {
        mockMvc.perform(post("/api/v1/imports/{taskNo}/validate", TASK_NO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A9999"))
                .andExpect(jsonPath("$.message").value("系统处理失败"));

        verifyNoInteractions(service);
    }

    @Test
    void statusWithoutStatusUsesCurrentGenericBindingErrorEnvelope() throws Exception {
        mockMvc.perform(post("/api/v1/imports/{taskNo}/status", TASK_NO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"issueCount\":0}")
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A9999"))
                .andExpect(jsonPath("$.message").value("系统处理失败"));

        verifyNoInteractions(service);
    }

    @Test
    void statusWithEmptyStatusUsesCurrentIllegalArgumentErrorEnvelope() throws Exception {
        mockMvc.perform(post("/api/v1/imports/{taskNo}/status", TASK_NO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"\",\"issueCount\":0}")
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
}
